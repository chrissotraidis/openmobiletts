/**
 * History store — persists recent TTS generations to localStorage.
 */
import { writable } from 'svelte/store';
import { removeCachedAudio, clearAudioCache } from '$lib/services/audioCache';

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
	const { subscribe, set, update } = writable(loadHistory());

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
