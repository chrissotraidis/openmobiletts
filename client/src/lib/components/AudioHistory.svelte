<script>
	import { historyStore } from '$lib/stores/history';
	import { playerStore } from '$lib/stores/player';
	import { settingsStore } from '$lib/stores/settings';
	import { Play, Trash2, Clock, Volume2 } from 'lucide-svelte';
	import { onDestroy } from 'svelte';

	let history = $state([]);
	const unsubHistory = historyStore.subscribe((h) => (history = h));
	onDestroy(unsubHistory);

	const voiceDisplayNames = {
		af_heart: 'Heart',
		af_nova: 'Nova',
		af_sky: 'Sky',
		af_bella: 'Bella',
		af_sarah: 'Sarah',
		am_adam: 'Adam',
		am_michael: 'Michael',
		bf_emma: 'Emma',
		bf_isabella: 'Isabella',
		bm_george: 'George',
		bm_lewis: 'Lewis',
	};

	function regenerate(entry) {
		playerStore.generate(entry.text, entry.voice, entry.speed, $settingsStore.autoPlay);
	}

	function formatDate(iso) {
		const d = new Date(iso);
		const now = new Date();
		const diff = now - d;

		if (diff < 60000) return 'Just now';
		if (diff < 3600000) return `${Math.floor(diff / 60000)}m ago`;
		if (diff < 86400000) return `${Math.floor(diff / 3600000)}h ago`;
		return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
	}
</script>

{#if history.length === 0}
	<div class="flex flex-col items-center justify-center py-20 text-center">
		<div class="w-16 h-16 bg-white/5 rounded-2xl flex items-center justify-center mb-4">
			<Clock size={28} class="text-slate-600" />
		</div>
		<p class="text-slate-500 text-sm mb-1">No history yet</p>
		<p class="text-slate-600 text-xs">Generated speech will appear here</p>
	</div>
{:else}
	<div class="space-y-2">
		<div class="flex items-center justify-between mb-4">
			<h3 class="text-sm font-semibold text-slate-400">{history.length} generation{history.length !== 1 ? 's' : ''}</h3>
			<button
				onclick={() => { if (confirm('Clear all history?')) historyStore.clear(); }}
				class="text-[10px] text-slate-600 hover:text-red-400 transition-colors"
			>
				Clear all
			</button>
		</div>

		{#each history as entry (entry.id)}
			<div class="group p-4 bg-slate-900/40 border border-white/5 rounded-xl hover:bg-slate-900/60 transition-colors">
				<div class="flex items-start gap-3">
					<!-- Play button -->
					<button
						onclick={() => regenerate(entry)}
						class="w-9 h-9 bg-blue-600/10 hover:bg-blue-600 rounded-lg flex items-center justify-center transition-colors shrink-0 mt-0.5"
					>
						<Play size={14} class="text-blue-400 group-hover:text-white ml-0.5" />
					</button>

					<!-- Content -->
					<div class="flex-1 min-w-0">
						<p class="text-sm text-slate-300 line-clamp-2 leading-relaxed">{entry.preview}</p>
						<div class="flex items-center gap-3 mt-2">
							<span class="text-[10px] text-slate-500 flex items-center gap-1">
								<Volume2 size={10} />
								{voiceDisplayNames[entry.voice] || entry.voice}
							</span>
							<span class="text-[10px] text-slate-600 font-mono">{entry.speed?.toFixed(1) || '1.0'}x</span>
							<span class="text-[10px] text-slate-600">{formatDate(entry.createdAt)}</span>
						</div>
					</div>

					<!-- Delete -->
					<button
						onclick={() => historyStore.remove(entry.id)}
						class="p-1.5 text-slate-700 hover:text-red-400 transition-colors opacity-0 group-hover:opacity-100"
						title="Remove"
					>
						<Trash2 size={14} />
					</button>
				</div>
			</div>
		{/each}
	</div>
{/if}

<style>
	.line-clamp-2 {
		display: -webkit-box;
		-webkit-line-clamp: 2;
		-webkit-box-orient: vertical;
		overflow: hidden;
	}
</style>
