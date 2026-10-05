# 2026-10-04 — Phase 2 PR A: vector domain

Branch `avatar/vector-domain` from `origin/main` (`3223775`). Sections 2–5 of `docs/handoff/PHASE_2_TASK.md` only. No renderer, art, or UI. PR #21.

## Acceptance

| Criterion | Result |
| --- | --- |
| Picture model, path parser, validator (spec §2) | Met. `VectorPicture` parses with `IdlJson`. `PathData.parse` emits absolute move, line, quad, cubic, and close. `VectorPictureValidator.validate` returns issues. `AssetPackTest` walks every shipped vector asset; the shipped pack has none yet, so the gate passes empty. |
| Manifest and registry (spec §3.1–3.4) | Met. `AssetDef` gains `contentVersion`, `colorSlots`, `family`, and `defaultTransform`, all defaulted. `HAIR` and `FACIAL_HAIR` use `defaultZ` 25 and 35. `expressionOverrides` merge into `ExpressionDef.baseOverrides` in manifest order. `AssetRegistry.baseFamilies` is passed into `migrateRecipe` from the resolver, `LegacyAvatarMigration`, and widget inputs. |
| `itemIds`, colors, render key (spec §4) | Met. Unknown category wires and cross-category ids drop as `UNKNOWN_ASSET`. An incompatible base drops as `INCOMPATIBLE_BASE`. Item conflicts use `SIGNATURE`, so a `STATUS` headset still wins. Vector eyes and mouths do not take a family variant. `colorSlots` and `itemTransforms` are on `ResolvedAvatar`. A vector layer appends `assetId@contentVersion` to the render key. |
| `CompositeOrder` (spec §5) | Met. Procedural-only ops match `PlaceholderFrames` layer order for every `QuickState`, aside from `FACE_STYLE`, which is not a category. Hair back (band 20) sorts before the base; bangs (band 70) sort after the eyes. Chrome sorts last. |
| Existing Roborazzi goldens unchanged | Met. `scripts/check.sh` runs `verifyRoborazziDebug`. |
| Domain stays Android-free (spec §12.4) | Met. New code is under `app.idl.domain.avatar`. `AssetPackTest` still fails the build if `domain` imports `android.`. No `random()` and no `Instant.now()`. Slot maps and item ids are sorted before they affect the key. |
| Docs (spec §12.5) | Partly met. `docs/ASSET_SPEC.md` §1 matches the picture JSON. `docs/ARCHITECTURE.md` §5 has procedural bands 95 and 105. `master-plan.md` Phase 2 row and the work log are updated. `docs/ROADMAP.md` steps 3–5 and 8 are not marked done. |
| Device tests and the debug screen (spec §12.2–3) | Not this PR. The slice and the screen are PR C. `scripts/check.sh --device` was not run. |
| F-09 | Still 🟡. The procedural bridge is unchanged. |

## Commands

```
scripts/check.sh
```

`207 tests, 0 failed, 1 skipped`. Lint `0 errors, 42 warnings`. Debug assemble succeeded. Snapshot verify succeeded, so the existing goldens are unchanged.

The same script is run again on the checkpoint commit before it is pushed.

## Deviations

- `AssetPacks.SHIPPED` stays `core_proto` only. Spec §3.5 adds `emoji_core` v1, and spec §7 is the pack itself (PR C). A second pack version is part of the render key. Shipping an empty manifest would change that key on every procedural avatar. The registry already accepts a list of manifests.
- `docs/ROADMAP.md` steps 3–5 and 8 stay open. Those steps include the renderer and the slice, which are PRs B and C.
- A vector `contentVersion` is appended to the render key only when the resolved draw list contains a vector layer. Procedural keys stay the same bytes as before.

## Known limitations

- No Canvas renderer, picture cache, emoji pack, or debug screen. Do not start PR B until this PR is merged.
- `CompositeOrder` emits procedural ops only for the categories in spec §5. Frame and reaction overlays are not in that table. A missing picture does not suppress the procedural category, so PR B can still fall back.
- `FACE_STYLE` remains a `PlaceholderFrames` insertion. It is not an `AssetCategory`.
- F-09 stays open until vector art replaces the procedural bridge category by category.

## Files

- `app/src/main/java/app/idl/domain/avatar/PathData.kt`
- `app/src/main/java/app/idl/domain/avatar/VectorPicture.kt`
- `app/src/main/java/app/idl/domain/avatar/VectorPictureValidator.kt`
- `app/src/main/java/app/idl/domain/avatar/ColorSlots.kt`
- `app/src/main/java/app/idl/domain/avatar/CompositeOrder.kt`
- `app/src/main/java/app/idl/domain/avatar/AssetManifest.kt`
- `app/src/main/java/app/idl/domain/avatar/AssetPacks.kt`
- `app/src/main/java/app/idl/domain/avatar/AvatarResolver.kt`
- `app/src/main/java/app/idl/domain/avatar/LegacyAvatarMigration.kt`
- `app/src/main/java/app/idl/widget/WidgetRenderInputs.kt`
- `app/src/test/java/app/idl/domain/avatar/PathDataTest.kt`
- `app/src/test/java/app/idl/domain/avatar/VectorPictureTest.kt`
- `app/src/test/java/app/idl/domain/avatar/VectorRegistryTest.kt`
- `app/src/test/java/app/idl/domain/avatar/VectorResolverTest.kt`
- `app/src/test/java/app/idl/domain/avatar/CompositeOrderTest.kt`
- `app/src/test/java/app/idl/domain/avatar/AssetPackTest.kt`
- `app/src/test/java/app/idl/domain/avatar/AvatarFixtures.kt`
- `docs/ASSET_SPEC.md`, `docs/ARCHITECTURE.md`, `master-plan.md`

## Commits

- `7f01c94` Add the vector picture domain so later art can resolve without moving today's goldens.
- `8204759` Record the Phase 2 vector-domain checkpoint and match `CompositeOrder` to the spec.

## Review follow-up (Claude, 2026-10-04)

- `CompositeOrderTest` gained `procedural ops match today's layer order for worn items and face styles`. It covers freckles, blush, ears, glasses, sunglasses, hats, the hoodie and the blanket at the standard and profile targets. It also asserts that the signature op comes right after the base whenever `PlaceholderFrames` inserts `FACE_STYLE`. The original parity test used a bare config and excluded `FACE_STYLE`, so it couldn't catch a wrong band for signature features. The new test fails when `SIGNATURE_FEATURE` is moved to band 55.
- `AssetRegistry.validate()` reports an expression defined by more than one pack (spec §1: a second pack uses `expressionOverrides`). Previously the later definition silently replaced the earlier one's parts. Test: `a second pack may not redefine an expression`.
- For PR B: the renderer must paint `Layer.FACE_STYLE` for a procedural `SIGNATURE_FEATURE` op only when the frame's face style isn't classic. Ears are still drawn inside `head()`.

