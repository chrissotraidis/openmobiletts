# About This Documentation

This project uses **Arnold** for documentation-first development. The `docs/` folder is the source of truth for what this project should be and how it should behave.

## How Docs Are Organized

Docs are organized by **feature**, not by document type:

```
docs/
├── overview.md              Project vision and goals
├── status.md                Feature statuses and check history
├── ABOUT.md                 This file
├── [feature]/[feature]-overview.md    Rules, assumptions, status per feature
├── [feature]/[flow].md      Step-by-step user flows
├── decisions/NNN-title.md   Why we chose what we chose
└── unknowns.md              Open questions and bets
```

## Status Markers

- 🟢 Implemented — working, documented, aligned
- 🟡 In Progress — partially built or partially documented
- 🔵 Not Started — documented but no code yet
- 🔴 Drifted — docs and code don't match
- ❓ Unknown — depends on an unresolved question

## Source Provenance

Rules are tagged with where they came from:

- **(spec-stated)** — explicitly in the v3.0 expansion spec
- **(code-derived)** — extracted from reading the existing codebase
- **(user-stated)** — the human explicitly said this
- **(decided)** — deliberate choice, links to decision record
- **(Arnold-inferred)** — Claude reasoned this should exist

## Arnold Commands

- `/arnold:check` — compare docs to code, find drift
- `/arnold:update` — sync docs after coding
- `/arnold:plan` — flesh out thin docs (flows, edge cases)
- `/arnold:status` — quick project overview
- `/arnold:decide` — record a decision
- `/arnold:resolve` — fix drift items
- `/arnold:recap` — start-of-session briefing

## Pre-Arnold Reference Docs

Pre-Arnold docs that are still accurate and useful live in `docs/_reference/`:

- `EXPANSION-PLAN-OPEN-MOBILE-VOICE.md` — v3.0 spec (canonical)
- `technical-architecture.md` — v2.0 system architecture
- `ANDROID_ARCHITECTURE.md` — Android WebView bridge details
- `HOW_IT_WORKS.md` — v2.0 user-facing explanation
- `ROADMAP.md` — version history and multi-language plans
- `LIMITS_AND_CONSTRAINTS.md` — performance and resource limits
- `SETUP_GUIDE.md`, `QUICK_REFERENCE.md` — user guides
- `SECURITY_CHECKLIST.md` — deployment security notes
- `CHANGELOG.md` — version history
- `testing-summary.md` — test coverage and results

## Archived Docs

Stale docs that described superseded architecture (Capacitor, Jetpack Compose, old doc structure) live in `docs/_archive/`. Each has an archive header noting it is for historical reference only.
