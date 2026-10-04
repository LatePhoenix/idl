# Checkpoint — emoji-style avatar architecture

**Date:** 2026-10-04  
**Task:** User direction to customize standard face emoji as avatars, via the long compositor prompt.  
**Scope taken:** inspect the repo, write the design, choose the renderer. No renderer code, no asset import, no Gradle changes.

## Acceptance

The prompt's first instruction was to inspect the repo and produce the architecture documents and the renderer decision. That part is met. Steps 8–17 (scaffold, models, editor, export, tests, a green `scripts/check.sh`) are not met. They are listed as the next order in `docs/ROADMAP.md`.

| Criterion | Status |
| --- | --- |
| Inspect the existing repo and keep its conventions | Met |
| `docs/ARCHITECTURE.md` | Met |
| `docs/ASSET_SPEC.md` | Met |
| `docs/AVATAR_RECIPE_SCHEMA.md` | Met |
| `docs/LICENSING.md` | Met |
| `docs/ROADMAP.md` | Met |
| ADR comparing Canvas path data vs AndroidSVG | Met (`docs/adr/0001-vector-asset-renderer.md`) |
| Modular Android scaffold | Not done. D-01 forbids a second module layout. |
| Domain models, renderer, editor, export, tests | Not done |
| `scripts/check.sh` | Not run. No code changed. |

## Files

Created:

- `docs/ARCHITECTURE.md`
- `docs/ASSET_SPEC.md`
- `docs/AVATAR_RECIPE_SCHEMA.md`
- `docs/LICENSING.md`
- `docs/ROADMAP.md`
- `docs/adr/0001-vector-asset-renderer.md`
- `config/emoji_face_scope.json`
- `docs/handoff/reports/2026-10-04-emoji-avatar-architecture.md`

Updated:

- `docs/IDL_DECISIONS.md` (D-39)
- `master-plan.md` (reference row and §5)

## Commands

No Gradle, lint, or unit tests. Inspection was read-only plus two lookups:

- Noto Emoji `main` commit `e20cbc2bbec1926686be9f9bee7d1d2cfa1fea0e` (2026-09-24, "Emoji 18")
- Unicode `emoji-test.txt` version 18.0, date 2026-04-30
- `svg/LICENSE` is Apache-2.0 (Copyright 2013 Google, Inc.). Fonts are OFL and are excluded.
- AndroidSVG `com.caverock:androidsvg` 1.4, released 2019-05-28, no later Maven release

## Deviations from the pasted prompt

- One `:app` module stays (D-01). Packages: `app.idl.domain.avatar`, `app.idl.avatar`.
- The recipe is schema 3 of `AvatarConfiguration`, not a new `AvatarRecipe` type.
- Presence chrome (availability, activity, reactions) uses z 200+, above the character stack, so D-28 survives.
- Noto files were not copied. `THIRD_PARTY_NOTICES.md` waits for the first import.
- Recommended first pictures are original, so the renderer can land without an upstream import. The license rules still allow a later pinned Noto import.
- F-01 and F-02 were fixed on main after this design was drafted. The vector painter still has to draw availability and activity badges when it takes over the widget.

## Renderer decision

Path IR drawn by Android Canvas, behind `VectorAssetRenderer`. AndroidSVG 1.4 is unmaintained as a release and fights semantic recoloring and golden tests.

## Known limits

- No picture has been drawn, so fidelity, 48 px readability, and the performance targets are unmeasured.
- OKLCH derivation, anchors beyond the current 0..1 set, and the editor are specified only.
- D-31 (explicit accessory vs signature) is still unrecorded.

## Commits

None. Not requested.

## Next

`docs/ROADMAP.md`, implementation step 1: branch from `origin/main`, then schema 3 with tests, before any SVG import.
