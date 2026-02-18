<script>
	import { historyStore } from '$lib/stores/history';
	import { playerStore, PlayState } from '$lib/stores/player';
	import { settingsStore } from '$lib/stores/settings';
	import { getCachedAudio, cacheAudio } from '$lib/services/audioCache';
	import { apiUrl } from '$lib/services/api';
	import { Play, Trash2, Clock, Volume2, Loader2, AlertTriangle, Download } from 'lucide-svelte';
	import { onDestroy } from 'svelte';

	let history = $state([]);
	let showClearModal = $state(false);
	let showDeleteModal = $state(false);
	let pendingDeleteEntry = $state(null);
	let loadingEntryId = $state(null);
	let downloadingEntryIds = $state(new Set());
	let playerState = $state(PlayState.IDLE);

	const unsubs = [
		historyStore.subscribe((h) => (history = h)),
		playerStore.state.subscribe((s) => {
			playerState = s;
			// Clear loading state when playback starts or errors
			if (s === PlayState.PLAYING || s === PlayState.PAUSED || s === PlayState.ERROR) {
				loadingEntryId = null;
			}
		}),
	];
	onDestroy(() => unsubs.forEach((u) => u()));

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
		loadingEntryId = entry.id;
		// Uses cache if available, otherwise regenerates
		playerStore.playFromHistory(entry, $settingsStore.autoPlay);
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

				blob = new Blob(audioChunks, { type: 'audio/mpeg' });

				// Cache for future use
				await cacheAudio(entry.id, blob, timingData);
			}

			// Trigger download
			const date = new Date(entry.createdAt).toISOString().slice(0, 10);
			const voice = voiceDisplayNames[entry.voice] || entry.voice;
			const filename = `tts-${date}-${voice.toLowerCase()}-${entry.speed?.toFixed(1) || '1.0'}x.mp3`;

			const url = URL.createObjectURL(blob);
			const a = document.createElement('a');
			a.href = url;
			a.download = filename;
			document.body.appendChild(a);
			a.click();
			document.body.removeChild(a);
			// Delay URL revocation to ensure browser has time to start download
			setTimeout(() => URL.revokeObjectURL(url), 1000);
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

{#if history.length === 0}
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
			<div class="group p-4 bg-slate-900/40 border border-white/5 rounded-xl hover:bg-slate-900/60 transition-colors {isLoading ? 'border-blue-500/30' : ''}">
				<div class="flex items-start gap-3">
					<!-- Play button -->
					<button
						onclick={() => playEntry(entry)}
						disabled={isLoading}
						class="w-9 h-9 bg-blue-600/10 hover:bg-blue-600 rounded-lg flex items-center justify-center transition-colors shrink-0 mt-0.5 {isLoading ? 'bg-blue-600' : ''}"
					>
						{#if isLoading}
							<Loader2 size={14} class="text-white animate-spin" />
						{:else}
							<Play size={14} class="text-blue-400 group-hover:text-white ml-0.5" />
						{/if}
					</button>

					<!-- Content -->
					<div class="flex-1 min-w-0">
						<p class="text-sm text-slate-300 line-clamp-2 leading-relaxed">{entry.preview}</p>
						<div class="flex items-center gap-3 mt-2">
							<span class="text-[10px] text-slate-500 flex items-center gap-1">
								<Volume2 size={10} />
								{voiceDisplayNames[entry.voice] || entry.voice}
							</span>
							<span class="text-[10px] text-slate-600 font-mono">{entry.speed?.toFixed(1) || '1.0'}x</span>
							<span class="text-[10px] text-slate-600">{formatDate(entry.createdAt)}</span>
							{#if isLoading}
								<span class="text-[10px] text-blue-400">Loading...</span>
							{/if}
						</div>
					</div>

					<!-- Download -->
					<button
						onclick={() => downloadEntry(entry)}
						disabled={downloadingEntryIds.has(entry.id)}
						class="p-1.5 text-slate-700 hover:text-blue-400 transition-colors opacity-0 group-hover:opacity-100 disabled:opacity-100 disabled:text-blue-400"
						title="Download MP3"
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
