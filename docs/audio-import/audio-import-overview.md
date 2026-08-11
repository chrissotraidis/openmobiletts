# Audio Import

## Current behavior

The Generate upload action accepts supported audio alongside documents and
routes audio to local speech-to-text. Desktop decodes through its audio stack;
Android uses `MediaExtractor`/`MediaCodec`, mixes/resamples to Moonshine's
16 kHz mono input, and returns editable text.

The active STT model is Moonshine v1 Base English INT8. Import is therefore
English-only and experimental.

## Supported surface

- Common inputs include MP3, AAC, OGG, and WAV, subject to codecs available on
  the active platform.
- The client chooses the audio route from the selected file; the decoder still
  validates whether the content can be read.
- The transcript replaces or populates the shared editable text area, where it
  can be corrected, exported, or synthesized.
- Android declares audio import in its platform-capability response.

## Current limits

Android accepts at most 15 minutes and 256 MiB per input, validates duration
while decoding, and sends Moonshine overlapping 25-second windows. It still
materializes one bounded 16 kHz float array, so 5/15-minute peak-memory,
cancellation, and physical-device acceptance remain. Music, silence, mislabeled
codecs, DRM, and unsupported formats must fail without erasing current text.

## Status

🟡 Bounded implementation. Representative short-file functional checks,
5/15-minute memory tests, error copy, cancellation, and physical Android
acceptance remain open.
