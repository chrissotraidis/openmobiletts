# Project Lifecycle Flow

## Who

A user who generates text or audio across multiple sessions and wants their work to persist.

## The Happy Path

### Creating a Project (automatic)

1. User performs any action that produces content:
   - Generates TTS audio from text
   - Transcribes speech via mic
   - Transcribes an imported audio file
   - Imports a document
2. App **automatically creates a project** — no manual "New Project" step
3. Project folder is created with:
   - `project.json` — metadata (auto-generated title from first line, creation date, type: tts/stt/import)
   - `content.txt` — the text content
   - `audio/` — generated audio files (if TTS)
4. Project appears in History tab

### Reopening a Project

1. User navigates to History tab
2. Taps a history card (TTS generation, STT transcription, or export)
3. Detail view opens with full text, playback controls (if audio), and action buttons
4. User can: replay audio, edit text, export, or generate speech from the text
5. Changes are saved back to the project (`PUT /api/projects/:id`)

### Auto-Cleanup

1. App launches
2. On startup, `ProjectStorage` scans all project folders
3. Checks each project's `modified` date against the configured cleanup threshold
4. Projects older than the threshold are deleted (files removed from disk)
5. Default threshold: 1 month (configurable in Settings)

### Exporting All Projects

1. User navigates to Settings → Storage
2. Taps "Export All Projects (JSON)"
3. App serializes all project metadata and text content into a single JSON file
4. File is shared (Android) or downloaded (desktop)

## What Could Go Wrong

### Disk full during project creation
- **When:** Device storage is full when trying to create a project folder or save audio
- **What happens:** File write fails. App shows error: "Not enough storage space."
- **Recovery:** User frees space (delete old projects, clear other app data) and retries.

### Corrupt project.json
- **When:** App crash or power loss during a project.json write leaves a malformed file
- **What happens:** Project listing skips the corrupt project. Log a warning.
- **Recovery:** On next cleanup pass, corrupt projects (unreadable JSON) are deleted. User loses that project's metadata but content.txt may still be recoverable.

### Cleanup deletes project user wanted to keep
- **When:** User has the default 1-month cleanup and forgets about an old project
- **What happens:** Project is automatically deleted on app launch
- **Recovery:** Prevention: set cleanup interval to "Never" in Settings. Once deleted, data is gone — no recycle bin in v3.0.

### Concurrent access (unlikely but possible)
- **When:** Two operations try to modify the same project simultaneously (e.g., auto-save while user exports)
- **What happens:** JSON file writes are not atomic — could produce corrupt file
- **Recovery:** Use write-then-rename pattern (write to temp file, rename to project.json) for atomic writes. ProjectStorage serializes access via a lock.

## Acceptance Criteria

- [ ] Projects are automatically created when TTS generates, mic transcribes, or audio imports
- [ ] Each project has a folder with project.json, content.txt, and optional audio/
- [ ] History tab shows all projects with correct type badges (TTS/STT/Export)
- [ ] Tapping a history card opens the project with full content
- [ ] Text edits are saved back to the project
- [ ] Auto-cleanup deletes projects older than the configured threshold
- [ ] Cleanup interval is configurable in Settings (1 week / 2 weeks / 1 month / 3 months / Never)
- [ ] "Export All Projects" produces a valid JSON file with all project data
- [ ] Storage usage is displayed in Settings
- [ ] Corrupt project.json files are handled gracefully (skipped, not crash)

## Related

- See: [Project Storage Overview](project-storage-overview.md) for folder structure and API endpoints
- See: [Audio Playback](../audio-playback/audio-playback-overview.md) for history card types
- Depends on: All content-producing features (TTS, STT, Audio Import, Document Import)
