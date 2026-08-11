/**
 * API service — fetch wrapper for the local TTS server.
 * No authentication needed (single-user local app).
 *
 * On the web (same-origin), base URL is empty so all fetches are relative.
 * On remote clients, the user sets a server URL in Settings, which is
 * read from localStorage and prepended to all API paths.
 */

const SETTINGS_KEY = 'openmobiletts_settings';

/**
 * Get the base URL for API requests.
 * Reads serverUrl from localStorage (same key used by settings store).
 * Returns '' for same-origin web mode, or 'http://192.168.x.x:8000' for Android.
 */
function getBaseUrl() {
	// The Android shell owns an in-process loopback backend. Never let a stale
	// browser preference redirect its privileged WebView to a remote server.
	if (typeof window !== 'undefined' && window.Android) return '';
	try {
		const stored = localStorage.getItem(SETTINGS_KEY);
		if (stored) {
			const settings = JSON.parse(stored);
			if (settings.serverUrl) {
				// Strip trailing slash
				return settings.serverUrl.replace(/\/+$/, '');
			}
		}
	} catch {
		// ignore parse errors
	}
	return '';
}

/**
 * Build a full API URL from a path.
 * @param {string} path - API path (e.g., '/api/tts/stream')
 * @returns {string} Full URL
 */
export function apiUrl(path) {
	return `${getBaseUrl()}${path}`;
}

/**
 * Fetch the backend feature contract used by the shared UI.
 * @returns {Promise<{schema_version: number, platform: string, features: Record<string, boolean>}>}
 */
export async function fetchCapabilities() {
	const res = await fetch(apiUrl('/api/capabilities'));
	if (!res.ok) throw new Error('Failed to fetch platform capabilities');
	return res.json();
}

/** Fetch the repository-owned model identity, integrity, and license catalog. */
export async function fetchModelCatalog() {
	const res = await fetch(apiUrl('/api/models/catalog'));
	if (!res.ok) throw new Error('Failed to fetch model catalog');
	return res.json();
}

/**
 * Fetch available voices from the server.
 * @returns {Promise<Array<{name: string, language: string}>>}
 */
export async function fetchVoices() {
	const res = await fetch(apiUrl('/api/voices'));
	if (!res.ok) throw new Error('Failed to fetch voices');
	return res.json();
}

/**
 * Upload a document and extract text.
 * @param {File} file
 * @returns {Promise<{filename: string, text: string, chunk_count: number}>}
 */
export async function uploadDocument(file) {
	const formData = new FormData();
	formData.append('file', file);

	const res = await fetch(apiUrl('/api/documents/upload'), {
		method: 'POST',
		body: formData,
	});

	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Upload failed');
	}

	return res.json();
}

/**
 * Fetch available TTS engines.
 * @returns {Promise<Array<{name: string, label: string, available: boolean, active: boolean}>>}
 */
export async function fetchEngines() {
	const res = await fetch(apiUrl('/api/engines'));
	if (!res.ok) throw new Error('Failed to fetch engines');
	return res.json();
}

/**
 * Switch the active TTS engine.
 * @param {string} name - Engine name (e.g., 'kokoro', 'sherpa-onnx')
 * @returns {Promise<{engine: string, voices: number, restart_required?: boolean}>}
 */
export async function switchEngine(name) {
	const res = await fetch(apiUrl('/api/engine/switch'), {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ engine: name }),
	});
	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Failed to switch engine');
	}
	return res.json();
}

/**
 * Check server health.
 * @returns {Promise<{status: string, version: string}>}
 */
export async function healthCheck() {
	const res = await fetch(apiUrl('/api/health'));
	if (!res.ok) throw new Error('Server unhealthy');
	return res.json();
}

/**
 * Transcribe audio to text via Moonshine STT.
 * @param {Blob|File} audioBlob - Audio data (any format: WAV, MP3, AAC, OGG, etc.)
 * @param {string} filename - Filename hint for the server
 * @returns {Promise<{text: string, duration_ms: number, model: string}>}
 */
export async function transcribeAudio(audioBlob, filename = 'recording.wav') {
	const formData = new FormData();
	formData.append('audio', audioBlob, filename);

	const res = await fetch(apiUrl('/api/stt/transcribe'), {
		method: 'POST',
		body: formData,
	});

	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Transcription failed');
	}

	return res.json();
}

/**
 * Batch transcribe audio files and return a ZIP of Markdown transcripts.
 * @param {File[]} files - Audio files to transcribe sequentially
 * @returns {Promise<Blob>} ZIP file containing Markdown transcripts
 */
export async function transcribeAudioBatch(files) {
	const formData = new FormData();
	for (const file of files) {
		formData.append('files', file, file.name);
	}

	const res = await fetch(apiUrl('/api/stt/batch'), {
		method: 'POST',
		body: formData,
	});

	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Batch transcription failed');
	}

	return res.blob();
}

/**
 * Create a background batch transcription job.
 * @param {File[]} files - Audio files to transcribe sequentially
 * @returns {Promise<{id: string, status: string, total: number, completed: number, failed: number, current_file?: string, result_url?: string, error?: string, files: Array}>}
 */
export async function createBatchTranscriptionJob(files) {
	const formData = new FormData();
	for (const file of files) {
		formData.append('files', file, file.name);
	}

	const res = await fetch(apiUrl('/api/stt/batch/jobs'), {
		method: 'POST',
		body: formData,
	});

	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Batch transcription failed');
	}

	return res.json();
}

/**
 * Fetch background batch transcription job status.
 * @param {string} jobId
 * @returns {Promise<object>}
 */
export async function fetchBatchTranscriptionJob(jobId) {
	const res = await fetch(apiUrl(`/api/stt/batch/jobs/${jobId}`));
	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Failed to fetch batch status');
	}
	return res.json();
}

/**
 * Download a completed batch transcription ZIP.
 * @param {string} jobId
 * @returns {Promise<Blob>}
 */
export async function downloadBatchTranscriptionZip(jobId) {
	const res = await fetch(apiUrl(`/api/stt/batch/jobs/${jobId}/download`));
	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Failed to download batch ZIP');
	}
	return res.blob();
}

/**
 * Get available STT models and their status.
 * @returns {Promise<{models: Array<object>}>}
 */
export async function fetchSttModels() {
	const res = await fetch(apiUrl('/api/stt/models'));
	if (!res.ok) throw new Error('Failed to fetch STT models');
	return res.json();
}

/**
 * Start downloading the pinned STT model.
 * @param {string} modelId
 * @returns {Promise<{status: string, model: object}>}
 */
export async function downloadSttModel(modelId) {
	const res = await fetch(apiUrl('/api/stt/models/download'), {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ model: modelId }),
	});
	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Failed to start model download');
	}
	return res.json();
}

/** Pause the durable Android STT model download. */
export async function cancelSttModelDownload() {
	const res = await fetch(apiUrl('/api/stt/models/download/cancel'), { method: 'POST' });
	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Failed to pause model download');
	}
	return res.json();
}

/** Fetch Android on-device TTS models and their install/activation state. */
export async function fetchTtsModels() {
	const res = await fetch(apiUrl('/api/tts/models'));
	if (!res.ok) throw new Error('Failed to fetch voice models');
	return res.json();
}

/** Start or resume an Android on-device TTS model download. */
export async function downloadTtsModel(modelId) {
	const res = await fetch(apiUrl('/api/tts/models/download'), {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ model: modelId }),
	});
	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Failed to start voice model download');
	}
	return res.json();
}

/** Pause one durable Android TTS model download. */
export async function cancelTtsModelDownload(modelId) {
	const res = await fetch(apiUrl('/api/tts/models/download/cancel'), {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ model: modelId }),
	});
	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Failed to pause voice model download');
	}
	return res.json();
}

/** Activate an installed Android TTS model. */
export async function activateTtsModel(modelId) {
	const res = await fetch(apiUrl('/api/tts/models/activate'), {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ model: modelId }),
	});
	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Failed to activate voice model');
	}
	return res.json();
}

/** Remove an inactive experimental Android TTS model. */
export async function deleteTtsModel(modelId) {
	const res = await fetch(apiUrl(`/api/tts/models/${encodeURIComponent(modelId)}`), {
		method: 'DELETE',
	});
	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Failed to remove voice model');
	}
	return res.json();
}

/**
 * Export text as a document file (PDF, MD, or TXT).
 * @param {string} text - The text content to export
 * @param {string} title - Document title
 * @param {'pdf'|'md'|'txt'} format - Export format
 * @returns {Promise<Blob>} The exported file as a Blob
 */
export async function exportDocument(text, title, format) {
	const res = await fetch(apiUrl(`/api/export/${format}`), {
		method: 'POST',
		headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ text, title }),
	});

	if (!res.ok) {
		const err = await res.json().catch(() => ({ detail: res.statusText }));
		throw new Error(err.detail || 'Export failed');
	}

	return res.blob();
}
