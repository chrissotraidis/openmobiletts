"""Integration tests for FastAPI endpoints (no authentication)."""

import pytest
from fastapi.testclient import TestClient
from src.main import app


class TestAPI:
    """Test API endpoints."""

    def setup_method(self):
        """Set up test client."""
        self.client = TestClient(app)

    def test_health_check(self):
        """Test health check endpoint."""
        response = self.client.get("/api/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"
        assert "version" in data

    def test_voices(self):
        """Test voices endpoint returns a list of voices."""
        response = self.client.get("/api/voices")
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) > 0
        # Each voice should have name and language
        assert "name" in data[0]
        assert "language" in data[0]

    def test_tts_stream_empty_text(self):
        """Test TTS streaming rejects empty text."""
        response = self.client.get("/api/tts/stream?text=")
        assert response.status_code == 400

    def test_document_upload_txt(self):
        """Test uploading a plain text document."""
        files = {"file": ("test.txt", "Hello world. This is a test.", "text/plain")}
        response = self.client.post("/api/documents/upload", files=files)
        assert response.status_code == 200
        data = response.json()
        assert data["filename"] == "test.txt"
        assert "text" in data
        assert len(data["text"]) > 0
        assert "chunk_count" in data

    def test_document_upload_unsupported_format(self):
        """Test uploading an unsupported file format."""
        files = {"file": ("test.xyz", "some content", "application/octet-stream")}
        response = self.client.post("/api/documents/upload", files=files)
        assert response.status_code == 400

    def test_root_endpoint(self):
        """Test root endpoint serves SPA or returns API info."""
        response = self.client.get("/")
        assert response.status_code == 200
