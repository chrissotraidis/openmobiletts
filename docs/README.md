# Documentation Index

## For Users

### Getting Started
- **[Quick Reference](QUICK_REFERENCE.md)** - One-page cheat sheet for common tasks
  - Commands, configuration, troubleshooting
  - Quick answers to "how do I...?"

- **[Setup Guide](SETUP_GUIDE.md)** - Full setup walkthrough
  - Prerequisites and installation
  - Docker and Android alternatives
  - Development mode

- **[How It Works](HOW_IT_WORKS.md)** - Complete system explanation
  - Is the model local or API-based?
  - Architecture diagram and data flow
  - How text becomes speech

- **[Limits & Constraints](LIMITS_AND_CONSTRAINTS.md)** - Performance and limitations
  - Token limits and why they exist
  - Processing speed expectations
  - Memory and storage requirements

### Android
- **[Android App Guide](ANDROID_APP_GUIDE.md)** - Running the app on Android
  - Capacitor setup and build instructions
  - Server connection over WiFi
  - Troubleshooting

## For Developers

- **[Technical Architecture](technical-architecture.md)** - Design decisions and patterns
  - Why Svelte? Why FastAPI? Why Kokoro?
  - Streaming protocol design
  - Android/Capacitor architecture

- **[Implementation Status](implementation-status.md)** - What's built vs. planned
  - Completed features (including Android)
  - Future roadmap (native on-device TTS)

- **[Testing Summary](testing-summary.md)** - Test coverage and results
  - Automated tests
  - Manual testing results
  - Known issues

- **[Migration](MIGRATION.md)** - How we moved from client-server to single app
  - What changed and why
  - What was removed and added

- **[Changelog](CHANGELOG.md)** - History of fixes and improvements

### Roadmap & Future
- **[Roadmap](ROADMAP.md)** - Version plan: v1 (Capacitor + server) → v2 (native on-device TTS)
- **[Implementation Plan](IMPLEMENTATION_PLAN.md)** - Detailed v2 implementation phases with code examples
- **[Offline TTS Feasibility](OFFLINE_TTS_FEASIBILITY.md)** - Research: running Kokoro on-device

## Quick Links

| I want to... | Read this |
|--------------|-----------|
| **Run the app** | [Quick Reference](QUICK_REFERENCE.md#start-the-app) |
| **Run on Android** | [Android App Guide](ANDROID_APP_GUIDE.md) |
| **Understand how it works** | [How It Works](HOW_IT_WORKS.md) |
| **Know the limits** | [Limits & Constraints](LIMITS_AND_CONSTRAINTS.md#quick-reference) |
| **Troubleshoot issues** | [Quick Reference](QUICK_REFERENCE.md#troubleshooting) |
| **See test results** | [Testing Summary](testing-summary.md) |
| **Understand architecture** | [Technical Architecture](technical-architecture.md) |
| **Check what's done** | [Implementation Status](implementation-status.md) |

## Document Purpose

Each document has a specific, non-overlapping purpose:

| Document | Purpose | Audience |
|----------|---------|----------|
| QUICK_REFERENCE | Commands & troubleshooting | Everyone |
| SETUP_GUIDE | Full installation walkthrough | New users |
| HOW_IT_WORKS | System explanation | Users wanting to understand |
| LIMITS_AND_CONSTRAINTS | Performance & limits | Users planning usage |
| ANDROID_APP_GUIDE | Android setup and usage | Android users |
| technical-architecture | Design decisions | Developers |
| implementation-status | Feature completion | Contributors |
| testing-summary | Test results | QA / Developers |
| MIGRATION | Architecture change history | Developers |
| CHANGELOG | Historical changes | Everyone |
| SECURITY_CHECKLIST | Deployment security | Anyone deploying |
| ROADMAP | Version plan (v1 → v2) | Everyone |
| IMPLEMENTATION_PLAN | Detailed v2 phases | Future contributors |
| OFFLINE_TTS_FEASIBILITY | On-device TTS research | Future contributors |
