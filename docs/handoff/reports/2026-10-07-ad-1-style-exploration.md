# Checkpoint · AD-1 style exploration sheets

**Date:** 2026-10-07 · **Branch:** `art/ad-1-style-exploration` · **Item:** AD-1

## Acceptance (STUDIO_TASK.md §AD-1)

| Criterion | Status |
| --- | --- |
| Sandbox only under `tools/studio/explore/<direction>/*.svg`; no `art/` or pack edits | Met |
| Directions A–G with coherent sets (neutral/happy/sad/surprised; short/long/curly; beanie, glasses, tee) | Met |
| Hair reads as hair at 48 px in B–G (volume, hairline, part, strands); A is shipped baseline | Met |
| A = current; B bold/flat; C big-eye soft; D cel; E sticker; F proposal; G rounder head (reopens D-45/D-46) | Met |
| Per-direction sheet: head 48(4×)+96, bust 512, light/dark; `overview.png` side-by-side | Met |
| Sources + sheets in `docs/art/exploration/v3/` with README; review queue + Q17 options; continue (no wait) | Met |

## What was implemented

- Generator `tools/studio/explore_ad1.py`: builds explore SVGs from shipped art + ST-1 acceptance beanie/curly + geometry helpers, then renders Studio sheets.
- **A:** references shipped pack; sandboxes only beanie + curly.
- **B–F:** teardrop head; improved hair geometry; style transforms (flat/bold, big-eye, cel shade, sticker border, readable-volume proposal).
- **G:** circular head (`base_round`), aliased over `base_teardrop` for compose; labelled on sheets.
- Docs: `docs/art/exploration/v3/README.md`, master-plan review queue + Q17 options A–G, AVATAR_PROGRAM §5 AD-1 ✅.

## Recommendation

**F** (readable volume). See v3 README. Hybrid: F + E’s sticker border.

## Files created or changed

- `tools/studio/explore_ad1.py`
- `tools/studio/explore/{A–G}/*.svg` (+ README per direction)
- `docs/art/exploration/v3/overview.png`
- `docs/art/exploration/v3/{A–G}/sheet.png` + copied sources
- `docs/art/exploration/v3/README.md`
- `docs/avatar/AVATAR_PROGRAM.md` (AD-1 🔨→✅)
- `master-plan.md` (review queue, Q17, §8 work log)
- `docs/handoff/reports/2026-10-07-ad-1-style-exploration.md`

## Commands run

```
python tools/studio/studio.py setup
tools\studio\.venv\Scripts\python tools/studio/explore_ad1.py
tools\studio\.venv\Scripts\python -m unittest discover -s tools/studio/tests -t tools/studio -q
cmd /c "python -m unittest discover -s tools/tests -q && python tools/asset_pipeline.py check && python tools/gen_asset_catalog.py check"
```

Results: Studio tests **28 OK**; tools tests **8 OK**; `asset_pipeline.py check` exit 0; `gen_asset_catalog.py check` → `asset catalog ok`.

## Deviations

- Surprised is composed from `eyes_round_wide` + `brows_round_raised` + `mouth_round_open` (no pack expression id).
- Direction A does not duplicate every shipped SVG; README documents that (allowed by the task).
- Beanie uses ST-1 acceptance draft (`hat_beanie_slouch`), not a shipped `emoji_core` beanie (none exists).

## Known limitations / open questions

- Explore SVGs are not linted against kits; AD-3 will restyle for real.
- Cel shade (D) and sticker (E) are stylised proofs — edge fidelity at 48 px should be judged by eye on the sheets.
- Q17 / AD-2 still needs the user’s pick (and Q16).

## Commits

```
d5d6c27 Add AD-1 style exploration sheets A–G and mark AD-1 done.
0b6aa98 Mark AD-1 in progress for style exploration sheets.
```
