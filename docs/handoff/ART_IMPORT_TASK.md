# Cursor task: ST-2, the art importer (`tools/import_art.py`)

**Written by:** Claude, 2026-10-07 · **Spec:** [`docs/avatar/ART_INTERCHANGE.md`](../avatar/ART_INTERCHANGE.md)
§4–§9 (D-51, D-52) · **Process:** [`CURSOR_RUNBOOK.md`](CURSOR_RUNBOOK.md) unchanged · **Context:**
[`COORDINATION.md`](../../COORDINATION.md). Claude audits when you're done.

The external iDL Art Studio drops one folder per accepted item into `art/incoming/<assetId>/`. ST-2
turns a drop into a reviewed PR. This replaces the "promote" half of the old ST-2/ST-3 plan
(`STUDIO_TASK.md` notes the re-scope). F-33 (legibility parity) stays in ST-2 as PR 4 below.

You don't need the studio, ComfyUI or a GPU: build and test everything against the fixtures in
`tools/testdata/art_incoming/` (three exporter outputs, synthetic drawings — never ship them).

## What a drop looks like (spec §8)

```text
art/incoming/<assetId>/
  <assetId>.svg       spec §7 subset, data-schema-version 2
  meta.json           id, pack, category, accessibilityLabel, tags, tier, colorSlots, patternRegions?,
                      provenance {tool, commit, model, controlnet, seed, prompt}, reviewNotes?
  lineart_512.png  preview_512.png  preview_48.png  preview_colors.png   (studio previews; review only)
```

The fixtures carry only the SVG, `meta.json` and `lineart_512.png`. Don't require the preview PNGs.

## Build it in four PRs (branch per PR from `origin/main`)

### PR 1 · `tools/import-art-validate`: `python tools/import_art.py check <dir>`

Standard library only (like `asset_pipeline.py`). Reuse `asset_pipeline.parse_svg` for parsing; add
the interchange checks on top. Report every problem with the part id, then exit 1.

- §7: `data-schema-version="2"`, `data-id` = folder name = `meta.id`, lowercase snake case, prefix by
  category (`hair_`, `beard_`/`mustache_`, `hat_`, `glasses_`, `top_`, `prop_`).
- Every drawn element: unique `data-part`, `data-z`, `data-slot`; no `fill`, `style`, `class`,
  `<image>`, filters.
- Bands per category (§6): hair 20 (tag `hair_back`) or 70 (tag `hair_top` + `data-clip-by
  ="occlude.hair_top:difference"`); facial hair 60; headwear 90; eyewear 80; tops 36/38; props 100/110.
- Bounds per band: −16..1040, or the body region for bands 0, 20, 34, 36, 38 (`AVATAR_PROGRAM.md`
  §3.1). Imported art never uses `data-allow-overflow` (D-52: no hat overflow; the studio never
  emits it): reject the attribute on any part. Test: a band-90 hat outside −16..1040 with
  `data-allow-overflow="true"` fails.
- Exactly one line-art part **per band** (`data-fill-rule="evenodd"`, slot `outline`, or
  `glasses.frame` for eyewear). Hair has two (D-52).
- Every region at least 32 units thick. "Thick" means the diameter of the region's **largest inscribed
  circle** (spec §5 step 5, "smallest dimension") — the same measure the studio uses when it merges
  thin regions, so the two sides agree. Rasterize each non-line part on a 1024 grid and take
  2 × the maximum distance-to-edge (a distance transform in plain Python on a coarse grid is fine;
  keep it stdlib). Don't require 32 units at every cross-section: every real shape tapers to thin
  tips (brim ends, hair points, corners), and the line art drawn on top keeps those legible.
- `meta.colorSlots` declares every slot the SVG uses, except `*.shadow` / `*.highlight` (derived by
  AP-9). Hex colours only.
- `meta.category` matches the id prefix: Hat → `head_accessory`, Eyewear → `face_accessory`, Hairstyle
  → `hair`, Facial hair → `facial_hair`, Top → `top`, Prop → `foreground_prop`.
- Under 16 KB (warn above 12 KB).
- No letters or brands is a human review item; print a reminder, don't try to detect it.

**Tests** (`tools/tests/test_import_art.py`, `unittest`, runs in `scripts/check.sh` and CI): all three
fixtures pass; one mutated copy per rule fails with the right message (write the mutations in the test
from the fixture text, don't commit broken fixtures).

### PR 2 · `tools/import-art-apply`: `python tools/import_art.py import <dir> [--pack emoji_core]`

1. Run `check`; refuse on any error.
2. Copy the SVG to `art/<pack>/<id>.svg`. If the id exists: require a higher `data-content-version`
   than the shipped file (spec §8 "new version"), else refuse.
3. Upsert the manifest entry in `app/src/main/assets/packs/<pack>/v*/manifest.json`, shaped like the
   existing items: `id`, `category`, `accessibilityLabel`, `render: {type: vector, file:
   pictures/<id>.json}`, `collection: <pack>`, `license: proprietary-idl`, `contentVersion`,
   `colorSlots`, `compatibleBases: ["base_teardrop"]`, `tags`, `tier`. Keep key order and 2-space
   formatting stable so diffs stay small. Provenance (spec §9 "keeps the provenance"): first check
   whether the manifest parser (`domain/avatar/`) accepts an extra `provenance` object; if it doesn't,
   don't change the parser — write it to `art/<pack>/<id>.provenance.json` next to the SVG instead.
4. Run `python tools/asset_pipeline.py build <pack>` and `python tools/gen_asset_catalog.py`.
5. Move the drop to `art/incoming/.imported/<id>-v<n>/` (gitignored) so a re-run is a no-op.
6. Print a summary: id, version, slots, parts per band, review notes from `meta.json`.

Don't touch git in the command; the PR flow is a runbook step, not code.

**Tests:** import each fixture into a temp copy of the pack (copy `art/<pack>` and the pack folder into
a temp root; make the paths injectable), check the manifest entry and the built picture JSON; a second
import of the same version refuses; a bumped version updates in place.

### PR 3 · `tools/import-art-sheets`: review sheets

`python tools/import_art.py sheets <id>` renders, with the in-repo Studio's renderer
(`tools/studio`, Skia venv, see `STUDIO_TASK.md`), the item on `base_teardrop` at 512 and 48 px, light
and dark, with three expressions (neutral, smile, open) and next to the shipped hats/glasses it can
conflict with. Write them to `docs/handoff/sheets/<id>/`. Sheets are tool-only: CI doesn't run them.

### PR 4 · `tools/legibility-zones`: F-33, legibility parity

From `master-plan.md` F-33: `LegibilityTest` sums non-skin pixels over the whole 48 px canvas, so a
dark shape over the eyes can still pass. Measure feature pixels **inside the eye and mouth zones**
instead (port the idea from `tools/studio/engine/legibility.py` into the Kotlin test).

**Acceptance:** a test fixture that covers the eyes with a dark shape fails `LegibilityTest`; every
shipped item and all three importer fixtures (imported into a temp pack) pass; F-33 is marked
resolved in `master-plan.md`.

### PR 5 · `art/import-<id>`: the pilot (after the studio exports a real item)

Blocked until the studio delivers a real generated item (GPU cooling, see `COORDINATION.md`). Then:
import it, add its sheets and a review-queue row (`master-plan.md` §4.1), open the PR. **The user
merges this first art PR** (D-50 as amended). After that, Cursor merges its own green art PRs.

## Acceptance

- PRs 1–4 merged (PR 5 waits for real art). `scripts/check.sh` is green with the new tests; CI unchanged apart from running them.
- All three fixtures pass `check` and import cleanly into a temp pack; the built pictures render
  in the existing pack tests without new failures.
- `art/incoming/` is ignored by git; nothing in the importer writes outside `art/<pack>/`, the pack's
  `manifest.json` / `pictures/`, the generated catalog, `art/incoming/.imported/` and
  `docs/handoff/sheets/`.

## Out of scope

Textures / `patternRegions` rendering (later spec), retire (ST-3), any change to the studio repo (file
a studio task in its `PLAN.md` instead — see `COORDINATION.md`).
