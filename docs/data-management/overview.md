# Data Management

## Purpose

Data Management controls the library visible in History. It must never report
an empty backend store as a successful backup of visible user work.

## Owned data

| Data | Store | Portable backup |
|---|---|---|
| History title, text, voice, speed, timestamps | Client `localStorage` | Yes |
| Generated audio and timing metadata | Client IndexedDB | No; cache only |
| Voice, engine, auto-play, retention preferences | Client `localStorage` | Yes |
| Server connection URL | Client `localStorage` | No; device-specific |
| Legacy backend projects | Python/Android app storage | No; not visible in History |

This ownership follows [Decision 014](../decisions/014-client-library-is-the-visible-data-source.md).

## Backup flow

1. Read the current visible history and portable preferences.
2. Write a versioned `openmobiletts-library` JSON document.
3. Download it through the browser or Android file bridge.
4. Clearly state that generated audio is not included.

## Restore flow

1. The user selects a JSON backup.
2. Validate the product marker, format version, entry fields, and size.
3. Merge history by ID, preferring the restored record for a matching ID.
4. Keep the newest 50 entries, matching the library limit.
5. Apply only portable preferences and keep the device's server URL.
6. Report how many entries were restored or explain the validation error.

Restore is additive. It does not silently clear current work.

## Retention flow

The selected retention period is a local preference. On app startup and when
the value changes, entries older than the cutoff are removed from History and
their audio-cache records are deleted. `Never` disables age-based cleanup.

## Edge cases

- Missing audio is allowed; History can regenerate it.
- Corrupt or foreign JSON is rejected without changing current data.
- A backup with more than 50 entries imports only the newest 50.
- Storage estimates may be unavailable if IndexedDB access fails; the library
  remains usable and Settings reports the estimate as unavailable.
