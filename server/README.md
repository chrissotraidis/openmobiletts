# Open Mobile TTS - Backend Server

FastAPI-based streaming TTS server powered by Kokoro.

## Overview

This is the backend server for Open Mobile TTS. It provides:
- RESTful API endpoints for TTS generation
- Real-time audio streaming with chunked encoding
- Document processing (PDF, DOCX)
- JWT authentication for single-user access
- Optimized MP3 encoding for speech

## Installation

### Prerequisites
- Python 3.9-3.12 (not 3.13+)
- GPU with 2-3GB VRAM recommended (CPU fallback available)
- Linux: `apt-get install espeak-ng`
- macOS: `brew install espeak`

### Setup

1. **Create virtual environment:**
   ```bash
   python -m venv venv
   source venv/bin/activate  # Windows: venv\Scripts\activate
   ```

2. **Install dependencies:**
   ```bash
   pip install -r requirements.txt
   ```

3. **Download TTS models:**
   ```bash
   python setup_models.py
   ```
   This downloads Kokoro TTS models (~100MB) to `~/.cache/kokoro/`

   **Note**: Models are downloaded automatically on first use, but running this script ensures they're cached and the setup is working correctly.

4. **Configure environment:**
   ```bash
   # Generate JWT secret
   openssl rand -hex 32

   # Create .env file
   cat > .env << EOF
   JWT_SECRET=your_generated_secret
   ADMIN_USERNAME=admin
   ADMIN_PASSWORD_HASH=your_argon2_hash
   EOF
   ```

5. **Generate password hash:**
   ```bash
   python generate_password_hash.py your_password
   ```
   Copy the generated hash to your `.env` file as `ADMIN_PASSWORD_HASH`

## Running

### Development
```bash
uvicorn src.main:app --reload --host 0.0.0.0 --port 8000
```

### Production
```bash
uvicorn src.main:app --host 0.0.0.0 --port 8000 --workers 2
```

### Docker
```bash
docker build -t openmobiletts-server .
docker run -d -p 8000:8000 \
  -e JWT_SECRET=your_secret \
  -e ADMIN_USERNAME=admin \
  -e ADMIN_PASSWORD_HASH=your_hash \
  --gpus all \
  openmobiletts-server
```

## API Endpoints

### Authentication
- `POST /token` - Login and get JWT token
  - Body: `username`, `password` (form data)
  - Returns: `{"access_token": "...", "token_type": "bearer"}`

### TTS
- `GET /api/tts/stream` - Stream TTS audio
  - Query params: `text`, `voice` (optional)
  - Headers: `Authorization: Bearer <token>`
  - Returns: Streaming MP3 with timing metadata

### Documents
- `POST /api/documents/upload` - Upload PDF/DOCX for TTS
  - Body: File upload (multipart/form-data)
  - Headers: `Authorization: Bearer <token>`
  - Returns: Extracted text and document ID

### Voices
- `GET /api/voices` - List available voices
  - Returns: Array of voice objects with language/name

## Project Structure

```
server/
├── src/
│   ├── main.py               # FastAPI app entry, route definitions
│   ├── auth.py               # JWT authentication logic
│   ├── tts_engine.py         # Kokoro TTS wrapper with streaming
│   ├── document_processor.py # PDF/DOCX text extraction
│   ├── audio_encoder.py      # MP3 encoding with pydub
│   ├── text_preprocessor.py  # Text cleaning and chunking
│   └── config.py             # Environment configuration
├── tests/                    # Unit and integration tests
├── requirements.txt          # Python dependencies
├── Dockerfile               # Container image
└── README.md                # This file
```

## Configuration

Environment variables:

| Variable | Description | Required |
|----------|-------------|----------|
| `JWT_SECRET` | Secret key for JWT signing | Yes |
| `ADMIN_USERNAME` | Admin username | Yes |
| `ADMIN_PASSWORD_HASH` | Argon2 hash of admin password | Yes |
| `KOKORO_MODEL_PATH` | Custom model path (optional) | No |
| `MAX_CHUNK_SIZE` | Max tokens per TTS chunk (default: 250) | No |
| `MP3_BITRATE` | MP3 bitrate in kbps (default: 64) | No |

## Development

### Running Tests
```bash
pytest tests/
```

### Code Style
```bash
black src/
isort src/
flake8 src/
```

## Performance Tuning

- **Chunk size**: Adjust `MAX_CHUNK_SIZE` (175-250 tokens optimal)
- **MP3 bitrate**: 64kbps recommended for speech, use 96kbps for higher quality
- **GPU selection**: Set `CUDA_VISIBLE_DEVICES=0` for specific GPU
- **Workers**: Use 1 worker per GPU for optimal performance

## Troubleshooting

**"No module named 'espeak'"**
- Install espeak-ng system package

**"CUDA out of memory"**
- Reduce `MAX_CHUNK_SIZE`
- Ensure no other GPU processes running
- Use CPU fallback (slower but works)

**"Token has expired"**
- JWT tokens expire after 24 hours by default
- Re-login to get new token

**Slow TTS generation on GPU**
- Check GPU utilization with `nvidia-smi`
- Ensure CUDA is properly installed
- Verify Kokoro is using GPU (check logs)

## References

- See `docs/technical-architecture.md` for detailed implementation patterns
- Kokoro TTS: https://pypi.org/project/kokoro/
- FastAPI documentation: https://fastapi.tiangolo.com/
