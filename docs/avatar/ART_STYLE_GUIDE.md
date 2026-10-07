# Avatar art style guide

**Status:** binding for every vector picture in the avatar program (`AVATAR_PROGRAM.md`), 2026-10-06.
All art is original (D-42) and drawn for one shape, the brand teardrop (D-45, D-46). When this guide
and a spec disagree, the spec wins. Record the case in the PR.

**Target look (D-53, 2026-10-07):** readable volume, with a light sticker edge on widget framing only.
Shipped pictures are placeholders until AD-3. This file's numbers stay the lint target until that
restyle, so placeholder art keeps passing. When AD-3 lands it updates this guide, the kits, and the
pictures together: 40-unit outlines, slightly larger eyes, hair outside the skull, and raised brows
drawn over front hair (Q16 a).

## 1. The look

- **Friendly emoji, not realistic.** Big readable features, flat color, a single soft shade, an
  optional highlight. Think of the launcher icon's face: rounded, warm, simple.
- **One silhouette.** Everything is drawn on the canonical teardrop
  (`config/teardrop_silhouette.json`). Never redraw or reshape the head.
- **Legible at 48 px first.** Design at 512 px, then check at 48 px in head framing. If a detail
  disappears or turns to mush at 48 px, simplify it or tag the part `minSizePx`/`widgetSafe: false`
  so compact renders drop it.
- **Default skin is brand coral `#FF8E6E`.** It's a neutral, emoji-like default. The quick
  creator offers natural tones first.

## 2. Geometry (1024 grid)

| Feature | Guide |
| --- | --- |
| Head | Canonical path. Crown y 96, widest 132–892 near y 476, chin about y 912 |
| Eye line | y 420–430. Eye centres near x 375 and 650 (the brand mark's proportions) |
| Open eyes | Filled ovals, rx ≥ 36, ry ≥ 50 |
| Mouth | Centred near y 570–600, 160–260 wide for neutral shapes |
| Brows | Above the eyes by 60–90, at most 150 long |
| Neck and body | AP-3 body part. Collar line y ≈ 960–1010 |
| Hairline | At or above y 330 at the centre. Bangs must end ≥ 20 above the top of the eyes |
| Headwear | Lower edge ≤ y 330, except items whose job is to cover the eyes (VR headset, masks) |
| Eyewear | Lenses centred on the eye line. Bridge at x 512 |

## 3. Line and shape

- **Head outline:** 36-unit inner ring (base only).
- **Item outlines (optional):** 24–32 units on the `outline` slot. Be consistent within a category.
- **Feature strokes** (closed eyes, mouths, brows, tear tracks): width 40–64. Nothing under 40
  units (about 1.7 px at 48 px in head framing). Round caps and joins.
- **Minimum filled detail:** 32 units in its smallest dimension, unless the part is dropped at
  widget sizes.
- **Shading:** one shade shape per item (on the `.shadow` slot), placed bottom-right, because the
  light comes from the upper left. An optional highlight shape (on the `.highlight` slot), upper
  left. No gradients on items. The face keeps its radial highlight.
- **No text, letters, numbers, logos, brands or real-world trademarks** anywhere, including
  clothes.

## 4. Color

- Every fill and stroke uses a **slot** (`docs/avatar/AVATAR_PROGRAM.md` §3.5). Never hard-code
  a color into a part. Defaults live in the manifest `colorSlots`.
- Pick defaults that read on both light and dark wallpapers. Run the contrast helper (AP-9)
  against the outline and the default background.
- Hair always has `hair.primary`, `hair.shadow` and at least one visible `hair.highlight` strand
  (the user's secondary color).
- Clothing: `top.primary` for the body of the garment, `top.secondary` for trim and collars,
  `top.accent` for small details. Patterns are separate parts (premium).

## 5. Category rules

- **Hair.** Split into `hair_back` (band 20, outside the head silhouette only, never a full circle
  behind the head), `hair_side` and `hair_top` (band 70). Curved hairline with a visible part, not
  a straight band. Long hair falls behind the shoulders (band 20). Nothing reads as a hood or a
  headband at 48 px.
- **Facial hair.** Follows the jaw. Keep the mouth hole (clip `difference`) so every expression
  shows through. Mustaches sit on the upper lip and never hide the mouth's corners.
- **Eyewear.** Frames on `glasses.frame`. Lenses on `glasses.lens` with opacity at most 0.35 for
  clear lenses and 0.75 for sunglasses, so the expression stays readable. Arms tuck under side hair
  (AP-8 masks).
- **Headwear.** Sits on the crown. Brims publish `occlude.hair_top`. Hats never cover the
  eye band.
- **Jewelry.** Small but at least 32 units. Earrings have back parts on band 30 when hair can cover
  them.
- **Tops and outerwear.** Drawn on the AP-3 body. The collar must be visible in head framing at
  48 px (at least 3 pixel rows). Outerwear (band 38) layers over tops (band 36).
- **Expressions.** Shared shapes, reused across expressions. Overlays (tears, sweat, Zs, hearts,
  blush, spirals, steam) are separate parts on band 110 or 50, so the privacy rules can drop them.
  Hands (face-hand subgroup) use a simple four-finger mitten shape on `face.*` slots, so they match
  the skin color.
- **Backgrounds.** Fill the framing viewport (band 0). Keep them low-contrast behind the face.
  Plain colors and soft shapes are free.

## 6. Inclusivity and content

- Hair textures and skin tones are first-class: every texture in D-48 is in the free set, and
  every style works with every color.
- Cultural and religious headwear (for example hijab, turban, kippah), if offered, is free, drawn
  respectfully, and reviewed by the user before release (put it in the review queue).
- No smoking, vaping, alcohol, drug or weapon props. For the mouth-prop clip case, use a lollipop,
  straw or whistle.
- No stereotyped features tied to ethnicity. Variation comes from hair, color and accessories, never
  from changing the face shape.

## 7. Review checklist (every content PR)

1. The category contact sheets at 48, 96 and 512 px, light and dark, in both framings, are in
   the PR.
2. At 48 px: the face reads, the expression reads, and nothing looks like a hood, a headband or a
   smudge.
3. Combinations: the item with three different hairstyles, and with glasses and headwear where
   relevant.
4. All tests are green: pack, legibility, combination, privacy.
5. Every item has a label, a license, a tier and a price (premium only), and a thumbnail.
6. Rows are added to the master-plan user review queue (`master-plan.md` §4.1).
