# AP-7 server — schema 3, PresenceView v2, contract vectors

Branch `avatar/ap-7-schema3-server`. Completes AP-7 after the client PR (#42).

## Acceptance

| Criterion | Status |
| --- | --- |
| New migration stores schema 3; `put_avatar` rejects unknown/retired/non-free ids | met |
| `presence_view` returns PresenceView v2 (`identity` + filtered `visual`) | met |
| `visual.expressionId` only when mood is visible (F-19) | met |
| `contract/privacy_vectors.json` regenerated; SQL matches | met (29/29) |
| `contract/composition_vectors.json` + mutation check | met |
| Fake/Supabase backends and UI use schema 3 / identity | met |
| `check.ps1`, `--sql`, `--device` | met |
| F-19 and F-29 marked ✅ | met |

## What changed

- `tools/gen_asset_catalog.py` builds `contract/asset_catalog.json` and
  `supabase/seed/asset_catalog_seed.sql`. `check.sh` / `check.ps1` run `check`.
- Migration `20261007000000_avatar_schema3.sql`: catalog tables, `migrate_avatar_config`,
  `presence_view` v2, catalog-checked `put_avatar`, free-tier entitlement hook.
- Kotlin `PresenceView` is now `{ identity, visual?, … }`. UI and widgets draw
  `view.identity` and pass status through `LegacyAvatarMigration.presence`.
- Composition vectors pin resolved layer ids; a deliberate expression leak without mood
  changes the layer list.

## Commands

```text
scripts/check.ps1                 # 264 tests, 0 failed, 1 skipped; lint 0/42
bash supabase/tests/run.sh --rest # privacy vectors 29/29; behaviour passed
./gradlew testDebugUnitTest --tests '*SupabaseRestIT*' -Pidl.postgrestUrl=http://localhost:54330
scripts/check.ps1 -Device         # ANDROID_SERIAL=emulator-5554; All checks passed
```

## Deviations

- `check.ps1 -Sql` prefers Git Bash (`C:\Program Files\Git\bin\bash.exe`) over the
  WindowsApps WSL stub, which cannot exec `/bin/bash`.
- Stale `ears_*.png` widget goldens that reappear from build intermediates were deleted
  and not committed.

## Known limitations

- Manual upgrade screenshot (old APK → new APK) was not re-run on this PR; client Room
  migration was covered in #42 and device instrumented tests include Migration2To3.
- Premium tier entitlement remains a free-only check until C.3.

## Open questions

None for AP-7. Next program row is AP-8 (occlusion).
