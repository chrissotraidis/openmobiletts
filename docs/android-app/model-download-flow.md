# Model Download Flow

> **Note:** The TTS model download (steps 1-6) documents existing v2.0 functionality. The STT model download (steps 7-9) is new in v3.0.

## Who

A user launching the app for the first time (or after a fresh install / data clear).

## The Happy Path

### First Launch (v3.0)

1. User installs and opens Open Mobile Voice
2. **MainActivity** checks for TTS model files in cache directory
3. TTS model not found → **download screen** appears (programmatic Views, not Compose)
4. "Downloading TTS model... (95 MB)" — progress bar with percentage
5. Model downloads from GitHub releases via ModelManager
6. ZIP is extracted to cache directory (Zip Slip protected)
7. TTS model ready → ModelManager checks for STT model
8. STT model not found → "Downloading STT model... (100 MB)" — same progress UI
9. STT model downloads and extracts
10. Both models loaded into memory via TtsManager and SttManager
11. Download screen dismisses → **WebView loads** SvelteKit app
12. App is fully functional — both TTS and STT available

### Optional Medium STT Model Download

1. User navigates to Settings → STT Defaults
2. Sees: "STT Model: Moonshine v2 Small (active)" and "Moonshine v2 Medium (250 MB) — Better accuracy"
3. Taps **Download** next to Medium model
4. Progress indicator in Settings shows download progress
5. Download completes → user can switch active STT model to Medium
6. Medium model loads into memory (Small can be unloaded or both kept, depending on RAM)

## What Could Go Wrong

### Network failure during download
- **When:** WiFi drops or server is unreachable mid-download
- **What happens:** Download pauses. Progress bar shows "Download paused — check your connection."
- **Recovery:** App retries automatically when connection is restored, or user taps "Retry."

### Download interrupted by app kill
- **When:** User force-closes app or Android kills process during download
- **What happens:** Partial files remain on disk
- **Recovery:** On next launch, ModelManager detects incomplete download (size/hash check), deletes partial files, restarts download from scratch.

### Insufficient storage space
- **When:** Device doesn't have ~200 MB free for both models (or ~450 MB if Medium STT is included)
- **What happens:** Download fails with OS-level storage error
- **Recovery:** Show "Not enough storage space. Free up X MB to continue." User frees space and retries.

### GitHub releases unreachable
- **When:** GitHub CDN is down or blocked (e.g., corporate network, regional firewall)
- **What happens:** Download fails after timeout
- **Recovery:** Show error with suggestion to check network. No alternative mirror in v3.0.

### Model file corruption
- **When:** Download completes but file is corrupt (network error, disk error)
- **What happens:** TtsManager or SttManager fails to initialize (sherpa-onnx throws exception)
- **Recovery:** ModelManager detects initialization failure, deletes corrupt files, prompts re-download.

### Zip Slip attack (security)
- **When:** A compromised model ZIP contains path traversal entries (e.g., `../../etc/passwd`)
- **What happens:** ModelDownloader validates all ZIP entry paths — rejects any that escape the target directory
- **Recovery:** Download is rejected. Logged as security event. This is already implemented in v2.0.

## Acceptance Criteria

Existing (v2.0):
- [x] First launch shows download progress for TTS model
- [x] TTS model downloads from GitHub releases
- [x] ZIP extraction is Zip Slip protected
- [x] App doesn't load WebView until model is ready
- [x] Interrupted downloads are detected and restarted on next launch

New (v3.0):
- [ ] First launch downloads both TTS (~95 MB) and STT (~100 MB) models sequentially
- [ ] Progress UI shows which model is downloading (TTS vs STT)
- [ ] Settings shows STT model status (Small downloaded, Medium available)
- [ ] Optional Medium model download works from Settings
- [ ] Both models load into memory simultaneously (~195 MB)
- [ ] Network failure during download shows clear error and allows retry

## Related

- See: [Android App Overview](android-app-overview.md) for full Kotlin architecture
- See: [STT Overview](../stt/stt-overview.md) for model specs and platform strategy
- See: [Decision 009](../decisions/009-github-releases-model-hosting.md) for hosting choice
