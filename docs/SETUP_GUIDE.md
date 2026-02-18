# Open Mobile TTS - Setup Guide

This guide walks you through setting up Open Mobile TTS from a fresh clone.

## What You're Setting Up

A **private, local** text-to-speech app. One command runs everything — no cloud, no API keys, no accounts.

## Prerequisites

| Requirement | Version | Install |
|---|---|---|
| Python | 3.9 – 3.12 | [python.org](https://www.python.org/downloads/) |
| Node.js | 18+ | [nodejs.org](https://nodejs.org/) |
| espeak-ng | any | `sudo apt install espeak-ng` / `brew install espeak-ng` |
| ffmpeg | any | `sudo apt install ffmpeg` / `brew install ffmpeg` |

## Step 1: Clone and Run

```bash
git clone <repo-url>
cd openmobiletts
python run.py
```

That's it. `run.py` will:
1. Check Python, Node.js, espeak-ng, and ffmpeg are installed
2. Install Python dependencies from `server/requirements.txt`
3. Install npm dependencies and build the SvelteKit client
4. Download the Kokoro TTS model (~320MB, first run only)
5. Start the app at **http://localhost:8000**

## Step 2: Use the App

1. Open http://localhost:8000
2. Enter text or upload a document (PDF, DOCX, TXT)
3. Select a voice and speed
4. Click "Generate"
5. Audio streams as it generates — listen while it processes

## Alternative: Android

Run the same app on your Android phone. The server stays on your computer; the phone connects over WiFi.

### Prerequisites
- Android Studio (latest stable)
- Android device or emulator (API 22+)
- Both devices on the same WiFi network

### Setup
```bash
# 1. Start the server on your computer
python run.py

# 2. Build the Android project
cd client
npm run build:android

# 3. Open client/android/ in Android Studio → Run on your device

# 4. In the Android app: Settings → Server Connection
#    Enter http://<your-computer-ip>:8000 → Test Connection
```

See [ANDROID_APP_GUIDE.md](ANDROID_APP_GUIDE.md) for full details.

## Alternative: Docker

If you prefer not to install Python/Node locally:

```bash
docker compose up --build
# Opens at http://localhost:8000
```

The Docker image handles all dependencies. A named volume persists the model cache across container restarts.

## Configuration (Optional)

Copy `server/.env.example` to `server/.env` to customize settings. All settings have sensible defaults — **no `.env` file is required** for basic usage.

Key settings you might want to change:

| Setting | Default | Purpose |
|---------|---------|---------|
| `PORT` | 8000 | Server port |
| `DEFAULT_VOICE` | af_heart | Default TTS voice |
| `MAX_UPLOAD_SIZE_MB` | 10 | Max document upload size |

## Development Mode

For hot-reload during development, run the server and client separately:

```bash
# Terminal 1: API server with auto-reload
cd server && pip install -r requirements.txt
uvicorn src.main:app --reload --port 8000

# Terminal 2: Vite dev server (proxies /api/* to port 8000)
cd client && npm install && npm run dev
```

In dev mode, the Vite dev server runs on port 5173 and proxies API requests to port 8000.

## Troubleshooting

### `run.py` reports missing dependencies
Follow the instructions it prints. Common fixes:
- **espeak-ng**: `brew install espeak-ng` (Mac) or `sudo apt install espeak-ng` (Linux)
- **ffmpeg**: `brew install ffmpeg` (Mac) or `sudo apt install ffmpeg` (Linux)
- **Python version**: Install Python 3.9-3.12 from [python.org](https://www.python.org/downloads/)

### npm install fails
- Try `sudo chown -R $(whoami) ~/.npm` to fix permissions
- Ensure Node.js 18+ is installed: `node --version`

### Audio doesn't play
- Check browser console (F12) for errors
- Try a different browser (Chrome recommended)
- Try a shorter text first to verify the setup

### Generation is very slow
- Normal for CPU: 2-5x real-time speed
- 200 words ≈ ~10 seconds to generate
- For faster generation, use a machine with a GPU

## Deployment (Optional)

### Docker on a VPS

```bash
docker compose up --build -d
```

Configure a reverse proxy (Caddy, Nginx) for HTTPS if exposing publicly.

### Direct Deployment

```bash
python run.py
# Or run directly:
cd server && STATIC_DIR=../client/build uvicorn src.main:app --host 0.0.0.0 --port 8000
```

## Success Checklist

You've set up Open Mobile TTS when:

- [ ] `python run.py` starts without errors
- [ ] http://localhost:8000 loads the app
- [ ] Text-to-speech generation works
- [ ] Audio plays in the browser

**Your private TTS system is ready to use! 🎉**
