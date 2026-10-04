# Roadmap — emoji-style avatar compositor

**Status:** Design accepted as D-39. Implementation has not started.  
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
3. **`VectorPicture` loader** and a `VectorAssetRenderer` that draws one solid path and one gradient path. Unit-test the picture parser on the JVM by keeping the parser in `domain`. The `Canvas` implementation stays in `app.idl.avatar`.
4. **One hand-authored original picture set**, not a Noto import: one round face, two expressions, one hairstyle, one beard, one glasses pair, one background. Original art avoids a license import in the same change as the renderer. Mark `license: proprietary-idl`.
5. **Wire `render.type = vector`** through the existing resolver. Procedural assets keep working.
6. **Editor slice** for those six choices: preview, category strip, undo, reset. 48 px preview beside the large one.
7. **Export** a 1024 px transparent PNG and the schema 3 JSON from that screen.
8. **Tests:** recipe round-trip, missing-asset fallback, deterministic render checksum of the vector path, exact bitmap dimensions. Run `scripts/check.sh`. Do not delete a test to go green.
9. **Only then** the asset-pipeline CLI and a Noto import of the vertical-slice counts in `ASSET_SPEC.md` §5, with `THIRD_PARTY_NOTICES.md` and `modified` flags.
10. **Widget cutover** only in a change that still draws availability and activity glyphs. Screenshot or snapshot required.

## Later

- Remaining slice content (6–12 expressions, 3 families, full accessory counts).
- OKLCH-derived shade slots and the advanced color picker.
- Anchor fitting with per-family overrides and user transforms.
- Golden images at 32, 48, 64, 128, 512, and 1024 px. Roborazzi is approved but not added; adding it still needs a deliberate dependency change.
- Performance pass against the targets below. Revise the numbers after measurement.
- House-style pack that replaces any Noto-derived pictures under the same ids.

## Performance targets (unmeasured)

- An edit updates the preview without a visible stall once pictures are cached.
- A cached preview stays within one frame.
- A 1024 px export is fast enough to feel like a save.
- Unchanged pictures are not decoded again.

## Open decisions

- Whether the first pictures are original (recommended in step 4) or a small pinned Noto import. The license rules allow either. Original first is the smaller legal change.
- Whether hair, beard, and jewelry become new `AssetCategory` values or reuse `SIGNATURE_FEATURE` / `HEAD_ACCESSORY`. Prefer new categories when the editor tabs need them, in the schema 3 change, not before.
- D-31 (temporary accessory versus signature) is still unrecorded and still blocks a correct VR-headset interaction. Unrelated to emoji art, still open.
