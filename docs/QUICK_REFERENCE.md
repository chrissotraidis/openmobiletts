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
| Concurrent requests | 1 | Single-user setup |
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

| Your Setup | Text | Time | Audio Length |
|------------|------|------|--------------|
| CPU (your Mac) | 200 words | ~10 sec | ~80 seconds |
| CPU (your Mac) | 1,000 words | ~1 min | ~7 minutes |
| GPU (if you had one) | 1,000 words | ~5 sec | ~7 minutes |

## Common Commands

### Start Everything
```bash
# Terminal 1 - Server
cd server
python -m uvicorn src.main:app --reload --port 8000

# Terminal 2 - Client
cd client
npm run dev
```

### Check Model
```bash
cd server
python setup_models.py
```

### View Logs
```bash
tail -f /tmp/server.log
tail -f /tmp/client.log
```

### Clear Audio Cache
Open browser DevTools (F12) → Application → IndexedDB → Delete `openmobiletts`

## Key Endpoints

| URL | What |
|-----|------|
| http://localhost:5173 | Client app |
| http://localhost:8000/health | Server health check |
| http://localhost:8000/docs | API documentation |
| http://localhost:8000/status | Debug GUI |

## Default Credentials

```
Username: admin
Password: testpassword123
```

⚠️ **Change these in production!**

## File Locations

```
openmobiletts/
├── server/               Server code
│   ├── src/main.py      API endpoints
│   ├── src/tts_engine.py   Kokoro wrapper
│   └── tests/           24 automated tests
├── client/              Client code
│   ├── src/routes/      Pages
│   ├── src/lib/         Components
│   └── static/          PWA assets
└── docs/                Documentation
    ├── HOW_IT_WORKS.md     ← Full explanation
    └── LIMITS_AND_CONSTRAINTS.md  ← All limits
```

## Troubleshooting

### Server won't start
```bash
# Install dependencies
cd server
pip install -r requirements.txt

# Download model
python setup_models.py
```

### Client shows white screen
- Hard refresh: Ctrl+Shift+R (or Cmd+Shift+R)
- Clear cache: DevTools → Application → Clear site data

### Audio won't play
- Check console (F12) for errors
- Try shorter text first
- Check server logs: `tail -f /tmp/server.log`

### Generation is slow
- Normal for CPU (2-5x real-time)
- Consider using GPU for 10-50x speedup
- Or process in smaller chunks

## Need More Info?

| Question | Read This |
|----------|-----------|
| How does it work? | [HOW_IT_WORKS.md](HOW_IT_WORKS.md) |
| What are ALL the limits? | [LIMITS_AND_CONSTRAINTS.md](LIMITS_AND_CONSTRAINTS.md) |
| Technical architecture? | [technical-architecture.md](technical-architecture.md) |
| What's implemented? | [implementation-status.md](implementation-status.md) |
| How are tests doing? | [testing-summary.md](testing-summary.md) |

## One-Minute Overview

1. **Backend**: FastAPI server running Kokoro TTS (82M model) locally
2. **Frontend**: SvelteKit PWA with audio player and synchronized text
3. **Model**: Downloads once to `~/.cache/kokoro/`, runs on your CPU/GPU
4. **Privacy**: Everything local, no external APIs, no data leaves your machine
5. **Limits**: No text length limit, speed depends on your hardware
6. **Cost**: Free (just electricity), no API costs
7. **Quality**: State-of-the-art neural TTS, sounds natural

## System Requirements

**Minimum:**
- Python 3.9+
- 4GB RAM
- 2GB disk space
- Any CPU

**Recommended:**
- Python 3.11
- 8GB RAM
- GPU with 2GB+ VRAM
- SSD storage

**Current Setup (Your Mac):**
- ✅ M1/M2 CPU (good performance)
- ✅ 8GB+ RAM (sufficient)
- ✅ Fast SSD (helps with model loading)
- ⚠️ No dedicated GPU (CPU mode, slower but works)
