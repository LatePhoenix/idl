# Asset spec — vector avatar pack

**Status:** Design, 2026-10-04. No vector assets are imported yet.  
**Pack layout today:** `app/src/main/assets/packs/<packId>/v<n>/manifest.json` (D-26).

## 1. Picture format

Every vector asset is normalized to a **1024×1024** logical canvas with a required viewBox. The on-disk picture is JSON, produced by the pipeline, not the authoring SVG. Runtime code does not parse SVG.

```json
{
  "schemaVersion": 1,
  "id": "hair_bob",
  "contentVersion": 1,
  "viewBox": 1024,
  "parts": [
    {
      "id": "back",
      "zBand": 20,
      "colorSlot": "hair.primary",
      "commands": "M ...",
      "clip": null
    },
    {
      "id": "bangs",
      "zBand": 70,
      "colorSlot": "hair.primary",
      "commands": "M ..."
    }
  ]
}
```

`commands` is an SVG path `d` string in viewBox space. Fills are either a slot name or a gradient whose stops reference slot names. A missing slot falls back to the asset's default color, then to a documented neutral. It does not fall back to black silence.

## 2. Manifest entry

Stable ids. Display names and list positions are not identifiers. Ids are lowercase snake case, prefixed by category (`hair_bob`, `beard_full`, `glasses_round`, `prop_pipe`).

| Field | Required | Notes |
| --- | --- | --- |
| `id` | yes | Permanent. Retirement maps the old id; it does not reuse it. |
| `schemaVersion` | yes | Manifest entry schema. |
| `displayName` | yes | English source string for the editor. Not a saved id. |
| `category` | yes | Existing `AssetCategory`, extended only when a real gap appears (hair, facial hair, jewelry). |
| `subcategory` | no | Editor filter. |
| `sourceFile` | yes for vector | Path of the picture JSON inside the pack. |
| `thumbnailFile` | yes for vector | Small preview, generated, not hand-exported as the render source. |
| `familyCompatibility` | yes | Family ids from `ARCHITECTURE.md` §4. Empty means none, not all. |
| `requiredAnchors` | yes | Anchor names that must exist on the base. |
| `zBand` | yes | Default band. Parts may override. |
| `defaultTransform` | yes | Offset, scale, rotation, flip. Identity is legal. |
| `bounds` | yes | Tight bounds in viewBox space, used by the validator. |
| `colorSlots` | yes | Slot name → default sRGB hex. |
| `occlusionMasks` | no | Clip or occluder ids. |
| `requires` | no | Other asset ids. |
| `conflictsWith` | no | Symmetric, as today. |
| `tags` | no | Search. |
| `license` | yes | SPDX-like id, see `LICENSING.md`. |
| `sourceUrl` | yes if not original | |
| `sourceCommit` | yes if not original | Full git sha. |
| `modified` | yes | `true` when the picture is not byte-for-byte the upstream art. |
| `contentVersion` | yes | Bumps when pixels change. Cache key includes it. |

The current `AssetDef` is the schema 2 procedural entry (`accessibilityLabel`, `render.type`, `compatibleBases`, `anchors` on bases). Schema 3 adds the columns above. Procedural rows stay valid. A loader accepts both.

`render.type`:

| type | Meaning |
| --- | --- |
| `procedural` | Named painter. Current placeholder pack. |
| `vector` | Picture JSON in this spec. |
| `raster` | Already reserved. Not used for the emoji slice. Export must not flatten vectors to raster as the source. |

## 3. Catalog scope

Unicode source, pinned, not floating: [emoji-test.txt](https://www.unicode.org/Public/emoji/18.0/emoji-test.txt) version **18.0**, file date **2026-04-30**. The checked-in filter is [`config/emoji_face_scope.json`](../config/emoji_face_scope.json).

Included subgroups are face-style heads. `face-hat` and `face-glasses` are **decomposition sources** (the hat or the glasses become items; the finished glyph is not a template). Hearts, emotion symbols, food, flags, objects, and the rest of the file are out even when they share the Smileys & Emotion group.

Families in §4 of the architecture doc are the normalization. A grinning face and a crying face are two expressions on `round_face`, not two avatar templates.

## 4. Pipeline

Desktop tool, later, under `tools/asset-pipeline/`. Not part of the Android build until it exists. Command that must exit nonzero on error: `validate` over the whole pack, suitable for CI.

Steps, in order:

1. Import or author the master SVG (Inkscape, Figma, or an editor that writes plain SVG).
2. Set the viewBox to `0 0 1024 1024`.
3. Convert text to paths and strokes to fills.
4. Assign part ids and color slots. A human checks the slot map.
5. Drop editor metadata, scripts, fonts, and external links.
6. Check bounds, required anchors, and license fields.
7. Write the picture JSON, thumbnail, compatibility preview, and contact sheet.
8. Update the manifest.
9. Run golden renders.

The validator rejects or fails the pack on:

- Embedded rasters, external references, fonts, live text, scripts
- Filters other than a clip
- Masks that do not have a finite bounds
- Missing viewBox
- A fill that is neither a named slot nor a gradient of named slots
- Geometry outside 0..1024 by more than a small stroke tolerance, unless the part is tagged `allowOverflow`
- Duplicate or missing part ids

## 5. First content set

Do not import the full face catalog first. The slice that proves beard-vs-mouth, hair-vs-hat, glasses-vs-eyes, earring-vs-hair, and a pipe stem is:

| Kind | Count | Notes |
| --- | --- | --- |
| Round-face expressions | 6–12 | Start with 2 (neutral, happy) before the rest. |
| Families | 3 | `round_face` first. Then one of `robot_head` or `cat_face`, then a third. |
| Hairstyles | 6 | At least one with a rear part and a front part. |
| Facial hair | 5 | Include a goatee and a large beard. |
| Glasses | 4 | Include one sunglasses pair whose lenses tint rather than hide the eyes. |
| Hats | 3 | One brim that occludes hair. |
| Jewelry | 4 | One earring that can sit behind hair. |
| Mouth props | 3 | Include a pipe and a cigar. |
| Backgrounds | 6 | Include transparent. |

Until those pictures exist, the editor may keep binding the existing procedural pack. That is a stand-in, not the emoji art.

## 6. Cache

In-memory cache key: `assetId + contentVersion + color slots used + transform`. Do not decode an unchanged picture again because an unrelated slot changed. D-27's render-result cache (full bitmap by render key) stays. The picture cache sits underneath it.
