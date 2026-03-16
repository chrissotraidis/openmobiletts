# Dictation Flow

## Who

A user who wants to speak into their phone or computer and get editable text.

## The Happy Path

1. User is on the Generate tab with an empty (or existing) text area
2. User taps the **mic button**
3. App checks microphone permission
   - If not granted: Android runtime permission dialog appears → user grants
   - If already granted: skip to step 4
4. Text area transforms into **recording view** — shows waveform/audio level indicator and a stop button
5. User speaks naturally
6. User taps **stop**
7. Audio blob is created via WebView MediaRecorder API
8. Audio is POSTed to `/api/stt/transcribe` (NanoHTTPD on Android, FastAPI on desktop)
9. **Progress indicator** appears (reuses GenerationProgress.svelte pattern) — "Transcribing..." with estimated time based on audio duration
10. Moonshine processes the audio batch and returns transcribed text
11. Transcribed text appears in the text area, **fully editable**
12. User can now: edit the text, export it (PDF/MD/TXT), or generate speech from it

## What Could Go Wrong

### Microphone permission denied
- **When:** User denies the runtime permission dialog (Android) or browser permission prompt (desktop)
- **What happens:** Mic button shows a brief error message: "Microphone access required for dictation"
- **Recovery:** User can tap mic again → permission dialog reappears. On Android, if "Don't ask again" was checked, direct user to Settings.

### STT model not downloaded
- **When:** User taps mic but Moonshine model hasn't been downloaded yet (e.g., first launch, download was interrupted)
- **What happens:** App shows a prompt: "Speech-to-text model required. Download now? (~100 MB)"
- **Recovery:** User taps download → model downloads with progress → mic becomes available

### Very long recording (>5 minutes)
- **When:** User records a long dictation session
- **What happens:** Recording continues normally. Transcription takes proportionally longer — progress indicator shows estimated time.
- **Recovery:** No special handling needed. Moonshine processes batch after recording stops.

### WebView MediaRecorder fails
- **When:** WebView's MediaRecorder API is unavailable or produces corrupt audio on certain Android versions
- **What happens:** Mic button shows error: "Recording failed. Try again."
- **Recovery:** Retry. If persistent, fall back to native `AudioRecord` via JS-native bridge (see [unknowns.md](../unknowns.md) — WebView MediaRecorder reliability).

### Background noise / poor audio quality
- **When:** User records in a noisy environment
- **What happens:** Transcription completes but with lower accuracy (higher WER)
- **Recovery:** User manually edits the transcribed text. No automatic correction (per [decision 002](../decisions/002-no-llm-transcript-correction.md)).

### App backgrounded during transcription
- **When:** User switches apps while Moonshine is processing
- **What happens:** On Android, transcription continues on the native side (SttManager runs on IO dispatcher). On desktop, server-side processing continues.
- **Recovery:** When user returns, result is displayed.

## Acceptance Criteria

- [ ] Tapping mic button requests microphone permission if not already granted
- [ ] Recording view shows visual feedback (waveform or audio level indicator)
- [ ] Stop button ends recording and triggers transcription
- [ ] Progress indicator shows during transcription with time estimate
- [ ] Transcribed text appears in the text area and is immediately editable
- [ ] Mic button is disabled during active TTS generation (no conflicts)
- [ ] Transcription works with Moonshine v2 Small on Android (~148ms latency per chunk)
- [ ] Transcription works with Moonshine v2 Medium on desktop
- [ ] Error state shown if mic permission is denied
- [ ] Error state shown if STT model is not downloaded
- [ ] Recording of 30+ seconds produces reasonable transcript (WER < 10% in quiet environment)

## Related

- See: [STT Overview](stt-overview.md) for model details and API endpoints
- See: [Unified Input](../unified-input/unified-input-overview.md) for how mic button fits into the Generate tab
- Depends on: Android microphone permission in AndroidManifest.xml, WebView `onPermissionRequest` override
