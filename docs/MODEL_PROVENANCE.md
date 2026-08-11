# Model and Runtime Provenance

**Last reviewed:** 2026-08-11

The machine-readable source of truth is
[`models/model-catalog.v1.json`](../models/model-catalog.v1.json). Both the
Python server and Android package read that file. This ledger explains the
release boundary around those facts.

## Current application models

| Role | Identity | Runtime | Integrity | Language surface |
|---|---|---|---|---|
| Desktop default TTS | `hexgrad/Kokoro-82M` | `kokoro`/PyTorch | Current Hugging Face revision is not pinned; release blocker for deterministic redistribution | Default process exposes English US only |
| Android/optional desktop TTS | `kokoro-multi-lang-v1_0` | sherpa-onnx 1.13.4 | 349,418,188-byte archive, SHA-256 `c133d26353d776da730870dac7da07dbfc9a5e3bc80cc5e8e83ab6e823be7046`; clean-process Pixel generation passed before and after the full model cycle | Package contains 53 speakers; product exposes 28 English US/UK speakers |
| Android experimental TTS | `kitten-mini-en-v0_8` | sherpa-onnx 1.13.4 | 67,547,594-byte archive, SHA-256 `518f9b130320f690d5b5476df77bde4215fca67773cda16710318e5081234b9d`; clean-process emulator and Pixel generation passed | English only; eight voices |
| Android experimental TTS | `kitten-micro-en-v0_8` | sherpa-onnx 1.13.4 | 44,423,643-byte archive, SHA-256 `85faaea7511ca9d1d2f251fed0a4553bdf0d1ee046102fa60ddd8046c751f76f`; clean-process emulator and Pixel generation passed | English only; eight voices |
| Desktop/Android STT | `sherpa-onnx-moonshine-base-en-int8` | sherpa-onnx 1.13.4 | 250,807,309-byte archive, SHA-256 `21870cecaa2e44e4e2bf63e02d1072bed183ccd10284871353bd9d24dad14e5e` | English only |

The current STT model is Moonshine v1 Base English INT8, not Moonshine v2
Medium. Speaker-name prefixes do not establish language support. The selected
sherpa Kokoro export documents English and Chinese runtime support, but Open
Mobile TTS keeps Chinese hidden until normalization, pronunciation, and hands-
on language acceptance pass.

## License review

| Component | Evidence | Review result |
|---|---|---|
| Repository source | Top-level `LICENSE` | Apache-2.0 |
| Kokoro-82M weights and voices | Upstream [model card](https://huggingface.co/hexgrad/Kokoro-82M) | Apache-2.0 package grant; upstream does not publish a separate per-voice license manifest |
| sherpa Kokoro archive | Archive-root `LICENSE` plus [sherpa catalog](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kokoro.html) | Apache-2.0 archive grant; no separate per-voice manifest inside the archive |
| KittenTTS v0.8 Mini/Micro weights and voices | Upstream [repository](https://github.com/KittenML/KittenTTS), model packages, and [sherpa export](https://k2-fsa.github.io/sherpa/onnx/tts/pretrained_models/kitten.html) | Apache-2.0 package grant; developer-preview models remain experimental and are not bundled in the APK |
| Moonshine v1 English model | [Moonshine repository](https://github.com/moonshine-ai/moonshine) and sherpa export | MIT model terms; sherpa runtime remains Apache-2.0 |
| sherpa-onnx runtime | [k2-fsa/sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) | Apache-2.0 |

This completes the current archive/per-voice document review; it does not
invent provenance that upstream does not provide. The APK and repository do
not bundle downloaded model archives. A future release that redistributes
weights or individual voice files must repeat the review against the exact
artifact and include upstream notices.

## Promotion policy

Before a candidate becomes downloadable or bundled, record in the shared
catalog:

1. exact model/checkpoint and upstream revision;
2. archive and extracted sizes;
3. SHA-256 and required files/directories;
4. runtime/ABI and minimum app version;
5. precision, sample rate, supported languages, and product-exposed languages;
6. code, weights, voice, dataset, and redistribution terms; and
7. the acceptance level required for the feature: intelligible full-duration
   generation in a clean process for an explicitly experimental opt-in, and
   comparative device benchmarks before any default or performance/quality
   claim.

Candidates with gated downloads, non-commercial restrictions, unclear voice
rights, or incompatible copyleft obligations cannot become defaults without a
new decision record. The comparison queue is maintained in
[`benchmarks/CANDIDATES.md`](../benchmarks/CANDIDATES.md).
