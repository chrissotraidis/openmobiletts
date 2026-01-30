// Service Worker for Open Mobile TTS PWA
const CACHE_NAME = 'openmobiletts-v1';
const STATIC_CACHE = [
	'/',
	'/login',
	'/player',
];

// Install event - cache static assets
self.addEventListener('install', (event) => {
	event.waitUntil(
		caches.open(CACHE_NAME).then((cache) => {
			return cache.addAll(STATIC_CACHE);
		})
	);
	self.skipWaiting();
});

// Activate event - clean up old caches
self.addEventListener('activate', (event) => {
	event.waitUntil(
		caches.keys().then((cacheNames) => {
			return Promise.all(
				cacheNames.map((cacheName) => {
					if (cacheName !== CACHE_NAME) {
						return caches.delete(cacheName);
					}
				})
			);
		})
	);
	self.clients.claim();
});

// Fetch event - serve from cache, fallback to network
self.addEventListener('fetch', (event) => {
	// Skip API requests (always use network for dynamic content)
	if (event.request.url.includes('/api/')) {
		return;
	}

	event.respondWith(
		caches.match(event.request).then((response) => {
			// Return cached response if found
			if (response) {
				return response;
			}

			// Otherwise fetch from network
			return fetch(event.request).then((response) => {
				// Cache successful responses for static assets
				if (response.status === 200) {
					const responseClone = response.clone();
					caches.open(CACHE_NAME).then((cache) => {
						cache.put(event.request, responseClone);
					});
				}
				return response;
			});
		})
	);
});
