# How Open Mobile TTS Works

## Overview

Open Mobile TTS is a **fully local, private text-to-speech system** that runs entirely on your own hardware. No external APIs are called, and no data leaves your machine.

## Architecture Summary

Open Mobile TTS is a **single-process application**. `python run.py` builds the SvelteKit UI and starts a FastAPI server that serves both the API and the built static files on one port.

```
┌─────────────────────────────────────────────────────────────┐
│                        Your Machine                          │
│                                                              │
│  ┌──────────────────┐              ┌────────────────────┐  │
│  │   Web Browser    │◄────HTTP─────►   FastAPI Server   │  │
│  │   (SvelteKit)    │   localhost   │   (Python)        │  │
│  │                  │     :8000     │                    │  │
│  │  • UI/Forms      │              │  • Text Processing │  │
│  │  • Audio Player  │              │  • TTS Generation  │  │
│  │  • localStorage  │              │  • Static Serving  │  │
│  └──────────────────┘              └────────┬───────────┘  │
│                                              │              │
│                                              ▼              │
│                                    ┌────────────────────┐  │
│                                    │   Kokoro TTS       │  │
│                                    │   82M Model        │  │
│                                    │   (Local CPU/GPU)  │  │
│                                    └────────────────────┘  │
│                                              │              │
│                                              ▼              │
│                                    ~/.cache/kokoro/        │
│                                    (Model files ~320MB)    │
└─────────────────────────────────────────────────────────────┘
```

**No authentication.** No separate processes. One command, one port. CORS enabled for Android support.

## Is the Model Running Locally?

**Yes, 100% local.** Here's how it works:

### Model Download & Storage

1. **First run**: When you start the app, it downloads the Kokoro TTS model from HuggingFace
2. **Storage location**: `~/.cache/kokoro/` (approximately 320MB)
3. **No API calls**: After initial download, everything runs locally on your CPU/GPU

### Model Details

- **Name**: Kokoro TTS (82M parameters)
- **License**: Apache 2.0 (fully open source, can use commercially)
- **Quality**: State-of-the-art neural TTS (#1 on HuggingFace TTS Arena)
- **Speed**:
  - GPU: 35-210x real-time speed
  - CPU: ~2-5x real-time speed
- **Voices**: 11 built-in voices (American/British, Male/Female)

## Limits & Constraints

### 1. Token Limit per Chunk

**Kokoro has a hard limit of 510 tokens per generation.**

- **What we do**: Text is automatically split into chunks of 175-250 tokens
- **Why**: Prevents exceeding Kokoro's limit while maintaining natural speech breaks
- **Impact**: Long texts are processed in multiple chunks and streamed progressively

### 2. Processing Speed

**CPU-based generation is slower than GPU but fully functional.**

- ~2-5x real-time speed on CPU
- Example: 1 minute of audio takes ~12-30 seconds to generate
- For very long texts (10,000+ words), expect several minutes

### 3. Memory Requirements

- **Model loading**: ~500MB RAM for model weights
- **Generation**: +200-500MB RAM during active generation
- **Browser**: localStorage for settings and history

### 4. No Hard Text Length Limit

- Text can be arbitrarily long
- It's chunked automatically
- Each chunk generates ~5-10 seconds of audio
- Chunks are streamed to the client as generated

## How Text Becomes Speech

### Step-by-Step Process

1. **User enters text** in the web interface (or uploads a document)
2. **Browser sends request** to the server at `/api/tts/stream`
3. **Server preprocesses text**:
   ```python
   # text_preprocessor.py
   - Normalize Unicode
   - Remove PDF artifacts
   - Expand abbreviations (Dr. → Doctor)
   - Convert numbers to words (123 → one hundred twenty-three)
   - Split into chunks (175-250 tokens each)
   ```

4. **Server generates speech per chunk**:
   ```python
   # tts_engine.py
   for chunk in text_chunks:
       # Kokoro generates audio + phoneme timing
       audio_array, timings = kokoro.generate(chunk, voice, speed)

       # Encode to MP3 (64kbps, 22050Hz)
       mp3_bytes = encode_mp3(audio_array)

       # Stream to client
       yield f"TIMING:{json.dumps(timing)}\n"
       yield mp3_bytes
   ```

5. **Browser receives stream**:
   - Parses TIMING metadata (text + timestamps)
   - Collects MP3 binary chunks
   - Combines into single audio blob
   - Creates blob URL for audio player
   - Saves to history (localStorage)

6. **Audio player plays** the blob URL using native HTML5 audio

## Streaming Protocol

The server streams a mix of text metadata and binary audio:

```
TIMING:{"text":"Hello world","start":0,"end":1.2}\n
<binary MP3 data>
TIMING:{"text":"This is a test","start":1.2,"end":2.5}\n
<binary MP3 data>
TIMING:{"text":"of the system","start":2.5,"end":3.8}\n
<binary MP3 data>
...
```

### Why This Design?

1. **Progressive playback**: Browser can start playing before all audio is generated
2. **Synchronized highlighting**: Timing data allows text to highlight as it's spoken
3. **Efficient**: No base64 encoding, raw binary MP3 data
4. **Interruptible**: Can cancel generation mid-stream

## Data Storage & Privacy

### Server Side (No Persistent Storage)

- **No database**: Server is stateless
- **Uploaded files**: Deleted immediately after text extraction
- **Logging**: Server logs requests and errors to an in-memory ring buffer (last 500 entries), exportable via the UI for debugging. No persistent log files on disk.

### Client Side (All Local)

- **localStorage**: Stores settings and history metadata in browser
- **Location**: Browser's private storage (not accessible to other sites)
- **Persistence**: Survives page refreshes, cleared only by user action

### No Authentication

The app has no login, no passwords, no tokens. It is designed as a **local-only tool** — anyone who can reach `localhost:8000` can use it. If you expose it on a network, access is controlled at the network level, not the application level.

## Android Support

The app runs on Android using the **same SvelteKit web app** in a WebView, backed by an embedded HTTP server that calls the on-device Sherpa-ONNX TTS engine. No external server needed — everything runs locally on the phone.

```
┌─────────────────────────────────────────┐
│          Android Phone                  │
│                                         │
│  WebView → localhost:8080               │
│  NanoHTTPD (static files + API)         │
│  Sherpa-ONNX TTS Engine                │
│  Kokoro INT8 model (~95 MB)            │
│                                         │
│  Same SvelteKit UI as desktop.          │
│  No external network. Fully offline.    │
└─────────────────────────────────────────┘
```

Run `./android/copy-webapp.sh` to bundle the web app, then open `android/` in Android Studio and run on your device. On first launch it downloads the Kokoro model (~95MB).

See [ANDROID_ARCHITECTURE.md](ANDROID_ARCHITECTURE.md) for full details.

## File Structure

```
openmobiletts/
├── run.py                   # Single-command launcher (desktop)
├── Dockerfile               # Multi-stage Docker build
├── docker-compose.yml       # Docker convenience wrapper
├── server/                  # Desktop web app backend
│   ├── src/
│   │   ├── main.py              # FastAPI app + API endpoints + static serving
│   │   ├── tts_engine.py        # Kokoro TTS wrapper
│   │   ├── text_preprocessor.py # Text normalization & chunking
│   │   ├── audio_encoder.py     # MP3 encoding (pydub + ffmpeg)
│   │   ├── document_processor.py# PDF/DOCX/TXT extraction
│   │   └── config.py            # Settings & environment
│   ├── tests/                   # Automated tests
│   ├── requirements.txt         # Python dependencies
│   └── setup_models.py          # Downloads Kokoro model
├── client/                  # Web app frontend (SvelteKit) — shared by desktop + Android
│   ├── src/
│   │   ├── routes/+page.svelte      # Main TTS interface
│   │   ├── lib/components/          # UI components
│   │   ├── lib/stores/              # State management
│   │   └── lib/services/            # API client
│   └── static/                      # PWA manifest
├── android/                 # Android app (WebView + native TTS bridge)
│   ├── copy-webapp.sh               # Bundles SvelteKit build into assets
│   ├── app/src/main/java/com/openmobiletts/app/
│   │   ├── MainActivity.kt         # WebView host + model download UI
│   │   ├── TtsHttpServer.kt        # NanoHTTPD: API endpoints + static files
│   │   ├── TtsManager.kt           # Sherpa-ONNX wrapper
│   │   ├── VoiceRegistry.kt        # Voice name → SID mapping
│   │   ├── WavEncoder.kt           # PCM → WAV conversion
│   │   ├── ModelDownloader.kt      # First-launch model download
│   │   ├── TtsService.kt           # Foreground notification for keep-alive
│   │   └── OpenMobileTtsApp.kt     # Application singleton
│   └── app/build.gradle.kts
└── docs/                            # Documentation
```

## Common Questions

### Can I use this offline?

**Mostly yes:**
- After first model download, server works offline
- No internet required for TTS generation

### Can I add more voices?

Yes! Kokoro supports custom voice training. You can:
1. Fine-tune on your own voice recordings
2. Download community-trained voices
3. Use the 11 built-in voices

### What happens if the app crashes mid-generation?

- Browser receives partial audio
- Browser shows warning if audio seems incomplete
- User can regenerate from the same text
- No data loss

### Why is generation slower for long texts?

1. **Sequential processing**: Chunks processed one at a time
2. **CPU bottleneck**: No GPU acceleration
3. **MP3 encoding**: Additional time to encode audio
4. **Typical times**:
   - Short paragraph (50 words): ~2-3 seconds
   - Long article (2,000 words): ~30-60 seconds
   - Full book chapter (10,000 words): ~3-5 minutes

### Can I run this on a server/VPS?

Yes! The app can be deployed via Docker:
- `docker compose up --build`
- Works on CPU-only servers
- Recommended: 4GB+ RAM, 2+ CPU cores

### How much disk space does it use?

- **Model**: ~320MB (one-time download)
- **Python deps**: ~400MB
- **Client build**: ~10MB
- **Cached audio**: Grows with usage (user can clear anytime)

**Total initial install**: ~500MB

## Next Steps

See also:
- [Technical Architecture](technical-architecture.md) - Detailed design decisions
- [Implementation Status](implementation-status.md) - What's built vs. planned
- [Limits & Constraints](LIMITS_AND_CONSTRAINTS.md) - Full performance details
- [Roadmap](ROADMAP.md) - Version plan and future features
