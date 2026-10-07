# ST-2 PR 2 · `import_art.py import`

**Date:** 2026-10-07 · **Branch:** `tools/import-art-apply` · **Spec:** `docs/handoff/ART_IMPORT_TASK.md` PR 2

## What was implemented

`python tools/import_art.py import <dir> [--pack id]` copies a drop that already passes `check` into the pack.

| Criterion | Result |
| --- | --- |
| Refuse on any `check` error and write nothing | met |
| Copy the SVG to `art/<pack>/<id>.svg` | met |
| Same or lower `data-content-version` refuses and leaves the drop in place | met |
| A higher version updates the manifest entry in place, including `conflictsWith` | met |
| Manifest entry matches shipped shape: id, category, label, vector render, collection, `proprietary-idl`, content version, color slots, `base_teardrop`, tags, tier | met |
| Provenance kept without a parser change | met — sidecar `art/<pack>/<id>.provenance.json` |
| `asset_pipeline` rebuild and catalog regeneration | met, against injectable paths |
| Drop moved to `art/incoming/.imported/<id>-v<n>/` | met |
| Summary: id, version, slots, parts per band, review notes | met |
| Each fixture imports into a temp pack; the real catalog and manifest stay untouched | met |

Omitted `*.shadow` and `*.highlight` slots are filled from the primary with the AP-9 OKLCH deltas. `#3A6EA5` becomes shadow `#104B82` and highlight `#5D8CBF`, locked in both the Python test and `OklchTest`.

## Files

- `tools/import_art.py` — `import` command, `RepoPaths`, OKLCH port
- `tools/asset_pipeline.py` — `build_pack` takes optional `art_root` and `version_pack`
- `tools/gen_asset_catalog.py` — `build` and `write` take optional paths
- `tools/tests/test_import_art.py` — apply tests
- `app/src/test/java/app/idl/domain/avatar/OklchTest.kt` — frozen derived hex
- `docs/avatar/AVATAR_PROGRAM.md`, `master-plan.md` — ST-2 still 🔨; PR 1 noted as #57

## Commands

| Command | Result |
| --- | --- |
| `python -m unittest discover -s tools/tests -v` | 35 tests, OK |
| `scripts/check.ps1` | Picture pipeline 35 tests OK. `asset_pipeline.py check` and `gen_asset_catalog.py check` OK. Gradle: 298 tests, 0 failed, 1 skipped. Lint: 0 errors, 42 warnings. **All checks passed.** |
| `scripts/check.ps1 -Device` | not applicable |
| `scripts/check.ps1 -Sql` | not applicable. The catalog SQL seed is generated into the temp root in tests. F-39 still means a seed edit does not update an already-migrated database |

## Deviations

- The importer calls `build_pack` and `gen_asset_catalog.build` / `write` in process so tests can pass a temp root. There is no `--root` flag.
- `AssetDef` has no provenance field and `IdlJson` ignores unknown keys, so provenance is a sidecar, not a manifest field.
- Derived colors are uppercase `#RRGGBB`. Highlight is added only when the SVG uses that slot.

## Known limitations

- Review sheets are PR 3. F-33 is PR 4. A real studio export is PR 5.
- Letters and brands are still a printed reminder, not a detector.
- Untracked `ears_cat.png` and `ears_fox.png` are not part of this PR.

## Commits

This report is in the same commit as the importer. The hash is in the pull request.
