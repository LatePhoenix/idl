# 2026-10-06 — AP-2 pack directory per asset

Branch `avatar/ap-2-pack-dirs` from `origin/main` (`fe5954d`). Spec: `docs/avatar/AVATAR_PROGRAM.md` §6 AP-2, which is master-plan F-31. One PR.

## Acceptance

| Criterion | Result |
| --- | --- |
| Registry records each asset's pack directory | Met. `AssetPacks.registry` passes the directory of each shipped manifest (`packs/core_proto/v1/`, `packs/emoji_core/v2/`). `AssetRegistry.packDirectory` returns it. The domain stays Android-free: the registry stores the path string, and `AppContainer` still opens the file. |
| Picture cache reads `<packDir>/<render.file>` | Met. `VectorPictureCache` joins the directory and the file. It no longer tries every shipped pack. |
| Two packs with the same relative file each load their own picture | Met. `PackPictureCacheTest`. |
| F-31 marked done | Met. Master-plan F-31 is ✅. |

## Commands

```
scripts/check.ps1
```

**235 tests, 0 failed, 1 skipped**. Lint **0 errors, 42 warnings**.

```
$env:ANDROID_SERIAL = "emulator-5554"
scripts/check.ps1 -Device
```

The row does not require a device, but the change is in `avatar/` picture loading, so the instrumented slice tests were run. **21 tests, 0 failed**, on `emulator-5554` (Pixel_9 AVD).

`--sql` was not required.

## Files

- `app/src/main/java/app/idl/domain/avatar/AssetManifest.kt`
- `app/src/main/java/app/idl/domain/avatar/AssetPacks.kt`
- `app/src/main/java/app/idl/avatar/VectorPictureCache.kt`
- `app/src/main/java/app/idl/AppContainer.kt`
- `app/src/test/java/app/idl/avatar/PackPictureCacheTest.kt`
- `app/src/test/java/app/idl/avatar/VectorSliceSnapshotTest.kt`
- `app/src/test/java/app/idl/domain/avatar/AssetPackTest.kt`
- `app/src/androidTest/java/app/idl/avatar/VectorSliceDeviceTest.kt`
- `docs/avatar/AVATAR_PROGRAM.md`, `master-plan.md`

## Deviations

None. Call sites that build a registry from manifests alone still work: with no directories, the cache opens `render.file` as given.

## Known limitations

A registry built by appending a manifest onto `coreRegistry().manifests` does not keep the shipped directories. Production goes through `AssetPacks.registry`, which does.

## Commits

```
39ba1b7 Load each picture from its own pack directory so a shared file name cannot cross packs.
```
