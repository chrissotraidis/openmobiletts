# Project Status

**Last Updated:** 2026-03-16
**Current Version:** v2.0.0 (production)
**Next Version:** v3.0.0 (in development — Phases 1-4 implemented)

## Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| [TTS](tts/tts-overview.md) | 🟢 Implemented | Core TTS fully working on both platforms |
| [Document Import](document-import/document-import-overview.md) | 🟢 Implemented | PDF/DOCX/TXT/MD extraction working |
| [Audio Playback](audio-playback/audio-playback-overview.md) | 🟢 Implemented | Player, queue, history, highlighting all working |
| [Android App](android-app/android-app-overview.md) | 🟢 Implemented | WebView + Sherpa-ONNX, job recovery, notifications |
| [STT](stt/stt-overview.md) | 🟡 In Progress | Backend + API done, needs StorageSettings UI |
| [Document Export](document-export/document-export-overview.md) | 🟡 In Progress | Backend + API + UI done (inline in TextInput) |
| [Audio Import](audio-import/audio-import-overview.md) | 🟡 In Progress | AudioDecoder + upload routing done |
| [Project Storage](project-storage/project-storage-overview.md) | 🟡 In Progress | CRUD + cleanup done, needs StorageSettings UI |
| [Unified Input](unified-input/unified-input-overview.md) | 🟡 In Progress | Mic button + export picker done in TextInput |
| [Multi-Language](multi-language/multi-language-overview.md) | 🔵 Not Started | 9-language TTS, planned |

## v3.0 Implementation Phases

| Phase | Goal | Status |
|-------|------|--------|
| Phase 1: Foundation | Moonshine STT end-to-end on Android | 🟢 Complete |
| Phase 2: STT UI + Audio Import | Mic button, audio decoder, file import | 🟢 Complete |
| Phase 3: Export + Projects | PDF/MD/TXT export, project storage | 🟢 Complete |
| Phase 4: Desktop Parity | Python STT, export, project storage | 🟢 Complete |
| Phase 5: Polish + Testing | Testing, UI polish, docs | 🟡 In Progress |

## Documentation Coverage

| Feature | Overview | Flows | Edge Cases | Acceptance Criteria |
|---------|----------|-------|------------|---------------------|
| TTS | Thorough | — | — | — |
| Document Import | Moderate | — | — | — |
| Audio Playback | Thorough | queue-and-history-flow.md | — | Yes (existing) |
| Android App | Thorough | model-download-flow.md | — | Yes (partial v3.0) |
| STT | Thorough | dictation-flow.md | edge-cases.md | Yes |
| Document Export | Thorough | export-flow.md | edge-cases.md | Yes |
| Audio Import | Thorough | import-flow.md | edge-cases.md | Yes |
| Project Storage | Thorough | project-lifecycle-flow.md | edge-cases.md | Yes |
| Unified Input | Moderate | mic flow (in overview) | edge-cases.md | — |
| Multi-Language | Moderate | — | — | — |

## Check History

| Date | Aligned | Drifted | Gaps | Notes |
|------|---------|---------|------|-------|
| 2026-03-16 | 42 | 6 | 5 | First check after Phases 1-4 implementation. 5 features status drift (code written, docs said Not Started). ModelManager.kt naming drift. Missing: StorageSettings.svelte, /api/stt/models/download, history card expansion. |
| 2026-03-16 (device test) | — | — | — | First Android emulator test. 5 bugs found: wrong STT model URL (critical), missing MODIFY_AUDIO_SETTINGS permission (critical), Generate tab text persistence, dropdown dismiss behavior, no STT download button. 1 feature request: remote VPS connection. See unknowns.md for details. |
