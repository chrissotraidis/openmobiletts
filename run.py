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

import hashlib
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
REQUIREMENTS_LOCK = SERVER_DIR / "requirements.lock"
PACKAGE_LOCK = CLIENT_DIR / "package-lock.json"
BUILD_HASH_FILE = CLIENT_BUILD / ".source-hash"
NODE_MODULES_HASH_FILE = CLIENT_DIR / "node_modules" / ".package-lock-hash"
PROJECT_VENV = ROOT / ".venv"
PYTHON_LOCK_HASH_FILE = ".openmobiletts-requirements-lock-hash"

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
    """Ensure Python 3.10 – 3.12."""
    v = sys.version_info
    if v < (3, 10) or v >= (3, 13):
        _fail(
            f"Python 3.10-3.12 required (found {v.major}.{v.minor}.{v.micro}).\n"
            "  Install a supported version: https://www.python.org/downloads/"
        )
    print(f"  Python {v.major}.{v.minor}.{v.micro}")


def _in_virtual_environment() -> bool:
    """Return true when the launcher is already using an isolated interpreter."""
    return sys.prefix != sys.base_prefix


def _project_venv_python() -> Path:
    """Return the platform-specific interpreter path for the managed venv."""
    if platform.system() == "Windows":
        return PROJECT_VENV / "Scripts" / "python.exe"
    return PROJECT_VENV / "bin" / "python"


def ensure_python_environment() -> None:
    """Create and enter the project venv unless the user activated one."""
    if _in_virtual_environment():
        return

    venv_python = _project_venv_python()
    if not venv_python.is_file():
        print(f"  Creating isolated Python environment at: {PROJECT_VENV}")
        result = _run([sys.executable, "-m", "venv", str(PROJECT_VENV)])
        if result.returncode != 0 or not venv_python.is_file():
            _fail("Could not create the project virtual environment.")

    print("  Using isolated project environment.")
    os.execve(
        str(venv_python),
        [str(venv_python), str(Path(__file__).resolve()), *sys.argv[1:]],
        os.environ.copy(),
    )


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
    """Check for a Node.js release supported by the pinned Vite toolchain."""
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
        if major not in (18, 20) and major < 22:
            print(
                f"  Node.js v{version_str} is outside Vite's supported engine range; "
                "use Node.js 18, 20, or 22+ for reproducible builds."
            )
            return
        print(f"  Node.js v{version_str}")
    except (subprocess.CalledProcessError, ValueError):
        print("  Node.js found (could not parse version)")


# ---------------------------------------------------------------------------
# Setup steps
# ---------------------------------------------------------------------------

def install_pip_deps():
    """Install the complete pinned Python dependency set when needed."""
    dependency_file = REQUIREMENTS_LOCK if REQUIREMENTS_LOCK.exists() else REQUIREMENTS
    dependency_hash = hashlib.sha256(dependency_file.read_bytes()).hexdigest()
    hash_file = Path(sys.prefix) / PYTHON_LOCK_HASH_FILE

    try:
        import kokoro  # noqa: F401
        import fastapi  # noqa: F401
        import uvicorn  # noqa: F401
        import sherpa_onnx  # noqa: F401
        import soundfile  # noqa: F401
        import pydub  # noqa: F401
        import num2words  # noqa: F401
        import pymupdf  # noqa: F401
        import pymupdf4llm  # noqa: F401
        import docx  # noqa: F401
        import reportlab  # noqa: F401
        import dotenv  # noqa: F401
        if hash_file.is_file() and hash_file.read_text(encoding="utf-8").strip() == dependency_hash:
            return
    except ImportError:
        pass

    print("\n  Installing Python dependencies...")
    print(f"  (from {dependency_file})\n")

    command = [sys.executable, "-m", "pip", "install"]
    if dependency_file == REQUIREMENTS_LOCK:
        command.append("--require-hashes")
    command.extend(["-r", str(dependency_file)])
    result = _run(
        command,
    )
    if result.returncode != 0:
        _fail("pip install failed. Check the output above for details.")

    hash_file.write_text(dependency_hash + "\n", encoding="utf-8")

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


def _hash_files(paths: list[Path]) -> str:
    """Return a stable content hash for files under the repository root."""
    digest = hashlib.sha256()
    for path in sorted(paths):
        digest.update(str(path.relative_to(ROOT)).encode("utf-8"))
        digest.update(b"\0")
        digest.update(path.read_bytes())
        digest.update(b"\0")
    return digest.hexdigest()


def _client_source_hash() -> str:
    """Hash every input that can change the static client build."""
    inputs = []
    for directory in (CLIENT_DIR / "src", CLIENT_DIR / "static"):
        if directory.exists():
            inputs.extend(path for path in directory.rglob("*") if path.is_file())
    for name in (
        "package.json",
        "package-lock.json",
        "svelte.config.js",
        "vite.config.js",
        "tailwind.config.js",
        "postcss.config.js",
        "jsconfig.json",
    ):
        path = CLIENT_DIR / name
        if path.is_file():
            inputs.append(path)
    return _hash_files(inputs)


def build_client():
    """Install pinned npm packages and rebuild when client inputs change."""
    source_hash = _client_source_hash()
    if (
        (CLIENT_BUILD / "index.html").is_file()
        and BUILD_HASH_FILE.is_file()
        and BUILD_HASH_FILE.read_text(encoding="utf-8").strip() == source_hash
    ):
        print("  Client build is current.")
        return

    npm = shutil.which("npm")
    if not npm:
        _fail("npm not found. Install Node.js 18+: https://nodejs.org/")

    if not PACKAGE_LOCK.is_file():
        _fail("client/package-lock.json is required for reproducible installs.")

    package_lock_hash = hashlib.sha256(PACKAGE_LOCK.read_bytes()).hexdigest()
    node_modules_current = (
        (CLIENT_DIR / "node_modules").is_dir()
        and NODE_MODULES_HASH_FILE.is_file()
        and NODE_MODULES_HASH_FILE.read_text(encoding="utf-8").strip() == package_lock_hash
    )
    if not node_modules_current:
        print("\n  Installing npm dependencies...")
        print("  (this may take a minute on first run)\n")
        result = _run(["npm", "ci"], cwd=CLIENT_DIR)
        if result.returncode != 0:
            _fail("npm ci failed. Check the output above.")
        NODE_MODULES_HASH_FILE.write_text(package_lock_hash + "\n", encoding="utf-8")

    print("\n  Building client UI...\n")
    result = _run(["npm", "run", "build"], cwd=CLIENT_DIR)
    if result.returncode != 0:
        _fail("Client build failed. Check the output above.")
    BUILD_HASH_FILE.write_text(source_hash + "\n", encoding="utf-8")
    print("\n  Client built successfully.")


def print_first_run_notice():
    """Inform user about model download on first run."""
    hf_home = Path(os.environ.get("HF_HOME", Path.home() / ".cache" / "huggingface")).expanduser()
    hub_cache = Path(
        os.environ.get(
            "HUGGINGFACE_HUB_CACHE",
            os.environ.get("HF_HUB_CACHE", hf_home / "hub"),
        )
    ).expanduser()
    model_cache = hub_cache / "models--hexgrad--Kokoro-82M"
    if model_cache.exists() and any(model_cache.rglob("*.pth")):
        return  # Model already cached

    print()
    print("  " + _bold("First run: Kokoro model will be downloaded (~320 MB)."))
    print("  This is automatic and only happens once.")
    print(f"  Models are cached at: {hub_cache}")
    print()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    check_python_version()
    ensure_python_environment()

    print()
    print("  " + _bold("Open Mobile TTS"))
    print("  " + "-" * 40)
    print()
    print("  Checking dependencies...")
    print()

    # 1. Check system requirements
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
