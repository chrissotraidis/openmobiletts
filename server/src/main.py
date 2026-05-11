"""Open Mobile TTS - Single-app server (no authentication)."""

import io
import json
import os
import threading
import time
import uuid
import zipfile
from datetime import datetime
from pathlib import Path
from typing import List, Optional

from fastapi import FastAPI, File, HTTPException, UploadFile, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, FileResponse
from pydantic import BaseModel

from .config import settings
from .document_processor import DocumentProcessor
from .text_preprocessor import TextPreprocessor
from .tts_engine import EngineManager
from .logging_config import setup_logging, get_logger, preview_text, export_logs_json, clear_logs
from .stt_engine import SttEngine, polish_transcript
from .export_manager import export_pdf, export_markdown, export_plaintext
from .project_storage import ProjectStorage

# Setup logging
setup_logging()
logger = get_logger(__name__)

# Create FastAPI app
app = FastAPI(
    title="Open Mobile TTS",
    description="Private text-to-speech app — single process, no auth",
    version="3.0.0",
)

# CORS — allow all origins for local/network access
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize services
engine_manager = EngineManager()
text_preprocessor = TextPreprocessor()
document_processor = DocumentProcessor()
stt_engine = SttEngine()
project_storage = ProjectStorage()

# Ensure upload directory exists
Path(settings.UPLOAD_DIR).mkdir(parents=True, exist_ok=True)


# Request/Response models
class VoiceInfo(BaseModel):
    name: str
    language: str
    language_name: str
    gender: str
    display_name: str


class DocumentUploadResponse(BaseModel):
    filename: str
    text: str
    chunk_count: int


# Voice endpoints
@app.get("/api/voices", response_model=List[VoiceInfo])
async def list_voices():
    """List available TTS voices for the active engine."""
    return engine_manager.active.available_voices


# Engine endpoints
class EngineInfo(BaseModel):
    name: str
    label: str
    available: bool
    active: bool = False


class EngineSwitchRequest(BaseModel):
    engine: str


@app.get("/api/engines", response_model=List[EngineInfo])
async def list_engines():
    """List available TTS engines."""
    engines = engine_manager.available_engines()
    for e in engines:
        e["active"] = e["name"] == engine_manager.active_name
    return engines


@app.get("/api/engine")
async def get_engine():
    """Get the currently active engine name."""
    return {"engine": engine_manager.active_name}


@app.post("/api/engine/switch")
async def switch_engine(request: EngineSwitchRequest):
    """Switch the active TTS engine at runtime."""
    valid_names = {e["name"] for e in engine_manager.available_engines() if e["available"]}
    if request.engine not in valid_names:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Engine '{request.engine}' is not available. Valid: {sorted(valid_names)}",
        )
    logger.info(f"Switching TTS engine: {engine_manager.active_name} → {request.engine}")
    try:
        engine_manager.switch(request.engine)
    except (ImportError, FileNotFoundError, RuntimeError) as e:
        logger.error(f"Failed to switch engine to '{request.engine}': {e}")
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(e))
    return {
        "engine": engine_manager.active_name,
        "voices": len(engine_manager.active.available_voices),
    }


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

    # Validate voice is available on the active engine
    valid_voices = {v['name'] for v in engine_manager.active.available_voices}
    if voice not in valid_voices:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Voice '{voice}' is not available. Valid voices: {sorted(valid_voices)}",
        )

    # Preprocess and chunk text
    text_chunks = text_preprocessor.process(text)
    logger.info(f"Text preprocessed into {len(text_chunks)} chunks")

    if not text_chunks:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Text produced no speakable content after preprocessing",
        )

    async def generate_stream():
        """Generator that yields timing metadata and audio chunks."""
        chunk_count = 0
        total_audio_bytes = 0
        try:
            async for mp3_bytes, timing in engine_manager.active.generate_speech_stream(
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
    if not file.filename:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Filename is required")

    logger.info(f"Document upload: filename={file.filename}, content_type={file.content_type}")

    max_size_bytes = settings.MAX_UPLOAD_SIZE_MB * 1024 * 1024
    file_size = 0

    # Validate extension before writing to disk
    suffix = Path(file.filename).suffix.lower()
    if suffix not in DocumentProcessor.SUPPORTED_FORMATS:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unsupported format: {suffix}. Supported: {', '.join(DocumentProcessor.SUPPORTED_FORMATS)}",
        )

    # Use UUID prefix to prevent filename collisions from concurrent uploads
    safe_name = f"{uuid.uuid4().hex[:8]}_{Path(file.filename).name}"
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

    except (ValueError, RuntimeError, OSError) as e:
        logger.error(f"Document processing error: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Document processing failed: {e}",
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
    if not file.filename:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="Filename is required")

    logger.info(f"Document stream: filename={file.filename}, voice={voice}, speed={speed}")

    max_size_bytes = settings.MAX_UPLOAD_SIZE_MB * 1024 * 1024
    file_size = 0

    # Validate extension before writing to disk
    suffix = Path(file.filename).suffix.lower()
    if suffix not in DocumentProcessor.SUPPORTED_FORMATS:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unsupported format: {suffix}. Supported: {', '.join(DocumentProcessor.SUPPORTED_FORMATS)}",
        )

    # Use UUID prefix to prevent filename collisions from concurrent uploads
    safe_name = f"{uuid.uuid4().hex[:8]}_{Path(file.filename).name}"
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
                async for mp3_bytes, timing in engine_manager.active.generate_speech_stream(
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
            finally:
                # Clean up temp file after the stream is fully consumed (or errors).
                # Must be inside the generator — the outer finally runs before streaming starts.
                if file_path.exists():
                    os.unlink(file_path)

        return StreamingResponse(
            generate_stream(),
            media_type="audio/mpeg",
            headers={
                "Cache-Control": "no-cache",
                "X-Content-Type-Options": "nosniff",
                "X-Accel-Buffering": "no",
            },
        )

    except HTTPException:
        # Clean up temp file on HTTP errors (e.g. file-too-large)
        if file_path.exists():
            os.unlink(file_path)
        raise

    except (ValueError, RuntimeError, OSError) as e:
        logger.error(f"Document stream processing error: {e}", exc_info=True)
        # Clean up temp file on pre-stream errors (extraction/processing failures)
        if file_path.exists():
            os.unlink(file_path)
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Document processing failed: {e}",
        )


# ── STT endpoints ──────────────────────────────────────────

SUPPORTED_AUDIO_FORMATS = {".mp3", ".aac", ".ogg", ".wav", ".webm", ".m4a"}
MAX_BATCH_AUDIO_FILES = 100
BATCH_JOBS: dict[str, dict] = {}
BATCH_JOBS_LOCK = threading.Lock()


def _safe_export_stem(filename: str) -> str:
    """Return a filesystem-safe stem for exported transcript files."""
    stem = Path(filename).stem.strip() or "transcript"
    safe = "".join(c if c.isalnum() or c in " ._-" else "_" for c in stem)
    safe = safe.strip(" ._-") or "transcript"
    return safe[:80]


def _unique_markdown_name(filename: str, used_names: set[str]) -> str:
    """Create a unique Markdown filename based on the source audio filename."""
    base = _safe_export_stem(filename)
    candidate = f"{base}.md"
    counter = 2
    while candidate.lower() in used_names:
        candidate = f"{base}-{counter}.md"
        counter += 1
    used_names.add(candidate.lower())
    return candidate


def _format_transcript_markdown(
    *,
    title: str,
    source_filename: str,
    model: str,
    transcribed_at: str,
    transcript: str,
    error: Optional[str] = None,
) -> str:
    """Build the Markdown file body for one transcript or failed file."""
    status_text = "Failed" if error else "Complete"
    lines = [
        f"# {title}",
        "",
        f"Source file: `{source_filename}`",
        f"Transcribed: {transcribed_at}",
        f"Model: {model}",
        "Speaker mode: Not separated",
        f"Status: {status_text}",
        "",
    ]
    if error:
        lines.extend(["## Error", "", error.strip() or "Unknown error", ""])
    else:
        body = polish_transcript(transcript) or "_No speech detected._"
        lines.extend(["## Transcript", "", body, ""])
    return "\n".join(lines)


def _ensure_stt_ready() -> None:
    """Initialize the STT engine or raise a user-facing HTTP error."""
    if not stt_engine.is_available:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="STT not available — sherpa_onnx not installed",
        )

    if stt_engine.is_initialized:
        return

    model_dir = Path.home() / ".cache" / SttEngine.MODEL_NAME
    if not model_dir.exists():
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"STT model not found at {model_dir}. Download it first.",
        )
    stt_engine.init(str(model_dir))


def _public_batch_job(job: dict) -> dict:
    """Return the browser-safe representation of a batch job."""
    return {
        "id": job["id"],
        "status": job["status"],
        "created_at": job["created_at"],
        "updated_at": job["updated_at"],
        "total": job["total"],
        "completed": job["completed"],
        "failed": job["failed"],
        "current_file": job.get("current_file"),
        "error": job.get("error"),
        "result_url": job.get("result_url"),
        "files": job["files"],
    }


def _get_batch_job(job_id: str) -> dict:
    with BATCH_JOBS_LOCK:
        job = BATCH_JOBS.get(job_id)
        if not job:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Batch job not found")
        return _public_batch_job(job)


def _update_batch_job(job_id: str, **fields) -> None:
    with BATCH_JOBS_LOCK:
        job = BATCH_JOBS[job_id]
        job.update(fields)
        job["updated_at"] = datetime.now().astimezone().isoformat(timespec="seconds")


def _update_batch_file(job_id: str, index: int, **fields) -> None:
    with BATCH_JOBS_LOCK:
        job = BATCH_JOBS[job_id]
        job["files"][index].update(fields)
        job["updated_at"] = datetime.now().astimezone().isoformat(timespec="seconds")


def _run_batch_job(job_id: str, job_dir: Path, items: list[dict]) -> None:
    """Process a saved batch job in the background."""
    try:
        logger.info(f"Batch job started: id={job_id}, files={len(items)}, workspace={job_dir}")
        _ensure_stt_ready()
        _update_batch_job(job_id, status="running", current_file=None)

        transcribed_at = datetime.now().astimezone().isoformat(timespec="seconds")
        used_names: set[str] = set()
        results = []
        zip_path = job_dir / "transcripts.zip"

        with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for index, item in enumerate(items):
                source_name = item["source_name"]
                markdown_name = _unique_markdown_name(source_name, used_names)
                title = Path(markdown_name).stem
                logger.info(
                    f"Batch job file started: id={job_id}, file={index + 1}/{len(items)}, "
                    f"source='{source_name}', transcript='{markdown_name}'"
                )
                _update_batch_file(
                    job_id,
                    index,
                    status="running",
                    transcript_file=markdown_name,
                    error=None,
                )
                _update_batch_job(job_id, current_file=source_name)

                result = {
                    "source_file": source_name,
                    "transcript_file": markdown_name,
                    "status": "pending",
                    "model": "moonshine-v2-medium",
                    "error": None,
                }

                try:
                    if item.get("validation_error"):
                        raise ValueError(item["validation_error"])

                    transcript = stt_engine.transcribe_file(str(item["file_path"]))
                    markdown = _format_transcript_markdown(
                        title=title,
                        source_filename=source_name,
                        model="moonshine-v2-medium",
                        transcribed_at=transcribed_at,
                        transcript=transcript,
                    )
                    result["status"] = "complete"
                    result["text_length"] = len(transcript)
                    archive.writestr(markdown_name, markdown)
                    _update_batch_file(job_id, index, status="complete", text_length=len(transcript))
                    logger.info(
                        f"Batch job file complete: id={job_id}, file={index + 1}/{len(items)}, "
                        f"source='{source_name}', chars={len(transcript)}"
                    )
                    with BATCH_JOBS_LOCK:
                        BATCH_JOBS[job_id]["completed"] += 1

                except Exception as e:
                    error = str(e)
                    logger.error(f"Batch STT failed for {source_name}: {error}", exc_info=True)
                    markdown = _format_transcript_markdown(
                        title=title,
                        source_filename=source_name,
                        model="moonshine-v2-medium",
                        transcribed_at=transcribed_at,
                        transcript="",
                        error=error,
                    )
                    result["status"] = "failed"
                    result["error"] = error
                    archive.writestr(markdown_name, markdown)
                    _update_batch_file(job_id, index, status="failed", error=error)
                    with BATCH_JOBS_LOCK:
                        BATCH_JOBS[job_id]["failed"] += 1

                finally:
                    results.append(result)
                    file_path = item.get("file_path")
                    if file_path and Path(file_path).exists():
                        os.unlink(file_path)

            completed = sum(1 for item in results if item["status"] == "complete")
            failed = len(results) - completed

        _update_batch_job(
            job_id,
            status="complete",
            current_file=None,
            result_url=f"/api/stt/batch/jobs/{job_id}/download",
            zip_path=str(zip_path),
        )
        logger.info(
            f"Batch job complete: id={job_id}, completed={completed}, failed={failed}, zip={zip_path}"
        )

    except HTTPException as e:
        logger.warning(f"Batch job failed before completion: id={job_id}, error={e.detail}")
        _update_batch_job(job_id, status="failed", current_file=None, error=str(e.detail))
    except Exception as e:
        logger.error(f"Batch job failed: {job_id}: {e}", exc_info=True)
        _update_batch_job(job_id, status="failed", current_file=None, error=str(e))


class SttTranscribeResponse(BaseModel):
    text: str
    duration_ms: int
    model: str


@app.post("/api/stt/transcribe", response_model=SttTranscribeResponse)
async def stt_transcribe(audio: UploadFile = File(...)):
    """Transcribe uploaded audio to text via Moonshine v2."""
    # Save uploaded audio to temp file
    safe_name = f"{uuid.uuid4().hex[:8]}_{audio.filename or 'audio.wav'}"
    file_path = Path(settings.UPLOAD_DIR) / safe_name

    try:
        max_size_bytes = settings.MAX_UPLOAD_SIZE_MB * 1024 * 1024
        file_size = 0
        with open(file_path, "wb") as f:
            while chunk := await audio.read(8192):
                file_size += len(chunk)
                if file_size > max_size_bytes:
                    raise HTTPException(
                        status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                        detail=f"Audio file too large. Maximum: {settings.MAX_UPLOAD_SIZE_MB}MB",
                    )
                f.write(chunk)

        _ensure_stt_ready()
        text = stt_engine.transcribe_file(str(file_path))
        duration_ms = 0  # TODO: extract from audio metadata

        return {"text": text, "duration_ms": duration_ms, "model": "moonshine-v2-medium"}

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"STT transcription failed: {e}", exc_info=True)
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Transcription failed: {e}",
        )
    finally:
        if file_path.exists():
            os.unlink(file_path)


@app.post("/api/stt/batch/jobs", status_code=status.HTTP_202_ACCEPTED)
async def create_stt_batch_job(files: List[UploadFile] = File(...)):
    """Create a background batch transcription job."""
    if not files:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No files uploaded")

    if len(files) > MAX_BATCH_AUDIO_FILES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Too many files. Maximum batch size: {MAX_BATCH_AUDIO_FILES}",
        )

    job_id = uuid.uuid4().hex
    created_at = datetime.now().astimezone().isoformat(timespec="seconds")
    job_dir = Path(settings.UPLOAD_DIR) / f"batch_{job_id}"
    job_dir.mkdir(parents=True, exist_ok=True)
    logger.info(f"Batch job created: id={job_id}, submitted_files={len(files)}")

    max_size_bytes = settings.MAX_UPLOAD_SIZE_MB * 1024 * 1024
    items = []
    job_files = []

    for index, audio in enumerate(files, start=1):
        source_name = Path(audio.filename or f"audio-{index}.wav").name
        suffix = Path(source_name).suffix.lower()
        file_path = job_dir / f"{uuid.uuid4().hex[:8]}_{source_name}"
        validation_error = None
        file_size = 0

        if suffix not in SUPPORTED_AUDIO_FORMATS:
            supported = ", ".join(sorted(SUPPORTED_AUDIO_FORMATS))
            validation_error = f"Unsupported audio format: {suffix}. Supported: {supported}"
            logger.warning(
                f"Batch job upload rejected: id={job_id}, source='{source_name}', error={validation_error}"
            )
        else:
            try:
                with open(file_path, "wb") as f:
                    while chunk := await audio.read(8192):
                        file_size += len(chunk)
                        if file_size > max_size_bytes:
                            validation_error = (
                                f"Audio file too large. Maximum: {settings.MAX_UPLOAD_SIZE_MB}MB"
                            )
                            logger.warning(
                                f"Batch job upload rejected: id={job_id}, source='{source_name}', "
                                f"bytes={file_size}, error={validation_error}"
                            )
                            break
                        f.write(chunk)
                if validation_error and file_path.exists():
                    os.unlink(file_path)
            except Exception as e:
                validation_error = f"Could not save upload: {e}"
                logger.error(
                    f"Batch job upload save failed: id={job_id}, source='{source_name}', error={e}",
                    exc_info=True,
                )
                if file_path.exists():
                    os.unlink(file_path)

        if not validation_error:
            logger.info(
                f"Batch job file queued: id={job_id}, file={index}/{len(files)}, "
                f"source='{source_name}', bytes={file_size}"
            )

        items.append(
            {
                "source_name": source_name,
                "file_path": None if validation_error else file_path,
                "validation_error": validation_error,
            }
        )
        job_files.append(
            {
                "source_file": source_name,
                "transcript_file": None,
                "status": "queued",
                "error": validation_error,
            }
        )

    with BATCH_JOBS_LOCK:
        BATCH_JOBS[job_id] = {
            "id": job_id,
            "status": "queued",
            "created_at": created_at,
            "updated_at": created_at,
            "total": len(job_files),
            "completed": 0,
            "failed": 0,
            "current_file": None,
            "error": None,
            "result_url": None,
            "zip_path": None,
            "files": job_files,
        }

    thread = threading.Thread(target=_run_batch_job, args=(job_id, job_dir, items), daemon=True)
    thread.start()

    return _get_batch_job(job_id)


@app.get("/api/stt/batch/jobs/{job_id}")
async def get_stt_batch_job(job_id: str):
    """Get background batch transcription job status."""
    return _get_batch_job(job_id)


@app.get("/api/stt/batch/jobs/{job_id}/download")
async def download_stt_batch_job(job_id: str):
    """Download a completed batch transcription ZIP."""
    with BATCH_JOBS_LOCK:
        job = BATCH_JOBS.get(job_id)
        if not job:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Batch job not found")
        if job["status"] != "complete" or not job.get("zip_path"):
            raise HTTPException(status_code=status.HTTP_409_CONFLICT, detail="Batch job is not complete")
        zip_path = Path(job["zip_path"])

    if not zip_path.exists():
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Batch ZIP not found")

    return FileResponse(
        zip_path,
        media_type="application/zip",
        filename=f"openmobiletts-transcripts-{job_id[:8]}.zip",
    )


@app.post("/api/stt/batch")
async def stt_batch_transcribe(files: List[UploadFile] = File(...)):
    """Transcribe multiple audio files sequentially and return Markdown ZIP."""
    if not files:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail="No files uploaded")

    if len(files) > MAX_BATCH_AUDIO_FILES:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Too many files. Maximum batch size: {MAX_BATCH_AUDIO_FILES}",
        )

    _ensure_stt_ready()

    max_size_bytes = settings.MAX_UPLOAD_SIZE_MB * 1024 * 1024
    transcribed_at = datetime.now().astimezone().isoformat(timespec="seconds")
    used_names: set[str] = set()
    results = []
    zip_buffer = io.BytesIO()

    with zipfile.ZipFile(zip_buffer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for index, audio in enumerate(files, start=1):
            source_name = Path(audio.filename or f"audio-{index}.wav").name
            suffix = Path(source_name).suffix.lower()
            markdown_name = _unique_markdown_name(source_name, used_names)
            title = Path(markdown_name).stem
            file_path = Path(settings.UPLOAD_DIR) / f"{uuid.uuid4().hex[:8]}_{source_name}"
            result = {
                "source_file": source_name,
                "transcript_file": markdown_name,
                "status": "pending",
                "model": "moonshine-v2-medium",
                "error": None,
            }

            try:
                if suffix not in SUPPORTED_AUDIO_FORMATS:
                    supported = ", ".join(sorted(SUPPORTED_AUDIO_FORMATS))
                    raise ValueError(f"Unsupported audio format: {suffix}. Supported: {supported}")

                file_size = 0
                with open(file_path, "wb") as f:
                    while chunk := await audio.read(8192):
                        file_size += len(chunk)
                        if file_size > max_size_bytes:
                            raise ValueError(
                                f"Audio file too large. Maximum: {settings.MAX_UPLOAD_SIZE_MB}MB"
                            )
                        f.write(chunk)

                transcript = stt_engine.transcribe_file(str(file_path))
                markdown = _format_transcript_markdown(
                    title=title,
                    source_filename=source_name,
                    model="moonshine-v2-medium",
                    transcribed_at=transcribed_at,
                    transcript=transcript,
                )
                result["status"] = "complete"
                result["text_length"] = len(transcript)
                archive.writestr(markdown_name, markdown)

            except Exception as e:
                error = str(e)
                logger.error(f"Batch STT failed for {source_name}: {error}", exc_info=True)
                markdown = _format_transcript_markdown(
                    title=title,
                    source_filename=source_name,
                    model="moonshine-v2-medium",
                    transcribed_at=transcribed_at,
                    transcript="",
                    error=error,
                )
                result["status"] = "failed"
                result["error"] = error
                archive.writestr(markdown_name, markdown)

            finally:
                results.append(result)
                if file_path.exists():
                    os.unlink(file_path)

    zip_buffer.seek(0)
    download_name = f"openmobiletts-transcripts-{datetime.now().strftime('%Y%m%d-%H%M%S')}.zip"
    return StreamingResponse(
        zip_buffer,
        media_type="application/zip",
        headers={"Content-Disposition": f'attachment; filename="{download_name}"'},
    )


@app.get("/api/stt/models")
async def stt_models():
    """List available STT models."""
    return {
        "models": [
            {
                "name": "moonshine-v2-medium",
                "size_mb": 250,
                "downloaded": stt_engine.is_initialized or (Path.home() / ".cache" / SttEngine.MODEL_NAME).exists(),
                "active": stt_engine.is_initialized,
            }
        ]
    }


# ── Export endpoints ──────────────────────────────────────

class ExportRequest(BaseModel):
    text: str
    title: str = "Export"


@app.post("/api/export/pdf")
async def export_pdf_endpoint(request: ExportRequest):
    """Export text as PDF."""
    if not request.text.strip():
        raise HTTPException(status_code=400, detail="No text to export")
    pdf_bytes = export_pdf(request.text, request.title)
    sanitized = "".join(c if c.isalnum() or c in "._-" else "_" for c in request.title[:50])
    return StreamingResponse(
        io.BytesIO(pdf_bytes),
        media_type="application/pdf",
        headers={"Content-Disposition": f'attachment; filename="{sanitized}.pdf"'},
    )


@app.post("/api/export/md")
async def export_md_endpoint(request: ExportRequest):
    """Export text as Markdown."""
    if not request.text.strip():
        raise HTTPException(status_code=400, detail="No text to export")
    md_bytes = export_markdown(request.text, request.title)
    sanitized = "".join(c if c.isalnum() or c in "._-" else "_" for c in request.title[:50])
    return StreamingResponse(
        io.BytesIO(md_bytes),
        media_type="text/markdown",
        headers={"Content-Disposition": f'attachment; filename="{sanitized}.md"'},
    )


@app.post("/api/export/txt")
async def export_txt_endpoint(request: ExportRequest):
    """Export text as plain text."""
    if not request.text.strip():
        raise HTTPException(status_code=400, detail="No text to export")
    txt_bytes = export_plaintext(request.text, request.title)
    sanitized = "".join(c if c.isalnum() or c in "._-" else "_" for c in request.title[:50])
    return StreamingResponse(
        io.BytesIO(txt_bytes),
        media_type="text/plain",
        headers={"Content-Disposition": f'attachment; filename="{sanitized}.txt"'},
    )


# ── Project endpoints ──────────────────────────────────────

class ProjectCreateRequest(BaseModel):
    title: str = "Untitled"
    type: str = "tts"
    content: str = ""


class ProjectUpdateRequest(BaseModel):
    content: Optional[str] = None
    title: Optional[str] = None


class CleanupRequest(BaseModel):
    older_than_days: int = 30


@app.get("/api/projects")
async def list_projects():
    """List all projects."""
    return {"projects": project_storage.list_projects()}


@app.post("/api/projects")
async def create_project(request: ProjectCreateRequest):
    """Create a new project."""
    project_id = project_storage.create(request.title, request.type, request.content)
    return {"id": project_id, "created": int(time.time() * 1000)}


@app.get("/api/projects/export")
async def export_all_projects():
    """Export all project metadata as JSON."""
    return project_storage.export_all()


@app.post("/api/projects/cleanup")
async def cleanup_projects(request: CleanupRequest):
    """Delete projects older than the specified number of days."""
    deleted = project_storage.cleanup(request.older_than_days)
    return {"deleted_count": deleted}


@app.get("/api/projects/{project_id}")
async def get_project(project_id: str):
    """Get a project by ID."""
    project = project_storage.get(project_id)
    if project is None:
        raise HTTPException(status_code=404, detail="Project not found")
    return project


@app.put("/api/projects/{project_id}")
async def update_project(project_id: str, request: ProjectUpdateRequest):
    """Update a project's content and/or title."""
    updated = project_storage.update(project_id, request.content, request.title)
    if not updated:
        raise HTTPException(status_code=404, detail="Project not found")
    return {"modified": int(time.time() * 1000)}


@app.delete("/api/projects/{project_id}")
async def delete_project(project_id: str):
    """Delete a project."""
    deleted = project_storage.delete(project_id)
    if not deleted:
        raise HTTPException(status_code=404, detail="Project not found")
    return {"deleted": True}


# Health check
@app.get("/api/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "version": "3.0.0"}


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


@app.post("/api/logs/clear")
async def clear_logs_endpoint():
    """Clear all server log files."""
    clear_logs()
    logger.info("Logs cleared")
    return {"cleared": True}


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
            "version": "3.0.0",
            "status": "Client not built. Run: cd client && npm run build",
        }
