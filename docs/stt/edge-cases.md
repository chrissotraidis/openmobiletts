# STT — Edge Cases

## Mic Permission Permanently Denied (Android)
**Scenario:** User previously denied mic permission with "Don't ask again" checked. Runtime permission dialog no longer appears.
**Why it matters:** Mic button becomes non-functional with no obvious way to fix it.
**How we handle it:**
1. Detect that permission is permanently denied (Android `shouldShowRequestPermissionRationale` returns false)
2. Show message: "Microphone permission was denied. Enable it in Settings."
3. Provide a button/link that opens Android app settings (`ACTION_APPLICATION_DETAILS_SETTINGS`)
**Status:** 🔵 Not built

---

## STT Model Download Interrupted
**Scenario:** User starts Moonshine model download (~100 MB), but network drops or app is killed mid-download.
**Why it matters:** Partial model files could corrupt the STT initialization.
**How we handle it:**
1. ModelManager detects incomplete download on next launch (check file size or hash)
2. Deletes partial files
3. Re-prompts user to download
4. Download resumes from scratch (GitHub releases don't reliably support range requests)
**Status:** 🔵 Not built

---

## Moonshine Initialization Failure
**Scenario:** Model files are present but sherpa-onnx `OfflineRecognizer` fails to initialize (corrupt model, incompatible device, JNI error).
**Why it matters:** STT is completely non-functional despite model appearing downloaded.
**How we handle it:**
1. SttManager catches initialization exception
2. Logs error to AppLog
3. STT endpoints return error response with message
4. Mic button shows "Speech-to-text unavailable" with option to re-download model
**Status:** 🔵 Not built

---

## Empty or Silent Recording
**Scenario:** User taps mic, says nothing (or is in complete silence), taps stop.
**Why it matters:** Transcription of silence could produce empty text or garbage output.
**How we handle it:**
1. If audio duration < 0.5 seconds, skip transcription and show "Recording too short"
2. If Moonshine returns empty text, show "No speech detected. Try again."
3. Don't insert empty text into the text area
**Status:** 🔵 Not built

---

## Very Long Recording (30+ minutes)
**Scenario:** User records a long monologue or meeting.
**Why it matters:** Large audio blob could cause memory pressure when POSTing to the local server. Moonshine batch processing time scales linearly.
**How we handle it:**
1. No hard recording limit (mic stays active)
2. Progress indicator shows estimated time ("Transcribing ~30 minutes of audio...")
3. Audio is chunked for Moonshine processing (Moonshine processes in segments internally)
4. Memory: audio blob is the main concern — a 30-minute WAV at 16kHz mono is ~58 MB
**Status:** 🔵 Not built

---

## Concurrent Mic and TTS Generation
**Scenario:** User taps mic while TTS generation is in progress, or starts generation while recording.
**Why it matters:** Both operations use the audio system and could conflict. On Android, both use sherpa-onnx (TtsManager and SttManager share the same JNI library).
**How we handle it:**
1. Mic button is disabled during active TTS generation
2. Generate button is disabled during active recording/transcription
3. Both models are loaded simultaneously (per [decision 006](../decisions/006-simultaneous-model-loading.md)) — no model-swap conflict
4. Mutex serialization in both TtsManager and SttManager prevents JNI conflicts
**Status:** 🔵 Not built

---

## WebView MediaRecorder Unsupported
**Scenario:** Specific Android WebView versions don't support MediaRecorder API, or the API is present but produces unusable output.
**Why it matters:** Primary audio capture method fails — dictation doesn't work.
**How we handle it:**
1. Feature-detect MediaRecorder API on page load
2. If unavailable: fall back to native `AudioRecord` via `window.Android` bridge
3. Native bridge method captures audio, saves to temp file, returns path
4. JS reads the file and POSTs to STT endpoint as normal
**Status:** 🔵 Not built — listed as open question in [unknowns.md](../unknowns.md)

---

## Non-English Audio Input
**Scenario:** User speaks in a non-English language while Moonshine is configured for English.
**Why it matters:** Moonshine v2 is English-only. Non-English input produces garbage transcription.
**How we handle it:**
1. v3.0: Moonshine is English-only. No detection or warning (user is expected to know).
2. Future: If multi-language STT models become available, add language selection to STT settings.
**Status:** 🔵 Not applicable in v3.0 (English-only STT)
