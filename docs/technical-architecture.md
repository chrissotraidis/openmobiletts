# Technical Architecture

## Overview

Open Mobile TTS is a single-process web application that converts text to speech using the Kokoro TTS model. FastAPI serves both the TTS API and the built SvelteKit UI from one process on one port.

```
python run.py
│
├── /api/voices          → List available voices
├── /api/tts/stream      → Stream TTS audio + timing metadata
├── /api/documents/*     → Upload and process documents
└── /*                   → SvelteKit UI (static files)
```

## Why These Technologies?

### Why FastAPI?

- Native `StreamingResponse` for chunked MP3 delivery
- Async support for non-blocking I/O during TTS generation
- Auto-generated API docs at `/docs` (OpenAPI/Swagger)
- Lightweight — single process, low overhead

### Why SvelteKit?

- Svelte 5 compiles to minimal JavaScript — fast on mobile
- SvelteKit's static adapter produces a build that FastAPI can serve directly
- Reactive stores make audio state management clean
- No virtual DOM overhead — better performance for real-time UI updates (text highlighting)

### Why Kokoro TTS?

- 82M parameters — small enough for CPU inference, large enough for quality
- #1 on HuggingFace TTS Arena (as of late 2024)
- Native generator-based streaming API
- Apache 2.0 license (commercial use permitted)
- 11 built-in voices across American/British English

## Streaming Protocol Design

The TTS endpoint uses a custom streaming protocol that interleaves text metadata with binary audio data:

```
TIMING:{"text":"Hello world","start":0.0,"end":1.2}\n
<MP3 bytes>
TIMING:{"text":"How are you","start":1.2,"end":2.5}\n
<MP3 bytes>
...
```

### Why this design?

1. **Single HTTP response** — No WebSockets, no polling, just one `GET` request
2. **Progressive playback** — Client can start playing before all audio is generated
3. **Text synchronization** — Timing metadata enables sentence highlighting during playback
4. **Binary-safe** — MP3 data is sent as raw bytes (no base64 bloat)
5. **Interruptible** — Client can abort the fetch to cancel generation

### Client parsing

The client uses a binary-safe streaming parser:
- Scans the byte stream for `TIMING:` markers
- Extracts JSON metadata as text
- Collects remaining bytes as MP3 audio
- Concatenates all audio chunks into a single `Blob` for playback

## Text Processing Pipeline

```
Raw text
  → Unicode normalization (NFKC)
  → PDF artifact removal
  → Abbreviation expansion (Dr. → Doctor)
  → Number-to-words (123 → one hundred twenty-three)
  → Sentence-boundary chunking (175-250 tokens per chunk)
  → Kokoro TTS generation (per chunk)
  → MP3 encoding (64kbps CBR, 22050Hz mono)
  → Streaming response
```

### Why 175-250 tokens per chunk?

Kokoro has a hard limit of **510 tokens per inference pass**. We use 175-250 to:
- Stay well below the limit
- Split at natural sentence boundaries
- Produce chunks of ~5-10 seconds of audio each
- Enable responsive streaming (first audio arrives quickly)

### Why 64kbps CBR MP3?

- **CBR** (not VBR) prevents playback stuttering during streaming
- **64kbps** is excellent for speech (voice doesn't need high bitrate)
- **22050Hz mono** — speech doesn't benefit from stereo or higher sample rates
- Result: ~480KB per minute of audio

## Document Processing

| Format | Library | Notes |
|--------|---------|-------|
| PDF | pymupdf4llm | Handles multi-column layouts, outputs Markdown |
| DOCX | python-docx | Extracts paragraph text |
| TXT | Built-in | Direct read |

All formats go through Markdown-to-plain-text conversion, then the standard text processing pipeline.

## Architecture: Single-App Design

### Before (v0.1 — client-server)
- Two processes: SvelteKit dev server (port 5173) + FastAPI (port 8000)
- JWT authentication with Argon2 password hashing
- CORS middleware required
- Complex deployment with separate containers

### After (v0.2 — monolithic)
- Single process: FastAPI serves everything on port 8000
- No authentication (local-only app)
- CORS enabled (required for Android WebView cross-origin requests)
- One `python run.py` command

### Why no authentication?

This is a personal, local tool:
- Running on `localhost` — already behind the network boundary
- No multi-user features needed
- Removes entire categories of bugs (token expiry, secret management)
- Simpler deployment and maintenance

If exposed on a network, access control should happen at the network/proxy level (firewall, VPN, reverse proxy auth).

### Why CORS?

When accessed from other devices on the local network, cross-origin requests would be blocked without CORS. Since this is a local-only app with no auth, wildcard CORS (`allow_origins=["*"]`) is appropriate.

## Performance Characteristics

| Hardware | TTS Speed | First Audio Chunk |
|----------|-----------|-------------------|
| RTX 4090 | ~210x real-time | ~80ms |
| RTX 3090 Ti | ~90x real-time | ~150ms |
| 8-core CPU | 3-11x real-time | ~1-3s |

For a 5,000-word document (~20 min audio):
- **GPU**: 6-35 seconds total generation
- **CPU**: 2-6 minutes total generation
- **Streaming**: User hears first audio within seconds regardless

## Deployment Options

### Local (recommended for personal use)
```bash
python run.py
```

### Docker (recommended for VPS)
```bash
docker compose up --build
```

The Dockerfile is a multi-stage build:
1. **Stage 1**: Node.js builds the SvelteKit client
2. **Stage 2**: Python runtime with espeak-ng + ffmpeg, serves everything

### Development (hot reload)
```bash
# Terminal 1: uvicorn src.main:app --reload --port 8000
# Terminal 2: cd client && npm run dev
```

Vite proxies `/api/*` requests to the Python server.

## Android Support (WebView + Native TTS Bridge)

The app runs on Android using the **same SvelteKit web app** in a WebView, backed by an embedded NanoHTTPD server that bridges API calls to the on-device Sherpa-ONNX TTS engine.

```
Desktop:  Browser → http://localhost:8000 → FastAPI (Python) → Kokoro TTS
Android:  WebView → http://localhost:8080 → NanoHTTPD (Kotlin) → Sherpa-ONNX TTS
```

Both platforms use the **identical SvelteKit frontend**. The only difference is the backend.

### Key Architecture

- **WebView** loads the SvelteKit build from an embedded HTTP server
- **NanoHTTPD** serves static files + API endpoints on `127.0.0.1:8080`
- **Sherpa-ONNX** for on-device TTS inference via JNI
- **AacEncoder** — hardware-accelerated AAC encoding via MediaCodec (ADTS framing)
- **TtsService** foreground notification to keep process alive during generation
- **Model download** on first launch (~95 MB Kokoro INT8)

### Build Workflow

```bash
# 1. Bundle web app into Android assets:
./android/copy-webapp.sh

# 2. Open android/ in Android Studio → Run
```

See [ANDROID_ARCHITECTURE.md](ANDROID_ARCHITECTURE.md) for full details.