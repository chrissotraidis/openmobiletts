<script>
	import { playerStore, PlayState } from '$lib/stores/player';
	import { AlignLeft } from 'lucide-svelte';
	import { onDestroy } from 'svelte';

	let segments = $state([]);
	let activeIndex = $state(-1);
	let playerState = $state(PlayState.IDLE);
	let inputText = $state('');

	const unsubs = [
		playerStore.segments.subscribe((s) => (segments = s)),
		playerStore.activeSegmentIndex.subscribe((i) => (activeIndex = i)),
		playerStore.state.subscribe((s) => (playerState = s)),
		playerStore.inputText.subscribe((t) => (inputText = t)),
	];
	onDestroy(() => unsubs.forEach((u) => u()));

	const isActive = $derived(
		playerState === PlayState.GENERATING ||
		playerState === PlayState.PLAYING ||
		playerState === PlayState.PAUSED
	);

	let scrollContainer = $state(null);

	// Auto-scroll to active segment
	$effect(() => {
		if (activeIndex >= 0 && scrollContainer) {
			const el = scrollContainer.querySelector(`[data-segment="${activeIndex}"]`);
			if (el) {
				el.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
			}
		}
	});
</script>

{#if isActive && segments.length > 0}
	<div class="p-5 bg-slate-900/40 border border-white/5 rounded-2xl">
		<div class="flex items-center gap-2 mb-4">
			<AlignLeft size={16} class="text-blue-400" />
			<h3 class="text-sm font-semibold text-slate-400">Text</h3>
			{#if playerState === PlayState.GENERATING}
				<span class="text-[10px] bg-blue-500/20 text-blue-400 px-2 py-0.5 rounded-full font-medium">
					Generating...
				</span>
			{/if}
		</div>

		<div
			bind:this={scrollContainer}
			class="text-[15px] leading-relaxed text-slate-300 max-h-[400px] overflow-y-auto custom-scrollbar space-y-1"
		>
			{#each segments as segment, i}
				<span
					data-segment={i}
					class="text-segment inline {i === activeIndex ? 'highlighted' : ''}"
				>
					{segment.text}
				</span>
				{' '}
			{/each}
		</div>
	</div>
{:else if isActive && inputText}
	<div class="p-5 bg-slate-900/40 border border-white/5 rounded-2xl">
		<div class="flex items-center gap-2 mb-4">
			<AlignLeft size={16} class="text-blue-400" />
			<h3 class="text-sm font-semibold text-slate-400">Text</h3>
			<span class="text-[10px] bg-blue-500/20 text-blue-400 px-2 py-0.5 rounded-full font-medium">
				Processing...
			</span>
		</div>
		<p class="text-[15px] leading-relaxed text-slate-500">{inputText}</p>
	</div>
{/if}
