# Glyphs, accessibility, scenes — checkpoint

Date: 2026-10-04. Branch: `fix/glyphs-a11y-scenes-f08-f11-f12` from `origin/main` (`5756cf5`).

## Acceptance

| Criterion | Status |
| --- | --- |
| F-08: renderer reads the glyph from the manifest | Met. Resolved frames use `registry.asset(layer.assetId).glyph`. The legacy badge path looks up `avail_*` / `badge_*` on the registry |
| `StatusGlyphs` deleted from production or kept as a test cross-check | Met. Moved to `app/src/test`. `AssetPackTest` still checks every `avail_*` and `badge_*` glyph |
| F-11: accessibility sentence de-duplicated case-insensitively | Met. Do not disturb is "Blob avatar, do not disturb" |
| F-12: unknown non-identity scene falls through; identity slots use the pack default | Met |
| Roborazzi goldens updated only if images change | Not applicable. This branch has no Roborazzi goldens (F-14 is not on `origin/main`). Manifest glyph names are unchanged, so resolved widget pixels do not change |

## What changed

`AvatarRenderer` no longer has a hardcoded glyph table. A resolved avatar's badge glyph is the one `PlaceholderFrames` copied from the asset. In-app `AvatarImage` passes the pack registry so a v1 badge still draws `avail_<state>.glyph`.

`describe` adds a label only when the sentence does not already contain it, ignoring case.

`resolveFamily` skips an unknown id and tries the next candidate. The pack default is used when nothing resolved. One unknown base, or one unknown saved scene, still falls back. An unknown presence scene no longer replaces a saved scene.

## Files

- `app/src/main/java/app/idl/avatar/AvatarRenderer.kt`
- `app/src/main/java/app/idl/avatar/AvatarImage.kt`
- `app/src/main/java/app/idl/domain/avatar/AvatarResolver.kt`
- `app/src/main/java/app/idl/domain/StatusGlyphs.kt` (deleted)
- `app/src/test/java/app/idl/domain/StatusGlyphs.kt`
- `app/src/test/java/app/idl/domain/avatar/AvatarResolverTest.kt`
- `app/src/androidTest/java/app/idl/RenderingAndCacheTest.kt`
- `master-plan.md`
- `docs/handoff/reports/2026-10-04-glyphs-a11y-scenes.md`

## Commands

```
ANDROID_SERIAL=emulator-5554 scripts/check.sh --device
```

From Git Bash. The first device attempt failed on a stale `utp.0.log.lck` before tests ran. After `./gradlew --stop` the same command passed.

- JVM: 163 tests, 0 failed, 1 skipped
- lint: 0 errors, 39 warnings
- Device (Pixel 9 AVD, emulator-5554): 15 tests, 0 failed
- All checks passed

## Deviations

None.

## Known limitations

Disk and server work from the other two pull requests are not in this branch. Each PR started from `origin/main` (`5756cf5`).

## Commits

Recorded on `fix/glyphs-a11y-scenes-f08-f11-f12`.
