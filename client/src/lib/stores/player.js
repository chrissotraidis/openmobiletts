/**
 * Player store — manages audio playback state, timing, and text highlighting.
 */
import { writable, derived, get } from 'svelte/store';
import { cacheAudio, getCachedAudio } from '$lib/services/audioCache';
import { apiUrl } from '$lib/services/api';
import { playlistStore } from '$lib/stores/playlist';

// Playback states
export const PlayState = {
	IDLE: 'idle',
	GENERATING: 'generating',
	PLAYING: 'playing',
	PAUSED: 'paused',
	ERROR: 'error',
};

// Android detection: true when running inside the Android WebView with the native bridge
const isAndroid = typeof window !== 'undefined' && !!window.Android;

function createPlayerStore() {
	const state = writable(PlayState.IDLE);
	const currentTime = writable(0);
	const duration = writable(0);
	const segments = writable([]); // Array of { text, start, end, chunk_index }
	const activeSegmentIndex = writable(-1);
	const errorMessage = writable('');
	const voice = writable('af_heart');
	const speed = writable(1.0);
	const playbackRate = writable(1.0); // Runtime playback speed (independent of generation speed)
	const inputText = writable('');
	const totalChunks = writable(0); // Server-reported chunk count for accurate progress
	const currentHistoryId = writable(null); // History entry ID currently being generated

	// Generation timer (persistent across component mounts)
	const generationStartTime = writable(null);
	const generationElapsed = writable(0);
	let genTimerInterval = null;

	function startGenerationTimer() {
		generationStartTime.set(Date.now());
		generationElapsed.set(0);
		if (genTimerInterval) clearInterval(genTimerInterval);
		genTimerInterval = setInterval(() => {
			const start = get(generationStartTime);
			if (start) generationElapsed.set(Math.floor((Date.now() - start) / 1000));
		}, 100);
	}

	function stopGenerationTimer() {
		generationStartTime.set(null);
		if (genTimerInterval) {
			clearInterval(genTimerInterval);
			genTimerInterval = null;
		}
	}

	// Internal: the audio element and blob URL
	let audioElement = null;
	let blobUrl = null;
	let audioChunks = [];
	let timingData = [];
	let timeUpdateHandler = null;
	let activeController = null; // AbortController for the current generation request
	let activeJobId = null; // Server-side job ID for recovery if the stream drops
	let cachedBlob = null; // Complete audio blob for download (avoids re-merging chunks)

	// Detect audio format from first bytes
	function detectAudioMime(chunks) {
		if (chunks.length > 0 && chunks[0].length >= 2) {
			const h = chunks[0];
			// WAV: starts with "RIFF"
			if (h.length >= 4 && h[0] === 0x52 && h[1] === 0x49 && h[2] === 0x46 && h[3] === 0x46) {
				return 'audio/wav';
			}
			// AAC ADTS: sync word 0xFFF, layer bits 00 (distinguishes from MP3 sync 0xFFE)
			if (h[0] === 0xFF && (h[1] & 0xF6) === 0xF0) {
				return 'audio/aac';
			}
		}
		return 'audio/mpeg';
	}

	// Merge multiple WAV chunks into a single valid WAV file.
	// Each chunk is a complete WAV (44-byte header + PCM data).
	// We take the first header, concatenate all PCM data, and fix the size fields.
	function mergeWavChunks(chunks) {
		if (chunks.length === 1) return new Blob(chunks, { type: 'audio/wav' });

		let totalPcmSize = 0;
		for (const chunk of chunks) {
			totalPcmSize += chunk.length - 44;
		}

		const result = new Uint8Array(44 + totalPcmSize);
		// Copy first chunk's 44-byte header
		result.set(chunks[0].subarray(0, 44));

		// Update RIFF file size at offset 4 (little-endian uint32): 36 + totalPcmSize
		const fileSize = 36 + totalPcmSize;
		result[4] = fileSize & 0xff;
		result[5] = (fileSize >> 8) & 0xff;
		result[6] = (fileSize >> 16) & 0xff;
		result[7] = (fileSize >> 24) & 0xff;

		// Update data chunk size at offset 40 (little-endian uint32)
		result[40] = totalPcmSize & 0xff;
		result[41] = (totalPcmSize >> 8) & 0xff;
		result[42] = (totalPcmSize >> 16) & 0xff;
		result[43] = (totalPcmSize >> 24) & 0xff;

		// Copy PCM data from all chunks (skip each chunk's 44-byte header)
		let offset = 44;
		for (const chunk of chunks) {
			const pcm = chunk.subarray(44);
			result.set(pcm, offset);
			offset += pcm.length;
		}

		return new Blob([result], { type: 'audio/wav' });
	}

	// Notification support for background generation
	function notifyGenerationComplete() {
		if (typeof document === 'undefined' || !document.hidden) return;
		if (!('Notification' in globalThis)) return;
		if (Notification.permission === 'granted') {
			new Notification('Audio Ready', {
				body: 'Your TTS generation is complete.',
				icon: '/favicon.png',
				tag: 'tts-ready',
			});
		}
	}

	function requestNotificationPermission() {
		if (typeof Notification === 'undefined') return;
		if (Notification.permission === 'default') {
			Notification.requestPermission();
		}
	}

	function resetAudio() {
		if (audioElement) {
			audioElement.pause();
			audioElement.removeEventListener('timeupdate', timeUpdateHandler);
			audioElement.removeEventListener('ended', handleEnded);
			audioElement = null;
		}
		if (blobUrl) {
			URL.revokeObjectURL(blobUrl);
			blobUrl = null;
		}
		audioChunks = [];
		timingData = [];
		cachedBlob = null;
		currentTime.set(0);
		duration.set(0);
		segments.set([]);
		activeSegmentIndex.set(-1);
		totalChunks.set(0);
		stopGenerationTimer();
	}

	function handleEnded() {
		// Check if there's a next track in the playlist queue
		const nextEntry = playlistStore.advance();
		if (nextEntry) {
			// Auto-play the next queued track
			store.playFromHistory(nextEntry, true);
			return;
		}
		state.set(PlayState.PAUSED);
		window.Android?.onPlaybackStopped();
	}

	// Reference to the store object (set after creation) for use in handleEnded
	let store = null;

	function updateActiveSegment() {
		if (!audioElement) return;
		const t = audioElement.currentTime;
		currentTime.set(t);

		const segs = timingData;
		let found = -1;
		for (let i = 0; i < segs.length; i++) {
			if (t >= segs[i].start && t < segs[i].end) {
				found = i;
				break;
			}
		}
		activeSegmentIndex.set(found);
	}

	/**
	 * Recover audio from a server-side job after the stream drops.
	 * Polls job status until complete, then fetches audio + timing from disk.
	 * @param {string} jobId - Server job ID
	 * @param {AbortSignal} signal - AbortSignal for cancellation
	 * @returns {{ blob: Blob, timing: Array } | null}
	 */
	async function recoverFromJob(jobId, signal) {
		const POLL_INTERVAL = 5_000;
		const MAX_WAIT = 3_600_000; // 60 minutes
		const startTime = Date.now();

		console.log(`[recovery] Starting job recovery for ${jobId}`);

		while (Date.now() - startTime < MAX_WAIT) {
			if (signal.aborted) return null;

			try {
				const statusRes = await fetch(apiUrl(`/api/tts/jobs/${jobId}/status`), { signal });
				if (!statusRes.ok) {
					console.warn('[recovery] Status endpoint failed:', statusRes.status);
					return null;
				}

				const status = await statusRes.json();

				// Update progress UI during recovery
				if (status.total > 0) totalChunks.set(status.total);
				if (status.completed > 0) {
					// Create placeholder segments so the progress bar updates
					const placeholders = Array.from({ length: status.completed }, (_, i) => ({
						text: '', start: 0, end: 0, chunk_index: i,
					}));
					segments.set(placeholders);
				}
				if (window.Android?.updateGenerationProgress) {
					window.Android.updateGenerationProgress(status.completed, status.total);
				}

				if (status.status === 'complete') {
					console.log(`[recovery] Job ${jobId} complete, fetching audio + timing`);
					const [audioRes, timingRes] = await Promise.all([
						fetch(apiUrl(`/api/tts/jobs/${jobId}/audio`), { signal }),
						fetch(apiUrl(`/api/tts/jobs/${jobId}/timing`), { signal }),
					]);

					if (!audioRes.ok || !timingRes.ok) {
						console.warn('[recovery] Failed to fetch audio/timing:', audioRes.status, timingRes.status);
						return null;
					}

					const blob = await audioRes.blob();
					const timing = await timingRes.json();
					return { blob, timing };
				}

				if (status.status === 'error') {
					console.warn('[recovery] Job failed:', status.error);
					return null;
				}
				if (status.status === 'cancelled') {
					console.log('[recovery] Job was cancelled');
					return null;
				}

				// Still generating — wait and poll again (cancellation-aware)
				await new Promise((r) => {
					const t = setTimeout(r, POLL_INTERVAL);
					signal.addEventListener('abort', () => { clearTimeout(t); r(); }, { once: true });
				});
			} catch (e) {
				if (signal.aborted) return null;
				console.warn('[recovery] Poll error, retrying:', e.message);
				await new Promise((r) => setTimeout(r, POLL_INTERVAL));
			}
		}

		console.warn('[recovery] Timed out waiting for job');
		return null;
	}

	store = {
		state,
		currentTime,
		duration,
		segments,
		activeSegmentIndex,
		errorMessage,
		voice,
		speed,
		playbackRate,
		inputText,
		totalChunks,
		currentHistoryId,
		generationElapsed,

		/**
		 * Start streaming TTS generation.
		 * @param {string} text - Text to generate
		 * @param {string} voiceName - Voice to use
		 * @param {number} speedVal - Speed multiplier
		 * @param {boolean} autoPlay - Whether to auto-play when complete
		 * @param {number|null} historyId - Optional history ID for caching
		 */
		async generate(text, voiceName, speedVal, autoPlay = true, historyId = null) {
			// Abort any in-flight generation before starting a new one
			if (activeController) {
				activeController.abort();
				activeController = null;
			}
			// Cancel the old server-side job (if any) so it doesn't
			// keep generating to disk and wasting resources.
			if (activeJobId) {
				fetch(apiUrl(`/api/tts/jobs/${activeJobId}/cancel`), { method: 'POST' }).catch(() => {});
				activeJobId = null;
			}
			resetAudio();
			state.set(PlayState.GENERATING);
			startGenerationTimer();
			window.Android?.onGenerationStarted();
			voice.set(voiceName);
			speed.set(speedVal);
			inputText.set(text);
			errorMessage.set('');
			currentHistoryId.set(historyId);
			requestNotificationPermission();

			// Abort controller with inactivity timeout — if no data arrives,
			// assume the server is stuck and abort. The client then recovers
			// via the job endpoint (Android) or shows an error (desktop).
			// Android: 5 min (server abandons slow streams and writes to disk,
			//   so no data for 5 min means something is genuinely stuck)
			// Desktop: 3 min (server should respond quickly)
			const controller = new AbortController();
			activeController = controller;
			let inactivityTimer = null;
			const INACTIVITY_TIMEOUT = isAndroid ? 300_000 : 180_000;

			function resetInactivityTimer() {
				if (inactivityTimer) clearTimeout(inactivityTimer);
				inactivityTimer = setTimeout(() => controller.abort(), INACTIVITY_TIMEOUT);
			}

			let superseded = false;

			try {
				resetInactivityTimer();

				const response = await fetch(apiUrl('/api/tts/stream'), {
					method: 'POST',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify({
						text,
						voice: voiceName,
						speed: speedVal,
					}),
					signal: controller.signal,
				});
				if (!response.ok) {
					const err = await response.json().catch(() => ({ detail: response.statusText }));
					throw new Error(err.detail || 'TTS generation failed');
				}

				const reader = response.body.getReader();
				let buffer = new Uint8Array(0);
				let pendingAudioBytes = 0;

				while (true) {
					const { done, value } = await reader.read();
					if (done) break;
					resetInactivityTimer();

					// Append new data to buffer
					const combined = new Uint8Array(buffer.length + value.length);
					combined.set(buffer);
					combined.set(value, buffer.length);
					buffer = combined;

					// Process buffer using length-prefixed framing protocol:
					//   CHUNKS:{total}\n        (sent once at start)
					//   TIMING:{json}\n
					//   AUDIO:{length}\n
					//   {exactly length bytes of audio}
					while (buffer.length > 0) {
						if (pendingAudioBytes > 0) {
							if (buffer.length < pendingAudioBytes) break;
							audioChunks.push(buffer.slice(0, pendingAudioBytes));
							buffer = buffer.slice(pendingAudioBytes);
							pendingAudioBytes = 0;
							continue;
						}

						const newlineIdx = buffer.indexOf(10); // \n
						if (newlineIdx === -1) break;

						const line = new TextDecoder().decode(buffer.slice(0, newlineIdx));
						buffer = buffer.slice(newlineIdx + 1);

						if (line.startsWith('TIMING:')) {
							const timing = JSON.parse(line.slice(7));
							timingData.push(timing);
							segments.set([...timingData]);
							if (window.Android?.updateGenerationProgress) {
								window.Android.updateGenerationProgress(timingData.length, get(totalChunks) || 0);
							}
						} else if (line.startsWith('AUDIO:')) {
							pendingAudioBytes = parseInt(line.slice(6), 10);
						} else if (line.startsWith('CHUNKS:')) {
							const total = parseInt(line.slice(7), 10);
							if (total > 0) totalChunks.set(total);
						} else if (line.startsWith('JOB:')) {
							activeJobId = line.slice(4);
						}
					}
				}

				// Stream complete — stop the inactivity timer before post-processing
				if (inactivityTimer) {
					clearTimeout(inactivityTimer);
					inactivityTimer = null;
				}

				// Handle any remaining buffered audio data
				if (pendingAudioBytes > 0 && buffer.length >= pendingAudioBytes) {
					audioChunks.push(buffer.slice(0, pendingAudioBytes));
				}

				// On Android, check if the stream was truncated (server abandoned
				// streaming because the client was too slow). If so, throw to
				// trigger job recovery which fetches the full audio from disk.
				// Compare audioChunks (not timingData) since a partial enqueue
				// could send TIMING without the matching AUDIO bytes.
				const expectedTotal = get(totalChunks);
				if (isAndroid && activeJobId && expectedTotal > 0 && audioChunks.length < expectedTotal) {
					console.log(`[player] Incomplete stream: ${audioChunks.length}/${expectedTotal} audio chunks received. Recovering from job ${activeJobId}`);
					throw new Error(`Incomplete stream: received ${audioChunks.length}/${expectedTotal} chunks`);
				}

				// Build audio blob and start playback immediately
				const mime = detectAudioMime(audioChunks);
				const blob = mime === 'audio/wav' ? mergeWavChunks(audioChunks) : new Blob(audioChunks, { type: mime });
				cachedBlob = blob;
				blobUrl = URL.createObjectURL(blob);

				audioElement = new Audio(blobUrl);
				audioElement.playbackRate = get(playbackRate);
				timeUpdateHandler = updateActiveSegment;
				audioElement.addEventListener('timeupdate', timeUpdateHandler);
				audioElement.addEventListener('ended', handleEnded);

				await new Promise((resolve, reject) => {
					audioElement.addEventListener('loadedmetadata', resolve, { once: true });
					audioElement.addEventListener('error', reject, { once: true });
				});

				duration.set(audioElement.duration);
				notifyGenerationComplete();

				if (autoPlay) {
					audioElement.play();
					state.set(PlayState.PLAYING);
					window.Android?.onPlaybackStarted();
				} else {
					state.set(PlayState.PAUSED);
					window.Android?.onPlaybackPaused();
				}

				// Cache in background AFTER playback starts — don't block the user
				if (historyId) {
					cacheAudio(historyId, blob, timingData).catch(
						(err) => console.warn('Cache write failed:', err.message)
					);
				}
				currentHistoryId.set(null);
			} catch (err) {
				// Clean up any audio resources allocated before the error
				if (audioElement) {
					audioElement.pause();
					audioElement.removeEventListener('timeupdate', timeUpdateHandler);
					audioElement.removeEventListener('ended', handleEnded);
					audioElement = null;
				}
				if (blobUrl) {
					URL.revokeObjectURL(blobUrl);
					blobUrl = null;
				}
				audioChunks = [];
				cachedBlob = null;

				// Don't show error if this generation was aborted by a newer one
				if (err.name === 'AbortError' && activeController !== controller) {
					superseded = true;
					return; // Superseded by a new generation — silently exit
				}

				// On Android, if the stream broke but we have a job ID, the
				// generation is still running on the Kotlin side writing to disk.
				// Poll the job status and recover the audio when complete.
				// This covers: network errors, incomplete streams, AND timeout
				// aborts (inactivity timer fires but server is still generating).
				const jobId = activeJobId;
				if (isAndroid && jobId) {
					console.log(`[player] Stream broke, attempting job recovery: ${jobId}`);
					const recoveryController = new AbortController();
					activeController = recoveryController;

					try {
						const recovered = await recoverFromJob(jobId, recoveryController.signal);
						if (recovered && activeController === recoveryController) {
							// Recovery succeeded — set up audio playback
							cachedBlob = recovered.blob;
							blobUrl = URL.createObjectURL(recovered.blob);
							timingData = recovered.timing || [];
							segments.set(timingData);

							audioElement = new Audio(blobUrl);
							audioElement.playbackRate = get(playbackRate);
							timeUpdateHandler = updateActiveSegment;
							audioElement.addEventListener('timeupdate', timeUpdateHandler);
							audioElement.addEventListener('ended', handleEnded);

							await new Promise((resolve, reject) => {
								audioElement.addEventListener('loadedmetadata', resolve, { once: true });
								audioElement.addEventListener('error', reject, { once: true });
							});

							duration.set(audioElement.duration);
							notifyGenerationComplete();

							if (autoPlay) {
								audioElement.play();
								state.set(PlayState.PLAYING);
								window.Android?.onPlaybackStarted();
							} else {
								state.set(PlayState.PAUSED);
								window.Android?.onPlaybackPaused();
							}

							// Cache the recovered audio
							if (historyId) {
								cacheAudio(historyId, recovered.blob, timingData).catch(
									(e) => console.warn('Cache write failed:', e.message)
								);
							}

							stopGenerationTimer();
							currentHistoryId.set(null);
							if (activeController === recoveryController) activeController = null;
							activeJobId = null;
							return; // Recovery complete
						}
					} catch (recoveryErr) {
						if (recoveryErr.name !== 'AbortError') {
							console.warn('[player] Job recovery failed:', recoveryErr.message);
						}
					}
				}

				if (err.name === 'AbortError') {
					const timeoutMin = isAndroid ? 5 : 3;
					errorMessage.set(`Generation timed out — no data received for ${timeoutMin} minutes`);
				} else {
					errorMessage.set(err.message);
				}
				currentHistoryId.set(null);
				state.set(PlayState.ERROR);
				window.Android?.onPlaybackStopped();
			} finally {
				if (inactivityTimer) clearTimeout(inactivityTimer);
				if (activeController === controller) activeController = null;
				// Don't stop the timer if superseded — the new generation owns it
				if (!superseded) stopGenerationTimer();
			}
		},

		/**
		 * Play from cache if available, otherwise regenerate.
		 * @param {object} entry - History entry with id, text, voice, speed
		 * @param {boolean} autoPlay - Whether to auto-play
		 */
		async playFromHistory(entry, autoPlay = true) {
			// Read from local IndexedDB cache — no timeout needed since this is
			// a local read that either succeeds or fails (not a network request).
			let cached = null;
			try {
				cached = await getCachedAudio(entry.id);
			} catch (err) {
				console.warn('Cache retrieval failed:', err.message);
				cached = null;
			}

			if (cached) {
				// Play from cache
				resetAudio();
				state.set(PlayState.GENERATING); // Brief loading state
				startGenerationTimer();
				window.Android?.onGenerationStarted();
				voice.set(entry.voice);
				speed.set(entry.speed);
				inputText.set(entry.text);
				errorMessage.set('');

				try {
					// Store blob reference for download support (no expensive ArrayBuffer copy)
					cachedBlob = cached.blob;

					blobUrl = URL.createObjectURL(cached.blob);
					timingData = cached.timingData || [];
					segments.set(timingData);

					audioElement = new Audio(blobUrl);
					audioElement.playbackRate = get(playbackRate);
					timeUpdateHandler = updateActiveSegment;
					audioElement.addEventListener('timeupdate', timeUpdateHandler);
					audioElement.addEventListener('ended', handleEnded);

					await new Promise((resolve, reject) => {
						// Timeout prevents permanent hang from corrupt cached audio
						const timeout = setTimeout(() => reject(new Error('Audio load timed out')), 10_000);
						audioElement.addEventListener('loadedmetadata', () => { clearTimeout(timeout); resolve(); }, { once: true });
						audioElement.addEventListener('error', () => { clearTimeout(timeout); reject(audioElement.error || new Error('Audio failed to load')); }, { once: true });
					});

					duration.set(audioElement.duration);
					stopGenerationTimer();

					if (autoPlay) {
						audioElement.play();
						state.set(PlayState.PLAYING);
						window.Android?.onPlaybackStarted();
					} else {
						state.set(PlayState.PAUSED);
						window.Android?.onPlaybackPaused();
					}
				} catch (err) {
					stopGenerationTimer();
					// Cached audio failed (corrupt data, unsupported format, timeout).
					// Fall back to regenerating fresh audio instead of dead-ending on error.
					console.warn('Cached audio playback failed, regenerating:', err.message);
					resetAudio();
					await this.generate(entry.text, entry.voice, entry.speed, autoPlay, entry.id);
				}
			} else {
				// No cache, regenerate and cache
				await this.generate(entry.text, entry.voice, entry.speed, autoPlay, entry.id);
			}
		},

		play() {
			if (audioElement) {
				// If audio ended (at the end), restart from beginning
				if (audioElement.ended) {
					audioElement.currentTime = 0;
				}
				audioElement.play();
				state.set(PlayState.PLAYING);
				window.Android?.onPlaybackStarted();
			}
		},

		pause() {
			if (audioElement) {
				audioElement.pause();
				state.set(PlayState.PAUSED);
				window.Android?.onPlaybackPaused();
			}
		},

		stop() {
			if (activeController) {
				activeController.abort();
				activeController = null;
			}
			// Cancel any server-side generation job
			if (activeJobId) {
				fetch(apiUrl(`/api/tts/jobs/${activeJobId}/cancel`), { method: 'POST' }).catch(() => {});
				activeJobId = null;
			}
			currentHistoryId.set(null);
			resetAudio();
			state.set(PlayState.IDLE);
			window.Android?.onPlaybackStopped();
		},

		seek(time) {
			if (audioElement) {
				audioElement.currentTime = time;
				currentTime.set(time);
				updateActiveSegment();
			}
		},

		setPlaybackRate(rate) {
			playbackRate.set(rate);
			if (audioElement) {
				audioElement.playbackRate = rate;
			}
		},

		getAudioElement() {
			return audioElement;
		},

		getAudioBlob() {
			// Return the current audio as a blob for download
			if (cachedBlob) return cachedBlob;
			if (audioChunks.length > 0) {
				const mime = detectAudioMime(audioChunks);
				return mime === 'audio/wav' ? mergeWavChunks(audioChunks) : new Blob(audioChunks, { type: mime });
			}
			return null;
		},

		async downloadAudio(blob, filename) {
			if (window.Android?.saveAudioFile) {
				// Android WebView: convert blob to base64 and send to native
				const arrayBuffer = await blob.arrayBuffer();
				const bytes = new Uint8Array(arrayBuffer);
				// Chunked base64 encoding to avoid call stack overflow
				let binary = '';
				const chunkSize = 8192;
				for (let i = 0; i < bytes.length; i += chunkSize) {
					const chunk = bytes.subarray(i, i + chunkSize);
					binary += String.fromCharCode.apply(null, chunk);
				}
				const base64 = btoa(binary);
				window.Android.saveAudioFile(base64, filename, blob.type || 'audio/wav');
			} else {
				// Desktop: standard blob URL download
				const url = URL.createObjectURL(blob);
				const a = document.createElement('a');
				a.href = url;
				a.download = filename;
				document.body.appendChild(a);
				a.click();
				document.body.removeChild(a);
				URL.revokeObjectURL(url);
			}
		},
	};

	return store;
}

export const playerStore = createPlayerStore();
