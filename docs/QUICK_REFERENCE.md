# Quick Reference Guide

## Is This Using an API?

**NO.** Everything runs locally on your machine:
- ✅ Model runs on your CPU/GPU
- ✅ No data sent to external servers
- ✅ Works completely offline (after initial model download)
- ✅ 100% private

## Does It Have Limits?

**No hard text length limit.** Practical limits:

| What | Limit | Why |
|------|-------|-----|
| Text length | Unlimited | Auto-chunked into 250-token pieces |
| Generation speed | 2-5x real-time (CPU) | Your hardware speed |
| Voices | 11 built-in | More can be trained |
| Concurrent requests | 1 | Single-process setup |
| File uploads | 10MB default | Configurable via `MAX_UPLOAD_SIZE_MB` env var |
| Storage | Your disk space | Audio cached in browser |

## Where Is the Model?

```
~/.cache/kokoro/          (~320MB)
├── kokoro-v0_19.pth      (Model weights)
├── voices/               (Voice embeddings)
└── config.json           (Configuration)
```

**First run**: Downloads from HuggingFace (one-time)
**After that**: Uses local cached model

## How Fast Is It?

| Setup | Text | Time | Audio Length |
|-------|------|------|--------------|
| CPU (Mac) | 200 words | ~10 sec | ~80 seconds |
| CPU (Mac) | 1,000 words | ~1 min | ~7 minutes |
| GPU | 1,000 words | ~5 sec | ~7 minutes |

## Common Commands

### Start the App
```bash
python run.py
# Opens at http://localhost:8000
```

### Start with Docker
```bash
docker compose up --build
# Opens at http://localhost:8000
```

### Development Mode (Hot Reload)
```bash
# Terminal 1: API server with auto-reload
cd server && pip install -r requirements.txt
uvicorn src.main:app --reload --port 8000

# Terminal 2: Vite dev server (proxies /api/* to port 8000)
cd client && npm install && npm run dev
```

### Build for Android
```bash
./android/copy-webapp.sh    # Builds SvelteKit + copies to Android assets
# Open android/ in Android Studio → Build → Run on device
```

### Check Model
```bash
cd server
python setup_models.py
```

### Clear Audio Cache
Open browser DevTools (F12) → Application → IndexedDB → Delete `openmobiletts`

## Key URLs

| URL | What |
|-----|------|
| http://localhost:8000 | App (UI + API) |
| http://localhost:8000/health | Health check |
| http://localhost:8000/docs | API documentation |

## Configuration

Copy `server/.env.example` to `server/.env` to customize. All settings have sensible defaults — no `.env` file is required.

Key settings:

| Setting | Default | Purpose |
|---------|---------|---------|
| `PORT` | 8000 | Server port |
| `DEFAULT_VOICE` | af_heart | Default TTS voice |
| `DEFAULT_SPEED` | 1.0 | Playback speed |
| `MAX_UPLOAD_SIZE_MB` | 10 | Max document upload size |
| `MAX_CHUNK_TOKENS` | 250 | Tokens per TTS chunk |
| `MP3_BITRATE` | 64k | Audio quality |

## File Locations

```
openmobiletts/
├── run.py                Single-command launcher
├── server/               FastAPI + Kokoro TTS
│   ├── src/main.py       API endpoints + CORS + static file serving
│   ├── src/tts_engine.py Kokoro wrapper
│   └── tests/            Automated tests
├── client/               SvelteKit UI
│   ├── src/routes/       Pages (single root page)
│   ├── src/lib/          Components, stores, services
│   └── static/           PWA assets
├── android/              Android app (WebView + Sherpa-ONNX)
└── docs/                 Documentation
```

## Troubleshooting

### App won't start
```bash
# run.py checks dependencies and tells you what's missing
python run.py
```

### Client shows white screen
- Hard refresh: Ctrl+Shift+R (or Cmd+Shift+R)
- Clear cache: DevTools → Application → Clear site data

### Audio won't play
- Check console (F12) for errors
- Try shorter text first
- Restart the app: `python run.py`

### Generation is slow
- Normal for CPU (2-5x real-time)
- Consider using GPU for 10-50x speedup

## One-Minute Overview

1. **Single app**: `python run.py` builds the UI and starts the server
2. **Model**: Kokoro TTS (82M params), downloads once to `~/.cache/kokoro/`
3. **Privacy**: 100% local, no external APIs, no data leaves your machine
4. **No auth**: No login, no passwords — designed for local/private use
5. **Limits**: No text length limit, speed depends on your hardware
6. **Cost**: Free (just electricity)

## Need More Info?

| Question | Read This |
|----------|-----------|
| How does it work? | [HOW_IT_WORKS.md](HOW_IT_WORKS.md) |
| What are ALL the limits? | [LIMITS_AND_CONSTRAINTS.md](LIMITS_AND_CONSTRAINTS.md) |
| Technical architecture? | [technical-architecture.md](technical-architecture.md) |
| What's implemented? | [implementation-status.md](implementation-status.md) |
| How are tests doing? | [testing-summary.md](testing-summary.md) |
| Run on Android? | [ANDROID_ARCHITECTURE.md](ANDROID_ARCHITECTURE.md) |
