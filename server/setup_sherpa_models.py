#!/usr/bin/env python3
"""Install the shared-catalog sherpa Kokoro model with full verification."""

from __future__ import annotations

import sys
import time
from pathlib import Path

SERVER_DIR = Path(__file__).resolve().parent
if str(SERVER_DIR) not in sys.path:
    sys.path.insert(0, str(SERVER_DIR))

from src.model_catalog import NATIVE_TTS_MODEL, is_model_complete  # noqa: E402
from src.model_installer import ModelInstaller  # noqa: E402


CACHE_ROOT = Path.home() / ".cache" / "sherpa-onnx-kokoro"


def validate_model(model_dir: Path) -> None:
    """Load the staged model and require non-empty generated audio."""
    import sherpa_onnx

    lexicons = [
        str(model_dir / name)
        for name in ("lexicon-us-en.txt", "lexicon-gb-en.txt")
        if (model_dir / name).is_file()
    ]
    fsts = sorted(model_dir.glob("*.fst"))
    config = sherpa_onnx.OfflineTtsConfig(
        model=sherpa_onnx.OfflineTtsModelConfig(
            kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
                model=str(model_dir / "model.onnx"),
                voices=str(model_dir / "voices.bin"),
                tokens=str(model_dir / "tokens.txt"),
                data_dir=str(model_dir / "espeak-ng-data"),
                lexicon=",".join(lexicons),
                dict_dir=str(model_dir / "dict"),
            ),
            num_threads=2,
        ),
        rule_fsts=",".join(str(path) for path in fsts),
    )
    engine = sherpa_onnx.OfflineTts(config)
    try:
        audio = engine.generate("Model ready.", sid=3, speed=1.0)
        if not audio.samples:
            raise RuntimeError("Model generated no audio")
    finally:
        del engine


def setup_sherpa_models() -> bool:
    spec = NATIVE_TTS_MODEL
    print(f"Installing {spec.label}")
    print(f"  Archive: {spec.archive_size_mb:.1f} MiB")
    print(f"  Destination: {spec.install_dir(CACHE_ROOT)}")
    print("  Verification: size, SHA-256, safe paths, required files, native generation")

    installer = ModelInstaller(CACHE_ROOT)
    already_installed = is_model_complete(spec.install_dir(CACHE_ROOT), spec)
    installer.start(spec, validate=validate_model)
    last_line = ""
    while True:
        state = installer.snapshot(spec)
        status = state["status"]
        if status == "downloading":
            line = f"  Downloading: {state['downloaded_bytes'] / 1024 / 1024:.1f} / {spec.archive_size_mb:.1f} MiB"
        elif status in {"verifying", "activating"}:
            line = f"  {status.title()}..."
        else:
            line = ""
        if line and line != last_line:
            print(f"\r{line}", end="", flush=True)
            last_line = line
        if status in {"installed", "error"}:
            if last_line:
                print()
            if status == "error":
                print(f"Installation failed: {state['error']}", file=sys.stderr)
                return False
            if already_installed:
                validate_model(spec.install_dir(CACHE_ROOT))
            print("Model verified and ready.")
            return True
        time.sleep(0.25)


if __name__ == "__main__":
    sys.exit(0 if setup_sherpa_models() else 1)
