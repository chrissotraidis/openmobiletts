"""Open Mobile TTS - Single-app server (no authentication)."""

import json
import os
from pathlib import Path
from typing import List

from fastapi import FastAPI, File, HTTPException, UploadFile, status
from fastapi.responses import StreamingResponse, FileResponse
from pydantic import BaseModel

from .config import settings
from .document_processor import DocumentProcessor
from .text_preprocessor import TextPreprocessor
from .tts_engine import TTSEngine

# Create FastAPI app
app = FastAPI(
    title="Open Mobile TTS",
    description="Private text-to-speech app — single process, no auth",
    version="0.2.0",
)

# Initialize services
tts_engine = TTSEngine()
text_preprocessor = TextPreprocessor()
document_processor = DocumentProcessor()

# Ensure upload directory exists
Path(settings.UPLOAD_DIR).mkdir(parents=True, exist_ok=True)


# Request/Response models
class VoiceInfo(BaseModel):
    name: str
    language: str


class DocumentUploadResponse(BaseModel):
    filename: str
    text: str
    chunk_count: int


# Voice endpoints
@app.get("/api/voices", response_model=List[VoiceInfo])
async def list_voices():
    """List available TTS voices."""
    return tts_engine.available_voices


# TTS endpoints
@app.get("/api/tts/stream")
async def stream_tts(
    text: str,
    voice: str = settings.DEFAULT_VOICE,
    speed: float = settings.DEFAULT_SPEED,
):
    """
    Stream TTS audio with timing metadata.

    Protocol:
        1. For each audio chunk, send timing metadata as JSON line
        2. Immediately follow with MP3 audio bytes
        3. Repeat until all text is processed
    """
    if not text.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Text cannot be empty",
        )

    # Preprocess and chunk text
    text_chunks = text_preprocessor.process(text)

    async def generate_stream():
        """Generator that yields timing metadata and audio chunks."""
        async for mp3_bytes, timing in tts_engine.generate_speech_stream(
            text_chunks, voice=voice, speed=speed
        ):
            # Yield timing metadata as JSON line
            timing_line = f"TIMING:{json.dumps(timing)}\n"
            yield timing_line.encode('utf-8')

            # Yield MP3 audio bytes
            yield mp3_bytes

    return StreamingResponse(
        generate_stream(),
        media_type="audio/mpeg",
        headers={
            "Cache-Control": "no-cache",
            "X-Content-Type-Options": "nosniff",
            "X-Accel-Buffering": "no",
        },
    )


# Document endpoints
@app.post("/api/documents/upload", response_model=DocumentUploadResponse)
async def upload_document(file: UploadFile = File(...)):
    """Upload a document (PDF, DOCX, TXT) and extract text for TTS."""
    max_size_bytes = settings.MAX_UPLOAD_SIZE_MB * 1024 * 1024
    file_size = 0

    file_path = Path(settings.UPLOAD_DIR) / file.filename

    try:
        with open(file_path, 'wb') as f:
            while chunk := await file.read(8192):
                file_size += len(chunk)
                if file_size > max_size_bytes:
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail=f"File too large. Maximum size: {settings.MAX_UPLOAD_SIZE_MB}MB",
                    )
                f.write(chunk)

        text = document_processor.extract(str(file_path))
        chunks = text_preprocessor.process(text)

        return {
            "filename": file.filename,
            "text": text,
            "chunk_count": len(chunks),
        }

    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e),
        )

    finally:
        if file_path.exists():
            os.unlink(file_path)


@app.post("/api/documents/stream")
async def stream_document(
    file: UploadFile = File(...),
    voice: str = settings.DEFAULT_VOICE,
    speed: float = settings.DEFAULT_SPEED,
):
    """Upload a document and stream TTS audio directly."""
    max_size_bytes = settings.MAX_UPLOAD_SIZE_MB * 1024 * 1024
    file_size = 0

    file_path = Path(settings.UPLOAD_DIR) / file.filename

    try:
        with open(file_path, 'wb') as f:
            while chunk := await file.read(8192):
                file_size += len(chunk)
                if file_size > max_size_bytes:
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail=f"File too large. Maximum size: {settings.MAX_UPLOAD_SIZE_MB}MB",
                    )
                f.write(chunk)

        text = document_processor.extract(str(file_path))
        text_chunks = text_preprocessor.process(text)

        async def generate_stream():
            async for mp3_bytes, timing in tts_engine.generate_speech_stream(
                text_chunks, voice=voice, speed=speed
            ):
                timing_line = f"TIMING:{json.dumps(timing)}\n"
                yield timing_line.encode('utf-8')
                yield mp3_bytes

        return StreamingResponse(
            generate_stream(),
            media_type="audio/mpeg",
            headers={
                "Cache-Control": "no-cache",
                "X-Content-Type-Options": "nosniff",
                "X-Accel-Buffering": "no",
            },
        )

    except ValueError as e:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=str(e),
        )

    finally:
        if file_path.exists():
            os.unlink(file_path)


# Health check
@app.get("/api/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "version": "0.2.0"}


# Serve built SvelteKit static files
# This must be mounted LAST so API routes take priority
_static_dir = Path(settings.STATIC_DIR) if settings.STATIC_DIR else Path(__file__).parent.parent.parent / "client" / "build"

if _static_dir.exists():
    @app.get("/{path:path}")
    async def serve_spa(path: str):
        """Serve the SvelteKit SPA — files if they exist, index.html otherwise."""
        file_path = _static_dir / path
        if file_path.is_file():
            return FileResponse(file_path)
        # SPA fallback
        return FileResponse(_static_dir / "index.html")
else:
    @app.get("/")
    async def root():
        return {
            "name": "Open Mobile TTS",
            "version": "0.2.0",
            "status": "Client not built. Run: cd client && npm run build",
        }
