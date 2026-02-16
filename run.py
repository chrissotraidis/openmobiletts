#!/usr/bin/env python3
"""
Open Mobile TTS — Single-app launcher.

Starts the TTS server which also serves the web UI.
Run:  python run.py
Then open: http://localhost:8000
"""

import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).parent
SERVER_DIR = ROOT / "server"
CLIENT_DIR = ROOT / "client"
CLIENT_BUILD = CLIENT_DIR / "build"


def build_client():
    """Build the SvelteKit client if not already built."""
    if CLIENT_BUILD.exists() and (CLIENT_BUILD / "index.html").exists():
        return True

    print("Building client...")
    if not (CLIENT_DIR / "node_modules").exists():
        print("  Installing npm dependencies...")
        subprocess.run(["npm", "install"], cwd=CLIENT_DIR, check=True)

    subprocess.run(["npm", "run", "build"], cwd=CLIENT_DIR, check=True)
    print("  Client built successfully.")
    return True


def main():
    # Build client if needed
    try:
        build_client()
    except (subprocess.CalledProcessError, FileNotFoundError) as e:
        print(f"Warning: Could not build client ({e}). Server will start without UI.")
        print("  To fix: cd client && npm install && npm run build")

    # Set static dir for the server
    os.environ.setdefault("STATIC_DIR", str(CLIENT_BUILD))

    # Add server to Python path
    sys.path.insert(0, str(SERVER_DIR))

    # Start uvicorn
    import uvicorn
    from src.config import settings

    print(f"\n  Open Mobile TTS")
    print(f"  http://localhost:{settings.PORT}\n")

    uvicorn.run(
        "src.main:app",
        host=settings.HOST,
        port=settings.PORT,
        workers=settings.WORKERS,
        log_level="info",
    )


if __name__ == "__main__":
    main()
