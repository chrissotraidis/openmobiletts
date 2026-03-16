# Project Storage — Edge Cases

## Hundreds of Projects Accumulate
**Scenario:** User sets cleanup to "Never" and accumulates 200+ projects over months.
**Why it matters:** Listing all projects requires scanning all folders and reading all project.json files. Could be slow on app launch.
**How we handle it:**
1. Project listing is cached in memory after first scan
2. Only re-scan on explicit refresh or after create/delete operations
3. JSON file reads are fast — 200 projects at ~1 KB each is ~200 KB total
4. If performance becomes an issue, migrate to SQLite (per [decision 004](../decisions/004-json-over-sqlite.md))
**Status:** 🔵 Not built

---

## Project Folder Manually Deleted
**Scenario:** User (or another app) deletes a project folder from the filesystem outside the app.
**Why it matters:** App's in-memory project list becomes stale — referencing a deleted project causes errors.
**How we handle it:**
1. When accessing a specific project (GET /api/projects/:id), check if folder exists
2. If folder is missing, return 404 and remove from cached list
3. Periodic re-scan on app launch catches deletions
**Status:** 🔵 Not built

---

## Audio Files Grow Large
**Scenario:** User generates many TTS audio files within a single project (e.g., regenerating with different voices).
**Why it matters:** A single project's `audio/` folder could grow to hundreds of MB.
**How we handle it:**
1. Storage usage display in Settings shows total project storage
2. Individual project detail view could show audio file count/size (future enhancement)
3. Auto-cleanup handles this at the project level — when the project is deleted, all audio goes with it
4. No per-file cleanup within a project in v3.0
**Status:** 🔵 Not built

---

## Export All with Large Audio Files
**Scenario:** "Export All Projects" is tapped, but projects contain large audio files.
**Why it matters:** Including audio binary data in a JSON export could produce a multi-GB file.
**How we handle it:**
1. Export includes **metadata and text only** — not audio files
2. This is explicitly a "data portability" feature, not a full backup
3. Audio files must be downloaded individually from history
4. Export JSON includes project metadata (title, dates, type, settings) and text content
**Status:** 🔵 Not built

---

## Cleanup Runs During Active Use
**Scenario:** Auto-cleanup fires on app launch while user is actively viewing/editing a project that's past the cleanup threshold.
**Why it matters:** Deleting a project the user is actively viewing would cause data loss and UI errors.
**How we handle it:**
1. Cleanup runs before the UI loads (during app initialization)
2. Once the UI is loaded and user is interacting, no background cleanup occurs
3. If a project is opened from history, its `modified` date is updated — preventing cleanup
**Status:** 🔵 Not built

---

## Clock Manipulation / Timezone Changes
**Scenario:** User changes device clock or travels across time zones. Project timestamps become inconsistent with cleanup logic.
**Why it matters:** A project created "yesterday" could appear as "1 month old" if the clock was set forward, triggering premature cleanup.
**How we handle it:**
1. Use monotonic timestamps where possible, but acknowledge this is a minor risk
2. Auto-cleanup compares `modified` timestamp to current time — if the clock is wrong, cleanup logic is wrong
3. Mitigation: the default cleanup interval (1 month) provides a large buffer
4. Edge case accepted — not worth adding complexity for this scenario
**Status:** 🔵 Not built — accepted risk
