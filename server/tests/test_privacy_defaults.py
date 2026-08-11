"""Tests for local-only networking and content-redacted logging defaults."""

from src import logging_config
from src.config import settings


def test_server_defaults_to_loopback_and_local_cors():
    assert settings.HOST == "127.0.0.1"
    origins = {origin.strip() for origin in settings.CORS_ORIGINS.split(",")}
    assert "http://localhost:8000" in origins
    assert "*" not in origins


def test_text_previews_are_redacted_by_default(monkeypatch):
    monkeypatch.setattr(logging_config, "LOG_CONTENT_PREVIEWS", False)
    assert logging_config.preview_text("private transcript") == "<redacted: 18 chars>"


def test_text_previews_can_be_explicitly_enabled(monkeypatch):
    monkeypatch.setattr(logging_config, "LOG_CONTENT_PREVIEWS", True)
    assert logging_config.preview_text("private transcript", 7) == "'private'... (18 chars total)"
