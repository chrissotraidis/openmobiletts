from __future__ import annotations

"""
JSON-based project storage for user sessions.

Each project is a folder with project.json (metadata) and content.txt (text).
Same folder structure as the Android ProjectStorage.kt — cross-platform compatible.

Default storage location: ~/.openmobilevoice/projects/
"""

import json
import logging
import time
import threading
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)

DEFAULT_PROJECTS_DIR = Path.home() / ".openmobilevoice" / "projects"


class ProjectStorage:
    """JSON-based project CRUD with auto-cleanup and export."""

    def __init__(self, base_dir: Optional[str] = None):
        self._base_dir = Path(base_dir) if base_dir else DEFAULT_PROJECTS_DIR
        self._base_dir.mkdir(parents=True, exist_ok=True)
        self._lock = threading.RLock()  # Reentrant — export_all() calls get() while holding lock

    def create(self, title: str, project_type: str, content: str) -> str:
        """Create a new project. Returns the project ID."""
        with self._lock:
            project_id = self._generate_id()
            project_dir = self._base_dir / project_id
            project_dir.mkdir(parents=True, exist_ok=True)

            now = int(time.time() * 1000)
            metadata = {
                "id": project_id,
                "title": title,
                "type": project_type,
                "created": now,
                "modified": now,
            }

            (project_dir / "project.json").write_text(
                json.dumps(metadata, indent=2), encoding="utf-8"
            )
            (project_dir / "content.txt").write_text(content, encoding="utf-8")

            logger.info(f"Project created: {project_id} ({title})")
            return project_id

    def get(self, project_id: str) -> Optional[dict]:
        """Get a project by ID. Returns None if not found."""
        with self._lock:
            project_dir = self._base_dir / project_id
            meta_file = project_dir / "project.json"
            if not meta_file.exists():
                return None

            try:
                meta = json.loads(meta_file.read_text(encoding="utf-8"))
                content_file = project_dir / "content.txt"
                meta["content"] = content_file.read_text(encoding="utf-8") if content_file.exists() else ""
                return meta
            except Exception as e:
                logger.warning(f"Failed to read project {project_id}: {e}")
                return None

    def list_projects(self) -> list[dict]:
        """List all projects, sorted by modified date (newest first)."""
        with self._lock:
            projects = []
            if not self._base_dir.exists():
                return projects

            for project_dir in self._base_dir.iterdir():
                if not project_dir.is_dir():
                    continue
                meta_file = project_dir / "project.json"
                if not meta_file.exists():
                    continue
                try:
                    meta = json.loads(meta_file.read_text(encoding="utf-8"))
                    projects.append(meta)
                except Exception:
                    continue

            projects.sort(key=lambda p: p.get("modified", 0), reverse=True)
            return projects

    def update(
        self, project_id: str, content: Optional[str] = None, title: Optional[str] = None
    ) -> bool:
        """Update a project's content and/or title. Returns True if found."""
        with self._lock:
            project_dir = self._base_dir / project_id
            meta_file = project_dir / "project.json"
            if not meta_file.exists():
                return False

            try:
                meta = json.loads(meta_file.read_text(encoding="utf-8"))
                meta["modified"] = int(time.time() * 1000)
                if title is not None:
                    meta["title"] = title
                meta_file.write_text(json.dumps(meta, indent=2), encoding="utf-8")

                if content is not None:
                    (project_dir / "content.txt").write_text(content, encoding="utf-8")

                logger.info(f"Project updated: {project_id}")
                return True
            except Exception as e:
                logger.warning(f"Failed to update project {project_id}: {e}")
                return False

    def delete(self, project_id: str) -> bool:
        """Delete a project and all its files."""
        with self._lock:
            project_dir = self._base_dir / project_id
            if not project_dir.exists():
                return False

            import shutil
            shutil.rmtree(project_dir, ignore_errors=True)
            logger.info(f"Project deleted: {project_id}")
            return True

    def cleanup(self, max_age_days: int) -> int:
        """Delete projects older than max_age_days. Returns count deleted."""
        if max_age_days <= 0:
            return 0

        with self._lock:
            cutoff = int(time.time() * 1000) - (max_age_days * 24 * 60 * 60 * 1000)
            deleted = 0

            if not self._base_dir.exists():
                return 0

            for project_dir in list(self._base_dir.iterdir()):
                if not project_dir.is_dir():
                    continue

                meta_file = project_dir / "project.json"
                if not meta_file.exists():
                    import shutil
                    shutil.rmtree(project_dir, ignore_errors=True)
                    deleted += 1
                    continue

                try:
                    meta = json.loads(meta_file.read_text(encoding="utf-8"))
                    modified = meta.get("modified", 0)
                    if 0 < modified < cutoff:
                        import shutil
                        shutil.rmtree(project_dir, ignore_errors=True)
                        deleted += 1
                except Exception:
                    import shutil
                    shutil.rmtree(project_dir, ignore_errors=True)
                    deleted += 1

            if deleted > 0:
                logger.info(f"Auto-cleanup: deleted {deleted} projects older than {max_age_days} days")
            return deleted

    def export_all(self) -> dict:
        """Export all projects as a single dict (metadata + text, no audio)."""
        with self._lock:
            projects = []
            if not self._base_dir.exists():
                return {"exported_at": int(time.time() * 1000), "format_version": 1, "projects": [], "count": 0}
            for project_dir in self._base_dir.iterdir():
                if not project_dir.is_dir():
                    continue
                project = self.get(project_dir.name)
                if project:
                    projects.append(project)

            return {
                "exported_at": int(time.time() * 1000),
                "format_version": 1,
                "projects": projects,
                "count": len(projects),
            }

    def _generate_id(self) -> str:
        import uuid
        return f"proj_{uuid.uuid4().hex[:16]}"
