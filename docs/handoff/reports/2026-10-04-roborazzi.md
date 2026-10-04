# 2026-10-04 — F-14 JVM widget snapshots

Branch `test/roborazzi-f14` from `origin/main` (`94295ce`). PR #11.

## Acceptance

| Criterion | Result |
| --- | --- |
| Roborazzi + Robolectric in the version catalog and `app/build.gradle.kts`, smallest config | Met. Roborazzi **1.60.0** and Robolectric **4.17**. See deviation. |
| Goldens committed under `app/src/test/snapshots/` | Met. 15 PNGs in `app/src/test/snapshots/widget/`. |
| Every `Availability` at 110×110 dp → `STANDARD_WIDGET` | Met. `WidgetSnapshotTest.every availability differs at the default 2x2 size`. |
| `COMPACT_WIDGET`, `STANDARD_WIDGET`, `LARGE_WIDGET` for one busy+VR presence | Met. `busy VR covers compact standard and large`. The three bitmaps are currently byte-identical; see limitations. |
| VR headset over signature glasses for a non-close friend (F-02) | Met. `non-close friend keeps the VR headset over signature glasses`. Asserts `head_vr_headset` and the absence of `face_glasses_round`. |
| Sleepy at the default size keeps `body_blanket` and tea (F-21) | Met. `sleepy at the default size keeps the blanket and tea`. |
| Light vs dark wallpaper contrast | Met. `light and dark wallpaper contrast differ`, plus a pixel inequality assert. |
| Pairwise byte compare of availability bitmaps | Met. Raw `copyPixelsToBuffer` bytes, every pair. |
| CI runs verify and uploads diffs on failure | Met. Android job runs `verifyRoborazziDebug` and uploads `app/build/outputs/roborazzi/` and `app/build/reports/roborazzi/` as `roborazzi-diffs` when the job fails. |
| `scripts/check.sh` runs verify | Met. It calls `verifyRoborazziDebug` instead of a separate `testDebugUnitTest`, because the verify task runs the unit tests. |
| `AGENTS.md` documents record and verify | Met. |
| PR template closes the §7.2 parking-lot item | Met. `.github/pull_request_template.md`. The parking-lot bullet is removed. |
| Deterministic render, no loosened tolerance | Met. Roborazzi's default validator is an exact threshold of 0. Two verifies of `WidgetSnapshotTest` passed, the second with `--rerun-tasks`. |
| Mutation: skip availability drawing, tests fail, then revert | Met. See below. |
| CI green on the PR | Met. Run [37231593526](https://github.com/LatePhoenix/idl/actions/runs/37231593526) on `2414418`: android pass (5m54s), backend pass (3m6s). Windows-recorded goldens matched Ubuntu with the default exact threshold. |

## Mutation check

In `PlaceholderFrames.from`, the `Layer.AVAILABILITY_BADGE` add was forced off (`if (false && availabilityLayer != null)`). Then:

```
./gradlew verifyRoborazziDebug --tests app.idl.widget.WidgetSnapshotTest
```

Result: **5 tests, 5 failed**, each `AssertionError` at `WidgetSnapshotTest.kt:174` (`bitmap.captureRoboImage`). That is the golden compare. The change was reverted before commit; `git diff` on `PlaceholderFrame.kt` was empty, and the golden hashes matched the recorded files (`avail_available.png` `77F02A3D…`, `avail_busy.png` `0DBBB1D0…`, `sleepy_default.png` `683F41F4…`).

The pairwise byte compare sits after the captures. This mutation failed on the goldens first. If those goldens were re-recorded with badges skipped, the eight availability bitmaps would be the same resting face and the byte compare would fail.

## Commands

Record:

```
./gradlew recordRoborazziDebug --tests app.idl.widget.WidgetSnapshotTest
```

BUILD SUCCESSFUL. 15 files under `app/src/test/snapshots/widget/`.

Verify (cached, then forced):

```
./gradlew verifyRoborazziDebug --tests app.idl.widget.WidgetSnapshotTest
./gradlew verifyRoborazziDebug --tests app.idl.widget.WidgetSnapshotTest --rerun-tasks
```

Both BUILD SUCCESSFUL. The second run executed `testDebugUnitTest` again. No pixel flicker on this machine.

`scripts/check.sh` via Git Bash (`C:\Program Files\Git\bin\bash.exe`):

```
165 tests, 0 failed, 1 skipped
lint: 0 errors, 41 warnings
All checks passed.
```

The skipped test is `SupabaseRestIT`. The two warnings above the previous 39 are `NewerVersionAvailable` for Roborazzi 1.76.0 (plugin id and the library).

## Deviations

- **Roborazzi 1.60.0, not 1.76.0.** 1.76.0's Kotlin metadata is 2.3.0. This repo's compiler and KSP are Kotlin 2.0.21, and `kspDebugUnitTestKotlin` refused the 1.76.0 jars. 1.60.0 (2026-04-28) is the newest release still built with Kotlin 2.0.21. Task names are unchanged: `recordRoborazziDebug`, `verifyRoborazziDebug`.
- The snapshot test uses `@Config(application = android.app.Application::class)`. Robolectric otherwise starts `IdlApp`, which constructs `WorkManager` and throws because the manifest disables `WorkManagerInitializer`. The painter does not need the app container; the registry is `coreRegistry()`.
- Compare images go to `app/build/outputs/roborazzi`, not next to the goldens.
- This branch is `origin/main` at `94295ce`. The F-18 action bumps are not on main, so the workflow still uses `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, and `ubuntu-latest`. The new upload step uses `actions/upload-artifact@v4` to match that file. It does not bump those actions.

## Limitations

- Goldens were recorded on Windows with Robolectric `GraphicsMode.NATIVE`. The sleepy tea prop is `Typeface.DEFAULT_BOLD` emoji text, so a future host-font change can move pixels. CI run 37231593526 verified these goldens on `ubuntu-latest` with the default exact threshold, so this set matches that runner. Do not raise the compare threshold to hide a later diff.
- Busy+VR at 70, 110, and 180 dp produces one bitmap. That presence has no body accessory and no decoration layer for `simplify()` to drop, and the painter always uses 256 px (the same size `Widgets.kt` uses). Each target still asserts `avail_busy` and `head_vr_headset`.
- No emulator job. `connectedDebugAndroidTest` stays local.

## Files

- `gradle/libs.versions.toml`, `build.gradle.kts`, `app/build.gradle.kts`
- `app/src/test/java/app/idl/widget/WidgetSnapshotTest.kt`
- `app/src/test/snapshots/widget/*.png` (15)
- `.github/workflows/ci.yml`, `.github/pull_request_template.md`
- `scripts/check.sh`, `AGENTS.md`, `master-plan.md`

## Commits

`git log --oneline origin/main..HEAD` before this report:

```
2414418 Record F-14 as done and write the snapshot checkpoint.
3555e44 Add JVM widget snapshot tests so CI catches visual regressions.
```

The commit after `2414418` only records that CI run and this hash.
