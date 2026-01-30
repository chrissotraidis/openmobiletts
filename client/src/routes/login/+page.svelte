<script>
	import { goto } from '$app/navigation';
	import { authStore } from '$lib/stores/auth';
	import { Mic, Loader2 } from 'lucide-svelte';

	let username = $state('');
	let password = $state('');
	let error = $state('');
	let loading = $state(false);

	async function handleLogin() {
		error = '';
		loading = true;

		try {
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

			if (!response.ok) {
				throw new Error('Invalid username or password');
			}

			const data = await response.json();

			if (data.access_token) {
				authStore.setToken(data.access_token);
				goto('/player');
			} else {
				throw new Error('No access token received');
			}
		} catch (err) {
			error = err.message || 'Login failed';
		} finally {
			loading = false;
		}
	}
</script>

<div class="min-h-screen flex items-center justify-center px-4 bg-[#0a0c10]">
	<div class="w-full max-w-md">
		<div class="bg-slate-900/60 border border-white/10 rounded-2xl shadow-2xl p-8">
			<div class="text-center mb-8">
				<div class="w-14 h-14 bg-blue-600 rounded-2xl flex items-center justify-center mx-auto mb-4 shadow-lg shadow-blue-600/20">
					<Mic size={28} class="text-white" />
				</div>
				<h1 class="text-2xl font-bold bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent mb-2">
					Open Mobile TTS
				</h1>
				<p class="text-slate-500 text-sm">Sign in to your local instance</p>
			</div>

			<form onsubmit={(e) => { e.preventDefault(); handleLogin(); }} class="space-y-5">
				{#if error}
					<div class="bg-red-500/10 border border-red-500/20 text-red-400 px-4 py-3 rounded-xl text-sm">
						{error}
					</div>
				{/if}

				<div class="space-y-2">
					<label for="username" class="block text-xs font-bold text-slate-500 uppercase tracking-widest">
						Username
					</label>
					<input
						id="username"
						type="text"
						bind:value={username}
						required
						disabled={loading}
						class="input"
						placeholder="Enter username"
					/>
				</div>

				<div class="space-y-2">
					<label for="password" class="block text-xs font-bold text-slate-500 uppercase tracking-widest">
						Password
					</label>
					<input
						id="password"
						type="password"
						bind:value={password}
						required
						disabled={loading}
						class="input"
						placeholder="Enter password"
					/>
				</div>

				<button
					type="submit"
					disabled={loading}
					class="w-full py-4 rounded-2xl font-bold text-base transition-all duration-300 flex items-center justify-center gap-3 active:scale-[0.98] {loading ? 'bg-slate-800 text-slate-500' : 'bg-blue-600 shadow-lg shadow-blue-600/20 text-white hover:bg-blue-500'}"
				>
					{#if loading}
						<Loader2 size={20} class="animate-spin" />
						<span>Signing in...</span>
					{:else}
						<span>Sign In</span>
					{/if}
				</button>
			</form>
		</div>

		<p class="text-center text-[10px] text-slate-600 mt-6 uppercase tracking-widest">
			Private Local Instance
		</p>
	</div>
</div>
