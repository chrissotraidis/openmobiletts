# Open Mobile TTS

A private, local text-to-speech app. Clone it, run it, done — no cloud, no API keys, no accounts.

## Quick Start

```bash
git clone <repo-url>
cd openmobiletts
python run.py
# Opens at http://localhost:8000
```

That's it. `run.py` checks your system, installs dependencies, builds the UI, and starts the server. On first launch, the Kokoro TTS model (~320 MB) downloads automatically and is cached for future runs.

### System Requirements

| Requirement | Version | Install |
|---|---|---|
| Python | 3.9 – 3.12 | [python.org](https://www.python.org/downloads/) |
| Node.js | 18+ | [nodejs.org](https://nodejs.org/) |
| espeak-ng | any | `sudo apt install espeak-ng` / `brew install espeak-ng` |
| ffmpeg | any | `sudo apt install ffmpeg` / `brew install ffmpeg` |

`run.py` will tell you exactly what's missing and how to install it.

### Docker

```bash
docker compose up --build
# Opens at http://localhost:8000
```

## Features

- **Kokoro TTS** — 82M parameter model, #1 on HuggingFace TTS Arena, Apache 2.0 licensed
- **Real-time streaming** — Audio plays as it generates, sentence-by-sentence
- **Text highlighting** — Synchronized sentence highlighting during playback
- **Document support** — Upload PDF, DOCX, or TXT files
- **11 voices** — American and British English, male and female
- **100% local** — No cloud, no API keys, no auth, no data leaves your machine

## Architecture

Single-process app: FastAPI serves both the TTS API and the built SvelteKit UI.

```
python run.py
│
├── /api/voices          → List available voices
├── /api/tts/stream      → Stream TTS audio + timing metadata
├── /api/documents/*     → Upload and process documents
└── /*                   → SvelteKit UI (static files)
```

### Server (`/server`)
- FastAPI + Kokoro TTS engine
- Streaming MP3 with timing metadata (TIMING protocol)
- Text preprocessing: abbreviation expansion, number-to-words, sentence chunking
- Document processing: PDF (pymupdf4llm), DOCX (python-docx), TXT

### Client (`/client`)
- SvelteKit + Svelte 5 + Tailwind CSS
- Dark theme, mobile-first responsive design
- Streaming audio player with sentence highlighting
- History (localStorage), settings persistence

## Development

For hot-reload during development, run the server and client separately:

```bash
# Terminal 1: API server with auto-reload
cd server && pip install -r requirements.txt
uvicorn src.main:app --reload --port 8000

# Terminal 2: Vite dev server (proxies /api/* to port 8000)
cd client && npm install && npm run dev
```

## Configuration

Copy `server/.env.example` to `server/.env` to customize settings. All settings have sensible defaults — no `.env` file is required for basic usage.

## Performance

| Hardware | Speed | First Audio |
|---|---|---|
| RTX 4090 | ~210x real-time | ~80ms |
| RTX 3090 Ti | ~90x real-time | ~150ms |
| 8-core CPU | 3-11x real-time | ~1-3s |

## Documentation

- **[Migration](docs/MIGRATION.md)** — Why and how we moved from client-server to single app
- **[Implementation Plan](docs/IMPLEMENTATION_PLAN.md)** — Future native Android app plan (Sherpa-ONNX + Kokoro INT8)
- **[How It Works](docs/HOW_IT_WORKS.md)** — System architecture and data flow
- **[Limits & Constraints](docs/LIMITS_AND_CONSTRAINTS.md)** — Performance limits and token boundaries

## License

Apache 2.0. Kokoro TTS is also Apache 2.0 (commercial use permitted).
