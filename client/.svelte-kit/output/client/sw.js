// Service Worker for Open Mobile TTS PWA
// DISABLED: This SW unregisters itself and clears all caches.
// Re-enable when PWA offline support is ready.

self.addEventListener('install', () => {
	self.skipWaiting();
});

self.addEventListener('activate', (event) => {
	event.waitUntil(
		caches.keys().then((cacheNames) => {
			return Promise.all(
				cacheNames.map((cacheName) => caches.delete(cacheName))
			);
		}).then(() => {
			return self.registration.unregister();
		}).then(() => {
			return self.clients.matchAll();
		}).then((clients) => {
			clients.forEach((client) => client.navigate(client.url));
		})
	);
});
