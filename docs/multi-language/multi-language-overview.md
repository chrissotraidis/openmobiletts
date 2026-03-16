# Multi-Language TTS

## What It Does

Extends TTS from English-only to 9 languages supported by Kokoro-82M: American English, British English, Spanish, French, Hindi, Italian, Japanese, Brazilian Portuguese, and Mandarin Chinese.

## Why It Matters

Unlocks the app for non-English speakers. The TTS engine and models already support these languages — the work is in text preprocessing and UI.

## Core Rules

- Kokoro-82M natively supports 9 languages with 103 speakers (code-derived)
- Sherpa-ONNX ships `kokoro-multi-lang-v1_1` model — same ~95 MB, all languages included (code-derived)
- Same model size as English-only — no additional download for multi-language on Android (code-derived)
- Japanese and Chinese require custom text chunking (no word boundaries/spaces) (code-derived)
- `num2words` supports most languages via `lang` parameter (code-derived)
- Per-language abbreviation expansion and punctuation rules needed (code-derived)

### Language & Voice Inventory

| Code | Language | Voices | Phonemizer | Notes |
|------|----------|--------|------------|-------|
| `a` | American English | 11 | Misaki (custom) | Current default |
| `b` | British English | 8 | Misaki (custom) | |
| `e` | Spanish | 2 | espeak-ng | |
| `f` | French | 1 | espeak-ng | |
| `h` | Hindi | 4 | espeak-ng | |
| `i` | Italian | 2 | espeak-ng | |
| `j` | Japanese | 5 | Misaki JAG2P | No spaces — custom chunking |
| `p` | Brazilian Portuguese | 3 | espeak-ng | |
| `z` | Mandarin Chinese | 8 | Misaki ZHG2P | No spaces — custom chunking |

### Implementation Phases

1. **M1: Language selection + voice routing** — Add language dropdown, extend voice map, filter voice picker by language. This alone gets working multilingual TTS.
2. **M2: Per-language text preprocessing** — Language-aware preprocessor, per-language abbreviation dicts, CJK chunking adjustments.
3. **M3: Auto language detection** (optional) — Lightweight offline detection via `langdetect` or `lingua-py`.
4. **M4: UI localization** (separate) — Translate app interface strings. Only if non-English user base emerges.

### Recommended Sequencing

M1 first (high value, contained scope) → M2 language-by-language (bulk of work) → M3 nice-to-have → M4 separate concern.

## Key References

- **Source:** `docs/ROADMAP.md`, "Multi-Language Support (v2)"
- **TTS engine:** `server/src/tts_engine.py`
- **Text preprocessor:** `server/src/text_preprocessor.py`

## Status

🔵 Not Started
