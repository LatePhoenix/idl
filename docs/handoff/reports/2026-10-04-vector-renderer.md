# 2026-10-04 — Phase 2 PR B: vector renderer

Branch `avatar/vector-renderer` from `origin/main` (`53cdf95`, merge of PR #21). Section 6 of `docs/handoff/PHASE_2_TASK.md` only. PR #22.

## Acceptance

| Criterion | Result |
| --- | --- |
| Picture cache (spec §6) | Met. `VectorPictureCache` parses once per `assetId@contentVersion`, including a cached failure. The lock is held across the read. A draw with a non-identity transform leaves the cached path bounds unchanged. |
| Canvas renderer (spec §6, ADR 0001) | Met. `CanvasVectorAssetRenderer` scales by `sizePx / 1024`, applies the recipe transform and then the manifest default, and fills from a slot or a linear/radial gradient. Opacity multiplies alpha. Clips use `clipPath` / `clipOutPath` inside save/restore. Fill type is set when the path is built. |
| Compositor-driven resolved bitmap | Met. `AvatarRenderer.bitmap(resolved, registry, pictures, …)` requires `pictures`. It walks `CompositeOrder.ops`. Procedural ops call the existing `Painter` methods. Framed ops stay inside save/restore; chrome is painted after. |
| `SIGNATURE_FEATURE` and ears | Met. A procedural signature op paints `Layer.FACE_STYLE` only when the face style is not classic. Ears stay inside `head()`. |
| Missing picture | Met. The log line is `avatar.vector_missing asset=<id>`. The test asserts that exact line and no other `avatar.vector_missing` line. |
| Widget wiring | Met. `AppContainer.vectorPictures` reads shipped pack files. `Widgets.kt` passes that cache. `AssetPacks.SHIPPED` is still `core_proto` only. |
| Existing Roborazzi goldens | Met. `verifyRoborazziDebug` passed. No pre-existing golden was re-recorded. |
| New procedural goldens | Met. Freckles, blush, cat ears, fox ears, glasses, sunglasses, hat, hoodie, and blanket were recorded on the pre-refactor renderer (`e7813de`) and still pass. |
| Vector snapshot | Met. `src/test/resources/pictures/mark_test_dot.json` is the only picture. `vector_dot.png` is a new golden, not a replacement. |
| Device tests (spec §12.2) | Met. 18 tests, 0 failed, on `emulator-5554` (Pixel 9 AVD). |
| Domain stays Android-free | Met. New code is under `app.idl.avatar`. No domain changes in this PR. |
| F-09 | Still 🟡. The procedural bridge is still how shipped avatars paint. |
| Emoji slice (spec §7–8) | Not this PR. |

## Commands

Recorded the nine procedural goldens before the renderer change, with a test filter so other goldens were not rewritten:

```
./gradlew recordRoborazziDebug --tests "app.idl.widget.WidgetSnapshotTest.procedural*"
```

Then, on the renderer tree:

```
ANDROID_SERIAL=emulator-5554 scripts/check.sh --device
```

`emulator-5554` was not attached at the start of that run (`adb devices` showed the physical Pixel 9 and a Quest 3). The Pixel_9 AVD was started, and the same `check.sh --device` process continued once `emulator-5554` appeared.

- Gradle: `213 tests, 0 failed, 1 skipped`. Lint `0 errors, 42 warnings`. Snapshot verify and the debug assemble succeeded.
- Device: 18 tests, 0 failed, 0 skipped. Classes in the result: `AvatarRendererTest`, `CacheAndWidgetDataTest`, `StatusDeckTest`, `WidgetRenderPathTest`, `RenderCacheRevocationTest`, `Migration1To2Test`.

The renderer commit is that tree. The checkpoint commit is docs only. `scripts/check.sh` on that docs tree: `213 tests, 0 failed, 1 skipped`. Lint `0 errors, 42 warnings`.

## Deviations

- `emoji_core` is not added to `AssetPacks.SHIPPED`. That pack is PR C. A second pack version is part of the render key.
- `docs/ROADMAP.md` steps 3–5 and 8 stay open. Those steps include the emoji slice.
- The nine procedural goldens and `vector_dot.png` are new files. Spec §9 says PR B has no visual change for procedural avatars and that a test-only picture may snapshot the vector path. Pre-existing pngs were not rewritten.
- A vector fallback is drawn as the worn asset's parts: the fallback picture is returned for the worn id, so transforms stay on that id. A procedural fallback is inserted by band and `defaultZ` and does not re-sort the list. No shipped asset has a fallback yet.

## Known limitations

- Do not start PR C until this PR is merged. No debug screen, no Noto, no Room or server change.
- F-09 stays open until vector art replaces the procedural bridge category by category.
- Categories with no procedural band and no picture (hair, facial hair, frame, reaction) still paint nothing. That is why the test dot uses `HAIR`: it does not replace a procedural painter.
- The cache reader throws `FileNotFoundException` with the asset-relative path only. `IdlLog` records the asset id, not the path or the exception.

## Files

- `app/src/main/java/app/idl/avatar/VectorPictureCache.kt`
- `app/src/main/java/app/idl/avatar/CanvasVectorAssetRenderer.kt`
- `app/src/main/java/app/idl/avatar/AvatarRenderer.kt`
- `app/src/main/java/app/idl/AppContainer.kt`
- `app/src/main/java/app/idl/widget/Widgets.kt`
- `app/src/test/java/app/idl/avatar/VectorRendererTest.kt`
- `app/src/test/java/app/idl/widget/WidgetSnapshotTest.kt`
- `app/src/androidTest/java/app/idl/RenderingAndCacheTest.kt`
- `app/src/test/resources/pictures/mark_test_dot.json`
- `app/src/test/snapshots/widget/freckles.png`, `blush.png`, `ears_cat.png`, `ears_fox.png`, `glasses.png`, `sunglasses.png`, `hat.png`, `hoodie.png`, `blanket.png`, `vector_dot.png`
- `docs/adr/0001-vector-asset-renderer.md`, `master-plan.md`

## Commits

- `e7813de` Record procedural goldens the widget suite did not cover yet.
- `8281a04` Drive resolved avatar paint from the compositor so vector parts can interleave without moving procedural pixels.
- `f5b04c8` Record the Phase 2 vector-renderer checkpoint and mark the Canvas renderer implemented.

## Review follow-up (Claude, 2026-10-04)

- **Fixed: a vector scene lost the frame shape.** The squircle or circle clip was applied only inside the procedural `Painter.scene()`. When `SCENE` is a vector asset (PR C's `scene_round_soft`), no procedural scene runs, so the whole avatar drew as a square. `Painter.frameClip()` now holds the clip; `scene()` calls it first (same calls, so procedural pixels are unchanged), and the compositor calls it when the framed ops have no procedural scene. Test: `a vector scene keeps the frame shape` (the corner pixel stays transparent); it fails without the fix.
- Verified that the nine goldens from `e7813de` pass on the pre-refactor renderer, and that every golden passes after the refactor.
- Checks after the follow-up: `scripts/check.sh` 214 tests, 0 failed, 1 skipped, lint 0 errors / 42 warnings; `connectedDebugAndroidTest` 18/18 on `emulator-5554`.
- Note for later (not blocking): `AppContainer.vectorPictures` finds a picture by trying each shipped pack directory in order. That's correct while picture files are named after unique asset ids. If two packs ever ship the same relative file, the first pack wins. The registry should record each asset's pack directory before a third pack lands.

