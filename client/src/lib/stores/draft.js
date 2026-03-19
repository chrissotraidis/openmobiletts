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
	const { subscribe, set, update } = writable(loadDraft());

	let currentValue = loadDraft();

	// Keep currentValue in sync
	subscribe((v) => {
		currentValue = v;
		try {
			if (v) {
				localStorage.setItem(STORAGE_KEY, v);
			} else {
				localStorage.removeItem(STORAGE_KEY);
			}
		} catch {
			// ignore
		}
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

		/** Clear the draft */
		clear() {
			set('');
		},
	};
}

export const draftStore = createDraftStore();
