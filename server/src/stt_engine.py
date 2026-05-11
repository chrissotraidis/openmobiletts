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


def _join_chunks(pieces: list[str]) -> str:
    """Join overlap-windowed transcript chunks, removing duplicated tail/head words.

    Moonshine is decoded in 25 s windows with 1 s overlap, so the last few words
    of chunk N often reappear at the start of chunk N+1. Trim the longest token
    overlap (up to 8 words) before concatenating to remove stutters like
    "for you today. for you today".
    """
    import re

    if not pieces:
        return ""

    def tokens(s: str) -> list[str]:
        return re.findall(r"\S+", s)

    def norm(tok: str) -> str:
        return re.sub(r"[^\w]", "", tok).lower()

    result_tokens = tokens(pieces[0])
    for piece in pieces[1:]:
        next_tokens = tokens(piece)
        if not next_tokens or not result_tokens:
            result_tokens.extend(next_tokens)
            continue
        max_overlap = min(8, len(result_tokens), len(next_tokens))
        best = 0
        for k in range(max_overlap, 1, -1):
            tail = [norm(t) for t in result_tokens[-k:]]
            head = [norm(t) for t in next_tokens[:k]]
            if tail == head and all(tail):
                best = k
                break
        result_tokens.extend(next_tokens[best:])
    return " ".join(result_tokens).strip()


def polish_transcript(text: str, sentences_per_paragraph: int = 4) -> str:
    """Format a raw STT transcript into readable paragraphs.

    Splits on sentence boundaries and groups N sentences per paragraph,
    so a single 5-minute call no longer renders as one wall of text.
    Also strips stray leading dashes that Moonshine sometimes emits at
    speaker turns (e.g. "Okay. - It's not shattered.").
    """
    import re

    if not text:
        return ""

    cleaned = re.sub(r"\s+-\s+", " ", text).strip()
    cleaned = re.sub(r"\s{2,}", " ", cleaned)

    sentences = re.findall(r".+?(?:[.!?]+(?=\s|$)|$)", cleaned)
    sentences = [s.strip() for s in sentences if s.strip()]

    if not sentences:
        return cleaned

    paragraphs = []
    for i in range(0, len(sentences), sentences_per_paragraph):
        paragraphs.append(" ".join(sentences[i : i + sentences_per_paragraph]))
    return "\n\n".join(paragraphs)


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

        # Discover actual filenames — INT8 models use ".int8.onnx" suffix
        import glob
        onnx_files = [f.name for f in model_path.iterdir() if f.suffix == ".onnx"]

        encoder = next((f for f in onnx_files if f.startswith("encode")), None)
        preprocessor = next((f for f in onnx_files if f.startswith("preprocess")), None)
        merged = next((f for f in onnx_files if f.startswith("decoder") and "uncached" not in f and "cached" not in f), None)
        uncached = next((f for f in onnx_files if f.startswith("uncached_decode")), None)
        cached = next((f for f in onnx_files if f.startswith("cached_decode")), None)

        if not encoder or not preprocessor:
            raise FileNotFoundError(f"Missing encoder or preprocessor in {model_path}. Found: {onnx_files}")

        logger.info(f"Model files: encoder={encoder}, preprocessor={preprocessor}, merged={merged}, uncached={uncached}, cached={cached}")

        if merged:
            moonshine_config = {
                "preprocessor": str(model_path / preprocessor),
                "encoder": str(model_path / encoder),
                "merged_decoder": str(model_path / merged),
            }
        elif uncached and cached:
            moonshine_config = {
                "preprocessor": str(model_path / preprocessor),
                "encoder": str(model_path / encoder),
                "uncached_decoder": str(model_path / uncached),
                "cached_decoder": str(model_path / cached),
            }
        else:
            raise FileNotFoundError(f"No decoder files found in {model_path}. Found: {onnx_files}")

        self._recognizer = sherpa_onnx.OfflineRecognizer.from_moonshine(
            tokens=str(model_path / "tokens.txt"),
            num_threads=4,
            decoding_method="greedy_search",
            **moonshine_config,
        )

        logger.info("Moonshine v2 STT engine initialized")

    # Moonshine is non-streaming and only reliably decodes ~30 s at a time.
    # Anything longer must be split into windows; pick 25 s with 1 s overlap
    # so we don't clip words at the seam.
    _CHUNK_SECONDS = 25
    _OVERLAP_SECONDS = 1

    def _decode_chunk(self, samples: list[float], sample_rate: int) -> str:
        stream = self._recognizer.create_stream()
        stream.accept_waveform(sample_rate, samples)
        if hasattr(self._recognizer, "decode_stream"):
            self._recognizer.decode_stream(stream)
        else:
            self._recognizer.decode(stream)
        return stream.result.text.strip()

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

        chunk_size = self._CHUNK_SECONDS * sample_rate
        overlap = self._OVERLAP_SECONDS * sample_rate
        total = len(samples)

        if total <= chunk_size:
            text = self._decode_chunk(samples, sample_rate)
            logger.info(f"Transcribed {total} samples → {len(text)} chars: {text[:100]}")
            return text

        pieces: list[str] = []
        start = 0
        chunk_idx = 0
        while start < total:
            end = min(start + chunk_size, total)
            piece = self._decode_chunk(samples[start:end], sample_rate)
            if piece:
                pieces.append(piece)
            chunk_idx += 1
            logger.info(
                f"Chunk {chunk_idx}: samples[{start}:{end}] → {len(piece)} chars: {piece[:80]}"
            )
            if end >= total:
                break
            start = end - overlap

        text = _join_chunks(pieces)
        logger.info(
            f"Transcribed {total} samples in {chunk_idx} chunks → {len(text)} chars: {text[:100]}"
        )
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
