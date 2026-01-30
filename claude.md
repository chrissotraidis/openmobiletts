# Open Mobile TTS - Claude Instructions

## Project Overview

**Open Mobile TTS** is a private, self-hosted text-to-speech platform designed for light server load and mobile usage. The system consists of two main components:

1. **Backend Server** (`/server`) - FastAPI application with Kokoro TTS engine
2. **PWA Client** (`/client`) - Progressive Web App (future Android app)

## Core Functionality

- Convert text to MP3 audio files or streams
- Stream audio in real-time with sentence-level text synchronization
- Process uploaded documents (PDF, DOCX) and extract text for TTS
- Single-user JWT authentication for private deployment
- Mobile-first design with offline capabilities
- Light server load: 64kbps MP3, efficient 82M-parameter Kokoro model

## Architecture Principles

### Backend Server (FastAPI + Kokoro TTS)
- **Streaming-first**: Use Kokoro's generator API for progressive audio delivery
- **Lightweight**: Target 64kbps CBR MP3 at 22050Hz mono for speech
- **Modular**: Separate concerns (auth, TTS, document processing, encoding)
- **Single-user security**: JWT with Argon2 password hashing
- **Document processing**: pymupdf4llm for PDFs, python-docx for DOCX
- **Text preprocessing**: Expand abbreviations, convert numbers to words, segment at 175-250 tokens

### PWA Client
- **Mobile-first**: Large touch targets (44×44px minimum), responsive design
- **Progressive enhancement**: Service worker for offline shell, MediaSession for lock screen controls
- **Synchronized playback**: Sentence-level text highlighting using timing metadata
- **Audio handling**: Handle iOS limitations (no background audio), optimize for Android

### Key Technical Constraints
- **Kokoro token limit**: 510 tokens per inference (~250 words optimal per chunk)
- **GPU VRAM**: ~2-3GB required
- **Python version**: 3.9-3.12 (not 3.13+)
- **Sample rate**: 24kHz native output from Kokoro
- **License**: Apache 2.0 (fully commercial use permitted)

## Repository Structure

```
openmobiletts/
├── docs/                          # All documentation
│   └── technical-architecture.md  # Detailed architecture plan (see for patterns)
├── server/                        # Backend FastAPI application
│   ├── src/
│   │   ├── main.py               # FastAPI app entry point
│   │   ├── auth.py               # JWT authentication with Argon2
│   │   ├── tts_engine.py         # Kokoro wrapper with streaming support
│   │   ├── document_processor.py # PDF/DOCX extraction
│   │   ├── audio_encoder.py      # MP3 encoding with pydub
│   │   ├── text_preprocessor.py  # TTS text cleaning and chunking
│   │   └── config.py             # Environment configuration
│   ├── tests/                    # Backend tests
│   ├── requirements.txt          # Python dependencies
│   ├── Dockerfile               # Container image for deployment
│   └── README.md                # Server-specific documentation
├── client/                        # PWA web application
│   ├── public/
│   │   ├── manifest.json         # PWA manifest
│   │   └── sw.js                # Service worker
│   ├── src/
│   │   ├── components/          # UI components
│   │   ├── services/            # API client, audio player, sync controller
│   │   └── styles/              # CSS (Tailwind recommended)
│   ├── package.json
│   └── README.md                # Client-specific documentation
├── .gitignore
├── claude.md                      # This file - Claude's working instructions
└── README.md                      # Project overview for users
```

## Development Guidelines

### When Working on Backend:
1. **Read `docs/technical-architecture.md`** for specific implementation patterns
2. **Streaming is critical**: All TTS endpoints must use `StreamingResponse` with chunked encoding
3. **Security first**: Never store passwords in plaintext, use environment variables for secrets
4. **Document extraction**: pymupdf4llm handles multi-column PDFs best
5. **Text preprocessing**: Always segment at sentence boundaries, target 175-250 tokens per chunk
6. **MP3 encoding**: Use 64kbps CBR, 22050Hz mono for optimal speech quality/size ratio
7. **Timing metadata**: Send JSON timing data before each audio chunk for client synchronization

### When Working on Client:
1. **Mobile-first always**: Design for touch, then enhance for desktop
2. **Progressive enhancement**: App must work offline for static shell
3. **Audio handling**: Implement fallbacks for iOS background audio limitations
4. **Synchronization**: Parse timing metadata from server, use `timeupdate` event for highlighting
5. **MediaSession API**: Implement lock screen controls for Android
6. **Network strategy**: Cache static assets, use network-first for dynamic audio

### Code Quality Standards:
- **No over-engineering**: Implement exactly what's needed, no speculative features
- **Clear separation**: Auth, TTS, document processing, encoding should be independent modules
- **Error handling**: At system boundaries only (user input, external APIs, file I/O)
- **Comments**: Only where logic isn't self-evident
- **Type hints**: Use Python type hints in backend code
- **Testing**: Focus on critical paths (TTS streaming, document extraction, auth)

### Performance Targets:
- **CPU-only deployment**: Target is VPS with modest CPU (no GPU)
- **First audio chunk**: ~1-3.5s on CPU (vs <500ms on GPU)
- **Total generation**: 2-6 minutes for 5,000 words on CPU (vs 6-35 seconds on GPU)
- **TTS speed**: 3-11x real-time on CPU (vs 35-210x on GPU)
- **MP3 file size**: ~480KB per minute at 64kbps
- **Concurrent users**: Optimize for single user, but design for 2-3 concurrent streams

## Reference Information

### Key Dependencies (Backend):
- `fastapi>=0.109.0` - API framework
- `uvicorn[standard]` - ASGI server
- `kokoro>=0.9.4` - TTS engine
- `pymupdf4llm` - PDF extraction
- `python-docx` - DOCX extraction
- `pydub` - MP3 encoding
- `python-jose[cryptography]` - JWT
- `pwdlib[argon2]` - Password hashing
- `num2words` - Number to word conversion

### System Requirements (Server):
- Python 3.9-3.12
- `espeak-ng` system package (Linux: `apt-get install espeak-ng`)
- GPU with 2-3GB VRAM (or CPU with degraded performance)

### Environment Variables (Server):
- `JWT_SECRET` - Generate with `openssl rand -hex 32`
- `ADMIN_USERNAME` - Single admin username
- `ADMIN_PASSWORD_HASH` - Argon2 hash of admin password

## Working on This Project

### When Adding Features:
1. Check `docs/technical-architecture.md` for existing patterns
2. Maintain separation between server and client
3. Keep server load minimal (streaming, chunked processing)
4. Test on mobile (iOS and Android have different PWA capabilities)
5. Document any deviations from the architecture plan

### When Fixing Bugs:
1. Understand the full data flow (client → server → TTS → encoding → streaming → playback)
2. Check timing metadata synchronization if audio/text sync is broken
3. Verify MP3 encoding settings if audio quality issues occur
4. Review text preprocessing if TTS pronunciation is wrong

### When Refactoring:
1. Keep modules independent (auth, TTS, document processing)
2. Don't add abstractions for one-time operations
3. Remove unused code completely (no commented-out code)
4. Maintain backward compatibility for streaming protocol

## Important Notes

- **CPU-only Deployment**: Target deployment is VPS with modest CPU (no GPU). Expected performance: 3-11x real-time with first chunk latency of 1-3.5 seconds. Streaming is still critical to provide progressive feedback to users.
- **Client Framework**: Using **Svelte/SvelteKit** for optimal mobile performance - smallest bundle size (~3KB gzipped), compiles to vanilla JS, no virtual DOM overhead, excellent mobile performance.
- **iOS Limitations**: PWA audio stops when minimized or screen locks - design UX accordingly
- **Android Support**: Full PWA capabilities including background audio and MediaSession
- **Streaming Protocol**: Two-phase (timing metadata line + audio chunk) - don't break this
- **Token Limit**: Kokoro has 510 token limit - always chunk text appropriately
- **License**: Apache 2.0 for Kokoro - safe for commercial use

## References

- **Technical Architecture**: `docs/technical-architecture.md` (detailed implementation patterns)
- **Kokoro TTS**: PyPI package `kokoro` (https://pypi.org/project/kokoro/)
- **FastAPI Streaming**: https://fastapi.tiangolo.com/advanced/custom-response/#streamingresponse
- **PWA Best Practices**: https://web.dev/progressive-web-apps/
- **MediaSession API**: https://developer.mozilla.org/en-US/docs/Web/API/MediaSession

---

**Last Updated**: 2026-01-30
**Project Status**: Initialization phase - repository structure created, ready for implementation
