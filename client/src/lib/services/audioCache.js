/**
 * Audio cache service — stores audio blobs in IndexedDB for instant history playback.
 */

const DB_NAME = 'openmobiletts_audio';
const DB_VERSION = 1;
const STORE_NAME = 'audio_blobs';
const MAX_ENTRIES = 50; // Limit cache size
const MAX_SIZE_BYTES = 200 * 1024 * 1024; // 200MB total cache size

let db = null;

/**
 * Initialize IndexedDB connection
 */
async function initDB() {
	if (db) return db;

	return new Promise((resolve, reject) => {
		const request = indexedDB.open(DB_NAME, DB_VERSION);

		request.onerror = () => reject(request.error);
		request.onsuccess = () => {
			db = request.result;
			resolve(db);
		};

		request.onupgradeneeded = (event) => {
			const database = event.target.result;
			if (!database.objectStoreNames.contains(STORE_NAME)) {
				const store = database.createObjectStore(STORE_NAME, { keyPath: 'id' });
				store.createIndex('createdAt', 'createdAt', { unique: false });
			}
		};
	});
}

/**
 * Generate a cache key from entry parameters
 */
export function getCacheKey(text, voice, speed) {
	// Use a hash of text + voice + speed as the key
	const content = `${text}|${voice}|${speed}`;
	let hash = 0;
	for (let i = 0; i < content.length; i++) {
		const char = content.charCodeAt(i);
		hash = ((hash << 5) - hash) + char;
		hash = hash & hash; // Convert to 32bit integer
	}
	return `audio_${Math.abs(hash)}`;
}

/**
 * Store audio blob with timing data
 */
export async function cacheAudio(historyId, blob, timingData) {
	try {
		// Convert blob to ArrayBuffer BEFORE starting transaction
		// (IndexedDB transactions auto-commit when awaiting non-transaction operations)
		const arrayBuffer = await blob.arrayBuffer();

		const database = await initDB();
		const transaction = database.transaction([STORE_NAME], 'readwrite');
		const store = transaction.objectStore(STORE_NAME);

		const entry = {
			id: historyId,
			audioData: arrayBuffer,
			timingData: timingData,
			size: arrayBuffer.byteLength,
			createdAt: Date.now(),
		};

		store.put(entry);

		// Cleanup old entries to stay within limits
		await cleanupCache();

		return true;
	} catch (err) {
		console.warn('Failed to cache audio:', err);
		return false;
	}
}

/**
 * Retrieve cached audio by history ID
 */
export async function getCachedAudio(historyId) {
	try {
		const database = await initDB();
		const transaction = database.transaction([STORE_NAME], 'readonly');
		const store = transaction.objectStore(STORE_NAME);

		return new Promise((resolve, reject) => {
			const request = store.get(historyId);
			request.onerror = () => reject(request.error);
			request.onsuccess = () => {
				const result = request.result;
				if (result) {
					// Convert ArrayBuffer back to Blob
					const blob = new Blob([result.audioData], { type: 'audio/mpeg' });
					resolve({ blob, timingData: result.timingData });
				} else {
					resolve(null);
				}
			};
		});
	} catch (err) {
		console.warn('Failed to get cached audio:', err);
		return null;
	}
}

/**
 * Remove cached audio by history ID
 */
export async function removeCachedAudio(historyId) {
	try {
		const database = await initDB();
		const transaction = database.transaction([STORE_NAME], 'readwrite');
		const store = transaction.objectStore(STORE_NAME);
		store.delete(historyId);
		return true;
	} catch (err) {
		console.warn('Failed to remove cached audio:', err);
		return false;
	}
}

/**
 * Clear all cached audio
 */
export async function clearAudioCache() {
	try {
		const database = await initDB();
		const transaction = database.transaction([STORE_NAME], 'readwrite');
		const store = transaction.objectStore(STORE_NAME);
		store.clear();
		return true;
	} catch (err) {
		console.warn('Failed to clear audio cache:', err);
		return false;
	}
}

/**
 * Cleanup old entries to stay within size and count limits
 */
async function cleanupCache() {
	try {
		const database = await initDB();
		const transaction = database.transaction([STORE_NAME], 'readwrite');
		const store = transaction.objectStore(STORE_NAME);
		const index = store.index('createdAt');

		// Get all entries sorted by creation time
		const request = index.getAll();

		return new Promise((resolve) => {
			request.onsuccess = () => {
				const entries = request.result;
				let totalSize = entries.reduce((sum, e) => sum + e.size, 0);
				let count = entries.length;

				// Remove oldest entries if over limits
				const toRemove = [];
				for (let i = 0; i < entries.length && (count > MAX_ENTRIES || totalSize > MAX_SIZE_BYTES); i++) {
					toRemove.push(entries[i].id);
					totalSize -= entries[i].size;
					count--;
				}

				// Delete marked entries
				toRemove.forEach((id) => store.delete(id));
				resolve();
			};
		});
	} catch (err) {
		console.warn('Failed to cleanup cache:', err);
	}
}
