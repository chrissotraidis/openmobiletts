# Open Mobile TTS Python Server

FastAPI serves the shared Svelte build and local TTS/STT/document/project API.
Most users should start from the repository root with `python3 run.py`; this
file is for server development and model inspection.

The root launcher creates and re-executes inside the repository-local `.venv`
unless another virtual environment is already active. Direct server-development
commands below assume you have activated an isolated environment yourself.

## Safety boundary

The code now binds `127.0.0.1` by default, limits CORS to local development
origins, redacts content previews in logs, and still has no authentication:

```sh
uvicorn src.main:app --reload --host 127.0.0.1 --port 8000
```

Do not expose the current server to an untrusted LAN or the public internet.
Secured LAN/remote mode requires authentication, TLS, origin restrictions, and
resource limits.

## Requirements

- Python 3.10-3.12 (Kokoro 0.9.4 requires Python 3.10+)
- Node.js 18, 20, or 22+ for the shared client build; Node 21 is outside
  Vite's supported engine range
- espeak-ng
- ffmpeg

```sh
python3 -m pip install --require-hashes -r requirements.lock
```

`requirements.txt` is the human-edited production input. `requirements.lock`
is the universal Python 3.10+ production lock; `requirements-dev.lock` adds the
test toolchain. Both locks include distribution hashes. Regenerate them with:

```sh
uv pip compile requirements.txt --universal --python-version 3.10 --generate-hashes --output-file requirements.lock
uv pip compile requirements-dev.in --universal --python-version 3.10 --generate-hashes --output-file requirements-dev.lock
```

## Models

### Default TTS

`hexgrad/Kokoro-82M` through the Python `kokoro` package. The cache is managed
by Hugging Face, normally under `~/.cache/huggingface/`, not
`~/.cache/kokoro/` as older scripts/docs claim.

Optional pre-download/test:

```sh
python3 setup_models.py
```

### Optional sherpa-onnx TTS

`kokoro-multi-lang-v1_0`: 333.2 MiB archive and 382.2 MiB of measured files.
The product exposes 28 accepted English US/UK speakers from the 53-speaker
package:

```sh
python3 setup_sherpa_models.py
TTS_ENGINE=sherpa-onnx uvicorn src.main:app --host 127.0.0.1 --port 8000
```

The setup script reads the shared catalog and performs exact-size/SHA-256,
safe-path, required-file/directory, native-load, and non-empty-generation
checks before activation.

### STT

The implemented recognizer is
`sherpa-onnx-moonshine-base-en-int8`: Moonshine v1 Base English INT8. It is not
Moonshine v2 Medium.

Settings now uses `POST /api/stt/models/download`. The server downloads the
pinned 239.2 MB archive in a background thread, reports byte progress, checks
its exact size and SHA-256, rejects unsafe archive paths/links, verifies all
required files, smoke-loads the model when sherpa-onnx is available, and
activates it from a staging directory. The desktop installer is intentionally
process-local; Android separately uses foreground WorkManager delivery with
pause/resume. Desktop resume/cancellation and a complete cross-platform model
manager remain future work.

## Main API groups

- `GET /api/health`
- `GET /api/capabilities`
- `GET /api/models/catalog`
- `GET /api/voices`
- `GET /api/engine` and `/api/engines`
- `POST /api/engine/switch`
- `POST /api/tts/stream`
- `POST /api/documents/upload` and `/api/documents/stream`
- `POST /api/stt/transcribe`
- `/api/stt/batch*` job and download routes
- `GET /api/stt/models`
- `POST /api/stt/models/download`
- `POST /api/export/pdf`, `/md`, and `/txt`
- `/api/projects*`
- `/api/logs*`

The TTS endpoint uses POST with a framed binary response. FastAPI transfers
chunks as they are produced, but the current browser client buffers the full
audio Blob before playback, so product copy must not claim genuinely
progressive playback yet.

## Tests

```sh
python3 -m pytest -q
```

At the 2026-08-11 modernization checkpoint, all 58 server tests pass. A clean default
launcher run installed the hashed production lock into `.venv`, started the
server, and initialized real Kokoro voices. A real PDF export also passes from
the isolated locked environment.
Coverage does not yet prove real TTS/STT quality, all parsers, projects,
resource limits, privacy/security, concurrent inference, or parity with Android
NanoHTTPD.

## Current technical debt

- dependency updates still need deliberate lock regeneration and review;
- hosted CI has been defined but not yet observed on GitHub;
- the full Docker image check is pending because Docker Hub metadata requests
  timed out during local verification;
- incomplete cross-platform model-manager features (desktop resume/cancel and
  repair/update/delete controls);
- synchronous inference inside async request flow;
- global mutable engine selection;
- unbounded/ephemeral batch job management;
- extension-first parser validation and inconsistent upload limits;
- no authentication for deliberately enabled LAN mode.

See [the Phase Zero plan](../docs/PHASE_ZERO_MODERNIZATION_PLAN.md) and
[full audit](../docs/TECH_DEBT_AND_MODERNIZATION_AUDIT.md).
