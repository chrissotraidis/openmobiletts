# Open Mobile TTS - Claude Instructions

## Project Overview

**Open Mobile TTS** is a private, local text-to-speech application that runs entirely on the user's machine. No cloud, no API keys, no accounts. One command (`python run.py`) starts everything.

It is a **single-process monolithic app**: FastAPI serves both the TTS API and the built SvelteKit UI on one port (8000). There is no authentication, no CORS, and no separate client/server processes.

## Core Functionality

- Convert text to streaming MP3 audio with sentence-level timing metadata
- Process uploaded documents (PDF, DOCX, TXT) and stream TTS directly
- Sentence-level text highlighting synchronized with audio playback
- 11 built-in Kokoro TTS voices (American/British, Male/Female)
- Mobile-first PWA with offline app shell

## Architecture

```
python run.py
├── Checks deps (Python, Node, espeak-ng, ffmpeg)
├── Installs pip/npm deps if needed
├── Builds SvelteKit → client/build/
└── Starts uvicorn on port 8000
    ├── /api/*      → FastAPI TTS endpoints
    └── /*          → SvelteKit static files (SPA)
```

**Single process. Single port. No auth. No CORS.**

## Repository Structure

```
openmobiletts/
├── run.py                         # Single-command launcher
├── Dockerfile                     # Multi-stage Docker build
├── docker-compose.yml             # Docker convenience wrapper
├── server/
│   ├── src/
│   │   ├── main.py               # FastAPI app + static serving (no auth)
│   │   ├── tts_engine.py         # Kokoro TTS wrapper with streaming
│   │   ├── text_preprocessor.py  # Text cleaning, chunking, abbreviation expansion
│   │   ├── document_processor.py # PDF/DOCX/TXT extraction
│   │   ├── audio_encoder.py      # MP3 encoding with pydub
│   │   └── config.py             # Environment configuration
│   ├── tests/                    # Pytest tests (API, preprocessing, doc processing)
│   ├── requirements.txt          # Python dependencies
│   ├── setup_models.py           # Pre-download Kokoro model
│   └── .env.example              # Configuration template
├── client/
│   ├── src/
│   │   ├── routes/+page.svelte   # Main TTS interface (single page, no routing)
│   │   ├── lib/components/       # AudioPlayer, AudioHistory, TextInput, TextDisplay
│   │   ├── lib/stores/           # player.js, settings.js, history.js
│   │   └── lib/services/api.js   # Fetch wrapper (no auth headers)
│   └── static/                   # PWA manifest, service worker
├── docs/                         # All documentation (flat structure)
└── .github/workflows/            # CI: server tests on push
```

## Development Guidelines

### Backend (FastAPI + Kokoro TTS)
1. **Streaming is critical**: All TTS endpoints use `StreamingResponse` with chunked encoding
2. **Text preprocessing**: Always segment at sentence boundaries, target 175-250 tokens per chunk
3. **No auth**: Endpoints are open — no JWT, no tokens, no middleware
4. **MP3 encoding**: 64kbps CBR, 22050Hz mono for optimal speech quality/size
5. **Timing protocol**: Send `TIMING:{json}\n` then `AUDIO:{length}\n` then MP3 bytes per chunk
6. **Document extraction**: pymupdf4llm for PDFs, python-docx for DOCX

### Frontend (SvelteKit + Tailwind)
1. **Mobile-first**: Large touch targets (44×44px minimum), responsive design
2. **Single page**: No client-side routing needed — everything on `+page.svelte`
3. **Binary-safe streaming**: Parse MP3 as `Uint8Array`, not text
4. **Audio handling**: Native HTML5 `<audio>` with blob URLs
5. **Stores**: `player.js` (playback state), `settings.js` (voice/speed), `history.js` (events)

### Code Quality
- **No over-engineering**: Implement exactly what's needed
- **Error handling**: At system boundaries only (user input, file I/O)
- **Type hints**: Python type hints in backend code
- **No auth code**: Do not add authentication — this is a local-only app

## Key Technical Constraints

| Constraint | Value | Notes |
|-----------|-------|-------|
| Kokoro token limit | 510 per inference | Chunk at 175-250 tokens |
| Python version | 3.9-3.12 | Not 3.13+ |
| Sample rate | 24kHz from Kokoro | Downsampled to 22050Hz for MP3 |
| CPU speed | 2-5x real-time | GPU: 35-210x |
| First chunk latency | 1-3s (CPU) | <500ms on GPU |
| MP3 file size | ~480KB/min at 64kbps | |
| License | Apache 2.0 | Commercial use OK |

## Key Dependencies

### Backend
- `fastapi` + `uvicorn` — API framework + ASGI server
- `kokoro` — TTS engine (82M params)
- `pymupdf4llm` — PDF extraction
- `python-docx` — DOCX extraction
- `pydub` — MP3 encoding (requires ffmpeg)
- `num2words` — Number-to-word conversion
- `python-dotenv` — Environment variable loading

### System
- `espeak-ng` — Phoneme backend for Kokoro
- `ffmpeg` — Audio encoding for pydub

### Frontend
- `svelte` / `sveltekit` — UI framework
- `tailwindcss` — Styling

## Environment Variables

All optional. Defaults are sensible for local use:

| Variable | Default | Purpose |
|----------|---------|---------|
| `DEFAULT_VOICE` | `af_heart` | Default TTS voice |
| `DEFAULT_SPEED` | `1.0` | Speech speed |
| `MAX_CHUNK_TOKENS` | `250` | Tokens per TTS chunk |
| `MP3_BITRATE` | `64k` | Audio bitrate |
| `PORT` | `8000` | Server port |
| `MAX_UPLOAD_SIZE_MB` | `10` | Upload size limit |
| `STATIC_DIR` | auto-detected | Path to built client |

## When Working on This Project

### Adding Features
1. Check `docs/technical-architecture.md` for existing patterns
2. Keep the app as a single process — don't add separate services
3. Test on mobile (iOS and Android have different PWA capabilities)
4. Update docs if behavior changes (see `docs/DOCUMENT_PURPOSES.md` for which doc to update)

### Fixing Bugs
1. Understand the data flow: text → preprocess → chunk → Kokoro → MP3 → stream → browser → play
2. Check timing metadata sync if audio/text highlighting is broken
3. Verify MP3 encoding if audio quality issues occur
4. Review text preprocessing if TTS pronunciation is wrong

### Refactoring
1. Keep modules independent (TTS, document processing, encoding)
2. Don't add abstractions for one-time operations
3. Remove unused code completely (no commented-out code)
4. Maintain backward compatibility for the streaming protocol

---

**Last Updated**: 2026-02-17
**Architecture**: Single-process monolithic app (v0.2)
