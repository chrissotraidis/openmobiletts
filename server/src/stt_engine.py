"""
Speech-to-Text engine using Moonshine v2 via sherpa-onnx Python bindings.

Provides batch transcription of audio files. The STT model is downloaded
on first use if not already present.
"""

from __future__ import annotations

import logging
import subprocess
import tempfile
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)

# Try to import sherpa_onnx — it's optional for desktop
try:
    import sherpa_onnx
    HAS_SHERPA = True
except ImportError:
    HAS_SHERPA = False
    logger.info("sherpa_onnx not installed — STT features disabled on desktop")


class SttEngine:
    """Moonshine v2 STT engine via sherpa-onnx."""

    SAMPLE_RATE = 16000
    MODEL_NAME = "sherpa-onnx-moonshine-base-en-int8"

    def __init__(self, model_dir: Optional[str] = None):
        self._recognizer = None
        self._model_dir = model_dir

    @property
    def is_available(self) -> bool:
        return HAS_SHERPA

    @property
    def is_initialized(self) -> bool:
        return self._recognizer is not None

    def init(self, model_dir: Optional[str] = None):
        """Initialize the STT engine with Moonshine v2 model files."""
        if not HAS_SHERPA:
            raise RuntimeError("sherpa_onnx is not installed. Install with: pip install sherpa-onnx")

        if self._recognizer is not None:
            return  # Already initialized

        model_path = Path(model_dir or self._model_dir or "")
        if not model_path.exists():
            raise FileNotFoundError(f"STT model directory not found: {model_path}")

        logger.info(f"Initializing Moonshine v2 STT from: {model_path}")

        # Check for merged vs split decoder
        merged_decoder = model_path / "decoder.onnx"
        if merged_decoder.exists():
            moonshine_config = {
                "preprocessor": str(model_path / "preprocess.onnx"),
                "encoder": str(model_path / "encode.onnx"),
                "merged_decoder": str(merged_decoder),
            }
        else:
            moonshine_config = {
                "preprocessor": str(model_path / "preprocess.onnx"),
                "encoder": str(model_path / "encode.onnx"),
                "uncached_decoder": str(model_path / "uncached_decode.onnx"),
                "cached_decoder": str(model_path / "cached_decode.onnx"),
            }

        self._recognizer = sherpa_onnx.OfflineRecognizer.from_moonshine(
            tokens=str(model_path / "tokens.txt"),
            num_threads=4,
            decoding_method="greedy_search",
            **moonshine_config,
        )

        logger.info("Moonshine v2 STT engine initialized")

    def transcribe(self, samples: list[float], sample_rate: int = SAMPLE_RATE) -> str:
        """
        Transcribe PCM audio samples to text.

        Args:
            samples: Float audio samples normalized to [-1, 1]
            sample_rate: Sample rate in Hz (default 16000)

        Returns:
            Transcribed text string
        """
        if self._recognizer is None:
            raise RuntimeError("STT engine not initialized")

        stream = self._recognizer.create_stream()
        stream.accept_waveform(sample_rate, samples)
        self._recognizer.decode(stream)
        text = stream.result.text.strip()

        logger.info(f"Transcribed {len(samples)} samples → {len(text)} chars: {text[:100]}")
        return text

    def transcribe_file(self, file_path: str) -> str:
        """
        Transcribe an audio file to text.
        Uses ffmpeg to decode to 16kHz mono PCM, then runs Moonshine.

        Args:
            file_path: Path to the audio file (mp3, aac, ogg, wav, etc.)

        Returns:
            Transcribed text string
        """
        samples = self._decode_audio_file(file_path)
        return self.transcribe(samples)

    def _decode_audio_file(self, file_path: str) -> list[float]:
        """Decode any audio file to 16kHz mono float samples using ffmpeg."""
        import struct

        tmp = tempfile.NamedTemporaryFile(suffix=".raw", delete=False)
        tmp_path = tmp.name
        tmp.close()

        try:
            cmd = [
                "ffmpeg", "-i", file_path,
                "-f", "s16le",       # raw 16-bit signed little-endian
                "-acodec", "pcm_s16le",
                "-ar", str(self.SAMPLE_RATE),
                "-ac", "1",          # mono
                "-y",                # overwrite
                tmp_path,
            ]

            result = subprocess.run(
                cmd, capture_output=True, text=True, timeout=300
            )

            if result.returncode != 0:
                raise RuntimeError(f"ffmpeg decode failed: {result.stderr[:500]}")

            raw_bytes = Path(tmp_path).read_bytes()
        finally:
            Path(tmp_path).unlink(missing_ok=True)

        # Convert 16-bit PCM to float [-1, 1]
        num_samples = len(raw_bytes) // 2
        shorts = struct.unpack(f"<{num_samples}h", raw_bytes)
        return [s / 32768.0 for s in shorts]

    def release(self):
        """Release the STT engine resources."""
        self._recognizer = None
        logger.info("STT engine released")
