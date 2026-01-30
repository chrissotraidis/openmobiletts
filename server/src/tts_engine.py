"""Kokoro TTS engine wrapper with streaming support."""

import asyncio
import json
from typing import AsyncGenerator, Dict, List, Tuple

import numpy as np
from kokoro import KPipeline

from .audio_encoder import StreamingAudioEncoder
from .config import settings


class TTSEngine:
    """Wrapper for Kokoro TTS with streaming support."""

    def __init__(
        self,
        lang_code: str = None,
        default_voice: str = None,
        default_speed: float = None,
    ):
        """
        Initialize the TTS engine.

        Args:
            lang_code: Language code ('a' for American English)
            default_voice: Default voice to use
            default_speed: Default speech speed (1.0 = normal)
        """
        self.lang_code = lang_code or settings.KOKORO_LANG_CODE
        self.default_voice = default_voice or settings.DEFAULT_VOICE
        self.default_speed = default_speed or settings.DEFAULT_SPEED

        # Initialize Kokoro pipeline
        self.pipeline = KPipeline(lang_code=self.lang_code)

        # Initialize audio encoder
        self.encoder = StreamingAudioEncoder()

        # Track available voices
        self._voices = self._get_available_voices()

    def _get_available_voices(self) -> List[Dict[str, str]]:
        """
        Get list of available voices for the current language.

        Returns:
            List of voice dictionaries with name and language info
        """
        # Kokoro voices by language code
        voices_by_lang = {
            'a': [  # American English
                'af_heart', 'af_nova', 'af_sky', 'af_bella', 'af_sarah',
                'am_adam', 'am_michael', 'bf_emma', 'bf_isabella', 'bm_george', 'bm_lewis'
            ],
            'b': [  # British English
                'bf_emma', 'bf_isabella', 'bm_george', 'bm_lewis'
            ],
        }

        voice_names = voices_by_lang.get(self.lang_code, [])
        return [
            {'name': voice, 'language': self.lang_code}
            for voice in voice_names
        ]

    @property
    def available_voices(self) -> List[Dict[str, str]]:
        """Get available voices."""
        return self._voices

    async def generate_speech_stream(
        self,
        text_chunks: List[str],
        voice: str = None,
        speed: float = None,
    ) -> AsyncGenerator[Tuple[bytes, Dict], None]:
        """
        Generate speech audio as a stream with timing metadata.

        This is the core streaming method that yields MP3 chunks with timing info
        as they're generated, enabling real-time playback on the client.

        Args:
            text_chunks: List of text chunks to convert to speech
            voice: Voice to use (defaults to default_voice)
            speed: Speech speed multiplier (defaults to default_speed)

        Yields:
            Tuple of (MP3 audio bytes, timing metadata dict)
        """
        voice = voice or self.default_voice
        speed = speed or self.default_speed

        cumulative_time = 0.0

        for chunk_index, text_chunk in enumerate(text_chunks):
            # Generate audio using Kokoro's generator
            generator = self.pipeline(
                text_chunk,
                voice=voice,
                speed=speed,
            )

            # Process each audio segment from the generator
            for graphemes, phonemes, audio_array in generator:
                # Encode to MP3
                mp3_bytes, duration = self.encoder.encode_chunk(audio_array)

                # Create timing metadata
                timing = {
                    'text': graphemes,
                    'start': cumulative_time,
                    'end': cumulative_time + duration,
                    'chunk_index': chunk_index,
                }

                cumulative_time += duration

                # Yield timing metadata and audio
                yield mp3_bytes, timing

                # Yield control to allow other async operations
                await asyncio.sleep(0)

    def generate_speech(
        self,
        text: str,
        voice: str = None,
        speed: float = None,
    ) -> Tuple[np.ndarray, float]:
        """
        Generate speech audio synchronously (non-streaming).

        Use this for complete audio generation when streaming isn't needed.

        Args:
            text: Text to convert to speech
            voice: Voice to use
            speed: Speech speed multiplier

        Returns:
            Tuple of (audio array, total duration)
        """
        voice = voice or self.default_voice
        speed = speed or self.default_speed

        # Collect all audio chunks
        audio_chunks = []
        for graphemes, phonemes, audio_array in self.pipeline(text, voice=voice, speed=speed):
            audio_chunks.append(audio_array)

        # Concatenate all chunks
        full_audio = np.concatenate(audio_chunks) if audio_chunks else np.array([])

        # Calculate duration (Kokoro outputs at 24kHz)
        duration = len(full_audio) / 24000

        return full_audio, duration
