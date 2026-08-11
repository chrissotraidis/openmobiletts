"""Tests for pinned model metadata and safe installation."""

import hashlib
import io
import shutil
import tarfile
import time
from pathlib import Path

import pytest

from src.model_catalog import (
    CATALOG_PATH,
    MODEL_CATALOG,
    NATIVE_TTS_MODEL,
    ModelSpec,
    STT_MODEL,
    is_model_complete,
)
from src.model_installer import ModelInstaller


def _build_model_archive(tmp_path: Path) -> tuple[Path, ModelSpec]:
    model_id = "test-model"
    source = tmp_path / "source" / model_id
    source.mkdir(parents=True)
    for filename in ("encoder.onnx", "tokens.txt"):
        (source / filename).write_bytes(f"test-{filename}".encode())

    archive = tmp_path / "test-model.tar.bz2"
    with tarfile.open(archive, "w:bz2") as bundle:
        bundle.add(source, arcname=model_id)

    data = archive.read_bytes()
    spec = ModelSpec(
        model_id=model_id,
        label="Test Model",
        family="Test",
        version="v1",
        precision="INT8",
        languages=("en",),
        archive_url="https://invalid.example/test-model.tar.bz2",
        archive_bytes=len(data),
        installed_bytes=sum((source / name).stat().st_size for name in ("encoder.onnx", "tokens.txt")),
        sha256=hashlib.sha256(data).hexdigest(),
        required_files=("encoder.onnx", "tokens.txt"),
    )
    return archive, spec


def test_pinned_stt_model_identity_is_truthful():
    assert STT_MODEL.model_id == "sherpa-onnx-moonshine-base-en-int8"
    assert STT_MODEL.version == "v1 Base"
    assert STT_MODEL.languages == ("en",)
    assert STT_MODEL.sha256 == "21870cecaa2e44e4e2bf63e02d1072bed183ccd10284871353bd9d24dad14e5e"


def test_shared_catalog_is_versioned_and_covers_current_models():
    assert CATALOG_PATH.is_file()
    assert MODEL_CATALOG["schema_version"] == 1
    ids = {entry["id"] for entry in MODEL_CATALOG["models"]}
    assert ids == {
        "hexgrad-kokoro-82m-pytorch",
        NATIVE_TTS_MODEL.model_id,
        "kitten-mini-en-v0_8",
        "kitten-micro-en-v0_8",
        STT_MODEL.model_id,
    }
    assert NATIVE_TTS_MODEL.required_directories == ("dict", "espeak-ng-data")
    assert NATIVE_TTS_MODEL.languages == ("en-us", "en-gb")


def test_model_installer_verifies_and_atomically_activates(tmp_path):
    archive, spec = _build_model_archive(tmp_path)
    cache = tmp_path / "cache"
    validated = []
    activated = []

    class LocalInstaller(ModelInstaller):
        def _download(self, model_spec, destination):
            shutil.copyfile(archive, destination)
            self._set_downloaded_bytes(model_spec.archive_bytes)

    installer = LocalInstaller(cache)
    installer.start(
        spec,
        validate=lambda path: validated.append(path.name),
        on_activated=lambda path: activated.append(path.name),
    )

    for _ in range(100):
        status = installer.snapshot(spec)
        if status["status"] in {"installed", "error"}:
            break
        time.sleep(0.01)

    assert status["status"] == "installed", status.get("error")
    assert status["downloaded"] is True
    assert is_model_complete(cache / spec.model_id, spec)
    assert validated == [spec.model_id]
    assert activated == [spec.model_id]
    assert not list(cache.glob(f".{spec.model_id}-*"))


def test_model_installer_rejects_path_traversal(tmp_path):
    archive = tmp_path / "unsafe.tar.bz2"
    with tarfile.open(archive, "w:bz2") as bundle:
        member = tarfile.TarInfo("../outside.txt")
        payload = b"unsafe"
        member.size = len(payload)
        bundle.addfile(member, io.BytesIO(payload))

    with pytest.raises(RuntimeError, match="Unsafe path"):
        ModelInstaller._safe_extract(archive, tmp_path / "extract")

    assert not (tmp_path / "outside.txt").exists()
