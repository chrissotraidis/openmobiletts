# Visible Library Lifecycle

## Create and reopen

1. A completed generation/transcription adds a History entry in client
   `localStorage`.
2. Generated audio and timing are cached in IndexedDB under the same entry ID.
3. History lists the current origin's entries.
4. Opening an entry restores text and, when cached audio exists, playback.
5. Missing cached audio does not invalidate the text record; it can be
   regenerated.

The shared client does not automatically create a backend JSON project folder.
`/api/projects*` remains a legacy compatibility surface.

## Retention

On startup and when the user changes the retention preference, the client
removes entries older than the selected cutoff and deletes their matching
IndexedDB audio. `Never` disables age-based cleanup. There is no recycle bin.

## Backup and restore

Settings exports a versioned JSON file containing History metadata/text and
portable preferences. Audio blobs and the device-specific server URL are
excluded. Restore validates the product/schema marker and merges by ID without
clearing current entries; the newest 50 entries are retained.

## Acceptance still needed

- large-library performance and IndexedDB quota handling;
- corrupt/foreign/oversized backup tests;
- cross-device desktop-to-Android restore;
- retention boundary and clock-change tests; and
- manual confirmation that delete/cleanup always removes matching cached audio.
