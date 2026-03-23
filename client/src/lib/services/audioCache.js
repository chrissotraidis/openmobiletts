/**
 * Audio cache service — stores audio blobs in IndexedDB for instant history playback.
 */

const DB_NAME = 'openmobiletts_audio';
const DB_VERSION = 1;
const STORE_NAME = 'audio_blobs';
const MAX_ENTRIES = 50; // Limit cache size
const MAX_SIZE_BYTES = 200 * 1024 * 1024; // 200MB total cache size

let db = null;
let dbPromise = null;

/**
 * Initialize IndexedDB connection (concurrency-safe — caches the in-flight promise)
 */
async function initDB() {
	if (db) return db;
	if (dbPromise) return dbPromise;

	dbPromise = new Promise((resolve, reject) => {
		const request = indexedDB.open(DB_NAME, DB_VERSION);

		request.onerror = () => { dbPromise = null; reject(request.error); };
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
	return dbPromise;
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

		// Await the write transaction fully — without this, large blobs (40MB+)
		// can silently fail to persist, leaving nothing to read from history.
		await new Promise((resolve, reject) => {
			const transaction = database.transaction([STORE_NAME], 'readwrite');
			const store = transaction.objectStore(STORE_NAME);

			const entry = {
				id: historyId,
				audioData: arrayBuffer,
				mimeType: blob.type || 'audio/mpeg',
				timingData: timingData,
				size: arrayBuffer.byteLength,
				createdAt: Date.now(),
			};

			store.put(entry);
			transaction.oncomplete = () => resolve();
			transaction.onerror = () => reject(transaction.error);
			transaction.onabort = () => reject(transaction.error || new Error('Transaction aborted'));
		});

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
					// Convert ArrayBuffer back to Blob, using stored MIME type
					const mime = result.mimeType || 'audio/mpeg';
					const blob = new Blob([result.audioData], { type: mime });
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
 * Get all cached audio IDs (lightweight — doesn't load audio data)
 */
export async function getCachedIds() {
	try {
		const database = await initDB();
		const transaction = database.transaction([STORE_NAME], 'readonly');
		const store = transaction.objectStore(STORE_NAME);
		return new Promise((resolve) => {
			const request = store.getAllKeys();
			request.onsuccess = () => resolve(new Set(request.result));
			request.onerror = () => resolve(new Set());
		});
	} catch {
		return new Set();
	}
}

/**
 * Remove cached audio by history ID
 */
export async function removeCachedAudio(historyId) {
	try {
		const database = await initDB();
		await new Promise((resolve, reject) => {
			const transaction = database.transaction([STORE_NAME], 'readwrite');
			const store = transaction.objectStore(STORE_NAME);
			store.delete(historyId);
			transaction.oncomplete = () => resolve();
			transaction.onerror = () => reject(transaction.error);
		});
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
		await new Promise((resolve, reject) => {
			const transaction = database.transaction([STORE_NAME], 'readwrite');
			const store = transaction.objectStore(STORE_NAME);
			store.clear();
			transaction.oncomplete = () => resolve();
			transaction.onerror = () => reject(transaction.error);
		});
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
		await new Promise((resolve, reject) => {
			const transaction = database.transaction([STORE_NAME], 'readwrite');
			const store = transaction.objectStore(STORE_NAME);
			const index = store.index('createdAt');

			// Get all entries sorted by creation time
			const request = index.getAll();

			request.onsuccess = () => {
				const entries = request.result;
				let totalSize = entries.reduce((sum, e) => sum + (e.size || 0), 0);
				let count = entries.length;

				// Remove oldest entries if over limits
				for (let i = 0; i < entries.length && (count > MAX_ENTRIES || totalSize > MAX_SIZE_BYTES); i++) {
					store.delete(entries[i].id);
					totalSize -= (entries[i].size || 0);
					count--;
				}
			};

			// Transaction commits when all requests complete
			transaction.oncomplete = () => resolve();
			transaction.onerror = () => reject(transaction.error);
		});
	} catch (err) {
		console.warn('Failed to cleanup cache:', err);
	}
}
