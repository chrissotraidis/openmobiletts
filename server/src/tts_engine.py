"""Kokoro TTS engine wrapper with streaming support."""

import asyncio
from concurrent.futures import ThreadPoolExecutor
from typing import AsyncGenerator, Dict, List, Tuple

import numpy as np
from kokoro import KPipeline

from .audio_encoder import StreamingAudioEncoder
from .config import settings

# Thread pool for parallel encoding
_encoder_pool = ThreadPoolExecutor(max_workers=2)


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
        text_chunks: List[Dict],
        voice: str = None,
        speed: float = None,
    ) -> AsyncGenerator[Tuple[bytes, Dict], None]:
        """
        Generate speech audio as a stream with timing metadata.

        This is the core streaming method that yields MP3 chunks with timing info
        as they're generated, enabling real-time playback on the client.

        Uses parallel encoding: MP3 encoding runs in a thread pool while
        Kokoro continues generating the next segment.

        Args:
            text_chunks: List of chunk dicts with 'text' and 'starts_paragraph' keys
            voice: Voice to use (defaults to default_voice)
            speed: Speech speed multiplier (defaults to default_speed)

        Yields:
            Tuple of (MP3 audio bytes, timing metadata dict)
        """
        voice = voice or self.default_voice
        speed = speed or self.default_speed

        cumulative_time = 0.0
        pending_encode = None  # Future for encoding previous segment
        pending_meta = None    # Metadata for pending encode

        for chunk_index, chunk_data in enumerate(text_chunks):
            # Extract text and paragraph info from chunk dict
            text_chunk = chunk_data['text']
            starts_paragraph = chunk_data.get('starts_paragraph', False)

            # Generate audio using Kokoro's generator
            generator = self.pipeline(
                text_chunk,
                voice=voice,
                speed=speed,
            )

            # Track if this is the first segment of this chunk
            is_first_segment = True

            # Process each audio segment from the generator
            for graphemes, phonemes, audio_array in generator:
                # If there's a pending encode from previous iteration, wait for it
                if pending_encode is not None:
                    mp3_bytes, duration = await asyncio.wrap_future(pending_encode)
                    pending_meta['end'] = pending_meta['start'] + duration
                    cumulative_time = pending_meta['end']
                    yield mp3_bytes, pending_meta

                # Start encoding current segment in thread pool (non-blocking)
                loop = asyncio.get_event_loop()
                pending_encode = loop.run_in_executor(
                    _encoder_pool,
                    self.encoder.encode_chunk,
                    audio_array,
                )
                pending_meta = {
                    'text': graphemes,
                    'start': cumulative_time,
                    'end': 0,  # Will be filled when encode completes
                    'chunk_index': chunk_index,
                    'starts_paragraph': starts_paragraph and is_first_segment,
                }
                is_first_segment = False

                # Yield control to allow concurrent operations
                await asyncio.sleep(0)

        # Yield final pending segment
        if pending_encode is not None:
            mp3_bytes, duration = await asyncio.wrap_future(pending_encode)
            pending_meta['end'] = pending_meta['start'] + duration
            yield mp3_bytes, pending_meta

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
