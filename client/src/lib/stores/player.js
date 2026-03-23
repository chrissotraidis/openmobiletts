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
	let lastAndroidProgressUpdate = 0; // Throttle playback position updates to Android

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

		// Send playback position to Android notification (throttled to ~1/sec)
		if (isAndroid && window.Android?.updatePlaybackProgress) {
			const now = Date.now();
			if (now - lastAndroidProgressUpdate >= 1000) {
				lastAndroidProgressUpdate = now;
				window.Android.updatePlaybackProgress(
					Math.floor(t * 1000),
					Math.floor((audioElement.duration || 0) * 1000)
				);
			}
		}

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
		const POLL_INTERVAL = 2_000;
		const MAX_WAIT = 3_600_000; // 60 minutes
		const MAX_CONSECUTIVE_ERRORS = 10; // Give up if server is unreachable
		const startTime = Date.now();
		let consecutiveErrors = 0;

		console.log(`[recovery] Starting job recovery for ${jobId}`);

		while (Date.now() - startTime < MAX_WAIT) {
			if (signal.aborted) return null;

			try {
				const statusRes = await fetch(apiUrl(`/api/tts/jobs/${jobId}/status`), { signal });
				if (!statusRes.ok) {
					console.warn('[recovery] Status endpoint failed:', statusRes.status);
					return null;
				}

				consecutiveErrors = 0; // Reset on successful request
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
					if (signal.aborted) return null;
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
				consecutiveErrors++;
				console.warn(`[recovery] Poll error (${consecutiveErrors}/${MAX_CONSECUTIVE_ERRORS}):`, e.message);
				if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
					console.error('[recovery] Server unreachable, giving up');
					return null;
				}
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

			// On Android, abort the stream when the app is backgrounded.
			// The server continues writing to disk — the client recovers
			// via the job endpoint when it returns to foreground.
			let visibilityHandler = null;
			if (isAndroid) {
				visibilityHandler = () => {
					if (document.hidden && activeController === controller) {
						console.log('[player] App backgrounded during generation, aborting stream for job recovery');
						controller.abort();
					}
				};
				document.addEventListener('visibilitychange', visibilityHandler);
			}

			let reader = null;
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

				reader = response.body.getReader();
				let buffer = new Uint8Array(0);
				let bufferOffset = 0; // Read position within buffer
				let pendingAudioBytes = 0;

				// Efficient buffer append: only copies unconsumed data
				function appendToBuffer(newData) {
					const unconsumed = buffer.length - bufferOffset;
					if (unconsumed === 0) {
						// Buffer fully consumed — just replace it
						buffer = newData;
						bufferOffset = 0;
					} else {
						// Compact: copy only unconsumed bytes + new data
						const combined = new Uint8Array(unconsumed + newData.length);
						combined.set(buffer.subarray(bufferOffset));
						combined.set(newData, unconsumed);
						buffer = combined;
						bufferOffset = 0;
					}
				}

				function bufferRemaining() {
					return buffer.length - bufferOffset;
				}

				function consumeBytes(n) {
					const slice = buffer.slice(bufferOffset, bufferOffset + n);
					bufferOffset += n;
					return slice;
				}

				function consumeUntilNewline() {
					for (let i = bufferOffset; i < buffer.length; i++) {
						if (buffer[i] === 10) { // \n
							const line = new TextDecoder().decode(buffer.subarray(bufferOffset, i));
							bufferOffset = i + 1;
							return line;
						}
					}
					return null; // No newline found
				}

				while (true) {
					const { done, value } = await reader.read();
					if (done) break;
					resetInactivityTimer();

					appendToBuffer(value);

					// Process buffer using length-prefixed framing protocol:
					//   JOB:{id}\n               (sent once at start)
					//   CHUNKS:{total}\n         (sent once at start)
					//   TIMING:{json}\n
					//   AUDIO:{length}\n
					//   {exactly length bytes of audio}
					while (bufferRemaining() > 0) {
						if (pendingAudioBytes > 0) {
							if (bufferRemaining() < pendingAudioBytes) break;
							audioChunks.push(consumeBytes(pendingAudioBytes));
							pendingAudioBytes = 0;
							continue;
						}

						const line = consumeUntilNewline();
						if (line === null) break;

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

					// On Android, once we have the job ID and chunk count,
					// close the stream and switch to poll-based generation.
					// The server continues generating to disk and the notification
					// updates natively from the generation thread. Polling is
					// far more reliable than streaming on mobile.
					if (isAndroid && activeJobId && get(totalChunks) > 0) {
						console.log(`[player] Android: closing stream, switching to poll-based generation for job ${activeJobId}`);
						audioChunks = []; // Discard partial stream data
						reader.cancel();
						// reader.read() will return { done: true } on next iteration
					}
				}

				// Stream complete — stop the inactivity timer before post-processing
				if (inactivityTimer) {
					clearTimeout(inactivityTimer);
					inactivityTimer = null;
				}

				// Handle any remaining buffered audio data
				if (pendingAudioBytes > 0 && bufferRemaining() >= pendingAudioBytes) {
					audioChunks.push(consumeBytes(pendingAudioBytes));
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
					try {
						await audioElement.play();
						state.set(PlayState.PLAYING);
						window.Android?.onPlaybackStarted();
					} catch {
						state.set(PlayState.PAUSED);
						window.Android?.onPlaybackPaused();
					}
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
				// Cancel the reader to release the response body and stop server-side writes
				try { reader?.cancel(); } catch {}

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

							// Re-check supersession: a new generate() or stop() may have
							// been called during the await above, replacing activeController.
							if (activeController !== recoveryController) {
								audioElement.pause();
								audioElement.removeEventListener('timeupdate', timeUpdateHandler);
								audioElement.removeEventListener('ended', handleEnded);
								audioElement = null;
								URL.revokeObjectURL(blobUrl);
								blobUrl = null;
								superseded = true;
								return;
							}

							duration.set(audioElement.duration);
							notifyGenerationComplete();

							if (autoPlay) {
								try {
									await audioElement.play();
									state.set(PlayState.PLAYING);
									window.Android?.onPlaybackStarted();
								} catch {
									state.set(PlayState.PAUSED);
									window.Android?.onPlaybackPaused();
								}
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
							superseded = true; // prevent finally from redundant stopGenerationTimer()
							return; // Recovery complete
						}
					} catch (recoveryErr) {
						if (recoveryErr.name !== 'AbortError') {
							console.warn('[player] Job recovery failed:', recoveryErr.message);
						}
					}

					// If superseded during recovery (new generation started or stop
					// called), don't override their state changes with our error.
					if (activeController !== recoveryController) {
						superseded = true;
						return;
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
				if (visibilityHandler) document.removeEventListener('visibilitychange', visibilityHandler);
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
						try {
							await audioElement.play();
							state.set(PlayState.PLAYING);
							window.Android?.onPlaybackStarted();
						} catch {
							state.set(PlayState.PAUSED);
							window.Android?.onPlaybackPaused();
						}
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
			const el = audioElement;
			if (el) {
				// If audio ended (at the end), restart from beginning
				if (el.ended) {
					el.currentTime = 0;
				}
				el.play().then(() => {
					if (audioElement !== el) return; // element was replaced by stop/generate
					state.set(PlayState.PLAYING);
					window.Android?.onPlaybackStarted();
				}).catch(() => {
					if (audioElement !== el) return;
					state.set(PlayState.PAUSED);
					window.Android?.onPlaybackPaused();
				});
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
				setTimeout(() => URL.revokeObjectURL(url), 1000);
			}
		},
	};

	return store;
}

export const playerStore = createPlayerStore();

// Expose playback controls globally for Android notification actions.
// When the user taps play/pause/stop in the system notification,
// the native side evaluates JS to call these functions.
if (typeof window !== 'undefined') {
	window.__ttsControl = {
		play: () => playerStore.play(),
		pause: () => playerStore.pause(),
		stop: () => playerStore.stop(),
		seekTo: (ms) => playerStore.seek(ms / 1000),
		next: () => {
			const entry = playlistStore.advance();
			if (entry) playerStore.playFromHistory(entry, true);
		},
		previous: () => {
			const result = playlistStore.previous();
			if (result === 'restart') {
				playerStore.seek(0);
			} else if (result) {
				playerStore.playFromHistory(result, true);
			}
		},
	};
}
