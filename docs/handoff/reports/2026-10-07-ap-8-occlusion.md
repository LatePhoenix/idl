# AP-8 — Occlusion, layering and legibility

Branch `avatar/ap-8-occlusion`.

## Acceptance

| Criterion | Status |
| --- | --- |
| Apply `publishMask` / `clipBy` in draw order (band → asset id → part index) | met |
| Hair split tags; brimmed cap publishes `occlude.hair_top`; hood back on band 20 | met |
| `FACIAL_HAIR` is `multiple`; beard/stubble conflict | met |
| Hand-overlay suppresses foreground prop for that render | met |
| Item-transform limits (eyewear ±24 Y; headwear scale 0.9–1.1) | met |
| `LegibilityTest` + combination property test | met |

## Deviations and choices

- Glasses temple tuck is not a separate arms band yet; the brim + `hair_top` clipBy path
  is the sample that proves cross-asset masks. Temple publish can land with eyewear art.
- `hair_short_crop` / `hair_long_straight` are tagged `hair_back` / `hair_top` without a
  geometry side split; `hair_bob` is the full three-part sample.
- Hair item transforms are cleared (spec: no allowance until declared). Eyewear/headwear
  clamps live in `ItemTransformLimits`.

## Commands

```text
scripts/check.ps1   # 274 tests, 0 failed, 1 skipped; lint 0/42
```

## Files

- Domain: `PublishedMasks`, `ItemTransformLimits`; resolver hand-overlay + transform clamp;
  `FACIAL_HAIR.multiple`.
- Renderer: cross-asset `clipBy` via baked publisher paths.
- Art: hair tags/split, `hat_brim_cap`, `outer_hood`; catalog regenerated.
- Tests: mask collect, transform limits, facial hair, hand overlay, legibility, combinations.
