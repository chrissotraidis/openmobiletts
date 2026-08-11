"""Integration tests for FastAPI endpoints (no authentication)."""

import io
import time
import zipfile

import pytest
from fastapi.testclient import TestClient
from src.app_info import APP_NAME, APP_VERSION
from src.model_catalog import STT_MODEL
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
        assert data["name"] == APP_NAME
        assert data["version"] == APP_VERSION

    def test_desktop_capability_contract(self):
        response = self.client.get("/api/capabilities")
        assert response.status_code == 200
        assert response.json() == {
            "schema_version": 1,
            "platform": "desktop",
            "features": {
                "tts": True,
                "stt": True,
                "batch_transcription": True,
                "engine_switching": True,
                "document_import": True,
                "audio_import": True,
                "model_download": True,
                "model_catalog": True,
                "project_storage": True,
                "exports": True,
                "logs": True,
            },
        }

    def test_shared_model_catalog_contract(self):
        response = self.client.get("/api/models/catalog")
        assert response.status_code == 200
        catalog = response.json()
        assert catalog["schema_version"] == 1
        by_id = {entry["id"]: entry for entry in catalog["models"]}
        assert by_id[STT_MODEL.model_id]["precision"] == "INT8"
        assert by_id["kokoro-multi-lang-v1_0"]["exposed_languages"] == [
            "en-us",
            "en-gb",
        ]

    @pytest.mark.parametrize("method", ["get", "post"])
    def test_unknown_api_route_returns_json_404(self, method):
        response = getattr(self.client, method)("/api/not-a-real-route")
        assert response.status_code == 404
        assert response.headers["content-type"].startswith("application/json")
        assert response.json() == {"detail": "Not found"}

    def test_stt_model_status_uses_real_model_identity(self):
        """The API must not describe Moonshine v1 Base as v2 Medium."""
        response = self.client.get("/api/stt/models")
        assert response.status_code == 200
        model = response.json()["models"][0]
        assert model["id"] == STT_MODEL.model_id
        assert model["label"] == "Moonshine v1 Base (English, INT8)"
        assert model["version"] == "v1 Base"
        assert model["precision"] == "INT8"
        assert model["languages"] == ["en"]
        assert model["archive_size_mb"] == STT_MODEL.archive_size_mb

    def test_stt_model_download_rejects_unknown_model(self):
        response = self.client.post(
            "/api/stt/models/download",
            json={"model": "moonshine-v2-medium"},
        )
        assert response.status_code == 404

    def test_stt_model_download_starts_background_installer(self, monkeypatch):
        calls = []

        monkeypatch.setattr("src.main.is_model_complete", lambda *args: False)
        monkeypatch.setattr(
            "src.main.stt_model_installer.start",
            lambda spec, **kwargs: calls.append((spec, kwargs)) or {"id": spec.model_id},
        )

        response = self.client.post(
            "/api/stt/models/download",
            json={"model": STT_MODEL.model_id},
        )

        assert response.status_code == 202
        assert response.json()["status"] == "started"
        assert calls[0][0] == STT_MODEL
        assert callable(calls[0][1]["validate"])
        assert callable(calls[0][1]["on_activated"])

    def test_voices(self, monkeypatch):
        """Test voices endpoint returns a list of voices."""
        class FakeVoiceEngine:
            available_voices = [
                {
                    "name": "test_voice",
                    "language": "en-us",
                    "language_name": "English (US)",
                    "gender": "female",
                    "display_name": "Test Voice",
                }
            ]

        class FakeEngineManager:
            active = FakeVoiceEngine()

        monkeypatch.setattr("src.main.engine_manager", FakeEngineManager())

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
