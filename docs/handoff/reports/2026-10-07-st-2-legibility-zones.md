# ST-2 PR 4 · zone legibility (F-33)

**Date:** 2026-10-07 · **Branch:** `tools/legibility-zones` · **Spec:** `docs/handoff/ART_IMPORT_TASK.md` PR 4

## What was implemented

`LegibilityTest` measures feature pixels inside the eye and mouth zones instead of counting dark pixels on the whole 48 px canvas.

| Criterion | Result |
| --- | --- |
| A fixture that covers the eyes with a dark shape fails | met — `glasses_test_eye_cover` |
| Every shipped worn item passes | met — vector hair, hats, glasses, facial hair |
| The three importer fixtures pass in a temp pack | met — imported by the test, not into the real pack |
| F-33 marked resolved | met |

Feature pixels are where the neutral face differs from a blank face. The floor is 0.6, the same number as the Studio measure. Eyewear may pass on contrast against nearby unchanged skin, so a 0.35 lens is not treated as a cover. Facial hair may cover the mouth.

## Files

- `app/src/test/java/app/idl/domain/avatar/LegibilityTest.kt`
- `master-plan.md` — F-33 resolved, ST-2 tool work done, work log
- `docs/avatar/AVATAR_PROGRAM.md` — ST-2 ✅. PR 5 still waits

## Commands

| Command | Result |
| --- | --- |
| `./gradlew testDebugUnitTest --tests app.idl.domain.avatar.LegibilityTest` | 2 tests, OK |
| `scripts/check.ps1` | 43 Python tests OK. Catalog OK. Gradle: 299 tests, 0 failed, 1 skipped. Lint: 0 errors, 42 warnings. **All checks passed.** |
| Device / SQL | not applicable. The test is a Robolectric unit test |

## Deviations

- The picture cache now uses the asset's pack directory. The old test read `app/src/main/assets/pictures/`, which is not where the vector pictures live.
- Zone boxes are the Studio guide boxes from 2026-10-07: eyes `[297, 356, 728, 484]` split at x = 512, mouth `[372, 540, 652, 790]`.
- A beard is allowed to cover the mouth. It still has to leave the eyes readable.

## Known limitations

- PR 5, a real studio export, is still blocked. Nothing is in `art/incoming/` except gitignored drops.
- The zones are constants. If the expression shapes move, the boxes should be measured again.

## Commits

This report is in the same commit as the test. The hash is in the pull request.
