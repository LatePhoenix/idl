# AP-10 · Store-ready plumbing and the D-48 guard

**Date:** 2026-10-07  
**Branch:** `avatar/ap-10-store-ready`  
**Status:** implemented locally; awaiting PR/CI

## Acceptance criteria

| Criterion | Met? |
| --- | --- |
| `AssetDef` gains `price`, `releaseTag`, `storeVisible` (tier + collection already existed) | yes |
| Domain `Entitlements` + `LocalEntitlements` (demo / unlock-all) | yes |
| Debug-only "Unlock all items" in settings (`BuildConfig.DEBUG`) | yes |
| `prepareForWrite` → `AvatarWrite.NeedsEntitlement(assetIds)` for unowned premium | yes |
| Studio surfaces the entitlement refusal | yes |
| D-48 guard test (§3.8) | yes (`D48GuardTest`) |
| Sample items marked `PREMIUM` with a price | yes (`frame_sticker`, `frame_pixel`, `scene_neon_city`, `face_visor`) |
| Entitlement unit tests; save refused then allowed after unlock | yes (`EntitlementsTest`) |

## Files

- `app/src/main/java/app/idl/domain/avatar/{AssetManifest,AvatarModel,Entitlements}.kt`
- `app/src/main/java/app/idl/{AppContainer,data/local/AppSettings,data/repo/Repositories,ui/settings/SettingsScreen,ui/avatar/AvatarStudioScreen}.kt`
- `app/src/test/java/app/idl/domain/avatar/{D48GuardTest,EntitlementsTest}.kt`
- Packs: 9 FREE procedural hair stubs (D-48 ≥12 + texture tags); 4 PREMIUM samples
- Regenerated `contract/asset_catalog.json` + `supabase/seed/asset_catalog_seed.sql`

## Commands

```text
./gradlew testDebugUnitTest --tests '*EntitlementsTest*' --tests '*D48GuardTest*' --tests '*AssetPackTest*'
→ BUILD SUCCESSFUL
scripts/check.ps1  (run before push)
```

## Deviations / notes

- Nine FREE procedural hair placeholders were added so the D-48 “≥12 free hairstyles + texture tags” guard can pass before AP-15 art waves. They are tagged for textures; vector replacements land in AP-15.
- Server `put_avatar` still allows free-tier catalog ids only (AP-7). Debug unlock is client-side until C.3.
- Rendering does not consult entitlements.

## Commits

See `git log --oneline origin/main..HEAD` on the PR branch.
