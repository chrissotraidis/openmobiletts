<script>
	import { playerStore, PlayState } from '$lib/stores/player';
	import { settingsStore } from '$lib/stores/settings';
	import { historyStore } from '$lib/stores/history';
	import { uploadDocument, fetchVoices, fetchEngines } from '$lib/services/api';
	import { Upload, Loader2, Clock, Play, ChevronDown, Cpu } from 'lucide-svelte';
	import { onMount, onDestroy } from 'svelte';

	let text = $state('');
	let isUploading = $state(false);
	let uploadError = $state('');
	let fileInput;
	let voices = $state([]);
	let selectedLang = $state('');
	let activeEngine = $state('');

	// Group voices by language
	const languages = $derived(() => {
		const map = new Map();
		for (const v of voices) {
			if (!map.has(v.language)) {
				map.set(v.language, v.language_name);
			}
		}
		return [...map.entries()].map(([code, name]) => ({ code, name }));
	});

	const filteredVoices = $derived(() => {
		if (!selectedLang) return voices;
		return voices.filter((v) => v.language === selectedLang);
	});

	onMount(async () => {
		try {
			const [v, e] = await Promise.all([fetchVoices(), fetchEngines()]);
			voices = v;
			const active = e.find((eng) => eng.active);
			activeEngine = active ? active.label : '';
			// Derive language from current voice
			const current = voices.find((vv) => vv.name === $settingsStore.defaultVoice);
			selectedLang = current ? current.language : (voices[0]?.language || '');
		} catch {
			// Fallback — keep empty, selects will be hidden
		}
	});

	let playerState = $state(PlayState.IDLE);
	const unsubState = playerStore.state.subscribe((s) => (playerState = s));
	onDestroy(unsubState);

	const isGenerating = $derived(playerState === PlayState.GENERATING);
	const isBusy = $derived(isGenerating || isUploading);

	// Speed slider mapping: 1.0x at center (50%)
	// Position 0-50: 0.5x to 1.0x, Position 50-100: 1.0x to 2.0x
	function speedToSlider(speed) {
		if (speed <= 1.0) {
			return (speed - 0.5) * 100; // 0.5→0, 1.0→50
		} else {
			return 50 + (speed - 1.0) * 50; // 1.0→50, 2.0→100
		}
	}

	function sliderToSpeed(position) {
		if (position <= 50) {
			return 0.5 + (position / 100); // 0→0.5, 50→1.0
		} else {
			return 1.0 + ((position - 50) / 50); // 50→1.0, 100→2.0
		}
	}

	function handleGenerate() {
		if (!text.trim() || isBusy) return;

		const historyId = historyStore.add({
			text: text.trim(),
			voice: $settingsStore.defaultVoice,
			speed: $settingsStore.defaultSpeed,
		});

		playerStore.generate(
			text.trim(),
			$settingsStore.defaultVoice,
			$settingsStore.defaultSpeed,
			$settingsStore.autoPlay,
			historyId
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

		<!-- Engine + Language + Voice + Speed (inline) -->
		<div class="flex items-center gap-2">
			{#if activeEngine}
				<span class="flex items-center gap-1 text-[10px] text-slate-400 bg-white/5 border border-white/10 rounded-lg px-2 py-2 font-medium whitespace-nowrap">
					<Cpu size={11} class="text-blue-400" />
					{activeEngine}
				</span>
			{/if}
			{#if voices.length > 0}
				<!-- Language dropdown -->
				<div class="relative">
					<select
						value={selectedLang}
						onchange={(e) => {
							selectedLang = e.target.value;
							const first = voices.find((v) => v.language === selectedLang);
							if (first) settingsStore.update('defaultVoice', first.name);
						}}
						disabled={isBusy}
						class="bg-slate-900/60 border border-white/10 rounded-xl pl-3 pr-7 py-2 text-xs appearance-none focus:outline-none focus:ring-1 focus:ring-blue-500 min-w-[90px]"
					>
						{#each languages() as lang}
							<option value={lang.code}>{lang.name}</option>
						{/each}
					</select>
					<div class="absolute right-1.5 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
						<ChevronDown size={12} />
					</div>
				</div>
				<!-- Voice dropdown -->
				<div class="relative">
					<select
						value={$settingsStore.defaultVoice}
						onchange={(e) => settingsStore.update('defaultVoice', e.target.value)}
						disabled={isBusy}
						class="bg-slate-900/60 border border-white/10 rounded-xl pl-3 pr-7 py-2 text-xs appearance-none focus:outline-none focus:ring-1 focus:ring-blue-500 min-w-[100px]"
					>
						{#each filteredVoices() as v}
							<option value={v.name}>{v.display_name} ({v.gender === 'female' ? 'F' : 'M'})</option>
						{/each}
					</select>
					<div class="absolute right-1.5 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
						<ChevronDown size={12} />
					</div>
				</div>
			{/if}

			<div class="flex items-center gap-2 bg-slate-900/60 border border-white/10 rounded-xl px-3 py-2">
				<Clock size={12} class="text-slate-500" />
				<input
					type="range"
					min="0"
					max="100"
					step="1"
					value={speedToSlider($settingsStore.defaultSpeed)}
					oninput={(e) => settingsStore.update('defaultSpeed', Math.round(sliderToSpeed(parseFloat(e.target.value)) * 10) / 10)}
					disabled={isBusy}
					class="w-16 h-1 accent-blue-500"
				/>
				<span class="text-xs font-mono text-slate-300 w-8">{$settingsStore.defaultSpeed.toFixed(1)}x</span>
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
		Supports PDF, DOCX, and TXT files up to 100MB. Press Ctrl+Enter to generate.
	</p>
</div>
