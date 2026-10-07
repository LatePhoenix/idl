# AD-2 · art direction

**Date:** 2026-10-07 · **Branch:** `art/ad-2-direction`

## What was decided

The user delegated the style pick and asked to treat the current avatars as placeholders. Recorded as D-53.

| Question | Answer |
| --- | --- |
| Q17 | **F** (readable volume), with E's light border on widget framing only. The head stays the teardrop. |
| Q16 | **(a)** raised brows draw over front hair |

Shipped pictures are not restyled in this change. AD-3 does that after the editor and the widgets. Lint numbers stay as they are so the placeholder art keeps passing. F-38 still applies: when the hair is redrawn it has to sit outside the skull. The AD-1 sheets did not.

## Files

- `docs/IDL_DECISIONS.md` — D-53
- `docs/avatar/ART_STYLE_GUIDE.md` — target note; numbers unchanged
- `docs/avatar/AVATAR_PROGRAM.md` — AD-2 ✅
- `docs/handoff/STUDIO_TASK.md`
- `master-plan.md` — Q16, Q17, F-34, F-32, work log

## Commands

| Command | Result |
| --- | --- |
| `scripts/check.ps1` | 47 Python tests OK. Catalog OK. Gradle: 305 tests, 0 failed, 1 skipped. Lint: 0 errors, 42 warnings. **All checks passed.** |
| Device / SQL | not run. Docs only. |

## Deviations

AD-3's "update the guide numbers and restyle" is not this row. The user asked to lock function first.

## Known limitations

Q18 (licensing of generated art) is still open. It does not block the editor or the widgets.

## Commits

This report is in the same commit as the decision.
