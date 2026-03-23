/**
 * Draft store — persists the current text input across tab switches.
 * Also provides save-to-history for text-only entries (dictation, notes).
 */
import { writable } from 'svelte/store';
import { historyStore } from './history';

const STORAGE_KEY = 'openmobiletts_draft';

function loadDraft() {
	try {
		return localStorage.getItem(STORAGE_KEY) || '';
	} catch {
		return '';
	}
}

function createDraftStore() {
	const initial = loadDraft();
	const { subscribe, set, update } = writable(initial);

	let currentValue = initial;
	let saveTimer = null;

	// Keep currentValue in sync; debounce localStorage writes to avoid
	// blocking the main thread during large pastes on mobile WebViews
	subscribe((v) => {
		currentValue = v;
		if (typeof localStorage === 'undefined') return;
		if (saveTimer) clearTimeout(saveTimer);
		saveTimer = setTimeout(() => {
			try {
				if (currentValue) {
					localStorage.setItem(STORAGE_KEY, currentValue);
				} else {
					localStorage.removeItem(STORAGE_KEY);
				}
			} catch {
				// ignore — localStorage full or unavailable
			}
		}, 300);
	});

	return {
		subscribe,
		set,
		update,

		/** Get current value synchronously */
		get() {
			return currentValue;
		},

		/** Save current draft text as a text-only history entry */
		saveAsNote(voice) {
			const text = currentValue.trim();
			if (!text) return null;
			const id = historyStore.add({
				text,
				voice: voice || 'none',
				speed: 1.0,
			});
			return id;
		},

		/** Clear the draft (flushes immediately to localStorage) */
		clear() {
			if (saveTimer) clearTimeout(saveTimer);
			set('');
			try { localStorage.removeItem(STORAGE_KEY); } catch {}
		},
	};
}

export const draftStore = createDraftStore();
