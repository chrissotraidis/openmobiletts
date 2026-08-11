# Current Kokoro Desktop Baseline

**Run date:** 2026-08-11

**Purpose:** validate the HTTP benchmark harness against the current desktop
Kokoro/PyTorch backend. This is one Mac run and cannot select a model.

| Case | Time to first audio | Real-time factor |
|---|---:|---:|
| Short UI copy | 694.1 ms | 0.1406 |
| Numbers and dates | 1,209.2 ms | 0.1052 |
| Names and abbreviations | 833.7 ms | 0.1282 |
| URL and punctuation | 760.3 ms | 0.1288 |
| Long form | 2,394.4 ms | 0.1035 |

The corrected runner derives audio duration from the maximum timing `end`
value and computes RTF from generation time divided by reported audio duration.
Future cold/warm runs must be labeled separately and must add process memory,
power/thermal state, listening notes, exact model revision, and device details.

See [candidate gate](CANDIDATES.md) and [harness usage](README.md).
