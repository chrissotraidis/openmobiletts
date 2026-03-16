# Audio Import — Edge Cases

## Mismatched Extension and Codec
**Scenario:** File is named `.mp3` but actually contains AAC audio (or vice versa). Common with voice recorder apps that mislabel formats.
**Why it matters:** `MediaExtractor` / ffmpeg may handle this gracefully or may fail depending on the mismatch.
**How we handle it:**
1. Both `MediaExtractor` (Android) and ffmpeg (desktop) are codec-aware — they detect the actual format from file headers, not the extension
2. Extension is used only for routing (audio vs document) — not for codec selection
3. If the actual codec is supported, transcription proceeds normally
4. If truly unreadable, show generic "Could not read audio file" error
**Status:** 🔵 Not built

---

## Stereo / Multi-Channel Audio
**Scenario:** User imports a stereo podcast or multi-channel recording. Moonshine expects mono 16kHz.
**Why it matters:** If not downmixed, one channel could be silence or the channels could contain different speakers.
**How we handle it:**
1. AudioDecoder downmixes to mono during resampling (average channels)
2. Resamples to 16kHz
3. Transparent to the user — no configuration needed
**Status:** 🔵 Not built

---

## High Sample Rate Audio (96kHz, 192kHz)
**Scenario:** Audiophile recordings or professional studio files at very high sample rates.
**Why it matters:** Resampling from 192kHz to 16kHz is a 12x downsample — quality should be fine for speech, but processing takes longer.
**How we handle it:**
1. AudioDecoder resamples to 16kHz regardless of input sample rate
2. No special handling needed — standard resampling
**Status:** 🔵 Not built

---

## Audio File with No Speech (Music Only)
**Scenario:** User imports a music file hoping for lyrics or imports the wrong file.
**Why it matters:** Moonshine will attempt transcription and produce garbage or empty text.
**How we handle it:**
1. Moonshine processes the audio and returns whatever it detects
2. If result is empty, show "No speech detected in audio file"
3. If result is garbage, user sees it in the text area and can delete it
4. No automatic music/speech detection in v3.0
**Status:** 🔵 Not built

---

## Extremely Short Audio File (<1 second)
**Scenario:** User accidentally uploads a click sound or very short clip.
**Why it matters:** Moonshine may not produce meaningful output from sub-second audio.
**How we handle it:**
1. Check audio duration after decoding headers
2. If < 1 second, show "Audio file is too short for transcription"
3. Skip Moonshine processing entirely
**Status:** 🔵 Not built

---

## DRM-Protected Audio
**Scenario:** User uploads DRM-protected audio (e.g., downloaded from a streaming service).
**Why it matters:** `MediaCodec` / ffmpeg cannot decode DRM-encrypted content.
**How we handle it:**
1. Decoding fails with a codec error
2. Show generic "Could not read audio file" error
3. No specific DRM detection or messaging (we don't want to encourage DRM circumvention)
**Status:** 🔵 Not built

---

## Concurrent Import and Recording
**Scenario:** User starts recording via mic, then also tries to upload an audio file (or vice versa).
**Why it matters:** Both operations target the same text area and use the same STT endpoint.
**How we handle it:**
1. Upload button is disabled during active recording
2. Mic button is disabled during active audio import transcription
3. Only one STT operation at a time
**Status:** 🔵 Not built
