<script>
	import { playerStore, PlayState } from '$lib/stores/player';
	import { settingsStore } from '$lib/stores/settings';
	import { historyStore } from '$lib/stores/history';
	import { uploadDocument } from '$lib/services/api';
	import { Upload, Loader2, Clock, Play, ChevronDown } from 'lucide-svelte';
	import { onDestroy } from 'svelte';

	let text = $state('');
	let isUploading = $state(false);
	let uploadError = $state('');
	let fileInput;

	let playerState = $state(PlayState.IDLE);
	const unsubState = playerStore.state.subscribe((s) => (playerState = s));
	onDestroy(unsubState);

	const isGenerating = $derived(playerState === PlayState.GENERATING);
	const isBusy = $derived(isGenerating || isUploading);

	function handleGenerate() {
		if (!text.trim() || isBusy) return;

		historyStore.add({
			text: text.trim(),
			voice: $settingsStore.defaultVoice,
			speed: $settingsStore.defaultSpeed,
		});

		playerStore.generate(
			text.trim(),
			$settingsStore.defaultVoice,
			$settingsStore.defaultSpeed,
			$settingsStore.autoPlay
		);
	}

	async function handleFileUpload(event) {
		const file = event.target.files?.[0];
		if (!file) return;

		isUploading = true;
		uploadError = '';

		try {
			const result = await uploadDocument(file);
			text = result.text;
		} catch (err) {
			uploadError = err.message;
		} finally {
			isUploading = false;
			if (fileInput) fileInput.value = '';
		}
	}

	function handleKeydown(event) {
		if (event.key === 'Enter' && (event.metaKey || event.ctrlKey)) {
			event.preventDefault();
			handleGenerate();
		}
	}
</script>

<div class="space-y-4">
	<!-- Text Area -->
	<div class="relative">
		<textarea
			bind:value={text}
			onkeydown={handleKeydown}
			disabled={isBusy}
			placeholder="Enter text to convert to speech, or upload a document..."
			rows="6"
			class="input resize-none !rounded-2xl !p-4 !pr-12 !text-[15px] leading-relaxed placeholder:text-slate-600"
		></textarea>

		{#if text.length > 0}
			<div class="absolute bottom-3 right-3 text-[10px] text-slate-600 font-mono">
				{text.length} chars
			</div>
		{/if}
	</div>

	{#if uploadError}
		<div class="bg-red-500/10 border border-red-500/20 text-red-400 px-4 py-3 rounded-xl text-sm">
			{uploadError}
		</div>
	{/if}

	<!-- Controls Row -->
	<div class="flex flex-col sm:flex-row gap-3">
		<!-- Upload Button -->
		<button
			onclick={() => fileInput?.click()}
			disabled={isBusy}
			class="btn btn-secondary flex items-center justify-center gap-2 text-sm sm:w-auto"
		>
			{#if isUploading}
				<Loader2 size={16} class="animate-spin" />
				<span>Uploading...</span>
			{:else}
				<Upload size={16} />
				<span>Upload Document</span>
			{/if}
		</button>

		<input
			bind:this={fileInput}
			type="file"
			accept=".pdf,.docx,.txt"
			onchange={handleFileUpload}
			class="hidden"
		/>

		<!-- Spacer -->
		<div class="flex-1"></div>

		<!-- Voice + Speed (inline) -->
		<div class="flex items-center gap-2">
			<div class="relative">
				<select
					value={$settingsStore.defaultVoice}
					onchange={(e) => settingsStore.update('defaultVoice', e.target.value)}
					disabled={isBusy}
					class="bg-slate-900/60 border border-white/10 rounded-xl pl-3 pr-8 py-2 text-xs appearance-none focus:outline-none focus:ring-1 focus:ring-blue-500 min-w-[120px]"
				>
					<option value="af_heart">Heart</option>
					<option value="af_nova">Nova</option>
					<option value="af_sky">Sky</option>
					<option value="af_bella">Bella</option>
					<option value="af_sarah">Sarah</option>
					<option value="am_adam">Adam</option>
					<option value="am_michael">Michael</option>
					<option value="bf_emma">Emma (UK)</option>
					<option value="bf_isabella">Isabella (UK)</option>
					<option value="bm_george">George (UK)</option>
					<option value="bm_lewis">Lewis (UK)</option>
				</select>
				<div class="absolute right-2 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
					<ChevronDown size={14} />
				</div>
			</div>

			<div class="flex items-center gap-1 bg-slate-900/60 border border-white/10 rounded-xl px-3 py-2">
				<Clock size={12} class="text-slate-500" />
				<span class="text-xs font-mono text-slate-300">{$settingsStore.defaultSpeed.toFixed(1)}x</span>
			</div>
		</div>

		<!-- Generate Button -->
		<button
			onclick={handleGenerate}
			disabled={!text.trim() || isBusy}
			class="btn btn-primary flex items-center justify-center gap-2 text-sm font-bold sm:w-auto"
		>
			{#if isGenerating}
				<Loader2 size={16} class="animate-spin" />
				<span>Generating...</span>
			{:else}
				<Play size={16} />
				<span>Generate</span>
			{/if}
		</button>
	</div>

	<p class="text-[10px] text-slate-600 px-1">
		Supports PDF, DOCX, and TXT files up to 10MB. Press Ctrl+Enter to generate.
	</p>
</div>
