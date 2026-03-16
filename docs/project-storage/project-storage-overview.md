# Project Storage

## What It Does

Persistent local storage for user sessions — text, transcriptions, generation history. Each project is a folder with JSON metadata and text content. Includes auto-cleanup and data export.

## Why It Matters

Moves the app from ephemeral localStorage/IndexedDB to structured, portable project data. Enables history across sessions and data backup/portability.

## Core Rules

- Format: JSON files, one folder per project (decided — [004](../decisions/004-json-over-sqlite.md))
- Auto-cleanup: configurable in Settings (1 week / 2 weeks / 1 month / 3 months / Never), default 1 month (spec-stated)
- App scans project folders on launch, deletes anything older than configured threshold (spec-stated)
- Project metadata export: single JSON file with all project data (titles, dates, text, settings) — data portability feature (spec-stated)
- Desktop projects stored in `~/.openmobilevoice/projects/` (spec-stated)
- Android projects stored in app's files directory (spec-stated)

### Folder Structure

```
/projects/
  /proj_20260309_143022/
    project.json        # metadata: title, created, modified, type
    content.txt         # the text content
    /audio/             # generated audio files (optional)
      generation_1.aac
```

### Storage Budget

| Component | Size | When |
|-----------|------|------|
| APK | ~50 MB | Install |
| TTS model (Kokoro INT8) | ~95 MB | First launch |
| STT model (Moonshine v2 Medium) | ~250 MB | First launch (default) |
| STT model (Moonshine v2 Small, optional) | ~100 MB | Alternative in Settings |
| Project data | Variable | Ongoing (auto-cleaned) |
| **Total (default)** | **~395 MB** | |
| **Total (with Small STT instead)** | **~245 MB** | |

## New API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `GET /api/projects` | GET | List all projects |
| `POST /api/projects` | POST | Create a new project |
| `GET /api/projects/:id` | GET | Get project details |
| `PUT /api/projects/:id` | PUT | Update project content |
| `DELETE /api/projects/:id` | DELETE | Delete a project |
| `GET /api/projects/export` | GET | Export all project metadata as JSON |
| `POST /api/projects/cleanup` | POST | Run auto-cleanup |

## New Code

### Android (Kotlin)

- **ProjectStorage.kt** — CRUD operations on JSON project folders. Auto-cleanup on launch. Export serialization.

### Desktop (Python)

- **project_storage.py** — Same JSON schema and folder structure as Android. Configurable directory.

### Frontend (SvelteKit)

- **StorageSettings.svelte** — Auto-cleanup interval selector, storage usage display, Export All Projects button, STT model selector with download.

## What's Assumed

- 10-20 projects is typical usage — JSON is plenty at this scale (spec-stated) — Risk if wrong: Low (can migrate to SQLite later)
- Users want temporary storage (days/weeks), not archival — Risk if wrong: Low (auto-cleanup is configurable)

## Key References

- **Source spec:** `docs/EXPANSION-PLAN-OPEN-MOBILE-VOICE.md`, section "Project Storage"
- **Decision:** [004-json-over-sqlite.md](../decisions/004-json-over-sqlite.md)

## Status

🔵 Not Started
