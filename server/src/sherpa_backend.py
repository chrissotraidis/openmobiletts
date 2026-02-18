"""Sherpa-ONNX TTS backend using the Kokoro multi-lang ONNX model."""

import asyncio
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import AsyncGenerator, Dict, List, Tuple

import numpy as np
import sherpa_onnx

from .audio_encoder import StreamingAudioEncoder
from .config import settings
from .tts_backend import TTSBackend

# Thread pool for parallel encoding (matches KokoroBackend pattern)
_encoder_pool = ThreadPoolExecutor(max_workers=2)

# Voice name → speaker ID mapping for kokoro-multi-lang-v1_0
_VOICE_TO_SID = {
    'af_alloy': 0, 'af_aoede': 1, 'af_bella': 2, 'af_heart': 3,
    'af_jessica': 4, 'af_kore': 5, 'af_nicole': 6, 'af_nova': 7,
    'af_river': 8, 'af_sarah': 9, 'af_sky': 10,
    'am_adam': 11, 'am_echo': 12, 'am_eric': 13, 'am_fenrir': 14,
    'am_liam': 15, 'am_michael': 16, 'am_onyx': 17, 'am_puck': 18,
    'am_santa': 19,
    'bf_alice': 20, 'bf_emma': 21, 'bf_isabella': 22, 'bf_lily': 23,
    'bm_daniel': 24, 'bm_fable': 25, 'bm_george': 26, 'bm_lewis': 27,
    'ef_dora': 28, 'em_alex': 29,
    'ff_siwis': 30,
    'hf_alpha': 31, 'hf_beta': 32, 'hm_omega': 33, 'hm_psi': 34,
    'if_sara': 35, 'im_nicola': 36,
    'jf_alpha': 37, 'jf_gongitsune': 38, 'jf_nezumi': 39, 'jf_tebukuro': 40,
    'jm_kumo': 41,
    'pf_dora': 42, 'pm_alex': 43, 'pm_santa': 44,
    'zf_xiaobei': 45, 'zf_xiaoni': 46, 'zf_xiaoxiao': 47, 'zf_xiaoyi': 48,
    'zm_yunjian': 49, 'zm_yunxi': 50, 'zm_yunxia': 51, 'zm_yunyang': 52,
}

# English voices exposed in the UI (same set as KokoroBackend)
_ENGLISH_VOICES = [
    'af_heart', 'af_nova', 'af_sky', 'af_bella', 'af_sarah',
    'am_adam', 'am_michael', 'bf_emma', 'bf_isabella', 'bm_george', 'bm_lewis',
]


class SherpaOnnxBackend(TTSBackend):
    """Sherpa-ONNX TTS backend using the Kokoro ONNX model.

    Processes pre-chunked text (from TextPreprocessor) through sherpa-onnx's
    OfflineTts, producing the same (MP3 bytes, timing dict) streaming protocol
    as KokoroBackend.
    """

    def __init__(self):
        model_dir = Path(settings.SHERPA_MODEL_DIR)

        if not (model_dir / "model.onnx").exists():
            raise FileNotFoundError(
                f"Sherpa-ONNX model not found at {model_dir}. "
                "Run: python server/setup_sherpa_models.py"
            )

        # Build lexicon path — multi-lang models require it
        lexicon_path = ""
        for candidate in ["lexicon-us-en.txt", "lexicon.txt"]:
            p = model_dir / candidate
            if p.exists():
                lexicon_path = str(p)
                break

        # Build dict dir path if present (for multi-lang jieba tokenizer)
        dict_dir = ""
        if (model_dir / "dict").exists():
            dict_dir = str(model_dir / "dict")

        # Build rule FSTs path if they exist
        rule_fsts = ""
        fst_files = sorted(model_dir.glob("*.fst"))
        if fst_files:
            rule_fsts = ",".join(str(f) for f in fst_files)

        config = sherpa_onnx.OfflineTtsConfig(
            model=sherpa_onnx.OfflineTtsModelConfig(
                kokoro=sherpa_onnx.OfflineTtsKokoroModelConfig(
                    model=str(model_dir / "model.onnx"),
                    voices=str(model_dir / "voices.bin"),
                    tokens=str(model_dir / "tokens.txt"),
                    data_dir=str(model_dir / "espeak-ng-data"),
                    lexicon=lexicon_path,
                    dict_dir=dict_dir,
                ),
                num_threads=settings.SHERPA_NUM_THREADS,
            ),
            rule_fsts=rule_fsts,
        )

        self.tts = sherpa_onnx.OfflineTts(config)
        self.encoder = StreamingAudioEncoder()
        self.default_voice = settings.DEFAULT_VOICE
        self.default_speed = settings.DEFAULT_SPEED

    @property
    def available_voices(self) -> List[Dict[str, str]]:
        return [{'name': v, 'language': 'a'} for v in _ENGLISH_VOICES]

    def _voice_to_sid(self, voice: str) -> int:
        """Map voice name string to integer speaker ID."""
        sid = _VOICE_TO_SID.get(voice)
        if sid is not None:
            return sid
        # Fall back to af_heart (SID 3) for unknown voices
        return _VOICE_TO_SID.get(self.default_voice, 3)

    async def generate_speech_stream(
        self,
        text_chunks: List[Dict],
        voice: str = None,
        speed: float = None,
    ) -> AsyncGenerator[Tuple[bytes, Dict], None]:
        """Generate speech as a stream of (MP3 bytes, timing metadata) tuples.

        Each text chunk (already 1-3 sentences from TextPreprocessor) is
        synthesized as one audio segment. Uses the same parallel-encode
        pattern as KokoroBackend for consistent streaming behavior.
        """
        voice = voice or self.default_voice
        speed = speed or self.default_speed
        sid = self._voice_to_sid(voice)

        cumulative_time = 0.0
        pending_encode = None
        pending_meta = None

        for chunk_index, chunk_data in enumerate(text_chunks):
            text_chunk = chunk_data['text']
            starts_paragraph = chunk_data.get('starts_paragraph', False)

            # Generate audio for the entire chunk synchronously
            # (sherpa-onnx doesn't have a sentence-level generator like Kokoro)
            loop = asyncio.get_running_loop()
            audio = await loop.run_in_executor(
                None,
                lambda t=text_chunk, s=sid, sp=speed: self.tts.generate(t, sid=s, speed=sp),
            )

            if not audio.samples or len(audio.samples) == 0:
                continue

            audio_array = np.array(audio.samples, dtype=np.float32)

            # If there's a pending encode, yield it first
            if pending_encode is not None:
                mp3_bytes, duration = await asyncio.wrap_future(pending_encode)
                pending_meta['end'] = pending_meta['start'] + duration
                cumulative_time = pending_meta['end']
                yield mp3_bytes, pending_meta

            # Start encoding this chunk in thread pool
            pending_encode = loop.run_in_executor(
                _encoder_pool,
                self.encoder.encode_chunk,
                audio_array,
                audio.sample_rate,
            )
            pending_meta = {
                'text': text_chunk,
                'start': cumulative_time,
                'end': 0,
                'chunk_index': chunk_index,
                'starts_paragraph': starts_paragraph,
            }

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
        """Generate speech synchronously (non-streaming)."""
        voice = voice or self.default_voice
        speed = speed or self.default_speed
        sid = self._voice_to_sid(voice)

        audio = self.tts.generate(text, sid=sid, speed=speed)

        if not audio.samples or len(audio.samples) == 0:
            return np.array([]), 0.0

        audio_array = np.array(audio.samples, dtype=np.float32)
        duration = len(audio_array) / audio.sample_rate

        return audio_array, duration
