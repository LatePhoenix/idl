# 2026-10-06 — AP-4 picture pipeline

Branch `avatar/ap-4-pipeline-b` from `origin/main` (`fb3cf24`, merge of PR #36). Spec: `docs/avatar/AVATAR_PROGRAM.md` §6 AP-4 and §3.9. This is pull request (b): `tools/asset_pipeline.py`, SVG sources for every `emoji_core` picture, and the CI check. Contact sheets are the next pull request. The row stays 🔨.

## Acceptance

| Criterion | Result |
| --- | --- |
| `build`, `validate`, and `check` | Met. Standard library only. `check` rebuilds to a temporary directory and fails if a committed picture differs. |
| Stable output | Met. Rebuilding the 15 `emoji_core` pictures matches the committed JSON byte for byte. |
| SVG subset | Met. `path`, `rect`, `circle`, `ellipse`, `polygon`, and `g` transforms. Arcs become cubics. Text, images, filters, scripts, and external references are rejected. |
| SVG source for every shipped picture | Met. `art/emoji_core/*.svg`. |
| Tests in `tools/tests/` from `check.sh`, `check.ps1`, and CI | Met. |
| Throwaway asset needs no Kotlin change | Met. See below. The asset was removed. |
| `PackContactSheetTest` | Not in this pull request. |

## Throwaway asset

Added `art/emoji_core/_probe.svg`, a schema 1 rect. `python tools/asset_pipeline.py build emoji_core` wrote `pictures/_probe.json` with commands `M 40 40 L 120 40 L 120 120 L 40 120 Z`. `git status` showed no Kotlin files. Both files were deleted, and `check` passed again.

## Commands

```
scripts/check.ps1
```

Picture pipeline: **8 tests, OK**. Gradle: **246 tests, 0 failed, 1 skipped**. Lint **0 errors, 42 warnings**.

`--device` was not required: this pull request does not change `avatar/` or `ui/`. `--sql` was not required. No goldens changed.

## Deviations

- A clip definition is a `path` with `data-clip-path`. The subset does not include a `clipPath` element.
- Gradients are `linearGradient` and `radialGradient` in `defs`, because `data-gradient` needs a target. `gradientUnits` must be `userSpaceOnUse`.
- `S` and `T` are kept only when the path is copied unchanged. A transformed path or an arc is written with `C` and `Q`.

## Commits

- `6fce614` Build picture JSON from SVG sources so the art files are the source of truth.
