# Android Architecture: WebView + Native TTS Bridge

## Why WebView?

The Android app uses a **WebView** to display the same SvelteKit web app that runs on desktop. This means:

- **Zero UI duplication** — one codebase for all platforms
- **Automatic feature parity** — every web UI change works on Android immediately
- **Mobile-first already** — the SvelteKit app has responsive layouts, 44×44px touch targets, and PWA support

The previous approach (rewriting every screen in Jetpack Compose) required maintaining two separate UIs and was fundamentally unsustainable.

## How It Works

```
Desktop:  Browser → http://localhost:8000 → FastAPI (Python) → Kokoro TTS
Android:  WebView → http://localhost:8080 → NanoHTTPD (Kotlin) → Sherpa-ONNX TTS
```

Both platforms use the **identical SvelteKit frontend**. The only difference is the backend.

### Architecture Overview

1. **NanoHTTPD** serves both static files (SvelteKit build) and API endpoints on `127.0.0.1:8080`
2. **WebView** loads `http://127.0.0.1:8080/` — same-origin, so `getBaseUrl()` returns `''`
3. **TTS generation** calls go to `/api/tts/stream` which runs Sherpa-ONNX natively
4. **Audio playback** happens in the WebView's HTML5 Audio element (not native Android audio)
5. **History/settings** persist via WebView's localStorage and IndexedDB

### Request Flow (TTS Generation)

```
User taps "Generate" in WebView
  → JavaScript fetch('/api/tts/stream', {text, voice, speed})
  → NanoHTTPD receives POST
  → Creates a TtsJob (id, jobDir, totalChunks)
  → Starts background generation thread
  → Streams JOB:{id}\n CHUNKS:{total}\n to client immediately
  → For each sentence:
      → Sherpa-ONNX generates PCM float samples
      → AacEncoder converts to AAC audio (hardware MediaCodec)
      → Writes audio chunk to {filesDir}/tts_jobs/{jobId}/audio.aac (disk-first)
      → Streams TIMING:{json}\n AUDIO:{length}\n {aacBytes} to client
  → WebView JavaScript parses stream, creates Audio blobs
  → HTML5 Audio element plays each chunk
```

**Generation is resilient to stream drops.** If the WebView HTTP stream breaks (e.g., WebView throttled when backgrounded), the Kotlin generation thread continues writing audio to disk. The client detects the stream drop, then polls the job status endpoint and recovers the completed audio via `/api/tts/jobs/{id}/audio` when generation finishes.

### Job Recovery Flow (Android-specific)

```
Stream drops (network error / WebView throttled)
  → client detects non-AbortError fetch failure
  → reads activeJobId captured from JOB:{id} line
  → polls GET /api/tts/jobs/{id}/status every 5 seconds
  → shows recovery progress UI (placeholder segments)
  → when status == "complete":
      → fetches GET /api/tts/jobs/{id}/audio
      → fetches GET /api/tts/jobs/{id}/timing
      → sets up audio playback from recovered data
      → caches recovered audio to IndexedDB
```

## What's Native (Kotlin)

Only the minimum needed to run TTS on-device:

| File | Purpose |
|------|---------|
| `TtsManager.kt` | Wraps Sherpa-ONNX JNI, generates PCM FloatArray (coroutine Mutex for thread-safe init) |
| `TtsHttpServer.kt` | NanoHTTPD server: API endpoints + static files + job system (1 MB body cap, cancellation-safe) |
| `TtsJob` (in TtsHttpServer.kt) | Tracks a background generation job: id, status, completedChunks, totalChunks, audioFile, timingFile, audioFormat, cancelled, error, completedAt |
| `QueueInputStream` (in TtsHttpServer.kt) | Backpressure-aware bounded queue (50 entries) bridging the generation thread to NanoHTTPD's response stream |
| `AacEncoder.kt` | PCM FloatArray → AAC audio via hardware MediaCodec (ADTS framing) |
| `WavEncoder.kt` | PCM FloatArray → WAV bytes (fallback, 44-byte header + 16-bit PCM) |
| `VoiceRegistry.kt` | Voice name → SID mapping, JSON for /api/voices (JSONArray/JSONObject) |
| `ModelDownloader.kt` | Downloads Kokoro model from GitHub (~95MB, Zip Slip protected) |
| `TtsService.kt` | Foreground notification to keep process alive during generation |
| `AppLog.kt` | In-app log ring buffer with thread-safe timestamps |
| `MainActivity.kt` | WebView host + model download gate + native bridge (`@JavascriptInterface`) |
| `OpenMobileTtsApp.kt` | Application singleton: TtsManager + server lifecycle |

## Build Process

### Bundle the Web App

Before building the Android APK, bundle the SvelteKit build into assets:

```bash
# From the repository root:
./android/copy-webapp.sh

# Or manually:
cd client && npm run build
cp -r build android/app/src/main/assets/webapp/
```

The Gradle build also has a `bundleWebApp` task:
```bash
cd android && ./gradlew bundleWebApp
```

### Build the APK

```bash
cd android && ./gradlew assembleDebug
```

## API Compatibility

The NanoHTTPD server implements the same endpoints as the Python FastAPI server, plus additional job recovery endpoints that exist only on Android:

| Endpoint | Desktop (Python) | Android (Kotlin) |
|----------|-----------------|-------------------|
| `GET /api/voices` | From Kokoro/Sherpa engine | From VoiceRegistry |
| `GET /api/health` | FastAPI | NanoHTTPD |
| `GET /api/engines` | Multiple engines | Single (sherpa-onnx) |
| `POST /api/tts/stream` | MP3 chunks | AAC chunks + job system |
| `GET /api/tts/jobs/{id}/status` | Not implemented | Job progress: id, status, completed, total, format, error |
| `GET /api/tts/jobs/{id}/audio` | Not implemented | Completed audio file from disk |
| `GET /api/tts/jobs/{id}/timing` | Not implemented | Timing metadata array (partial if still generating) |
| `POST /api/tts/jobs/{id}/cancel` | Not implemented | Cancel a running job |
| `POST /api/documents/upload` | PDF/DOCX extraction | TXT only (Android) |
| `/*` (static) | From client/build/ | From assets/webapp/ |

### Job Lifecycle

Jobs are stored in `{filesDir}/tts_jobs/{jobId}/` and automatically cleaned up:
- **2-hour TTL** after completion — expired jobs are pruned before each new request
- **On server restart** — leftover job directories from previous sessions are deleted in `init {}`

### Audio Format Difference

- **Desktop**: MP3 (64kbps CBR, 22050Hz mono) via pydub/ffmpeg
- **Android**: AAC-LC (hardware-accelerated via MediaCodec, ADTS framing, 24000Hz mono) — ~6x smaller than WAV, no external library needed

The web client's `new Audio(blobUrl)` plays both formats natively. The streaming protocol (TIMING/AUDIO framing) is identical.

## Key Design Decisions

| Decision | Choice | Reasoning |
|----------|--------|-----------|
| UI approach | WebView | One source of truth, zero duplication |
| HTTP server | NanoHTTPD | Lightweight, proven on Android, streaming support |
| Audio format | AAC (ADTS) | Hardware MediaCodec encoding, ~6x smaller than WAV, no external libs |
| Server binding | 127.0.0.1 only | Security: not network-accessible |
| Download screen | Programmatic Views | Simple progress UI without Compose or XML layouts |
| Job-based generation | `TtsJob` + disk write | Android WebView throttles JS when backgrounded; writing to disk lets generation survive stream drops |
| Backpressure queue | `QueueInputStream` (50 entries) | Prevents OOM on very long texts when the client reads slowly |
| Job cleanup | 2-hour TTL + restart purge | Balances recovery window against disk usage |
| Timing thread-safety | `synchronizedList` + snapshot | Avoids `ConcurrentModificationException` when reading partial timing during active generation |
