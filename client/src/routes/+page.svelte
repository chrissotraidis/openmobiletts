<script>
	import { onMount, tick } from 'svelte';
	import { browser } from '$app/environment';
	import AudioPlayer from '$lib/components/AudioPlayer.svelte';
	import TextInput from '$lib/components/TextInput.svelte';
	import TextDisplay from '$lib/components/TextDisplay.svelte';
	import AudioHistory from '$lib/components/AudioHistory.svelte';
	import GenerationProgress from '$lib/components/GenerationProgress.svelte';
	import { settingsStore } from '$lib/stores/settings';
	import { playerStore } from '$lib/stores/player';
	import { apiUrl, healthCheck, fetchCapabilities, fetchModelCatalog, fetchVoices, fetchEngines, switchEngine, fetchSttModels, downloadSttModel, cancelSttModelDownload, fetchTtsModels, downloadTtsModel, cancelTtsModelDownload, activateTtsModel, deleteTtsModel } from '$lib/services/api';
	import { draftStore } from '$lib/stores/draft';
	import { historyStore } from '$lib/stores/history';
	import { batchTranscriptionStore } from '$lib/stores/batchTranscription';
	import { getAudioCacheStats } from '$lib/services/audioCache';
	import { createLibraryBackup, restoreLibraryBackup } from '$lib/services/libraryBackup';
	import { Mic, Plus, History, Settings, ShieldCheck, Zap, Volume2, Sliders, Info, RotateCcw, ChevronDown, FileDown, FileUp, Download, Loader2, Wifi, CheckCircle, XCircle, Cpu, HardDrive, Trash2, AlertTriangle } from 'lucide-svelte';

	let isIOS = $state(false);
	let activeTab = $state('generate');
	let exportingLogs = $state(false);
	let logsClearStatus = $state(''); // '' | 'clearing' | 'cleared' | 'failed'
	let showNewConfirm = $state(false);
	let testingConnection = $state(false);
	let connectionStatus = $state(null); // null | 'success' | 'error'
	let connectionMessage = $state('');
	let appVersion = $state('Loading...');
	let playerExpanded = $state(false);
	let capabilities = $state(null);
	let modelCatalog = $state([]);
	let settingsSection = $state('voice');

	// Engine & voice state
	let engines = $state([]);
	let voices = $state([]);
	let settingsLang = $state('');
	let switchingEngine = $state(false);

	// Storage & STT settings state
	let sttModels = $state([]);
	let sttDownloadProgress = $state('');
	let sttDownloadCancelled = $state(false);
	let ttsModels = $state([]);
	let ttsModelMessages = $state({});
	let activatingTtsModel = $state('');
	let libraryStats = $state({ entries: 0, audioEntries: 0, audioBytes: 0, available: true });
	let dataStatus = $state('');
	let dataStatusKind = $state('neutral');
	let restoreInput = $state(null);

	function consumeAndroidModelRestart() {
		const params = new URLSearchParams(window.location.search);
		const modelId = params.get('resume_model');
		const section = params.get('resume_section');
		if (!modelId || !['models', 'voice'].includes(section)) return null;
		return { modelId, section };
	}

	const settingsLanguages = $derived(() => {
		const map = new Map();
		for (const v of voices) {
			if (!map.has(v.language)) {
				map.set(v.language, v.language_name);
			}
		}
		return [...map.entries()].map(([code, name]) => ({ code, name }));
	});

	const settingsFilteredVoices = $derived(() => {
		if (!settingsLang) return voices;
		return voices.filter((v) => v.language === settingsLang);
	});

	const activeEngineName = $derived(() => {
		const active = engines.find((e) => e.active);
		return active ? active.label : 'Loading...';
	});

	function currentTtsCatalogEntry() {
		const active = engines.find((engine) => engine.active)?.name
			|| (capabilities?.platform === 'android' ? 'kokoro-multi-lang-v1_0' : ($settingsStore.engine || 'kokoro'));
		if (capabilities?.platform === 'android') {
			return modelCatalog.find((entry) => entry.id === active);
		}
		const id = active === 'sherpa-onnx'
			? 'kokoro-multi-lang-v1_0'
			: 'hexgrad-kokoro-82m-pytorch';
		return modelCatalog.find((entry) => entry.id === id);
	}

	function formatModelSize(bytes) {
		if (!bytes) return null;
		return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
	}

	async function loadVoicesAndEngines() {
		try {
			capabilities = await fetchCapabilities();
		} catch {
			capabilities = { schema_version: 1, platform: 'unknown', features: { tts: true } };
		}
		try {
			const catalog = await fetchModelCatalog();
			modelCatalog = catalog.models || [];
		} catch {
			modelCatalog = [];
		}
		try {
			const health = await healthCheck();
			appVersion = health.version || 'Unknown';
		} catch {
			appVersion = 'Unavailable';
		}
		try {
			const [v, e] = await Promise.all([fetchVoices(), fetchEngines()]);
			voices = v;
			engines = e;
			let current = voices.find((vv) => vv.name === $settingsStore.defaultVoice);
			if (!current && voices.length > 0) {
				current = voices[0];
				settingsStore.update('defaultVoice', current.name);
			}
			settingsLang = current ? current.language : (voices[0]?.language || '');
		} catch {
			// silently fail — selectors remain empty
		}
		// Load STT model info
		try {
			const result = await fetchSttModels();
			sttModels = result.models || [];
		} catch {
			// STT info unavailable
		}
		if (capabilities?.platform === 'android') {
			try {
				const result = await fetchTtsModels();
				ttsModels = result.models || [];
			} catch {
				ttsModels = [];
			}
		}
	}

	async function handleEngineSwitch(newEngine) {
		if (switchingEngine) return;
		switchingEngine = true;
		try {
			const result = await switchEngine(newEngine);
			settingsStore.update('engine', newEngine);
			if (result.restart_required && window.Android?.restartApp) {
				const modelLabel = engines.find((engine) => engine.name === newEngine)?.label || newEngine;
				await tick();
				window.Android.restartApp(newEngine, modelLabel, 'voice');
				return;
			}
			// Reload voices + engines after switch
			await loadVoicesAndEngines();
			// If current voice doesn't exist in new engine, pick first voice
			const voiceExists = voices.some((v) => v.name === $settingsStore.defaultVoice);
			if (!voiceExists && voices.length > 0) {
				settingsStore.update('defaultVoice', voices[0].name);
				settingsLang = voices[0].language;
			}
		} catch (err) {
			console.error('Engine switch failed:', err);
		} finally {
			switchingEngine = false;
		}
	}

	function formatBytes(bytes) {
		if (!Number.isFinite(bytes) || bytes <= 0) return '0 MB';
		return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
	}

	async function refreshLibraryStats() {
		const audio = await getAudioCacheStats();
		libraryStats = {
			entries: $historyStore.length,
			audioEntries: audio.entries,
			audioBytes: audio.bytes,
			available: audio.available,
		};
	}

	function showDataStatus(message, kind = 'neutral') {
		dataStatus = message;
		dataStatusKind = kind;
	}

	async function updateRetention(days) {
		settingsStore.update('cleanupIntervalDays', days);
		const removed = await historyStore.cleanupOlderThan(days);
		await refreshLibraryStats();
		showDataStatus(
			removed > 0 ? `Removed ${removed} expired ${removed === 1 ? 'item' : 'items'}.` : 'Retention preference saved.',
			'success',
		);
	}

	async function downloadLibraryBackup() {
		try {
			const backup = createLibraryBackup();
			const blob = new Blob([JSON.stringify(backup, null, 2)], { type: 'application/json' });
			const filename = `openmobiletts-library-${new Date().toISOString().slice(0, 10)}.json`;
			await playerStore.downloadAudio(blob, filename);
			showDataStatus(`Backed up ${backup.history.length} ${backup.history.length === 1 ? 'item' : 'items'}. Audio is not included.`, 'success');
		} catch (error) {
			showDataStatus(error instanceof Error ? error.message : 'Backup failed.', 'error');
		}
	}

	async function restoreLibrary(event) {
		const file = event.currentTarget.files?.[0];
		if (!file) return;
		try {
			if (file.size > 25 * 1024 * 1024) throw new Error('Backup is larger than the 25 MB import limit.');
			const result = restoreLibraryBackup(await file.text());
			await historyStore.cleanupOlderThan(Number($settingsStore.cleanupIntervalDays));
			await refreshLibraryStats();
			showDataStatus(`Restored ${result.merged} ${result.merged === 1 ? 'item' : 'items'}; ${result.total} now in History.`, 'success');
		} catch (error) {
			showDataStatus(error instanceof Error ? error.message : 'Restore failed.', 'error');
		} finally {
			event.currentTarget.value = '';
		}
	}

	async function handleSttModelDownload(model) {
		sttDownloadCancelled = false;
		sttDownloadProgress = 'Starting verified download...';
		try {
			await downloadSttModel(model.id || model.name);
			for (let attempt = 0; attempt < 900; attempt += 1) {
				if (sttDownloadCancelled) return;
				const result = await fetchSttModels();
				sttModels = result.models || [];
				const updated = sttModels.find((item) => item.id === (model.id || model.name));
				if (!updated) throw new Error('Model status is unavailable');

				if (updated.status === 'error') {
					throw new Error(updated.error || 'Model installation failed');
				}
				if (updated.active || updated.status === 'ready' || updated.status === 'installed') {
					sttDownloadProgress = updated.active
						? 'Model verified and ready.'
						: 'Model verified and installed. It will load when transcription starts.';
					setTimeout(() => { sttDownloadProgress = ''; }, 3000);
					return;
				}

				const downloaded = formatBytes(updated.downloaded_bytes);
				const total = formatBytes(updated.total_bytes);
				const phase = updated.status === 'verifying'
					? 'Verifying checksum and model files'
					: updated.status === 'activating'
						? 'Loading model'
						: 'Downloading';
				sttDownloadProgress = `${phase}: ${downloaded} of ${total}`;
				await new Promise((resolve) => setTimeout(resolve, 1000));
			}
			throw new Error('Model installation timed out.');
		} catch (error) {
			sttDownloadProgress = error instanceof Error ? error.message : 'Model installation failed.';
			try {
				const result = await fetchSttModels();
				sttModels = result.models || [];
			} catch {
				// Keep the actionable error visible if status refresh also fails.
			}
		}
	}

	async function handleSttModelDownloadCancel() {
		sttDownloadCancelled = true;
		try {
			await cancelSttModelDownload();
			sttDownloadProgress = 'Download paused. Retry later to resume.';
			const result = await fetchSttModels();
			sttModels = result.models || [];
		} catch (error) {
			sttDownloadProgress = error instanceof Error ? error.message : 'Unable to pause download.';
		}
	}

	async function refreshTtsModels() {
		const result = await fetchTtsModels();
		ttsModels = result.models || [];
		return ttsModels;
	}

	async function handleTtsModelDownload(model) {
		const modelId = model.id || model.name;
		ttsModelMessages[modelId] = 'Starting verified download...';
		try {
			await downloadTtsModel(modelId);
			for (let attempt = 0; attempt < 900; attempt += 1) {
				const updated = (await refreshTtsModels()).find((item) => item.id === modelId);
				if (!updated) throw new Error('Voice model status is unavailable');
				if (updated.status === 'error') throw new Error(updated.error || 'Voice model installation failed');
				if (updated.downloaded) {
					ttsModelMessages[modelId] = 'Verified and installed. Choose Use this model to activate it.';
					return;
				}
				const phase = updated.status === 'downloading' ? 'Downloading' : 'Verifying and installing';
				ttsModelMessages[modelId] = `${phase}: ${formatBytes(updated.downloaded_bytes)} of ${formatBytes(updated.total_bytes)}`;
				await new Promise((resolve) => setTimeout(resolve, 1000));
			}
			throw new Error('Voice model installation timed out.');
		} catch (error) {
			ttsModelMessages[modelId] = error instanceof Error ? error.message : 'Voice model installation failed.';
			try { await refreshTtsModels(); } catch { /* keep actionable error */ }
		}
	}

	async function handleTtsModelDownloadCancel(modelId) {
		try {
			await cancelTtsModelDownload(modelId);
			ttsModelMessages[modelId] = 'Download paused. Retry later to resume.';
			await refreshTtsModels();
		} catch (error) {
			ttsModelMessages[modelId] = error instanceof Error ? error.message : 'Unable to pause download.';
		}
	}

	async function handleTtsModelActivate(modelId) {
		if (activatingTtsModel) return;
		activatingTtsModel = modelId;
		ttsModelMessages[modelId] = 'Selecting model...';
		try {
			const result = await activateTtsModel(modelId);
			settingsStore.update('engine', modelId);
			if (result.restart_required && window.Android?.restartApp) {
				const modelLabel = ttsModels.find((model) => model.id === modelId)?.label || modelId;
				ttsModelMessages[modelId] = 'Restarting with the selected model...';
				await tick();
				window.Android.restartApp(modelId, modelLabel, 'models');
				return;
			}
			await loadVoicesAndEngines();
			const voiceExists = voices.some((voice) => voice.name === $settingsStore.defaultVoice);
			if (!voiceExists && voices.length > 0) {
				settingsStore.update('defaultVoice', voices[0].name);
				settingsLang = voices[0].language;
			}
			ttsModelMessages[modelId] = 'Active and ready.';
		} catch (error) {
			ttsModelMessages[modelId] = error instanceof Error ? error.message : 'Voice model could not be activated.';
		} finally {
			activatingTtsModel = '';
		}
	}

	async function handleTtsModelDelete(model) {
		if (!window.confirm(`Remove ${model.label} from this device? You can download it again later.`)) return;
		try {
			await deleteTtsModel(model.id);
			delete ttsModelMessages[model.id];
			await refreshTtsModels();
		} catch (error) {
			ttsModelMessages[model.id] = error instanceof Error ? error.message : 'Voice model could not be removed.';
		}
	}

	// Tab switching with browser history for Android back button support
	function switchTab(tab) {
		if (playerExpanded) {
			playerExpanded = false;
			if (tab === activeTab) {
				// Same tab — just close expanded and go back in history
				if (history.state?.expanded) history.back();
				return;
			}
			// Different tab — replace the expanded state with new tab
			activeTab = tab;
			history.replaceState({ tab }, '');
			return;
		}
		if (tab === activeTab) return;
		activeTab = tab;
		if (tab === 'settings') refreshLibraryStats();
		history.pushState({ tab }, '');
	}

	function handleExpandedOpen() {
		history.pushState({ tab: activeTab, expanded: true }, '');
	}

	function handleExpandedClose() {
		if (history.state?.expanded) {
			history.back();
		}
	}

	function handlePopState(e) {
		// If expanded player is open, close it first
		if (playerExpanded && !e.state?.expanded) {
			playerExpanded = false;
			return;
		}

		// If we're in a reader/detail view (history pushes view:'reader' state),
		// let AudioHistory's own popstate handler close the reader — don't switch tabs
		if (e.state?.view === 'reader' || (!e.state?.view && activeTab === 'history')) {
			// Let AudioHistory handle it — it has its own popstate listener
			return;
		}

		const tab = e.state?.tab;
		if (tab && tab !== activeTab) {
			activeTab = tab;
		} else if (activeTab !== 'generate') {
			// Back from settings returns to Generate
			activeTab = 'generate';
			history.replaceState({ tab: 'generate' }, '');
		} else {
			// Already on Generate — push state to prevent app exit
			history.pushState({ tab: 'generate' }, '');
		}
	}

	onMount(() => {
		if (browser) {
			isIOS = /iPhone|iPad|iPod/.test(navigator.userAgent);
			const restartContext = consumeAndroidModelRestart();
			if (restartContext) {
				activeTab = 'settings';
				settingsSection = restartContext.section;
				history.replaceState({ tab: 'generate' }, '', window.location.pathname);
				history.pushState({ tab: 'settings' }, '', window.location.pathname);
			} else {
				history.replaceState({ tab: 'generate' }, '');
			}
			loadVoicesAndEngines().then(async () => {
				if (!restartContext) return;
				if (restartContext.modelId) {
					ttsModelMessages[restartContext.modelId] = 'Active and ready.';
				}
				await tick();
				if (restartContext.section === 'models' && restartContext.modelId) {
					document.getElementById(`tts-model-${restartContext.modelId}`)?.scrollIntoView({
						behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
						block: 'center',
					});
				}
			});
			historyStore.cleanupOlderThan(Number($settingsStore.cleanupIntervalDays)).then(refreshLibraryStats);
			window.addEventListener('popstate', handlePopState);
			return () => window.removeEventListener('popstate', handlePopState);
		}
	});

	async function exportLogs() {
		exportingLogs = true;
		try {
			const response = await fetch(apiUrl('/api/logs/export?max_lines=500'));
			if (!response.ok) throw new Error('Failed to fetch logs');

			const data = await response.json();
			const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
			const filename = `openmobiletts-logs-${new Date().toISOString().slice(0, 10)}.json`;
			await playerStore.downloadAudio(blob, filename);
		} catch (err) {
			console.error('Failed to export logs:', err);
			alert('Failed to export logs: ' + err.message);
		} finally {
			exportingLogs = false;
		}
	}

	async function testConnection() {
		testingConnection = true;
		connectionStatus = null;
		connectionMessage = '';
		try {
			const controller = new AbortController();
			const timeout = setTimeout(() => controller.abort(), 5000);
			const res = await fetch(apiUrl('/api/health'), { signal: controller.signal });
			clearTimeout(timeout);
			if (!res.ok) throw new Error('Server unhealthy');
			const data = await res.json();
			connectionStatus = 'success';
			connectionMessage = `Connected (v${data.version})`;
		} catch (err) {
			connectionStatus = 'error';
			connectionMessage = err.name === 'AbortError' ? 'Connection timed out' : 'Could not reach server';
		} finally {
			testingConnection = false;
		}
	}

</script>

<div class="flex h-screen w-full bg-[#0a0c10] text-slate-200 overflow-hidden">

	<!-- DESKTOP SIDEBAR -->
	<aside class="hidden md:flex w-64 border-r border-white/5 flex-col p-4 shrink-0">
		<button
			onclick={() => switchTab('generate')}
			class="flex items-center gap-2 px-2 mb-8 hover:opacity-80 transition-opacity cursor-pointer"
		>
			<img
				src="/icon-192.png"
				alt=""
				class="w-8 h-8 rounded-lg shadow-lg shadow-blue-600/20"
			/>
			<h1 class="font-bold text-lg tracking-tight bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">
				Open Mobile TTS
			</h1>
		</button>

		<nav class="space-y-1 flex-1">
			<button
				onclick={() => switchTab('generate')}
				class="flex items-center gap-3 w-full px-3 py-2 rounded-lg transition-all duration-200 {activeTab === 'generate' ? 'bg-blue-600/10 text-blue-400 font-medium' : 'text-slate-400 hover:bg-white/5 hover:text-slate-200'}"
			>
				<Plus size={18} />
				<span class="text-sm">New Audio</span>
				{#if $batchTranscriptionStore.active}
					<Loader2 size={12} class="ml-auto animate-spin text-blue-400" />
				{/if}
			</button>
			<button
				onclick={() => switchTab('history')}
				class="flex items-center gap-3 w-full px-3 py-2 rounded-lg transition-all duration-200 {activeTab === 'history' ? 'bg-blue-600/10 text-blue-400 font-medium' : 'text-slate-400 hover:bg-white/5 hover:text-slate-200'}"
			>
				<History size={18} />
				<span class="text-sm">History</span>
			</button>
			<button
				onclick={() => switchTab('settings')}
				class="flex items-center gap-3 w-full px-3 py-2 rounded-lg transition-all duration-200 {activeTab === 'settings' ? 'bg-blue-600/10 text-blue-400 font-medium' : 'text-slate-400 hover:bg-white/5 hover:text-slate-200'}"
			>
				<Settings size={18} />
				<span class="text-sm">Settings</span>
			</button>
		</nav>

		<div class="mt-auto pt-4 border-t border-white/5">
			<div class="p-3 bg-white/5 rounded-xl">
				<div class="flex items-center gap-2 mb-2">
					<ShieldCheck size={14} class="text-emerald-400" />
					<span class="text-[10px] font-bold uppercase tracking-wider text-slate-400">100% Local</span>
				</div>
				<div class="flex justify-between items-center text-xs">
					<span class="text-slate-300 font-mono">On-device TTS</span>
					<span class="w-2 h-2 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)] animate-pulse"></span>
				</div>
			</div>
		</div>
	</aside>

	<!-- MAIN CONTENT AREA -->
	<main class="flex-1 flex flex-col overflow-hidden relative pb-20 md:pb-0">

		<!-- TOP BAR -->
		<header class="h-14 border-b border-white/5 flex items-center justify-between px-4 md:px-8 bg-[#0a0c10]/50 backdrop-blur-md z-20 shrink-0">
			<div class="flex items-center gap-3">
				<button
					onclick={() => switchTab('generate')}
					class="md:hidden w-8 h-8 rounded-lg hover:opacity-80 transition-opacity"
					aria-label="Open Generate"
				>
					<img src="/icon-192.png" alt="" class="w-8 h-8 rounded-lg" />
				</button>
				<h2 class="text-sm font-semibold md:font-medium text-slate-300 md:text-slate-400">
					{#if activeTab === 'generate'}
						Generate Speech
					{:else if activeTab === 'history'}
						History
					{:else}
						Settings
					{/if}
				</h2>
			</div>
			{#if activeTab === 'generate'}
				<button
					onclick={() => {
						const hasDraft = draftStore.get().trim();
						if (hasDraft) {
							showNewConfirm = true;
						} else {
							playerStore.stop();
						}
					}}
					class="flex items-center gap-1.5 text-xs text-slate-500 hover:text-slate-300 px-3 py-1.5 rounded-lg transition-colors hover:bg-white/5"
				>
					<Plus size={14} />
					New
				</button>
			{/if}
		</header>

		<!-- SCROLLABLE VIEW -->
		<div class="flex-1 overflow-y-auto overflow-x-hidden p-4 md:p-8 custom-scrollbar">
			{#if activeTab === 'generate'}
				<div class="max-w-4xl mx-auto space-y-6 md:space-y-8 pb-32">
					<TextInput {capabilities} />
					<GenerationProgress />
					<TextDisplay />
				</div>
			{:else if activeTab === 'history'}
				<div class="max-w-4xl mx-auto">
					<AudioHistory />
				</div>
			{:else if activeTab === 'settings'}
				<div class="max-w-5xl mx-auto pb-12">
					<div class="grid lg:grid-cols-[220px_minmax(0,1fr)] gap-4 lg:gap-6 items-start">
						<nav aria-label="Settings categories" class="settings-categories grid grid-cols-5 gap-1 p-1.5 bg-slate-900/70 border border-white/5 rounded-2xl lg:flex lg:flex-col lg:gap-1 lg:p-2 lg:sticky lg:top-0">
							<button
								onclick={() => settingsSection = 'voice'}
								aria-pressed={settingsSection === 'voice'}
								class="settings-category {settingsSection === 'voice' ? 'settings-category-active' : ''}"
							>
								<Sliders size={18} />
								<span><strong>Voice</strong><small>Engine & playback</small></span>
							</button>
							<button
								onclick={() => settingsSection = 'connection'}
								aria-pressed={settingsSection === 'connection'}
								class="settings-category {settingsSection === 'connection' ? 'settings-category-active' : ''}"
							>
								<Wifi size={18} />
								<span><strong>Connection</strong><small>Local server</small></span>
							</button>
							<button
								onclick={() => settingsSection = 'models'}
								aria-pressed={settingsSection === 'models'}
								class="settings-category {settingsSection === 'models' ? 'settings-category-active' : ''}"
							>
								<Cpu size={18} />
								<span><strong>Models</strong><small>Voice & speech</small></span>
							</button>
							<button
								onclick={() => { settingsSection = 'data'; refreshLibraryStats(); }}
								aria-pressed={settingsSection === 'data'}
								class="settings-category {settingsSection === 'data' ? 'settings-category-active' : ''}"
							>
								<HardDrive size={18} />
								<span><strong>Data</strong><small>Library & backup</small></span>
							</button>
							<button
								onclick={() => settingsSection = 'app'}
								aria-pressed={settingsSection === 'app'}
								class="settings-category {settingsSection === 'app' ? 'settings-category-active' : ''}"
							>
								<Info size={18} />
								<span><strong>App</strong><small>About & logs</small></span>
							</button>
						</nav>

						<section class="settings-panel min-w-0" aria-live="polite">
						{#if settingsSection === 'voice'}
					<!-- TTS Defaults -->
					<div class="p-4 md:p-6 bg-slate-900/40 border border-white/5 rounded-2xl space-y-4 md:space-y-5">
						<div class="flex items-center gap-2">
							<Sliders size={18} class="text-blue-400" />
							<h3 class="text-lg font-semibold">TTS Defaults</h3>
						</div>

						<!-- TTS Engine -->
						{#if engines.length > 0 && capabilities?.features?.engine_switching}
							<div class="space-y-2">
								<div class="flex items-center gap-2 px-1">
									<Cpu size={14} class="text-slate-500" />
									<span class="text-xs font-bold text-slate-500 uppercase tracking-widest">TTS Engine</span>
								</div>
								<div class="relative">
									<select
										value={engines.find((e) => e.active)?.name || ''}
									onchange={(e) => handleEngineSwitch(e.currentTarget.value)}
										disabled={switchingEngine}
										class="w-full bg-slate-900 border border-white/10 rounded-xl p-3 appearance-none focus:outline-none focus:ring-1 focus:ring-blue-500 transition-all text-sm disabled:opacity-50"
									>
										{#each engines.filter((e) => e.available) as eng}
											<option value={eng.name}>{eng.label}</option>
										{/each}
									</select>
									<div class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
										{#if switchingEngine}
											<Loader2 size={16} class="animate-spin" />
										{:else}
											<ChevronDown size={18} />
										{/if}
									</div>
								</div>
							</div>
						{/if}

						<!-- Default Voice (Language + Voice) -->
						<div class="space-y-2">
							<div class="flex items-center gap-2 px-1">
								<Volume2 size={14} class="text-slate-500" />
								<span class="text-xs font-bold text-slate-500 uppercase tracking-widest">Default Voice</span>
							</div>
							{#if voices.length > 0}
								<div class="flex gap-2">
									<!-- Language picker -->
									<div class="relative flex-1">
										<select
											value={settingsLang}
											onchange={(e) => {
											settingsLang = e.currentTarget.value;
												const first = voices.find((v) => v.language === settingsLang);
												if (first) settingsStore.update('defaultVoice', first.name);
											}}
											class="w-full bg-slate-900 border border-white/10 rounded-xl p-3 appearance-none focus:outline-none focus:ring-1 focus:ring-blue-500 transition-all text-sm"
										>
											{#each settingsLanguages() as lang}
												<option value={lang.code}>{lang.name}</option>
											{/each}
										</select>
										<div class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
											<ChevronDown size={18} />
										</div>
									</div>
									<!-- Voice picker -->
									<div class="relative flex-1">
										<select
											value={$settingsStore.defaultVoice}
										onchange={(e) => settingsStore.update('defaultVoice', e.currentTarget.value)}
											class="w-full bg-slate-900 border border-white/10 rounded-xl p-3 appearance-none focus:outline-none focus:ring-1 focus:ring-blue-500 transition-all text-sm"
										>
											{#each settingsFilteredVoices() as v}
												<option value={v.name}>{v.display_name} ({v.gender === 'female' ? 'F' : 'M'})</option>
											{/each}
										</select>
										<div class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
											<ChevronDown size={18} />
										</div>
									</div>
								</div>
							{:else}
								<p class="text-sm text-slate-500 px-1">Loading voices...</p>
							{/if}
						</div>

						<!-- Auto-play toggle -->
						{#if currentTtsCatalogEntry()}
							{@const model = currentTtsCatalogEntry()}
							<details class="group bg-white/[0.03] border border-white/5 rounded-xl px-4 py-3">
								<summary class="cursor-pointer list-none flex items-center justify-between text-sm text-slate-300">
									<span class="flex items-center gap-2"><Info size={14} class="text-blue-400" /> Model details</span>
									<ChevronDown size={16} class="text-slate-500 transition-transform group-open:rotate-180" />
								</summary>
								<div class="grid sm:grid-cols-2 gap-x-5 gap-y-2 mt-3 text-xs">
									<div><span class="text-slate-500">Model</span><p class="text-slate-300 mt-0.5">{model.label}</p></div>
									<div><span class="text-slate-500">Runtime</span><p class="text-slate-300 mt-0.5">{model.runtime.name} {model.runtime.minimum_version}</p></div>
									<div><span class="text-slate-500">Languages shown</span><p class="text-slate-300 mt-0.5">{model.exposed_languages.join(', ')}</p></div>
									<div><span class="text-slate-500">Weights license</span><p class="text-slate-300 mt-0.5">{model.license.weights}</p></div>
									<div class="sm:col-span-2"><span class="text-slate-500">Integrity</span><p class="text-slate-300 mt-0.5">{model.integrity_status}</p></div>
								</div>
							</details>
						{/if}

						<div class="flex items-center justify-between p-3 bg-white/5 rounded-xl">
							<div>
								<p class="text-sm font-medium text-slate-200">Auto-play on generation</p>
								<p class="text-[10px] text-slate-500 mt-0.5">Automatically play audio when generation completes</p>
							</div>
							<button
								onclick={() => settingsStore.update('autoPlay', !$settingsStore.autoPlay)}
								class="w-11 h-6 rounded-full transition-colors duration-200 relative {$settingsStore.autoPlay ? 'bg-blue-600' : 'bg-slate-700'}"
								aria-label="Toggle auto-play"
							>
								<div class="w-4 h-4 bg-white rounded-full absolute top-1 transition-all duration-200 {$settingsStore.autoPlay ? 'left-6' : 'left-1'}"></div>
							</button>
						</div>

						<!-- Reset -->
						<button
							onclick={() => {
							settingsStore.reset();
							const defaultVoice = voices.find((v) => v.name === 'af_heart');
							if (defaultVoice) settingsLang = defaultVoice.language;
						}}
							class="flex items-center gap-2 text-xs text-slate-500 hover:text-slate-300 transition-colors px-1"
						>
							<RotateCcw size={12} />
							Reset to defaults
						</button>
					</div>

					{:else if settingsSection === 'connection'}
					<!-- Server Connection -->
					<div class="p-4 md:p-6 bg-slate-900/40 border border-white/5 rounded-2xl space-y-4 md:space-y-5">
						<div class="flex items-center justify-between gap-3">
							<div class="flex items-center gap-2">
								<Wifi size={18} class="text-blue-400" />
								<h3 class="text-lg font-semibold">Server Connection</h3>
							</div>
							<details class="group relative shrink-0">
								<summary class="min-h-11 px-3 flex items-center gap-1.5 rounded-lg text-xs text-slate-400 hover:text-slate-200 hover:bg-white/5 cursor-pointer list-none focus:outline-none focus:ring-1 focus:ring-blue-500">
									<Info size={14} />
									What is this?
								</summary>
								<div class="absolute right-0 z-20 mt-2 w-[min(18rem,calc(100vw-4rem))] p-3 bg-slate-800 border border-white/10 rounded-xl shadow-xl text-xs leading-5 text-slate-300">
									Open Mobile TTS uses a local server to run speech processing on your device. Keep the default unless you intentionally run the desktop backend on another trusted computer.
								</div>
							</details>
						</div>

						{#if capabilities?.platform === 'android'}
							<div class="p-4 bg-emerald-500/5 border border-emerald-500/15 rounded-xl flex items-start gap-3">
								<ShieldCheck size={18} class="text-emerald-400 mt-0.5" />
								<div>
									<p class="text-sm font-medium text-slate-200">On-device connection</p>
									<p class="text-xs text-slate-500 mt-1">The Android app always uses its private loopback server at 127.0.0.1. Remote server overrides are disabled.</p>
								</div>
							</div>
						{:else}
						<div class="space-y-2">
							<div class="flex items-center gap-2 px-1">
								<span class="text-xs font-bold text-slate-500 uppercase tracking-widest">Server URL</span>
							</div>
							<input
								type="text"
								value={$settingsStore.serverUrl}
								onchange={(e) => {
									settingsStore.update('serverUrl', e.currentTarget.value.trim());
									connectionStatus = null;
								}}
								placeholder="Leave empty for same-origin (default)"
								class="w-full bg-slate-900 border border-white/10 rounded-xl p-3 focus:outline-none focus:ring-1 focus:ring-blue-500 transition-all text-sm placeholder:text-slate-600"
							/>
							<p class="text-[10px] text-slate-600 px-1">
								Leave empty for this local app. Use an override only with an explicitly enabled trusted-LAN development server.
							</p>
						</div>

						<div class="flex items-center gap-3">
							<button
								onclick={testConnection}
								disabled={testingConnection}
								class="flex items-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-500 disabled:bg-blue-600/50 rounded-xl text-sm font-medium transition-colors"
							>
								{#if testingConnection}
									<Loader2 size={16} class="animate-spin" />
									Testing...
								{:else}
									<Wifi size={16} />
									Test Connection
								{/if}
							</button>

							{#if connectionStatus === 'success'}
								<div class="flex items-center gap-1.5 text-sm text-emerald-400">
									<CheckCircle size={16} />
									{connectionMessage}
								</div>
							{:else if connectionStatus === 'error'}
								<div class="flex items-center gap-1.5 text-sm text-red-400">
									<XCircle size={16} />
									{connectionMessage}
								</div>
							{/if}
						</div>
						{/if}
					</div>

					{:else if settingsSection === 'models'}
					<!-- Local Models -->
					<div class="p-6 bg-slate-900/40 border border-white/5 rounded-2xl space-y-5">
						<div class="flex items-center gap-2">
							<Cpu size={18} class="text-blue-400" />
							<h3 class="text-lg font-semibold">Models</h3>
						</div>

						<div class="p-3 md:p-4 bg-blue-500/5 border border-blue-500/15 rounded-xl">
							<p class="text-sm font-medium text-slate-200">What is needed?</p>
							<p class="text-xs text-slate-500 mt-1 leading-5">
								A voice model is required to generate audio. The speech-to-text model is optional and is only needed for dictation or transcribing audio files.
							</p>
						</div>

						<div class="grid sm:grid-cols-2 gap-4 items-start">
						<!-- TTS Model Status -->
						<div class="space-y-2 min-w-0" aria-label="Text-to-speech model">
							<div class="flex items-center gap-2 px-1">
								<span class="text-xs font-bold text-slate-500 uppercase tracking-widest">Text-to-speech</span>
								<span class="text-[10px] font-medium text-blue-300 bg-blue-500/10 border border-blue-500/15 rounded-full px-2 py-0.5">Required</span>
							</div>
				{#if capabilities?.platform === 'android' && ttsModels.length > 0}
								{#each ttsModels as ttsModel}
									<div id={`tts-model-${ttsModel.id}`} class="bg-slate-900/60 border {ttsModel.active ? 'border-emerald-500/25' : 'border-white/10'} rounded-xl p-3 md:p-4 space-y-3">
										<div class="flex items-start justify-between gap-3">
											<div class="min-w-0">
												<div class="flex flex-wrap items-center gap-2">
													<p class="text-sm text-slate-200">{ttsModel.label}</p>
													{#if ttsModel.experimental}
														<span class="text-[10px] font-medium text-amber-300 bg-amber-500/10 border border-amber-500/20 rounded-full px-2 py-0.5">Experimental</span>
													{:else}
														<span class="text-[10px] font-medium text-blue-300 bg-blue-500/10 border border-blue-500/15 rounded-full px-2 py-0.5">Stable default</span>
													{/if}
												</div>
												<p class="text-xs text-slate-500 mt-1">Creates spoken audio locally on this device.</p>
											</div>
											{#if ttsModel.active}
												<span class="text-xs text-emerald-400 flex items-center gap-1 shrink-0"><CheckCircle size={12} /> Active</span>
											{:else if ttsModel.downloading}
												<span class="text-xs text-blue-400 flex items-center gap-1 shrink-0"><Loader2 size={12} class="animate-spin" /> Installing</span>
											{:else if ttsModel.downloaded}
												<span class="text-xs text-emerald-400/70 flex items-center gap-1 shrink-0"><CheckCircle size={12} /> Installed</span>
											{:else}
												<span class="text-xs text-amber-400 shrink-0">Not installed</span>
											{/if}
										</div>

										<p class="text-[10px] text-slate-500">
											{ttsModel.archive_size_mb} MB download · {ttsModel.installed_size_mb} MB installed · {ttsModel.precision} · {ttsModel.languages?.join(', ') || 'English'} · {ttsModel.weights_license}
										</p>

										{#if ttsModel.downloading}
											<div class="space-y-1">
												<div class="w-full h-1.5 bg-white/5 rounded-full overflow-hidden">
													<div class="h-full bg-blue-500 rounded-full transition-all duration-300" style={`width: ${ttsModel.progress_percent ?? 0}%`}></div>
												</div>
												<p class="text-[10px] text-slate-500">{ttsModelMessages[ttsModel.id] || 'Downloading and verifying model...'}</p>
											</div>
										{:else if ttsModelMessages[ttsModel.id]}
											<p class="text-[10px] {ttsModel.status === 'error' ? 'text-red-400' : 'text-slate-400'}">{ttsModelMessages[ttsModel.id]}</p>
										{/if}

										{#if ttsModel.downloading}
											<button
												onclick={() => handleTtsModelDownloadCancel(ttsModel.id)}
												class="w-full flex items-center justify-center gap-2 px-3 py-2 bg-slate-800 hover:bg-slate-700 border border-white/10 rounded-lg text-xs transition-colors"
											>Pause download</button>
										{:else if !ttsModel.downloaded}
											<button
												onclick={() => handleTtsModelDownload(ttsModel)}
												class="w-full flex items-center justify-center gap-2 px-3 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg text-xs font-medium transition-colors"
											>
												<Download size={14} /> {ttsModel.status === 'error' ? 'Retry' : 'Download'} ({ttsModel.archive_size_mb} MB)
											</button>
										{:else if !ttsModel.active}
											<div class="flex gap-2">
												<button
													onclick={() => handleTtsModelActivate(ttsModel.id)}
													disabled={activatingTtsModel !== ''}
													class="flex-1 flex items-center justify-center gap-2 px-3 py-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 rounded-lg text-xs font-medium transition-colors"
												>
													{#if activatingTtsModel === ttsModel.id}<Loader2 size={14} class="animate-spin" />{/if}
													Use this model
												</button>
												{#if ttsModel.removable}
													<button
														onclick={() => handleTtsModelDelete(ttsModel)}
														class="min-h-11 px-3 flex items-center justify-center bg-slate-800 hover:bg-red-500/10 border border-white/10 hover:border-red-500/20 rounded-lg text-slate-400 hover:text-red-300 transition-colors"
														aria-label={`Remove ${ttsModel.label}`}
													><Trash2 size={14} /></button>
												{/if}
											</div>
										{/if}
									</div>
								{/each}
							{:else if currentTtsCatalogEntry()}
								{@const ttsModel = currentTtsCatalogEntry()}
								<div class="bg-slate-900/60 border border-white/10 rounded-xl p-3 md:p-4 space-y-3">
									<div class="flex items-start justify-between gap-3">
										<div class="min-w-0">
											<p class="text-sm text-slate-200">{ttsModel.label}</p>
											<p class="text-xs text-slate-500 mt-1">Creates spoken audio from text.</p>
										</div>
										{#if voices.length > 0}
											<span class="text-xs text-emerald-400 flex items-center gap-1 shrink-0"><CheckCircle size={12} /> Ready</span>
										{:else}
											<span class="text-xs text-blue-400 flex items-center gap-1 shrink-0"><Loader2 size={12} class="animate-spin" /> Preparing</span>
										{/if}
									</div>
									<div class="grid sm:grid-cols-2 gap-x-4 gap-y-2 text-[10px] text-slate-500">
										<p><span class="text-slate-400">Runtime:</span> {ttsModel.runtime.name}</p>
										<p><span class="text-slate-400">Storage:</span> {formatModelSize(ttsModel.installed_bytes) || 'reported after setup'}</p>
										<p><span class="text-slate-400">Languages:</span> {ttsModel.exposed_languages.join(', ')}</p>
										<p><span class="text-slate-400">License:</span> {ttsModel.license.weights}</p>
									</div>
									<p class="text-xs text-slate-400 leading-5 border-t border-white/5 pt-3">
										{#if ttsModel.id === 'hexgrad-kokoro-82m-pytorch'}
											Downloads automatically the first time the local app starts. No separate download button is required.
										{:else}
											Installed through the desktop Sherpa-ONNX model setup and selected under Voice.
										{/if}
									</p>
								</div>
							{:else}
								<div class="bg-slate-900/60 border border-white/10 rounded-xl px-4 py-4 text-xs text-slate-500 flex items-center gap-2">
									<Loader2 size={14} class="animate-spin" /> Checking voice model...
								</div>
							{/if}
						</div>

						<!-- STT Model Status -->
						<div class="space-y-2 min-w-0" aria-label="Speech-to-text model">
							<div class="flex items-center gap-2 px-1">
								<span class="text-xs font-bold text-slate-500 uppercase tracking-widest">Speech-to-text</span>
								<span class="text-[10px] font-medium text-slate-400 bg-white/5 border border-white/10 rounded-full px-2 py-0.5">Optional</span>
							</div>
							{#if sttModels.length > 0}
								{#each sttModels as model}
									<div class="bg-slate-900/60 border border-white/10 rounded-xl p-3 md:p-4 space-y-3">
										<div class="flex items-start justify-between gap-3">
											<div class="min-w-0">
												<p class="text-sm text-slate-200">{model.label}</p>
												<p class="text-xs text-slate-500 mt-1">Turns microphone recordings and audio files into text.</p>
											</div>
											{#if model.active}
												<span class="text-xs text-emerald-400 flex items-center gap-1 shrink-0">
													<CheckCircle size={12} /> Ready
												</span>
										{:else if model.downloaded}
											<span class="text-xs text-emerald-400/70 flex items-center gap-1 shrink-0">
												<CheckCircle size={12} /> Downloaded
											</span>
										{:else if model.downloading}
											<span class="text-xs text-blue-400 flex items-center gap-1 shrink-0">
												<Loader2 size={12} class="animate-spin" /> {model.status === 'downloading' ? 'Downloading' : model.status === 'verifying' ? 'Verifying' : 'Loading'}...
											</span>
										{:else if model.status === 'error'}
											<span class="text-xs text-red-400 shrink-0">Install failed</span>
										{:else}
											<span class="text-xs text-amber-400 shrink-0">Not installed</span>
										{/if}
										</div>
										<p class="text-[10px] text-slate-500">
											{model.archive_size_mb} MB download · {model.installed_size_mb} MB installed · {model.precision} · {model.languages?.join(', ') || 'language not reported'} · {model.weights_license || 'license in provenance ledger'}
										</p>

										{#if model.downloading && sttDownloadProgress}
											<div class="space-y-1">
												<div class="w-full h-1.5 bg-white/5 rounded-full overflow-hidden">
													<div class="h-full bg-blue-500 rounded-full transition-all duration-300" style={`width: ${model.progress_percent ?? 0}%`}></div>
												</div>
												<p class="text-[10px] text-slate-500">{sttDownloadProgress}</p>
											</div>
										{/if}

										{#if model.downloading && capabilities?.platform === 'android'}
											<button
												onclick={handleSttModelDownloadCancel}
												class="w-full flex items-center justify-center gap-2 px-3 py-2 bg-slate-800 hover:bg-slate-700 border border-white/10 rounded-lg text-xs transition-colors"
											>
												Pause download
											</button>
										{/if}

										{#if model.error && !model.downloading}
											<p class="text-[10px] text-red-400">{model.error}</p>
										{/if}

										{#if !model.downloaded && !model.active && !model.downloading}
											<button
												onclick={() => handleSttModelDownload(model)}
												class="w-full flex items-center justify-center gap-2 px-3 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg text-xs font-medium transition-colors"
											>
												<Download size={14} />
												{model.status === 'error' ? 'Retry' : 'Download'} optional speech-to-text model ({model.archive_size_mb} MB)
											</button>
										{/if}
									</div>
								{/each}
							{:else}
								<p class="text-xs text-slate-500 px-1">Checking model status...</p>
							{/if}
						</div>
						</div>
					</div>

					{:else if settingsSection === 'data'}
					<!-- Data Management -->
					<div class="p-6 bg-slate-900/40 border border-white/5 rounded-2xl space-y-5">
						<div class="flex items-center gap-2">
							<HardDrive size={18} class="text-blue-400" />
							<h3 class="text-lg font-semibold">Data Management</h3>
						</div>

						<div class="grid grid-cols-3 gap-2" aria-label="Library usage">
							<div class="p-3 bg-white/5 rounded-xl">
								<p class="text-xl font-semibold text-slate-100">{$historyStore.length}</p>
								<p class="text-[10px] text-slate-500 uppercase tracking-wider">History items</p>
							</div>
							<div class="p-3 bg-white/5 rounded-xl">
								<p class="text-xl font-semibold text-slate-100">{libraryStats.audioEntries}</p>
								<p class="text-[10px] text-slate-500 uppercase tracking-wider">Cached audio</p>
							</div>
							<div class="p-3 bg-white/5 rounded-xl">
								<p class="text-xl font-semibold text-slate-100">{libraryStats.available ? formatBytes(libraryStats.audioBytes) : '—'}</p>
								<p class="text-[10px] text-slate-500 uppercase tracking-wider">Audio storage</p>
							</div>
						</div>

						<div class="space-y-2">
							<label for="history-retention" class="text-xs font-bold text-slate-500 uppercase tracking-widest px-1">History retention</label>
							<select
								id="history-retention"
								value={$settingsStore.cleanupIntervalDays}
								onchange={(e) => updateRetention(Number(e.currentTarget.value))}
								class="w-full bg-slate-900 border border-white/10 rounded-xl p-3 text-sm appearance-none focus:outline-none focus:ring-1 focus:ring-blue-500"
							>
								<option value="7">After 1 week</option>
								<option value="14">After 2 weeks</option>
								<option value="30">After 1 month</option>
								<option value="90">After 3 months</option>
								<option value="0">Never</option>
							</select>
							<p class="text-[10px] text-slate-600 px-1">
								Removes expired History items and their cached audio on launch. “Never” keeps them until you delete them.
							</p>
						</div>

						<div class="p-4 bg-white/5 rounded-xl space-y-3">
							<div>
								<p class="text-sm font-medium text-slate-200">Portable library backup</p>
								<p class="text-xs text-slate-500 mt-1">Includes History text, titles, voices, and portable preferences. Generated audio is a cache and is not included.</p>
							</div>
							<div class="grid sm:grid-cols-2 gap-2">
							<button
								onclick={downloadLibraryBackup}
								class="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-500 rounded-xl text-sm font-medium transition-colors"
							>
								<FileDown size={16} />
								Back up library
							</button>
							<button
								onclick={() => restoreInput?.click()}
								class="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-slate-800 hover:bg-slate-700 border border-white/10 rounded-xl text-sm transition-colors"
							>
								<FileUp size={16} />
								Restore backup
							</button>
							<input bind:this={restoreInput} class="hidden" type="file" accept="application/json,.json" onchange={restoreLibrary} aria-label="Choose library backup" />
							</div>
							{#if dataStatus}
								<p role="status" class="text-xs {dataStatusKind === 'error' ? 'text-red-400' : dataStatusKind === 'success' ? 'text-emerald-400' : 'text-slate-400'}">{dataStatus}</p>
							{/if}
						</div>
					</div>

					{:else}
					<div class="space-y-4">
					<!-- About -->
					<div class="p-6 bg-slate-900/40 border border-white/5 rounded-2xl space-y-4">
						<div class="flex items-center gap-2">
							<Info size={18} class="text-blue-400" />
							<h3 class="text-lg font-semibold">About</h3>
						</div>
						<div class="space-y-3 text-sm text-slate-400">
							<div class="flex justify-between">
								<span>App</span>
								<span class="text-slate-200">Open Mobile TTS</span>
							</div>
							<div class="flex justify-between">
								<span>Version</span>
								<span class="text-slate-200">{appVersion}</span>
							</div>
							<div class="flex justify-between">
								<span>Engine</span>
								<span class="text-slate-200">{activeEngineName()}</span>
							</div>
							<div class="flex justify-between">
								<span>Architecture</span>
								<span class="text-slate-200">Single-app (no auth)</span>
							</div>
							<div class="flex justify-between">
								<span>Audio Format</span>
								<span class="text-slate-200">{capabilities?.platform === 'android' ? '64kbps AAC, 24kHz mono' : '64kbps CBR MP3, 22050Hz mono'}</span>
							</div>
							<div class="flex justify-between">
								<span>Max Chunk</span>
								<span class="text-slate-200">250 tokens (~175 words)</span>
							</div>
						</div>
					</div>

					<!-- Export Logs -->
					<div class="p-6 bg-slate-900/40 border border-white/5 rounded-2xl space-y-4">
						<div class="flex items-center gap-2">
							<FileDown size={18} class="text-blue-400" />
							<h3 class="text-lg font-semibold">Export Logs</h3>
						</div>
						<p class="text-sm text-slate-400">
							Download server logs for bug reports. Text content is redacted by default; errors and timing remain available.
						</p>
						<div class="flex gap-3">
							<button
								onclick={exportLogs}
								disabled={exportingLogs}
								class="flex items-center gap-2 px-4 py-2.5 bg-blue-600 hover:bg-blue-500 disabled:bg-blue-600/50 rounded-xl text-sm font-medium transition-colors"
							>
								{#if exportingLogs}
									<Loader2 size={16} class="animate-spin" />
									Exporting...
								{:else}
									<FileDown size={16} />
									Export Logs
								{/if}
							</button>
							<button
								onclick={async () => {
									logsClearStatus = 'clearing';
									try {
										localStorage.removeItem('openmobiletts_logs');
										const res = await fetch(apiUrl('/api/logs/clear'), { method: 'POST' });
										logsClearStatus = res.ok ? 'cleared' : 'failed';
									} catch {
										logsClearStatus = 'failed';
									}
									setTimeout(() => { logsClearStatus = ''; }, 2000);
								}}
								disabled={logsClearStatus === 'clearing'}
								class="flex items-center gap-2 px-4 py-2.5 bg-red-600/20 hover:bg-red-600/30 text-red-400 rounded-xl text-sm font-medium transition-colors disabled:opacity-50"
							>
								{#if logsClearStatus === 'clearing'}
									<Loader2 size={16} class="animate-spin" />
									Clearing...
								{:else if logsClearStatus === 'cleared'}
									<CheckCircle size={16} class="text-emerald-400" />
									<span class="text-emerald-400">Cleared!</span>
								{:else if logsClearStatus === 'failed'}
									<AlertTriangle size={16} />
									Failed
								{:else}
									<Trash2 size={16} />
									Clear Logs
								{/if}
							</button>
						</div>
					</div>

					{#if isIOS}
						<div class="p-4 bg-yellow-500/10 border border-yellow-500/20 rounded-2xl">
							<p class="text-sm text-yellow-400">
								iOS Limitation: Audio will stop when app is minimized or screen locks. Keep app in foreground during playback.
							</p>
						</div>
					{/if}
					</div>
					{/if}
						</section>
					</div>
				</div>
			{/if}
		</div>

		<!-- BOTTOM PLAYER BAR -->
		<AudioPlayer bind:expanded={playerExpanded} onopen={handleExpandedOpen} onclose={handleExpandedClose} />

		<!-- MOBILE NAVIGATION BAR -->
		<nav class="md:hidden fixed bottom-0 left-0 right-0 h-[72px] bg-[#0d1117] border-t border-white/5 flex items-center px-2 z-[55]">
			<button
				onclick={() => switchTab('generate')}
				class="flex flex-col items-center gap-1 flex-1 py-2 transition-all duration-200 {activeTab === 'generate' ? 'text-blue-400' : 'text-slate-500'}"
			>
				<Plus size={20} />
				<span class="text-[10px] font-medium">Generate</span>
			</button>
			<button
				onclick={() => switchTab('history')}
				class="flex flex-col items-center gap-1 flex-1 py-2 transition-all duration-200 {activeTab === 'history' ? 'text-blue-400' : 'text-slate-500'}"
			>
				<History size={20} />
				<span class="text-[10px] font-medium">History</span>
			</button>
			<button
				onclick={() => switchTab('settings')}
				class="flex flex-col items-center gap-1 flex-1 py-2 transition-all duration-200 {activeTab === 'settings' ? 'text-blue-400' : 'text-slate-500'}"
			>
				<Settings size={20} />
				<span class="text-[10px] font-medium">Settings</span>
			</button>
		</nav>
	</main>
</div>

<!-- "New" Confirmation Modal -->
{#if showNewConfirm}
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div
		class="fixed inset-0 bg-black/60 backdrop-blur-sm z-[60] flex items-center justify-center p-4"
		onclick={(e) => { if (e.target === e.currentTarget) showNewConfirm = false; }}
		onkeydown={(e) => { if (e.key === 'Escape') showNewConfirm = false; }}
	>
		<div role="dialog" aria-modal="true" aria-labelledby="new-confirm-title" class="bg-[#0f1218] border border-white/10 rounded-2xl p-6 max-w-sm w-full shadow-2xl">
			<div class="flex items-center gap-3 mb-4">
				<div class="w-10 h-10 bg-amber-500/10 rounded-xl flex items-center justify-center">
					<AlertTriangle size={20} class="text-amber-400" />
				</div>
				<h3 id="new-confirm-title" class="text-lg font-semibold text-slate-200">Unsaved Text</h3>
			</div>
			<p class="text-sm text-slate-400 mb-6">
				You have text in the editor. Would you like to save it to history before starting fresh?
			</p>
			<div class="flex flex-col gap-2">
				<button
					onclick={() => {
						draftStore.saveAsNote($settingsStore.defaultVoice);
						draftStore.clear();
						playerStore.stop();
						showNewConfirm = false;
					}}
					class="w-full px-4 py-2.5 bg-blue-600 hover:bg-blue-500 rounded-xl text-sm font-medium transition-colors"
				>
					Save to History & Start New
				</button>
				<button
					onclick={() => {
						draftStore.clear();
						playerStore.stop();
						showNewConfirm = false;
					}}
					class="w-full px-4 py-2.5 bg-red-600/20 hover:bg-red-600/30 text-red-400 rounded-xl text-sm font-medium transition-colors"
				>
					Discard & Start New
				</button>
				<button
					onclick={() => showNewConfirm = false}
					class="w-full px-4 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors"
				>
					Cancel
				</button>
			</div>
		</div>
	</div>
{/if}
