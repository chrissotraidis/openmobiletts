/**
 * Settings store — persists user preferences to localStorage.
 */
import { writable } from 'svelte/store';

const STORAGE_KEY = 'openmobiletts_settings';

const defaults = {
	defaultVoice: 'af_heart',
	defaultSpeed: 1.0,
	autoPlay: true,
};

function loadSettings() {
	try {
		const stored = localStorage.getItem(STORAGE_KEY);
		if (stored) {
			return { ...defaults, ...JSON.parse(stored) };
		}
	} catch {
		// ignore parse errors
	}
	return { ...defaults };
}

function createSettingsStore() {
	const { subscribe, set, update: storeUpdate } = writable(loadSettings());

	return {
		subscribe,
		update(key, value) {
			storeUpdate((s) => {
				const next = { ...s, [key]: value };
				localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
				return next;
			});
		},
		reset() {
			localStorage.removeItem(STORAGE_KEY);
			set({ ...defaults });
		},
	};
}

export const settingsStore = createSettingsStore();
