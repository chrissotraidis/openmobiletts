#!/usr/bin/env python3
"""
Setup script to download Kokoro TTS models.

This downloads the Kokoro model files to the local cache directory
so they're available offline and don't need to be downloaded on first use.
"""

import os
import sys
from pathlib import Path

def setup_kokoro_models():
    """Download Kokoro TTS models."""
    print("🔧 Setting up Kokoro TTS models...")
    print("=" * 60)

    try:
        from kokoro import KPipeline
        import torch

        # Check if CUDA is available
        if torch.cuda.is_available():
            print(f"✅ CUDA available: {torch.cuda.get_device_name(0)}")
        else:
            print("ℹ️  CUDA not available, will use CPU")

        # Initialize pipeline - this downloads models
        print("\n📥 Downloading Kokoro models (this may take a few minutes)...")
        try:
            from huggingface_hub.constants import HF_HUB_CACHE
            cache_dir = Path(HF_HUB_CACHE)
        except Exception:
            cache_dir = Path.home() / ".cache" / "huggingface" / "hub"
        print(f"   Models will be cached under: {cache_dir}")

        pipeline = KPipeline(lang_code='a')  # American English

        # Test generation to ensure everything works
        print("\n🧪 Testing TTS generation...")
        test_text = "Hello, this is a test."

        # Generate a small test to verify model works
        audio_chunks = []
        for graphemes, phonemes, audio in pipeline(test_text, voice='af_heart', speed=1.0):
            audio_chunks.append(audio)
            print("   ✓ Generated audio chunk")

        print(f"\n✅ Success! Generated {len(audio_chunks)} audio chunk(s)")
        print("   Models are now cached and ready to use")

        # Show cache location
        model_cache = cache_dir / "models--hexgrad--Kokoro-82M"
        if model_cache.exists():
            model_files = list(model_cache.rglob("*"))
            total_size = sum(f.stat().st_size for f in model_files if f.is_file())
            print(f"\n📁 Cache location: {model_cache}")
            print(f"   Total size: {total_size / 1024 / 1024:.1f} MB")

        print("\n" + "=" * 60)
        print("✨ Kokoro TTS setup complete!")
        print("\nYou can now start the server with:")
        print("   uvicorn src.main:app --host 127.0.0.1 --port 8000")

        return True

    except ImportError as e:
        print(f"\n❌ Error: Required packages not installed")
        print(f"   {e}")
        print("\nPlease install dependencies first:")
        print("   pip install -r requirements.txt")
        return False

    except Exception as e:
        print(f"\n❌ Error during setup: {e}")
        print("\nPlease check your internet connection and try again.")
        return False


if __name__ == "__main__":
    print("\n🎙️  Open Mobile TTS - Model Setup\n")

    # Check Python version
    if sys.version_info < (3, 10) or sys.version_info >= (3, 13):
        print("❌ Python 3.10-3.12 required")
        print(f"   Current version: {sys.version}")
        sys.exit(1)

    # Check espeak-ng
    import shutil
    if not shutil.which('espeak-ng'):
        print("⚠️  Warning: espeak-ng not found")
        print("   Install it with:")
        print("   - Linux: sudo apt-get install espeak-ng")
        print("   - macOS: brew install espeak-ng")
        print()

    success = setup_kokoro_models()
    sys.exit(0 if success else 1)
