# Art interchange: iDL Art Studio → iDL app

**Status:** accepted 2026-10-07 (D-51). **Audience:** the agent working on
`Z:\ai-tools\idl-art-studio` (repo `LatePhoenix/idl-art-studio`, the "ai-tools" thread) and the agents
working on this repo. The art studio treats this file as **read-only**. Changes to it go through a PR here.

**In one line:** the studio generates **black line art**, closes it into **regions**, labels each region
with a **color slot**, and hands the app one vector file per item. The app does all the coloring (and,
later, textures).

## 1. Who owns what

| | iDL Art Studio (separate repo) | iDL app (this repo) |
| --- | --- | --- |
| Job | Generate, clean, label and convert candidates | Accept, validate, color, ship |
| Writes | `art/incoming/<assetId>/` in this repo (§8), nothing else | `art/<pack>/`, pack manifests, pictures, catalog |
| Owns | Prompts, ComfyUI graphs, scoring, line cleanup, region labelling | Canvas, anchors, slots, bands, ids, tiers, the style guide |

The studio never writes to `app/src/main/assets/` or edits a manifest. Its own `catalog.json`, `.png`
and `.xml` VectorDrawable are for its own use; the app reads none of them.

## 2. Coordinates

The app draws on a **1024 grid** (`viewBox="0 0 1024 1024"`). The studio's 512 rig uses the same brand
teardrop, fitted differently. A plain scale and shift maps one onto the other:

```text
s  = 760 / 300 = 2.53333
X  = 512 + (x - 256) * s        x = 256 + (X - 512) / s
Y  =  96 + (y - 110) * s        y = 110 + (Y -  96) / s
```

Checked 2026-10-07: the studio's `config/head_outline.json` mapped this way overlaps the app's
`config/teardrop_silhouette.json` with **IoU 0.998**. The exporter bakes the transform into the
coordinates and rounds to 0.1.

## 3. The rig's face must be the app's face

The head matches, but **the studio rig's eyes and mouth do not**. Anything placed around them (eyewear,
bangs, beards, mustaches) sits wrong in the app until the rig uses these numbers.

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
`eyes_round_neutral.svg`, `brows_round_relaxed.svg`, `mouth_round_neutral.svg`), scaled into 512 space,
and regenerate the ControlNet line art and the category masks from it. Re-check the eyewear region.

## 4. The format: line art plus labelled regions

Every item is made of three kinds of shape, all flat, all on slots, no hex colors:

| Shape | What it is | Slot |
| --- | --- | --- |
| **Line art** | The black lines of the drawing, traced as a filled shape (`data-fill-rule="evenodd"`) | `outline` (eyewear: `glasses.frame`, §6) |
| **Regions** | Each closed white area inside the lines, traced as its own shape | A role from the category table (§6) |
| **Shade** (optional, at most one per item) | Lower-right crescent of the main region (§5 step 6) | `<item>.shadow` |

The app recolors by changing the slot colors: every colorway comes from the same drawing.
Shade and highlight colors are derived by the app in OKLCH (AP-9); the studio only draws the shade
shape. **Textures** are not drawn by the studio: patterns will later fill a region inside the app (§9).

## 5. The studio pipeline

1. **Generate line art.** Black lines on white, no fills, no shading, no grey. Same rig, ControlNet
   and inpaint masks as now, with prompts and negatives that ask for clean, closed, even-weight
   outlines.
2. **Isolate** the item from the rig face, as today.
3. **Binarize** to pure black and white.
4. **Clean the lines.**
   - Close gaps up to **8 px** (512 space) so regions don't leak.
   - Remove specks and lines shorter than **12 px**.
   - **Normalize line weight to 10–12 px** in 512 space, which is 24–32 units in the app
     (`ART_STYLE_GUIDE.md` §3). Thinner lines vanish on the 48 px widget. Fine detail lines inside a
     region (hair strands, knit ribs, stitching) may go down to **7 px** and are dropped first if they
     clutter the 48 px preview.
5. **Find regions.** Flood-fill every enclosed white area. Drop the outside background. Merge any
   region smaller than **13 px** in its smallest dimension (32 units) into its largest neighbour or
   into the line art.
6. **Label regions.** Guess roles automatically (largest = primary, then by position and the category
   table in §6), then let the operator fix them with one click per region in the UI. Areas where the
   face or background should show through (inside a brim, around the lenses) get the role `none` and
   are not exported. Optional shade: `primary − translate(primary, −10 px, −10 px)`, kept only if it is
   at least 13 px thick.
7. **Vectorize** each region and the line art separately, simplify the curves, transform to 1024
   (§2), round to 0.1.
8. **Preview** on the app-faced rig at 512 and 48 px, in **three color presets** (the default and two
   contrasting ones) to prove the item recolors cleanly, and on light and dark backgrounds.
9. **Export** (§7, §8).

**Fallback:** the current color-tracing path stays available for items that line art can't express,
but the exporter must still map each color group to a slot (§6) and the item gets a review note.

## 6. Region roles and bands per category

Part order inside the file is the draw order within a band: regions first, then the shade, then the
line art on top.

| Category | Region roles | Line art | Bands and extras |
| --- | --- | --- | --- |
| Hair | `hair.primary` (main mass), `hair.highlight` (one strand region, required by the style guide), optional `hair.shadow` shade | `outline`; strand lines allowed | Split every shape against the head silhouette: **outside** the head → band 20, tag `hair_back`; **inside** → band 70, tag `hair_top`, `data-clip-by="occlude.hair_top:difference"`. Keep hair as a few big regions; draw strands as lines, not regions |
| Facial hair | `beard.primary`, optional `beard.shadow` shade | `outline` | Band 60. Keep the mouth hole (`data-clip`, mode `difference`) so every expression shows |
| Headwear | `hat.primary` (crown), `hat.secondary` (brim, band, pom-pom, cuff) | `outline` | Band 90. The crown/brim union is also published as `data-publish-mask="occlude.hair_top"` |
| Eyewear | `glasses.lens` with `data-opacity` ≤ 0.35 (≤ 0.75 for sunglasses), `none` for anything else | **`glasses.frame`** (the lines are the frame) | Band 80. Thick frames may add a `glasses.frame` region |
| Tops | `top.primary` (body), `top.secondary` (collar, cuffs, trim), `top.accent` (small details) | `outline` | Band 36, outerwear band 38. The collar stays visible at 48 px |
| Props | `accessory.primary`, `accessory.secondary` | `outline` | Band 100 (mouth-held) or 110 (hand/foreground) |

## 7. The exported SVG

The subset `tools/asset_pipeline.py` reads. Example, a beanie:

```xml
<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"
     data-schema-version="2" data-id="hat_beanie_cuffed" data-content-version="1">
  <path data-part="crown" data-z="90" data-slot="hat.primary"   data-publish-mask="occlude.hair_top" d="…"/>
  <path data-part="cuff"  data-z="90" data-slot="hat.secondary" d="…"/>
  <path data-part="shade" data-z="90" data-slot="hat.shadow"    d="…"/>
  <path data-part="lines" data-z="90" data-slot="outline" data-fill-rule="evenodd" d="…"/>
</svg>
```

- Elements: `path`, `rect`, `circle`, `ellipse`, `polygon`, `g` only. No `<image>`, filters, CSS or
  `fill="#…"`. Every drawn element has a unique `data-part`, a `data-z` and a `data-slot`.
- `data-id` equals the file name and the asset id: lowercase snake case with the category prefix
  (`hair_`, `beard_`/`mustache_`, `hat_`, `glasses_`, `top_`, `prop_`).
- `data-content-version` starts at 1 and goes up by one whenever the art changes.
- Geometry stays within −16..1040 (body, rear-hair and background bands may extend further,
  `AVATAR_PROGRAM.md` §3.1).
- Aim for well under 16 KB per item. Every item is drawn on the home-screen widget.

## 8. Hand-off folder and metadata

**Accept & Export** writes one folder per item into `art/incoming/` of this repo's checkout
(`export.app_assets_dir` = `Z:/Singularity/idl/art/incoming`). It **does not run git in this repo**:
other agents may be working in the same checkout. The app-side importer (§9) makes the branch and PR.
Re-exporting the same id overwrites that folder only.

```text
art/incoming/<assetId>/
  <assetId>.svg            the item (§4–§7)
  meta.json                see below
  lineart_512.png          the cleaned black-and-white drawing (for review)
  preview_512.png          on the app-faced rig, default colors
  preview_48.png           head framing at 48 px
  preview_colors.png       the three color presets, light and dark
```

```json
{
  "id": "hat_beanie_cuffed",
  "pack": "emoji_core",
  "category": "head_accessory",
  "accessibilityLabel": "Cuffed beanie",
  "tags": ["beanie", "knit"],
  "tier": "free",
  "colorSlots": { "hat.primary": "#3A6EA5", "hat.secondary": "#2B4F78", "outline": "#7A4E00" },
  "patternRegions": ["crown"],
  "provenance": { "tool": "idl-art-studio", "commit": "<sha>", "model": "<checkpoint>", "controlnet": "<model>", "seed": 21, "prompt": "cuffed knit beanie" }
}
```

- Category names: Hat → `head_accessory`, Eyewear → `face_accessory`, Hairstyle → `hair`,
  Facial hair → `facial_hair`, Top → `top`, Prop → `foreground_prop`.
- `colorSlots` are the default colors and must list **every** slot the SVG uses (the validator
  rejects an undeclared slot). Shade and highlight defaults may be left out of `meta.json`: the
  importer fills them with the OKLCH derivation from the primary (AP-9), and the app re-derives them
  when the user picks a primary color. Line art on `outline` defaults to the base head's outline
  color (`#7A4E00` in `emoji_core`) so items and head match.
- `patternRegions` lists the parts a future texture may fill (§9). Optional.
- `tier` is a suggestion: the app decides, and core items stay free (D-48).
- An id that already exists means "new version of that item": keep the id and raise
  `data-content-version`. Never reuse an id for a different item.

## 9. On the app side

**Importer** (`tools/import_art.py`, to be built here) takes `art/incoming/<id>/` and:

1. Checks §4–§7: subset, slots per category, bands, tags, id, bounds, size, exactly one line-art part,
   every region at least 32 units thick.
2. Moves the SVG to `art/<pack>/`, upserts the manifest entry from `meta.json` (adds
   `license: proprietary-idl`, `compatibleBases: ["base_teardrop"]`) and keeps the provenance.
3. Runs `python tools/asset_pipeline.py build <pack>` and `python tools/gen_asset_catalog.py`.
4. Renders 48 px and 512 px sheets, light and dark, with a few expressions and the existing hats and
   glasses, then runs `scripts/check.sh`.
5. Opens a PR and adds a row to the review queue (`master-plan.md` §4.1).

**Textures (later, its own spec).** A pattern is a small repeating vector tile, colored from the item's
slots and clipped to a region listed in `patternRegions`. Renders below about 96 px drop the pattern
and show the flat color. Nothing for the studio to do until then.

App rules that still apply: no letters, logos or brands (`ART_STYLE_GUIDE.md` §3), items must read at
48 px, a removed item needs a `retired` mapping (invariant 5), and catalog changes reach the server
through a migration (F-39).

## 10. Open points for the user

- **Decided (D-51).** Item art comes from the studio through this contract; ST-2 is the importer
  (§9), ST-3 keeps retire. Expression art stays in-repo SVG.
- **Licensing.** Every item ships as `proprietary-idl` original art. Generated art records its model
  and seed in `provenance`. Whether that's enough is your call (Q18, not legal advice).
- **Style (AD-2, Q17).** Line art plus flat fills fits the current style guide. The look of the line
  (weight, roundness) becomes the main style choice.
