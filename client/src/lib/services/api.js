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
 * @returns {Promise<{engine: string, voices: number}>}
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
 * @param {Blob} audioBlob - Audio data (WAV format preferred)
 * @returns {Promise<{text: string, duration_ms: number, model: string}>}
 */
export async function transcribeAudio(audioBlob) {
	const formData = new FormData();
	formData.append('audio', audioBlob, 'recording.wav');

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
 * Get available STT models and their status.
 * @returns {Promise<{models: Array<{name: string, size_mb: number, downloaded: boolean, active: boolean}>}>}
 */
export async function fetchSttModels() {
	const res = await fetch(apiUrl('/api/stt/models'));
	if (!res.ok) throw new Error('Failed to fetch STT models');
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
