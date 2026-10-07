# Cursor → Claude handoff · paused before ST-2

**Date:** 2026-10-07 · **Parked on:** `main` (`8989a39` at handoff authorship) · **ST-2:** not started

## Read first

Cursor stopped before ST-2 at the user's explicit request. AD-1 and AP-10 are merged and tagged.
Resume the avatar program with **ST-2** when the user says so. Do not mark ST-2 🔨 until work begins.

## What landed

| Item | Status | PR | Tag | Report |
| --- | --- | --- | --- | --- |
| AD-1 style exploration sheets (Q17) | ✅ | [#51](https://github.com/LatePhoenix/idl/pull/51) | `avatar-ad-1` | `docs/handoff/reports/2026-10-07-ad-1-style-exploration.md` |
| AP-10 store-ready / D-48 guard | ✅ | [#52](https://github.com/LatePhoenix/idl/pull/52) | `avatar-ap-10` | `docs/handoff/reports/2026-10-07-ap-10-store-ready.md` |

Main CI was green after both merges.

## Where the AD-1 sheets live

- Overview: `docs/art/exploration/v3/overview.png`
- Per direction: `docs/art/exploration/v3/{A–G}/sheet.png`
- Guide + agent lean (**F** readable volume): `docs/art/exploration/v3/README.md`
- Sandbox sources: `tools/studio/explore/{A–G}/`
- User review queue row is in `master-plan.md` §4.1 (verdict still empty)

## Still needs the user

- **Q17 / AD-2 👤** — pick art direction from the AD-1 sheets (and Q16 when convenient). AD-3, AP-13, and AP-15 wait on AD-2. Program work that does not depend on AD-2 (ST-2, ST-3, AP-11, AP-12) can proceed without that pick.

## Next item when resumed

1. `master-plan.md` §0–§1 and §4.0
2. `docs/avatar/AVATAR_PROGRAM.md` §5 — first ready ⬜ row: **ST-2** (deps ST-1 ✅)
3. `docs/handoff/CURSOR_RUNBOOK.md` §1 onward
4. Spec: `docs/handoff/STUDIO_TASK.md` §ST-2

Do **not** implement ST-2 in this handoff session.

## Stop reason / session state

- User asked Cursor to stop before ST-2
- Keep-working loop killed
- No open agent PRs expected
- Working tree may still show untracked leftover snapshots (stash from earlier work, **not** part of AD-1/AP-10): `app/src/test/snapshots/widget/ears_cat.png`, `ears_fox.png` — leave untracked unless a completed item claims them

## Pointers

| Doc | Why |
| --- | --- |
| `master-plan.md` §0, §4.0, §6 (Q17), §8 | Living status, pause note, open questions, work log |
| `docs/avatar/AVATAR_PROGRAM.md` §5 | Status table (AD-1/AP-10 ✅, ST-2 ⬜) |
| `docs/handoff/CURSOR_RUNBOOK.md` | How to run the next item |
| `docs/handoff/STUDIO_TASK.md` | ST-2 acceptance criteria |
| `docs/avatar/ART_STUDIO.md` | Studio phases; AP-8..AP-10 engine deps are ✅ |

## Commands / hygiene at handoff

```text
git fetch --prune origin
git status          # main clean except optional untracked ears_*.png
gh pr list --author "@me" --state open   # none
gh run list --branch main --limit 3      # green after #51 / #52
```
