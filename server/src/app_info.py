"""Shared product metadata for the local server and its API responses."""

import os
from pathlib import Path

APP_NAME = "Open Mobile TTS"


def _read_version() -> str:
    """Read the canonical version in a checkout or packaged container layout."""
    if override := os.getenv("APP_VERSION"):
        return override.strip()

    source = Path(__file__).resolve()
    for candidate in (source.parents[2] / "VERSION", source.parents[1] / "VERSION"):
        if candidate.is_file():
            return candidate.read_text(encoding="utf-8").strip()
    raise RuntimeError("Open Mobile TTS VERSION file is missing")


APP_VERSION = _read_version()
