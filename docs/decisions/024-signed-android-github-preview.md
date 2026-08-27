# Decision: Signed Android Preview on GitHub Releases

**Date:** 2026-08-27
**Who Decided:** User and Codex
**Status:** Accepted

## The Situation

The Android source, strict build, emulator update, and physical Pixel model
cycle are verified, but the repository does not publish an installable APK.
Android CI currently retains a debug APK and an unsigned release target for
seven days. Neither is an appropriate consumer download.

The wider low/mid-range hardware, fresh-download, background, interruption,
and thermal matrix remains open, so the first public artifact must not be
presented as a stable or Play Store-quality release.

## What We Chose

- Publish `v3.1.0-preview.1` as a GitHub prerelease with one signed, installable
  APK and its SHA-256 checksum.
- Keep model weights out of the APK. First launch continues to download the
  required checksum-pinned Kokoro package.
- Use a dedicated long-lived Android release key. Keep an owner backup outside
  the repository and provide the key and passwords to GitHub Actions only
  through encrypted repository secrets.
- Build tagged releases in GitHub Actions from the exact tagged source, verify
  the version/tag match, APK signature, packaged web entry, shared catalog,
  and arm64 JNI runtime, then publish the assets.
- Keep ordinary Android CI producing debug and unsigned release targets so
  pull requests never require release secrets.

## What We Rejected

- Publishing the unsigned release APK, because Android cannot install it.
- Publishing a debug-signed APK as the consumer release, because debug signing
  does not provide a controlled, durable update identity.
- Bundling hundreds of megabytes of model weights into the APK.
- Calling the first APK stable while documented device and lifecycle gates
  remain open.

## Consequences

- Future updates must use the same release key and a higher Android
  `versionCode`. Losing the key prevents in-place updates to installed copies.
- Locally built debug APKs use a different signing identity. Existing debug
  installs must be removed before installing the GitHub APK, which also removes
  that debug app's private models and data unless the user exports a backup.
- GitHub users can install directly from Releases without Android Studio, ADB,
  Node.js, or a JDK.
- Play Store distribution and stable-release acceptance remain separate work.
