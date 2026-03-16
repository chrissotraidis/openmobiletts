# Speech-to-Text (STT)

## What It Does

On-device speech recognition that converts voice input to text using Moonshine v2. Users can dictate via microphone or import audio files for transcription. All inference happens on-device — no cloud, no network after model download.

## Why It Matters

This is the headline v3.0 feature — transforms the app from a one-way TTS tool into a bidirectional voice-to-text platform. Users can dictate, edit, then export or listen back.

## Core Rules

- STT uses Moonshine v2, NOT Whisper (spec-stated — sherpa-onnx Whisper has accuracy regressions, GitHub issue #2900)
- No LLM for transcript correction — Moonshine accuracy is sufficient; removes ~500 MB model dependency (decided — [002](../decisions/002-no-llm-transcript-correction.md))
- Batch transcription only in v3.0 — record first, transcribe after. Streaming STT deferred (decided — [007](../decisions/007-batch-stt-before-streaming.md))
- sherpa-onnx handles both TTS and STT — same framework, same .aar library, same Kotlin API pattern (spec-stated)
- Microphone audio captured via WebView MediaRecorder API, POSTed to backend for transcription (spec-stated)
- Output is plain text in the text area, immediately editable (spec-stated)

### Model Options by Platform

| Model | Params | Size | Avg WER | Latency | Platform |
|-------|--------|------|---------|---------|----------|
| Moonshine v2 Small | 123M | ~100 MB | 7.84% | 148ms | Android optional (lighter download) |
| Moonshine v2 Medium | 245M | ~250 MB | 6.65% | 258ms | **Android default, Desktop default** |
| Whisper Large v3 | 1.55B | ~3 GB | ~5% | ~2s | Desktop optional (future) |

### Android Model Strategy

- Default download: Moonshine v2 Medium (~250 MB) — tested on Pixel 9 Pro with no speed difference vs Small, better accuracy (user-stated, validated 3/16/26)
- Optional: Moonshine v2 Small (~100 MB) available in Settings for users who want a lighter download
- Both TTS (~95 MB) and STT (~250 MB) models loaded simultaneously in RAM (~345 MB total) (decided — [006](../decisions/006-simultaneous-model-loading.md))

### Desktop Model Strategy

- Default: Moonshine v2 Medium — desktop has the RAM and CPU for it (spec-stated)
- Future option: Whisper Large v3 for maximum accuracy with GPU (spec-stated)

## New API Endpoints

| Endpoint | Method | Purpose | Request | Response |
|----------|--------|---------|---------|----------|
| `/api/stt/transcribe` | POST | Transcribe audio to text | Audio data (WAV/PCM) as multipart | `{text, duration_ms, model}` |
| `/api/stt/models` | GET | List available STT models and download status | — | `{models: [{name, size, downloaded, active}]}` |
| `/api/stt/models/download` | POST | Download an STT model | `{model: "moonshine-v2-medium"}` | `{status, progress}` |

## New Code

### Android (Kotlin)

- **SttManager.kt** — Wraps sherpa-onnx `OfflineRecognizer` for Moonshine. Coroutine-based, Mutex-serialized, IO dispatcher (mirrors TtsManager pattern). Accepts PCM audio bytes, returns transcribed text.
- **ModelManager.kt** — Extended from ModelDownloader.kt to download both TTS and STT models from GitHub releases. Sequential download on first launch.

### Desktop (Python)

- **stt_engine.py** — Moonshine STT via Python sherpa-onnx bindings. Creates `OfflineRecognizer`, accepts PCM audio, returns text.

### Frontend (SvelteKit)

- **MicButton.svelte** — Floating mic button on Generate tab. MediaRecorder API capture, recording state UI, POST to `/api/stt/transcribe`.
- **SttProgress.svelte** — Progress indicator during transcription. Reuses GenerationProgress.svelte pattern.

## What's Assumed

- Moonshine v2 Small quality (7.84% WER) is acceptable for dictation — Risk if wrong: Medium
- WebView MediaRecorder API works reliably for audio capture — Risk if wrong: Medium (fallback: native AudioRecord)
- Both models fit in RAM on target devices (16 GB Pixel 9 Pro) — Risk if wrong: Low

## Key References

- **Source spec:** `docs/EXPANSION-PLAN-OPEN-MOBILE-VOICE.md`, sections "Speech-to-Text" and "Architecture"
- **Decision:** [001-moonshine-over-whisper.md](../decisions/001-moonshine-over-whisper.md)

## Status

🔵 Not Started
