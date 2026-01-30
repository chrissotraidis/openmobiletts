"""Audio encoding utilities for converting TTS output to MP3."""

from io import BytesIO
from typing import Tuple

import numpy as np
import soundfile as sf
from pydub import AudioSegment

from .config import settings


class AudioEncoder:
    """Encode TTS audio output to MP3 format optimized for speech."""

    def __init__(
        self,
        bitrate: str = None,
        sample_rate: int = None,
        channels: int = None,
    ):
        """
        Initialize the audio encoder.

        Args:
            bitrate: MP3 bitrate (e.g., '64k', '96k')
            sample_rate: Output sample rate in Hz
            channels: Number of audio channels (1=mono, 2=stereo)
        """
        self.bitrate = bitrate or settings.MP3_BITRATE
        self.sample_rate = sample_rate or settings.MP3_SAMPLE_RATE
        self.channels = channels or settings.MP3_CHANNELS

    def encode_to_mp3(
        self, audio_data: np.ndarray, source_sample_rate: int = 24000
    ) -> Tuple[bytes, float]:
        """
        Encode numpy audio array to MP3 bytes.

        Args:
            audio_data: Audio as numpy array (from Kokoro)
            source_sample_rate: Sample rate of input audio (Kokoro outputs 24kHz)

        Returns:
            Tuple of (MP3 bytes, duration in seconds)
        """
        # Convert numpy array to WAV in memory
        wav_buffer = BytesIO()
        sf.write(
            wav_buffer,
            audio_data,
            source_sample_rate,
            format='WAV',
            subtype='PCM_16',
        )
        wav_buffer.seek(0)

        # Load WAV and convert to MP3
        audio_segment = AudioSegment.from_file(wav_buffer, format='wav')

        # Resample and convert to mono if needed
        if audio_segment.frame_rate != self.sample_rate:
            audio_segment = audio_segment.set_frame_rate(self.sample_rate)

        if audio_segment.channels != self.channels:
            audio_segment = audio_segment.set_channels(self.channels)

        # Export to MP3 with optimized settings for speech
        mp3_buffer = BytesIO()
        audio_segment.export(
            mp3_buffer,
            format='mp3',
            bitrate=self.bitrate,
            parameters=[
                '-ar', str(self.sample_rate),  # Sample rate
                '-ac', str(self.channels),     # Channels
                '-q:a', '2',                    # Quality (0-9, lower is better)
            ],
        )

        # Calculate duration
        duration = len(audio_data) / source_sample_rate

        return mp3_buffer.getvalue(), duration


class StreamingAudioEncoder(AudioEncoder):
    """Audio encoder optimized for streaming responses."""

    def encode_chunk(
        self, audio_data: np.ndarray, source_sample_rate: int = 24000
    ) -> Tuple[bytes, float]:
        """
        Encode a single audio chunk for streaming.

        This is an alias for encode_to_mp3 to maintain API clarity.

        Args:
            audio_data: Audio chunk as numpy array
            source_sample_rate: Sample rate of input audio

        Returns:
            Tuple of (MP3 bytes, duration in seconds)
        """
        return self.encode_to_mp3(audio_data, source_sample_rate)
