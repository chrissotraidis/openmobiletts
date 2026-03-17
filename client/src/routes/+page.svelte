<script>
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import AudioPlayer from '$lib/components/AudioPlayer.svelte';
	import TextInput from '$lib/components/TextInput.svelte';
	import TextDisplay from '$lib/components/TextDisplay.svelte';
	import AudioHistory from '$lib/components/AudioHistory.svelte';
	import GenerationProgress from '$lib/components/GenerationProgress.svelte';
	import { settingsStore } from '$lib/stores/settings';
	import { playerStore } from '$lib/stores/player';
	import { apiUrl, healthCheck, fetchVoices, fetchEngines, switchEngine, fetchSttModels } from '$lib/services/api';
	import { Mic, Plus, History, Settings, ShieldCheck, Zap, Volume2, Clock, Sliders, Info, RotateCcw, ChevronDown, FileDown, Download, Loader2, Wifi, CheckCircle, XCircle, Cpu, HardDrive, Trash2 } from 'lucide-svelte';

	let isIOS = $state(false);
	let activeTab = $state('generate');
	let exportingLogs = $state(false);
	let testingConnection = $state(false);
	let connectionStatus = $state(null); // null | 'success' | 'error'
	let connectionMessage = $state('');
	let playerExpanded = $state(false);

	// Engine & voice state
	let engines = $state([]);
	let voices = $state([]);
	let settingsLang = $state('');
	let switchingEngine = $state(false);

	// Storage & STT settings state
	let sttModels = $state([]);
	let sttDownloadProgress = $state('');
	let cleanupInterval = $state('30'); // days

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

	async function loadVoicesAndEngines() {
		try {
			const [v, e] = await Promise.all([fetchVoices(), fetchEngines()]);
			voices = v;
			engines = e;
			const current = voices.find((vv) => vv.name === $settingsStore.defaultVoice);
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
	}

	async function handleEngineSwitch(newEngine) {
		if (switchingEngine) return;
		switchingEngine = true;
		try {
			await switchEngine(newEngine);
			settingsStore.update('engine', newEngine);
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
		// If expanded view is open and we navigated back, close it
		if (playerExpanded && !e.state?.expanded) {
			playerExpanded = false;
			return;
		}
		const tab = e.state?.tab;
		if (tab && tab !== activeTab) {
			activeTab = tab;
		}
	}

	onMount(() => {
		if (browser) {
			isIOS = /iPhone|iPad|iPod/.test(navigator.userAgent);
			loadVoicesAndEngines();
			// Set initial state so back button has somewhere to return to
			history.replaceState({ tab: 'generate' }, '');
			window.addEventListener('popstate', handlePopState);
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

	// Speed slider mapping: 1.0x at center (50%)
	// Position 0-50: 0.5x to 1.0x, Position 50-100: 1.0x to 2.0x
	function speedToSlider(speed) {
		if (speed <= 1.0) {
			return (speed - 0.5) * 100; // 0.5→0, 1.0→50
		} else {
			return 50 + (speed - 1.0) * 50; // 1.0→50, 2.0→100
		}
	}

	function sliderToSpeed(position) {
		if (position <= 50) {
			return 0.5 + (position / 100); // 0→0.5, 50→1.0
		} else {
			return 1.0 + ((position - 50) / 50); // 50→1.0, 100→2.0
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
			<div class="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center shadow-lg shadow-blue-600/20">
				<Mic size={18} class="text-white" />
			</div>
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
					class="md:hidden w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center hover:opacity-80 transition-opacity"
				>
					<Mic size={16} class="text-white" />
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
		</header>

		<!-- SCROLLABLE VIEW -->
		<div class="flex-1 overflow-y-auto overflow-x-hidden p-4 md:p-8 custom-scrollbar">
			{#if activeTab === 'generate'}
				<div class="max-w-4xl mx-auto space-y-6 md:space-y-8">
					<TextInput />
					<GenerationProgress />
					<TextDisplay />
				</div>
			{:else if activeTab === 'history'}
				<div class="max-w-4xl mx-auto">
					<AudioHistory />
				</div>
			{:else if activeTab === 'settings'}
				<div class="max-w-4xl mx-auto space-y-6 pb-12">
					<!-- TTS Defaults -->
					<div class="p-6 bg-slate-900/40 border border-white/5 rounded-2xl space-y-5">
						<div class="flex items-center gap-2">
							<Sliders size={18} class="text-blue-400" />
							<h3 class="text-lg font-semibold">TTS Defaults</h3>
						</div>

						<!-- TTS Engine -->
						{#if engines.length > 0}
							<div class="space-y-2">
								<div class="flex items-center gap-2 px-1">
									<Cpu size={14} class="text-slate-500" />
									<span class="text-xs font-bold text-slate-500 uppercase tracking-widest">TTS Engine</span>
								</div>
								<div class="relative">
									<select
										value={engines.find((e) => e.active)?.name || ''}
										onchange={(e) => handleEngineSwitch(e.target.value)}
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
												settingsLang = e.target.value;
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
											onchange={(e) => settingsStore.update('defaultVoice', e.target.value)}
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

						<!-- Default Speed -->
						<div class="space-y-2">
							<div class="flex justify-between items-center px-1">
								<div class="flex items-center gap-2">
									<Clock size={14} class="text-slate-500" />
									<span class="text-xs font-bold text-slate-500 uppercase tracking-widest">Default Speed</span>
								</div>
								<span class="text-xs font-mono text-blue-400">{$settingsStore.defaultSpeed.toFixed(1)}x</span>
							</div>
							<div class="py-3" style="touch-action: manipulation">
								<input
									type="range"
									min="0"
									max="100"
									step="1"
									value={speedToSlider($settingsStore.defaultSpeed)}
									oninput={(e) => settingsStore.update('defaultSpeed', Math.round(sliderToSpeed(parseFloat(e.target.value)) * 10) / 10)}
									class="w-full h-2 accent-blue-500 cursor-pointer"
									style="touch-action: manipulation"
								/>
							</div>
							<div class="flex text-[10px] text-slate-600">
								<span class="flex-1 text-left">0.5x</span>
								<span class="flex-1 text-center">1.0x</span>
								<span class="flex-1 text-right">2.0x</span>
							</div>
						</div>

						<!-- Auto-play toggle -->
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
							onclick={() => settingsStore.reset()}
							class="flex items-center gap-2 text-xs text-slate-500 hover:text-slate-300 transition-colors px-1"
						>
							<RotateCcw size={12} />
							Reset to defaults
						</button>
					</div>

					<!-- Server Connection -->
					<div class="p-6 bg-slate-900/40 border border-white/5 rounded-2xl space-y-5">
						<div class="flex items-center gap-2">
							<Wifi size={18} class="text-blue-400" />
							<h3 class="text-lg font-semibold">Server Connection</h3>
						</div>

						<div class="space-y-2">
							<div class="flex items-center gap-2 px-1">
								<span class="text-xs font-bold text-slate-500 uppercase tracking-widest">Server URL</span>
							</div>
							<input
								type="text"
								value={$settingsStore.serverUrl}
								onchange={(e) => {
									settingsStore.update('serverUrl', e.target.value.trim());
									connectionStatus = null;
								}}
								placeholder="Leave empty for same-origin (default)"
								class="w-full bg-slate-900 border border-white/10 rounded-xl p-3 focus:outline-none focus:ring-1 focus:ring-blue-500 transition-all text-sm placeholder:text-slate-600"
							/>
							<p class="text-[10px] text-slate-600 px-1">
								For Android: enter your computer's IP, e.g. http://192.168.1.100:8000
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
					</div>

					<!-- Storage & STT -->
					<div class="p-6 bg-slate-900/40 border border-white/5 rounded-2xl space-y-5">
						<div class="flex items-center gap-2">
							<HardDrive size={18} class="text-blue-400" />
							<h3 class="text-lg font-semibold">Storage & Speech-to-Text</h3>
						</div>

						<!-- STT Model Status -->
						<div class="space-y-2">
							<span class="text-xs font-bold text-slate-500 uppercase tracking-widest px-1">Speech-to-Text Model</span>
							<p class="text-[10px] text-slate-600 px-1">Required for dictation and audio file transcription.</p>
							{#if sttModels.length > 0}
								{#each sttModels as model}
									<div class="bg-slate-900/60 border border-white/10 rounded-xl px-4 py-3 space-y-2">
										<div class="flex items-center justify-between">
											<div>
												<span class="text-sm text-slate-200">Moonshine v2</span>
												<span class="text-xs text-slate-500 ml-1">({model.size_mb} MB)</span>
											</div>
											{#if model.active}
												<span class="text-xs text-emerald-400 flex items-center gap-1">
													<CheckCircle size={12} /> Ready
												</span>
											{:else if model.downloaded}
												<span class="text-xs text-emerald-400/70 flex items-center gap-1">
													<CheckCircle size={12} /> Downloaded
												</span>
											{:else if model.downloading}
												<span class="text-xs text-blue-400 flex items-center gap-1">
													<Loader2 size={12} class="animate-spin" /> Downloading...
												</span>
											{:else}
												<span class="text-xs text-amber-400">Not installed</span>
											{/if}
										</div>

										{#if model.downloading && sttDownloadProgress}
											<div class="space-y-1">
												<div class="w-full h-1.5 bg-white/5 rounded-full overflow-hidden">
													<div class="h-full bg-blue-500 rounded-full transition-all duration-300 animate-pulse" style="width: 50%"></div>
												</div>
												<p class="text-[10px] text-slate-500">{sttDownloadProgress}</p>
											</div>
										{/if}

										{#if !model.downloaded && !model.active && !model.downloading}
											<button
												onclick={async () => {
													model.downloading = true;
													sttDownloadProgress = 'Starting download...';
													sttModels = [...sttModels];
													try {
														// Trigger background download on server
														const res = await fetch(apiUrl('/api/stt/models/download'), {
															method: 'POST',
															headers: { 'Content-Type': 'application/json' },
															body: JSON.stringify({ model: model.name }),
														});
														if (!res.ok) {
															sttDownloadProgress = 'Download failed. Try again.';
															model.downloading = false;
															sttModels = [...sttModels];
															return;
														}

														const data = await res.json();
														if (data.status === 'already_downloaded') {
															sttDownloadProgress = 'Model already downloaded!';
															const result = await fetchSttModels();
															sttModels = result.models || [];
															model.downloading = false;
															sttModels = [...sttModels];
															return;
														}

														// Poll for download completion
														sttDownloadProgress = `Downloading model (~${model.size_mb} MB)... This may take a few minutes.`;
														let attempts = 0;
														const maxAttempts = 120; // ~10 min at 5s intervals
														while (attempts < maxAttempts) {
															await new Promise(r => setTimeout(r, 5000));
															attempts++;
															try {
																const check = await fetchSttModels();
																const updated = (check.models || []).find(m => m.name === model.name);
																if (updated && (updated.downloaded || updated.active)) {
																	sttDownloadProgress = 'Download complete! Model ready.';
																	sttModels = check.models;
																	model.downloading = false;
																	sttModels = [...sttModels];
																	setTimeout(() => { sttDownloadProgress = ''; }, 3000);
																	return;
																}
																sttDownloadProgress = `Downloading... (checking ${attempts * 5}s)`;
															} catch {
																sttDownloadProgress = `Downloading... (check failed, retrying)`;
															}
														}
														sttDownloadProgress = 'Download timed out. Check Settings later.';
													} catch {
														sttDownloadProgress = 'Download failed. Check your internet connection.';
													} finally {
														model.downloading = false;
														sttModels = [...sttModels];
														setTimeout(() => { sttDownloadProgress = ''; }, 5000);
													}
												}}
												class="w-full flex items-center justify-center gap-2 px-3 py-2 bg-blue-600 hover:bg-blue-500 rounded-lg text-xs font-medium transition-colors"
											>
												<Download size={14} />
												Download Speech-to-Text Model ({model.size_mb} MB)
											</button>
										{/if}
									</div>
								{/each}
							{:else}
								<p class="text-xs text-slate-500 px-1">Checking model status...</p>
							{/if}
						</div>

						<!-- Auto-Cleanup -->
						<div class="space-y-2">
							<span class="text-xs font-bold text-slate-500 uppercase tracking-widest px-1">Auto-Cleanup</span>
							<select
								bind:value={cleanupInterval}
								onchange={async (e) => {
									const days = parseInt(e.target.value);
									if (days > 0) {
										try {
											await fetch(apiUrl('/api/projects/cleanup'), {
												method: 'POST',
												headers: { 'Content-Type': 'application/json' },
												body: JSON.stringify({ older_than_days: days }),
											});
										} catch { /* silent */ }
									}
								}}
								class="w-full bg-slate-900 border border-white/10 rounded-xl p-3 text-sm appearance-none focus:outline-none focus:ring-1 focus:ring-blue-500"
							>
								<option value="7">After 1 week</option>
								<option value="14">After 2 weeks</option>
								<option value="30">After 1 month</option>
								<option value="90">After 3 months</option>
								<option value="0">Never</option>
							</select>
							<p class="text-[10px] text-slate-600 px-1">
								Projects older than this are automatically deleted on app launch.
							</p>
						</div>

						<!-- Backup -->
						<div class="space-y-2">
							<span class="text-xs font-bold text-slate-500 uppercase tracking-widest px-1">Backup</span>
							<p class="text-[10px] text-slate-600 px-1">
								Save all your project text and metadata as a backup file. Does not include audio files.
							</p>
							<button
								onclick={async () => {
									try {
										const res = await fetch(apiUrl('/api/projects/export'));
										if (!res.ok) throw new Error('Export failed');
										const data = await res.json();
										const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
										const url = URL.createObjectURL(blob);
										const a = document.createElement('a');
										a.href = url;
										a.download = `omv-backup-${new Date().toISOString().slice(0,10)}.json`;
										document.body.appendChild(a);
										a.click();
										document.body.removeChild(a);
										setTimeout(() => URL.revokeObjectURL(url), 1000);
									} catch (e) { /* silent */ }
								}}
								class="w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-slate-800 hover:bg-slate-700 border border-white/10 rounded-xl text-sm transition-colors"
							>
								<FileDown size={16} />
								Download Backup
							</button>
						</div>
					</div>

					<!-- About -->
					<div class="p-6 bg-slate-900/40 border border-white/5 rounded-2xl space-y-4">
						<div class="flex items-center gap-2">
							<Info size={18} class="text-blue-400" />
							<h3 class="text-lg font-semibold">About</h3>
						</div>
						<div class="space-y-3 text-sm text-slate-400">
							<div class="flex justify-between">
								<span>App</span>
								<span class="text-slate-200">Open Mobile Voice</span>
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
								<span class="text-slate-200">64kbps CBR MP3, 22050Hz mono</span>
							</div>
							<div class="flex justify-between">
								<span>Max Chunk</span>
								<span class="text-slate-200">250 tokens (~175 words)</span>
							</div>
							<div class="flex justify-between">
								<span>License</span>
								<span class="text-slate-200">Apache 2.0</span>
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
							Download server logs for bug reports. Includes text processing details, errors, and timing information.
						</p>
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
								Export Logs (JSON)
							{/if}
						</button>
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
