import { get } from 'svelte/store';
import { historyStore } from '$lib/stores/history';
import { settingsStore } from '$lib/stores/settings';

const PRODUCT = 'openmobiletts-library';
const FORMAT_VERSION = 1;
const MAX_ENTRIES = 50;
const MAX_TEXT_LENGTH = 2_000_000;
const PORTABLE_SETTINGS = ['defaultVoice', 'defaultSpeed', 'autoPlay', 'engine', 'cleanupIntervalDays'];

function portableSettings() {
	const settings = get(settingsStore);
	return Object.fromEntries(PORTABLE_SETTINGS.map((key) => [key, settings[key]]));
}

export function createLibraryBackup() {
	return {
		product: PRODUCT,
		format_version: FORMAT_VERSION,
		exported_at: new Date().toISOString(),
		audio_included: false,
		settings: portableSettings(),
		history: historyStore.getEntries(),
	};
}

function validateEntry(entry, index) {
	if (!entry || typeof entry !== 'object') throw new Error(`History entry ${index + 1} is invalid.`);
	if (!Number.isSafeInteger(entry.id) || entry.id < 0) throw new Error(`History entry ${index + 1} has an invalid ID.`);
	if (typeof entry.text !== 'string' || entry.text.length > MAX_TEXT_LENGTH) {
		throw new Error(`History entry ${index + 1} has invalid text.`);
	}
	if (typeof entry.createdAt !== 'string' || !Number.isFinite(Date.parse(entry.createdAt))) {
		throw new Error(`History entry ${index + 1} has an invalid date.`);
	}
	return {
		id: entry.id,
		title: typeof entry.title === 'string' ? entry.title.slice(0, 200) : 'Untitled',
		text: entry.text,
		voice: typeof entry.voice === 'string' ? entry.voice : 'af_heart',
		speed: Number.isFinite(entry.speed) ? entry.speed : 1,
		createdAt: entry.createdAt,
		preview: typeof entry.preview === 'string' ? entry.preview.slice(0, 200) : entry.text.slice(0, 200),
	};
}

export function parseLibraryBackup(text) {
	let payload;
	try {
		payload = JSON.parse(text);
	} catch {
		throw new Error('This file is not valid JSON.');
	}
	if (payload?.product !== PRODUCT || payload?.format_version !== FORMAT_VERSION) {
		throw new Error('This is not a supported Open Mobile TTS library backup.');
	}
	if (!Array.isArray(payload.history)) throw new Error('The backup has no history list.');
	const history = payload.history.slice(0, MAX_ENTRIES).map(validateEntry);
	const settings = payload.settings && typeof payload.settings === 'object' ? payload.settings : {};
	return { history, settings };
}

export function restoreLibraryBackup(text) {
	const parsed = parseLibraryBackup(text);
	const beforeIds = new Set(historyStore.getEntries().map((entry) => entry.id));
	historyStore.mergeEntries(parsed.history);
	settingsStore.applyPortableSettings(parsed.settings);
	return {
		imported: parsed.history.filter((entry) => !beforeIds.has(entry.id)).length,
		merged: parsed.history.length,
		total: historyStore.getEntries().length,
	};
}
