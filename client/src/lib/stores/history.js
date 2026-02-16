/**
 * History store — persists recent TTS generations to localStorage.
 */
import { writable } from 'svelte/store';

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

function createHistoryStore() {
	const { subscribe, set, update } = writable(loadHistory());

	return {
		subscribe,

		add(entry) {
			update((entries) => {
				const next = [
					{
						id: Date.now(),
						text: entry.text,
						voice: entry.voice,
						speed: entry.speed,
						createdAt: new Date().toISOString(),
						preview: entry.text.slice(0, 80),
					},
					...entries,
				].slice(0, MAX_ENTRIES);
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
		},

		clear() {
			set([]);
			localStorage.removeItem(STORAGE_KEY);
		},
	};
}

export const historyStore = createHistoryStore();
