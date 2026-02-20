"""Kokoro TTS backend, engine manager, and factory."""

import asyncio
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
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

        self.lang_code = lang_code if lang_code is not None else settings.KOKORO_LANG_CODE
        self.default_voice = default_voice if default_voice is not None else settings.DEFAULT_VOICE
        self.default_speed = default_speed if default_speed is not None else settings.DEFAULT_SPEED

        # Initialize Kokoro pipeline
        self.pipeline = KPipeline(lang_code=self.lang_code)

        # Initialize audio encoder
        self.encoder = StreamingAudioEncoder()

        # Track available voices
        self._voices = self._get_available_voices()

    def _get_available_voices(self) -> List[Dict[str, str]]:
        """Get list of available voices with rich metadata."""
        voices_by_lang = {
            'a': [
                'af_heart', 'af_nova', 'af_sky', 'af_bella', 'af_sarah',
                'am_adam', 'am_michael', 'bf_emma', 'bf_isabella', 'bm_george', 'bm_lewis'
            ],
            'b': [
                'bf_emma', 'bf_isabella', 'bm_george', 'bm_lewis'
            ],
        }

        lang_map = {
            'a': ('en-us', 'English (US)'),
            'b': ('en-gb', 'English (UK)'),
        }

        voice_names = voices_by_lang.get(self.lang_code, [])
        result = []
        for v in voice_names:
            prefix = v[0]
            gender_char = v[1]
            lang_code, lang_name = lang_map.get(prefix, ('en-us', 'English (US)'))
            display = v.split('_', 1)[1].title() if '_' in v else v
            result.append({
                'name': v,
                'language': lang_code,
                'language_name': lang_name,
                'gender': 'female' if gender_char == 'f' else 'male',
                'display_name': display,
            })
        return result

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
        voice = voice if voice is not None else self.default_voice
        speed = speed if speed is not None else self.default_speed

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
        voice = voice if voice is not None else self.default_voice
        speed = speed if speed is not None else self.default_speed

        audio_chunks = []
        for graphemes, phonemes, audio_array in self.pipeline(text, voice=voice, speed=speed):
            audio_chunks.append(audio_array)

        full_audio = np.concatenate(audio_chunks) if audio_chunks else np.array([])
        duration = len(full_audio) / 24000

        return full_audio, duration


# Keep backward-compatible alias
TTSEngine = KokoroBackend


class EngineManager:
    """Manages TTS backends with lazy loading and runtime switching."""

    def __init__(self):
        self._engines: Dict[str, TTSBackend] = {}
        self._active_name: str = settings.TTS_ENGINE
        self._active: TTSBackend | None = None

    @property
    def active(self) -> TTSBackend:
        if self._active is None:
            self._active = self._load(self._active_name)
        return self._active

    @property
    def active_name(self) -> str:
        return self._active_name

    def _load(self, name: str) -> TTSBackend:
        if name not in self._engines:
            if name == "sherpa-onnx":
                from .sherpa_backend import SherpaOnnxBackend
                self._engines[name] = SherpaOnnxBackend()
            else:
                self._engines[name] = KokoroBackend()
        return self._engines[name]

    def switch(self, name: str) -> None:
        self._active = self._load(name)
        self._active_name = name

    def available_engines(self) -> list:
        engines = [{"name": "kokoro", "label": "Kokoro (PyTorch)", "available": True}]
        sherpa_available = Path(settings.SHERPA_MODEL_DIR, "model.onnx").exists()
        engines.append({
            "name": "sherpa-onnx",
            "label": "Sherpa-ONNX (Multi-lang)",
            "available": sherpa_available,
        })
        return engines


def create_tts_engine() -> TTSBackend:
    """Factory function — backward-compatible wrapper.

    Returns the default EngineManager's active engine.
    """
    manager = EngineManager()
    return manager.active
