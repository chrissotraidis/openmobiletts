/**
 * Settings store — persists user preferences to localStorage.
 */
import { writable } from 'svelte/store';

const STORAGE_KEY = 'openmobiletts_settings';

const defaults = {
	defaultVoice: 'af_heart',
	defaultSpeed: 1.0,
	autoPlay: true,
	serverUrl: '',
	engine: 'kokoro',
};

function loadSettings() {
	try {
		const stored = localStorage.getItem(STORAGE_KEY);
		if (stored) {
			const settings = { ...defaults, ...JSON.parse(stored) };
			// One-time migration: the old range slider had a drag bug that
			// accidentally set speed to 0.5. Reset to 1.0 for affected users.
			if (!settings._migratedSpeedV1) {
				settings.defaultSpeed = defaults.defaultSpeed;
				settings._migratedSpeedV1 = true;
				localStorage.setItem(STORAGE_KEY, JSON.stringify(settings));
			}
			return settings;
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
			storeUpdate((s) => {
				const next = { ...defaults, serverUrl: s.serverUrl };
				localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
				return next;
			});
		},
	};
}

export const settingsStore = createSettingsStore();
