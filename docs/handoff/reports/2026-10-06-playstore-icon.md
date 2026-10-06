# Checkpoint report: Play Store icon (2026-10-06)

Branch `brand/playstore-icon`, from `origin/main` `c72ae8f`. Author: Claude. Follows D-44.

## What was implemented

| Requirement | Status |
|---|---|
| 512×512 Google Play icon | ✅ `docs/art/brand/playstore-icon-512.png`: 512×512, 8-bit RGBA (32-bit) PNG, 16 KB |
| Play rules: full-bleed square, no baked-in corners or shadow | ✅ Corners are the opaque cream background. Play applies its own mask |
| Matches the home-screen icon | ✅ Rendered from the same drawables as the launcher: the centre 72 units of the adaptive canvas, without the circle mask |
| Can't silently go stale | ✅ `LauncherIconSnapshotTest` re-renders it and fails if more than 0.1% of the pixels differ from the committed file |

## Files

- Created: `docs/art/brand/playstore-icon-512.png`,
  `app/src/test/snapshots/brand/launcher_playstore_512.png`, this report
- Changed: `app/src/test/java/app/idl/brand/LauncherIconSnapshotTest.kt` (new test, a `masked`
  option on `composite`, `near` helper), `docs/art/brand/README.md`, `master-plan.md` (§8)

## Commands and results

- `./gradlew testDebugUnitTest --tests '*LauncherIconSnapshotTest*' -Pidl.updateGolden=true -Proborazzi.test.record=true`:
  writes the asset and the golden.
- `scripts/check.sh`: **224 tests, 0 failed, 1 skipped** (+1). Lint **0 errors, 42 warnings**
  (same as before). Existing goldens unchanged.
- Mutation check: changing `idl_launcher_bg` makes the test fail with "is stale (197075 pixels
  differ)". Reverted, and the 4 icon tests pass again.

## Deviations and notes

- The Play icon uses the launcher crop (72 of the 108 units), not the full adaptive canvas, so it
  looks like the home-screen icon. The mark sits inside the launcher safe circle, so Play's
  rounded-square mask can't clip it.
- The asset reuses the existing `-Pidl.updateGolden=true` flag, the same flag that refreshes
  `contract/privacy_vectors.json`.

## Known limitations

- The Play listing still needs a 1024×500 feature graphic and phone screenshots.
- The icon hasn't been uploaded to a Play Console listing. There's no listing yet.

## Commits

See `git log --oneline origin/main..brand/playstore-icon`.
