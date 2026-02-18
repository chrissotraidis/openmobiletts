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
from .logging_config import setup_logging, get_logger, preview_text, export_logs_json

# Setup logging
setup_logging()
logger = get_logger(__name__)

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


# TTS request model for POST body
class TTSRequest(BaseModel):
    text: str
    voice: str = settings.DEFAULT_VOICE
    speed: float = settings.DEFAULT_SPEED


# TTS endpoints
@app.post("/api/tts/stream")
async def stream_tts(request: TTSRequest):
    """
    Stream TTS audio with timing metadata.

    Uses POST to handle arbitrarily long text (no URL length limits).

    Protocol per chunk:
        1. TIMING:{json}\\n  — timing metadata
        2. AUDIO:{length}\\n  — byte count of following MP3 data
        3. {MP3 bytes}        — exactly {length} bytes of audio
    """
    text = request.text
    voice = request.voice
    speed = request.speed

    logger.info(f"TTS request: voice={voice}, speed={speed}, text_length={len(text)}")
    logger.debug(f"TTS input text: {preview_text(text, 500)}")

    if not text.strip():
        logger.warning("TTS request rejected: empty text")
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Text cannot be empty",
        )

    # Preprocess and chunk text
    text_chunks = text_preprocessor.process(text)
    logger.info(f"Text preprocessed into {len(text_chunks)} chunks")

    async def generate_stream():
        """Generator that yields timing metadata and audio chunks."""
        chunk_count = 0
        total_audio_bytes = 0
        try:
            async for mp3_bytes, timing in tts_engine.generate_speech_stream(
                text_chunks, voice=voice, speed=speed
            ):
                chunk_count += 1
                total_audio_bytes += len(mp3_bytes)
                logger.debug(f"Generated chunk {chunk_count}: {len(mp3_bytes)} bytes, text={timing.get('text', '')[:50]}")

                # Yield timing metadata as JSON line
                timing_line = f"TIMING:{json.dumps(timing)}\n"
                yield timing_line.encode('utf-8')

                # Yield audio length header then MP3 bytes
                yield f"AUDIO:{len(mp3_bytes)}\n".encode('utf-8')
                yield mp3_bytes

            logger.info(f"TTS stream complete: {chunk_count} chunks, {total_audio_bytes} total bytes")
        except Exception as e:
            logger.error(f"TTS generation error: {e}", exc_info=True)
            raise

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
    logger.info(f"Document upload: filename={file.filename}, content_type={file.content_type}")

    max_size_bytes = settings.MAX_UPLOAD_SIZE_MB * 1024 * 1024
    file_size = 0

    # Sanitize filename to prevent path traversal
    safe_name = Path(file.filename).name
    file_path = Path(settings.UPLOAD_DIR) / safe_name

    try:
        with open(file_path, 'wb') as f:
            while chunk := await file.read(8192):
                file_size += len(chunk)
                if file_size > max_size_bytes:
                    logger.warning(f"Document upload rejected: file too large ({file_size} bytes)")
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail=f"File too large. Maximum size: {settings.MAX_UPLOAD_SIZE_MB}MB",
                    )
                f.write(chunk)

        logger.info(f"Document saved: {file_size} bytes")

        text = document_processor.extract(str(file_path))
        logger.info(f"Document extracted: {len(text)} chars")
        logger.debug(f"Extracted text preview: {preview_text(text, 500)}")

        chunks = text_preprocessor.process(text)
        logger.info(f"Document processed into {len(chunks)} chunks")

        return {
            "filename": file.filename,
            "text": text,
            "chunk_count": len(chunks),
        }

    except ValueError as e:
        logger.error(f"Document processing error: {e}")
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
    logger.info(f"Document stream: filename={file.filename}, voice={voice}, speed={speed}")

    max_size_bytes = settings.MAX_UPLOAD_SIZE_MB * 1024 * 1024
    file_size = 0

    # Sanitize filename to prevent path traversal
    safe_name = Path(file.filename).name
    file_path = Path(settings.UPLOAD_DIR) / safe_name

    try:
        with open(file_path, 'wb') as f:
            while chunk := await file.read(8192):
                file_size += len(chunk)
                if file_size > max_size_bytes:
                    logger.warning(f"Document stream rejected: file too large ({file_size} bytes)")
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail=f"File too large. Maximum size: {settings.MAX_UPLOAD_SIZE_MB}MB",
                    )
                f.write(chunk)

        logger.info(f"Document saved for streaming: {file_size} bytes")

        text = document_processor.extract(str(file_path))
        logger.info(f"Document extracted: {len(text)} chars")

        text_chunks = text_preprocessor.process(text)
        logger.info(f"Document processed into {len(text_chunks)} chunks for TTS")

        async def generate_stream():
            chunk_count = 0
            total_bytes = 0
            try:
                async for mp3_bytes, timing in tts_engine.generate_speech_stream(
                    text_chunks, voice=voice, speed=speed
                ):
                    chunk_count += 1
                    total_bytes += len(mp3_bytes)
                    timing_line = f"TIMING:{json.dumps(timing)}\n"
                    yield timing_line.encode('utf-8')
                    yield f"AUDIO:{len(mp3_bytes)}\n".encode('utf-8')
                    yield mp3_bytes
                logger.info(f"Document stream complete: {chunk_count} chunks, {total_bytes} bytes")
            except Exception as e:
                logger.error(f"Document stream error: {e}", exc_info=True)
                raise

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
        logger.error(f"Document stream processing error: {e}")
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


# Logs export endpoint (for mobile bug reports)
@app.get("/api/logs/export")
async def export_logs(max_lines: int = 500):
    """Export recent logs as JSON for bug reports.

    Args:
        max_lines: Maximum number of log entries to return (default 500)

    Returns:
        JSON with metadata and log entries
    """
    logger.info(f"Log export requested: max_lines={max_lines}")
    return export_logs_json(max_lines=min(max_lines, 2000))  # Cap at 2000


# Serve built SvelteKit static files
# This must be mounted LAST so API routes take priority
_static_dir = Path(settings.STATIC_DIR) if settings.STATIC_DIR else Path(__file__).parent.parent.parent / "client" / "build"

if _static_dir.exists():
    _static_root = _static_dir.resolve()

    @app.get("/{path:path}")
    async def serve_spa(path: str):
        """Serve the SvelteKit SPA — files if they exist, index.html otherwise."""
        file_path = (_static_dir / path).resolve()
        # Prevent path traversal — resolved path must be within static root
        if str(file_path).startswith(str(_static_root)) and file_path.is_file():
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
