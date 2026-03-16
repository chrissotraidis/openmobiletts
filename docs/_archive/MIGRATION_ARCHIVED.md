**ARCHIVED 3/16/26 — This document is for historical reference only. It describes the v0.1→v0.2 migration (removing auth). The "Future: Native Android" section references Jetpack Compose which was not adopted. See `docs/android-app/android-app-overview.md` for the actual Android architecture.**

---

# Migration: Client-Server to Single App

## What Changed

Open Mobile TTS was redesigned from a **two-process client-server architecture** into a **single self-contained application**.

### Before (v0.1)

```
┌──────────────────┐         ┌──────────────────────────┐
│  SvelteKit PWA   │  HTTP   │  FastAPI Server           │
│  (port 5173)     │◄───────►│  (port 8000)              │
│                  │  CORS   │  JWT auth, Kokoro TTS     │
│  Static site     │         │  Docker deployment        │
└──────────────────┘         └──────────────────────────┘
```

- Two separate processes (client dev server + API server)
- JWT authentication with Argon2 password hashing
- CORS configuration required
- Docker deployment with separate containers
- Environment variables: JWT_SECRET, ADMIN_USERNAME, ADMIN_PASSWORD_HASH

### After (v0.2)

```
┌─────────────────────────────────────────┐
│  python run.py                          │
│                                         │
│  FastAPI serves both:                   │
│   /api/*  → TTS endpoints              │
│   /*      → Built SvelteKit UI         │
│                                         │
│  No auth. No CORS. Single process.      │
└─────────────────────────────────────────┘
```

- Single `python run.py` command starts everything
- No authentication (local-only app, no multi-user)
- No CORS (same origin — UI served by the same process)
- No Docker required (can still be used, but not needed)
- Minimal environment variables (just TTS settings)

## Why

1. **Simplicity** — This is a personal tool. Auth and multi-user features add complexity without value for a single-user local app.

2. **Mobile-first** — The app is designed to run locally on a machine the user controls. When accessed from a phone on the same network, it's already behind the network boundary.

3. **Path to native** — The long-term plan is to port this to a native Android app using Sherpa-ONNX for on-device TTS. Stripping the server complexity now makes the architecture closer to that goal.

4. **Fewer failure modes** — No token expiration, no CORS errors, no password management, no secret rotation.

## What Was Removed

| Component | Status |
|---|---|
| `server/src/auth.py` | **Deleted** — JWT + Argon2 auth |
| Login page (`client/src/routes/login/`) | **Deleted** — No auth needed |
| Player page (`client/src/routes/player/`) | **Merged** into root page |
| CORS middleware | **Removed** — Same origin |
| `settings.validate()` | **Removed** — No required secrets |
| JWT dependencies (`python-jose`, `pwdlib`) | **Removed** from requirements.txt |
| Docker-specific config | **Simplified** |

## What Was Added

| Component | Purpose |
|---|---|
| `run.py` | Single entry point — builds client + starts server |
| `client/src/lib/stores/player.js` | Audio playback state + streaming protocol parser |
| `client/src/lib/stores/settings.js` | Persisted user preferences (localStorage) |
| `client/src/lib/stores/history.js` | Recent generation history (localStorage) |
| `client/src/lib/services/api.js` | Fetch wrapper for TTS API (no auth headers) |
| `client/src/lib/components/TextInput.svelte` | Text input + file upload UI |
| `client/src/lib/components/TextDisplay.svelte` | Sentence-level text highlighting during playback |
| `client/src/lib/components/AudioPlayer.svelte` | Bottom player bar with controls |
| `client/src/lib/components/AudioHistory.svelte` | History list with replay |
| Static file serving in `main.py` | SPA fallback serving from FastAPI |
| Vite proxy config | Dev server proxies `/api/*` to Python backend |

## How to Run

```bash
# One command:
python run.py

# Opens at http://localhost:8000
```

For development (hot-reload on client changes):

```bash
# Terminal 1: Start the API server
cd server && uvicorn src.main:app --reload --port 8000

# Terminal 2: Start the Vite dev server (proxies /api/* to port 8000)
cd client && npm run dev
```

## Future: Native Android

The implementation plan (`docs/IMPLEMENTATION_PLAN.md`) details the next phase: porting to a native Android app using:

- **Sherpa-ONNX** for on-device TTS inference
- **Kokoro INT8** model (~100 MB, runs on Pixel 9 Pro's Tensor G4)
- **Jetpack Compose** for the UI
- **AudioTrack** for streaming playback

The single-app web version serves as the functional prototype. The UI design, text preprocessing logic, and user flows transfer directly to the native app.
