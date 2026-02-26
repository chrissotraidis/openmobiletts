<script>
	import { playerStore, PlayState } from '$lib/stores/player';
	import { playlistStore } from '$lib/stores/playlist';
	import { Play, Pause, Square, Volume2, Gauge, AlertTriangle, Download, ListMusic, X, GripVertical, ChevronDown, AudioLines, SkipBack, SkipForward } from 'lucide-svelte';
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
	let showQueue = $state(false);
	// Props: expanded state managed by parent for back-button/history integration
	let { expanded = $bindable(false), onopen = () => {}, onclose = () => {} } = $props();
	let expandedSpeedMenu = $state(false);
	let showExpandedQueue = $state(false);

	// Playlist state
	let queueItems = $state([]);
	let queueCurrentIndex = $state(-1);

	// Drag-to-reorder state
	let dragIndex = $state(-1);
	let dragOverIndex = $state(-1);
	let queueListEl = $state(null);

	// Expanded view seek state
	let isSeeking = $state(false);
	let seekPosition = $state(0);

	function handleDragStart(idx, e) {
		e.preventDefault();
		dragIndex = idx;
		dragOverIndex = idx;
		const target = e.currentTarget.closest('[data-queue-item]');
		if (target) target.setPointerCapture(e.pointerId);
	}

	function handleDragMove(e) {
		if (dragIndex < 0 || !queueListEl) return;
		const items = queueListEl.querySelectorAll('[data-queue-item]');
		const y = e.clientY;
		let closest = dragIndex;
		for (let i = 0; i < items.length; i++) {
			const rect = items[i].getBoundingClientRect();
			if (y >= rect.top && y <= rect.bottom) {
				closest = i;
				break;
			}
			if (i === 0 && y < rect.top) { closest = 0; break; }
			if (i === items.length - 1 && y > rect.bottom) { closest = items.length - 1; break; }
		}
		dragOverIndex = closest;
	}

	function handleDragEnd() {
		if (dragIndex >= 0 && dragOverIndex >= 0 && dragIndex !== dragOverIndex) {
			playlistStore.move(dragIndex, dragOverIndex);
		}
		dragIndex = -1;
		dragOverIndex = -1;
	}

	// Progress tracking during generation
	let genSegments = $state([]);
	let genInputText = $state('');
	let genTotalChunks = $state(0);
	let genElapsed = $state(0);

	const unsubs = [
		playerStore.state.subscribe((s) => {
			playerState = s;
			if (s === PlayState.IDLE && expanded) {
				expanded = false;
				showExpandedQueue = false;
				onclose();
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
		playerStore.totalChunks.subscribe((n) => (genTotalChunks = n)),
		playerStore.generationElapsed.subscribe((t) => (genElapsed = t)),
		playlistStore.items.subscribe((items) => (queueItems = items)),
		playlistStore.currentIndex.subscribe((i) => (queueCurrentIndex = i)),
	];
	onDestroy(() => unsubs.forEach((u) => u()));

	const playbackRates = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2, 2.5, 3];

	function setPlaybackRate(rate) {
		playerStore.setPlaybackRate(rate);
		showSpeedMenu = false;
		expandedSpeedMenu = false;
	}

	const isVisible = $derived(playerState !== PlayState.IDLE || queueItems.length > 0);
	const isPlaying = $derived(playerState === PlayState.PLAYING);
	const isGenerating = $derived(playerState === PlayState.GENERATING);
	const isError = $derived(playerState === PlayState.ERROR);

	const progress = $derived(duration > 0 ? (currentTime / duration) * 100 : 0);

	// Generation progress calculation
	const genChunksReceived = $derived(genSegments.length);
	const genEstimatedTotal = $derived(
		genTotalChunks > 0
			? genTotalChunks
			: Math.max(1, Math.ceil(genInputText.length / 700))
	);
	const genProgressPct = $derived(
		genChunksReceived > 0
			? Math.min(99, Math.round((genChunksReceived / genEstimatedTotal) * 100))
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

	// Expanded view seek handlers (touch-friendly)
	function handleExpandedSeekStart(event) {
		if (isGenerating) return;
		isSeeking = true;
		updateSeekPosition(event);
	}

	function handleExpandedSeekMove(event) {
		if (!isSeeking) return;
		updateSeekPosition(event);
	}

	function handleExpandedSeekEnd() {
		if (!isSeeking) return;
		isSeeking = false;
		playerStore.seek(seekPosition);
	}

	function updateSeekPosition(event) {
		const target = event.currentTarget;
		const rect = target.getBoundingClientRect();
		const clientX = event.touches ? event.touches[0].clientX : event.clientX;
		const pct = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
		seekPosition = pct * duration;
	}

	function skipBack() {
		playerStore.seek(Math.max(0, currentTime - 15));
	}

	function skipForward() {
		playerStore.seek(Math.min(duration, currentTime + 15));
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

	async function downloadCurrentAudio() {
		const blob = playerStore.getAudioBlob();
		if (!blob) return;

		const ext = blob.type === 'audio/wav' ? '.wav' : '.mp3';
		const filename = `tts-${displayVoice.toLowerCase()}-${speed.toFixed(1)}x${ext}`;
		await playerStore.downloadAudio(blob, filename);
	}

	function playQueueItem(index) {
		const item = queueItems[index];
		if (!item) return;
		playlistStore.currentIndex.set(index);
		playerStore.playFromHistory(item, true);
	}

	function startQueue() {
		const entry = playlistStore.start();
		if (entry) {
			playerStore.playFromHistory(entry, true);
		}
	}

	const hasQueue = $derived(queueItems.length > 0);

	// Displayed seek position (actual or drag)
	const displayTime = $derived(isSeeking ? seekPosition : currentTime);
	const displayProgress = $derived(duration > 0 ? (displayTime / duration) * 100 : 0);

	// Open expanded view (only for non-button taps on the compact bar)
	function openExpanded(e) {
		// Don't open if tapping a button or interactive element
		if (e.target.closest('button') || e.target.closest('[data-no-expand]')) return;
		if (isError) return;
		expanded = true;
		onopen();
	}
</script>

<!-- Queue Panel (slides up above the player bar) -->
{#if showQueue && hasQueue}
	<!-- svelte-ignore a11y_no_static_element_interactions -->
	<div
		class="fixed bottom-[120px] md:bottom-[48px] left-0 md:left-64 right-0 z-29 bg-[#0d1117]/98 backdrop-blur-md border-t border-white/5 max-h-[50vh] overflow-y-auto custom-scrollbar"
		onpointermove={handleDragMove}
		onpointerup={handleDragEnd}
		onpointercancel={handleDragEnd}
	>
		<div class="px-4 py-3 flex items-center justify-between border-b border-white/5">
			<div class="flex items-center gap-2">
				<ListMusic size={14} class="text-blue-400" />
				<span class="text-xs font-semibold text-slate-400">Queue</span>
				<span class="text-[10px] text-slate-600">{queueItems.length} track{queueItems.length !== 1 ? 's' : ''}</span>
			</div>
			<div class="flex items-center gap-3">
				{#if queueCurrentIndex < 0}
					<button
						onclick={startQueue}
						class="text-xs text-blue-400 hover:text-blue-300 font-medium transition-colors px-2 py-1"
					>
						Play All
					</button>
				{/if}
				<button
					onclick={() => { playlistStore.clear(); showQueue = false; }}
					class="text-xs text-slate-600 hover:text-red-400 transition-colors px-2 py-1"
				>
					Clear
				</button>
			</div>
		</div>
		<div bind:this={queueListEl}>
			{#each queueItems as item, idx (item.id)}
				{@const isDragging = dragIndex === idx}
				{@const isDropTarget = dragIndex >= 0 && dragOverIndex === idx && dragIndex !== idx}
				<div
					data-queue-item={idx}
					class="flex items-center gap-2 px-2 py-3 transition-colors select-none
						{idx === queueCurrentIndex ? 'bg-blue-500/10 border-l-2 border-blue-500' : 'border-l-2 border-transparent'}
						{isDragging ? 'opacity-50 bg-white/5' : ''}
						{isDropTarget ? 'border-t-2 !border-t-blue-400' : ''}"
				>
					<!-- Drag Handle (touch-friendly) -->
					<!-- svelte-ignore a11y_no_static_element_interactions -->
					<div
						class="w-10 h-10 flex items-center justify-center shrink-0 cursor-grab active:cursor-grabbing text-slate-600 hover:text-slate-400 touch-none"
						onpointerdown={(e) => handleDragStart(idx, e)}
					>
						<GripVertical size={16} />
					</div>

					<!-- Track number / play button -->
					<button
						onclick={() => playQueueItem(idx)}
						class="w-8 h-8 flex items-center justify-center shrink-0 rounded-lg hover:bg-white/10 transition-colors"
					>
						{#if idx === queueCurrentIndex && (isPlaying || isGenerating)}
							<div class="w-3.5 h-3.5 border-2 border-blue-400 border-t-transparent rounded-full animate-spin"></div>
						{:else}
							<span class="text-xs font-mono text-slate-500">{idx + 1}</span>
						{/if}
					</button>

					<!-- Track info -->
					<div class="flex-1 min-w-0 py-1">
						<p class="text-sm text-slate-300 truncate">{item.preview || item.text?.slice(0, 60)}</p>
						<span class="text-[11px] text-slate-600">
							{voiceDisplayNames[item.voice] || item.voice} · {item.speed?.toFixed(1) || '1.0'}x
						</span>
					</div>

					<!-- Remove (touch-friendly) -->
					<button
						onclick={() => playlistStore.remove(idx)}
						class="w-10 h-10 flex items-center justify-center shrink-0 text-slate-700 hover:text-red-400 active:text-red-400 transition-colors"
						title="Remove from queue"
					>
						<X size={16} />
					</button>
				</div>
			{/each}
		</div>
	</div>
{/if}

<!-- Compact Player Bar -->
{#if isVisible}
	<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
	<div
		class="fixed bottom-[72px] md:bottom-0 left-0 md:left-64 right-0 z-30 bg-[#0d1117]/95 backdrop-blur-md border-t border-white/5"
		style="touch-action: manipulation"
		onclick={openExpanded}
	>
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
				data-no-expand
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

				<!-- Voice name (compact bar) -->
				<span class="text-[11px] text-slate-600 truncate flex-1">
					{displayVoice}
				</span>

				<!-- Speed (compact) -->
				<div class="relative" style="touch-action: manipulation">
					<button
						onclick={() => showSpeedMenu = !showSpeedMenu}
						class="flex items-center gap-1 text-[10px] text-slate-400 hover:text-slate-200 bg-white/5 hover:bg-white/10 px-2 py-1.5 rounded-full font-mono transition-colors select-none min-w-[44px] min-h-[44px] justify-center"
						style="touch-action: manipulation"
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
									class="w-full px-3 py-2.5 text-left text-xs font-mono transition-colors select-none {playbackRate === rate ? 'text-blue-400 bg-blue-500/10' : 'text-slate-300 hover:bg-white/5'}"
									style="touch-action: manipulation"
								>
									{rate}x
								</button>
							{/each}
						</div>
					{/if}
				</div>

				<!-- Queue Toggle -->
				{#if hasQueue}
					<button
						onclick={() => showQueue = !showQueue}
						class="relative p-2 transition-colors {showQueue ? 'text-blue-400' : 'text-slate-500 hover:text-blue-400'}"
						title="Queue ({queueItems.length})"
					>
						<ListMusic size={14} />
						<span class="absolute -top-0.5 -right-0.5 w-3.5 h-3.5 bg-blue-600 rounded-full text-[8px] font-bold flex items-center justify-center text-white">
							{queueItems.length}
						</span>
					</button>
				{/if}

				<!-- Stop (only when NOT generating) -->
				{#if !isGenerating}
					<button
						onclick={() => showStopConfirm = true}
						class="p-2 text-slate-500 hover:text-white transition-colors"
						title="Stop and discard"
					>
						<Square size={14} />
					</button>
				{/if}
			</div>
		{/if}
	</div>
{/if}

<!-- Expanded "Now Playing" View -->
{#if expanded && isVisible && !isError}
	<div class="fixed inset-0 z-50 bg-[#0a0c10] flex flex-col expand-slide-up pb-[72px] md:pb-0" style="touch-action: manipulation">
		<!-- Header with close and queue toggle -->
		<div class="flex items-center justify-between px-6 pt-6 pb-4">
			<button
				onclick={() => { expanded = false; showExpandedQueue = false; onclose(); }}
				class="w-10 h-10 flex items-center justify-center text-slate-400 hover:text-white transition-colors rounded-full hover:bg-white/5"
			>
				<ChevronDown size={24} />
			</button>
			<span class="text-xs font-medium text-slate-500 uppercase tracking-widest">{showExpandedQueue ? 'Queue' : 'Now Playing'}</span>
			{#if hasQueue}
				<button
					onclick={() => showExpandedQueue = !showExpandedQueue}
					class="w-10 h-10 flex items-center justify-center transition-colors rounded-full hover:bg-white/5 {showExpandedQueue ? 'text-blue-400' : 'text-slate-400 hover:text-white'}"
				>
					<ListMusic size={20} />
				</button>
			{:else}
				<div class="w-10"></div>
			{/if}
		</div>

		{#if showExpandedQueue}
			<!-- Queue list in expanded view -->
			<div class="flex-1 overflow-y-auto px-4 custom-scrollbar">
				{#if queueItems.length === 0}
					<div class="flex flex-col items-center justify-center h-full text-slate-600">
						<ListMusic size={40} class="mb-3 opacity-50" />
						<p class="text-sm">Queue is empty</p>
						<p class="text-xs mt-1 text-slate-700">Add items from History</p>
					</div>
				{:else}
					<div class="flex items-center justify-between px-2 py-3 border-b border-white/5 mb-1">
						<span class="text-sm font-semibold text-slate-300">Up Next · {queueItems.length} track{queueItems.length !== 1 ? 's' : ''}</span>
						<button
							onclick={() => { playlistStore.clear(); showExpandedQueue = false; }}
							class="text-xs text-slate-600 hover:text-red-400 transition-colors px-2 py-1"
						>
							Clear All
						</button>
					</div>
					{#each queueItems as item, idx (item.id)}
						<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
						<div
							onclick={() => playQueueItem(idx)}
							class="flex items-center gap-3 w-full px-3 py-3 transition-colors cursor-pointer rounded-lg
								{idx === queueCurrentIndex ? 'bg-blue-500/10' : 'hover:bg-white/5'}"
						>
							<div class="w-8 h-8 flex items-center justify-center shrink-0 rounded-lg {idx === queueCurrentIndex ? 'bg-blue-500/20' : 'bg-white/5'}">
								{#if idx === queueCurrentIndex && (isPlaying || isGenerating)}
									<div class="w-3.5 h-3.5 border-2 border-blue-400 border-t-transparent rounded-full animate-spin"></div>
								{:else}
									<span class="text-xs font-mono {idx === queueCurrentIndex ? 'text-blue-400' : 'text-slate-500'}">{idx + 1}</span>
								{/if}
							</div>
							<div class="flex-1 min-w-0">
								<p class="text-sm truncate {idx === queueCurrentIndex ? 'text-blue-300' : 'text-slate-300'}">{item.preview || item.text?.slice(0, 60)}</p>
								<span class="text-[11px] text-slate-600">
									{voiceDisplayNames[item.voice] || item.voice} · {item.speed?.toFixed(1) || '1.0'}x
								</span>
							</div>
							<button
								onclick={(e) => { e.stopPropagation(); playlistStore.remove(idx); }}
								class="w-8 h-8 flex items-center justify-center shrink-0 text-slate-700 hover:text-red-400 active:text-red-400 transition-colors rounded-lg"
							>
								<X size={14} />
							</button>
						</div>
					{/each}
				{/if}
			</div>
		{:else}
			<!-- Visual / Art area -->
			<div class="flex-1 flex items-center justify-center px-8">
			<div class="w-64 h-64 sm:w-72 sm:h-72 rounded-3xl bg-gradient-to-br from-blue-600/20 via-purple-600/20 to-blue-600/10 border border-white/5 flex items-center justify-center relative overflow-hidden">
				<!-- Animated background rings -->
				{#if isPlaying || isGenerating}
					<div class="absolute inset-0 flex items-center justify-center">
						<div class="w-48 h-48 rounded-full border border-blue-500/10 animate-ping-slow"></div>
					</div>
					<div class="absolute inset-0 flex items-center justify-center">
						<div class="w-32 h-32 rounded-full border border-purple-500/10 animate-ping-slow" style="animation-delay: 0.5s"></div>
					</div>
				{/if}
				<!-- Icon -->
				<div class="relative z-10">
					{#if isGenerating}
						<div class="w-20 h-20 border-4 border-blue-400/30 border-t-blue-400 rounded-full animate-spin"></div>
					{:else}
						<AudioLines size={80} class="text-blue-400/60 {isPlaying ? 'animate-pulse' : ''}" />
					{/if}
				</div>
			</div>
		</div>
		{/if}

		<!-- Info + Controls -->
		<div class="px-8 pb-8 space-y-6">
			<!-- Voice & speed info -->
			<div class="text-center space-y-1">
				{#if isGenerating}
					<h3 class="text-lg font-semibold text-slate-200">Generating Speech</h3>
					<p class="text-sm text-slate-500">{genProgressPct}% · Chunk {genChunksReceived} of {genTotalChunks > 0 ? '' : '~'}{genEstimatedTotal} · {formatElapsed(genElapsed)}</p>
				{:else}
					<h3 class="text-lg font-semibold text-slate-200">{displayVoice}</h3>
					<p class="text-sm text-slate-500">{speed.toFixed(1)}x generation speed</p>
				{/if}
			</div>

			<!-- Seek bar (large, touch-friendly) -->
			<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
			<div class="space-y-2">
				<div
					class="relative h-10 flex items-center cursor-pointer group"
					style="touch-action: none"
					onmousedown={handleExpandedSeekStart}
					onmousemove={handleExpandedSeekMove}
					onmouseup={handleExpandedSeekEnd}
					ontouchstart={handleExpandedSeekStart}
					ontouchmove={handleExpandedSeekMove}
					ontouchend={handleExpandedSeekEnd}
				>
					<div class="w-full h-1.5 bg-white/10 rounded-full overflow-hidden group-hover:h-2 transition-all">
						{#if isGenerating}
							<div
								class="h-full bg-gradient-to-r from-blue-500 via-purple-500 to-blue-500 rounded-full transition-all duration-300"
								style="width: {genProgressPct > 0 ? genProgressPct : 3}%"
							></div>
						{:else}
							<div
								class="h-full bg-blue-500 rounded-full transition-all"
								style="width: {displayProgress}%; transition-duration: {isSeeking ? '0ms' : '100ms'}"
							></div>
						{/if}
					</div>
					<!-- Seek handle (visible on hover/drag) -->
					{#if !isGenerating}
						<div
							class="absolute top-1/2 -translate-y-1/2 w-4 h-4 bg-blue-500 rounded-full shadow-lg shadow-blue-500/30 transition-opacity {isSeeking ? 'opacity-100 scale-125' : 'opacity-0 group-hover:opacity-100'}"
							style="left: calc({displayProgress}% - 8px)"
						></div>
					{/if}
				</div>
				<!-- Time labels -->
				<div class="flex justify-between text-xs font-mono text-slate-500">
					{#if isGenerating}
						<span>{formatElapsed(genElapsed)}</span>
						<span>{genProgressPct}%</span>
					{:else}
						<span>{formatTime(displayTime)}</span>
						<span>{formatTime(duration)}</span>
					{/if}
				</div>
			</div>

			<!-- Transport controls -->
			<div class="flex items-center justify-center gap-6">
				<!-- Skip Back 15s -->
				<button
					onclick={skipBack}
					disabled={isGenerating}
					class="w-12 h-12 flex items-center justify-center text-slate-400 hover:text-white transition-colors disabled:opacity-30 disabled:cursor-default rounded-full hover:bg-white/5"
				>
					<SkipBack size={22} />
				</button>

				<!-- Play/Pause (large) -->
				{#if isGenerating}
					<div class="w-16 h-16 bg-blue-600/20 rounded-full flex items-center justify-center">
						<div class="w-7 h-7 border-3 border-blue-400 border-t-transparent rounded-full animate-spin"></div>
					</div>
				{:else}
					<button
						onclick={togglePlay}
						class="w-16 h-16 bg-blue-600 hover:bg-blue-500 active:scale-95 rounded-full flex items-center justify-center transition-all shadow-lg shadow-blue-600/30"
					>
						{#if isPlaying}
							<Pause size={28} class="text-white" />
						{:else}
							<Play size={28} class="text-white ml-1" />
						{/if}
					</button>
				{/if}

				<!-- Skip Forward 15s -->
				<button
					onclick={skipForward}
					disabled={isGenerating}
					class="w-12 h-12 flex items-center justify-center text-slate-400 hover:text-white transition-colors disabled:opacity-30 disabled:cursor-default rounded-full hover:bg-white/5"
				>
					<SkipForward size={22} />
				</button>
			</div>

			<!-- Bottom actions row -->
			<div class="flex items-center justify-between px-4">
				<!-- Speed control -->
				<div class="relative" style="touch-action: manipulation">
					<button
						onclick={() => expandedSpeedMenu = !expandedSpeedMenu}
						class="flex items-center gap-1.5 text-xs text-slate-400 hover:text-slate-200 bg-white/5 hover:bg-white/10 px-3 py-2 rounded-full font-mono transition-colors"
						style="touch-action: manipulation"
					>
						<Gauge size={14} />
						{playbackRate.toFixed(playbackRate === 1 ? 0 : 2)}x
					</button>
					{#if expandedSpeedMenu}
						<div class="absolute bottom-full left-0 mb-2 py-1 bg-slate-800 border border-white/10 rounded-lg shadow-xl min-w-[80px] max-h-[240px] overflow-y-auto">
							{#each playbackRates as rate}
								<button
									onclick={() => setPlaybackRate(rate)}
									class="w-full px-3 py-2.5 text-left text-xs font-mono transition-colors select-none {playbackRate === rate ? 'text-blue-400 bg-blue-500/10' : 'text-slate-300 hover:bg-white/5'}"
									style="touch-action: manipulation"
								>
									{rate}x
								</button>
							{/each}
						</div>
					{/if}
				</div>

				<!-- Download -->
				{#if !isGenerating}
					<button
						onclick={downloadCurrentAudio}
						class="flex items-center gap-1.5 text-xs text-slate-400 hover:text-blue-400 bg-white/5 hover:bg-white/10 px-3 py-2 rounded-full transition-colors"
					>
						<Download size={14} />
						Download
					</button>
				{/if}

				<!-- Stop / Discard -->
				{#if !isGenerating}
					<button
						onclick={() => showStopConfirm = true}
						class="flex items-center gap-1.5 text-xs text-slate-400 hover:text-red-400 bg-white/5 hover:bg-white/10 px-3 py-2 rounded-full transition-colors"
					>
						<Square size={14} />
						Stop
					</button>
				{/if}
			</div>
		</div>
	</div>
{/if}

<!-- Stop Confirmation Modal -->
{#if showStopConfirm}
	<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
	<div class="fixed inset-0 bg-black/60 backdrop-blur-sm z-[60] flex items-center justify-center p-4" onclick={() => showStopConfirm = false}>
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
					onclick={() => { playerStore.stop(); playlistStore.clear(); showStopConfirm = false; showQueue = false; }}
					class="flex-1 px-4 py-2.5 bg-red-600 hover:bg-red-500 rounded-xl text-sm font-medium transition-colors"
				>
					Discard
				</button>
			</div>
		</div>
	</div>
{/if}

<style>
	@keyframes ping-slow {
		0% { transform: scale(1); opacity: 0.3; }
		50% { transform: scale(1.15); opacity: 0.1; }
		100% { transform: scale(1); opacity: 0.3; }
	}

	.animate-ping-slow {
		animation: ping-slow 3s ease-in-out infinite;
	}

	.expand-slide-up {
		animation: slideUp 0.3s ease-out;
	}

	@keyframes slideUp {
		from {
			transform: translateY(100%);
			opacity: 0.5;
		}
		to {
			transform: translateY(0);
			opacity: 1;
		}
	}
</style>
