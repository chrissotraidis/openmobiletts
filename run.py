#!/usr/bin/env python3
"""
Open Mobile TTS — Single-command launcher.

Clone, run, done:
    git clone <repo>
    cd openmobiletts
    python run.py

Checks all dependencies, installs what's needed, builds the UI, and starts
the server at http://localhost:8000
"""

import os
import platform
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).parent
SERVER_DIR = ROOT / "server"
CLIENT_DIR = ROOT / "client"
CLIENT_BUILD = CLIENT_DIR / "build"
REQUIREMENTS = SERVER_DIR / "requirements.txt"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _bold(text: str) -> str:
    """Return text wrapped in ANSI bold if stdout is a terminal."""
    if sys.stdout.isatty():
        return f"\033[1m{text}\033[0m"
    return text


def _fail(message: str):
    """Print an error and exit."""
    print(f"\n  ERROR: {message}\n", file=sys.stderr)
    sys.exit(1)


def _run(cmd, **kwargs):
    """Run a command, streaming output to the terminal."""
    return subprocess.run(cmd, **kwargs)


# ---------------------------------------------------------------------------
# Dependency checks
# ---------------------------------------------------------------------------

def check_python_version():
    """Ensure Python 3.9 – 3.12."""
    v = sys.version_info
    if v < (3, 9) or v >= (3, 13):
        _fail(
            f"Python 3.9-3.12 required (found {v.major}.{v.minor}.{v.micro}).\n"
            "  Install a supported version: https://www.python.org/downloads/"
        )
    print(f"  Python {v.major}.{v.minor}.{v.micro}")


def check_system_deps():
    """Check for espeak-ng and ffmpeg."""
    missing = []

    if not shutil.which("espeak-ng"):
        missing.append("espeak-ng")
    else:
        print("  espeak-ng found")

    if not shutil.which("ffmpeg"):
        missing.append("ffmpeg")
    else:
        print("  ffmpeg found")

    if missing:
        names = " and ".join(missing)
        system = platform.system()
        if system == "Linux":
            hint = f"sudo apt-get install {' '.join(missing)}"
        elif system == "Darwin":
            hint = f"brew install {' '.join(missing)}"
        else:
            hint = f"Install {names} and make sure they are on your PATH"
        _fail(f"{names} not found.\n  Install: {hint}")


def check_node():
    """Ensure Node.js 18+ is installed."""
    node = shutil.which("node")
    if not node:
        _fail(
            "Node.js not found.\n"
            "  Install Node.js 18+: https://nodejs.org/"
        )

    try:
        result = subprocess.run(
            ["node", "--version"], capture_output=True, text=True, check=True
        )
        version_str = result.stdout.strip().lstrip("v")
        major = int(version_str.split(".")[0])
        if major < 18:
            _fail(
                f"Node.js 18+ required (found v{version_str}).\n"
                "  Update: https://nodejs.org/"
            )
        print(f"  Node.js v{version_str}")
    except (subprocess.CalledProcessError, ValueError):
        print("  Node.js found (could not parse version)")


# ---------------------------------------------------------------------------
# Setup steps
# ---------------------------------------------------------------------------

def install_pip_deps():
    """Install Python dependencies from requirements.txt if needed."""
    # Quick check: try importing the heaviest dependency
    try:
        import kokoro  # noqa: F401
        import fastapi  # noqa: F401
        import uvicorn  # noqa: F401
        return  # Already installed
    except ImportError:
        pass

    print("\n  Installing Python dependencies...")
    print(f"  (from {REQUIREMENTS})\n")

    result = _run(
        [sys.executable, "-m", "pip", "install", "-r", str(REQUIREMENTS)],
    )
    if result.returncode != 0:
        _fail("pip install failed. Check the output above for details.")

    print("\n  Python dependencies installed.\n")


def check_sherpa_model():
    """Check if Sherpa-ONNX model is downloaded when that engine is selected."""
    tts_engine = os.environ.get("TTS_ENGINE", "kokoro")
    if tts_engine != "sherpa-onnx":
        return

    model_dir = os.environ.get(
        "SHERPA_MODEL_DIR",
        str(Path.home() / ".cache" / "sherpa-onnx-kokoro" / "kokoro-multi-lang-v1_0"),
    )

    if not Path(model_dir, "model.onnx").exists():
        print()
        print("  " + _bold("Sherpa-ONNX model not found."))
        print(f"  Expected at: {model_dir}")
        print()
        print("  Download it with:")
        print("    python server/setup_sherpa_models.py")
        print()
        _fail("Sherpa-ONNX model required when TTS_ENGINE=sherpa-onnx")

    print(f"  Sherpa-ONNX model found at: {model_dir}")


def build_client():
    """Build the SvelteKit client if not already built."""
    if CLIENT_BUILD.exists() and (CLIENT_BUILD / "index.html").exists():
        print("  Client already built.")
        return

    npm = shutil.which("npm")
    if not npm:
        _fail("npm not found. Install Node.js 18+: https://nodejs.org/")

    if not (CLIENT_DIR / "node_modules").exists():
        print("\n  Installing npm dependencies...")
        print("  (this may take a minute on first run)\n")
        result = _run(["npm", "install"], cwd=CLIENT_DIR)
        if result.returncode != 0:
            _fail("npm install failed. Check the output above.")

    print("\n  Building client UI...\n")
    result = _run(["npm", "run", "build"], cwd=CLIENT_DIR)
    if result.returncode != 0:
        _fail("Client build failed. Check the output above.")
    print("\n  Client built successfully.")


def print_first_run_notice():
    """Inform user about model download on first run."""
    cache_dir = Path.home() / ".cache" / "kokoro"
    if cache_dir.exists() and any(cache_dir.rglob("*.pt")):
        return  # Model already cached

    print()
    print("  " + _bold("First run: Kokoro model will be downloaded (~320 MB)."))
    print("  This is automatic and only happens once.")
    print("  Models are cached at: ~/.cache/kokoro/")
    print()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    print()
    print("  " + _bold("Open Mobile TTS"))
    print("  " + "-" * 40)
    print()
    print("  Checking dependencies...")
    print()

    # 1. Check system requirements
    check_python_version()
    check_system_deps()
    check_node()

    print()
    print("  All system dependencies found.")

    # 2. Install Python packages
    install_pip_deps()

    # 3. Build client
    build_client()

    # 4. Check Sherpa model if needed
    check_sherpa_model()

    # 5. Notify about model download
    print_first_run_notice()

    # 6. Set static dir for the server
    os.environ.setdefault("STATIC_DIR", str(CLIENT_BUILD))

    # 7. Add server to Python path and start
    sys.path.insert(0, str(SERVER_DIR))

    import uvicorn
    from src.config import settings

    print()
    print("  " + _bold(f"Ready at http://localhost:{settings.PORT}"))
    print()

    uvicorn.run(
        "src.main:app",
        host=settings.HOST,
        port=settings.PORT,
        workers=settings.WORKERS,
        log_level="info",
    )


if __name__ == "__main__":
    main()
