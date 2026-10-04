# Avatar bases v2 — exploration checkpoint

Date: 2026-10-04. Visual discovery only. No renderer, manifest, Room, or canonical lock. No commit.

Plan: avatar base exploration (v2). Prompts: `docs/art/avatar-base-prompts-v2.md` §4–§5, plus the style contract on every base prompt. Output: `docs/art/exploration/v2/`.

## Acceptance

| Criterion | Result |
|---|---|
| Twelve faceless 1:1 bases | Met. Ten vector bases kept from generation (ghost B is the retry). Pixel A and B are authored 24×24 grids. |
| Pass/fail against the base gate and §5.3 | Met. All twelve kept bases pass. Two generative attempts were rejected and replaced (ghost B hollow head; both pixel grids). |
| 48px expression contact sheet for every passing base | Met. Looked at the 48×48 cell sheets on white and on `#0F1722`. Happy, sad, angry, and sleepy stay distinct on every kept base. |
| One face-parts sheet for the leading non-pixel style | Met, with a deviation: the generated kit is saved, and the sheets use one authored glyph set (see below). |
| One recommended silhouette per family | Withdrawn on 2026-10-04. See Recommendation. |
| Renderer, manifest, Room unchanged | Met. |

## Base checklist

Pass and fail calls in this checklist are historical generation notes. A pass is not a selection.

Fill is the content bounding box as a fraction of the 1024×1024 frame. “About 70%” was judged on the long side for tall figures, then confirmed by looking at the 48px sheet. A base that is only ~50% of the width still passes when the long side is ~70–85% and the face stays readable at 48px.

| Base | Call | Why |
|---|---|---|
| blob-a | **Pass** | Faceless mint gumdrop, nub arms, one white highlight, flat body (center samples stay within ~5 levels). Box 73% × 70%. Empty face is most of the body. Even outline. No text, shadow, or props. Happy arcs, sad frown + tear, angry V brows, and sleepy bars stay distinct at 48px on white and on `#0F1722`. |
| blob-b | **Pass** | Faceless coral dome, three cream drip feet, flat fills, wobbly outline as asked. Box 76% × 77%. Face sits in the dome, clear of the drips. Same four emotions distinct at 48px; the feet still read on both backgrounds. |
| bot-a | **Pass** | Faceless screen-face robot, antenna kept, flat sky-blue screen `(115, 195, 254)`. Box 51% × 85%. The width is under 70%; the scale-up retry did not change it (519×869 vs 518×868). Kept the first image because the 48px sheet still shows a recognizable robot and the blank screen separates the four emotions. White body reads on `#0F1722` via the dark outline. |
| bot-b | **Pass** | Faceless cream face plate in a mustard dome, charcoal joints, one red chest light, no rivets or halftone. Box 62% × 77%. Face plate is the largest of the set. Four emotions distinct at 48px. Ear bolts and claws are the detail cost. |
| ghost-a | **Pass** | Faceless sheet, dark outline, pale lavender side shade `(~225, 209, 236)`. Body samples are ivory-white `(~254, 251, 244)`, lighter than the requested `#F4EFE6`. It does not vanish: the outline and lavender edge hold on white, and the sheet is obvious on `#0F1722`. Box 66% × 85%. Four emotions distinct. |
| ghost-b | **Pass** (retry) | Attempt 1 was a hollow teal ring (white hole, no face surface). Saved as `bases/ghost-b-attempt1-hollow.png`. The no-reference retry is a solid teal head `(~0, 175, 178)` with a flat darker blue tail `(~11, 117, 159)`, not a gradient. Box 51% × 76%. Comma tail and the four emotions read on white and on `#0F1722`. |
| critter-a | **Pass** | Faceless fox, ears kept, cream muzzle, nose only, flat orange. Box 59% × 88%. Eyes sit on the forehead; the mouth is on the muzzle. At 48px, happy / sad / angry / sleepy stay distinct because of arcs, the tear, the V brows, and the sleepy bars. Mouth shapes collapse toward one bar. |
| critter-b | **Pass** | Faceless sage bear-bunny, short ears, cream belly, no felt, stitching, or button eyes. Box 58% × 77%. Open face. Mouths as well as eyes stay distinct at 48px. |
| orb-a | **Pass** | Flat lavender ring, darker violet rim, pale inner disc `(247, 243, 242)` covering the center. Not a radial gradient and not a hole. Box 71% × 72%. Dark glyphs on the disc are the clearest orb faces at 48px. |
| orb-b | **Pass**, with a dark-wallpaper limit | Flat deep-blue sphere `(0, 74, 179)`, horizontal yellow ring below the upper two thirds, no moon. Box 84% × 83%. Dark ink disappeared on the blue, so this base uses the same glyph shapes in cream. Happy, sad, angry, and sleepy still separate. Crying streams read as a pale bracket. On `#0F1722` the ring’s enclosed white hole stays white, because it is not connected to the page edge. |
| pixel-a | **Pass** (authored) | Generative try is not a 24×24 grid: anti-aliased, many cyan shades. Scrap: `bases/pixel-a-generated.png`. Authored grid is 24×24, 4 colors + transparent, nearest-neighbor scaled to 384px for viewing. Largest flat cyan block is 13×8. Two feet, one white highlight. Four emotions distinct at 48px (nearest 2× of the 24×24 cell). |
| pixel-b | **Pass** (authored) | Generative try is off-grid and shaded. Scrap: `bases/pixel-b-generated.png`. Authored 24×24 Game Boy greens, sprout kept, flat mid-green face forced to 12×8 (verified pixel-for-pixel). Four emotions distinct at 48px on white and on `#0F1722`. The 12×8 block squares the cheeks. |

No kept base was rejected on a second miss. Bot A’s retry failed to fix width and the first image was kept. Ghost B’s second image passed. Pixel did not get a second generative try.

## Retries

| Base | Failure named | What changed |
|---|---|---|
| ghost-b | Head was a hollow ring, so there was no face surface | Retry, no reference. Solid teal head. Kept. |
| bot-a | Character only ~51% of the frame width | Retry referenced the faceless first pass and asked for a larger robot. Size did not change. First image kept. |
| pixel-a, pixel-b | Not a true equal-pixel 24×24 grid | No second generation. Replaced with authored PNGs. |

## How the sheets were made

The image model was not asked to redraw any character 16 times.

`parts/face-parts.png` is the generated §5 kit (4:3). Cropping it was unreliable: brow arcs are near-duplicates, the steam puff became a cloud, and several eyes are shaded. Composites use one Pillow glyph set in the same thick charcoal style (`parts/face-glyphs.png`): oval, dot, happy arc, half-lid, wide, heart, spiral, wink, smirk; line, smile, grin, frown, o, tongue, wavy; blush, tear, streams, sweat, steam, Z. Every vector base gets that same set, stamped into a measured empty face box. The silhouette pixels are the generated base.

Orb B’s face luminance is ~66, so those glyphs are cream instead of charcoal. Shapes are unchanged. Tear marks on that base are cream as well; blue tears vanished into the sphere.

Pixel sheets stamp a matching pixel glyph set (`parts/face-parts-pixel.png`) into the flat face block, then nearest-neighbor scale. Pixel B’s first stamp landed on the stem; it was moved into the 12×8 block before the 48px check.

`build_sheets.py` is the reproducible composite. Exterior near-white connected to the page edge is cleared before the dark size check, so ivory, the bot’s white body, and the orb disc stay.

Size-check files `*-white.png` and `*-dark.png` are the real 48px sheets: each cell is 48×48, gap 4, page 212×212. `*-x4.png` is a nearest-neighbor blowup of that exact raster, used to see the 48px pixels. Vector cells are LANCZOS from the keyed 1024 image. Pixel cells are nearest-neighbor from the 24×24 sprite.

The 48px blob-a white sheet was inspected at native size as well as at 4×. Happy arcs, the sad tear, angry brows, and sleepy bars are still different pictures.

## Recommendation

The user rejected the set on 2026-10-04 and withdrew every family pick (blob B, bot A, ghost B, critter B, orb A, pixel A).

The temporary stand-in is blob A only, judged from the 48px sheets enlarged 4×: blob-a-white-x4.png and blob-a-dark-x4.png. It is a design reference, not a canonical layer and not wired into the app.

## Files

- `docs/art/exploration/v2/build_sheets.py`
- `docs/art/exploration/v2/bases/` — `blob-a.png`, `blob-b.png`, `bot-a.png`, `bot-a-retry.png`, `bot-b.png`, `ghost-a.png`, `ghost-b.png`, `ghost-b-attempt1-hollow.png`, `critter-a.png`, `critter-b.png`, `orb-a.png`, `orb-b.png`, `pixel-a.png`, `pixel-a-24.png`, `pixel-a-generated.png`, `pixel-b.png`, `pixel-b-24.png`, `pixel-b-generated.png`
- `docs/art/exploration/v2/parts/` — `face-parts.png`, `face-glyphs.png`, `face-parts-pixel.png`
- `docs/art/exploration/v2/sheets/` — one 4×4 sheet per base
- `docs/art/exploration/v2/size-check/` — `*-white.png`, `*-dark.png`, and `*-x4.png` inspection blowups
- `master-plan.md` — one work-log line

## Commands

Image generation used the Cursor image tool, `aspect_ratio` `1:1` (face-parts `4:3`). Pillow 12.3.0 was already installed.

```text
python docs/art/exploration/v2/build_sheets.py
```

Exit 0. `scripts/check.sh` was not run: no application code, tests, or contracts changed.

## Deviations

- Expression sheets are composited. The generated face-parts kit is reference only.
- Ghost A is lighter than `#F4EFE6`. The outline and lavender shade are what keep it visible.
- Bot A stayed narrow after its one retry. Passed on the 48px read, not on a 70% width.
- Orb B glyphs are cream so they survive the blue sphere.
- Pixel viewing PNGs are 16× nearest-neighbor of the 24×24 masters (`pixel-a-24.png`, `pixel-b-24.png`).

## Limitations

- Not traced into layers. Not in the manifest. Not a style lock.
- Critter A mouths will not carry the full vocabulary until the muzzle is a separate layer.
- Orb B’s ring hole has to be transparent in any traced asset.
- The sleepy “Z” is a small mark at 48px. Sleepy is distinct because of the eye bars, not because the letter is large.
- JPEG bases have edge anti-aliasing. Flat-fill judgments used interior samples, not the compressed edge.

## Commits

None.
