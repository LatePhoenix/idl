# AP-11 · editor session

**Date:** 2026-10-07 · **Branch:** `avatar/ap-11-editor` · **Spec:** `docs/avatar/AVATAR_PROGRAM.md` AP-11 (a), `docs/avatar/EDITOR_NOTE.md`

## What was implemented

The design note and the pure-Kotlin editor session. The screen, quick creator, and export are later pull requests. The old Studio is unchanged.

| Criterion | Result |
| --- | --- |
| Design note before the UI | met — `docs/avatar/EDITOR_NOTE.md` |
| Undo and redo in pure Kotlin | met |
| Seeded randomize, free items only, no conflicts | met — 1,000 seeds |
| Procedural wardrobe hidden (F-37) | met for the grid and randomize |
| Premium try-on, save still refused | met — existing `prepareForWrite` |
| Full editor UI, quick creator, export, device screenshots | not this pull request |

## Files

- `docs/avatar/EDITOR_NOTE.md`
- `app/src/main/java/app/idl/domain/avatar/EditorSession.kt`
- `app/src/test/java/app/idl/domain/avatar/EditorSessionTest.kt`

## Commands

Filled after `scripts/check.ps1`.

## Deviations

- Randomize leaves the base, palette, scene, and frame alone. It changes wardrobe and the resting expression.
- Before/after does not push undo.

## Known limitations

- No screen yet, so there is no device screenshot and the two-minute first-run timing is not measured.
- F-40 (labels instead of raw ids) is the UI pull request. The domain result still carries ids.

## Commits

This report is in the same commit as the session. The hash is in the pull request.
