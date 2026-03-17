# Open Mobile Voice

## What We're Building

Open Mobile Voice (formerly Open Mobile TTS) is a bidirectional voice-to-text platform that runs entirely on-device. Users can convert text to speech, dictate speech to text, import audio files for transcription, and export documents — all fully local on Android, with more powerful model options on desktop. No cloud, no API keys, no accounts.

## Who It's For

Users who want private, local text-to-speech and speech-to-text with zero cloud dependencies. Power users, accessibility-focused users, and privacy-conscious individuals who prefer on-device inference over cloud APIs.

## Core Features

- **[TTS](tts/tts-overview.md):** Convert text to streaming MP3/AAC audio with sentence-level highlighting (implemented)
- **[STT](stt/stt-overview.md):** Dictate speech to text via Moonshine v2, fully on-device (v3.0)
- **[Document Import](document-import/document-import-overview.md):** Upload PDF/DOCX/TXT/MD for TTS (implemented)
- **[Document Export](document-export/document-export-overview.md):** Save text as PDF/MD/TXT (v3.0)
- **[Audio Playback](audio-playback/audio-playback-overview.md):** Player with queue, history, synchronized text highlighting (implemented)
- **[Audio Import](audio-import/audio-import-overview.md):** Import audio files (mp3/aac/ogg/wav) for transcription (v3.0)
- **[Project Storage](project-storage/project-storage-overview.md):** JSON-based local project management with auto-cleanup (v3.0)
- **[Unified Input](unified-input/unified-input-overview.md):** Redesigned Generate tab as voice+text scratchpad (v3.0)
- **[Android App](android-app/android-app-overview.md):** WebView + NanoHTTPD + Sherpa-ONNX native bridge (implemented)
- **[Multi-Language](multi-language/multi-language-overview.md):** 9-language TTS support via Kokoro (planned)

## Spec Reference

The original v3.0 expansion specification is at `docs/_reference/EXPANSION-PLAN-OPEN-MOBILE-VOICE.md`. Arnold's feature docs are derived from it combined with existing architecture docs. When in doubt, the spec is authoritative for v3.0 features.

## Current Status

🟡 In Progress — v2.0 implemented and production-ready. v3.0 spec decomposed into feature docs, new features not yet started.

## Next Steps

- [ ] Review each feature's overview for accuracy
- [ ] Run `/arnold:plan` to flesh out flows and edge cases
- [ ] Start building Phase 1 (STT foundation)
- [ ] Run `/arnold:check` after coding to verify alignment
