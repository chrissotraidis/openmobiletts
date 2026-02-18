"""Kokoro TTS backend and engine factory."""

import asyncio
from concurrent.futures import ThreadPoolExecutor
from typing import AsyncGenerator, Dict, List, Tuple

import numpy as np

from .audio_encoder import StreamingAudioEncoder
from .config import settings
from .tts_backend import TTSBackend

# Thread pool for parallel encoding
_encoder_pool = ThreadPoolExecutor(max_workers=2)


class KokoroBackend(TTSBackend):
    """Kokoro PyTorch TTS backend with streaming support."""

    def __init__(
        self,
        lang_code: str = None,
        default_voice: str = None,
        default_speed: float = None,
    ):
        from kokoro import KPipeline

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
        """Get list of available voices for the current language."""
        voices_by_lang = {
            'a': [
                'af_heart', 'af_nova', 'af_sky', 'af_bella', 'af_sarah',
                'am_adam', 'am_michael', 'bf_emma', 'bf_isabella', 'bm_george', 'bm_lewis'
            ],
            'b': [
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
        return self._voices

    async def generate_speech_stream(
        self,
        text_chunks: List[Dict],
        voice: str = None,
        speed: float = None,
    ) -> AsyncGenerator[Tuple[bytes, Dict], None]:
        """Generate speech audio as a stream with timing metadata.

        Uses parallel encoding: MP3 encoding runs in a thread pool while
        Kokoro continues generating the next segment.
        """
        voice = voice or self.default_voice
        speed = speed or self.default_speed

        cumulative_time = 0.0
        pending_encode = None
        pending_meta = None

        for chunk_index, chunk_data in enumerate(text_chunks):
            text_chunk = chunk_data['text']
            starts_paragraph = chunk_data.get('starts_paragraph', False)

            generator = self.pipeline(
                text_chunk,
                voice=voice,
                speed=speed,
            )

            is_first_segment = True

            for graphemes, phonemes, audio_array in generator:
                if pending_encode is not None:
                    mp3_bytes, duration = await asyncio.wrap_future(pending_encode)
                    pending_meta['end'] = pending_meta['start'] + duration
                    cumulative_time = pending_meta['end']
                    yield mp3_bytes, pending_meta

                loop = asyncio.get_running_loop()
                pending_encode = loop.run_in_executor(
                    _encoder_pool,
                    self.encoder.encode_chunk,
                    audio_array,
                )
                pending_meta = {
                    'text': graphemes,
                    'start': cumulative_time,
                    'end': 0,
                    'chunk_index': chunk_index,
                    'starts_paragraph': starts_paragraph and is_first_segment,
                }
                is_first_segment = False

                await asyncio.sleep(0)

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
        """Generate speech audio synchronously (non-streaming)."""
        voice = voice or self.default_voice
        speed = speed or self.default_speed

        audio_chunks = []
        for graphemes, phonemes, audio_array in self.pipeline(text, voice=voice, speed=speed):
            audio_chunks.append(audio_array)

        full_audio = np.concatenate(audio_chunks) if audio_chunks else np.array([])
        duration = len(full_audio) / 24000

        return full_audio, duration


# Keep backward-compatible alias
TTSEngine = KokoroBackend


def create_tts_engine() -> TTSBackend:
    """Factory function to create the configured TTS backend.

    Reads settings.TTS_ENGINE to decide which backend to instantiate.
    Returns a TTSBackend instance.
    """
    if settings.TTS_ENGINE == "sherpa-onnx":
        from .sherpa_backend import SherpaOnnxBackend
        return SherpaOnnxBackend()
    return KokoroBackend()
