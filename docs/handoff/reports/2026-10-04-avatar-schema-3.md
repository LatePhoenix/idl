# Checkpoint — avatar recipe schema 3

**Date:** 2026-10-04  
**Branch:** `avatar/schema-3`  
**Spec:** `docs/AVATAR_RECIPE_SCHEMA.md` and `docs/ROADMAP.md` step 2.

## Acceptance

| Criterion | Status |
| --- | --- |
| Schema 3 fields on `AvatarConfiguration`, with defaults | Met |
| Schema 2 JSON still decodes and migrates | Met |
| A newer schema version is not rewritten | Met |
| Round-trip test | Met |
| Family is not guessed from a display name or from `base_blob` | Met |
| No UI, no Noto import, no renderer | Met |

## What landed

New fields, all defaulted: `packId`, `packVersion`, `familyId`, `itemIds`, `colorOverrides`, `unlinkedSlots`, `itemTransforms`, `background`, `randomSeed`. `schemaVersion` is 3. `renderVersion` stays 2.

`migrateRecipe(baseFamilies)` bumps schema 2 to 3. It sets `familyId` only from the map the caller passes. Schema 4 is returned as decoded. Unknown JSON keys are dropped by `IdlJson` (`ignoreUnknownKeys`), so a caller that must keep a future payload has to retain the original string.

Render keys sort the new maps, so key order does not change the hash.

## Commands

`.\gradlew.bat testDebugUnitTest lintDebug assembleDebug --console=plain`

- 160 tests, 0 failed, 1 skipped
- lint: 0 errors, 39 warnings
- `assembleDebug` succeeded

`scripts/check.sh` could not start: `bash` is not on PATH in this shell (`execvpe(/bin/bash) failed`). The Gradle tasks that script runs were executed directly.

## Deviations

- `familyId` is not read from the manifest yet. The manifest has no family field. The migration takes an explicit map instead.
- Original JSON for a newer schema is not preserved byte-for-byte after decode.

## Commits

Recorded in the pull request for this branch.
