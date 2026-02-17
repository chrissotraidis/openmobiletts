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

	// Split segment text into words for fine-grained clicking
	function getWords(text) {
		return text.split(/(\s+)/).filter(w => w.length > 0);
	}

	// Calculate interpolated time for a word within a segment
	function getWordTime(segment, wordIndex, totalWords) {
		if (typeof segment.start !== 'number' || typeof segment.end !== 'number') {
			return segment.start;
		}
		const duration = segment.end - segment.start;
		// Count only actual words (not whitespace) for position calculation
		const words = getWords(segment.text).filter(w => w.trim().length > 0);
		const actualWordIndex = Math.min(wordIndex, words.length - 1);
		const progress = words.length > 1 ? actualWordIndex / (words.length - 1) : 0;
		return segment.start + (duration * progress * 0.9); // 0.9 to not overshoot
	}

	// Click/tap to seek to word position within segment
	function handleWordClick(segment, wordIndex) {
		if (playerState === PlayState.GENERATING) return;
		if (typeof segment.start === 'number') {
			const words = getWords(segment.text).filter(w => w.trim().length > 0);
			const seekTime = getWordTime(segment, wordIndex, words.length);
			playerStore.seek(seekTime);
			if (playerState === PlayState.PAUSED) {
				playerStore.play();
			}
		}
	}
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
			class="text-[15px] leading-relaxed text-slate-300 max-h-[400px] overflow-y-auto custom-scrollbar"
		>
			{#each segments as segment, segIdx}
				{@const words = getWords(segment.text)}
				{@const isClickable = playerState !== PlayState.GENERATING}
				{#if segment.starts_paragraph && segIdx > 0}<br><br>{/if}<span
					data-segment={segIdx}
					class="text-segment {segIdx === activeIndex ? 'highlighted' : ''}"
				>{#each words as word, wordIdx}{#if word.trim().length === 0}{word}{:else}<span
							class="{isClickable ? 'cursor-pointer hover:text-blue-300 transition-colors' : ''}"
							onclick={() => handleWordClick(segment, words.slice(0, wordIdx).filter(w => w.trim().length > 0).length)}
							role="button"
							tabindex="0"
							onkeydown={(e) => e.key === 'Enter' && handleWordClick(segment, words.slice(0, wordIdx).filter(w => w.trim().length > 0).length)}
						>{word}</span>{/if}{/each}</span>{' '}
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
