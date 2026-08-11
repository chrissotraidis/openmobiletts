# Settings Flow Audit — 2026-08-11

## Scope

Combined UX and accessibility review of the current Settings screen on the
shared desktop/mobile client. Evidence includes the user-provided narrow-screen
capture and the locally captured desktop state in `01-current-settings.jpg`.

## Current flow

1. **Open Settings — working, visually consistent.** The existing navigation
   makes Settings easy to enter and clearly marks the active destination.
2. **Find a category — high friction.** Six large cards are stacked in one
   page. Reaching Transcription, Data, About, or Logs requires remembering the
   order and repeatedly scrolling.
3. **Change a setting — working, oversized context.** Controls are legible and
   generally have large targets, but each task carries unrelated sections
   above and below it.
4. **Backup or clean data — misleading.** The controls target backend projects
   that the visible History screen does not create, so the apparent action and
   owned data do not match.

## Strengths

- Strong contrast and consistent dark surfaces.
- Clear card headings, familiar icons, and large primary controls.
- Generate/History/Settings remains a simple, understandable product model.

## Highest-impact changes

1. Add five settings categories and show one panel at a time.
2. Use a compact mobile category picker and a desktop category rail.
3. Make Data operate on visible History and its audio cache.
4. Combine About and log controls under one App/Diagnostics panel.
5. Preserve focus visibility and test category navigation at narrow widths,
   keyboard-only, reduced motion, and 200% zoom.

## Evidence limits

Screenshots confirm hierarchy and scroll burden, not screen-reader output,
focus order, contrast ratios, OS text scaling, or touch behavior. Those require
post-implementation browser/device checks.

## Implemented result

The redesigned desktop state is captured in
`02-redesigned-settings-desktop.jpg`; `03-current-vs-redesign.jpg` places the
same 1280-by-720 source and implementation states together. Voice, Connection,
Transcribe, Data, and App navigation worked in the local browser, retention
reported its saved state, and browser logs remained empty. See the repository
root `design-qa.md` for the fidelity and remaining device-test boundary.
