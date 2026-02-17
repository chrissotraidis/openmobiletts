<script>
	import { playerStore, PlayState } from '$lib/stores/player';
	import { Play, Pause, Square, Volume2, Gauge, AlertTriangle, Download } from 'lucide-svelte';
	import { onDestroy } from 'svelte';

	let playerState = $state(PlayState.IDLE);
	let currentTime = $state(0);
	let duration = $state(0);
	let voiceName = $state('');
	let speed = $state(1.0);
	let playbackRate = $state(1.0);
	let errorMessage = $state('');
	let showSpeedMenu = $state(false);
	let showStopConfirm = $state(false);

	// Progress tracking during generation
	let genSegments = $state([]);
	let genInputText = $state('');
	let genStartTime = $state(null);
	let genElapsed = $state(0);
	let genIntervalId = null; // NOT $state - just a reference

	const unsubs = [
		playerStore.state.subscribe((s) => {
			playerState = s;
			// Start/stop elapsed timer based on generation state
			if (s === PlayState.GENERATING && !genStartTime) {
				genStartTime = Date.now();
				genIntervalId = setInterval(() => {
					genElapsed = Math.floor((Date.now() - genStartTime) / 1000);
				}, 100);
			} else if (s !== PlayState.GENERATING) {
				genStartTime = null;
				genElapsed = 0;
				if (genIntervalId) {
					clearInterval(genIntervalId);
					genIntervalId = null;
				}
			}
		}),
		playerStore.currentTime.subscribe((t) => (currentTime = t)),
		playerStore.duration.subscribe((d) => (duration = d)),
		playerStore.voice.subscribe((v) => (voiceName = v)),
		playerStore.speed.subscribe((s) => (speed = s)),
		playerStore.playbackRate.subscribe((r) => (playbackRate = r)),
		playerStore.errorMessage.subscribe((e) => (errorMessage = e)),
		playerStore.segments.subscribe((s) => (genSegments = s)),
		playerStore.inputText.subscribe((t) => (genInputText = t)),
	];
	onDestroy(() => {
		unsubs.forEach((u) => u());
		if (genIntervalId) clearInterval(genIntervalId);
	});

	const playbackRates = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3];

	function setPlaybackRate(rate) {
		playerStore.setPlaybackRate(rate);
		showSpeedMenu = false;
	}

	const isVisible = $derived(playerState !== PlayState.IDLE);
	const isPlaying = $derived(playerState === PlayState.PLAYING);
	const isGenerating = $derived(playerState === PlayState.GENERATING);
	const isError = $derived(playerState === PlayState.ERROR);

	const progress = $derived(duration > 0 ? (currentTime / duration) * 100 : 0);

	// Generation progress calculation
	const genChunksReceived = $derived(genSegments.length);
	const genEstimatedTotal = $derived(Math.max(1, Math.ceil(genInputText.length / 700)));
	const genProgressPct = $derived(
		genChunksReceived > 0
			? Math.min(95, Math.round((genChunksReceived / genEstimatedTotal) * 100))
			: 0
	);

	function formatElapsed(seconds) {
		const m = Math.floor(seconds / 60);
		const s = seconds % 60;
		return m > 0 ? `${m}m ${s}s` : `${s}s`;
	}

	function formatTime(seconds) {
		if (!seconds || !isFinite(seconds)) return '0:00';
		const m = Math.floor(seconds / 60);
		const s = Math.floor(seconds % 60);
		return `${m}:${s.toString().padStart(2, '0')}`;
	}

	function togglePlay() {
		if (isPlaying) {
			playerStore.pause();
		} else {
			playerStore.play();
		}
	}

	function handleSeek(event) {
		const rect = event.currentTarget.getBoundingClientRect();
		const pct = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
		playerStore.seek(pct * duration);
	}

	// Friendly voice display name
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

	const displayVoice = $derived(voiceDisplayNames[voiceName] || voiceName);

	function downloadCurrentAudio() {
		const blob = playerStore.getAudioBlob();
		if (!blob) return;

		const filename = `tts-${displayVoice.toLowerCase()}-${speed.toFixed(1)}x.mp3`;
		const url = URL.createObjectURL(blob);
		const a = document.createElement('a');
		a.href = url;
		a.download = filename;
		document.body.appendChild(a);
		a.click();
		document.body.removeChild(a);
		URL.revokeObjectURL(url);
	}
</script>

{#if isVisible}
	<div class="fixed bottom-[72px] md:bottom-0 left-0 md:left-64 right-0 z-30 bg-[#0d1117]/95 backdrop-blur-md border-t border-white/5">
		{#if isError}
			<div class="px-4 py-3 flex items-center gap-3">
				<div class="w-8 h-8 bg-red-500/10 rounded-lg flex items-center justify-center">
					<Volume2 size={16} class="text-red-400" />
				</div>
				<p class="text-sm text-red-400 flex-1">{errorMessage}</p>
				<button
					onclick={() => playerStore.stop()}
					class="p-2 text-slate-400 hover:text-white transition-colors"
				>
					<Square size={16} />
				</button>
			</div>
		{:else}
			<!-- Progress Bar (clickable for playback, animated for generation) -->
			<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
			<div
				class="h-1 bg-white/5 cursor-pointer group"
				onclick={handleSeek}
			>
				{#if isGenerating}
					<div
						class="h-full bg-gradient-to-r from-blue-500 via-purple-500 to-blue-500 transition-all duration-300"
						style="width: {genProgressPct > 0 ? genProgressPct : 3}%"
					></div>
				{:else}
					<div
						class="h-full bg-blue-500 transition-all duration-100 group-hover:bg-blue-400"
						style="width: {progress}%"
					></div>
				{/if}
			</div>

			<div class="px-4 py-2.5 flex items-center gap-3">
				<!-- Play/Pause -->
				{#if isGenerating}
					<div class="w-9 h-9 bg-blue-600/20 rounded-full flex items-center justify-center">
						<div class="w-4 h-4 border-2 border-blue-400 border-t-transparent rounded-full animate-spin"></div>
					</div>
				{:else}
					<button
						onclick={togglePlay}
						class="w-9 h-9 bg-blue-600 hover:bg-blue-500 rounded-full flex items-center justify-center transition-colors"
					>
						{#if isPlaying}
							<Pause size={16} class="text-white" />
						{:else}
							<Play size={16} class="text-white ml-0.5" />
						{/if}
					</button>
				{/if}

				<!-- Time / Generation Progress -->
				<span class="text-[11px] font-mono text-slate-500 min-w-[80px]">
					{#if isGenerating}
						{genProgressPct}% · {formatElapsed(genElapsed)}
					{:else}
						{formatTime(currentTime)} / {formatTime(duration)}
					{/if}
				</span>

				<!-- Spacer -->
				<div class="flex-1"></div>

				<!-- Voice label (desktop) + Playback Speed control (all devices) -->
				<div class="flex items-center gap-2">
					<span class="hidden sm:inline text-[10px] text-slate-500 bg-white/5 px-2 py-0.5 rounded-full">
						{displayVoice}
					</span>
					<!-- Playback Speed Dropdown -->
					<div class="relative">
						<button
							onclick={() => showSpeedMenu = !showSpeedMenu}
							class="flex items-center gap-1 text-[10px] text-slate-400 hover:text-slate-200 bg-white/5 hover:bg-white/10 px-2 py-1 rounded-full font-mono transition-colors"
							title="Playback speed"
						>
							<Gauge size={12} />
							{playbackRate.toFixed(playbackRate === 1 ? 0 : 2)}x
						</button>
						{#if showSpeedMenu}
							<div class="absolute bottom-full right-0 mb-2 py-1 bg-slate-800 border border-white/10 rounded-lg shadow-xl min-w-[80px] max-h-[240px] overflow-y-auto">
								{#each playbackRates as rate}
									<button
										onclick={() => setPlaybackRate(rate)}
										class="w-full px-3 py-1.5 text-left text-xs font-mono transition-colors {playbackRate === rate ? 'text-blue-400 bg-blue-500/10' : 'text-slate-300 hover:bg-white/5'}"
									>
										{rate}x
									</button>
								{/each}
							</div>
						{/if}
					</div>
				</div>

				<!-- Download -->
				<button
					onclick={downloadCurrentAudio}
					disabled={isGenerating}
					class="p-2 text-slate-500 hover:text-blue-400 transition-colors disabled:opacity-50"
					title="Download MP3"
				>
					<Download size={14} />
				</button>

				<!-- Stop -->
				<button
					onclick={() => showStopConfirm = true}
					class="p-2 text-slate-500 hover:text-white transition-colors"
					title="Stop and discard"
				>
					<Square size={14} />
				</button>
			</div>
		{/if}
	</div>
{/if}

<!-- Stop Confirmation Modal -->
{#if showStopConfirm}
	<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
	<div class="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4" onclick={() => showStopConfirm = false}>
		<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
		<div class="bg-[#0f1218] border border-white/10 rounded-2xl p-6 max-w-sm w-full shadow-2xl" onclick={(e) => e.stopPropagation()}>
			<div class="flex items-center gap-3 mb-4">
				<div class="w-10 h-10 bg-red-500/10 rounded-xl flex items-center justify-center">
					<AlertTriangle size={20} class="text-red-400" />
				</div>
				<h3 class="text-lg font-semibold text-slate-200">Discard Audio?</h3>
			</div>
			<p class="text-sm text-slate-400 mb-6">
				This will stop playback and discard the current audio. If this took a long time to generate, you may want to keep it.
			</p>
			<div class="flex gap-3">
				<button
					onclick={() => showStopConfirm = false}
					class="flex-1 px-4 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors"
				>
					Keep Playing
				</button>
				<button
					onclick={() => { playerStore.stop(); showStopConfirm = false; }}
					class="flex-1 px-4 py-2.5 bg-red-600 hover:bg-red-500 rounded-xl text-sm font-medium transition-colors"
				>
					Discard
				</button>
			</div>
		</div>
	</div>
{/if}
