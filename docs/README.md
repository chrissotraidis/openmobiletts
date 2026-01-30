# Documentation Index

## For Users

### Getting Started
- **[Quick Reference](QUICK_REFERENCE.md)** - One-page cheat sheet for common tasks
  - Commands, endpoints, troubleshooting
  - Quick answers to "how do I...?"

- **[How It Works](HOW_IT_WORKS.md)** - Complete system explanation
  - Is the model local or API-based?
  - Architecture diagram and data flow
  - Where files are stored
  - How text becomes speech

- **[Limits & Constraints](LIMITS_AND_CONSTRAINTS.md)** - Performance and limitations
  - Token limits and why they exist
  - Processing speed expectations
  - Memory and storage requirements
  - Comparison to cloud TTS

## For Developers

- **[Technical Architecture](technical-architecture.md)** - Design decisions and patterns
  - Why Svelte? Why FastAPI?
  - Streaming protocol design
  - Security considerations

- **[Implementation Status](implementation-status.md)** - What's built vs. planned
  - Completed features
  - In-progress work
  - Future roadmap

- **[Testing Summary](testing-summary.md)** - Test coverage and results
  - Unit tests (24 passing)
  - Integration tests
  - Known issues

- **[Changelog](CHANGELOG.md)** - History of fixes and improvements
  - Major bugs fixed
  - Features added
  - Breaking changes

## Component Documentation

- **[Server README](../server/README.md)** - Backend setup and API
- **[Client README](../client/README.md)** - Frontend setup and architecture

## Quick Links

| I want to... | Read this |
|--------------|-----------|
| **Understand how it works** | [How It Works](HOW_IT_WORKS.md) |
| **Know the limits** | [Limits & Constraints](LIMITS_AND_CONSTRAINTS.md#quick-reference) |
| **Run the servers** | [Quick Reference](QUICK_REFERENCE.md#common-commands) |
| **Troubleshoot issues** | [Quick Reference](QUICK_REFERENCE.md#troubleshooting) |
| **Set up the server** | [Server README](../server/README.md) |
| **Set up the client** | [Client README](../client/README.md) |
| **See test results** | [Testing Summary](testing-summary.md) |
| **Understand architecture** | [Technical Architecture](technical-architecture.md) |
| **Check what's done** | [Implementation Status](implementation-status.md) |

## Document Purpose

Each document has a specific, non-overlapping purpose:

| Document | Purpose | Audience |
|----------|---------|----------|
| QUICK_REFERENCE | Commands & troubleshooting | Everyone |
| HOW_IT_WORKS | System explanation | Users wanting to understand |
| LIMITS_AND_CONSTRAINTS | Performance & limits | Users planning usage |
| technical-architecture | Design decisions | Developers |
| implementation-status | Feature completion | Project managers |
| testing-summary | Test results | QA / Developers |
| CHANGELOG | Historical changes | Everyone |
