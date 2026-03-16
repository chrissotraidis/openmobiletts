# Decision: JSON Over SQLite for Project Storage

**Date:** March 2026 (from v3.0 spec)
**Who Decided:** Per spec
**Status:** Accepted
**Source:** EXPANSION-PLAN-OPEN-MOBILE-VOICE.md, "Project Storage"

## The Situation

Projects need persistent local storage. Two main options: JSON files (one folder per project) or SQLite database (Room on Android).

## What We Chose

JSON files. One folder per project with `project.json` metadata and `content.txt`.

## What We Rejected

- SQLite / Room (Android-specific abstractions, heavier)
- Single JSON file for all projects (doesn't scale, harder to manage audio files)

## Why

- Portable — easy to backup, transfer, or inspect
- Human-readable — users can see what's stored
- Cross-platform — no Room/Android-specific abstractions (helps if iOS is ever a target)
- Simple — for 10-20 projects, JSON is plenty
- SQLite is built into Android, so migration is straightforward if needed later

## Consequences

- No query capabilities (must scan all folders to list projects)
- Adequate for expected scale (10-20 projects)
- Can migrate to SQLite later if query/indexing needs arise
