"""Safe background download and activation for pinned local models."""

from __future__ import annotations

import hashlib
import os
import shutil
import tarfile
import tempfile
import threading
import urllib.request
import uuid
from pathlib import Path
from typing import Callable, Optional

from .model_catalog import ModelSpec, is_model_complete

ModelValidator = Callable[[Path], None]
ActivationCallback = Callable[[Path], None]


class ModelInstaller:
    """Install one pinned model at a time and expose thread-safe progress."""

    def __init__(self, cache_root: Path):
        self.cache_root = cache_root
        self._lock = threading.Lock()
        self._state = "idle"
        self._model_id: Optional[str] = None
        self._downloaded_bytes = 0
        self._total_bytes = 0
        self._error: Optional[str] = None
        self._thread: Optional[threading.Thread] = None

    def snapshot(self, spec: ModelSpec, *, active: bool = False) -> dict:
        target = spec.install_dir(self.cache_root)
        complete = is_model_complete(target, spec)
        with self._lock:
            applies = self._model_id == spec.model_id
            state = self._state if applies else ("installed" if complete else "not_installed")
            downloaded_bytes = self._downloaded_bytes if applies else 0
            total_bytes = self._total_bytes if applies else spec.archive_bytes
            error = self._error if applies else None

        if complete and state in {"idle", "not_installed", "ready"}:
            state = "installed"

        progress_percent = None
        if total_bytes > 0 and state in {"downloading", "verifying", "activating"}:
            progress_percent = round(min(downloaded_bytes / total_bytes, 1.0) * 100, 1)

        return {
            "id": spec.model_id,
            "name": spec.model_id,
            "label": spec.label,
            "family": spec.family,
            "version": spec.version,
            "precision": spec.precision,
            "languages": list(spec.languages),
            "runtime": spec.runtime,
            "minimum_runtime_version": spec.minimum_runtime_version,
            "source_url": spec.source_url,
            "weights_license": spec.weights_license,
            "archive_license": spec.archive_license,
            "voice_license": spec.voice_license,
            "archive_size_mb": spec.archive_size_mb,
            "installed_size_mb": spec.installed_size_mb,
            # Compatibility with the current client while it migrates to explicit sizes.
            "size_mb": spec.archive_size_mb,
            "downloaded": complete,
            "active": active,
            "downloading": state in {"downloading", "verifying", "activating"},
            "status": "ready" if active else state,
            "downloaded_bytes": downloaded_bytes,
            "total_bytes": total_bytes,
            "progress_percent": progress_percent,
            "error": error,
        }

    def start(
        self,
        spec: ModelSpec,
        *,
        validate: Optional[ModelValidator] = None,
        on_activated: Optional[ActivationCallback] = None,
    ) -> dict:
        target = spec.install_dir(self.cache_root)
        if is_model_complete(target, spec):
            return self.snapshot(spec)

        with self._lock:
            if self._thread and self._thread.is_alive():
                if self._model_id != spec.model_id:
                    raise RuntimeError("Another model download is already running")
                already_running = True
            else:
                already_running = False

            if not already_running:
                self._model_id = spec.model_id
                self._state = "downloading"
                self._downloaded_bytes = 0
                self._total_bytes = spec.archive_bytes
                self._error = None
                self._thread = threading.Thread(
                    target=self._run,
                    args=(spec, validate, on_activated),
                    daemon=True,
                    name=f"model-download-{spec.model_id}",
                )
                self._thread.start()

        return self.snapshot(spec)

    def _set_state(self, state: str, *, error: Optional[str] = None) -> None:
        with self._lock:
            self._state = state
            self._error = error

    def _set_downloaded_bytes(self, value: int) -> None:
        with self._lock:
            self._downloaded_bytes = value

    def _run(
        self,
        spec: ModelSpec,
        validate: Optional[ModelValidator],
        on_activated: Optional[ActivationCallback],
    ) -> None:
        self.cache_root.mkdir(parents=True, exist_ok=True)
        staging_root = Path(tempfile.mkdtemp(prefix=f".{spec.model_id}-", dir=self.cache_root))
        archive_path = staging_root / f"{spec.model_id}.tar.bz2"
        extracted_root = staging_root / "extracted"

        try:
            self._download(spec, archive_path)
            self._set_state("verifying")
            self._verify_archive(spec, archive_path)
            self._safe_extract(archive_path, extracted_root)

            candidate = extracted_root / spec.model_id
            if not is_model_complete(candidate, spec):
                missing = [name for name in spec.required_files if not (candidate / name).is_file()]
                missing.extend(
                    name for name in spec.required_directories if not (candidate / name).is_dir()
                )
                raise RuntimeError(f"Model archive is missing required files: {', '.join(missing)}")
            if validate:
                validate(candidate)

            self._set_state("activating")
            target = spec.install_dir(self.cache_root)
            backup = self.cache_root / f".{spec.model_id}.backup-{uuid.uuid4().hex}"
            had_previous = target.exists()
            if had_previous:
                os.replace(target, backup)
            try:
                os.replace(candidate, target)
                if on_activated:
                    on_activated(target)
            except Exception:
                if target.exists():
                    shutil.rmtree(target, ignore_errors=True)
                if had_previous and backup.exists():
                    os.replace(backup, target)
                raise
            if backup.exists():
                shutil.rmtree(backup, ignore_errors=True)

            self._set_downloaded_bytes(spec.archive_bytes)
            self._set_state("installed")
        except Exception as exc:
            self._set_state("error", error=str(exc))
        finally:
            shutil.rmtree(staging_root, ignore_errors=True)

    def _download(self, spec: ModelSpec, destination: Path) -> None:
        request = urllib.request.Request(
            spec.archive_url,
            headers={"User-Agent": "Open-Mobile-TTS-model-installer"},
        )
        with urllib.request.urlopen(request, timeout=60) as response, destination.open("wb") as output:
            status = getattr(response, "status", 200)
            if status != 200:
                raise RuntimeError(f"Model download failed with HTTP {status}")

            content_length = response.headers.get("Content-Length")
            if content_length and int(content_length) != spec.archive_bytes:
                raise RuntimeError(
                    f"Unexpected model archive size: server reported {content_length} bytes"
                )

            downloaded = 0
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                output.write(chunk)
                downloaded += len(chunk)
                self._set_downloaded_bytes(downloaded)

        if downloaded != spec.archive_bytes:
            raise RuntimeError(
                f"Incomplete model download: expected {spec.archive_bytes} bytes, got {downloaded}"
            )

    @staticmethod
    def _verify_archive(spec: ModelSpec, archive_path: Path) -> None:
        digest = hashlib.sha256()
        with archive_path.open("rb") as archive:
            for chunk in iter(lambda: archive.read(1024 * 1024), b""):
                digest.update(chunk)
        if digest.hexdigest() != spec.sha256:
            raise RuntimeError("Model archive checksum verification failed")

    @staticmethod
    def _safe_extract(archive_path: Path, destination: Path) -> None:
        destination.mkdir(parents=True, exist_ok=True)
        root = destination.resolve()
        with tarfile.open(archive_path, "r:bz2") as archive:
            members = archive.getmembers()
            for member in members:
                member_path = Path(member.name)
                resolved = (destination / member_path).resolve()
                if member_path.is_absolute() or not resolved.is_relative_to(root):
                    raise RuntimeError(f"Unsafe path in model archive: {member.name}")
                if member.issym() or member.islnk() or member.isdev():
                    raise RuntimeError(f"Unsupported link/device in model archive: {member.name}")
            archive.extractall(destination, members=members)
