# Android App

## What It Does

Runs the same SvelteKit web app in an Android WebView, backed by an embedded NanoHTTPD server that bridges API calls to on-device Sherpa-ONNX engines. Fully local after model download — no external server needed.

## Why It Matters

Enables the app to run entirely on a phone with no desktop server. Same UI, same features, fully offline after initial setup.

## Core Rules

### Architecture

- WebView loads SvelteKit build from embedded NanoHTTPD on `127.0.0.1:8080` (code-derived)
- NanoHTTPD serves both static files (SvelteKit build) and API endpoints (code-derived)
- Sherpa-ONNX for on-device TTS inference via JNI (code-derived)
- All UI is in SvelteKit — no Jetpack Compose code (decided — [005](../decisions/005-webview-over-compose.md))
- `window.Android` bridge object indicates WebView context — use for feature detection (code-derived)
- Server binds to `127.0.0.1` only — not network-accessible (code-derived)

### Job-Based Generation (Resilient to Stream Drops)

- Each `/api/tts/stream` creates a `TtsJob`, immediately sends `JOB:{id}` to client (code-derived)
- Generation thread writes audio to disk **before** streaming to client (code-derived)
- On backgrounding: `player.js` detects `visibilitychange`, proactively aborts HTTP stream (code-derived)
- Generation thread continues unaffected — no HTTP backpressure (code-derived)
- Client enters recovery: polls `/api/tts/jobs/{id}/status` every 5s until complete (code-derived)
- Client fetches complete audio from `/api/tts/jobs/{id}/audio` and timing from `/api/tts/jobs/{id}/timing` (code-derived)
- Jobs auto-cleaned: 2-hour TTL after completion + purge on server restart (code-derived)

### Notifications

- Foreground service (`TtsService`) with two modes (code-derived):
  - **Generation mode**: progress text ("Generating speech... 3/12 chunks"), progress updates bypass WebView via direct `TtsService.instance?.updateProgress()` call
  - **Playback mode**: `MediaStyle` notification with play/pause/stop, lock screen + Bluetooth via `MediaSessionCompat`
- Notification → WebView routing: `PendingIntent` → `TtsService.onStartCommand` → `playbackCommandCallback` → `webView.evaluateJavascript("window.__ttsControl?.play()")` (code-derived)

### Model Download

- First launch: downloads Kokoro INT8 model (~95 MB) from GitHub releases (code-derived)
- v3.0: also downloads Moonshine v2 Small (~100 MB) — sequential download (spec-stated)
- Optional: Moonshine v2 Medium (~250 MB) downloadable from Settings (spec-stated)
- Zip Slip protection on model extraction (code-derived)

### Build Workflow

```bash
# Bundle web app into Android assets:
./android/copy-webapp.sh
# Or manually:
cd client && npm run build
cp -r build android/app/src/main/assets/webapp/
# Open android/ in Android Studio → Run
```

### Known Constraints

- `npm run build` does NOT update Android — must copy to `android/app/src/main/assets/webapp/`
- AAPT ignores `_*` directories — needs custom `aaptOptions` in build.gradle.kts
- Cleartext HTTP requires `android:usesCleartextTraffic="true"` in AndroidManifest.xml
- No `<input type="file">` without native `WebChromeClient.onShowFileChooser()`
- Clean build required after asset changes

## Kotlin Source Files

| File | Purpose |
|------|---------|
| `MainActivity.kt` | WebView host + model download UI + `@JavascriptInterface` bridge |
| `TtsHttpServer.kt` | NanoHTTPD: API + static files + TtsJob + QueueInputStream |
| `TtsManager.kt` | Sherpa-ONNX wrapper (Mutex-safe init, coroutine-based) |
| `AacEncoder.kt` | PCM → AAC audio (hardware MediaCodec, ADTS framing) |
| `WavEncoder.kt` | PCM → WAV conversion (fallback) |
| `VoiceRegistry.kt` | Voice name → SID mapping |
| `ModelDownloader.kt` | First-launch model download (Zip Slip protected) |
| `TtsService.kt` | Foreground service: generation progress + media controls |
| `AppLog.kt` | In-app log ring buffer |
| `OpenMobileTtsApp.kt` | Application singleton: TtsManager + server lifecycle |

## v3.0 New Kotlin Classes

| File | Purpose |
|------|---------|
| `SttManager.kt` | Sherpa-ONNX `OfflineRecognizer` wrapper for Moonshine STT |
| `AudioDecoder.kt` | MediaExtractor + MediaCodec → PCM for imported audio |
| `ExportManager.kt` | PDF via PdfDocument, MD/TXT via file I/O |
| `ProjectStorage.kt` | JSON project CRUD + auto-cleanup |
| `ModelManager.kt` | Extended ModelDownloader for TTS + STT models |

## Key References

- **Full architecture:** `docs/ANDROID_ARCHITECTURE.md`
- **JS-Native bridge:** `docs/ANDROID_ARCHITECTURE.md`, "JS ↔ Native Bridge Methods"
- **Decision:** [005-webview-over-compose.md](../decisions/005-webview-over-compose.md)

## Status

🟢 Implemented (v2.0) — v3.0 additions (SttManager, AudioDecoder, ExportManager, ProjectStorage, ModelManager) are 🔵 Not Started
