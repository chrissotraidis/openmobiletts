# Open Mobile TTS - Implementation Status

**Last Updated**: 2026-02-18
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

**3. Text Display (`TextDisplay.svelte`)**
- Sentence-level highlighting synchronized with audio

**4. Audio History (`AudioHistory.svelte`)**
- History list with replay and delete

**5. Settings**
- Voice and speed persistence (localStorage)
- Server URL configuration (for Android / remote connections)
- Test Connection with timeout and status feedback
- Log export

**6. Stores & Services**
- `player.js` — Audio playback state + streaming protocol parser
- `settings.js` — Persisted user preferences (including server URL)
- `history.js` — History refresh events
- `api.js` — Fetch wrapper with configurable base URL for Android support

---

## ✅ Completed: Infrastructure

- **`run.py`** — Single entry point: checks deps, builds client, starts server
- **Docker** — Multi-stage Dockerfile + docker-compose.yml
- **CI** — GitHub Actions workflow for server tests

---

## ✅ Completed: Android (Capacitor)

The app runs on Android via **Capacitor** — the same SvelteKit web app wrapped in a native Android shell. See [ANDROID_APP_GUIDE.md](ANDROID_APP_GUIDE.md) for full details.

- **Capacitor WebView** loads the built SvelteKit SPA
- **Configurable server URL** connects to the Python server over WiFi
- **Test Connection** button verifies server connectivity with 5-second timeout
- **CORS middleware** on the server allows cross-origin requests from the WebView
- **Mixed content** enabled for HTTPS-origin WebView making HTTP API calls
- Build with `npm run build:android` → open `client/android/` in Android Studio

---

## 📋 Future: Native On-Device TTS

See [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for a potential native app with on-device inference:

- **Sherpa-ONNX** for on-device TTS inference
- **Kokoro INT8** model (~100 MB)
- **Jetpack Compose** UI
- **AudioTrack** for streaming playback

See also [OFFLINE_TTS_FEASIBILITY.md](OFFLINE_TTS_FEASIBILITY.md) for feasibility analysis.

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
│   ├── capacitor.config.ts         Capacitor config (Android)
│   ├── android/                    Android project (Capacitor-generated)
│   ├── package.json                Node dependencies
│   └── svelte.config.js            SvelteKit config
├── docs/                           Documentation
└── .github/workflows/              CI/CD
```
