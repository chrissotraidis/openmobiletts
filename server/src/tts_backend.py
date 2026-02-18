"""Abstract base class for TTS backends."""

from abc import ABC, abstractmethod
from typing import AsyncGenerator, Dict, List, Tuple

import numpy as np


class TTSBackend(ABC):
    """Abstract interface for TTS engine backends.

    All backends must implement streaming and synchronous generation
    with the same timing/audio protocol so endpoints are engine-agnostic.
    """

    @abstractmethod
    async def generate_speech_stream(
        self,
        text_chunks: List[Dict],
        voice: str = None,
        speed: float = None,
    ) -> AsyncGenerator[Tuple[bytes, Dict], None]:
        """Generate speech as a stream of (MP3 bytes, timing metadata) tuples.

        Args:
            text_chunks: List of chunk dicts with 'text' and 'starts_paragraph' keys
            voice: Voice name (e.g. 'af_heart')
            speed: Speech speed multiplier (1.0 = normal)

        Yields:
            Tuple of (MP3 audio bytes, timing metadata dict)
        """
        ...

    @abstractmethod
    def generate_speech(
        self,
        text: str,
        voice: str = None,
        speed: float = None,
    ) -> Tuple[np.ndarray, float]:
        """Generate speech synchronously (non-streaming).

        Args:
            text: Text to convert to speech
            voice: Voice name
            speed: Speech speed multiplier

        Returns:
            Tuple of (audio array, total duration in seconds)
        """
        ...

    @property
    @abstractmethod
    def available_voices(self) -> List[Dict[str, str]]:
        """Get list of available voices.

        Returns:
            List of voice dicts with 'name' and 'language' keys
        """
        ...
