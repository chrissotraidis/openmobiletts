<script>
	import { playerStore, PlayState } from '$lib/stores/player';
	import { onDestroy } from 'svelte';
	import { AudioLines, Zap, XCircle } from 'lucide-svelte';

	let playerState = $state(PlayState.IDLE);
	let segments = $state([]);
	let inputText = $state('');
	let serverTotalChunks = $state(0);
	let elapsedTime = $state(0);

	const unsubs = [
		playerStore.state.subscribe((s) => (playerState = s)),
		playerStore.segments.subscribe((s) => (segments = s)),
		playerStore.inputText.subscribe((t) => (inputText = t)),
		playerStore.totalChunks.subscribe((n) => (serverTotalChunks = n)),
		playerStore.generationElapsed.subscribe((t) => (elapsedTime = t)),
	];
	onDestroy(() => unsubs.forEach((u) => u()));

	const isGenerating = $derived(playerState === PlayState.GENERATING);
	const chunksReceived = $derived(segments.length);

	// Use server-reported chunk count when available, otherwise estimate from text length
	const estimatedTotalChunks = $derived(
		serverTotalChunks > 0
			? serverTotalChunks
			: Math.max(1, Math.ceil(inputText.length / 700))
	);

	const progressPercent = $derived(
		chunksReceived > 0
			? Math.min(99, Math.round((chunksReceived / estimatedTotalChunks) * 100))
			: 0
	);

	// Estimated time remaining based on average chunk time
	const estimatedRemaining = $derived(() => {
		if (chunksReceived < 2 || elapsedTime < 1) return null;
		const avgTime = elapsedTime / chunksReceived;
		const remaining = estimatedTotalChunks - chunksReceived;
		return Math.ceil(avgTime * remaining);
	});

	function formatTime(seconds) {
		const m = Math.floor(seconds / 60);
		const s = seconds % 60;
		return m > 0 ? `${m}m ${s}s` : `${s}s`;
	}

	function handleCancel() {
		playerStore.stop();
	}
</script>

{#if isGenerating}
	<div class="bg-gradient-to-r from-blue-600/10 via-purple-600/10 to-blue-600/10 border border-blue-500/20 rounded-2xl p-4 mb-4 overflow-hidden relative">
		<!-- Animated background -->
		<div class="absolute inset-0 bg-gradient-to-r from-transparent via-white/5 to-transparent animate-shimmer"></div>

		<div class="relative">
			<div class="flex items-center gap-4">
				<!-- Animated icon -->
				<div class="relative shrink-0">
					<div class="w-12 h-12 bg-blue-600/20 rounded-xl flex items-center justify-center">
						<AudioLines size={24} class="text-blue-400 animate-pulse" />
					</div>
					<div class="absolute -top-1 -right-1 w-4 h-4 bg-purple-500 rounded-full flex items-center justify-center animate-bounce">
						<Zap size={10} class="text-white" />
					</div>
				</div>

				<!-- Progress info -->
				<div class="flex-1 min-w-0">
					<div class="flex items-center justify-between mb-2">
						<span class="text-sm font-medium text-slate-200">
							{#if progressPercent > 0}
								Generating speech... {progressPercent}%
							{:else}
								Preparing text...
							{/if}
						</span>
						<span class="text-xs font-mono text-slate-400">
							{formatTime(elapsedTime)}
							{#if estimatedRemaining() != null}
								<span class="text-slate-600">· ~{formatTime(estimatedRemaining())} left</span>
							{/if}
						</span>
					</div>

					<!-- Progress bar based on actual completion -->
					<div class="h-2 bg-slate-800 rounded-full overflow-hidden">
						<div
							class="h-full bg-gradient-to-r from-blue-500 via-purple-500 to-blue-500 rounded-full transition-all duration-300 ease-out"
							style="width: {progressPercent > 0 ? progressPercent : 3}%"
						></div>
					</div>

					<!-- Chunks counter -->
					<div class="flex items-center justify-between mt-2">
						<span class="text-[10px] text-slate-500">
							{#if chunksReceived > 0}
								Chunk <span class="text-blue-400">{chunksReceived}</span> of {serverTotalChunks > 0 ? '' : '~'}{estimatedTotalChunks}
							{:else}
								Analyzing text...
							{/if}
						</span>
						<span class="text-[10px] text-slate-600 flex items-center gap-1">
							<span class="w-1.5 h-1.5 bg-blue-400 rounded-full animate-ping"></span>
							Processing on device
						</span>
					</div>
				</div>
			</div>

			<!-- Cancel button -->
			<button
				onclick={handleCancel}
				class="mt-3 w-full flex items-center justify-center gap-2 px-4 py-2.5 bg-red-600/10 hover:bg-red-600/20 active:bg-red-600/30 border border-red-500/20 rounded-xl text-sm font-medium text-red-400 transition-colors"
				style="touch-action: manipulation"
			>
				<XCircle size={16} />
				Cancel Generation
			</button>
		</div>
	</div>
{/if}

<style>
	@keyframes shimmer {
		0% { transform: translateX(-100%); }
		100% { transform: translateX(100%); }
	}

	.animate-shimmer {
		animation: shimmer 2s infinite;
	}
</style>
