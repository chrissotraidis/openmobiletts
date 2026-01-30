"""Integration tests for FastAPI endpoints."""

import pytest
from fastapi.testclient import TestClient
from src.main import app


class TestAPI:
    """Test API endpoints."""

    def setup_method(self):
        """Set up test client."""
        self.client = TestClient(app)
        self.token = None

    def test_root_endpoint(self):
        """Test root endpoint returns API info."""
        response = self.client.get("/")
        assert response.status_code == 200
        data = response.json()
        assert "name" in data
        assert "version" in data
        assert data["name"] == "Open Mobile TTS"

    def test_health_check(self):
        """Test health check endpoint."""
        response = self.client.get("/health")
        assert response.status_code == 200
        data = response.json()
        assert data["status"] == "healthy"

    def test_login_success(self):
        """Test successful login."""
        response = self.client.post(
            "/token",
            data={"username": "admin", "password": "testpassword123"},
        )
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert data["token_type"] == "bearer"
        self.token = data["access_token"]

    def test_login_wrong_password(self):
        """Test login with wrong password."""
        response = self.client.post(
            "/token",
            data={"username": "admin", "password": "wrongpassword"},
        )
        assert response.status_code == 401

    def test_login_wrong_username(self):
        """Test login with wrong username."""
        response = self.client.post(
            "/token",
            data={"username": "wronguser", "password": "testpassword123"},
        )
        assert response.status_code == 401

    def test_voices_without_auth(self):
        """Test voices endpoint without authentication."""
        response = self.client.get("/api/voices")
        assert response.status_code == 401

    def test_voices_with_auth(self):
        """Test voices endpoint with authentication."""
        # First login
        login_response = self.client.post(
            "/token",
            data={"username": "admin", "password": "testpassword123"},
        )
        token = login_response.json()["access_token"]

        # Then get voices
        response = self.client.get(
            "/api/voices",
            headers={"Authorization": f"Bearer {token}"},
        )
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) > 0
        # Check voice structure
        assert "name" in data[0]
        assert "language" in data[0]

    def test_tts_stream_without_auth(self):
        """Test TTS streaming without authentication."""
        response = self.client.get("/api/tts/stream?text=Hello")
        assert response.status_code == 401

    def test_tts_stream_empty_text(self):
        """Test TTS streaming with empty text."""
        # Login first
        login_response = self.client.post(
            "/token",
            data={"username": "admin", "password": "testpassword123"},
        )
        token = login_response.json()["access_token"]

        # Try streaming with empty text
        response = self.client.get(
            "/api/tts/stream?text=",
            headers={"Authorization": f"Bearer {token}"},
        )
        assert response.status_code == 400

    def test_document_upload_without_auth(self):
        """Test document upload without authentication."""
        files = {"file": ("test.txt", "Hello world", "text/plain")}
        response = self.client.post("/api/documents/upload", files=files)
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_tts_stream_with_auth(self):
        """Test TTS streaming with authentication."""
        # Login first
        login_response = self.client.post(
            "/token",
            data={"username": "admin", "password": "testpassword123"},
        )
        token = login_response.json()["access_token"]

        # Stream short text
        # Note: This test may take a few seconds on CPU
        response = self.client.get(
            "/api/tts/stream?text=Hello",
            headers={"Authorization": f"Bearer {token}"},
        )
        assert response.status_code == 200
        assert response.headers["content-type"] == "audio/mpeg"
