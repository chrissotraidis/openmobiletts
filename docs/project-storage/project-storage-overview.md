# Library Storage and Legacy Project APIs

## Visible product data

The shared client's History library is the user-visible source of truth:

- history metadata and text use browser `localStorage`;
- generated audio and timing use IndexedDB keyed by history ID;
- Settings reports entries, cached-audio count, and cached-audio bytes from
  those same stores;
- retention removes both an aged history entry and its cached audio; and
- portable JSON backup includes history and portable preferences, but not
  audio blobs or the server URL.

Restore validates the file and merges IDs additively. It does not silently
replace the existing library. Each desktop or Android local origin has its own
store; transfer requires explicit backup and restore.

## Legacy compatibility surface

Desktop and Android still implement `/api/projects*` JSON-folder APIs. The
shared client does not create or read these projects, so they are not described
as the History backup. They remain a compatibility surface pending a later
removal or migration decision.

| Endpoint | Purpose |
|---|---|
| `GET/POST /api/projects` | Legacy list/create operations |
| `GET/PUT/DELETE /api/projects/:id` | Legacy item operations |
| `GET /api/projects/export` | Export legacy projects only |
| `POST /api/projects/cleanup` | Clean legacy projects only |

## Retention and backup rules

- Default retention is 30 days; choices include 7, 14, 30, 90 days, or Never.
- Changing retention runs client-library cleanup immediately.
- Backup excludes cached audio to avoid multi-hundred-megabyte base64 JSON on
  phones. Audio remains downloadable per History entry.
- Server connection settings are intentionally device-local and excluded.
- A backup file identifies itself as `openmobiletts-library` with a schema
  version before restore accepts it.

## Status

🟡 Implemented for the visible client library. Automated browser checks cover
backup/retention states and the Settings ownership contract; large libraries,
IndexedDB quota errors, corrupt backups, and cross-device restore still need
manual/device acceptance.

See decision
[014](../decisions/014-client-library-is-the-visible-data-source.md) and the
[data management overview](../data-management/overview.md).
