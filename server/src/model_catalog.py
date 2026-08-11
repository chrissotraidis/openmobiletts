"""Load and validate the repository-owned model catalog."""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path
from typing import Any


_REPOSITORY_CATALOG = Path(__file__).resolve().parents[2] / "models" / "model-catalog.v1.json"
_PACKAGED_CATALOG = Path(__file__).resolve().parents[1] / "models" / "model-catalog.v1.json"
CATALOG_PATH = Path(
    os.getenv(
        "MODEL_CATALOG_PATH",
        str(_REPOSITORY_CATALOG if _REPOSITORY_CATALOG.is_file() else _PACKAGED_CATALOG),
    )
)


@dataclass(frozen=True)
class ModelSpec:
    """Immutable metadata used for managed model status and installation."""

    model_id: str
    label: str
    family: str
    version: str
    precision: str
    languages: tuple[str, ...]
    archive_url: str
    archive_bytes: int
    installed_bytes: int
    sha256: str
    required_files: tuple[str, ...]
    required_directories: tuple[str, ...] = ()
    role: str = ""
    runtime: str = ""
    minimum_runtime_version: str = ""
    source_url: str = ""
    weights_license: str = ""
    archive_license: str = ""
    voice_license: str = ""
    platforms: tuple[str, ...] = ()
    minimum_app_version: str = ""
    smoke_test: str = ""
    migration: str = ""
    rollback: str = ""

    @property
    def archive_size_mb(self) -> float:
        return round(self.archive_bytes / (1024 * 1024), 1)

    @property
    def installed_size_mb(self) -> float:
        return round(self.installed_bytes / (1024 * 1024), 1)

    def install_dir(self, cache_root: Path) -> Path:
        return cache_root / self.model_id


def _load_catalog() -> dict[str, Any]:
    try:
        catalog = json.loads(CATALOG_PATH.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise RuntimeError(f"Unable to load model catalog at {CATALOG_PATH}: {exc}") from exc

    if catalog.get("schema_version") != 1:
        raise RuntimeError("Unsupported model catalog schema")
    entries = catalog.get("models")
    if not isinstance(entries, list) or not entries:
        raise RuntimeError("Model catalog must contain a non-empty models list")

    ids = [entry.get("id") for entry in entries if isinstance(entry, dict)]
    if len(ids) != len(entries) or any(not value for value in ids) or len(set(ids)) != len(ids):
        raise RuntimeError("Model catalog IDs must be present and unique")
    return catalog


MODEL_CATALOG = _load_catalog()
MODEL_ENTRIES = {entry["id"]: entry for entry in MODEL_CATALOG["models"]}


def model_entry(model_id: str) -> dict[str, Any]:
    """Return the checked catalog entry for ``model_id``."""
    try:
        return MODEL_ENTRIES[model_id]
    except KeyError as exc:
        raise RuntimeError(f"Model is missing from the shared catalog: {model_id}") from exc


def managed_model_spec(model_id: str) -> ModelSpec:
    """Build an installer specification from one downloadable catalog entry."""
    entry = model_entry(model_id)
    archive_url = entry.get("archive_url")
    archive_bytes = entry.get("archive_bytes")
    installed_bytes = entry.get("installed_bytes")
    sha256 = entry.get("sha256")
    if not archive_url or not isinstance(archive_bytes, int) or not isinstance(installed_bytes, int) or not sha256:
        raise RuntimeError(f"Managed model metadata is incomplete: {model_id}")

    runtime = entry.get("runtime") or {}
    license_info = entry.get("license") or {}
    source = entry.get("source") or {}
    return ModelSpec(
        model_id=entry["id"],
        label=entry["label"],
        family=entry["family"],
        version=entry["version"],
        precision=entry["precision"],
        languages=tuple(entry.get("exposed_languages", [])),
        archive_url=archive_url,
        archive_bytes=archive_bytes,
        installed_bytes=installed_bytes,
        sha256=sha256,
        required_files=tuple(entry.get("required_files", [])),
        required_directories=tuple(entry.get("required_directories", [])),
        role=entry.get("role", ""),
        runtime=runtime.get("name", ""),
        minimum_runtime_version=runtime.get("minimum_version", ""),
        source_url=source.get("url", ""),
        weights_license=license_info.get("weights", ""),
        archive_license=license_info.get("archive", ""),
        voice_license=license_info.get("voices", ""),
        platforms=tuple(entry.get("platforms", [])),
        minimum_app_version=entry.get("minimum_app_version", ""),
        smoke_test=entry.get("smoke_test", ""),
        migration=entry.get("migration", ""),
        rollback=entry.get("rollback", ""),
    )


NATIVE_TTS_MODEL = managed_model_spec("kokoro-multi-lang-v1_0")
STT_MODEL = managed_model_spec("sherpa-onnx-moonshine-base-en-int8")


def is_model_complete(model_dir: Path, spec: ModelSpec = STT_MODEL) -> bool:
    """Return true only when every required model path exists and is non-empty."""
    files_complete = all(
        (model_dir / filename).is_file() and (model_dir / filename).stat().st_size > 0
        for filename in spec.required_files
    )
    directories_complete = all(
        (model_dir / dirname).is_dir() and any((model_dir / dirname).iterdir())
        for dirname in spec.required_directories
    )
    return model_dir.is_dir() and files_complete and directories_complete
