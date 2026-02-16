/**
 * API service — fetch wrapper for the local TTS server.
 * No authentication needed (single-user local app).
 */

const BASE = '';  // Same origin — served by the same process

/**
 * Fetch available voices from the server.
 * @returns {Promise<Array<{name: string, language: string}>>}
 */
export async function fetchVoices() {
	const res = await fetch(`${BASE}/api/voices`);
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

	const res = await fetch(`${BASE}/api/documents/upload`, {
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
 * Check server health.
 * @returns {Promise<{status: string, version: string}>}
 */
export async function healthCheck() {
	const res = await fetch(`${BASE}/api/health`);
	if (!res.ok) throw new Error('Server unhealthy');
	return res.json();
}
