# Visible Library Edge Cases

## IndexedDB unavailable or full

History text can remain usable even if audio caching fails. Settings reports
that the audio estimate is unavailable, generation should still offer a direct
download, and cleanup must not claim cached bytes were removed unless the
operation succeeds.

## Missing cached audio

An entry with missing/evicted audio remains valid text. Opening it should offer
regeneration rather than deleting the History record.

## Large library or backup

The client bounds History to the newest 50 entries. Backup omits audio blobs.
Restore validates the marker, schema, entry shapes, and input size before any
merge; corrupt or foreign JSON must leave current data unchanged.

## Retention and clock changes

Retention uses wall-clock timestamps and can be affected by a badly changed
device clock. `Never` is the safe archival choice. Cleanup has no undo, so its
copy must state that entries and cached audio will be removed.

## Multiple local origins

Desktop and Android stores are independent because browser origins differ.
Moving work requires explicit backup and restore; a desktop backend project
export is not a backup of Android History.
