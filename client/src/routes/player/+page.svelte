<script>
	import { authStore } from '$lib/stores/auth';
	import { playerStore } from '$lib/stores/player';
	import { goto } from '$app/navigation';
	import { onMount } from 'svelte';
	import { browser } from '$app/environment';
	import AudioPlayer from '$lib/components/AudioPlayer.svelte';
	import TextInput from '$lib/components/TextInput.svelte';
	import TextDisplay from '$lib/components/TextDisplay.svelte';
	import AudioHistory from '$lib/components/AudioHistory.svelte';
	import { settingsStore } from '$lib/stores/settings';
	import { Mic, Plus, History, Settings, ShieldCheck, LogOut, Zap, User, X, ChevronRight, Volume2, Clock, Sliders, Info, RotateCcw, ChevronDown } from 'lucide-svelte';

	let isAuthenticated = $state(false);
	let isIOS = $state(false);
	let activeTab = $state('generate');
	let isMenuOpen = $state(false);
	let showLogoutConfirm = $state(false);

	onMount(() => {
		if (browser) {
			const token = localStorage.getItem('access_token');
			if (!token) {
				goto('/login');
			} else {
				isAuthenticated = true;
			}
			isIOS = /iPhone|iPad|iPod/.test(navigator.userAgent);
		}
	});

	function handleLogout() {
		authStore.clearToken();
		goto('/login');
	}
</script>

{#if isAuthenticated}
	<div class="flex h-screen w-full bg-[#0a0c10] text-slate-200 overflow-hidden">

		<!-- DESKTOP SIDEBAR -->
		<aside class="hidden md:flex w-64 border-r border-white/5 flex-col p-4 shrink-0">
			<div class="flex items-center gap-2 px-2 mb-8">
				<div class="w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center shadow-lg shadow-blue-600/20">
					<Mic size={18} class="text-white" />
				</div>
				<h1 class="font-bold text-lg tracking-tight bg-gradient-to-r from-white to-slate-400 bg-clip-text text-transparent">
					Open Mobile TTS
				</h1>
			</div>

			<nav class="space-y-1 flex-1">
				<button
					onclick={() => { activeTab = 'generate'; }}
					class="flex items-center gap-3 w-full px-3 py-2 rounded-lg transition-all duration-200 {activeTab === 'generate' ? 'bg-blue-600/10 text-blue-400 font-medium' : 'text-slate-400 hover:bg-white/5 hover:text-slate-200'}"
				>
					<Plus size={18} />
					<span class="text-sm">New Speech</span>
				</button>
				<button
					onclick={() => { activeTab = 'history'; }}
					class="flex items-center gap-3 w-full px-3 py-2 rounded-lg transition-all duration-200 {activeTab === 'history' ? 'bg-blue-600/10 text-blue-400 font-medium' : 'text-slate-400 hover:bg-white/5 hover:text-slate-200'}"
				>
					<History size={18} />
					<span class="text-sm">History</span>
				</button>
				<button
					onclick={() => { activeTab = 'settings'; }}
					class="flex items-center gap-3 w-full px-3 py-2 rounded-lg transition-all duration-200 {activeTab === 'settings' ? 'bg-blue-600/10 text-blue-400 font-medium' : 'text-slate-400 hover:bg-white/5 hover:text-slate-200'}"
				>
					<Settings size={18} />
					<span class="text-sm">Settings</span>
				</button>
			</nav>

			<div class="mt-auto pt-4 border-t border-white/5">
				<div class="p-3 bg-white/5 rounded-xl mb-4">
					<div class="flex items-center gap-2 mb-2">
						<ShieldCheck size={14} class="text-emerald-400" />
						<span class="text-[10px] font-bold uppercase tracking-wider text-slate-400">Local Privacy</span>
					</div>
					<div class="flex justify-between items-center text-xs">
						<span class="text-slate-300 font-mono">127.0.0.1</span>
						<span class="w-2 h-2 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.5)] animate-pulse"></span>
					</div>
				</div>
				<div class="flex items-center gap-2">
					<button
						onclick={() => { activeTab = 'settings'; }}
						class="flex items-center gap-3 px-3 py-2 flex-1 text-slate-400 hover:text-white transition-colors text-sm group"
					>
						<div class="w-8 h-8 rounded-full bg-slate-800 flex items-center justify-center text-[10px] group-hover:bg-slate-700">
							<User size={14} />
						</div>
						<span class="flex-1 text-left">Account</span>
					</button>
					<button
						onclick={() => { showLogoutConfirm = true; }}
						class="p-2 text-slate-500 hover:text-red-400 transition-colors rounded-lg hover:bg-red-400/5"
						title="Logout"
					>
						<LogOut size={16} />
					</button>
				</div>
			</div>
		</aside>

		<!-- MAIN CONTENT AREA -->
		<main class="flex-1 flex flex-col overflow-hidden relative pb-20 md:pb-0">

			<!-- TOP BAR -->
			<header class="h-16 border-b border-white/5 flex items-center justify-between px-4 md:px-8 bg-[#0a0c10]/50 backdrop-blur-md z-20 shrink-0">
				<div class="flex items-center gap-3">
					<div class="md:hidden w-8 h-8 bg-blue-600 rounded-lg flex items-center justify-center">
						<Mic size={16} class="text-white" />
					</div>
					<h2 class="text-sm font-semibold md:font-medium text-slate-300 md:text-slate-400">
						{#if activeTab === 'generate'}
							Generate Speech
						{:else if activeTab === 'history'}
							History
						{:else}
							Settings
						{/if}
					</h2>
				</div>
				<div class="flex items-center gap-3">
					<div class="hidden sm:flex items-center gap-2 px-3 py-1 bg-blue-600/10 border border-blue-500/20 rounded-full">
						<Zap size={12} class="text-blue-400" />
						<span class="text-[10px] font-bold text-blue-400 tracking-wider">LOCAL ENGINE</span>
					</div>
					<button class="md:hidden p-2 text-slate-400" onclick={() => { isMenuOpen = !isMenuOpen; }}>
						{#if isMenuOpen}
							<X size={20} />
						{:else}
							<User size={20} />
						{/if}
					</button>
				</div>
			</header>

			<!-- MOBILE MENU OVERLAY -->
			{#if isMenuOpen}
				<div class="absolute inset-0 z-30 bg-[#0a0c10] p-6">
					<div class="flex flex-col gap-6">
						<div class="flex items-center gap-4 p-4 bg-white/5 rounded-2xl">
							<div class="w-12 h-12 rounded-full bg-blue-600 flex items-center justify-center">
								<User size={20} class="text-white" />
							</div>
							<div>
								<p class="font-bold">Account</p>
								<p class="text-xs text-slate-500">Local Instance</p>
							</div>
						</div>
						<div class="space-y-2">
							<button
								onclick={() => { isMenuOpen = false; activeTab = 'settings'; }}
								class="w-full text-left p-4 hover:bg-white/5 rounded-xl flex items-center justify-between"
							>
								<span class="text-sm">App Settings</span>
								<ChevronRight size={16} class="text-slate-600" />
							</button>
							<button
								onclick={() => { isMenuOpen = false; showLogoutConfirm = true; }}
								class="w-full text-left p-4 text-red-400 hover:bg-red-400/5 rounded-xl"
							>
								<span class="text-sm">Logout</span>
							</button>
						</div>
						<button onclick={() => { isMenuOpen = false; }} class="mt-4 w-full py-4 bg-slate-800 rounded-xl text-sm font-medium">
							Close
						</button>
					</div>
				</div>
			{/if}

			<!-- LOGOUT CONFIRMATION MODAL -->
			{#if showLogoutConfirm}
				<!-- svelte-ignore a11y_no_static_element_interactions a11y_click_events_have_key_events -->
				<div
					class="fixed inset-0 z-50 bg-black/60 backdrop-blur-sm flex items-center justify-center p-4"
					onclick={(e) => { if (e.target === e.currentTarget) showLogoutConfirm = false; }}
				>
					<div class="bg-[#141820] border border-white/10 rounded-2xl p-6 w-full max-w-sm shadow-2xl">
						<div class="text-center mb-6">
							<div class="w-12 h-12 bg-red-500/10 rounded-full flex items-center justify-center mx-auto mb-3">
								<LogOut size={22} class="text-red-400" />
							</div>
							<h3 class="text-lg font-bold text-white mb-1">Sign Out?</h3>
							<p class="text-sm text-slate-400">You'll need to sign in again to access your local TTS instance.</p>
						</div>
						<div class="flex gap-3">
							<button
								onclick={() => { showLogoutConfirm = false; }}
								class="flex-1 py-3 bg-white/5 hover:bg-white/10 rounded-xl text-sm font-medium transition-colors"
							>
								Cancel
							</button>
							<button
								onclick={() => { showLogoutConfirm = false; handleLogout(); }}
								class="flex-1 py-3 bg-red-500 hover:bg-red-600 rounded-xl text-sm font-bold text-white transition-colors"
							>
								Sign Out
							</button>
						</div>
					</div>
				</div>
			{/if}

			<!-- SCROLLABLE VIEW -->
			<div class="flex-1 overflow-y-auto p-4 md:p-8 custom-scrollbar">
				{#if activeTab === 'generate'}
					<div class="max-w-4xl mx-auto space-y-6 md:space-y-8">
						<TextInput />
						<TextDisplay />
					</div>
				{:else if activeTab === 'history'}
					<div class="max-w-4xl mx-auto">
						<AudioHistory />
					</div>
				{:else if activeTab === 'settings'}
					<div class="max-w-4xl mx-auto space-y-6 pb-12">
						<!-- TTS Defaults -->
						<div class="p-6 bg-slate-900/40 border border-white/5 rounded-2xl space-y-5">
							<div class="flex items-center gap-2">
								<Sliders size={18} class="text-blue-400" />
								<h3 class="text-lg font-semibold">TTS Defaults</h3>
							</div>

							<!-- Default Voice -->
							<div class="space-y-2">
								<div class="flex items-center gap-2 px-1">
									<Volume2 size={14} class="text-slate-500" />
									<span class="text-xs font-bold text-slate-500 uppercase tracking-widest">Default Voice</span>
								</div>
								<div class="relative">
									<select
										value={$settingsStore.defaultVoice}
										onchange={(e) => settingsStore.update('defaultVoice', e.target.value)}
										class="w-full bg-slate-900 border border-white/10 rounded-xl p-3 appearance-none focus:outline-none focus:ring-1 focus:ring-blue-500 transition-all text-sm"
									>
										<option value="af_heart">Female (Heart)</option>
										<option value="af_nova">Female (Nova)</option>
										<option value="af_sky">Female (Sky)</option>
										<option value="af_bella">Female (Bella)</option>
										<option value="af_sarah">Female (Sarah)</option>
										<option value="am_adam">Male (Adam)</option>
										<option value="am_michael">Male (Michael)</option>
										<option value="bf_emma">British Female (Emma)</option>
										<option value="bf_isabella">British Female (Isabella)</option>
										<option value="bm_george">British Male (George)</option>
										<option value="bm_lewis">British Male (Lewis)</option>
									</select>
									<div class="absolute right-3 top-1/2 -translate-y-1/2 text-slate-500 pointer-events-none">
										<ChevronDown size={18} />
									</div>
								</div>
							</div>

							<!-- Default Speed -->
							<div class="space-y-2">
								<div class="flex justify-between items-center px-1">
									<div class="flex items-center gap-2">
										<Clock size={14} class="text-slate-500" />
										<span class="text-xs font-bold text-slate-500 uppercase tracking-widest">Default Speed</span>
									</div>
									<span class="text-xs font-mono text-blue-400">{$settingsStore.defaultSpeed.toFixed(1)}x</span>
								</div>
								<div class="px-2 py-3">
									<input
										type="range"
										min="0.5"
										max="2.0"
										step="0.1"
										value={$settingsStore.defaultSpeed}
										oninput={(e) => settingsStore.update('defaultSpeed', parseFloat(e.target.value))}
									/>
								</div>
							</div>

							<!-- Auto-play toggle -->
							<div class="flex items-center justify-between p-3 bg-white/5 rounded-xl">
								<div>
									<p class="text-sm font-medium text-slate-200">Auto-play on generation</p>
									<p class="text-[10px] text-slate-500 mt-0.5">Automatically play audio when generation completes</p>
								</div>
								<button
									onclick={() => settingsStore.update('autoPlay', !$settingsStore.autoPlay)}
									class="w-11 h-6 rounded-full transition-colors duration-200 relative {$settingsStore.autoPlay ? 'bg-blue-600' : 'bg-slate-700'}"
									aria-label="Toggle auto-play"
								>
									<div class="w-4 h-4 bg-white rounded-full absolute top-1 transition-all duration-200 {$settingsStore.autoPlay ? 'left-6' : 'left-1'}"></div>
								</button>
							</div>

							<!-- Reset -->
							<button
								onclick={() => settingsStore.reset()}
								class="flex items-center gap-2 text-xs text-slate-500 hover:text-slate-300 transition-colors px-1"
							>
								<RotateCcw size={12} />
								Reset to defaults
							</button>
						</div>

						<!-- About -->
						<div class="p-6 bg-slate-900/40 border border-white/5 rounded-2xl space-y-4">
							<div class="flex items-center gap-2">
								<Info size={18} class="text-blue-400" />
								<h3 class="text-lg font-semibold">About</h3>
							</div>
							<div class="space-y-3 text-sm text-slate-400">
								<div class="flex justify-between">
									<span>App</span>
									<span class="text-slate-200">Open Mobile TTS</span>
								</div>
								<div class="flex justify-between">
									<span>Engine</span>
									<span class="text-slate-200">Kokoro TTS (Local)</span>
								</div>
								<div class="flex justify-between">
									<span>Audio Format</span>
									<span class="text-slate-200">64kbps CBR MP3, 22050Hz mono</span>
								</div>
								<div class="flex justify-between">
									<span>Max Chunk</span>
									<span class="text-slate-200">250 tokens (~175 words)</span>
								</div>
								<div class="flex justify-between">
									<span>Sample Rate</span>
									<span class="text-slate-200">24kHz native (downsampled to 22050Hz)</span>
								</div>
								<div class="flex justify-between">
									<span>License</span>
									<span class="text-slate-200">Apache 2.0</span>
								</div>
							</div>
						</div>

						{#if isIOS}
							<div class="p-4 bg-yellow-500/10 border border-yellow-500/20 rounded-2xl">
								<p class="text-sm text-yellow-400">
									iOS Limitation: Audio will stop when app is minimized or screen locks. Keep app in foreground during playback.
								</p>
							</div>
						{/if}
					</div>
				{/if}
			</div>

			<!-- BOTTOM PLAYER BAR -->
			<AudioPlayer />

			<!-- MOBILE NAVIGATION BAR -->
			<nav class="md:hidden fixed bottom-0 left-0 right-0 h-[72px] bg-[#0d1117] border-t border-white/5 flex items-center px-2 z-40">
				<button
					onclick={() => { activeTab = 'generate'; isMenuOpen = false; }}
					class="flex flex-col items-center gap-1 flex-1 py-2 transition-all duration-200 {activeTab === 'generate' ? 'text-blue-400' : 'text-slate-500'}"
				>
					<Plus size={20} />
					<span class="text-[10px] font-medium">Generate</span>
				</button>
				<button
					onclick={() => { activeTab = 'history'; isMenuOpen = false; }}
					class="flex flex-col items-center gap-1 flex-1 py-2 transition-all duration-200 {activeTab === 'history' ? 'text-blue-400' : 'text-slate-500'}"
				>
					<History size={20} />
					<span class="text-[10px] font-medium">History</span>
				</button>
				<button
					onclick={() => { activeTab = 'settings'; isMenuOpen = false; }}
					class="flex flex-col items-center gap-1 flex-1 py-2 transition-all duration-200 {activeTab === 'settings' ? 'text-blue-400' : 'text-slate-500'}"
				>
					<Settings size={20} />
					<span class="text-[10px] font-medium">Settings</span>
				</button>
			</nav>
		</main>
	</div>
{:else}
	<div class="flex items-center justify-center min-h-screen bg-[#0a0c10]">
		<p class="text-slate-500">Checking authentication...</p>
	</div>
{/if}
