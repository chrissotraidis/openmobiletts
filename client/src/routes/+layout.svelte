<script>
	import '../app.css';
	import { onMount } from 'svelte';

	// Register service worker for PWA - DISABLED FOR NOW
	// onMount(() => {
	// 	if ('serviceWorker' in navigator) {
	// 		navigator.serviceWorker
	// 			.register('/sw.js')
	// 			.then(() => console.log('Service Worker registered'))
	// 			.catch((err) => console.error('Service Worker registration failed:', err));
	// 	}
	// });
</script>

<div class="min-h-screen flex flex-col">
	<slot />
</div>
