"""Integration tests for FastAPI endpoints (no authentication)."""

import io
import time
import zipfile

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
        response = self.client.post("/api/tts/stream", json={"text": ""})
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

    def test_stt_batch_returns_markdown_zip(self, monkeypatch):
        """Test batch STT returns a ZIP with per-file Markdown transcripts."""
        class FakeSttEngine:
            is_available = True
            is_initialized = True

            def transcribe_file(self, filepath):
                return "This is a transcript."

        monkeypatch.setattr("src.main.stt_engine", FakeSttEngine())

        files = [
            ("files", ("call one.wav", b"fake audio", "audio/wav")),
            ("files", ("call two.mp3", b"fake audio", "audio/mpeg")),
        ]
        response = self.client.post("/api/stt/batch", files=files)

        assert response.status_code == 200
        assert response.headers["content-type"] == "application/zip"

        archive = zipfile.ZipFile(io.BytesIO(response.content))
        names = set(archive.namelist())
        assert "call one.md" in names
        assert "call two.md" in names
        assert "summary.md" not in names
        assert "manifest.json" not in names
        assert "This is a transcript." in archive.read("call one.md").decode()

    def test_stt_batch_job_status_and_download(self, monkeypatch):
        """Test background batch jobs expose progress and final ZIP download."""
        class FakeSttEngine:
            is_available = True
            is_initialized = True

            def transcribe_file(self, filepath):
                return "Background transcript."

        monkeypatch.setattr("src.main.stt_engine", FakeSttEngine())

        response = self.client.post(
            "/api/stt/batch/jobs",
            files=[("files", ("call.wav", b"fake audio", "audio/wav"))],
        )

        assert response.status_code == 202
        job = response.json()
        assert job["total"] == 1

        for _ in range(20):
            status_response = self.client.get(f"/api/stt/batch/jobs/{job['id']}")
            assert status_response.status_code == 200
            job = status_response.json()
            if job["status"] == "complete":
                break
            time.sleep(0.05)

        assert job["status"] == "complete"
        assert job["completed"] == 1
        assert job["result_url"]
        assert job["files"][0]["source_file"] == "call.wav"
        assert job["files"][0]["transcript_file"] == "call.md"
        assert job["files"][0]["status"] == "complete"

        download = self.client.get(job["result_url"])
        assert download.status_code == 200
        archive = zipfile.ZipFile(io.BytesIO(download.content))
        assert "call.md" in archive.namelist()
        assert "Background transcript." in archive.read("call.md").decode()

    def test_root_endpoint(self):
        """Test root endpoint serves SPA or returns API info."""
        response = self.client.get("/")
        assert response.status_code == 200
