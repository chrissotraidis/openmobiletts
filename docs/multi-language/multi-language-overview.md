# Multilingual TTS

## Current boundary

Multilingual TTS is not an accepted product capability. The Python backend is
configured for one Kokoro language code at process start and defaults to
American English. The Android/sherpa archive includes additional speaker
identifiers, but identifiers are not evidence that text
normalization, pronunciation, quality, licensing, and UI behavior are ready.

The active native UI now exposes only 28 English US/UK voices. The selected
sherpa export documents English and Chinese runtime support, but Chinese stays
hidden until normalization, pronunciation, and hands-on language acceptance.
The UI does not advertise nine languages, 103 speakers, or an unchanged 95 MB
package based on historical planning material.

## Acceptance required per language

1. exact model/checkpoint and runtime;
2. model, voice, and dataset/redistribution terms;
3. representative names, numbers, dates, abbreviations, punctuation, and
   long-form pronunciation;
4. language-specific segmentation and normalization, including CJK behavior;
5. voice labels that match actual language and speaker output;
6. desktop and target-device latency, memory, thermals, and stability; and
7. export, history, backup, and accessibility behavior.

## Direction

First determine which languages users need. Compare existing Kokoro support
with candidate language packs only after the English model bake-off is
repeatable. Prefer optional, independently licensed packs over making every
user download unverified language data.

## Status

🟡 Correctly gated. English remains the documented product baseline; other
languages are research candidates rather than selectable product claims.
