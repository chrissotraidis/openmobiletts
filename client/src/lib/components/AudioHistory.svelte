<script>
	import { historyStore } from '$lib/stores/history';
	import { playerStore, PlayState } from '$lib/stores/player';
	import { playlistStore } from '$lib/stores/playlist';
	import { settingsStore } from '$lib/stores/settings';
	import { getCachedAudio, cacheAudio, getCachedIds } from '$lib/services/audioCache';
	import { apiUrl } from '$lib/services/api';
	import TextDisplay from '$lib/components/TextDisplay.svelte';
	import { Play, Trash2, Clock, Volume2, Loader2, AlertTriangle, Download, ListPlus, Check, ArrowLeft, BookOpen, CircleCheck, CircleDashed } from 'lucide-svelte';
	import { onMount, onDestroy } from 'svelte';

	let history = $state([]);
	let showClearModal = $state(false);
	let showDeleteModal = $state(false);
	let pendingDeleteEntry = $state(null);
	let loadingEntryId = $state(null);
	let downloadingEntryIds = $state(new Set());
	let playerState = $state(PlayState.IDLE);
	let queuedIds = $state(new Set());
	let viewingEntry = $state(null); // When set, shows reader/detail view
	let cachedIds = $state(new Set()); // Track which entries have cached audio
	let activeHistoryId = $state(null); // History entry currently being generated

	const unsubs = [
		historyStore.subscribe((h) => (history = h)),
		playlistStore.items.subscribe((items) => {
			queuedIds = new Set(items.map((e) => e.id));
		}),
		playerStore.state.subscribe((s) => {
			const prev = playerState;
			playerState = s;
			// Clear loading state when playback starts or errors
			if (s === PlayState.PLAYING || s === PlayState.PAUSED || s === PlayState.ERROR) {
				loadingEntryId = null;
			}
			// Refresh cache status when generation completes (new audio may have been cached)
			if (prev === PlayState.GENERATING && (s === PlayState.PLAYING || s === PlayState.PAUSED)) {
				refreshCacheStatus();
			}
		}),
		playerStore.currentHistoryId.subscribe((id) => (activeHistoryId = id)),
	];

	async function refreshCacheStatus() {
		cachedIds = await getCachedIds();
	}

	// Android back button / gesture support for reader view
	function handlePopState() {
		if (viewingEntry) {
			viewingEntry = null;
		}
	}

	onMount(() => {
		refreshCacheStatus();
		window.addEventListener('popstate', handlePopState);
	});

	onDestroy(() => {
		unsubs.forEach((u) => u());
		window.removeEventListener('popstate', handlePopState);
	});

	const voiceDisplayNames = {
		af_heart: 'Heart',
		af_nova: 'Nova',
		af_sky: 'Sky',
		af_bella: 'Bella',
		af_sarah: 'Sarah',
		am_adam: 'Adam',
		am_michael: 'Michael',
		bf_emma: 'Emma',
		bf_isabella: 'Isabella',
		bm_george: 'George',
		bm_lewis: 'Lewis',
	};

	function playEntry(entry) {
		// Don't restart if this entry is already being generated
		if (activeHistoryId === entry.id && playerState === PlayState.GENERATING) return;
		loadingEntryId = entry.id;
		// Uses cache if available, otherwise regenerates
		playerStore.playFromHistory(entry, $settingsStore.autoPlay);
	}

	function addToQueue(entry) {
		playlistStore.add(entry);
	}

	function openReader(entry) {
		viewingEntry = entry;
		// Push browser history state so Android back button returns to the list
		history.pushState({ tab: 'history', view: 'reader' }, '');

		// Don't trigger playback if this entry is already being generated
		if (activeHistoryId === entry.id && playerState === PlayState.GENERATING) return;

		// Only auto-play if audio is cached — don't trigger expensive on-device regeneration
		if (cachedIds.has(entry.id)) {
			loadingEntryId = entry.id;
			playerStore.playFromHistory(entry, $settingsStore.autoPlay);
		}
	}

	function closeReader() {
		viewingEntry = null;
	}

	function handleClearAll() {
		historyStore.clear();
		showClearModal = false;
	}

	function confirmDelete(entry) {
		pendingDeleteEntry = entry;
		showDeleteModal = true;
	}

	function handleDelete() {
		if (pendingDeleteEntry) {
			historyStore.remove(pendingDeleteEntry.id);
			// If we were viewing this entry, go back to the list
			if (viewingEntry && viewingEntry.id === pendingDeleteEntry.id) {
				viewingEntry = null;
			}
		}
		showDeleteModal = false;
		pendingDeleteEntry = null;
	}

	async function downloadEntry(entry) {
		// Track this download (allow multiple concurrent downloads)
		downloadingEntryIds = new Set([...downloadingEntryIds, entry.id]);

		try {
			// Try cache first
			let cached = await getCachedAudio(entry.id);
			let blob = cached?.blob;

			// If not cached, regenerate the audio
			if (!blob) {
				const response = await fetch(apiUrl('/api/tts/stream'), {
					method: 'POST',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify({
						text: entry.text,
						voice: entry.voice,
						speed: entry.speed || 1.0,
					}),
				});

				if (!response.ok) {
					throw new Error('Failed to generate audio');
				}

				// Parse streaming response to extract audio chunks
				const reader = response.body.getReader();
				const audioChunks = [];
				const timingData = [];
				let buffer = new Uint8Array(0);
				let pendingAudioBytes = 0;

				while (true) {
					const { done, value } = await reader.read();
					if (done) break;

					const combined = new Uint8Array(buffer.length + value.length);
					combined.set(buffer);
					combined.set(value, buffer.length);
					buffer = combined;

					while (buffer.length > 0) {
						if (pendingAudioBytes > 0) {
							if (buffer.length < pendingAudioBytes) break;
							audioChunks.push(buffer.slice(0, pendingAudioBytes));
							buffer = buffer.slice(pendingAudioBytes);
							pendingAudioBytes = 0;
							continue;
						}

						const newlineIdx = buffer.indexOf(10);
						if (newlineIdx === -1) break;

						const line = new TextDecoder().decode(buffer.slice(0, newlineIdx));
						buffer = buffer.slice(newlineIdx + 1);

						if (line.startsWith('TIMING:')) {
							timingData.push(JSON.parse(line.slice(7)));
						} else if (line.startsWith('AUDIO:')) {
							pendingAudioBytes = parseInt(line.slice(6), 10);
						}
					}
				}

				// Handle any remaining buffered audio data
				if (pendingAudioBytes > 0 && buffer.length >= pendingAudioBytes) {
					audioChunks.push(buffer.slice(0, pendingAudioBytes));
				}

				// Detect audio type from first chunk header
				let audioType = 'audio/mpeg';
				if (audioChunks.length > 0 && audioChunks[0].length >= 2) {
					const h = audioChunks[0];
					if (h.length >= 4 && h[0] === 0x52 && h[1] === 0x49 && h[2] === 0x46 && h[3] === 0x46) {
						audioType = 'audio/wav';
					} else if (h[0] === 0xFF && (h[1] & 0xF6) === 0xF0) {
						audioType = 'audio/aac';
					}
				}
				// WAV chunks each have a 44-byte header and need proper merging;
				// AAC ADTS and MP3 are directly concatenatable.
				if (audioType === 'audio/wav' && audioChunks.length > 1) {
					let totalPcmSize = 0;
					for (const c of audioChunks) totalPcmSize += c.length - 44;
					const merged = new Uint8Array(44 + totalPcmSize);
					merged.set(audioChunks[0].subarray(0, 44));
					const fileSize = 36 + totalPcmSize;
					merged[4] = fileSize & 0xff; merged[5] = (fileSize >> 8) & 0xff;
					merged[6] = (fileSize >> 16) & 0xff; merged[7] = (fileSize >> 24) & 0xff;
					merged[40] = totalPcmSize & 0xff; merged[41] = (totalPcmSize >> 8) & 0xff;
					merged[42] = (totalPcmSize >> 16) & 0xff; merged[43] = (totalPcmSize >> 24) & 0xff;
					let off = 44;
					for (const c of audioChunks) { const pcm = c.subarray(44); merged.set(pcm, off); off += pcm.length; }
					blob = new Blob([merged], { type: 'audio/wav' });
				} else {
					blob = new Blob(audioChunks, { type: audioType });
				}

				// Cache for future use
				await cacheAudio(entry.id, blob, timingData);
				refreshCacheStatus();
			}

			// Trigger download (uses native bridge on Android, blob URL on desktop)
			const date = new Date(entry.createdAt).toISOString().slice(0, 10);
			const voice = voiceDisplayNames[entry.voice] || entry.voice;
			const ext = blob.type === 'audio/wav' ? 'wav' : blob.type === 'audio/aac' ? 'aac' : 'mp3';
			const filename = `tts-${date}-${voice.toLowerCase()}-${entry.speed?.toFixed(1) || '1.0'}x.${ext}`;

			await playerStore.downloadAudio(blob, filename);
		} catch (err) {
			console.error('Download failed:', err);
		} finally {
			// Remove from downloading set
			const newSet = new Set(downloadingEntryIds);
			newSet.delete(entry.id);
			downloadingEntryIds = newSet;
		}
	}

	function formatDate(iso) {
		const d = new Date(iso);
		const now = new Date();
		const diff = now - d;

		if (diff < 60000) return 'Just now';
		if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
		if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
		return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
	}
</script>

<!-- Clear All Confirmation Modal -->
{#if showClearModal}
	<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
	<div class="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4" onclick={() => showClearModal = false}>
		<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
		<div class="bg-[#0f1218] border border-white/10 rounded-2xl p-6 max-w-sm w-full shadow-2xl" onclick={(e) => e.stopPropagation()}>
			<div class="flex items-center gap-3 mb-4">
				<div class="w-10 h-10 bg-red-500/10 rounded-xl flex items-center justify-center">
					<AlertTriangle size={20} class="text-red-400" />
				</div>
				<h3 class="text-lg font-semibold text-slate-200">Clear History</h3>
			</div>
			<p class="text-sm text-slate-400 mb-6">
				This will permanently delete all {history.length} generation{history.length !== 1 ? 's' : ''} and their cached audio. This action cannot be undone.
			</p>
			<div class="flex gap-3">
				<button
					onclick={() => showClearModal = false}
					class="flex-1 px-4 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors"
				>
					Cancel
				</button>
				<button
					onclick={handleClearAll}
					class="flex-1 px-4 py-2.5 bg-red-600 hover:bg-red-500 rounded-xl text-sm font-medium transition-colors"
				>
					Clear All
				</button>
			</div>
		</div>
	</div>
{/if}

<!-- Delete Single Entry Confirmation Modal -->
{#if showDeleteModal && pendingDeleteEntry}
	<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
	<div class="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4" onclick={() => { showDeleteModal = false; pendingDeleteEntry = null; }}>
		<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
		<div class="bg-[#0f1218] border border-white/10 rounded-2xl p-6 max-w-sm w-full shadow-2xl" onclick={(e) => e.stopPropagation()}>
			<div class="flex items-center gap-3 mb-4">
				<div class="w-10 h-10 bg-red-500/10 rounded-xl flex items-center justify-center">
					<AlertTriangle size={20} class="text-red-400" />
				</div>
				<h3 class="text-lg font-semibold text-slate-200">Delete Generation?</h3>
			</div>
			<p class="text-sm text-slate-400 mb-2">
				This will permanently delete this generation and its cached audio.
			</p>
			<p class="text-xs text-slate-500 mb-6 line-clamp-2">
				"{pendingDeleteEntry.preview}"
			</p>
			<div class="flex gap-3">
				<button
					onclick={() => { showDeleteModal = false; pendingDeleteEntry = null; }}
					class="flex-1 px-4 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors"
				>
					Cancel
				</button>
				<button
					onclick={handleDelete}
					class="flex-1 px-4 py-2.5 bg-red-600 hover:bg-red-500 rounded-xl text-sm font-medium transition-colors"
				>
					Delete
				</button>
			</div>
		</div>
	</div>
{/if}

{#if viewingEntry}
	<!-- READER / DETAIL VIEW -->
	<div class="space-y-4">
		<!-- Header -->
		<div class="flex items-center gap-3">
			<button
				onclick={closeReader}
				class="w-9 h-9 bg-white/5 hover:bg-white/10 rounded-lg flex items-center justify-center transition-colors"
			>
				<ArrowLeft size={16} class="text-slate-400" />
			</button>
			<div class="flex-1 min-w-0">
				<h3 class="text-sm font-semibold text-slate-300 truncate">{viewingEntry.preview}</h3>
				<div class="flex items-center gap-3 mt-0.5">
					<span class="text-[10px] text-slate-500 flex items-center gap-1">
						<Volume2 size={10} />
						{voiceDisplayNames[viewingEntry.voice] || viewingEntry.voice}
					</span>
					<span class="text-[10px] text-slate-600 font-mono">{viewingEntry.speed?.toFixed(1) || '1.0'}x</span>
					<span class="text-[10px] text-slate-600">{formatDate(viewingEntry.createdAt)}</span>
				</div>
			</div>
		</div>

		<!-- Cache status notice -->
		{#if activeHistoryId === viewingEntry.id && playerState === PlayState.GENERATING}
			<div class="flex items-center gap-2 px-3 py-2 bg-blue-500/5 border border-blue-500/15 rounded-xl">
				<Loader2 size={14} class="text-blue-400 shrink-0 animate-spin" />
				<p class="text-xs text-blue-400/80">Audio is being generated — check progress below</p>
			</div>
		{:else if !cachedIds.has(viewingEntry.id)}
			<div class="flex items-center gap-2 px-3 py-2 bg-amber-500/5 border border-amber-500/15 rounded-xl">
				<CircleDashed size={14} class="text-amber-400 shrink-0" />
				<p class="text-xs text-amber-400/80">Audio not cached — playing will regenerate from text</p>
			</div>
		{/if}

		<!-- Play / Queue / Download controls -->
		<div class="flex items-center gap-2">
			<button
				onclick={() => playEntry(viewingEntry)}
				disabled={loadingEntryId === viewingEntry.id || (activeHistoryId === viewingEntry.id && playerState === PlayState.GENERATING)}
				class="btn btn-primary flex items-center gap-2 text-sm px-4 py-2.5"
			>
				{#if (activeHistoryId === viewingEntry.id && playerState === PlayState.GENERATING) || (loadingEntryId === viewingEntry.id && playerState === PlayState.GENERATING)}
					<Loader2 size={14} class="animate-spin" />
					<span>Generating...</span>
				{:else if cachedIds.has(viewingEntry.id)}
					<Play size={14} />
					<span>Play</span>
				{:else}
					<Play size={14} />
					<span>Generate & Play</span>
				{/if}
			</button>
			<button
				onclick={() => addToQueue(viewingEntry)}
				disabled={queuedIds.has(viewingEntry.id)}
				class="flex items-center gap-2 text-sm px-4 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl transition-colors disabled:opacity-50"
			>
				{#if queuedIds.has(viewingEntry.id)}
					<Check size={14} class="text-emerald-400" />
					<span class="text-emerald-400">In Queue</span>
				{:else}
					<ListPlus size={14} />
					<span>Add to Queue</span>
				{/if}
			</button>
			<button
				onclick={() => downloadEntry(viewingEntry)}
				disabled={downloadingEntryIds.has(viewingEntry.id)}
				class="flex items-center gap-2 text-sm px-4 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl transition-colors disabled:opacity-50"
			>
				{#if downloadingEntryIds.has(viewingEntry.id)}
					<Loader2 size={14} class="animate-spin" />
				{:else}
					<Download size={14} />
				{/if}
				<span>Download</span>
			</button>
		</div>

		<!-- Text Display with synchronized highlighting and click-to-seek -->
		<TextDisplay />
	</div>
{:else if history.length === 0}
	<div class="flex flex-col items-center justify-center py-20 text-center">
		<div class="w-16 h-16 bg-white/5 rounded-2xl flex items-center justify-center mb-4">
			<Clock size={28} class="text-slate-600" />
		</div>
		<p class="text-slate-500 text-sm mb-1">No history yet</p>
		<p class="text-slate-600 text-xs">Generated speech will appear here</p>
	</div>
{:else}
	<div class="space-y-2">
		<div class="flex items-center justify-between mb-4">
			<h3 class="text-sm font-semibold text-slate-400">{history.length} generation{history.length !== 1 ? 's' : ''}</h3>
			<button
				onclick={() => showClearModal = true}
				class="text-[10px] text-slate-600 hover:text-red-400 transition-colors"
			>
				Clear all
			</button>
		</div>

		{#each history as entry (entry.id)}
			{@const isLoading = loadingEntryId === entry.id}
			{@const hasCachedAudio = cachedIds.has(entry.id)}
			{@const isGenerating = activeHistoryId === entry.id && playerState === PlayState.GENERATING}
			<div class="group p-4 bg-slate-900/40 border border-white/5 rounded-xl hover:bg-slate-900/60 transition-colors {isLoading || isGenerating ? 'border-blue-500/30' : ''}">
				<div class="flex items-start gap-3">
					<!-- Play button -->
					<button
						onclick={() => playEntry(entry)}
						disabled={isLoading || isGenerating}
						class="w-9 h-9 rounded-lg flex items-center justify-center transition-colors shrink-0 mt-0.5 {isLoading || isGenerating ? 'bg-blue-600' : hasCachedAudio ? 'bg-blue-600/10 hover:bg-blue-600' : 'bg-slate-700/30 hover:bg-slate-600/50'}"
						title={isGenerating ? 'Currently generating...' : hasCachedAudio ? 'Play from cache' : 'Generate & play'}
					>
						{#if isLoading || isGenerating}
							<Loader2 size={14} class="text-white animate-spin" />
						{:else}
							<Play size={14} class="{hasCachedAudio ? 'text-blue-400 group-hover:text-white' : 'text-slate-500 group-hover:text-slate-300'} ml-0.5" />
						{/if}
					</button>

					<!-- Content (clickable to open reader) -->
					<button
						onclick={() => openReader(entry)}
						class="flex-1 min-w-0 text-left"
					>
						<p class="text-sm text-slate-300 line-clamp-2 leading-relaxed">{entry.preview}</p>
						<div class="flex items-center gap-3 mt-2 flex-wrap">
							<span class="text-[10px] text-slate-500 flex items-center gap-1">
								<Volume2 size={10} />
								{voiceDisplayNames[entry.voice] || entry.voice}
							</span>
							<span class="text-[10px] text-slate-600 font-mono">{entry.speed?.toFixed(1) || '1.0'}x</span>
							<span class="text-[10px] text-slate-600">{formatDate(entry.createdAt)}</span>
							{#if isGenerating}
								<span class="text-[10px] text-blue-400 flex items-center gap-1">
									<Loader2 size={10} class="animate-spin" />
									Generating...
								</span>
							{:else if isLoading}
								<span class="text-[10px] text-blue-400">Loading...</span>
							{:else if hasCachedAudio}
								<span class="text-[10px] text-emerald-500 flex items-center gap-1">
									<CircleCheck size={10} />
									Audio ready
								</span>
							{:else}
								<span class="text-[10px] text-slate-600 flex items-center gap-1">
									<CircleDashed size={10} />
									Text only
								</span>
							{/if}
						</div>
					</button>

					<!-- Reader mode button -->
					<button
						onclick={() => openReader(entry)}
						class="p-1.5 text-slate-700 hover:text-blue-400 transition-colors opacity-0 group-hover:opacity-100"
						title="Open reader"
					>
						<BookOpen size={14} />
					</button>

					<!-- Add to Queue -->
					<button
						onclick={() => addToQueue(entry)}
						disabled={queuedIds.has(entry.id)}
						class="p-1.5 transition-colors opacity-0 group-hover:opacity-100 disabled:opacity-100 {queuedIds.has(entry.id) ? 'text-emerald-400' : 'text-slate-700 hover:text-emerald-400'}"
						title={queuedIds.has(entry.id) ? 'In queue' : 'Add to queue'}
					>
						{#if queuedIds.has(entry.id)}
							<Check size={14} />
						{:else}
							<ListPlus size={14} />
						{/if}
					</button>

					<!-- Download -->
					<button
						onclick={() => downloadEntry(entry)}
						disabled={downloadingEntryIds.has(entry.id)}
						class="p-1.5 text-slate-700 hover:text-blue-400 transition-colors opacity-0 group-hover:opacity-100 disabled:opacity-100 disabled:text-blue-400"
						title="Download audio"
					>
						{#if downloadingEntryIds.has(entry.id)}
							<Loader2 size={14} class="animate-spin" />
						{:else}
							<Download size={14} />
						{/if}
					</button>

					<!-- Delete -->
					<button
						onclick={() => confirmDelete(entry)}
						class="p-1.5 text-slate-700 hover:text-red-400 transition-colors opacity-0 group-hover:opacity-100"
						title="Remove"
					>
						<Trash2 size={14} />
					</button>
				</div>
			</div>
		{/each}
	</div>
{/if}

<style>
	.line-clamp-2 {
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}
</style>
