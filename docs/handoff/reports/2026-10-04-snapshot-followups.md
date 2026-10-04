# 2026-10-04 — Snapshot and renderer follow-ups

Branch `test/snapshot-followups` from `origin/main` after PR #14 (`5b81bd6`).

## Acceptance

| Criterion | Result |
| --- | --- |
| Bump `actions/upload-artifact` off v4 | Met. The android job uses `@v7`. `archive` stays default, so the two Roborazzi directories are still zipped. |
| A snapshot whose layers differ by target | Met. `sleepy compact drops the blanket and keeps the tea`. Compact has no `body_blanket` and keeps `prop_tea`. Standard keeps both. The bitmaps differ. Golden: `app/src/test/snapshots/widget/sleepy_compact.png`. |
| Registry on the config renderer is non-null | Met. `AvatarRenderer.bitmap` and `draw` for `AvatarConfig` take a required `AssetRegistry`. `AvatarRendererTest` passes the core pack. |
| Record F-23–F-26 in `master-plan.md` §3 | Met. F-23, F-24 and F-25 are fixed here. F-26 was already fixed on PR #14 and is recorded as such. |

## Commands

```
./gradlew recordRoborazziDebug --tests 'app.idl.widget.WidgetSnapshotTest.sleepy compact drops the blanket and keeps the tea'
scripts/check.sh
ANDROID_SERIAL=emulator-5554 ./gradlew connectedDebugAndroidTest
```

- Record: exit 0. Wrote `sleepy_compact.png`.
- `scripts/check.sh`: 181 tests, 0 failed, 1 skipped. Lint 0 errors, 42 warnings. Debug build succeeded.
- Device: 18 tests, 0 failed, on `emulator-5554` (Pixel 9 AVD). Includes `AvatarRendererTest` expression and determinism bitmaps with the required registry.

## Deviation

The review expected compact sleepy to drop both `body_blanket` and `prop_tea`. Compact drops every body accessory, and it drops every foreground prop only when an activity badge is present. Sleepy has no activity, so `prop_tea` stays. The test asserts that rule. The busy-VR goldens are unchanged; they still do not differ by target.

## Files

- `.github/workflows/ci.yml`
- `app/src/main/java/app/idl/avatar/AvatarRenderer.kt`
- `app/src/androidTest/java/app/idl/RenderingAndCacheTest.kt`
- `app/src/test/java/app/idl/widget/WidgetSnapshotTest.kt`
- `app/src/test/snapshots/widget/sleepy_compact.png`
- `master-plan.md`

## Commit

`492a5ca` on `test/snapshot-followups`.

## Known limitations

CI for this branch has not run yet. F-26's lock and generation stamp are on main via PR #14; this branch does not change that code.
