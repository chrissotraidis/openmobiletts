# Unknowns & Open Questions

Extracted from EXPANSION-PLAN-OPEN-MOBILE-VOICE.md and user discussion on 2026-03-16.

## Resolved Questions

### Batch vs. streaming STT?
- **Resolution:** Batch only in v3.0. Streaming deferred — technically complex, unexplored.
- **Decision:** [007-batch-stt-before-streaming.md](decisions/007-batch-stt-before-streaming.md)

### Concurrent model loading on low-RAM devices?
- **Resolution:** Document minimum requirements transparently. Not adding model-swapping complexity for low-RAM devices in v3.0.

### Audio file size limits?
- **Resolution:** 2 hours max duration for imported audio files.

### PDF export quality across platforms?
- **Resolution:** Android uses native `PdfDocument` (fastest, most native). Desktop uses ReportLab. Output may look slightly different — accepted tradeoff.
- **Decision:** [008-android-native-pdf-export.md](decisions/008-android-native-pdf-export.md)

### Moonshine model hosting?
- **Resolution:** GitHub releases, same as TTS model.
- **Decision:** [009-github-releases-model-hosting.md](decisions/009-github-releases-model-hosting.md)

## Open Questions

### WebView MediaRecorder reliability?
- **Owner:** TBD
- **Why it matters:** The mic button depends on WebView's MediaRecorder API to capture audio. If unreliable, need to fall back to native `AudioRecord` via the JS-native bridge.
- **Current thinking:** Start with MediaRecorder. Fall back to native AudioRecord only if issues arise.
- **Decide by:** During Phase 1 implementation (STT foundation)

### Minimum device RAM requirements?
- **Owner:** TBD
- **Why it matters:** Both models loaded simultaneously use ~195 MB. Need to verify this works on devices with less than 8 GB RAM and document minimum requirements.
- **Current thinking:** Target 8 GB+ RAM as minimum. Test on a mid-range device during Phase 5.
- **Decide by:** Before v3.0 release

### Repository rename timing?
- **Owner:** User
- **Why it matters:** openmobiletts → openmobilevoice affects GitHub URLs, model download URLs, package names, and documentation.
- **Current thinking:** User will handle this later. Not a v3.0 blocker.
- **Decide by:** Whenever the user is ready
