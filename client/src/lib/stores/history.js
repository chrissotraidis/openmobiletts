/**
 * History store — persists recent TTS generations to localStorage.
 */
import { get, writable } from 'svelte/store';
import { removeCachedAudio, removeCachedAudioMany, clearAudioCache } from '$lib/services/audioCache';

const STORAGE_KEY = 'openmobiletts_history';
const MAX_ENTRIES = 50;

function loadHistory() {
	try {
		const stored = localStorage.getItem(STORAGE_KEY);
		if (stored) return JSON.parse(stored);
	} catch {
		// ignore
	}
	return [];
}

function saveHistory(entries) {
	localStorage.setItem(STORAGE_KEY, JSON.stringify(entries.slice(0, MAX_ENTRIES)));
}

let idCounter = 0;

function createHistoryStore() {
	const store = writable(loadHistory());
	const { subscribe, set, update } = store;

	return {
		subscribe,

		/**
		 * Add a new history entry.
		 * @returns {number} The ID of the new entry (for caching)
		 */
		add(entry) {
			// Use timestamp * 1000 + counter to avoid collision on rapid calls
			const id = Date.now() * 1000 + (idCounter++ % 1000);
			// Auto-generate title from first line (max 60 chars)
			const firstLine = entry.text.split('\n')[0].trim();
			const title = entry.title || (firstLine.length > 60 ? firstLine.slice(0, 57) + '...' : firstLine);
			update((entries) => {
				const next = [
					{
						id,
						title,
						text: entry.text,
						voice: entry.voice,
						speed: entry.speed,
						createdAt: new Date().toISOString(),
						preview: entry.text.slice(0, 200),
					},
					...entries,
				].slice(0, MAX_ENTRIES);
				saveHistory(next);
				return next;
			});
			return id;
		},

		/** Update fields on an existing entry (e.g. rename). */
		updateEntry(id, fields) {
			update((entries) => {
				const next = entries.map((e) =>
					e.id === id ? { ...e, ...fields } : e
				);
				saveHistory(next);
				return next;
			});
		},

		/** Return a snapshot of the visible library. */
		getEntries() {
			return get(store);
		},

		/** Merge validated backup entries without clearing current work. */
		mergeEntries(importedEntries) {
			const current = get(store);
			const byId = new Map(current.map((entry) => [entry.id, entry]));
			for (const entry of importedEntries) byId.set(entry.id, entry);
			const next = [...byId.values()]
				.sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt))
				.slice(0, MAX_ENTRIES);
			set(next);
			saveHistory(next);
			return next.length;
		},

		/** Remove entries older than the configured retention period. */
		async cleanupOlderThan(days) {
			if (!Number.isFinite(days) || days <= 0) return 0;
			const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
			const current = get(store);
			const removedIds = current
				.filter((entry) => Date.parse(entry.createdAt) < cutoff)
				.map((entry) => entry.id);
			if (removedIds.length === 0) return 0;
			const removed = new Set(removedIds);
			const next = current.filter((entry) => !removed.has(entry.id));
			set(next);
			saveHistory(next);
			await removeCachedAudioMany(removedIds);
			return removedIds.length;
		},

		remove(id) {
			update((entries) => {
				const next = entries.filter((e) => e.id !== id);
				saveHistory(next);
				return next;
			});
			// Also remove from audio cache
			removeCachedAudio(id);
		},

		clear() {
			set([]);
			localStorage.removeItem(STORAGE_KEY);
			// Also clear audio cache
			clearAudioCache();
		},
	};
}

export const historyStore = createHistoryStore();
