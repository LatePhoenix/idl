# 2026-10-04 — Phase 2 PR C: emoji_core slice

Branch `avatar/emoji-core-slice` from `origin/main` (`2847b2d`, merge of PR #22). Sections 7 and 8 of `docs/handoff/PHASE_2_TASK.md` only. PR #23.

## Acceptance

| Criterion | Result |
| --- | --- |
| Original `emoji_core` v1 (spec §7) | Met. Ten pictures, authored on the 1024 grid. License on every row is `proprietary-idl`. No Noto files. |
| Shipped after `core_proto` | Met. `AssetPacks.SHIPPED` is `core_proto` then `emoji_core`. `core_proto` defaults still win. |
| Existing goldens | Met. `verifyRoborazziDebug` passed. No pre-existing png was rewritten. |
| Render keys | Every key changes once. `packVersions` is part of the key, so adding the pack changes it for every avatar. The procedural bitmaps do not. Widget snapshots still match. |
| Validator | Met. `AssetPackTest` walks every shipped vector asset: the file exists and `VectorPictureValidator` returns no issues. |
| Expressions | Met. `neutral` and `happy` on `base_round_face` use the vector eyes, brows, and mouth. Other expressions, including `sleepy`, keep the procedural parts. |
| Debug screen (spec §8) | Met. Settings shows "Vector slice" only when `BuildConfig.DEBUG`. The route is registered only in debug builds. Recipes render off the main thread at 48, 128, and 512 px. Hair swatches override `hair.primary`. "Unlink hair.shadow" stops the derived shadow. The last 512 px render time is shown. |
| New goldens | Met. `VectorSliceSnapshotTest` records the seven recipes at 48, 128, and 512. The 48 px neutral face has a lighter cheek than the eye. The busy glyph is ordered after the bangs and changes the badge pixel. The soft-scene corner pixel is transparent. |
| Device (spec §12.2–3) | Met. 21 instrumented tests, 0 failed, on `emulator-5554`. The three new tests check 48 and 512 dimensions, `sameAs` for one recipe twice, and one read per picture per `contentVersion`. Screenshots are in `docs/handoff/reports/screenshots/`. |
| Docs (spec §12.5) | Met. `docs/ROADMAP.md` steps 3–5 and 8 are done. `master-plan.md` Phase 2 row and the work log are updated. |
| F-09 | Still 🟡. Shipped widgets still use the procedural bridge. |

## Commands

```
scripts/check.sh
```

On the pack commit: `214 tests, 0 failed, 1 skipped`. Lint `0 errors, 42 warnings`. Existing goldens matched.

```
scripts/check.sh
```

On the screen and golden commit: `216 tests, 0 failed, 1 skipped`. Lint `0 errors, 42 warnings`.

```
ANDROID_SERIAL=emulator-5554 scripts/check.sh --device
./gradlew connectedDebugAndroidTest
```

`emulator-5554` was already booted. The Gradle check was `216 tests, 0 failed, 1 skipped`, lint `0 errors, 42 warnings`. `connectedDebugAndroidTest` finished **21 tests, 0 failed** on Pixel_9(AVD).

The checkpoint commit is docs and screenshots. `scripts/check.sh` on that tree: `216 tests, 0 failed, 1 skipped`. Lint `0 errors, 42 warnings`.

## Deviations

- `AssetDef` has no `modified`, `sourceUrl`, or `sourceCommit` field. The rows set `license` to `proprietary-idl`. The pictures are original, so there is no upstream commit to record.
- The busy recipe asks for glasses and for VR. `glasses_round_wire` conflicts with `head_vr_headset`, and the activity headset outranks a signature, then occludes the eyes. The glasses recipe is the one that shows the lenses. The busy recipe shows the headset, the VR badge, and the availability glyph.
- Other expressions on the round face still use procedural eyes, brows, and mouth. That is the accepted stand-in in spec §7.

## Known limitations

- The editor, export, Noto import, and widget cutover are still open (`docs/ROADMAP.md` steps 6, 7, 9, and 10).
- F-09 stays open. Vector art replaces the procedural bridge one category at a time.
- `AppContainer.vectorPictures` still searches shipped pack directories in order. Picture files in this pack are named by asset id, so they do not collide with `core_proto`.

## Screenshots

Taken on the Pixel 9 AVD (`emulator-5554`) from the debug screen:

- `docs/handoff/reports/screenshots/vector-slice-top.png` — controls, and the 48 px face beside the large one
- `docs/handoff/reports/screenshots/vector-slice-hair.png` — hair behind the head and bangs in front; the smile shows through the beard
- `docs/handoff/reports/screenshots/vector-slice-glasses.png` — eyes through the lenses, the soft background inside a rounded frame, and the availability glyph over the character

## Files

- `app/src/main/assets/packs/emoji_core/v1/`
- `app/src/main/java/app/idl/domain/avatar/AssetPacks.kt`
- `app/src/main/java/app/idl/avatar/EmojiSlice.kt`
- `app/src/main/java/app/idl/ui/debug/VectorSliceScreen.kt`
- `app/src/main/java/app/idl/ui/settings/SettingsScreen.kt`
- `app/src/main/java/app/idl/MainActivity.kt`
- `app/src/test/java/app/idl/avatar/VectorSliceSnapshotTest.kt`
- `app/src/test/java/app/idl/domain/avatar/AssetPackTest.kt`
- `app/src/androidTest/java/app/idl/avatar/VectorSliceDeviceTest.kt`
- `app/src/test/snapshots/vector/`
- `docs/ROADMAP.md`, `docs/ASSET_SPEC.md`, `master-plan.md`

## Commits

- `6bc0a4a` Add the original emoji_core picture set beside the procedural pack.
- `0a28713` Show the emoji slice on a debug screen and snapshot it at three sizes.
- `c4519dc` Record the emoji_core slice checkpoint and mark roadmap steps 3-5 and 8 done.

## Review (Claude, 2026-10-04)

- Verified on the PR head: `scripts/check.sh` 216 tests, 0 failed, 1 skipped, lint 0 errors / 42 warnings; `connectedDebugAndroidTest` 21/21 on `emulator-5554`. The existing widget goldens are unchanged.
- The goldens and screenshots meet spec §12.3. Hair is behind and in front of the head, the mouth shows through the beard, the eyes show through the lenses, the frame corners stay rounded with the vector scene, and the availability glyph and VR badge draw above the character.
- Follow-up for the widget cutover (ROADMAP step 10): `EmojiSlice` renders every size with `RenderTarget.PROFILE`, so the 48 px goldens don't show compact-widget simplification. Snapshot the slice at `COMPACT_WIDGET` and `STANDARD_WIDGET` before vector art reaches widgets.
- Art note for the user: the hair's rear part is a full circle around the head, so at 48–128 px it reads as a dark hood or halo, and the bangs read as a headband. That's within the spec's placeholder bar, but it's the first art anyone will see.

