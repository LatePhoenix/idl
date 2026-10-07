# Art interchange: iDL Art Studio → iDL app

**Status:** draft for the user's OK, 2026-10-07. **Audience:** the agent working on
`Z:\ai-tools\idl-art-studio` (repo `LatePhoenix/idl-art-studio`, the "ai-tools" thread) and the agents
working on this repo. The art studio treats this file as **read-only**. Changes to it go through a PR here.

## 1. Who owns what

| | iDL Art Studio (separate repo) | iDL app (this repo) |
| --- | --- | --- |
| Job | Generate, pick and convert candidates | Accept, validate, ship |
| Writes | `art/incoming/<assetId>/` in this repo (§6), nothing else | `art/<pack>/`, pack manifests, pictures, catalog |
| Owns | Prompts, ComfyUI graphs, scoring, tracing | Canvas, anchors, slots, bands, ids, tiers, the style guide |

The studio never writes to `app/src/main/assets/` or edits a manifest. The `catalog.json` /
`.png` / `.xml` VectorDrawable it makes are for its own use. The app reads none of them.

## 2. Coordinates

The app draws on a **1024 grid** (`viewBox="0 0 1024 1024"`). The studio's 512 rig uses the same
brand teardrop, fitted differently. A plain scale and shift maps one onto the other:

```text
s  = 760 / 300 = 2.53333
X  = 512 + (x - 256) * s        x = 256 + (X - 512) / s
Y  =  96 + (y - 110) * s        y = 110 + (Y -  96) / s
```

Checked 2026-10-07: the studio's `config/head_outline.json` mapped this way overlaps the app's
`config/teardrop_silhouette.json` with **IoU 0.998** (no better scale or shift found). The exporter
bakes this transform into the coordinates. Round to 0.1.

## 3. The face must match the app's face

The head matches, but **the studio rig's eyes and mouth do not**. Anything drawn around them (eyewear,
bangs, beards, mustaches) will sit wrong in the app until the rig uses these numbers.

| Feature | App (1024) | Same in studio 512 space | Studio rig today |
| --- | --- | --- | --- |
| Crown | y 96 | y 110 | y 110 ✓ |
| Widest point | x 132–892 near y 476 | x 106–406 near y 260 | ✓ |
| Chin | y ≈ 912 | y ≈ 432 | ✓ |
| Eye centres | (375, 420), (650, 420) | (201.9, 237.9), (310.5, 237.9) | (180, 230), (332, 230) ✗ |
| Eye size (neutral) | rx 40, ry 52 | rx 15.8, ry 20.5 | 13 × 17 ✗ |
| Brows | y 306–342, x 305–445 / 580–720 | y 193–208 | none |
| Mouth (neutral) | centre (512, 580), 192 wide | (256, 298.2), 76 wide | (256, 340) ✗ |
| Eyewear bridge | x 512, on the eye line | x 256, y 237.9 | |
| Hairline limit | front hair ends at y ≥ 330 at the centre | y ≥ 202.4 | |
| Headwear lower edge | ≤ y 330 (except eye-covering items) | ≤ y 202.4 | |
| Collar line | y 960–1010 | y 451–471 | |

**Best fix:** render the rig's base face from the app's own art (`art/emoji_core/base_teardrop.svg`,
`eyes_round_neutral.svg`, `brows_round_relaxed.svg`, `mouth_round_neutral.svg`) scaled into 512 space,
and regenerate the ControlNet line art and masks from it. Then the rig can't drift from the app.
Re-check the category `region` masks (eyewear especially) after the move.

## 4. What one exported item looks like

One SVG per item, in the subset `tools/asset_pipeline.py` reads:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"
     data-schema-version="2" data-id="hair_wavy_undercut" data-content-version="1">
  <path data-part="back"  data-z="20" data-slot="hair.shadow"  data-tags="hair_back" d="…"/>
  <path data-part="front" data-z="70" data-slot="hair.primary" data-tags="hair_top"
        data-clip-by="occlude.hair_top:difference" d="…"/>
  <path data-part="shade" data-z="70" data-slot="hair.shadow"  data-tags="hair_top" d="…"/>
  <path data-part="strand" data-z="80" data-slot="hair.highlight" data-tags="hair_top" d="…"/>
</svg>
```

- Elements: `path`, `rect`, `circle`, `ellipse`, `polygon`, `g` only. No `<image>`, no filters, no
  `fill="#…"`, no CSS. Every drawn element has `data-part` (unique), `data-z` and `data-slot`.
- `data-id` equals the file name and the asset id: lowercase snake case, starting with the category
  prefix (`hair_`, `beard_`/`mustache_`, `hat_`, `glasses_`, `top_`, `prop_`).
- `data-content-version` starts at 1 and goes up by one each time the art changes.
- Geometry stays within −16..1040 (body, rear-hair and background bands may go further, §3.1 of
  `AVATAR_PROGRAM.md`).
- Keep files small: round to 0.1, simplify the traced curves, aim for well under 16 KB per item.
  Every item is drawn on the home-screen widget.

## 5. Colors become slots

The app recolors every item, so a traced palette color must become a **slot**, not a hex value
(`AVATAR_PROGRAM.md` §3.5, `ART_STYLE_GUIDE.md` §4). The exporter maps the studio's palette groups:

| Category | Dominant color | Darker color | Lightest color | `ink` (outline) |
| --- | --- | --- | --- | --- |
| Hair | `hair.primary` | `hair.shadow` | `hair.highlight` (required: at least one strand) | `outline` |
| Facial hair | `beard.primary` | `beard.shadow` | `beard.primary` | `outline` |
| Headwear | `hat.primary` | `hat.secondary` | `hat.secondary` | `outline` |
| Eyewear | `glasses.frame` | `glasses.frame` | lens → `glasses.lens` with `data-opacity` ≤ 0.35 (≤ 0.75 sunglasses) | `glasses.frame` |
| Tops | `top.primary` | `top.secondary` | `top.accent` | `outline` |
| Props | `accessory.primary` | `accessory.secondary` | `accessory.secondary` | `outline` |

The swatch's hex becomes that slot's default in the item's metadata (§6). Shade and highlight are
derived in OKLCH by the app (AP-9). The art should have **one** shade shape, lower right, and at most
one highlight, upper left, which matches flat cel shading.

## 6. Layers (bands) and splitting parts

| Category | Parts and bands |
| --- | --- |
| Hair | Clip the traced shape against the head silhouette: **outside** the head → `back`, band 20, tag `hair_back` (long hair falls behind the shoulders); **inside** → `front`, band 70, tag `hair_top`, `data-clip-by="occlude.hair_top:difference"` so hats can hide it. Highlight strands band 80 |
| Facial hair | Band 60. Keep the mouth hole (`data-clip`, mode `difference`) so every expression shows |
| Eyewear | Band 80. Frames and lenses as separate parts |
| Headwear | Band 90. The crown or brim shape also gets `data-publish-mask="occlude.hair_top"` |
| Tops | Band 36 (outerwear 38). The collar stays visible at 48 px |
| Props | Band 100 (mouth-held) or 110 (hand/foreground) |

## 7. Hand-off folder and metadata

The studio's **Accept & Export** writes one folder per item into this repo's working tree, on a branch
it creates (`art/studio-<assetId>`), and commits there. It never pushes and never merges.

```text
art/incoming/<assetId>/
  <assetId>.svg          the item (§4–§6)
  meta.json              see below
  preview_512.png        on the app-faced rig
  preview_48.png         head framing at 48 px
```

```json
{
  "id": "hair_wavy_undercut",
  "pack": "emoji_core",
  "category": "hair",
  "accessibilityLabel": "Wavy undercut",
  "tags": ["short", "wavy"],
  "tier": "free",
  "colorSlots": { "hair.primary": "#6E6474", "hair.shadow": "#4A4250", "hair.highlight": "#A39A9E", "outline": "#1C1420" },
  "provenance": { "tool": "idl-art-studio", "commit": "<sha>", "model": "<checkpoint>", "controlnet": "<model>", "seed": 21, "prompt": "messy wavy undercut" }
}
```

Category names map like this: Hat → `head_accessory`, Eyewear → `face_accessory`, Hairstyle → `hair`,
Facial hair → `facial_hair`, Top → `top`, Prop → `foreground_prop`. `tier` is a suggestion only: the
app decides, and core items stay free (D-48). An id that already exists in a pack means "new version of
that item": keep the id and raise `data-content-version`. Never reuse an id for a different item.

## 8. On the app side: import and review

A small importer (`tools/import_art.py`, to be built here) takes `art/incoming/<id>/` and:

1. Checks §4–§6 (subset, slots, bands, tags, id, bounds, size).
2. Moves the SVG to `art/<pack>/`, upserts the manifest entry from `meta.json` (with
   `license: proprietary-idl`, `compatibleBases: ["base_teardrop"]`), and records provenance.
3. Runs `python tools/asset_pipeline.py build <pack>` and `python tools/gen_asset_catalog.py`.
4. Renders the 48 px and 512 px sheets, light and dark, with a few expressions and the existing hats
   and glasses (the in-repo `tools/studio` render path), then `scripts/check.sh`.
5. Opens a PR and adds a row to the review queue (`master-plan.md` §4.1).

Rules from the app that still apply: no letters, logos or brands (`ART_STYLE_GUIDE.md` §3), items must
read at 48 px, a removed item needs a `retired` mapping (invariant 5), and catalog changes reach the
server through a migration (F-39).

## 9. Open points for the user

- **D-50 changes.** D-50 says the in-repo Studio makes the art as hand-written SVG. If the art studio's
  generated art is the source from now on, that becomes a new decision (proposed D-51), and ST-2 / ST-3
  shrink to the importer and review sheets in §8.
- **Licensing.** Every item ships as `proprietary-idl` original art. For generated art, record the
  model and its license in `provenance`. Whether AI-generated art is protectable, and whether that
  matters for iDL, is your call (this note is not legal advice).
- **Style direction (AD-2, Q17).** If you prefer the art studio's look to the AD-1 sheets, that
  answers Q17. The style guide's flat-color rules may need to change to match.
