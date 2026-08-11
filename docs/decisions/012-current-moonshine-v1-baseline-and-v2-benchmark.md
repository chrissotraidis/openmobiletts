# Decision: Keep Moonshine v1 as the Truthful Baseline and Benchmark v2

**Date:** 2026-08-11
**Who Decided:** Phase Zero modernization implementation
**Status:** Accepted; supersedes Decision 001
**Source:** Verified model archive, runtime configuration, and modernization audit

## The Situation

Decision 001 selected Moonshine v2, but both active backends actually load
`sherpa-onnx-moonshine-base-en-int8`, the older English Moonshine v1 Base
export. Treating the planned model as the installed model made API, UI, size,
and quality claims incorrect.

## What We Chose

Keep the installed Moonshine v1 Base English INT8 model as the truthful
experimental baseline until measured device benchmarks justify a replacement.
Benchmark Moonshine v2 Streaming Small and Medium during Phase One rather than
changing the default from planning evidence alone.

## What We Rejected

- Continuing to label the v1 archive as v2.
- Replacing the default before Android/desktop quality, latency, RAM, storage,
  battery, and thermal measurements exist.
- Treating one STT engine as mandatory for every future platform.

## Consequences

- Product metadata matches the files users actually download.
- Decision 001 remains historical context but is no longer operative.
- v2 remains the primary English STT benchmark candidate, not a shipped claim.
