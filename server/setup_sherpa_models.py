#!/usr/bin/env python3
"""
Setup script to download Sherpa-ONNX Kokoro TTS models.

Downloads the kokoro-multi-lang-v1_0 model files (~350 MB) to
~/.cache/sherpa-onnx-kokoro/ for offline use.
"""

import os
import sys
import shutil
import tarfile
import tempfile
import urllib.request
from pathlib import Path

MODEL_NAME = "kokoro-multi-lang-v1_0"
MODEL_URL = f"https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/{MODEL_NAME}.tar.bz2"
CACHE_DIR = Path.home() / ".cache" / "sherpa-onnx-kokoro"


def download_with_progress(url: str, dest: Path) -> None:
    """Download a file with progress reporting."""
    print(f"  Downloading from: {url}")
    print(f"  Saving to: {dest}")

    response = urllib.request.urlopen(url)
    total_size = int(response.headers.get("Content-Length", 0))
    downloaded = 0
    block_size = 8192

    with open(dest, "wb") as f:
        while True:
            data = response.read(block_size)
            if not data:
                break
            f.write(data)
            downloaded += len(data)
            if total_size > 0:
                pct = downloaded * 100 / total_size
                mb = downloaded / (1024 * 1024)
                total_mb = total_size / (1024 * 1024)
                print(f"\r  {mb:.1f} / {total_mb:.1f} MB ({pct:.0f}%)", end="", flush=True)

    print()


def setup_sherpa_models():
    """Download and extract Sherpa-ONNX Kokoro model."""
    print("Setting up Sherpa-ONNX Kokoro TTS models...")
    print("=" * 60)

    model_dir = CACHE_DIR / MODEL_NAME

    # Check if already downloaded
    if (model_dir / "model.onnx").exists():
        print(f"\n  Model already exists at: {model_dir}")
        total_size = sum(f.stat().st_size for f in model_dir.rglob("*") if f.is_file())
        print(f"  Total size: {total_size / 1024 / 1024:.1f} MB")
        print("\n  To re-download, delete the directory and run again.")
        return True

    CACHE_DIR.mkdir(parents=True, exist_ok=True)

    # Download tarball to temp file
    print(f"\n  Downloading {MODEL_NAME} (~350 MB)...")
    print("  This only happens once.\n")

    with tempfile.NamedTemporaryFile(suffix=".tar.bz2", delete=False) as tmp:
        tmp_path = Path(tmp.name)

    try:
        download_with_progress(MODEL_URL, tmp_path)

        # Extract
        print("\n  Extracting model files...")
        with tarfile.open(tmp_path, "r:bz2") as tar:
            tar.extractall(path=CACHE_DIR)

        # Verify extraction
        if not (model_dir / "model.onnx").exists():
            print(f"\n  ERROR: model.onnx not found in {model_dir}")
            print("  The archive may have a different structure.")
            return False

        total_size = sum(f.stat().st_size for f in model_dir.rglob("*") if f.is_file())
        print(f"\n  Model extracted to: {model_dir}")
        print(f"  Total size: {total_size / 1024 / 1024:.1f} MB")

    finally:
        tmp_path.unlink(missing_ok=True)

    # Quick verification with sherpa-onnx
    try:
        import sherpa_onnx

        print("\n  Verifying model loads correctly...")

        # Build lexicon path — multi-lang models require it
        lexicon = str(model_dir / "lexicon-us-en.txt")
        if not (model_dir / "lexicon-us-en.txt").exists():
            lexicon = ""

        # Build rule FSTs if present
        fst_files = sorted(model_dir.glob("*.fst"))
        rule_fsts = ",".join(str(f) for f in fst_files) if fst_files else ""

        # Build dict dir if present
        dict_dir = str(model_dir / "dict") if (model_dir / "dict").exists() else ""

        config = sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
                    model=str(model_dir / "model.onnx"),
                    voices=str(model_dir / "voices.bin"),
                    tokens=str(model_dir / "tokens.txt"),
                    data_dir=str(model_dir / "espeak-ng-data"),
                    lexicon=lexicon,
                    dict_dir=dict_dir,
                ),
                num_threads=2,
            ),
            rule_fsts=rule_fsts,
        )
        tts = sherpa_onnx.OfflineTts(config)
        audio = tts.generate("Hello", sid=3, speed=1.0)
        if audio.samples and len(audio.samples) > 0:
            print("  Model verified — audio generation works!")
        else:
            print("  WARNING: Model loaded but generated empty audio.")

    except ImportError:
        print("\n  sherpa-onnx not installed — skipping verification.")
        print("  Install with: pip install sherpa-onnx")
    except Exception as e:
        print(f"\n  WARNING: Verification failed: {e}")
        print("  Model files are downloaded — the error may resolve after installing sherpa-onnx.")

    print("\n" + "=" * 60)
    print("Sherpa-ONNX Kokoro TTS setup complete!")
    print(f"\nModel location: {model_dir}")
    print("\nTo use with Open Mobile TTS, set:")
    print('  TTS_ENGINE=sherpa-onnx')

    return True


if __name__ == "__main__":
    print("\nOpen Mobile TTS - Sherpa-ONNX Model Setup\n")

    if sys.version_info < (3, 9):
        print("ERROR: Python 3.9+ required")
        print(f"  Current version: {sys.version}")
        sys.exit(1)

    success = setup_sherpa_models()
    sys.exit(0 if success else 1)
