# Coordination: iDL app ↔ iDL Art Studio

Two repos, one product. This file says who owns what and how work crosses between them, so each
repo can be worked on by its own agent without waiting for the other. The studio repo has a mirror
of this file (`COORDINATION.md` there); **this one is the source of truth.**

| | iDL app (this repo) | iDL Art Studio |
| --- | --- | --- |
| Repo / checkout | `LatePhoenix/idl` · `Z:\Singularity\idl` | `LatePhoenix/idl-art-studio` · `Z:\ai-tools\idl-art-studio` |
| Builds | The app, the asset pipeline, the **importer** (`tools/import_art.py`, ST-2), review sheets | Generation on local ComfyUI, line-art cleanup, region labelling, the exporter |
| Plan | `master-plan.md`, `docs/avatar/AVATAR_PROGRAM.md` §5 | `PLAN.md` |
| Agent instructions | `AGENTS.md`, `docs/handoff/CURSOR_RUNBOOK.md` | `AGENTS.md`, `docs/CURSOR_RUNBOOK.md` |

## Roles

- **Cursor does the building** in both repos, one session per repo (never one session editing both).
- **Claude audits**: reads merged PRs against the spec and the task docs, files findings as tasks,
  and writes the next task doc. Claude only edits code when an audit finding is quicker to fix than
  to describe.
- **The user** decides anything the spec, `docs/IDL_DECISIONS.md` or a task doc doesn't cover.

## The contract

`docs/avatar/ART_INTERCHANGE.md` ("the spec", D-51, D-52) defines everything that crosses: the
coordinate map, the rig's face, the SVG subset, slots, bands, ids, `meta.json` and the hand-off
folder. **The spec lives here and wins over anything in the studio repo.**

Changing the contract:
1. A PR in **this** repo changes the spec (and `IDL_DECISIONS.md` if it's a decision). Merge it first.
2. Then a PR in the studio follows it. The studio's `studio/spec.py` mirrors the spec's tables and
   cites the section it copies.
3. If the app's base face art changes (`art/emoji_core/base_teardrop.svg`, `eyes_round_neutral`,
   `brows_round_relaxed`, `mouth_round_neutral`, or `beard_full`'s `mouth_hole`), open a studio task to
   copy it again into `templates/app_face/` (the studio records the iDL commit it copied from).

## What crosses the boundary (and nothing else)

| Direction | What | Where |
| --- | --- | --- |
| Studio → app | One folder per accepted item | `art/incoming/<assetId>/` in this checkout (gitignored here) |
| App → studio | Base face SVGs, the beard mouth hole (copies) | studio `templates/app_face/` |
| App → studio | The asset reader, run read-only by the studio's tests | `tools/asset_pipeline.py` |
| Both ways | Shared test fixtures | here: `tools/testdata/art_incoming/` (made by the studio's exporter) |

Hard rules:
- The studio **writes only `art/incoming/<assetId>/`** here, never runs git here, and never edits
  anything else in this repo (D-51).
- This repo never edits the studio repo. Studio changes are studio tasks.
- `art/incoming/` is a drop zone: the importer moves accepted items into `art/<pack>/` and the PR.
  Nobody commits `art/incoming/` (it's in `.gitignore`).

## Shared fixtures keep the two sides honest

- `tools/testdata/art_incoming/` holds three exporter outputs (a hat, a hairstyle, glasses). They are
  **synthetic** test drawings, not art to ship. The importer's tests (ST-2) must accept all three.
- The studio's tests parse every exported SVG with this repo's `tools/asset_pipeline.py` (read-only).
- When the exporter's output format changes, the studio regenerates the fixtures and a small app PR
  updates them here. A fixture change and an importer change never share a PR.

## Sequencing (what unblocks what)

1. ✅ Spec merged (D-51), clarified (D-52).
2. **App: ST-2 importer** — `docs/handoff/ART_IMPORT_TASK.md`. Doesn't need the GPU or the studio
   running; it works from the fixtures.
3. **Studio: real generations** — blocked until the GPU's cooling is fixed (2026-10-07: fans at 0 %).
   Then tune line-art prompts and export three real samples into `art/incoming/`.
4. **Pilot (ST-2 PR 5):** one real item end to end (generate → export → import → widget at 48 px). The user merges
   this first art PR (D-50 amended). Only after it works: more categories and content waves (AP-15).

Until step 4 works, neither side adds features the other side hasn't asked for.

## Audit loop

After each merged PR in either repo, Claude (when asked) checks it against the spec, the task doc and
the invariants, and writes findings as a task doc or plan rows. Cursor picks those up like any other
task. Findings that change the contract go through "Changing the contract" above.
