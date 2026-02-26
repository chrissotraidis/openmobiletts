<script>
	import { playerStore, PlayState } from '$lib/stores/player';
	import { onDestroy } from 'svelte';
	import { AudioLines, Zap, XCircle, AlertTriangle } from 'lucide-svelte';

	let playerState = $state(PlayState.IDLE);
	let segments = $state([]);
	let inputText = $state('');
	let serverTotalChunks = $state(0);
	let elapsedTime = $state(0);
	let showCancelConfirm = $state(false);

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
		showCancelConfirm = true;
	}

	function confirmCancel() {
		showCancelConfirm = false;
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
					<div class="flex items-baseline justify-between gap-3 mb-2">
						<span class="text-sm font-medium text-slate-200 shrink-0">
							{#if progressPercent > 0}
								Generating speech... {progressPercent}%
							{:else}
								Preparing text...
							{/if}
						</span>
						<span class="text-[11px] font-mono text-slate-400 shrink-0">
							{formatTime(elapsedTime)}
							{#if estimatedRemaining() != null}
								<span class="text-slate-600 ml-1">· ~{formatTime(estimatedRemaining())} left</span>
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

			<!-- Cancel link (subtle — not a big red button) -->
			<div class="mt-3 flex justify-end">
				<button
					onclick={handleCancel}
					class="text-[11px] text-slate-500 hover:text-red-400 active:text-red-400 transition-colors px-2 py-1"
					style="touch-action: manipulation"
				>
					Cancel
				</button>
			</div>
		</div>
	</div>
{/if}

<!-- Cancel Confirmation Modal -->
{#if showCancelConfirm}
	<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
	<div class="fixed inset-0 bg-black/60 backdrop-blur-sm z-50 flex items-center justify-center p-4" onclick={() => showCancelConfirm = false}>
		<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
		<div class="bg-[#0f1218] border border-white/10 rounded-2xl p-6 max-w-sm w-full shadow-2xl" onclick={(e) => e.stopPropagation()}>
			<div class="flex items-center gap-3 mb-4">
				<div class="w-10 h-10 bg-red-500/10 rounded-xl flex items-center justify-center">
					<AlertTriangle size={20} class="text-red-400" />
				</div>
				<h3 class="text-lg font-semibold text-slate-200">Cancel Generation?</h3>
			</div>
			<p class="text-sm text-slate-400 mb-2">
				This will stop the current generation and discard all progress.
			</p>
			{#if elapsedTime > 60}
				<p class="text-sm text-amber-400/80 mb-4">
					This has been running for {formatTime(elapsedTime)} — are you sure?
				</p>
			{:else}
				<div class="mb-4"></div>
			{/if}
			<div class="flex gap-3">
				<button
					onclick={() => showCancelConfirm = false}
					class="flex-1 px-4 py-2.5 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors"
				>
					Keep Going
				</button>
				<button
					onclick={confirmCancel}
					class="flex-1 px-4 py-2.5 bg-red-600 hover:bg-red-500 rounded-xl text-sm font-medium transition-colors"
				>
					Cancel
				</button>
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
