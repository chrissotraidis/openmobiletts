# Decision: WebView Over Jetpack Compose for Android UI

**Date:** 2026 (from existing architecture)
**Who Decided:** Per codebase
**Status:** Accepted
**Source:** ANDROID_ARCHITECTURE.md, "Why WebView?"

## The Situation

The Android app needs a UI. Two approaches: rewrite every screen in Jetpack Compose (native Android UI), or load the existing SvelteKit web app in a WebView.

## What We Chose

WebView. The Android app loads the same SvelteKit build that runs on desktop.

## What We Rejected

- Jetpack Compose — required maintaining two separate UIs, fundamentally unsustainable

## Why

- Zero UI duplication — one codebase for all platforms
- Automatic feature parity — every web UI change works on Android immediately
- The SvelteKit app is already mobile-first (responsive layouts, 44x44px touch targets)
- 8 Kotlin files instead of 24 — only native TTS engine + HTTP server bridge

## Consequences

- All new UI features (v3.0 mic button, export picker, etc.) are Svelte components, not native
- No Jetpack Compose code in the project
- WebView has some limitations (file input needs `onShowFileChooser`, JS throttled when backgrounded)
- Job-based architecture required to handle WebView throttling on background
