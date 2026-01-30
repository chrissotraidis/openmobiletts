<script>
	import { authStore } from '$lib/stores/auth';
	import { goto } from '$app/navigation';
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import AudioPlayer from '$lib/components/AudioPlayer.svelte';
	import TextInput from '$lib/components/TextInput.svelte';
	import TextDisplay from '$lib/components/TextDisplay.svelte';
	import AudioHistory from '$lib/components/AudioHistory.svelte';

	let isAuthenticated = false;
	let isIOS = false;

	onMount(() => {
		if (browser) {
			// Check authentication
			const token = localStorage.getItem('access_token');
			if (!token) {
				goto('/login');
			} else {
				isAuthenticated = true;
			}

			// Check if iOS
			isIOS = /iPhone|iPad|iPod/.test(navigator.userAgent);
		}
	});

	function handleLogout() {
		authStore.clearToken();
		goto('/login');
	}
</script>

{#if isAuthenticated}
	<div class="min-h-screen flex flex-col">
		<!-- Header -->
		<header class="bg-white shadow-sm border-b border-gray-200">
			<div class="max-w-7xl mx-auto px-4 py-4 flex justify-between items-center">
				<h1 class="text-2xl font-bold text-primary-600">Open Mobile TTS</h1>
				<button on:click={handleLogout} class="btn btn-secondary text-sm">
					Logout
				</button>
			</div>
		</header>

		<!-- Main Content -->
		<main class="flex-1 max-w-7xl mx-auto w-full px-4 py-6 space-y-6">
			<!-- Text Input Section -->
			<TextInput />

			<!-- Text Display with Synchronized Highlighting -->
			<TextDisplay />

			<!-- Audio Player -->
			<AudioPlayer />

			<!-- History Section -->
			<AudioHistory />
		</main>

		<!-- iOS Background Audio Warning -->
		{#if isIOS}
			<div class="bg-yellow-50 border-t border-yellow-200 p-4">
				<p class="text-sm text-yellow-800 text-center">
					📱 iOS Limitation: Audio will stop when app is minimized or screen locks.
					Keep app in foreground during playback.
				</p>
			</div>
		{/if}
	</div>
{:else}
	<div class="flex items-center justify-center min-h-screen">
		<p class="text-gray-600">Checking authentication...</p>
	</div>
{/if}
