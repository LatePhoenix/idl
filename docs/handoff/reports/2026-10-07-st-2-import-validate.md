# ST-2 PR 1 · `import_art.py check`

**Date:** 2026-10-07 · **Branch:** `tools/import-art-validate` · **Spec:** `docs/handoff/ART_IMPORT_TASK.md` PR 1

## What was implemented

`python tools/import_art.py check <dir>` validates one art drop against `docs/avatar/ART_INTERCHANGE.md` §4–§9. Standard library only. It reuses `asset_pipeline.parse_svg`, then adds the interchange rules, prints every problem with its part id, and exits 1.

Acceptance for this PR:

| Criterion | Result |
| --- | --- |
| `check` is stdlib-only and reuses `parse_svg` | met |
| Schema 2, matching ids, snake case, category prefix | met |
| Unique part, z, slot; no fill, style, class, image, filter; no `data-allow-overflow` | met |
| Bands per category, including hair tags and `occlude.hair_top` clip | met |
| Bounds: −16..1040, body region on bands 0, 20, 34, 36, 38. Overflow attribute fails even when set | met |
| Exactly one line-art part per band (hair has two bands, D-52) | met |
| Region thickness is 2 × max distance-to-edge, at least 32 units | met |
| `meta.colorSlots` covers every used slot except `*.shadow` and `*.highlight`; hex only | met |
| Under 16 KB (error); over 12 KB (warning). The hair fixture warns and still passes | met |
| Letters and brands: printed reminder, not detected | met |
| All three fixtures pass; each rule has a failing mutation in `tools/tests/test_import_art.py` | met |
| `scripts/check.ps1` green | met |

## Files

- `tools/import_art.py` (new)
- `tools/tests/test_import_art.py` (new)
- `docs/avatar/AVATAR_PROGRAM.md` §5: ST-2 set to 🔨
- `master-plan.md` §1 and §4.0 updated for the resume. §1.3 no longer says widgets are procedural-only or that Room stores v1; schema 3 shipped in AP-7, and the editor is still AP-11

## Commands

| Command | Result |
| --- | --- |
| `python -m unittest tools.tests.test_import_art -v` | 22 tests, OK |
| `scripts/check.ps1` | Picture pipeline: 30 tests, OK. `asset_pipeline.py check` and `gen_asset_catalog.py check` OK. Gradle: 297 tests, 0 failed, 1 skipped. Lint: 0 errors, 42 warnings. **All checks passed.** |
| `scripts/check.ps1 -Device` | not applicable: no UI, widget, or renderer change. Device column is empty |
| `scripts/check.ps1 -Sql` | not applicable: no supabase, contract, or wire change. Server column is empty |

## Deviations and choices

- Thickness is a 1-unit raster of the filled path plus a separable Euclidean distance transform (Felzenszwalb), in the stdlib. A 40-unit square measures 40. The studio uses SciPy's EDT on its 512 mask; this is the same definition (diameter of the largest inscribed circle) on the 1024 grid the task asks for.
- Extra checks from §4–§7 that the bullet list did not spell out: lens opacity (0.35, or 0.75 with a `sunglasses` tag), headwear must publish `occlude.hair_top`, at most one shade per band, provenance keys present. The three fixtures pass these.
- `meta.colorSlots` may omit shade and highlight. The manifest fill-in is PR 2.
- Session start found untracked `app/src/test/snapshots/widget/ears_cat.png` and `ears_fox.png`. Stashed as `found at session start` (runbook §8). Not part of this PR.

## Known limitations

- `check` does not copy files, build pictures, or open a PR (PRs 2–3).
- F-33 (zone legibility) is PR 4.
- No letter or logo detector.

## Commits

`984a273` Add interchange checks so a bad art drop is refused before import.
