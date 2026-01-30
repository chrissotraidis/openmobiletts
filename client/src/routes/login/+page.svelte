<script>
	import { goto } from '$app/navigation';
	import { authStore } from '$lib/stores/auth';

	let username = $state('');
	let password = $state('');
	let error = $state('');
	let loading = $state(false);
	let debugInfo = $state('');

	async function testAPI() {
		debugInfo = 'Testing API connection...';
		try {
			const res = await fetch('http://localhost:8000/health');
			const data = await res.json();
			debugInfo = 'API works! ' + JSON.stringify(data);
		} catch (err) {
			debugInfo = 'API Error: ' + err.message;
		}
	}

	async function handleLogin() {
		error = '';
		debugInfo = '';
		loading = true;

		console.log('Login attempt:', username);

		try {
			// Direct fetch to login endpoint
			const response = await fetch('http://localhost:8000/token', {
				method: 'POST',
				headers: {
					'Content-Type': 'application/x-www-form-urlencoded',
				},
				body: new URLSearchParams({
					username: username,
					password: password
				})
			});

			console.log('Response status:', response.status);

			if (!response.ok) {
				const errorText = await response.text();
				console.error('Login failed:', errorText);
				throw new Error('Invalid username or password');
			}

			const data = await response.json();
			console.log('Login success:', data);

			if (data.access_token) {
				authStore.setToken(data.access_token);
				goto('/player');
			} else {
				throw new Error('No access token received');
			}
		} catch (err) {
			console.error('Error:', err);
			error = err.message || 'Login failed';
		} finally {
			loading = false;
		}
	}
</script>

<div class="min-h-screen flex items-center justify-center px-4 bg-gradient-to-br from-primary-50 to-blue-100">
	<div class="w-full max-w-md">
		<div class="bg-white rounded-2xl shadow-xl p-8">
			<div class="text-center mb-8">
				<h1 class="text-3xl font-bold text-primary-600 mb-2">Open Mobile TTS</h1>
				<p class="text-gray-600">Sign in to continue</p>
			</div>

			<form onsubmit={(e) => { e.preventDefault(); handleLogin(); }} class="space-y-6">
				{#if error}
					<div class="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
						{error}
					</div>
				{/if}

				<div>
					<label for="username" class="block text-sm font-medium text-gray-700 mb-2">
						Username
					</label>
					<input
						id="username"
						type="text"
						bind:value={username}
						required
						disabled={loading}
						class="input"
						placeholder="admin"
					/>
				</div>

				<div>
					<label for="password" class="block text-sm font-medium text-gray-700 mb-2">
						Password
					</label>
					<input
						id="password"
						type="password"
						bind:value={password}
						required
						disabled={loading}
						class="input"
						placeholder="testpassword123"
					/>
				</div>

				<button type="submit" disabled={loading} class="btn btn-primary w-full">
					{loading ? 'Signing in...' : 'Sign In'}
				</button>

				<button type="button" onclick={testAPI} class="btn btn-secondary w-full">
					Test API Connection
				</button>
			</form>

			{#if debugInfo}
				<div class="mt-4 p-3 bg-gray-100 rounded text-xs">
					{debugInfo}
				</div>
			{/if}
		</div>
	</div>
</div>
