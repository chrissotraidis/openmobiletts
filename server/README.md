# Open Mobile TTS - Server

FastAPI-based streaming TTS server powered by Kokoro.

> **Most users should just run `python run.py` from the repo root.** This README is for server-only development.

## Standalone Development

```bash
pip install -r requirements.txt
uvicorn src.main:app --reload --host 0.0.0.0 --port 8000
```

### Prerequisites
- Python 3.9-3.12
- espeak-ng (`apt install espeak-ng` / `brew install espeak-ng`)
- ffmpeg (`apt install ffmpeg` / `brew install ffmpeg`)

### Optional: Pre-download models

```bash
python setup_models.py
```

Models are downloaded automatically on first request, but this script lets you cache them ahead of time (~320 MB to `~/.cache/kokoro/`).

## API Endpoints

### TTS
- `GET /api/tts/stream` — Stream TTS audio
  - Query params: `text`, `voice` (optional), `speed` (optional)
  - Returns: Streaming MP3 with timing metadata

### Documents
- `POST /api/documents/upload` — Upload PDF/DOCX/TXT, get extracted text
- `POST /api/documents/stream` — Upload and stream TTS directly

### Voices
- `GET /api/voices` — List available voices

### Health
- `GET /api/health` — Health check

## Project Structure

```
server/
├── src/
│   ├── main.py               # FastAPI app, route definitions
│   ├── tts_engine.py         # Kokoro TTS wrapper with streaming
│   ├── document_processor.py # PDF/DOCX/TXT text extraction
│   ├── audio_encoder.py      # MP3 encoding with pydub
│   ├── text_preprocessor.py  # Text cleaning and chunking
│   └── config.py             # Environment configuration
├── requirements.txt
├── setup_models.py           # Pre-download Kokoro models
└── .env.example              # Configuration reference
```

## Configuration

All settings are optional and have sensible defaults. See `.env.example` for the full list.

| Variable | Default | Description |
|---|---|---|
| `DEFAULT_VOICE` | `af_heart` | Default TTS voice |
| `DEFAULT_SPEED` | `1.0` | Default speech speed |
| `MAX_CHUNK_TOKENS` | `250` | Max tokens per TTS chunk |
| `MP3_BITRATE` | `64k` | MP3 encoding bitrate |
| `PORT` | `8000` | Server port |

## Troubleshooting

**"No module named 'espeak'"** — Install espeak-ng: `sudo apt install espeak-ng`

**Slow generation on CPU** — Expected. GPU gives 90-210x real-time; CPU gives 3-11x.

**"CUDA out of memory"** — Reduce `MAX_CHUNK_TOKENS` or close other GPU processes.
