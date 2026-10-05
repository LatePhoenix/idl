# Roadmap — emoji-style avatar compositor

**Status:** Steps 3–5 and 8 are done. **D-42 (2026-10-05) changes the rest:** the art is original
teardrop-face art, step 9's Noto import is dropped, and the plan of record for the remaining work
is `master-plan.md` §4.1 (Avatar program).  
**Living project plan:** [`master-plan.md`](../master-plan.md). F-01 and F-02 are fixed on main. This roadmap is the avatar-art track.

## Done in this step

- Inspected the repo. Avatar schema 2, manifests, compatibility, and a Canvas renderer already exist.
- Wrote the design: `docs/ARCHITECTURE.md`, `docs/ASSET_SPEC.md`, `docs/AVATAR_RECIPE_SCHEMA.md`, `docs/LICENSING.md`, this file.
- Accepted [ADR 0001](adr/0001-vector-asset-renderer.md): path IR on Canvas, not AndroidSVG.
- Pinned the Noto commit and the Unicode 18.0 catalog in `docs/LICENSING.md` and `config/emoji_face_scope.json`. No art copied.

## Explicitly not doing

- A second Android application or extra Gradle modules (D-01).
- Importing the Noto tree before a pack validator exists.
- Generating a full face catalog of placeholder SVGs.
- Runtime generative images.
- Changing `supabase/migrations/` or `contract/privacy_vectors.json` for this design.

## Next implementation order

1. **Branch** from up-to-date `origin/main`. Do not reuse a dirty local `main` (it was behind `origin/main` at the last audit).
2. **Schema 3 fields** on `AvatarConfiguration`, with defaults, plus round-trip and migration tests. No UI. Done in `avatar/schema-3`. `familyId` stays blank unless the caller passes an explicit base→family map.
3. **Done.** `VectorPicture` loader and `CanvasVectorAssetRenderer`. The parser stays in `domain`. The Canvas implementation is `app.idl.avatar`.
4. **Done.** One hand-authored original picture set, `emoji_core` v1, license `proprietary-idl`. No Noto files. Round face, neutral and happy, bob, beard, wire glasses, soft background.
5. **Done.** `render.type = vector` resolves through the existing pipeline. Procedural assets still paint the same pixels.
6. **Editor slice** for those six choices: preview, category strip, undo, reset. 48 px preview beside the large one.
7. **Export** a 1024 px transparent PNG and the schema 3 JSON from that screen.
8. **Done.** Pack validation, deterministic vector renders, exact bitmap dimensions at 48 and 512, and Roborazzi goldens for the slice. `scripts/check.sh` stays required. Do not delete a test to go green.
9. **Only then** the asset-pipeline CLI and a Noto import of the vertical-slice counts in `ASSET_SPEC.md` §5, with `THIRD_PARTY_NOTICES.md` and `modified` flags.
10. **Widget cutover** only in a change that still draws availability and activity glyphs. Screenshot or snapshot required.

## Later

- Remaining slice content (6–12 expressions, 3 families, full accessory counts).
- OKLCH-derived shade slots and the advanced color picker.
- Anchor fitting with per-family overrides and user transforms.
- Golden images at 32, 48, 64, 128, 512, and 1024 px. Roborazzi is in place (F-14); the slice has goldens at 48, 128 and 512 px (PROFILE target). Widget-target goldens are F-30.
- Performance pass against the targets below. Revise the numbers after measurement.
- House-style pack that replaces any Noto-derived pictures under the same ids.

## Performance targets (unmeasured)

- An edit updates the preview without a visible stall once pictures are cached.
- A cached preview stays within one frame.
- A 1024 px export is fast enough to feel like a save.
- Unchanged pictures are not decoded again.

## Open decisions

- First pictures are original `emoji_core` art. A Noto import waits for step 9.
- Hair and facial hair are `AssetCategory` values. Glasses stay `FACE_ACCESSORY`. Jewelry is still later.
