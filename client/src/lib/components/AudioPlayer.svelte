<script>
	import { playerStore, PlayState } from '$lib/stores/player';
	import { Play, Pause, Square, Volume2 } from 'lucide-svelte';

	let playerState = $state(PlayState.IDLE);
	let currentTime = $state(0);
	let duration = $state(0);
	let voiceName = $state('');
	let speed = $state(1.0);
	let errorMessage = $state('');

	playerStore.state.subscribe((s) => (playerState = s));
	playerStore.currentTime.subscribe((t) => (currentTime = t));
	playerStore.duration.subscribe((d) => (duration = d));
	playerStore.voice.subscribe((v) => (voiceName = v));
	playerStore.speed.subscribe((s) => (speed = s));
	playerStore.errorMessage.subscribe((e) => (errorMessage = e));

	const isVisible = $derived(playerState !== PlayState.IDLE);
	const isPlaying = $derived(playerState === PlayState.PLAYING);
	const isGenerating = $derived(playerState === PlayState.GENERATING);
	const isError = $derived(playerState === PlayState.ERROR);

	const progress = $derived(duration > 0 ? (currentTime / duration) * 100 : 0);

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
			<!-- Progress Bar (clickable) -->
			<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
			<div
				class="h-1 bg-white/5 cursor-pointer group"
				onclick={handleSeek}
			>
				<div
					class="h-full bg-blue-500 transition-all duration-100 group-hover:bg-blue-400"
					style="width: {progress}%"
				></div>
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

				<!-- Time -->
				<span class="text-[11px] font-mono text-slate-500 min-w-[80px]">
					{formatTime(currentTime)} / {formatTime(duration)}
				</span>

				<!-- Spacer -->
				<div class="flex-1"></div>

				<!-- Voice + Speed labels -->
				<div class="hidden sm:flex items-center gap-2">
					<span class="text-[10px] text-slate-500 bg-white/5 px-2 py-0.5 rounded-full">
						{displayVoice}
					</span>
					<span class="text-[10px] text-slate-500 bg-white/5 px-2 py-0.5 rounded-full font-mono">
						{speed.toFixed(1)}x
					</span>
				</div>

				<!-- Stop -->
				<button
					onclick={() => playerStore.stop()}
					class="p-2 text-slate-500 hover:text-white transition-colors"
					title="Stop"
				>
					<Square size={14} />
				</button>
			</div>
		{/if}
	</div>
{/if}
