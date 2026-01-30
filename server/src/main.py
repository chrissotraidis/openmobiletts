"""Open Mobile TTS - FastAPI server application."""

import json
import os
from pathlib import Path
from typing import List

from fastapi import Depends, FastAPI, File, HTTPException, UploadFile, status
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import StreamingResponse, HTMLResponse
from fastapi.security import OAuth2PasswordRequestForm
from pydantic import BaseModel

from .auth import authenticate_user, create_access_token, verify_token
from .config import settings
from .document_processor import DocumentProcessor
from .text_preprocessor import TextPreprocessor
from .tts_engine import TTSEngine

# Validate settings on startup
settings.validate()

# Create FastAPI app
app = FastAPI(
    title="Open Mobile TTS",
    description="Private text-to-speech server with streaming support",
    version="0.1.0",
)

# CORS middleware for web client
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.CORS_ORIGINS,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Initialize services
tts_engine = TTSEngine()
text_preprocessor = TextPreprocessor()
document_processor = DocumentProcessor()

# Ensure upload directory exists
Path(settings.UPLOAD_DIR).mkdir(parents=True, exist_ok=True)


# Request/Response models
class TokenResponse(BaseModel):
    access_token: str
    token_type: str


class VoiceInfo(BaseModel):
    name: str
    language: str


class TextRequest(BaseModel):
    text: str
    voice: str = settings.DEFAULT_VOICE
    speed: float = settings.DEFAULT_SPEED


class DocumentUploadResponse(BaseModel):
    filename: str
    text: str
    chunk_count: int


# Authentication endpoints
@app.post("/token", response_model=TokenResponse)
async def login(form_data: OAuth2PasswordRequestForm = Depends()):
    """
    Authenticate user and return JWT token.

    Args:
        form_data: OAuth2 form with username and password

    Returns:
        Access token and token type

    Raises:
        HTTPException: If authentication fails
    """
    if not authenticate_user(form_data.username, form_data.password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid username or password",
            headers={"WWW-Authenticate": "Bearer"},
        )

    access_token = create_access_token(form_data.username)
    return {"access_token": access_token, "token_type": "bearer"}


# Voice endpoints
@app.get("/api/voices", response_model=List[VoiceInfo])
async def list_voices(username: str = Depends(verify_token)):
    """
    List available TTS voices.

    Args:
        username: Authenticated username from token

    Returns:
        List of available voices with metadata
    """
    return tts_engine.available_voices


# TTS endpoints
@app.get("/api/tts/stream")
async def stream_tts(
    text: str,
    voice: str = settings.DEFAULT_VOICE,
    speed: float = settings.DEFAULT_SPEED,
    username: str = Depends(verify_token),
):
    """
    Stream TTS audio with timing metadata.

    This endpoint preprocesses the text, generates audio chunks via Kokoro,
    and streams both timing metadata and MP3 audio to the client.

    Protocol:
        1. For each audio chunk, send timing metadata as JSON line
        2. Immediately follow with MP3 audio bytes
        3. Repeat until all text is processed

    Args:
        text: Text to convert to speech
        voice: Voice name to use
        speed: Speech speed multiplier
        username: Authenticated username from token

    Returns:
        StreamingResponse with chunked MP3 audio and timing metadata
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
            "X-Accel-Buffering": "no",  # Disable nginx buffering
        },
    )


# Document endpoints
@app.post("/api/documents/upload", response_model=DocumentUploadResponse)
async def upload_document(
    file: UploadFile = File(...),
    username: str = Depends(verify_token),
):
    """
    Upload a document (PDF, DOCX, TXT) and extract text for TTS.

    Args:
        file: Uploaded document file
        username: Authenticated username from token

    Returns:
        Extracted text and metadata

    Raises:
        HTTPException: If file format is unsupported or file is too large
    """
    # Validate file size
    max_size_bytes = settings.MAX_UPLOAD_SIZE_MB * 1024 * 1024
    file_size = 0

    # Save uploaded file
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

        # Extract text from document
        text = document_processor.extract(str(file_path))

        # Preprocess and chunk for analysis
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
        # Clean up uploaded file
        if file_path.exists():
            os.unlink(file_path)


@app.post("/api/documents/stream")
async def stream_document(
    file: UploadFile = File(...),
    voice: str = settings.DEFAULT_VOICE,
    speed: float = settings.DEFAULT_SPEED,
    username: str = Depends(verify_token),
):
    """
    Upload a document and stream TTS audio directly.

    Combines document upload, text extraction, and TTS streaming in one endpoint.

    Args:
        file: Uploaded document file
        voice: Voice name to use
        speed: Speech speed multiplier
        username: Authenticated username from token

    Returns:
        StreamingResponse with chunked MP3 audio and timing metadata
    """
    # Validate file size
    max_size_bytes = settings.MAX_UPLOAD_SIZE_MB * 1024 * 1024
    file_size = 0

    # Save uploaded file temporarily
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

        # Extract and preprocess text
        text = document_processor.extract(str(file_path))
        text_chunks = text_preprocessor.process(text)

        async def generate_stream():
            """Generator for streaming document TTS."""
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
        # Clean up uploaded file
        if file_path.exists():
            os.unlink(file_path)


# Health check
@app.get("/health")
async def health_check():
    """Health check endpoint."""
    return {"status": "healthy", "version": "0.1.0"}


# Root endpoint
@app.get("/")
async def root():
    """Root endpoint with API information."""
    return {
        "name": "Open Mobile TTS",
        "version": "0.1.0",
        "description": "Private text-to-speech server with streaming support",
        "endpoints": {
            "auth": "/token",
            "voices": "/api/voices",
            "stream_tts": "/api/tts/stream",
            "upload_document": "/api/documents/upload",
            "stream_document": "/api/documents/stream",
        },
    }


# Debug/Status page
@app.get("/status", response_class=HTMLResponse)
async def status_page():
    """HTML status page for debugging."""
    html_content = """
    <!DOCTYPE html>
    <html>
    <head>
        <title>Open Mobile TTS - Status</title>
        <style>
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Arial, sans-serif;
                max-width: 800px;
                margin: 40px auto;
                padding: 20px;
                background: #f5f5f5;
            }
            .container {
                background: white;
                padding: 30px;
                border-radius: 12px;
                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
            }
            h1 {
                color: #0ea5e9;
                margin-bottom: 10px;
            }
            .status {
                display: inline-block;
                padding: 4px 12px;
                background: #10b981;
                color: white;
                border-radius: 4px;
                font-size: 14px;
            }
            .section {
                margin: 25px 0;
                padding: 20px;
                background: #f9fafb;
                border-radius: 8px;
            }
            .endpoint {
                display: flex;
                align-items: center;
                margin: 10px 0;
                padding: 10px;
                background: white;
                border-radius: 4px;
            }
            .method {
                background: #3b82f6;
                color: white;
                padding: 4px 8px;
                border-radius: 4px;
                font-size: 12px;
                font-weight: bold;
                margin-right: 10px;
                min-width: 50px;
                text-align: center;
            }
            .method.post { background: #10b981; }
            .path {
                font-family: monospace;
                color: #374151;
            }
            button {
                background: #0ea5e9;
                color: white;
                border: none;
                padding: 10px 20px;
                border-radius: 6px;
                cursor: pointer;
                font-size: 14px;
                margin: 5px;
            }
            button:hover {
                background: #0284c7;
            }
            #output {
                margin-top: 15px;
                padding: 15px;
                background: #1f2937;
                color: #10b981;
                border-radius: 6px;
                font-family: monospace;
                font-size: 12px;
                white-space: pre-wrap;
                max-height: 300px;
                overflow-y: auto;
            }
            .info {
                color: #6b7280;
                font-size: 14px;
                margin-top: 10px;
            }
        </style>
    </head>
    <body>
        <div class="container">
            <h1>🎙️ Open Mobile TTS</h1>
            <span class="status">✓ Server Running</span>

            <div class="section">
                <h3>📡 API Endpoints</h3>
                <div class="endpoint">
                    <span class="method post">POST</span>
                    <span class="path">/token</span>
                </div>
                <div class="endpoint">
                    <span class="method">GET</span>
                    <span class="path">/api/voices</span>
                </div>
                <div class="endpoint">
                    <span class="method">GET</span>
                    <span class="path">/api/tts/stream?text={text}&voice={voice}</span>
                </div>
                <div class="endpoint">
                    <span class="method post">POST</span>
                    <span class="path">/api/documents/upload</span>
                </div>
                <div class="endpoint">
                    <span class="method">GET</span>
                    <span class="path">/health</span>
                </div>
            </div>

            <div class="section">
                <h3>🧪 Quick Tests</h3>
                <button onclick="testHealth()">Test Health</button>
                <button onclick="testLogin()">Test Login</button>
                <button onclick="testVoices()">Test Voices</button>
                <div id="output"></div>
            </div>

            <div class="section">
                <h3>ℹ️ Info</h3>
                <p class="info">
                    <strong>Version:</strong> 0.1.0<br>
                    <strong>Client:</strong> <a href="http://localhost:5173" target="_blank">http://localhost:5173</a><br>
                    <strong>API Docs:</strong> <a href="/docs" target="_blank">/docs</a> (FastAPI auto-docs)
                </p>
            </div>
        </div>

        <script>
            const output = document.getElementById('output');

            async function testHealth() {
                output.textContent = 'Testing health endpoint...\n';
                try {
                    const res = await fetch('/health');
                    const data = await res.json();
                    output.textContent += '✓ SUCCESS\n' + JSON.stringify(data, null, 2);
                } catch (err) {
                    output.textContent += '✗ ERROR\n' + err.message;
                }
            }

            async function testLogin() {
                output.textContent = 'Testing login...\n';
                try {
                    const res = await fetch('/token', {
                        method: 'POST',
                        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
                        body: 'username=admin&password=testpassword123'
                    });
                    const data = await res.json();
                    output.textContent += '✓ SUCCESS\n' + JSON.stringify(data, null, 2);
                    window.testToken = data.access_token;
                } catch (err) {
                    output.textContent += '✗ ERROR\n' + err.message;
                }
            }

            async function testVoices() {
                output.textContent = 'Testing voices endpoint...\n';
                if (!window.testToken) {
                    output.textContent += 'Please run "Test Login" first to get a token.';
                    return;
                }
                try {
                    const res = await fetch('/api/voices', {
                        headers: { 'Authorization': 'Bearer ' + window.testToken }
                    });
                    const data = await res.json();
                    output.textContent += '✓ SUCCESS\n' + JSON.stringify(data, null, 2);
                } catch (err) {
                    output.textContent += '✗ ERROR\n' + err.message;
                }
            }
        </script>
    </body>
    </html>
    """
    return HTMLResponse(content=html_content)
