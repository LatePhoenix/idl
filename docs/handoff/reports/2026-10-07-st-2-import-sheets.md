# ST-2 PR 3 · `import_art.py sheets`

**Date:** 2026-10-07 · **Branch:** `tools/import-art-sheets` · **Spec:** `docs/handoff/ART_IMPORT_TASK.md` PR 3

## What was implemented

`python tools/import_art.py sheets <id>` writes `docs/handoff/sheets/<id>/review.png` through the in-repo Studio renderer.

| Criterion | Result |
| --- | --- |
| Renders the item on `base_teardrop` at 512 and 48 px | met |
| Light and dark | met |
| Neutral, smile, and open | met |
| Shown next to the shipped hats or glasses it can sit with | met |
| Sheets are tool-only; CI does not need the Skia venv | met — the render test skips when `tools/studio/.venv` is absent |
| Sample sheet reviewed | met — `docs/handoff/sheets/glasses_round_wire/review.png` |

## Files

- `tools/import_art.py` — `sheets` command and `sheet_neighbors`
- `tools/studio/engine/review_sheet.py` — Skia sheet
- `tools/studio/studio.py` — `sheets` subcommand
- `tools/tests/test_import_art.py`
- `docs/handoff/sheets/glasses_round_wire/review.png`
- `docs/avatar/AVATAR_PROGRAM.md`, `master-plan.md`

## Commands

| Command | Result |
| --- | --- |
| `python -m unittest tools.tests.test_import_art.ImportArtSheetsTest -v` | 6 tests, OK, including one Skia render (2.4 s) |
| `scripts/check.ps1` | Picture pipeline 43 tests OK. Catalog OK. Gradle: 298 tests, 0 failed, 1 skipped. Lint: 0 errors, 42 warnings. **All checks passed.** |
| Device / SQL | not applicable |

## Deviations

- Smile uses the shipped expression `smiling_face_with_smiling_eyes`. Open keeps the neutral eyes and brows and swaps in `mouth_round_open`. No shipped expression is an open mouth without spiral eyes.
- A hat is shown with each shipped glasses item, glasses with each shipped hat, and hair with each shipped hat. An explicit `conflictsWith` on either side is included when that other item is a hat or glasses.
- `import_art.py` stays stdlib. It delegates to `tools/studio/studio.py`, which re-executes inside the Studio venv.

## Known limitations

- F-33 is PR 4. A real studio export is PR 5.
- The sample sheet shows the current shipped head, including the cheek mark already in that render.

## Commits

This report is in the same commit as the sheet command. The hash is in the pull request.
