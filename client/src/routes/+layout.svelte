<script>
	import '../app.css';

	let { children } = $props();

	// Register service worker for PWA - DISABLED FOR NOW
	// import { onMount } from 'svelte';
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
	{@render children()}
</div>
