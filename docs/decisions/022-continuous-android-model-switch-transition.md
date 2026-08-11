# Decision 022: Continuous Android model-switch transition

**Date:** 2026-08-11
**Status:** Accepted
**Decided by:** Project owner

## Context

Decision 021 requires a clean Android process when changing TTS models because
releasing and recreating sherpa TTS engines in one process corrupts later
synthesis. Physical testing confirmed that safety boundary, but the first UI
implementation briefly showed a blank screen and returned users to Generate.
That looked like a crash and discarded the Models context that initiated the
change.

## Decision

- Keep the clean-process model switch required by Decision 021.
- Show an opaque, branded native transition while Android replaces the
  inference process, including the selected model name and visible progress.
- Persist one short-lived, one-shot client resume context before the restart.
- Relaunch into Settings > Models, restore the selected model card in view, and
  show **Active and ready** after switching from the Models panel.
- A switch initiated from the Voice engine selector returns to Settings >
  Voice instead.
- Clear the resume context after it is consumed so ordinary launches continue
  to open Generate.

## Rejected

- Returning to unsafe in-process hot swapping to avoid a visual transition.
- Always restoring the last open page on every launch.
- Showing the Android launcher or an unlabelled black screen during the switch.

## Consequences

Model activation remains a real process restart, but it reads as one deliberate
flow. Draft text, History, downloaded models, and normal preferences remain
untouched. The one-shot resume record contains only a model ID, model label,
destination section, and timestamp; it expires if it is stale.
