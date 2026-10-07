# 2026-10-06 — AP-6 expression art batch 1

Branch `avatar/ap-6-mood-faces` from `origin/main` (`c6e3b93`, merge of PR #40). This is pull request (b), the art. The system landed in PR #40. The row is ✅ in this commit. Tag `avatar-ap-6` after this PR merges and main CI is green.

`Mood` has 15 constants. The spec text says 16. Batch 1 is one face per constant.

## Acceptance

| Criterion | Result |
| --- | --- |
| Every `Mood` resolves to its priority-1 expression | Met. `ExpressionCatalogTest` loads the real registry and catalog. |
| Hidden mood gives neutral eyes and mouth and no overlays | Met. Resting `neutral` canonicalizes to `neutral_face` (open eyes, flat mouth, no overlay). |
| Contact sheet of the batch-1 faces at 48, 96, and 512 | Met. Light and dark: `app/src/test/snapshots/vector/mood_faces_*.png`. |
| No two batch-1 faces are pixel-identical at 48 | Met. `MoodFaceSheetTest`. |
| Shared eye, brow, mouth, and overlay parts on the AP-1 face | Met. 27 new vector pictures. Neutral and happy teardrop drawings are reused. |
| Retire procedural parts that vector parts replace | Not done. See deviations. Procedural parts are still the non-teardrop defaults. |

## Commands

```
scripts/check.ps1
$env:ANDROID_SERIAL = "emulator-5554"; powershell -NoProfile -File scripts/check.ps1 -Device
```

Picture pipeline: **8 tests, OK**. Gradle: **254 tests, 0 failed, 1 skipped**. Lint **0 errors, 42 warnings**.

Device (`emulator-5554`, Pixel 9 AVD API 17): **21 tests, 0 failed**. `--sql` is not required: no server or contract change.

## Deviations

- New eye shapes use `eye.primary` and `eye.white`. The shipped neutral and happy eyes stay on `eye.iris` so those pictures stay byte-identical.
- `neutral_face` and `smiling_face_with_smiling_eyes` reuse the existing teardrop neutral and happy parts. `slightly_smiling_face` is the face that splits Good from Happy.
- Monocle and party hat are expression overlays. `ExpressionDef` has no decomposed-item field. AP-15 can still add the wearable items.
- Faces whose catalog brow is `none` still get a vector brow on the teardrop, so a procedural brow painter is not drawn on top of the vector eyes.
- Vector overlays are compatible with `base_teardrop` only. Other bases keep their procedural eyes, brows, and mouth, and drop the new overlays. Happy and Good keep the existing procedural `overlay_blush`.
- `overlay_cheek_blush` and `overlay_heart_marks` are shipped parts (overlay sheet) and are not on a priority-1 face. Affection faces are AP-13.
- Spiral eyes are seven beads along a spiral. A single filled ribbon collapsed into a blob.
- Procedural part ids are not retired. They are still the defaults for non-teardrop bases. Pointing them at teardrop-only pictures would drop those faces. AP-7 retires the non-teardrop bases.

## Changed goldens

Category sheets grew because new parts joined the row. Widget and vector-slice goldens were not re-recorded; verify passed.

- `packs/emoji_core/face_eye_{48,96,512}.png` and `_dark`: bags, closed, spiral, squint, star, and wide eyes added.
- `packs/emoji_core/face_brow_{48,96,512}.png` and `_dark`: raised and worried brows added.
- `packs/emoji_core/face_mouth_{48,96,512}.png` and `_dark`: asleep, frown, grimace, grin, open, slight frown, slight smile, tongue, and yawn added.
- `packs/emoji_core/expression_overlay_{48,96,512}.png` and `_dark`: new sheet for blush, hearts, monocle, party hat, sparkles, steam, sweat, tears, thermometer, and Zs.
- `vector/mood_faces_{48,96,512}.png` and `_dark`: the 15 priority faces, labeled by mood.

## Commits

- `13729dc` Draw the 15 priority mood faces on the teardrop.
- The following commit marks AP-6 done and adds this report.
