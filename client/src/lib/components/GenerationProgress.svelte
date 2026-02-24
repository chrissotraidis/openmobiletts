<script>
	import { playerStore, PlayState } from '$lib/stores/player';
	import { onDestroy } from 'svelte';
	import { AudioLines, Zap } from 'lucide-svelte';

	const isAndroid = typeof window !== 'undefined' && !!window.Android;

	let playerState = $state(PlayState.IDLE);
	let inputText = $state('');
	let serverTotalChunks = $state(0);
	let serverChunksCompleted = $state(0);
	let elapsedTime = $state(0);
	let eta = $state(null);

	const unsubs = [
		playerStore.state.subscribe((s) => (playerState = s)),
		playerStore.inputText.subscribe((t) => (inputText = t)),
		playerStore.totalChunks.subscribe((n) => (serverTotalChunks = n)),
		playerStore.chunksCompleted.subscribe((n) => (serverChunksCompleted = n)),
		playerStore.generationElapsed.subscribe((t) => (elapsedTime = t)),
		playerStore.estimatedSecondsLeft.subscribe((n) => (eta = n)),
	];
	onDestroy(() => unsubs.forEach((u) => u()));

	const isGenerating = $derived(playerState === PlayState.GENERATING);
	const chunksReceived = $derived(serverChunksCompleted);

	// Use server-reported chunk count when available, otherwise estimate from text length
	const estimatedTotalChunks = $derived(
		serverTotalChunks > 0
			? serverTotalChunks
			: Math.max(1, Math.ceil(inputText.length / 1000))
	);

	const progressPercent = $derived(
		chunksReceived > 0
			? Math.min(99, Math.round((chunksReceived / estimatedTotalChunks) * 100))
			: 0
	);

	function formatTime(seconds) {
		const m = Math.floor(seconds / 60);
		const s = seconds % 60;
		return m > 0 ? `${m}m ${s}s` : `${s}s`;
	}

	const etaText = $derived(
		eta !== null && eta > 0 ? `~${formatTime(eta)} remaining` : null
	);
</script>

{#if isGenerating}
	<div class="bg-gradient-to-r from-blue-600/10 via-purple-600/10 to-blue-600/10 border border-blue-500/20 rounded-2xl p-4 mb-4 overflow-hidden relative">
		<!-- Animated background -->
		<div class="absolute inset-0 bg-gradient-to-r from-transparent via-white/5 to-transparent animate-shimmer"></div>

		<div class="relative flex items-center gap-4">
			<!-- Animated icon -->
			<div class="relative">
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
					<span class="text-xs font-mono text-slate-400">{formatTime(elapsedTime)}</span>
				</div>

				<!-- Progress bar based on actual completion -->
				<div class="h-2 bg-slate-800 rounded-full overflow-hidden">
					<div
						class="h-full bg-gradient-to-r from-blue-500 via-purple-500 to-blue-500 rounded-full transition-all duration-300 ease-out"
						style="width: {progressPercent > 0 ? progressPercent : 3}%"
					></div>
				</div>

				<!-- Chunks counter + ETA -->
				<div class="flex items-center justify-between mt-2">
					<span class="text-[10px] text-slate-500">
						{#if chunksReceived > 0}
							<span class="text-blue-400">{chunksReceived}</span> of {serverTotalChunks > 0 ? '' : '~'}{estimatedTotalChunks} chunks{#if etaText} &middot; {etaText}{/if}
						{:else}
							Analyzing text...
						{/if}
					</span>
					<span class="text-[10px] text-slate-600 flex items-center gap-1">
						<span class="w-1.5 h-1.5 bg-blue-400 rounded-full animate-ping"></span>
						Processing on device
					</span>
				</div>

				<!-- Background notice for Android -->
				{#if isAndroid && chunksReceived > 0}
					<div class="mt-2 text-[10px] text-slate-600">
						You can switch apps — generation continues in the background
					</div>
				{/if}
			</div>
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
