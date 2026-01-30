# Open Mobile TTS

A private, self-hosted text-to-speech platform optimized for mobile devices and lightweight server deployment.

## Features

- **High-Quality TTS**: Powered by Kokoro TTS (82M parameters, #1 HuggingFace TTS Arena)
- **Real-Time Streaming**: Progressive audio generation and playback
- **Synchronized Text**: Sentence-level highlighting that follows audio playback
- **Document Support**: Upload and convert PDF/DOCX files to speech
- **Mobile-Optimized**: Progressive Web App with offline capabilities
- **Lightweight**: 64kbps MP3 encoding, efficient model (~2-3GB VRAM)
- **Private**: Single-user JWT authentication, self-hosted deployment

## Architecture

The system consists of two main components:

### Backend Server (`/server`)
- FastAPI application with streaming endpoints
- Kokoro TTS engine (Apache 2.0 licensed)
- Document processing (PDF via pymupdf4llm, DOCX via python-docx)
- JWT authentication with Argon2 password hashing
- MP3 encoding optimized for speech (64kbps CBR, 22050Hz mono)

### PWA Client (`/client`)
- Progressive Web App for mobile and desktop
- Audio player with synchronized text highlighting
- Service worker for offline capabilities
- MediaSession API for lock screen controls
- Mobile-first responsive design

## Quick Start

### Prerequisites

**Server:**
- Python 3.9-3.12 (not 3.13+)
- GPU with 2-3GB VRAM (or CPU with degraded performance)
- Linux: `apt-get install espeak-ng`

**Client:**
- Node.js 18+ and npm/yarn/pnpm

### Installation

1. **Clone the repository:**
   ```bash
   git clone https://github.com/yourusername/openmobiletts.git
   cd openmobiletts
   ```

2. **Set up the backend:**
   ```bash
   cd server
   python -m venv venv
   source venv/bin/activate  # On Windows: venv\Scripts\activate
   pip install -r requirements.txt
   ```

3. **Configure environment variables:**
   ```bash
   # Generate JWT secret
   openssl rand -hex 32

   # Create .env file
   cat > .env << EOF
   JWT_SECRET=your_generated_secret_here
   ADMIN_USERNAME=admin
   ADMIN_PASSWORD_HASH=your_argon2_hash_here
   EOF
   ```

4. **Set up the client:**
   ```bash
   cd ../client
   npm install
   ```

### Running Locally

**Start the backend:**
```bash
cd server
source venv/bin/activate
uvicorn src.main:app --reload --host 0.0.0.0 --port 8000
```

**Start the client:**
```bash
cd client
npm run dev
```

Visit `http://localhost:3000` (or your client dev server port) to access the application.

## Performance

| Hardware | TTS Speed | First Chunk Latency |
|----------|-----------|-------------------|
| RTX 4090 | ~210x real-time | ~80ms |
| RTX 3090 Ti | ~90x real-time | ~150ms |
| RTX 4060 Ti | 35-100x real-time | ~200ms |
| 8-core CPU | 3-11x real-time | ~1-3.5s |

For a 5,000-word document (~20 minutes audio):
- **GPU generation time**: 6-35 seconds
- **First audio chunk**: <500ms
- **Final MP3 size**: ~10MB at 64kbps

## Supported Languages & Voices

Kokoro TTS supports **54 voices** across **8 languages**:
- American & British English
- Spanish
- French
- Hindi
- Italian
- Japanese
- Portuguese
- Mandarin Chinese

## Documentation

### Getting Started
- **[How It Works](docs/HOW_IT_WORKS.md)** - Complete explanation of the system architecture, model, and data flow
- **[Limits & Constraints](docs/LIMITS_AND_CONSTRAINTS.md)** - Detailed breakdown of all limits, performance, and how to overcome them

### Technical Details
- **[Technical Architecture](docs/technical-architecture.md)** - Detailed implementation patterns and design decisions
- **[Implementation Status](docs/implementation-status.md)** - Current progress and planned features
- **[Testing Summary](docs/testing-summary.md)** - Test coverage and results

### Component Documentation
- **Server Documentation**: See `server/README.md`
- **Client Documentation**: See `client/README.md`
- **Development Guidelines**: See `claude.md` for development instructions

### Key Questions Answered
- **Is the model local?** Yes, 100% local. See [How It Works](docs/HOW_IT_WORKS.md#is-the-model-running-locally)
- **What are the limits?** See [Limits & Constraints](docs/LIMITS_AND_CONSTRAINTS.md#quick-reference)
- **How fast is it?** See [Limits & Constraints](docs/LIMITS_AND_CONSTRAINTS.md#2-processing-speed)

## Deployment

### Docker (Recommended)

```bash
cd server
docker build -t openmobiletts-server .
docker run -d -p 8000:8000 \
  -e JWT_SECRET=your_secret \
  -e ADMIN_USERNAME=admin \
  -e ADMIN_PASSWORD_HASH=your_hash \
  openmobiletts-server
```

### VPS Deployment

1. Deploy server on VPS with GPU support (e.g., AWS g4dn.xlarge, Paperspace)
2. Configure HTTPS with Let's Encrypt / Caddy / Nginx
3. Build and deploy PWA client to static hosting (Vercel, Netlify, Cloudflare Pages)
4. Configure PWA to connect to your server API endpoint

## Mobile Support

### Android
- ✅ Full PWA support
- ✅ Background audio playback
- ✅ Lock screen controls via MediaSession API
- ✅ Add to home screen
- ✅ Offline capabilities

### iOS
- ✅ Add to home screen
- ✅ Basic audio playback
- ⚠️ Audio stops when app is minimized or screen locks
- ⚠️ No background audio support (iOS PWA limitation)

**Recommendation**: Design UX to inform iOS users to keep app in foreground during playback.

## License

Apache 2.0 - See LICENSE file for details.

Kokoro TTS is licensed under Apache 2.0 and permits commercial use.

## Contributing

This is a private, self-hosted project. Contributions welcome via pull requests.

## Acknowledgments

- **Kokoro TTS** - High-quality, lightweight TTS model
- **FastAPI** - Modern Python web framework
- **pymupdf4llm** - Excellent PDF text extraction

## Support

For issues, questions, or feature requests, please open an issue on GitHub.

---

**Status**: 🚧 In active development - repository structure initialized
