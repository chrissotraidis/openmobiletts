import { writable, get } from 'svelte/store';
import {
	createBatchTranscriptionJob,
	fetchBatchTranscriptionJob,
	downloadBatchTranscriptionZip,
} from '../services/api.js';

const initial = {
	active: false,
	status: '',
	progress: null,
	error: '',
	jobId: null,
};

function describeBatchJob(job) {
	if (!job) return '';
	if (job.status === 'queued') return `Queued ${job.total} file${job.total === 1 ? '' : 's'}...`;
	if (job.status === 'running') {
		const current = job.current_file ? `: ${job.current_file}` : '';
		return `Transcribing ${job.completed + job.failed + 1} of ${job.total}${current}`;
	}
	if (job.status === 'complete') return `Packaging complete: ${job.completed} done, ${job.failed} failed.`;
	if (job.status === 'failed') return job.error || 'Batch transcription failed.';
	return `Processing ${job.completed || 0} of ${job.total || 0}...`;
}

function createStore() {
	const { subscribe, set, update } = writable({ ...initial });

	let currentRun = null;

	async function start(files) {
		if (get({ subscribe }).active) return;

		set({
			active: true,
			status: `Uploading ${files.length} file${files.length === 1 ? '' : 's'}...`,
			progress: null,
			error: '',
			jobId: null,
		});

		const run = (async () => {
			try {
				const created = await createBatchTranscriptionJob(files);
				let job = created;
				update((s) => ({ ...s, jobId: job.id, progress: job, status: describeBatchJob(job) }));

				const maxPolls = 240; // 20 min at 5s
				for (let poll = 0; poll < maxPolls && job.status !== 'complete' && job.status !== 'failed'; poll++) {
					await new Promise((r) => setTimeout(r, 5000));
					job = await fetchBatchTranscriptionJob(job.id);
					update((s) => ({ ...s, progress: job, status: describeBatchJob(job) }));
				}

				if (job.status !== 'complete') {
					throw new Error(job.error || 'Batch transcription did not complete.');
				}

				const zipBlob = await downloadBatchTranscriptionZip(job.id);
				const stamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-');
				const filename = `openmobiletts-transcripts-${stamp}.zip`;
				const url = URL.createObjectURL(zipBlob);
				const a = document.createElement('a');
				a.href = url;
				a.download = filename;
				document.body.appendChild(a);
				a.click();
				document.body.removeChild(a);
				setTimeout(() => URL.revokeObjectURL(url), 1000);

				update((s) => ({
					...s,
					active: false,
					status: `Downloaded ZIP for ${job.completed} file${job.completed === 1 ? '' : 's'}${job.failed ? `; ${job.failed} failed` : ''}.`,
				}));
				setTimeout(() => {
					update((s) => (s.active ? s : { ...initial }));
				}, 5000);
			} catch (err) {
				update((s) => ({
					...s,
					active: false,
					status: '',
					progress: null,
					error: err?.message || 'Batch transcription failed',
				}));
			} finally {
				currentRun = null;
			}
		})();

		currentRun = run;
		return run;
	}

	function clearError() {
		update((s) => ({ ...s, error: '' }));
	}

	function reset() {
		if (currentRun) return; // don't wipe an in-flight run
		set({ ...initial });
	}

	return { subscribe, start, clearError, reset };
}

export const batchTranscriptionStore = createStore();
