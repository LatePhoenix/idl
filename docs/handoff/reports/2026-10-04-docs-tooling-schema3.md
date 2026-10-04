# 2026-10-04 — Housekeeping and schema 3 follow-ups

Branch `chore/docs-tooling-schema3` from `origin/main` (`5b81bd6`). PR #18.

## Acceptance

| Criterion | Result |
| --- | --- |
| `scripts/check.ps1` matches `scripts/check.sh` | Met. Both print `183 tests, 0 failed, 1 skipped` and `lint: 0 errors, 42 warnings`. `check.sh` stays the canonical script. `AGENTS.md` mentions the PowerShell script. |
| F-20 boot wait | Met. `--device` runs `adb wait-for-device`, waits until `sys.boot_completed=1`, then `adb shell input keyevent 82`, then the instrumented tests. The same steps are in `check.ps1`. |
| F-15 doc drift | Met. §1.1 names `5b81bd6` and the "local main is behind" line is gone. Work log rows for the base exploration, D-39, and schema 3 name PRs #7, #8, and #9. `docs/ROADMAP.md` no longer says D-31 is unrecorded. README points at `master-plan.md` §1.2 instead of embedding a count. Creator plan §7 says the widget path uses the resolver. |
| Schema 2 upgraded on load | Met. `AvatarConfiguration.decode` migrates. The widget path runs `migrateRecipe` for the saved avatar and a friend's `restingAvatar`. |
| `itemIds` order | Met. Lists are unordered sets. The render key sorts ids. Reversing a list does not change the key. |
| Newer schema is not written back | Partly met. `prepareForWrite` returns "update the app to edit this avatar" and no configuration, and it is unit-tested. Nothing calls it yet, because no `AvatarConfiguration` is persisted. The Avatar Studio `recipe` parameter was never passed by `MainActivity`; Claude removed it during review. Wiring is tracked as F-29 (🟡). |
| F-15, F-20, Track 0.8, F-27–F-29 | Met in `master-plan.md`. These were F-23–F-25 on the branch and were renumbered during review because PR #17 had already used those numbers. |

## Decision

`itemIds` values are unordered sets. Nothing draws from list order, and `docs/AVATAR_RECIPE_SCHEMA.md` already sorted ids in the render key. Drawing order is the asset z-index. The key keeps sorting ids so `[pin_a, pin_b]` and `[pin_b, pin_a]` share a cache entry.

## Commands

```
scripts/check.sh
scripts/check.ps1
ANDROID_SERIAL=emulator-5554 scripts/check.sh --device
```

- `check.sh`: 183 tests, 0 failed, 1 skipped. Lint 0 errors, 42 warnings.
- `check.ps1`: the same two lines.
- `--device`: boot wait completed, then 18 instrumented tests, 0 failed, on `emulator-5554`.

## Commit

`a88b1de` on `chore/docs-tooling-schema3`. PR #18.

## Deviation

Room and the server still store v1 `AvatarConfig`, not `AvatarConfiguration`. There is no production `decodeFromString` of a recipe. `decode` is the JSON load path, covered by `schema 2 json loaded through decode comes out as schema 3`. The live path that turns a saved avatar or a friend's `restingAvatar` into a recipe is `LegacyAvatarMigration.migrate`, which now calls `migrateRecipe`. `AvatarResolver.resolve` migrates again before the render key.

## Files

- `scripts/check.sh`, `scripts/check.ps1`, `AGENTS.md`
- `app/src/main/java/app/idl/domain/avatar/AvatarModel.kt`
- `app/src/main/java/app/idl/domain/avatar/AvatarResolver.kt`
- `app/src/main/java/app/idl/domain/avatar/LegacyAvatarMigration.kt`
- `app/src/main/java/app/idl/widget/WidgetRenderInputs.kt`
- `app/src/test/java/app/idl/domain/avatar/AvatarConfigurationSchemaTest.kt`
- `docs/AVATAR_RECIPE_SCHEMA.md`, `docs/ROADMAP.md`, `docs/IDL_AVATAR_CREATOR_PLAN.md`, `README.md`, `master-plan.md`

## Review follow-up (Claude, 2026-10-04)

- Merged `test/snapshot-followups` (PR #17) into this branch so the two `master-plan.md` edits land together. #18's findings are now F-27, F-28 and F-29.
- F-29 is 🟡. The guard exists and is tested; it gets wired when the vector editor persists `AvatarConfiguration`.
- Removed the unused `recipe` parameter from `AvatarStudioScreen`. That file now matches `main`.
- Recorded D-40: the "Earning" banner's timing signal is accepted.
- Checks after the follow-up are listed in the PR comment and the master-plan work log.
