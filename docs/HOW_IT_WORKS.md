# How Open Mobile TTS Works

## Overview

Open Mobile TTS is a **fully local, private text-to-speech system** that runs entirely on your own hardware. No external APIs are called, and no data leaves your machine.

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────┐
│                        Your Machine                          │
│                                                              │
│  ┌──────────────────┐              ┌────────────────────┐  │
│  │   Web Browser    │◄────HTTP─────►   FastAPI Server   │  │
│  │   (SvelteKit)    │   localhost   │   (Python)        │  │
│  │                  │     :8000     │                    │  │
│  │  • UI/Forms      │              │  • Authentication  │  │
│  │  • Audio Player  │              │  • Text Processing │  │
│  │  • IndexedDB     │              │  • TTS Generation  │  │
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

## Is the Model Running Locally?

**Yes, 100% local.** Here's how it works:

### Model Download & Storage

1. **First run**: When you start the server for the first time, it downloads the Kokoro TTS model from HuggingFace
2. **Storage location**: `~/.cache/kokoro/` (approximately 320MB)
3. **No API calls**: After initial download, everything runs locally on your CPU/GPU

### Model Details

- **Name**: Kokoro TTS (82M parameters)
- **License**: Apache 2.0 (fully open source, can use commercially)
- **Quality**: State-of-the-art neural TTS
- **Speed**:
  - GPU: 35-210x real-time speed
  - CPU (your setup): ~2-5x real-time speed
- **Voices**: 11 built-in voices (American/British, Male/Female)

### Setup Process

When you run `python setup_models.py` or start the server:

```python
# This downloads from HuggingFace (one-time)
model = Kokoro(repo_id='hexgrad/Kokoro-82M', lang='en-us')

# Model is cached at: ~/.cache/kokoro/
# Contains:
# - kokoro-v0_19.pth (~318MB) - Neural network weights
# - voices/ - Voice embeddings
# - config.json - Model configuration
```

## Limits & Constraints

### 1. Token Limit per Chunk

**Kokoro has a hard limit of 510 tokens per generation.**

- **What we do**: Text is automatically split into chunks of 175-250 tokens
- **Why**: Prevents exceeding Kokoro's limit while maintaining natural speech breaks
- **Impact**: Long texts are processed in multiple chunks and streamed progressively

### 2. Processing Speed

**CPU-based generation is slower than GPU but fully functional.**

Current setup (your Mac):
- ~2-5x real-time speed
- Example: 1 minute of audio takes ~12-30 seconds to generate
- For very long texts (10,000+ words), expect several minutes

### 3. Memory Requirements

- **Model loading**: ~500MB RAM for model weights
- **Generation**: +200-500MB RAM during active generation
- **Client**: IndexedDB can store GBs of cached audio locally

### 4. No Hard Text Length Limit

- Text can be arbitrarily long
- It's chunked automatically
- Each chunk generates ~5-10 seconds of audio
- Chunks are streamed to the client as generated

## How Text Becomes Speech

### Step-by-Step Process

1. **User enters text** in the web interface
2. **Client sends request** to server via HTTP
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

5. **Client receives stream**:
   ```javascript
   // TextInput.svelte
   - Parse TIMING: metadata (text + timestamps)
   - Collect MP3 binary chunks
   - Combine into single audio blob
   - Create blob URL for audio player
   - Save to IndexedDB for history
   ```

6. **Audio player plays** the blob URL using native HTML5 audio

## Streaming Protocol

The server and client communicate using a custom streaming protocol:

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

1. **Progressive playback**: Client can start playing before all audio is generated
2. **Synchronized highlighting**: Timing data allows text to highlight as it's spoken
3. **Efficient**: No base64 encoding, raw binary MP3 data
4. **Interruptible**: Can cancel generation mid-stream

## Data Storage & Privacy

### Server Side (No Persistent Storage)

- **No database**: Server is stateless
- **Uploaded files**: Deleted immediately after text extraction
- **No logging**: Text content is never written to disk

### Client Side (All Local)

- **IndexedDB**: Stores generated audio + metadata in browser
- **Location**: Browser's private storage (not accessible to other sites)
- **Size**: Unlimited (subject to browser quota, typically 50%+ of available disk)
- **Persistence**: Survives page refreshes, cleared only by user action

### Authentication

- **JWT tokens**: Stored in localStorage
- **Password hashing**: Argon2id (memory-hard, secure)
- **No sessions**: Server doesn't track logged-in users

## File Structure

### Server (`/server`)

```
server/
├── src/
│   ├── main.py              # FastAPI app, API endpoints
│   ├── tts_engine.py        # Kokoro TTS wrapper
│   ├── text_preprocessor.py # Text normalization & chunking
│   ├── audio_encoder.py     # MP3 encoding (pydub + ffmpeg)
│   ├── document_processor.py# PDF/DOCX/TXT extraction
│   ├── auth.py              # JWT + password verification
│   └── config.py            # Settings & environment
├── tests/                   # 24 automated tests
├── requirements.txt         # Python dependencies
└── setup_models.py          # Downloads Kokoro model
```

### Client (`/client`)

```
client/
├── src/
│   ├── routes/
│   │   ├── +page.svelte           # Root (redirects to login)
│   │   ├── login/+page.svelte     # Login form
│   │   └── player/+page.svelte    # Main TTS interface
│   ├── lib/
│   │   ├── components/
│   │   │   ├── AudioPlayer.svelte   # Playback controls
│   │   │   ├── TextInput.svelte     # Text entry + generation
│   │   │   ├── TextDisplay.svelte   # Synchronized highlighting
│   │   │   └── AudioHistory.svelte  # Saved audio list
│   │   ├── stores/
│   │   │   ├── auth.js              # Auth state
│   │   │   ├── player.js            # Playback state
│   │   │   └── history.js           # History refresh events
│   │   └── services/
│   │       ├── api.js               # HTTP client
│   │       └── audioCache.js        # IndexedDB wrapper
│   └── app.css                      # Tailwind styles
└── static/
    ├── manifest.json                # PWA manifest
    └── sw.js                        # Service worker (disabled for now)
```

## Common Questions

### Can I use this offline?

**Mostly yes:**
- After first model download, server works offline
- PWA can work offline (service worker currently disabled but can be enabled)
- No internet required for TTS generation

### Can I add more voices?

Yes! Kokoro supports custom voice training. You can:
1. Fine-tune on your own voice recordings
2. Download community-trained voices
3. Use the 11 built-in voices

See Kokoro documentation for voice training.

### What happens if the server crashes mid-generation?

- Client receives partial audio
- Client shows warning if audio seems incomplete
- User can regenerate from the same text
- No data loss (worst case: need to retry)

### Why is generation slower for long texts?

1. **Sequential processing**: Chunks processed one at a time
2. **CPU bottleneck**: No GPU acceleration on your machine
3. **MP3 encoding**: Additional time to encode audio
4. **Typical times**:
   - Short paragraph (50 words): ~2-3 seconds
   - Long article (2,000 words): ~30-60 seconds
   - Full book chapter (10,000 words): ~3-5 minutes

### Can I run this on a server/VPS?

Yes! The server is designed to be deployed:
- Docker support included
- Can run on CPU-only servers
- Multi-user capable (JWT auth)
- Recommended: 4GB+ RAM, 2+ CPU cores

### How much disk space does it use?

- **Model**: ~320MB (one-time download)
- **Server code**: ~50MB (Python dependencies)
- **Client code**: ~10MB (built assets)
- **Cached audio**: Grows with usage (user can clear anytime)

**Total initial install**: ~500MB

## Performance Optimization Tips

### Server Side

1. **Use GPU if available**: 10-50x faster than CPU
2. **Increase chunk size**: Faster but less granular timing
3. **Pre-generate common phrases**: Cache frequently used audio

### Client Side

1. **Clear old history**: Saves IndexedDB space
2. **Disable native audio controls**: Slightly less overhead
3. **Use Chrome**: Best streaming performance

## Troubleshooting

### Model download fails

```bash
# Manually download model
cd server
python setup_models.py
```

### Audio is choppy or has gaps

- Likely a streaming parser issue
- Check browser console for errors
- Try a shorter text first to verify setup

### Generation is very slow

- Normal for CPU (2-5x real-time)
- For faster: use GPU-enabled machine
- Consider shorter texts or batch processing

### "No audio data received"

- Server might have crashed
- Check server logs: `tail -f /tmp/server.log`
- Restart server: `cd server && python -m uvicorn src.main:app --reload`

## Next Steps

See also:
- [Technical Architecture](technical-architecture.md) - Detailed design decisions
- [Implementation Status](implementation-status.md) - What's built vs. planned
- [Testing Summary](testing-summary.md) - Test coverage & results
