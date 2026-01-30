# Private TTS Application

# **Private TTS Web Application: Technical Architecture Plan**

**Kokoro-based text-to-speech with streaming PWA playback and synchronized text highlighting is entirely achievable** with the architecture outlined below. The 82M-parameter Kokoro TTS model delivers top-tier quality at **35-210x real-time speed** on GPU, supports native streaming through a generator API, and is Apache 2.0 licensed. This plan provides specific library choices, code patterns, and implementation strategies for each component.

---

# **Kokoro TTS is ideal for this project**

Kokoro TTS (released late 2024) stands out as the optimal choice for a self-hosted TTS server. Despite having only **82 million parameters**, it achieved #1 ranking in the HuggingFace TTS Arena, beating models 5-15x its size. The model runs efficiently on modest hardware—a GTX 1060 6GB provides usable performance, while an RTX 4090 delivers ~210x real-time generation.

**Key specifications:** - **Sample rate**: 24kHz native output - **Voices**: 54 voices across 8 languages (American/British English, Spanish, French, Hindi, Italian, Japanese, Portuguese, Mandarin) - **Token limit**: 510 tokens per inference pass (~250 words optimal per chunk) - **GPU VRAM**: ~2-3GB required - **License**: Apache 2.0 (fully commercial use permitted)

**Installation requirements:** ```bash

# **Python 3.9-3.12 (not 3.13+)**

pip install kokoro>=0.9.4 soundfile misaki[en]

# **System dependency required:**

apt-get install espeak-ng # Linux ```

Critically, **Kokoro natively supports streaming output** through its generator-based API. The `KPipeline` returns a generator that yields audio chunks progressively, enabling real-time streaming to the client without waiting for full text processing:

`from kokoro import KPipeline

pipeline = KPipeline(lang_code='a')
generator = pipeline(text, voice='af_heart', speed=1.0)

# Each iteration yields one audio chunk immediately
for graphemes, phonemes, audio in generator:
    yield encode_to_mp3(audio)  # Stream each chunk`

---

# **System architecture overview**

`┌─────────────────────────────────────────────────────────────────┐
│                         VPS Server                               │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │                    FastAPI Application                       │ │
│  │  ┌───────────┐ ┌───────────────┐ ┌────────────────────────┐ │ │
│  │  │ Auth      │ │ Document      │ │ TTS Engine             │ │ │
│  │  │ (JWT)     │ │ Processor     │ │ (Kokoro + Streaming)   │ │ │
│  │  └───────────┘ └───────────────┘ └────────────────────────┘ │ │
│  │                         │                                    │ │
│  │  ┌──────────────────────▼─────────────────────────────────┐ │ │
│  │  │              StreamingResponse (chunked MP3)            │ │ │
│  │  └─────────────────────────────────────────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────────┘ │
└───────────────────────────────┬─────────────────────────────────┘
                                │ HTTPS
┌───────────────────────────────▼─────────────────────────────────┐
│                         PWA Client                               │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Audio Player │ Sync Controller │ Text Display (highlights) │ │
│  └─────────────────────────────────────────────────────────────┘ │
│  ┌─────────────────────────────────────────────────────────────┐ │
│  │  Service Worker (offline caching) │ MediaSession API        │ │
│  └─────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘`

---

# **Server-side implementation**

### **FastAPI application structure**

The recommended server stack uses **FastAPI** for the API layer, **pymupdf4llm** for PDF extraction (best multi-column and structure handling), **python-docx** for DOCX files, and **pydub** for MP3 encoding. FastAPI's `StreamingResponse` with chunked transfer encoding enables progressive audio delivery.

**Recommended dependencies:**`fastapi>=0.109.0 uvicorn[standard] python-multipart python-jose[cryptography] pwdlib[argon2] kokoro>=0.9.4 soundfile pymupdf4llm python-docx pydub num2words`

**Project structure:**`server/ ├── main.py # FastAPI app entry ├── auth.py # JWT authentication ├── tts_engine.py # Kokoro wrapper with streaming ├── document_processor.py # PDF/DOCX extraction ├── audio_encoder.py # MP3 encoding utilities ├── text_preprocessor.py # TTS text cleaning └── config.py # Environment configuration`

### **Streaming TTS endpoint implementation**

The core streaming endpoint accepts text (raw or from document extraction) and returns chunked MP3 audio with timing metadata. The pattern splits text at sentence boundaries, generates audio for each chunk through Kokoro's generator, encodes to MP3, and yields immediately:

`from fastapi import FastAPI, Depends
from fastapi.responses import StreamingResponse
from kokoro import KPipeline
from pydub import AudioSegment
from io import BytesIO
import asyncio
import json

app = FastAPI()
pipeline = KPipeline(lang_code='a')

async def generate_tts_stream(text: str, voice: str = 'af_heart'):
    """Generator yielding MP3 chunks with timing metadata."""
    cumulative_time = 0.0
    
    for graphemes, phonemes, audio in pipeline(text, voice=voice):
        # Encode WAV numpy array to MP3
        wav_buffer = BytesIO()
        sf.write(wav_buffer, audio, 24000, format='WAV')
        wav_buffer.seek(0)
        
        audio_segment = AudioSegment.from_file(wav_buffer, format='wav')
        mp3_buffer = BytesIO()
        audio_segment.export(mp3_buffer, format='mp3', bitrate='64k',
                            parameters=['-ar', '22050', '-ac', '1'])
        
        # Calculate timing for text sync
        chunk_duration = len(audio) / 24000
        timing = {
            'text': graphemes,
            'start': cumulative_time,
            'end': cumulative_time + chunk_duration
        }
        cumulative_time += chunk_duration
        
        # Yield timing metadata then audio chunk
        yield f"TIMING:{json.dumps(timing)}\n".encode()
        yield mp3_buffer.getvalue()
        await asyncio.sleep(0)

@app.get("/api/tts/stream")
async def stream_tts(text: str, voice: str = 'af_heart', 
                     user = Depends(verify_token)):
    return StreamingResponse(
        generate_tts_stream(text, voice),
        media_type="audio/mpeg",
        headers={"Cache-Control": "no-cache", "X-Content-Type-Options": "nosniff"}
    )`

**MP3 encoding settings for speech**: Use **64kbps CBR, 22050Hz mono**. This provides excellent voice quality at minimal file size (~480KB per minute). For higher quality, use 96kbps. Constant bitrate (CBR) is critical for streaming to prevent playback stuttering.

### **Document extraction pipeline**

**For PDFs, use pymupdf4llm**—it provides the best balance of speed, accuracy, and structure preservation. It automatically handles multi-column layouts and outputs clean Markdown that's easily converted to plain text. For DOCX, **python-docx** is the standard choice.

`import pymupdf4llm
from docx import Document
import re

class DocumentProcessor:
    def extract_pdf(self, filepath: str) -> str:
        """Extract text from PDF preserving reading order."""
        markdown = pymupdf4llm.to_markdown(filepath, write_images=False)
        return self._markdown_to_plain(markdown)
    
    def extract_docx(self, filepath: str) -> str:
        """Extract text from DOCX file."""
        doc = Document(filepath)
        paragraphs = [p.text.strip() for p in doc.paragraphs if p.text.strip()]
        return "\n\n".join(paragraphs)
    
    def _markdown_to_plain(self, markdown: str) -> str:
        """Convert markdown to TTS-ready plain text."""
        text = re.sub(r'^#{1,6}\s+', '', markdown, flags=re.MULTILINE)
        text = re.sub(r'\*{1,2}([^*]+)\*{1,2}', r'\1', text)
        text = re.sub(r'\[([^\]]+)\]\([^)]+\)', r'\1', text)
        return text`

**Text preprocessing for TTS** is essential—expand abbreviations ("Dr." → "Doctor"), convert numbers to words using `num2words`, remove PDF artifacts (page numbers, hyphenation), and normalize Unicode with NFKC. Segment text into chunks of **175-250 tokens**(approximately 400-500 characters) at sentence boundaries for optimal Kokoro quality.

### **Authentication with JWT**

For a single-user private app, JWT with OAuth2 password flow provides the right balance of security and simplicity. Use **Argon2** for password hashing (recommended by OWASP over bcrypt) via the `pwdlib` library:

`from datetime import datetime, timedelta, timezone
from fastapi import Depends, HTTPException
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
import jwt
from pwdlib import PasswordHash
import os

SECRET_KEY = os.environ.get("JWT_SECRET")  # Generate: openssl rand -hex 32
ADMIN_USERNAME = os.environ.get("ADMIN_USERNAME")
ADMIN_PASSWORD_HASH = os.environ.get("ADMIN_PASSWORD_HASH")

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token")
password_hash = PasswordHash.recommended()

def create_access_token(username: str, expires_hours: int = 24) -> str:
    expire = datetime.now(timezone.utc) + timedelta(hours=expires_hours)
    return jwt.encode({"sub": username, "exp": expire}, SECRET_KEY, algorithm="HS256")

def verify_token(token: str = Depends(oauth2_scheme)) -> str:
    try:
        payload = jwt.decode(token, SECRET_KEY, algorithms=["HS256"])
        return payload.get("sub")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid token")

@app.post("/token")
async def login(form_data: OAuth2PasswordRequestForm = Depends()):
    if form_data.username != ADMIN_USERNAME:
        raise HTTPException(status_code=401, detail="Invalid credentials")
    if not password_hash.verify(ADMIN_PASSWORD_HASH, form_data.password):
        raise HTTPException(status_code=401, detail="Invalid credentials")
    return {"access_token": create_access_token(form_data.username), "token_type": "bearer"}`

Store the token in an **httpOnly cookie** (most secure, prevents XSS) or localStorage (simpler, acceptable for private single-user apps). Token expiration of 24 hours balances security and convenience for personal use.

---

# **PWA client implementation**

### **Text synchronization strategy**

For audiobook-style text highlighting synchronized with playback, you need timing data mapping text segments to audio timestamps. The recommended approach generates **sentence-level timing metadata**server-side (sent as JSON before each audio chunk) and uses the client's `timeupdate` event to highlight the current segment.

**Two-phase streaming protocol:** 1. Server sends timing metadata line (`TIMING:{json}`) before each audio chunk 2. Client parses timing into an array, concatenates audio chunks 3. During playback, `timeupdate` handler highlights matching text segment

`class SyncedAudioPlayer {
  constructor() {
    this.audio = new Audio();
    this.timingData = [];
    this.textContainer = document.getElementById('text-display');
  }

  async streamAndPlay(text, voice) {
    const response = await fetch(`/api/tts/stream?text=${encodeURIComponent(text)}&voice=${voice}`, {
      headers: { 'Authorization': `Bearer ${this.token}` }
    });
    
    const reader = response.body.getReader();
    const audioChunks = [];
    let buffer = '';
    
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      
      // Parse timing metadata vs audio data
      const text = new TextDecoder().decode(value);
      if (text.startsWith('TIMING:')) {
        const timing = JSON.parse(text.slice(7).split('\n')[0]);
        this.timingData.push(timing);
        this.renderTextSegment(timing.text, this.timingData.length - 1);
      } else {
        audioChunks.push(value);
      }
    }
    
    // Combine chunks and play
    const blob = new Blob(audioChunks, { type: 'audio/mpeg' });
    this.audio.src = URL.createObjectURL(blob);
    this.audio.play();
  }

  setupSyncHighlighting() {
    this.audio.addEventListener('timeupdate', () => {
      const currentTime = this.audio.currentTime;
      this.timingData.forEach((segment, index) => {
        const element = document.getElementById(`segment-${index}`);
        if (currentTime >= segment.start && currentTime < segment.end) {
          element.classList.add('highlighted');
          element.scrollIntoView({ behavior: 'smooth', block: 'center' });
        } else {
          element.classList.remove('highlighted');
        }
      });
    });
  }
}`

**For word-level highlighting** (higher precision), you would need Kokoro to output per-word timestamps. Currently, Kokoro's streaming outputs sentence-level chunks. Two alternatives exist: use forced alignment tools like **Aeneas** post-generation, or implement a hybrid approach where word timing is estimated based on character count within each sentence chunk.

### **Mobile audio playback considerations**

**Android** provides excellent PWA audio support—background playback, lock screen controls via MediaSession API, and reliable streaming all work well. **iOS has significant limitations**: audio stops when the PWA is minimized or screen locks, and autoplay requires a user gesture. Design the UX assuming iOS users must keep the app in the foreground.

**MediaSession API integration** for lock screen controls:

`if ('mediaSession' in navigator) {
  navigator.mediaSession.metadata = new MediaMetadata({
    title: 'Document Reading',
    artist: 'TTS App',
    album: 'Uploaded Document'
  });
  
  navigator.mediaSession.setActionHandler('play', () => audio.play());
  navigator.mediaSession.setActionHandler('pause', () => audio.pause());
  navigator.mediaSession.setActionHandler('seekbackward', () => {
    audio.currentTime = Math.max(audio.currentTime - 10, 0);
  });
  navigator.mediaSession.setActionHandler('seekforward', () => {
    audio.currentTime = Math.min(audio.currentTime + 10, audio.duration);
  });
}`

### **PWA manifest and service worker**

`{
  "name": "Private TTS Reader",
  "short_name": "TTS Reader",
  "start_url": "/",
  "display": "standalone",
  "background_color": "#1a1a1a",
  "theme_color": "#4a90d9",
  "icons": [
    { "src": "/icon-192.png", "sizes": "192x192", "type": "image/png" },
    { "src": "/icon-512.png", "sizes": "512x512", "type": "image/png" }
  ]
}`

The service worker should cache static assets (HTML, CSS, JS) for offline shell loading, but audio content should use network-first strategy since it's dynamically generated. For completed MP3 downloads that users want offline, cache explicitly to IndexedDB (better for large files than Cache API).

### **Mobile-first UI approach**

Use **Tailwind CSS** or a similar utility framework for rapid mobile-first development. Key UI elements: - Large touch targets (minimum 44×44px) for play/pause/seek controls - Full-width scrubbing timeline with visible progress - Text display area with smooth scroll-into-view for highlighted segments - File upload via drag-drop on desktop, file picker on mobile - Paste-text modal for direct text input - Download button for completed MP3 files

---

# **Performance benchmarks and expectations**

| **Hardware** | **TTS Speed** | **First Chunk Latency** |
| --- | --- | --- |
| RTX 4090 | ~210x real-time | ~80ms |
| RTX 3090 Ti | ~90x real-time | ~150ms |
| RTX 4060 Ti | 35-100x real-time | ~200ms |
| 8-core CPU | 3-11x real-time | ~1-3.5s |

For a typical document of 5,000 words (~20 minutes of audio), expect: - **GPU generation time**: 6-35 seconds total - **First audio chunk**: Available within 100-500ms (GPU) - **Final MP3 size**: ~10MB at 64kbps

---

# **Reference implementations worth studying**

Several open-source projects provide valuable patterns:

- **kokoro-web** (github.com/eduardolat/kokoro-web): Svelte-based UI with OpenAI-compatible API, simple API key auth, Docker deployment—excellent starting point for the server structure
- **abogen** (github.com/denizsafak/abogen): 3.9k stars, handles EPUB/PDF/TXT input with word-level synchronized subtitles for English—reference for timing synchronization algorithms
- **Storyteller** (gitlab.com/storyteller-platform): Self-hosted audiobook sync platform with EPUB 3 Media Overlays—reference for synced reader experience architecture

---

# **Implementation roadmap**

**Phase 1 (Core Server - 1 week):** 1. FastAPI skeleton with JWT authentication 2. Kokoro TTS integration with basic streaming endpoint 3. MP3 encoding pipeline with pydub 4. Document upload endpoints (PDF/DOCX extraction)

**Phase 2 (PWA Foundation - 1 week):** 1. Service worker and manifest setup 2. Login screen and token management 3. Basic audio player with streaming fetch 4. File upload and text paste interfaces

**Phase 3 (Synchronized Playback - 1 week):** 1. Timing metadata protocol between server and client 2. Text display component with segment rendering 3. timeupdate-based highlighting synchronization 4. Smooth scroll following current segment

**Phase 4 (Polish - 1 week):** 1. MediaSession API for lock screen controls 2. MP3 download for completed generations 3. Voice selection UI 4. Mobile UI refinement and testing 5. Error handling and loading states

This architecture leverages Kokoro's native streaming capability, proven document extraction libraries, and standard web APIs for audio playback. The result will be a responsive, private TTS reader with real-time audio streaming and synchronized text highlighting optimized for mobile use.