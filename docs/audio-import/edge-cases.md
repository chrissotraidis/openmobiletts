# Audio Import Edge Cases

## Mismatched extension and codec

The extension selects the audio workflow; MediaExtractor/ffmpeg still inspect
the actual container and codec. A readable mismatch may work. An unreadable
file returns a local error without replacing current text. Representative
error-copy acceptance remains open.

## Stereo and high sample rates

Android averages channels to mono and linearly resamples to 16 kHz. The decoder
checks observed sample count against the 15-minute limit while draining codec
output. High-rate/multi-channel files still need target-device performance
coverage.

## No speech or very short audio

Moonshine may return empty or poor text for silence, music, or sub-second
clips. Empty output must not erase current text. Silence detection, minimum
duration, and hallucination tests remain part of the model bake-off rather
than being hidden by an LLM rewrite.

## DRM and unsupported codecs

The app does not circumvent DRM. Codec/decoder failure is reported as an
unsupported or unreadable local file.

## Oversized and long input

Android rejects source files above 256 MiB and audio above 15 minutes. This is
a conservative stabilization boundary, not a quality claim. Users must split
larger recordings. Desktop limits remain separately configurable.

## Concurrent operations and interruption

Native TTS and STT managers serialize access to their own sherpa objects, but
the model RAM, audio-focus, cancellation, background, and process-death paths
still require physical-device acceptance.
