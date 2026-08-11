# Open Mobile TTS Overview

## Product

Open Mobile TTS is a private, local voice workspace. Its stable core converts
text and documents to speech. The current development tree also includes
experimental speech-to-text, audio import, batch transcription, export,
history, projects, and Android background playback/generation support.

The working product name remains **Open Mobile TTS** for the stabilization
release. A broader rename is deferred until STT is correctly identified,
benchmarked, and accepted on the supported platforms.

## Supported runtime shapes

| Surface | Current status | UI | Inference |
|---|---|---|---|
| Desktop/local web | Working development surface | SvelteKit served by FastAPI | Kokoro-82M/PyTorch TTS by default; optional sherpa-onnx TTS; Moonshine v1 Base INT8 STT |
| Android | Reproducible source build and physical Pixel acceptance; no signed public release | Same SvelteKit UI in a WebView | Stable sherpa-onnx Kokoro TTS, optional experimental Kitten Mini/Micro TTS, and optional Moonshine v1 Base INT8 STT through JNI |
| Browser-installed PWA | Not currently supported | Manifest exists, service worker disables itself | Requires the local backend and models |
| iOS/iPadOS | Planned only | Not implemented | Not selected |

“Web” means a local browser interface to the Python process. It does not mean a
hosted cloud service or serverless in-browser inference.

## Current model truth

### Text-to-speech

- Desktop default: `hexgrad/Kokoro-82M` through the Python `kokoro` package.
- Desktop optional and Android: sherpa-onnx
  `kokoro-multi-lang-v1_0`.
- The Android/sherpa package is roughly 350-383 MB extracted, not the old
  approximately 95 MB INT8 claim.
- The default Python configuration exposes seven US voices. Other voices and
  languages must not be advertised without runtime-specific verification.

### Speech-to-text

- Desktop and Android currently use
  `sherpa-onnx-moonshine-base-en-int8`.
- This is English-only Moonshine v1 Base INT8, not Moonshine v2 Medium.
- STT remains experimental until identity, download, platform parity, and
  physical-device benchmark gates pass.
- No LLM corrects transcripts. Formatting is deterministic.

## Experience model

Keep the primary product simple:

- **Create:** enter/dictate/import text or audio and generate/transcribe.
- **Library:** history, saved work, queues, batch jobs, and exports.
- **Player:** persistent playback, queue, timing, and reading/highlighting.
- **Settings:** five compact categories for Voice, Connection, Models, Data,
  and App/logs. Models distinguishes required speech generation from optional
  transcription downloads.

The current app uses Generate, History, and Settings. Settings now shows one
responsive category panel at a time rather than one continuous page.

## Privacy boundary

Inference is local after model download. FastAPI now binds loopback by default,
limits default CORS to local origins, and redacts content previews in logs.
There is still no authentication. Explicit LAN mode is a trusted-development
option only; public/remote use needs authentication, TLS, origin controls, and
resource limits.

## Current direction

The active plan is [Phase Zero Modernization](PHASE_ZERO_MODERNIZATION_PLAN.md):

1. make the project truthful and reproducible;
2. run a measured TTS/STT model bake-off;
3. unify platform contracts and user storage;
4. redesign the shared UI and brand from selected visual references;
5. produce a reproducible Android release path; and
6. build iOS only after shared contracts are stable.

The supporting evidence is in the
[technical debt and modernization audit](TECH_DEBT_AND_MODERNIZATION_AUDIT.md).
