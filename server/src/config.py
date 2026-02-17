"""Configuration management for Open Mobile TTS."""

import os
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()


class Settings:
    """Application settings loaded from environment variables."""

    # TTS Engine
    KOKORO_LANG_CODE: str = os.getenv("KOKORO_LANG_CODE", "a")  # 'a' for American English
    DEFAULT_VOICE: str = os.getenv("DEFAULT_VOICE", "af_heart")
    DEFAULT_SPEED: float = float(os.getenv("DEFAULT_SPEED", "1.0"))
    MAX_CHUNK_TOKENS: int = int(os.getenv("MAX_CHUNK_TOKENS", "250"))

    # Audio Encoding
    MP3_BITRATE: str = os.getenv("MP3_BITRATE", "64k")
    MP3_SAMPLE_RATE: int = int(os.getenv("MP3_SAMPLE_RATE", "22050"))
    MP3_CHANNELS: int = 1  # Mono for speech

    # Server
    HOST: str = os.getenv("HOST", "0.0.0.0")
    PORT: int = int(os.getenv("PORT", "8000"))
    WORKERS: int = int(os.getenv("WORKERS", "1"))

    # File uploads
    MAX_UPLOAD_SIZE_MB: int = int(os.getenv("MAX_UPLOAD_SIZE_MB", "100"))
    UPLOAD_DIR: str = os.getenv("UPLOAD_DIR", "/tmp/openmobiletts_uploads")

    # Static files (built client) — auto-detected if empty
    STATIC_DIR: str = os.getenv("STATIC_DIR", "")


# Global settings instance
settings = Settings()
