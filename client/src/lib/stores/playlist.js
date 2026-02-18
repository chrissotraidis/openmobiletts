/**
 * Playlist store — manages an ordered queue of history entries for sequential playback.
 */
import { writable, get } from 'svelte/store';

function createPlaylistStore() {
	const items = writable([]);       // Array of history entries
	const currentIndex = writable(-1); // Index of currently playing track (-1 = none)

	return {
		items,
		currentIndex,

		/** Add a history entry to the end of the queue. Returns false if already queued. */
		add(entry) {
			const current = get(items);
			if (current.some((e) => e.id === entry.id)) return false;
			items.set([...current, entry]);
			return true;
		},

		/** Remove an entry by its index in the queue. */
		remove(index) {
			const current = get(items);
			if (index < 0 || index >= current.length) return;
			const ci = get(currentIndex);
			items.set(current.filter((_, i) => i !== index));
			// Adjust currentIndex if needed
			if (index < ci) {
				currentIndex.set(ci - 1);
			} else if (index === ci) {
				// Removed the currently playing track — reset so advance() doesn't skip
				currentIndex.set(-1);
			}
		},

		/** Move an item from one index to another. */
		move(from, to) {
			const current = [...get(items)];
			if (from < 0 || from >= current.length || to < 0 || to >= current.length) return;
			const [item] = current.splice(from, 1);
			current.splice(to, 0, item);
			items.set(current);
			// Adjust currentIndex to follow the playing track
			const ci = get(currentIndex);
			if (ci === from) {
				currentIndex.set(to);
			} else if (from < ci && to >= ci) {
				currentIndex.set(ci - 1);
			} else if (from > ci && to <= ci) {
				currentIndex.set(ci + 1);
			}
		},

		/** Advance to the next track. Returns the entry or null if at end.
		 *  Only advances if already playing from the queue (currentIndex >= 0). */
		advance() {
			const ci = get(currentIndex);
			if (ci < 0) return null; // Not playing from queue — don't auto-start
			const current = get(items);
			const next = ci + 1;
			if (next < current.length) {
				currentIndex.set(next);
				return current[next];
			}
			return null;
		},

		/** Start playing from the beginning of the queue. Returns first entry or null. */
		start() {
			const current = get(items);
			if (current.length === 0) return null;
			currentIndex.set(0);
			return current[0];
		},

		/** Check if an entry ID is in the queue. */
		isInQueue(entryId) {
			return get(items).some((e) => e.id === entryId);
		},

		/** Clear the entire queue. */
		clear() {
			items.set([]);
			currentIndex.set(-1);
		},

		/** Get the number of items in the queue. */
		get count() {
			return get(items).length;
		},
	};
}

export const playlistStore = createPlaylistStore();
