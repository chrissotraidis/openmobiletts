# Batch Transcription — Edge Cases

## Large Batch
**Scenario:** User selects dozens of audio files.
**Why it matters:** Local transcription is CPU-bound and can take time.
**How we handle it:**
1. Process files sequentially.
2. Save uploaded files into a local batch workspace.
3. Expose a job status endpoint for progress polling.
4. Return a ZIP download after the batch completes.
**Status:** 🟢 Implemented

---

## Unknown Speakers
**Scenario:** A call has multiple speakers but no known speaker identities.
**Why it matters:** The transcript should not pretend it knows who is speaking.
**How we handle it:**
1. v1 exports plain transcript without speaker labels.
2. Future diarization exports `Speaker 1`, `Speaker 2`, etc.
3. Future UI lets user rename speaker labels.
**Status:** 🔵 Not built

---

## Overlapping Speech
**Scenario:** Two people talk at the same time.
**Why it matters:** Diarization and transcription quality both degrade on overlap.
**How we handle it:**
1. Mark diarization as best-effort.
2. Preserve timestamps when available.
3. Let users edit transcripts after export.
**Status:** 🔵 Not built

---

## ZIP Filename Collision
**Scenario:** Two source files produce the same Markdown filename.
**Why it matters:** ZIP entries must not overwrite each other.
**How we handle it:**
1. Keep the first normalized filename unchanged.
2. Append `-2`, `-3`, etc. for duplicates.
3. Record original filenames in transcript metadata.
**Status:** 🟢 Implemented
