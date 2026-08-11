# Contributing

Thanks for helping improve Open Mobile TTS. Keep changes focused, local-first,
and easy to review.

## Before changing code

1. Read `docs/status.md`, the relevant feature overview, `docs/unknowns.md`,
   and applicable decision records.
2. Do not change model identity, product privacy, storage ownership, or platform
   behavior without updating or superseding the relevant decision record.
3. Do not add model archives, generated APKs, signing files, user audio, or
   private logs to the repository.

## Desktop checks

```sh
python3 run.py
PYTHONDONTWRITEBYTECODE=1 python3 -m pytest server/tests -p no:cacheprovider -q
cd client
npm ci
npm run check
npm run build
```

## Android check

```sh
cd android
./gradlew :app:assembleDebug :app:assembleRelease --dependency-verification strict --no-daemon
```

The Android build requires Android Studio JBR 21 and SDK/API 36. The release
target is unsigned. Successful assembly does not prove model download,
physical playback, signing, or release acceptance.

## Pull requests

- Explain the user-visible outcome and evidence boundary.
- Include tests or explain why the change is documentation-only.
- Include screenshots for visible UI changes.
- Keep unrelated worktree changes out of the patch.
- Update current docs when behavior changes.

Contributions are submitted under the repository's Apache License 2.0.
