# Decision 014: Client library is the visible data source

**Date:** 2026-08-11

**Status:** Accepted

**Decided by:** Project owner and Codex modernization implementation

## Context

The History screen reads entries from browser `localStorage` and audio from
IndexedDB. Settings previously sent cleanup and backup requests to the Python
or Android project store, but the shared client never created those projects.
The resulting backup could be empty while the user visibly had history.

Desktop and Android also serve the same client from different local origins.
A server-owned library would require a migration protocol and would make the
simple local UI dependent on another storage abstraction.

## Decision

The shared client's visible library is the source of truth:

- History metadata and text are stored in `localStorage`.
- Generated audio and timing data are stored in IndexedDB and keyed by history
  entry ID.
- Retention and deletion remove both the visible history entry and its cached
  audio.
- Portable backup includes visible history and portable preferences. It does
  not include audio blobs or the server URL.
- Restore validates the backup, merges entries without duplicating IDs, and
  never silently deletes the current library.
- The existing `/api/projects*` implementations remain legacy compatibility
  surfaces, but the shared client no longer presents them as its backup.

This decision applies independently within each local web origin. Moving data
between desktop and Android requires an explicit backup and restore.

## Rejected alternatives

### Make the backend project store authoritative

Rejected for this stabilization pass because the current client does not use
it for History, and migrating every edit/playback path would add complexity
without improving the visible experience.

### Include audio blobs in JSON backup

Rejected because the cache may be hundreds of megabytes and base64 would make
the file larger and memory-heavy on phones. Audio remains a reproducible cache;
the backup is text, metadata, and portable preferences.

### Keep the existing split

Rejected because it makes backup and cleanup claims untrue.

## Consequences

- Settings can accurately report library entry and cached-audio usage.
- Backup/restore behaves the same on desktop and Android.
- Clearing or aging out a history entry also removes its audio.
- Server/native project stores can be removed or repurposed only through a
  later decision; this record does not delete those compatibility APIs.
