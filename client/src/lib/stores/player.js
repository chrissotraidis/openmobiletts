/**
 * Player store — manages audio playback state, timing, and text highlighting.
 */
import { writable, derived, get } from 'svelte/store';
import { cacheAudio, getCachedAudio } from '$lib/services/audioCache';
import { apiUrl } from '$lib/services/api';

// Playback states
export const PlayState = {
	IDLE: 'idle',
	GENERATING: 'generating',
	PLAYING: 'playing',
	PAUSED: 'paused',
	ERROR: 'error',
};

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

	// Internal: the audio element and blob URL
	let audioElement = null;
	let blobUrl = null;
	let audioChunks = [];
	let timingData = [];
	let timeUpdateHandler = null;

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
		currentTime.set(0);
		duration.set(0);
		segments.set([]);
		activeSegmentIndex.set(-1);
	}

	function handleEnded() {
		state.set(PlayState.IDLE);
		activeSegmentIndex.set(-1);
	}

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

	return {
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

		/**
		 * Start streaming TTS generation.
		 * @param {string} text - Text to generate
		 * @param {string} voiceName - Voice to use
		 * @param {number} speedVal - Speed multiplier
		 * @param {boolean} autoPlay - Whether to auto-play when complete
		 * @param {number|null} historyId - Optional history ID for caching
		 */
		async generate(text, voiceName, speedVal, autoPlay = true, historyId = null) {
			resetAudio();
			state.set(PlayState.GENERATING);
			voice.set(voiceName);
			speed.set(speedVal);
			inputText.set(text);
			errorMessage.set('');

			try {
				// Use POST with JSON body to handle arbitrarily long text
				// (GET query params have ~2KB-8KB length limits)
				const response = await fetch(apiUrl('/api/tts/stream'), {
					method: 'POST',
					headers: { 'Content-Type': 'application/json' },
					body: JSON.stringify({
						text,
						voice: voiceName,
						speed: speedVal,
					}),
				});
				if (!response.ok) {
					const err = await response.json().catch(() => ({ detail: response.statusText }));
					throw new Error(err.detail || 'TTS generation failed');
				}

				const reader = response.body.getReader();
				let buffer = new Uint8Array(0);
				let pendingAudioBytes = 0; // when > 0, we're reading exactly this many bytes of MP3

				while (true) {
					const { done, value } = await reader.read();
					if (done) break;

					// Append new data to buffer
					const combined = new Uint8Array(buffer.length + value.length);
					combined.set(buffer);
					combined.set(value, buffer.length);
					buffer = combined;

					// Process buffer using length-prefixed framing protocol:
					//   TIMING:{json}\n
					//   AUDIO:{length}\n
					//   {exactly length bytes of MP3}
					while (buffer.length > 0) {
						if (pendingAudioBytes > 0) {
							// Reading binary MP3 data — take exactly pendingAudioBytes
							if (buffer.length < pendingAudioBytes) break; // need more data
							audioChunks.push(buffer.slice(0, pendingAudioBytes));
							buffer = buffer.slice(pendingAudioBytes);
							pendingAudioBytes = 0;
							continue;
						}

						// Looking for a text line (TIMING: or AUDIO:)
						const newlineIdx = buffer.indexOf(10); // \n
						if (newlineIdx === -1) break; // incomplete line, wait for more data

						const line = new TextDecoder().decode(buffer.slice(0, newlineIdx));
						buffer = buffer.slice(newlineIdx + 1);

						if (line.startsWith('TIMING:')) {
							const timing = JSON.parse(line.slice(7));
							timingData.push(timing);
							segments.set([...timingData]);
						} else if (line.startsWith('AUDIO:')) {
							pendingAudioBytes = parseInt(line.slice(6), 10);
						}
					}
				}

				// Handle any remaining buffered audio data
				if (pendingAudioBytes > 0 && buffer.length > 0) {
					audioChunks.push(buffer.slice(0, pendingAudioBytes));
				}

				// Build audio blob and create playback element
				const blob = new Blob(audioChunks, { type: 'audio/mpeg' });
				blobUrl = URL.createObjectURL(blob);

				// Cache the audio if historyId provided
				if (historyId) {
					cacheAudio(historyId, blob, timingData);
				}

				audioElement = new Audio(blobUrl);
				audioElement.playbackRate = get(playbackRate); // Apply saved playback rate
				timeUpdateHandler = updateActiveSegment;
				audioElement.addEventListener('timeupdate', timeUpdateHandler);
				audioElement.addEventListener('ended', handleEnded);

				// Wait for metadata to load
				await new Promise((resolve, reject) => {
					audioElement.addEventListener('loadedmetadata', resolve, { once: true });
					audioElement.addEventListener('error', reject, { once: true });
				});

				duration.set(audioElement.duration);

				if (autoPlay) {
					audioElement.play();
					state.set(PlayState.PLAYING);
				} else {
					state.set(PlayState.PAUSED);
				}
			} catch (err) {
				errorMessage.set(err.message);
				state.set(PlayState.ERROR);
			}
		},

		/**
		 * Play from cache if available, otherwise regenerate.
		 * @param {object} entry - History entry with id, text, voice, speed
		 * @param {boolean} autoPlay - Whether to auto-play
		 */
		async playFromHistory(entry, autoPlay = true) {
			// Check cache first with a timeout
			let cached = null;
			try {
				const cachePromise = getCachedAudio(entry.id);
				const timeoutPromise = new Promise((_, reject) =>
					setTimeout(() => reject(new Error('Cache timeout')), 5000)
				);
				cached = await Promise.race([cachePromise, timeoutPromise]);
			} catch (err) {
				console.warn('Cache retrieval failed:', err.message);
				cached = null;
			}

			if (cached) {
				// Play from cache
				resetAudio();
				state.set(PlayState.GENERATING); // Brief loading state
				voice.set(entry.voice);
				speed.set(entry.speed);
				inputText.set(entry.text);
				errorMessage.set('');

				try {
					// Convert blob to ArrayBuffer and store in audioChunks for download support
					const arrayBuffer = await cached.blob.arrayBuffer();
					audioChunks = [new Uint8Array(arrayBuffer)];

					blobUrl = URL.createObjectURL(cached.blob);
					timingData = cached.timingData || [];
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

					if (autoPlay) {
						audioElement.play();
						state.set(PlayState.PLAYING);
					} else {
						state.set(PlayState.PAUSED);
					}
				} catch (err) {
					errorMessage.set(err.message);
					state.set(PlayState.ERROR);
				}
			} else {
				// No cache, regenerate and cache
				await this.generate(entry.text, entry.voice, entry.speed, autoPlay, entry.id);
			}
		},

		play() {
			if (audioElement) {
				audioElement.play();
				state.set(PlayState.PLAYING);
			}
		},

		pause() {
			if (audioElement) {
				audioElement.pause();
				state.set(PlayState.PAUSED);
			}
		},

		stop() {
			resetAudio();
			state.set(PlayState.IDLE);
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
			if (audioChunks.length > 0) {
				return new Blob(audioChunks, { type: 'audio/mpeg' });
			}
			return null;
		},
	};
}

export const playerStore = createPlayerStore();
