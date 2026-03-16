# Decision: No .docx Export

**Date:** March 2026 (from v3.0 spec)
**Who Decided:** Per spec
**Status:** Accepted
**Source:** EXPANSION-PLAN-OPEN-MOBILE-VOICE.md, "Document Export"

## The Situation

Document export needs to support common formats. .docx is widely used but complex to generate programmatically, especially on Android.

## What We Chose

Export PDF, Markdown (.md), and plain text (.txt) only. No .docx.

## What We Rejected

- .docx export via Apache POI (~10 MB JAR, bloated)
- .docx export via manual XML construction (fragile)

## Why

- Android has no clean, lightweight library for .docx generation
- Apache POI adds ~10 MB to the APK for a single feature
- Manual OOXML construction is error-prone and hard to maintain
- PDF covers the "formal document" use case
- Users who need .docx can export .md and convert externally

## Consequences

- Users who specifically need .docx must use an external converter
- Keeps APK lean and dependency-free for document export
- May revisit if a lightweight Android .docx library emerges
