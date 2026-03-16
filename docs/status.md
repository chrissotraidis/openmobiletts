# Project Status

**Last Updated:** 2026-03-16
**Current Version:** v2.0.0 (production)
**Next Version:** v3.0.0 (in planning)

## Feature Status

| Feature | Status | Notes |
|---------|--------|-------|
| [TTS](tts/tts-overview.md) | 🟢 Implemented | Core TTS fully working on both platforms |
| [Document Import](document-import/document-import-overview.md) | 🟢 Implemented | PDF/DOCX/TXT/MD extraction working |
| [Audio Playback](audio-playback/audio-playback-overview.md) | 🟢 Implemented | Player, queue, history, highlighting all working |
| [Android App](android-app/android-app-overview.md) | 🟢 Implemented | WebView + Sherpa-ONNX, job recovery, notifications |
| [STT](stt/stt-overview.md) | 🔵 Not Started | Moonshine v2, v3.0 feature |
| [Document Export](document-export/document-export-overview.md) | 🔵 Not Started | PDF/MD/TXT, v3.0 feature |
| [Audio Import](audio-import/audio-import-overview.md) | 🔵 Not Started | Transcribe audio files, v3.0 feature |
| [Project Storage](project-storage/project-storage-overview.md) | 🔵 Not Started | JSON project management, v3.0 feature |
| [Unified Input](unified-input/unified-input-overview.md) | 🔵 Not Started | Redesigned Generate tab, v3.0 feature |
| [Multi-Language](multi-language/multi-language-overview.md) | 🔵 Not Started | 9-language TTS, planned |

## v3.0 Implementation Phases

| Phase | Goal | Status |
|-------|------|--------|
| Phase 1: Foundation | Moonshine STT end-to-end on Android | 🔵 Not Started |
| Phase 2: STT UI + Audio Import | Mic button, audio decoder, file import | 🔵 Not Started |
| Phase 3: Export + Projects | PDF/MD/TXT export, project storage | 🔵 Not Started |
| Phase 4: Desktop Parity | Python STT, export, project storage | 🔵 Not Started |
| Phase 5: Polish + Testing | Testing, UI polish, docs | 🔵 Not Started |

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

_No checks run yet. Run `/arnold:check` after writing code._
