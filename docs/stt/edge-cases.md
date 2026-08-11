# STT Edge Cases

## Permission permanently denied

Android may stop showing its permission dialog after a permanent denial. The
client explains the problem, but a direct action that opens the app's system
settings remains unverified. This is a physical-device acceptance item.

## Interrupted model download

Android uses unique WorkManager jobs with a connected-network constraint,
foreground notification, exponential retry, and durable progress. The partial
archive remains in app-private storage. A retry requests the remaining bytes;
if the host does not honor the Range request, the file is safely overwritten.
A checksum mismatch deletes the invalid partial file.

Desktop uses a pinned background installer with status polling, safe extraction,
required-file validation, and activation rollback. Desktop resume is not yet a
product claim.

## Initialization failure

Presence of model files is not sufficient evidence that JNI/Python can load
them. The installer validates archive integrity and required files, retains the
previous active model during candidate activation, and reports a local error.
A dedicated repair/delete UI and a fresh Android native load-smoke test remain
open.

## Empty or silent recording

An empty transcript must not erase existing editable text. Minimum recording
duration, silence detection, and hallucination behavior require explicit tests
with the same recordings used by the benchmark harness.

## Long recordings

Batch uploads and decoded PCM can create memory pressure. Android now rejects
inputs over 15 minutes or 256 MiB and feeds Moonshine overlapping 25-second
windows. Before release, measure 5- and 15-minute samples and verify
cancellation, temporary-file cleanup, and peak memory on target phones.
Desktop limits remain independently configurable.

## Concurrent TTS and STT

The UI should prevent recording and generation from starting over each other.
Native TTS and STT managers serialize access to their own non-thread-safe
sherpa objects, but simultaneous model RAM, audio focus, and thermal behavior
still require device evidence.

## Unsupported language

The current model is English-only Moonshine v1 Base INT8. Non-English audio may
produce unusable text; the app must not imply automatic language detection or
multilingual accuracy. A multilingual model may be offered later only after
license and device benchmarks.
