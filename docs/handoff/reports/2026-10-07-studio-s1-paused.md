# Art Studio S1: paused (work in progress)

**Who:** Claude · **Date:** 2026-10-07 · **Branch:** `tools/studio-s1` (not a PR yet)

The user paused Studio work to rethink the avatar's look before more development. This branch
holds the S1 work so far. It's built on `tools/studio-s0` (PR #46), which is built on #45.

## Done on this branch (lightly tested)

- `engine/paths.py`: path parsing through `asset_pipeline.parse_path`, Skia booleans (union,
  subtract, intersect), offset, thicken, mirror, smooth curves, area and thin-detail measures.
- `engine/guides.py`: head guides computed from the shipped art (head outline, rows, eye, brow,
  mouth and expression zones, eye centres, anchors). Checked by hand.
- `engine/render.py`: Skia renderer mirroring the app, plus labelled sheets. Checked against
  `hair_512.png`: same geometry and layering.
- `engine/compose.py`, `engine/kits.py`, `engine/drafts.py`, `engine/legibility.py`,
  `engine/lint.py`, `kits/*.json` (hair, headwear, eyewear, facial hair): written. **Not run yet.**
- `requirements.txt` (skia-python, numpy). The venv is `tools/studio/.venv` (gitignored).

## Not done

- The CLI commands for S1 (`kit`, `guides`, `draft`, `lint`, `render`, `geom`, `setup`) in `studio.py`.
- Drafts view in the web UI, the Claude Code skill, tests, the S1 acceptance run (beanie and
  hairstyle from one prompt each).

## Findings to carry into the next audit

1. `hat_brim_cap` (AP-8) sits on the forehead (crown y ~180–360, brim to y ~400): it's too small,
   it covers the raised brows, and its lower edge breaks the style guide's y 330 limit.
2. Raised brows reach y ~271, above the style guide's hairline limit (y 330). Bangs that follow
   the guide can still hide raised brows. The guide or the brow shapes need a decision.
3. `LegibilityTest` counts dark pixels anywhere on the canvas, so dark hair or a hat over the eyes
   can raise the count and pass. It should measure the eye and mouth zones (as
   `engine/legibility.py` does).
4. The happy expressions list `overlay_blush` (a `core_proto` procedural asset). The vector
   `overlay_cheek_blush` isn't used by any expression.

## Resume

Merge `origin/main` into `tools/studio-s0` and this branch, finish `studio.py`, run the
S1 acceptance, and open the PR. If the avatar look changes (a new base or style), the guides
and kits follow the art automatically. Only the kit tips and lint numbers that quote
coordinates need a review.
