# AP-11 · quick creator

**Date:** 2026-10-10 · **Branch:** `avatar/ap-11-quick` · **Spec:** `docs/avatar/AVATAR_PROGRAM.md` AP-11 (c)

## What was implemented

First run, and Start over from the full editor, walk skin, then hair and hair color, then a top. The 48 px head stays on screen. Done saves the recipe. All options opens the full editor.

| Criterion | Result |
| --- | --- |
| Skin, hair and hair color, top, done | met |
| 48 px preview stays visible | met |
| First run and Start over | met |
| Under 2 minutes | met on the emulator, timed in `QuickCreatorTest` |
| Export | not this pull request |

## Files

- `app/src/main/java/app/idl/ui/avatar/AvatarStudioScreen.kt`
- `app/src/androidTest/java/app/idl/ui/avatar/QuickCreatorTest.kt`
- `docs/avatar/EDITOR_NOTE.md`

## Commands

| Command | Result |
| --- | --- |
| `scripts/check.ps1` | 47 Python tests OK. Catalog OK. Gradle: 307 tests, 0 failed, 1 skipped. Lint: 0 errors, 42 warnings. **All checks passed.** |
| `connectedDebugAndroidTest` `QuickCreatorTest` on `emulator-5554` (Pixel 9, API 37) | 1 test, 0 failed. Skin, hair, hair color, top, and save finished inside 2 minutes. |
| SQL | not run. No server change. |

## Deviations

- Start over lives on the full editor. It resets the look and opens the three steps.
- All options leaves the short flow for the full editor without saving yet.

## Known limitations

- Export is the remaining AP-11 pull request.

## Commits

This report is in the same commit as the screen.
