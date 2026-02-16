/**
 * Auth store — stub for backwards compatibility.
 * Authentication has been removed (single-user local app).
 */
import { writable } from 'svelte/store';

function createAuthStore() {
	const { subscribe } = writable({ authenticated: true });

	return {
		subscribe,
		setToken() {},
		clearToken() {},
	};
}

export const authStore = createAuthStore();
