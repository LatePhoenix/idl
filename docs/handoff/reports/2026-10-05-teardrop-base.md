# 2026-10-05 — AV.2 teardrop base and hair redraw

Branch `avatar/teardrop-base` from `origin/main` (`037fdc1`). Item N.1 in `master-plan.md` §4.0, spec §4.1 AV.2. Commit `b49afe0`.

## Acceptance

| Criterion | Result |
| --- | --- |
| `emoji_core` v2, v1 removed | Met. `AssetPacks.SHIPPED` loads `packs/emoji_core/v2/manifest.json` after `core_proto`. |
| `base_teardrop` | Met. Family `teardrop_face`. Crown y 96, widest point (132–892) at y 430, neck 260 wide at y 930, then straight to the bottom edge. Face, shade, and outline are band 40. Shade sits on the chin and neck, below the mouth. |
| Anchors and face-safe zone | Met. Spec table plus the registry's required anchors (`face_center` 512,480, `eyes` on the eye line, `prop_hand` 180,980, `body` 512,1000). Procedural bases still use the placeholder anchors. |
| Expression parts | Met. Neutral and happy on `base_teardrop` use the re-placed eyes, brows, and mouth (`contentVersion` 2). Sleepy stays procedural (`eyes_closed_line`). |
| Hair redraw | Met. `hair_short_crop`, `hair_bob`, `hair_long_straight`. Rear hair is a cap plus side pieces outside the silhouette, not a circle behind the head. Bangs are two curves with a center part. Each style has a `hair.highlight` strand. |
| Facial hair | Met. `beard_full` follows the jaw and keeps a mouth hole. `stubble` is a low-opacity dot field. `mustache_classic` sits on the upper lip. |
| Retirement | Met for the renamed ids: `base_round_face` → `base_teardrop`, `hair_round_bob` → `hair_bob`, `beard_round_full` → `beard_full`. |
| Glasses and scene | Met. `scene_round_soft` is unchanged (`contentVersion` 1). Glasses were re-placed onto the eye line (`contentVersion` 2) and still conflict with `head_vr_headset`. |
| F-30 | Met. Compact and standard widget renders, targets from `WidgetRenderInputs.targetFor`. Identity vector layers survive both. `overlay_blush` is decoration and drops. PROFILE goldens kept. |
| Procedural goldens | Met. `verifyRoborazziDebug` passed. No file under `app/src/test/snapshots/widget/` changed. |
| Contact sheet | Met. Every hair × facial hair pair at 48 px (nearest-neighbor ×4 so the pixels stay the 48 px render) and at 512 px. |
| 48 px read | Met. The three hair styles read as hair. They do not read as a hood or a headband. |
| Device screenshots | Met. Vector slice on `emulator-5554`: top, hair, facial hair, glasses/scene/VR. |

## Commands

```
scripts/check.sh
```

On `b49afe0`: **220 tests, 0 failed, 1 skipped**. Lint **0 errors, 42 warnings**.

Vector goldens were recorded with:

```
./gradlew recordRoborazziDebug --tests app.idl.avatar.VectorSliceSnapshotTest
```

Procedural widget goldens were not recorded.

```
ANDROID_SERIAL=emulator-5554 scripts/check.sh --device
```

On `b49afe0`, before the docs commit: **220 tests, 0 failed, 1 skipped**. Lint **0 errors, 42 warnings**. Instrumented tests: **21 tests, 0 failed**, on `emulator-5554` (Pixel_9 AVD). The emulator was started for this run because only the Quest was attached.

## Files

- `app/src/main/assets/packs/emoji_core/v2/` (v1 removed)
- `app/src/main/java/app/idl/domain/avatar/AssetPacks.kt`
- `app/src/main/java/app/idl/avatar/EmojiSlice.kt`
- `app/src/main/java/app/idl/ui/debug/VectorSliceScreen.kt`
- `app/src/test/java/app/idl/domain/avatar/AssetPackTest.kt`
- `app/src/test/java/app/idl/avatar/VectorSliceSnapshotTest.kt`
- `app/src/androidTest/java/app/idl/avatar/VectorSliceDeviceTest.kt`
- `app/src/test/snapshots/vector/` including `contact_48.png`, `contact_512.png`, and `widget/`
- `docs/handoff/reports/screenshots/vector-slice-teardrop-*.png` and `contact-hair-*.png`
- `master-plan.md`, `docs/ASSET_SPEC.md`

## Deviations

- Ids that keep their name are not in the `retired` map. A same-id entry would cycle in `canonicalId`. Retired entries are only the renames: base, bob, and full beard.
- `AssetDef` has no `modified` field. License is `proprietary-idl` on every row.
- Glasses moved. The spec said to leave them unchanged unless they needed re-placing. The v1 lenses were centered at y 488; the teardrop eye line is y 430, so `contentVersion` is 2.
- Required anchors that the spec table does not list: `face_center` (512, 480), `eyes` (same as `eye_line`), `prop_hand` (180, 980), `body` (512, 1000).
- Other expressions on the teardrop still use procedural parts. Happy still adds the procedural `overlay_blush` extra. The VR recipe still drops the glasses because the activity headset outranks them and occludes the eyes.
- The 48 px contact sheet is the 48 px render scaled ×4 with nearest-neighbor filtering, so a reviewer can see the pixels. The 512 px sheet is unscaled.
- Pack version is in the render key, so caches rebuild once. Procedural pixels do not change. `core_proto` stays first, so its defaults still win.

## Known limitations

- The three hair styles are the slice, not the 10+ hair wave (AV.7).
- F-31 (pack directory per asset) is not in this change.
- F-09 stays open. Widgets still use the procedural bridge.
- ROADMAP steps 6, 7, 9, and 10 are not done.

## Commits

```
b49afe0 Replace the round face with a teardrop base so hair sits outside the silhouette.
```
