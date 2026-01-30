"""Configuration management for Open Mobile TTS server."""

import os
from typing import Optional
from dotenv import load_dotenv

# Load environment variables from .env file
load_dotenv()


class Settings:
    """Application settings loaded from environment variables."""

    # Authentication
    JWT_SECRET: str = os.getenv("JWT_SECRET", "")
    ADMIN_USERNAME: str = os.getenv("ADMIN_USERNAME", "admin")
    ADMIN_PASSWORD_HASH: str = os.getenv("ADMIN_PASSWORD_HASH", "")
    JWT_ALGORITHM: str = "HS256"
    JWT_EXPIRATION_HOURS: int = int(os.getenv("JWT_EXPIRATION_HOURS", "24"))

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
    MAX_UPLOAD_SIZE_MB: int = int(os.getenv("MAX_UPLOAD_SIZE_MB", "10"))
    UPLOAD_DIR: str = os.getenv("UPLOAD_DIR", "/tmp/openmobiletts_uploads")

    # CORS
    CORS_ORIGINS: list[str] = os.getenv("CORS_ORIGINS", "http://localhost:5173").split(",")

    def validate(self) -> None:
        """Validate required settings are present."""
        if not self.JWT_SECRET:
            raise ValueError(
                "JWT_SECRET environment variable is required. "
                "Generate with: openssl rand -hex 32"
            )
        if not self.ADMIN_PASSWORD_HASH:
            raise ValueError(
                "ADMIN_PASSWORD_HASH environment variable is required. "
                "Generate with pwdlib."
            )
        if len(self.JWT_SECRET) < 32:
            raise ValueError("JWT_SECRET must be at least 32 characters long")


# Global settings instance
settings = Settings()
