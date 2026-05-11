<script>
	import { playerStore, PlayState } from '$lib/stores/player';
	import { settingsStore } from '$lib/stores/settings';
	import { historyStore } from '$lib/stores/history';
	import { draftStore } from '$lib/stores/draft';
	import { batchTranscriptionStore } from '$lib/stores/batchTranscription';
	import Waveform from '$lib/components/Waveform.svelte';
	import { uploadDocument, fetchVoices, fetchEngines, transcribeAudio, exportDocument, fetchSttModels } from '$lib/services/api';
	import { Upload, Loader2, Play, ChevronDown, Cpu, X, Mic, Square, Download, AlertTriangle, Save } from 'lucide-svelte';
	import { onMount, onDestroy } from 'svelte';

	let text = $state(draftStore.get());
	let isUploading = $state(false);
	let uploadError = $state('');
	let fileInput;
	let batchFileInput;
	let voices = $state([]);
	let selectedLang = $state('');
	let activeEngine = $state('');
	const isAndroid = typeof window !== 'undefined' && !!window.Android;
	const audioExtensions = ['mp3', 'aac', 'ogg', 'wav', 'webm', 'm4a'];
	const documentExtensions = ['pdf', 'doc', 'docx', 'txt', 'md'];

	// Export state
	let showExportPicker = $state(false);
	let isExporting = $state(false);
	// Batch transcription state is held in batchTranscriptionStore so it survives view switches.
	const isBatchTranscribing = $derived($batchTranscriptionStore.active);
	const batchStatus = $derived($batchTranscriptionStore.status);
	const batchProgress = $derived($batchTranscriptionStore.progress);

	// STT model availability
	let sttModelMissing = $state(false);

	// STT recording state
	let isRecording = $state(false);
	let isTranscribing = $state(false);
	let recordingDuration = $state(0);
	let mediaRecorder = null;
	let audioChunks = [];
	let recordingTimer = null;
	let activeStream = $state(null);

	// Two-way sync with draft store: persist across tab switches and respond to external clears
	const unsubDraft = draftStore.subscribe((v) => {
		if (v !== text) {
			text = v;
		}
	});
	$effect(() => {
		const v = text;
		if (v !== draftStore.get()) {
			draftStore.set(v);
		}
	});
	onDestroy(() => unsubDraft());

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

	// Synchronous onMount — returns cleanup function correctly
	onMount(() => {
		// Kick off async fetch (not awaited — onMount must be sync for cleanup to work)
		(async () => {
			try {
				const [v, e] = await Promise.all([fetchVoices(), fetchEngines()]);
				voices = v;
				const active = e.find((eng) => eng.active);
				activeEngine = active ? active.label : '';
				const current = voices.find((vv) => vv.name === $settingsStore.defaultVoice);
				selectedLang = current ? current.language : (voices[0]?.language || '');
			} catch {
				// Fallback — keep empty, selects will be hidden
			}

			// Check STT model availability (retry up to 3 times — server may still be starting)
			for (let attempt = 0; attempt < 3; attempt++) {
				try {
					if (attempt > 0) await new Promise(r => setTimeout(r, 2000));
					const result = await fetchSttModels();
					const models = result.models || [];
					const activeModel = models.find(m => m.active || m.downloaded);
					sttModelMissing = !activeModel;
					break;
				} catch {
					// Don't block dictate on network errors — let the server-side
					// handle "model not found" when the user actually tries to record
					sttModelMissing = false; // allow dictate attempt, server will error if truly missing
				}
			}
		})();

		// Close dropdowns on outside tap
		function handleGlobalClick(e) {
			if (showExportPicker && !e.target.closest('[data-export-picker]')) {
				showExportPicker = false;
			}
		}
		document.addEventListener('click', handleGlobalClick);
		return () => document.removeEventListener('click', handleGlobalClick);
	});

	let playerState = $state(PlayState.IDLE);
	const unsubState = playerStore.state.subscribe((s) => (playerState = s));
	onDestroy(() => {
		unsubState();
		// Clean up any active recording on component destroy
		if (mediaRecorder && mediaRecorder.state !== 'inactive') {
			mediaRecorder.stream?.getTracks().forEach(t => t.stop());
			mediaRecorder.stop();
		}
		clearInterval(recordingTimer);
	});

	const isGenerating = $derived(playerState === PlayState.GENERATING);
	const isBusy = $derived(isGenerating || isUploading || isRecording || isTranscribing || isBatchTranscribing || isExporting);

	function handleGenerate() {
		if (!text.trim() || isBusy) return;
		uploadError = '';

		const selectedSpeed = $settingsStore.defaultSpeed || 1.0;
		const historyId = historyStore.add({
			text: text.trim(),
			voice: $settingsStore.defaultVoice,
			speed: selectedSpeed,
		});

		// Mark as generate-tab playback so TextDisplay/GenerationProgress show on this tab
		playerStore.playbackSource.set('generate');
		playerStore.generate(
			text.trim(),
			$settingsStore.defaultVoice,
			selectedSpeed,
			$settingsStore.autoPlay,
			historyId
		);

		// Clear the input — the text is captured by generate() and in history
		text = '';
	}

	// ── Export ──────────────────────────────────────

	async function handleExport(format) {
		if (!text.trim() || isExporting) return;
		showExportPicker = false;
		isExporting = true;
		uploadError = '';

		try {
			const title = text.trim().split('\n')[0].substring(0, 50) || 'Export';
			const blob = await exportDocument(text.trim(), title, format);
			const sanitized = title.replace(/[^a-zA-Z0-9._-]/g, '_').substring(0, 50);
			const filename = `${sanitized}.${format}`;

			const mimeTypes = { pdf: 'application/pdf', md: 'text/markdown', txt: 'text/plain' };

			if (isAndroid && window.Android?.saveAudioFile) {
				// Android WebView: use native bridge to save file
				const reader = new FileReader();
				reader.onload = () => {
					const base64 = reader.result.split(',')[1];
					window.Android.saveAudioFile(base64, filename, mimeTypes[format] || 'application/octet-stream');
				};
				reader.onerror = () => {
					uploadError = 'Failed to read export data';
				};
				reader.readAsDataURL(blob);
			} else {
				// Desktop: trigger browser download
				const url = URL.createObjectURL(blob);
				const a = document.createElement('a');
				a.href = url;
				a.download = filename;
				document.body.appendChild(a);
				a.click();
				document.body.removeChild(a);
				setTimeout(() => URL.revokeObjectURL(url), 1000);
			}
		} catch (err) {
			uploadError = err.message || 'Export failed';
		} finally {
			isExporting = false;
		}
	}

	// ── STT Recording ──────────────────────────────

	async function startRecording() {
		if (isRecording || isGenerating || isTranscribing) return;
		uploadError = '';

		// Re-check STT model availability live (don't rely on stale onMount flag)
		try {
			const result = await fetchSttModels();
			const models = result.models || [];
			const activeModel = models.find(m => m.active || m.downloaded);
			sttModelMissing = !activeModel;
			if (!activeModel) {
				uploadError = 'Speech-to-text model not installed. Go to Settings → Speech-to-Text to download it.';
				return;
			}
		} catch {
			// Server may not support STT models endpoint — proceed anyway
			// The transcribe endpoint will return an error if the model is truly missing
		}

		try {
			const stream = await navigator.mediaDevices.getUserMedia({
				audio: { channelCount: 1, echoCancellation: true }
			});

			audioChunks = [];
			recordingDuration = 0;

			// Use audio/webm if available, fallback to default
			const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus')
				? 'audio/webm;codecs=opus'
				: undefined;

			mediaRecorder = new MediaRecorder(stream, mimeType ? { mimeType } : undefined);

			mediaRecorder.ondataavailable = (e) => {
				if (e.data.size > 0) audioChunks.push(e.data);
			};

			mediaRecorder.onstop = async () => {
				// Stop all tracks to release mic
				stream.getTracks().forEach(t => t.stop());
				activeStream = null;
				clearInterval(recordingTimer);

				if (audioChunks.length === 0) {
					uploadError = 'Recording too short. Try again.';
					isRecording = false;
					return;
				}

				// Convert to WAV for the server
				const audioBlob = new Blob(audioChunks, { type: mediaRecorder.mimeType });
				isTranscribing = true;  // set before clearing isRecording to keep isBusy true
				isRecording = false;

				try {
					// Convert to WAV (16kHz mono 16-bit PCM)
					const wavBlob = await convertToWav(audioBlob);
					const result = await transcribeAudio(wavBlob);

					if (result.text && result.text.trim()) {
						const transcribedText = result.text.trim();
						// Append to existing text with newline separator
						text = text ? text + '\n' + transcribedText : transcribedText;
						// Auto-save the dictation segment to history as a text-only entry
						historyStore.add({
							text: transcribedText,
							voice: $settingsStore.defaultVoice,
							speed: 1.0,
						});
					} else {
						uploadError = 'No speech detected. Try again.';
					}
				} catch (err) {
					uploadError = err.message || 'Transcription failed';
				} finally {
					isTranscribing = false;
				}
			};

			activeStream = stream;  // set before isRecording so Waveform mounts with non-null stream
			mediaRecorder.start(250);
			isRecording = true;

			// Track recording duration
			recordingTimer = setInterval(() => {
				recordingDuration++;
			}, 1000);

		} catch (err) {
			if (err.name === 'NotAllowedError') {
				uploadError = 'Microphone access denied. Enable it in your browser or device settings.';
			} else {
				uploadError = 'Could not access microphone: ' + (err.message || 'Unknown error');
			}
		}
	}

	function stopRecording() {
		clearInterval(recordingTimer);
		if (mediaRecorder && mediaRecorder.state !== 'inactive') {
			mediaRecorder.stop();
		}
	}

	/**
	 * Convert an audio blob (webm/ogg) to WAV (16kHz mono 16-bit PCM).
	 * Uses AudioContext to decode and resample.
	 */
	async function convertToWav(audioBlob) {
		const audioContext = new (window.AudioContext || window.webkitAudioContext)();
		try {
			const arrayBuffer = await audioBlob.arrayBuffer();
			const audioBuffer = await audioContext.decodeAudioData(arrayBuffer);

			// Get mono channel (mix down if stereo)
			const numSamples = audioBuffer.length;
			const channelData = audioBuffer.numberOfChannels > 1
				? mixToMono(audioBuffer)
				: audioBuffer.getChannelData(0);

			// Resample to 16kHz if needed
			let samples = channelData;
			if (audioBuffer.sampleRate !== 16000) {
				const ratio = 16000 / audioBuffer.sampleRate;
				const newLength = Math.round(numSamples * ratio);
				samples = new Float32Array(newLength);
				for (let i = 0; i < newLength; i++) {
					const srcIndex = i / ratio;
					const idx = Math.floor(srcIndex);
					const frac = srcIndex - idx;
					samples[i] = idx + 1 < channelData.length
						? channelData[idx] * (1 - frac) + channelData[idx + 1] * frac
						: channelData[idx] || 0;
				}
			}

			// Encode as WAV
			const wavBuffer = encodeWav(samples, 16000);
			return new Blob([wavBuffer], { type: 'audio/wav' });
		} finally {
			await audioContext.close();
		}
	}

	function mixToMono(audioBuffer) {
		const length = audioBuffer.length;
		const mixed = new Float32Array(length);
		const channels = audioBuffer.numberOfChannels;
		for (let i = 0; i < length; i++) {
			let sum = 0;
			for (let ch = 0; ch < channels; ch++) {
				sum += audioBuffer.getChannelData(ch)[i];
			}
			mixed[i] = sum / channels;
		}
		return mixed;
	}

	function encodeWav(samples, sampleRate) {
		const buffer = new ArrayBuffer(44 + samples.length * 2);
		const view = new DataView(buffer);

		// WAV header
		writeString(view, 0, 'RIFF');
		view.setUint32(4, 36 + samples.length * 2, true);
		writeString(view, 8, 'WAVE');
		writeString(view, 12, 'fmt ');
		view.setUint32(16, 16, true); // subchunk size
		view.setUint16(20, 1, true);  // PCM format
		view.setUint16(22, 1, true);  // mono
		view.setUint32(24, sampleRate, true);
		view.setUint32(28, sampleRate * 2, true); // byte rate
		view.setUint16(32, 2, true);  // block align
		view.setUint16(34, 16, true); // bits per sample
		writeString(view, 36, 'data');
		view.setUint32(40, samples.length * 2, true);

		// PCM samples
		let offset = 44;
		for (let i = 0; i < samples.length; i++) {
			const s = Math.max(-1, Math.min(1, samples[i]));
			view.setInt16(offset, s < 0 ? s * 0x8000 : s * 0x7FFF, true);
			offset += 2;
		}

		return buffer;
	}

	function writeString(view, offset, string) {
		for (let i = 0; i < string.length; i++) {
			view.setUint8(offset + i, string.charCodeAt(i));
		}
	}

	function formatRecordingTime(seconds) {
		const m = Math.floor(seconds / 60);
		const s = seconds % 60;
		return `${m}:${s.toString().padStart(2, '0')}`;
	}

	// ── File Upload (extended for audio) ──────────

	async function handleFileUpload(event) {
		const file = event.target.files?.[0];
		if (!file) return;

		const ext = file.name.split('.').pop()?.toLowerCase();
		const allSupported = [...audioExtensions, ...documentExtensions];

		if (!allSupported.includes(ext)) {
			uploadError = `Unsupported file type: .${ext}. Supported: ${documentExtensions.join(', ')} (documents), ${audioExtensions.join(', ')} (audio).`;
			if (fileInput) fileInput.value = '';
			return;
		}

		if (audioExtensions.includes(ext)) {
			// Route audio files to STT transcription — send the raw file directly
			// to the server for decoding (server uses ffmpeg / MediaCodec which
			// handle AAC, MP3, OGG, etc. natively). Browser-side WAV conversion
			// via AudioContext.decodeAudioData() fails for many formats on Android WebView.
			isTranscribing = true;
			uploadError = '';
			try {
				const result = await transcribeAudio(file, file.name);
				if (result.text && result.text.trim()) {
					text = text ? text + '\n' + result.text.trim() : result.text.trim();
				} else {
					uploadError = 'No speech detected in audio file.';
				}
			} catch (err) {
				uploadError = err.message || 'Audio transcription failed';
			} finally {
				isTranscribing = false;
				if (fileInput) fileInput.value = '';
			}
			return;
		}

		// Document files → existing text extraction
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

	async function handleBatchUpload(event) {
		const selected = Array.from(event.target.files || []);
		if (selected.length === 0) return;

		const audioFiles = selected.filter((file) => {
			const ext = file.name.split('.').pop()?.toLowerCase();
			return audioExtensions.includes(ext);
		});

		if (audioFiles.length !== selected.length) {
			uploadError = 'Batch Upload only accepts audio files: MP3, AAC, OGG, WAV, WEBM, or M4A.';
			if (batchFileInput) batchFileInput.value = '';
			return;
		}

		uploadError = '';
		batchTranscriptionStore.clearError();
		try {
			await batchTranscriptionStore.start(audioFiles);
		} finally {
			if (batchFileInput) batchFileInput.value = '';
			if ($batchTranscriptionStore.error) {
				uploadError = $batchTranscriptionStore.error;
			}
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
			placeholder="Enter or dictate text, or upload a file..."
			rows="6"
			class="input resize-none !rounded-2xl !p-4 !pr-12 !text-[15px] leading-relaxed placeholder:text-slate-600"
		></textarea>

		{#if text.length > 0 && !isBusy}
			<button
				onclick={() => text = ''}
				class="absolute top-2.5 right-2.5 p-1 text-slate-600 hover:text-slate-300 rounded-md transition-colors"
				title="Clear text"
			>
				<X size={14} />
			</button>
		{/if}

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

	<!-- Recording indicator with waveform -->
	{#if isRecording}
		<div class="bg-red-500/10 border border-red-500/30 rounded-xl px-4 py-3 space-y-2">
			<div class="flex items-center gap-3">
				<div class="w-3 h-3 bg-red-500 rounded-full animate-pulse"></div>
				<span class="text-red-400 text-sm font-medium">Recording... {formatRecordingTime(recordingDuration)}</span>
			</div>
			{#if activeStream}
				<Waveform stream={activeStream} />
			{/if}
		</div>
	{/if}

	{#if isTranscribing}
		<div class="flex items-center gap-3 bg-blue-500/10 border border-blue-500/20 rounded-xl px-4 py-3">
			<Loader2 size={16} class="animate-spin text-blue-400" />
			<span class="text-blue-400 text-sm">Transcribing audio...</span>
		</div>
	{/if}

	{#if isBatchTranscribing || batchStatus}
		<div class="bg-blue-500/10 border border-blue-500/20 rounded-xl px-4 py-3 space-y-3">
			<div class="flex items-center gap-3">
				{#if isBatchTranscribing}
					<Loader2 size={16} class="animate-spin text-blue-400" />
				{:else}
					<Download size={16} class="text-blue-400" />
				{/if}
				<span class="text-blue-400 text-sm">{batchStatus}</span>
			</div>
			{#if batchProgress}
				<div class="space-y-2">
					<div class="h-1.5 bg-white/5 rounded-full overflow-hidden">
						<div
							class="h-full bg-blue-500 rounded-full transition-all duration-300"
							style="width: {Math.min(100, Math.round(((batchProgress.completed + batchProgress.failed) / Math.max(1, batchProgress.total)) * 100))}%"
						></div>
					</div>
					<div class="flex items-center justify-between text-[10px] text-slate-500">
						<span>{batchProgress.completed} complete</span>
						<span>{batchProgress.failed} failed</span>
						<span>{batchProgress.total} total</span>
					</div>
					{#if batchProgress.files?.length}
						<div class="max-h-28 overflow-y-auto rounded-lg border border-white/5 divide-y divide-white/5">
							{#each batchProgress.files as item}
								<div class="flex items-center justify-between gap-3 px-3 py-1.5 text-[11px]">
									<span class="truncate text-slate-300">{item.source_file}</span>
									<span class="shrink-0 {item.status === 'complete' ? 'text-emerald-400' : item.status === 'failed' ? 'text-red-400' : item.status === 'running' ? 'text-blue-400' : 'text-slate-500'}">
										{item.status}
									</span>
								</div>
							{/each}
						</div>
					{/if}
				</div>
			{/if}
		</div>
	{/if}

	<!-- Input sources + voice config -->
	<div class="flex flex-col sm:flex-row sm:items-center sm:flex-wrap gap-3">
		<!-- Mic Button -->
		<button
			onclick={isRecording ? stopRecording : startRecording}
			disabled={isGenerating || isUploading || isTranscribing || isBatchTranscribing}
			class="btn flex items-center justify-center gap-2 text-sm sm:w-auto {isRecording ? 'bg-red-500/20 border-red-500/40 text-red-400 hover:bg-red-500/30' : sttModelMissing ? 'btn-secondary opacity-60' : 'btn-secondary'}"
			title={sttModelMissing ? 'STT model not installed — go to Settings to download' : 'Record audio for transcription'}
		>
			{#if isRecording}
				<Square size={16} />
				<span>Stop</span>
			{:else if sttModelMissing}
				<AlertTriangle size={16} class="text-amber-400" />
				<span>Dictate</span>
			{:else}
				<Mic size={16} />
				<span>Dictate</span>
			{/if}
		</button>

		<!-- Upload Button -->
		<button
			onclick={() => fileInput?.click()}
			disabled={isBusy}
			class="btn btn-secondary flex items-center justify-center gap-2 text-sm sm:w-auto"
		>
			{#if isUploading || isTranscribing}
				<Loader2 size={16} class="animate-spin" />
				<span>{isTranscribing ? 'Transcribing...' : 'Uploading...'}</span>
			{:else}
				<Upload size={16} />
				<span>Upload</span>
			{/if}
		</button>

		<input
			bind:this={fileInput}
			type="file"
			accept=".pdf,.doc,.docx,.txt,.md,.mp3,.aac,.ogg,.wav,.webm,.m4a"
			onchange={handleFileUpload}
			class="hidden"
		/>

		<!-- Batch Upload Button -->
		<button
			onclick={() => batchFileInput?.click()}
			disabled={isBusy}
			class="btn btn-secondary flex items-center justify-center gap-2 text-sm sm:w-auto"
			title="Select multiple audio files and download Markdown transcripts as a ZIP"
		>
			{#if isBatchTranscribing}
				<Loader2 size={16} class="animate-spin" />
				<span>Batching...</span>
			{:else}
				<Download size={16} />
				<span>Batch Upload</span>
			{/if}
		</button>

		<input
			bind:this={batchFileInput}
			type="file"
			multiple
			accept=".mp3,.aac,.ogg,.wav,.webm,.m4a"
			onchange={handleBatchUpload}
			class="hidden"
		/>

		<!-- Engine + Language + Voice (pushed right on desktop) -->
		<div class="flex items-center gap-2 flex-wrap sm:ml-auto">
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
		</div>
	</div>

	<!-- Output actions -->
	<div class="flex flex-col sm:flex-row sm:items-center sm:justify-end gap-3">
		<!-- Save as Note Button -->
		<button
			onclick={() => {
				if (!text.trim()) return;
				historyStore.add({
					text: text.trim(),
					voice: $settingsStore.defaultVoice,
					speed: 1.0,
				});
				text = '';
			}}
			disabled={!text.trim() || isBusy}
			class="btn btn-secondary flex items-center justify-center gap-2 text-sm sm:w-auto"
			title="Save text to history without generating audio"
		>
			<Save size={16} />
			<span>Save</span>
		</button>

		<!-- Export Button -->
		<div class="relative" data-export-picker>
			<button
				onclick={() => showExportPicker = !showExportPicker}
				disabled={!text.trim() || isBusy}
				class="btn btn-secondary flex items-center justify-center gap-2 text-sm w-full sm:w-auto"
			>
				{#if isExporting}
					<Loader2 size={16} class="animate-spin" />
					<span>Exporting...</span>
				{:else}
					<Download size={16} />
					<span>Export</span>
				{/if}
			</button>
			{#if showExportPicker}
				<div class="absolute bottom-full mb-1 right-0 bg-slate-800 border border-white/10 rounded-lg shadow-xl py-1 min-w-[120px] z-50">
					<button
						onclick={() => handleExport('pdf')}
						class="w-full px-3 py-2.5 text-left text-xs transition-colors text-slate-300 hover:bg-white/5"
					>
						PDF
					</button>
					<button
						onclick={() => handleExport('md')}
						class="w-full px-3 py-2.5 text-left text-xs transition-colors text-slate-300 hover:bg-white/5"
					>
						Markdown
					</button>
					<button
						onclick={() => handleExport('txt')}
						class="w-full px-3 py-2.5 text-left text-xs transition-colors text-slate-300 hover:bg-white/5"
					>
						Plain Text
					</button>
				</div>
			{/if}
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
		{isAndroid ? 'Dictate or upload documents (PDF, DOCX, TXT, MD) and audio files (MP3, AAC, WAV).' : 'Upload one document or audio file, or batch upload audio files for a Markdown ZIP. Press Ctrl+Enter to generate.'}
	</p>
</div>
