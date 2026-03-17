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

## Bugs — Device Testing (3/16/26, Android Emulator)

### BUG-1: STT model download URL is wrong (CRITICAL)
- **Severity:** Blocks all STT functionality
- **Symptom:** `Download size: 0 MB` then `Background STT model download failed`
- **Cause:** URL `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-moonshine-medium-en-int8.tar.bz2` returns empty/404
- **Fix needed:** Find the correct sherpa-onnx release URL for Moonshine v2 Medium INT8 model
- **Files:** `ModelDownloader.kt` (STT_MODEL_NAME, STT_MODEL_URL), `stt_engine.py` (MODEL_NAME)

### BUG-2: Missing MODIFY_AUDIO_SETTINGS permission (CRITICAL)
- **Severity:** Blocks all microphone recording
- **Symptom:** `Requires MODIFY_AUDIO_SETTINGS and RECORD_AUDIO. No audio device will be available for recording`
- **Cause:** AndroidManifest.xml only has `RECORD_AUDIO`, missing `MODIFY_AUDIO_SETTINGS`
- **Fix needed:** Add `<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />` to AndroidManifest.xml
- **Files:** `AndroidManifest.xml`

### BUG-3: Generate tab doesn't clear text when switching back
- **Severity:** UX — confusing
- **Symptom:** Navigate from History (after playing an entry) back to Generate tab — old text from previous entry persists in the text area
- **Fix needed:** Clear text area state when switching to Generate tab, or at minimum when navigating away from a history detail view
- **Files:** `+page.svelte` (tab switching logic), `TextInput.svelte` (text state)

### BUG-4: Dropdown menus don't close on outside tap
- **Severity:** UX — annoying
- **Symptom:** Speed picker, export picker, and other popup menus stay open when tapping elsewhere on screen. Must re-tap the trigger button or select an option to close.
- **Fix needed:** Add click-outside handler or invisible backdrop overlay to dismiss popups
- **Files:** `TextInput.svelte` (showSpeedPicker, showExportPicker), `+page.svelte` (any settings dropdowns)

### BUG-5: No STT model download button in Settings
- **Severity:** UX — feature incomplete
- **Symptom:** Settings shows "Moonshine v2 Medium: Not downloaded" but no way to trigger download
- **Fix needed:** Add a download button next to the model status that triggers model download with progress
- **Files:** `+page.svelte` (Storage & STT settings section)

## Feature Requests — Device Testing (3/16/26)

### FEAT-1: Remote VPS connection for Android
- **What:** Connect Android app to a remote desktop/VPS server running Open Mobile Voice instead of using local NanoHTTPD
- **Why:** Desktop GPU is 35-210x real-time vs Android's 0.8x RTF. Remote connection gives phone users desktop-class speed.
- **How:** The API contract is identical between NanoHTTPD and FastAPI. Android app could toggle between local engine and remote server. The existing `serverUrl` setting in the web app already supports this partially.
- **Scope:** New feature — needs secure authentication for remote access, server URL configuration, connection testing, fallback to local
- **Priority:** Post-v3.0 — document and plan separately

## Open Questions

### WebView MediaRecorder reliability?
- **Owner:** TBD
- **Status:** PARTIALLY ANSWERED — WebView requests AUDIO_CAPTURE permission correctly (logs confirm `onPermissionRequest` fires), but fails due to missing `MODIFY_AUDIO_SETTINGS` permission (BUG-2). After BUG-2 fix, test on real device.
- **Current thinking:** Fix the permission first, then test on Pixel 9 Pro. Emulator may have additional mic limitations.
- **Decide by:** After BUG-2 fix

### Minimum device RAM requirements?
- **Owner:** TBD
- **Why it matters:** Both models loaded simultaneously use ~345 MB (Medium STT). Need to verify this works on devices with less than 8 GB RAM.
- **Current thinking:** Target 8 GB+ RAM as minimum. Test on a mid-range device.
- **Decide by:** Before v3.0 release

### Repository rename timing?
- **Owner:** User
- **Why it matters:** openmobiletts → openmobilevoice affects GitHub URLs, model download URLs, package names, and documentation.
- **Current thinking:** User will handle this later. Not a v3.0 blocker.
- **Decide by:** Whenever the user is ready
