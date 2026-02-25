# Open Mobile TTS - Implementation Status

**Last Updated**: 2026-02-25
**Status**: ✅ Fully functional monolithic app — server, client, and Android support complete

---

## Architecture

Open Mobile TTS is a **single-process application**. `python run.py` builds the SvelteKit client and starts FastAPI to serve both the API and the UI on port 8000.

No authentication. No separate processes. CORS enabled for Android WebView support.

---

## ✅ Completed: Server

### Core Modules

**1. Configuration (`server/src/config.py`)**
- Environment variable management with python-dotenv
- Sensible defaults for all settings

**2. Text Preprocessing (`server/src/text_preprocessor.py`)**
- Unicode normalization (NFKC)
- Abbreviation expansion (Dr., Mr., Inc., etc.)
- Number-to-words conversion
- PDF artifact removal (page numbers, hyphenation)
- Sentence-boundary chunking (optimized for Kokoro's 510 token limit)

**3. Document Processing (`server/src/document_processor.py`)**
- PDF extraction via pymupdf4llm
- DOCX extraction via python-docx
- Plain text (.txt) support

**4. Audio Encoding (`server/src/audio_encoder.py`)**
- Numpy array to MP3 conversion via pydub
- 64kbps CBR, 22050Hz mono
- Streaming-optimized chunk encoding

**5. TTS Engine (`server/src/tts_engine.py`)**
- Kokoro TTS wrapper with streaming support
- Async generator for progressive audio delivery
- Timing metadata generation (sentence-level)
- 11 voices (American/British, Male/Female)

**6. FastAPI Application (`server/src/main.py`)**
- Voice listing (`/api/voices`)
- Text-to-speech streaming (`/api/tts/stream`)
- Document upload (`/api/documents/upload`)
- Document-to-speech streaming (`/api/documents/stream`)
- Static file serving (built SvelteKit UI)
- Health check endpoint
- Log export endpoint
- CORS middleware (for Android WebView cross-origin requests)

---

## ✅ Completed: Client

### Components

**1. Main Interface (`+page.svelte`)**
- Single-page app — no routing, no login
- Text input with voice/speed controls
- Document upload (PDF, DOCX, TXT up to 10MB)

**2. Audio Player (`AudioPlayer.svelte`)**
- Bottom player bar with playback controls
- Progress tracking and seeking
- Visible "Cancel" button (44px touch target) during generation — sends cancel to the server-side job
- Download button hidden during active generation

**3. Text Display (`TextDisplay.svelte`)**
- Sentence-level highlighting synchronized with audio

**4. Generation Progress (`GenerationProgress.svelte`)**
- Progress bar, chunk counter, elapsed time, estimated time remaining
- Cancel button routes through `playerStore.stop()` which also cancels the server-side job

**5. Audio History (`AudioHistory.svelte`)**
- History list with play, queue, download, and delete
- Cache status indicators: "Audio ready" (cached in IndexedDB) vs "Text only" (no cached audio)
- Reader/detail view with synchronized text highlighting and Android back button support (`popstate`)
- Opening an entry in reader mode auto-plays only if audio is cached (avoids triggering expensive on-device regeneration)

**6. Settings**
- Voice and speed persistence (localStorage)
- Server URL configuration (for Android / remote connections)
- Test Connection with timeout and status feedback
- Log export

**7. Stores & Services**
- `player.js` — Audio playback state + streaming protocol parser + Android job recovery logic
  - Parses `JOB:{id}` from stream to track the active server-side job
  - On stream error (Android only, non-abort): polls `/api/tts/jobs/{id}/status` every 5 seconds, fetches recovered audio + timing when complete
  - `stop()` sends `POST /api/tts/jobs/{id}/cancel` to terminate server-side generation
- `settings.js` — Persisted user preferences (including server URL)
- `history.js` — History refresh events
- `api.js` — Fetch wrapper with configurable base URL for Android support
- `audioCache.js` — IndexedDB audio cache; added `getCachedIds()` for lightweight cache status queries (returns a `Set` of all cached history IDs without loading audio data)

---

## ✅ Completed: Infrastructure

- **`run.py`** — Single entry point: checks deps, builds client, starts server
- **Docker** — Multi-stage Dockerfile + docker-compose.yml
- **CI** — GitHub Actions workflow for server tests

---

## ✅ Completed: Android App (WebView + Native TTS)

The app runs on Android using the **same SvelteKit web app** in a WebView, backed by an embedded NanoHTTPD server that calls the on-device Sherpa-ONNX TTS engine.

- **WebView** loads the SvelteKit build — identical UI to desktop
- **NanoHTTPD** embedded HTTP server: API endpoints + static file serving
- **Sherpa-ONNX** TTS engine with Kokoro INT8 model (~95 MB)
- **Job-based generation**: each `/api/tts/stream` request creates a `TtsJob` that writes audio directly to `{filesDir}/tts_jobs/{jobId}/audio.aac`. Generation continues even if the WebView HTTP stream drops.
- **Job recovery endpoints**: `GET /api/tts/jobs/{id}/status|audio|timing`, `POST /api/tts/jobs/{id}/cancel`
- **Client-side recovery**: `player.js` polls job status on stream error (Android only) and recovers the completed audio automatically
- **Foreground service** notification to keep process alive during generation
- **Model download** on first launch
- Run `./android/copy-webapp.sh` then open `android/` in Android Studio

See [ANDROID_ARCHITECTURE.md](ANDROID_ARCHITECTURE.md) for full details.

---

## Repository Structure

```
openmobiletts/
├── run.py                           Single-command launcher
├── Dockerfile                       Multi-stage Docker build
├── docker-compose.yml               Docker convenience wrapper
├── server/
│   ├── src/
│   │   ├── main.py                 FastAPI app + static serving + CORS
│   │   ├── config.py               Environment configuration
│   │   ├── tts_engine.py           Kokoro TTS wrapper
│   │   ├── text_preprocessor.py    Text cleaning & chunking
│   │   ├── document_processor.py   PDF/DOCX extraction
│   │   └── audio_encoder.py        MP3 encoding
│   ├── tests/                      Automated tests
│   ├── requirements.txt            Python dependencies
│   ├── .env.example                Config template
│   └── setup_models.py             Model download script
├── client/
│   ├── src/
│   │   ├── routes/+page.svelte     Main TTS interface
│   │   ├── lib/components/         UI components
│   │   ├── lib/stores/             State management
│   │   └── lib/services/           API client (configurable base URL)
│   ├── static/                     PWA manifest
│   ├── package.json                Node dependencies
│   └── svelte.config.js            SvelteKit config
├── android/
│   ├── copy-webapp.sh              Bundles SvelteKit build into assets
│   └── app/src/main/java/com/openmobiletts/app/
│       ├── MainActivity.kt         WebView host + model download UI
│       ├── TtsHttpServer.kt        NanoHTTPD: API + static files + job system (TtsJob, QueueInputStream)
│       ├── TtsManager.kt           Sherpa-ONNX wrapper (Mutex-safe init)
│       ├── AacEncoder.kt           PCM → AAC audio (hardware MediaCodec)
│       ├── WavEncoder.kt           PCM → WAV conversion (fallback)
│       ├── VoiceRegistry.kt        Voice name → SID mapping
│       ├── ModelDownloader.kt      First-launch model download (Zip Slip protected)
│       ├── TtsService.kt           Foreground notification
│       ├── AppLog.kt               In-app log ring buffer
│       └── OpenMobileTtsApp.kt     Application singleton
├── docs/                           Documentation
└── .github/workflows/              CI/CD
```
