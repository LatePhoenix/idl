# Cursor task: Art Studio S1–S3

**Written by:** Claude, 2026-10-07 · **Design:** [`docs/avatar/ART_STUDIO.md`](../avatar/ART_STUDIO.md) (D-50)
**Process:** [`CURSOR_RUNBOOK.md`](CURSOR_RUNBOOK.md) applies unchanged (branches, checks, PRs,
merging your own green PRs, stop conditions). Claude audits when you're done.

The Art Studio is a private, operator-only tool for making and managing avatar art. It's never
shipped and never runs on a server. The user decided on 2026-10-07 that **Cursor builds S1–S3**
(this amends D-50, which said Claude would). S0, the read-only viewer, is on `main` (PRs #45, #47).
AP-9 then extended its renderer with OKLCH colors and slot links.

Read first: `ART_STUDIO.md` §2–§5 and §10, `docs/avatar/ART_STYLE_GUIDE.md`, `docs/avatar/AUTHORING.md`,
`tools/studio/README.md`, and the paused-work report
[`reports/2026-10-07-studio-s1-paused.md`](reports/2026-10-07-studio-s1-paused.md).

## Starting point

Branch **`tools/studio-s1`** (pushed) is `main` plus Claude's unfinished S1 engine. Start S1 from
it: `git fetch && git checkout -b tools/studio-s1-cursor origin/tools/studio-s1 && git merge origin/main`.

| File | State |
| --- | --- |
| `tools/studio/engine/paths.py` | Written, lightly run. Skia path parse/serialize, union/subtract/intersect, offset, thicken, mirror, smooth curves, area, thin-detail |
| `tools/studio/engine/guides.py` | Written and run. Head guides derived from the shipped art |
| `tools/studio/engine/render.py` | Written and run. Skia renderer and labelled sheets. **Its color code predates AP-9**: port `resolveColors` (OKLCH derivation, slot links) from `tools/studio/web/render.js` so the Python and browser renderers agree |
| `tools/studio/engine/compose.py` | Written, run once. Base + expression + items, drafts layered over shipped ids |
| `tools/studio/engine/kits.py`, `kits/*.json` | Written, not run. Kits for hair, headwear, eyewear, facial hair |
| `tools/studio/engine/drafts.py` | Written, not run. `.studio/drafts/<id>/meta.json` + `rev-NNN.svg` |
| `tools/studio/engine/legibility.py`, `lint.py` | Written, not run |
| `tools/studio/requirements.txt` | `skia-python`, `numpy`. Tool-only, approved under D-50. The venv is `tools/studio/.venv` (gitignored) |

Treat all of it as a draft: read it, test it, fix it, and change it where you find a better way.

## Rules for the Studio

- S0 commands (`serve`, `list`, `show`) stay standard-library only. Commands that need Skia
  re-run themselves under `tools/studio/.venv` when it exists, or say how to create it
  (`python tools/studio/studio.py setup` creates the venv and installs `requirements.txt`).
- The app, `tools/asset_pipeline.py` and CI gain **no** dependencies. CI doesn't need to run Studio
  tests that need Skia. Standard-library Studio tests may run in `scripts/check.sh`/`check.ps1`.
- Bind the server to 127.0.0.1 only. No network calls. No API keys anywhere.
- The Studio writes only to `.studio/` (gitignored) until S3's `promote`. After that, it writes only to
  `art/`, the pack manifest, picture JSON, `config/expression_catalog.json` and snapshot goldens.
- Python: standard formatting, type hints, short docstrings. Match `tools/asset_pipeline.py`.
- Every Studio PR includes a screenshot or sheet of what it changes (from the Studio itself is fine).

## ST-1 · S1: kits, drafts, lint, PNG renders, agent guide

Branch `tools/studio-s1-cursor`.

1. **CLI** in `tools/studio/studio.py`:
   - `setup`
   - `guides [--json]`
   - `kit <category> [--skeleton <id>]`
   - `draft new <id> --category <c> [--prompt] [--group] [--label] [--from <assetId>]`
   - `draft write <id> <file|-> [--note]`
   - `draft list|show <id>|revert <id> <n>|set <id> key=value…`
   - `lint <id> [--json]`
   - `render <id|assetId> [--with a,b] [--expression e] [--out f.png]`
   - `geom <op> …`: ops from `paths.py`. Arguments accept `@guide:<name>`, `@part:<draftId>/<part>` and `@file:<path>`, so long paths don't go through the shell.

   `draft write` compiles, lints and renders automatically, then prints a summary and the PNG path.
2. **Standard render sheet** (`render`), one PNG:
   - head framing, light and dark, at 48 px (zoomed 4× nearest-neighbour), 96 and 192
   - bust framing at 256
   - the kit's try-on sets at 128
   - two skin tones (`face.primary` overrides)
   - the expressions neutral, star-struck, crying and sleeping at 96

   Save to `.studio/renders/<id>-r<NNN>.png`.
3. **Drafts view in the web UI.** List drafts by group. Show the current revision with the
   browser renderer, the lint results and the revision history (with revert). Add a guides overlay
   toggle (`guides.overlay_svg()`). Poll for changes every 1–2 s so edits from the CLI appear live.
   The server adds `/api/drafts`, `/api/drafts/<id>` and `/api/guides`.
4. **Agent guide** `docs/avatar/STUDIO_AGENT_GUIDE.md`: the prompt-to-item loop from `ART_STUDIO.md` §4
   as concrete commands, the kit/guide/lint vocabulary, and the content rules (style guide §6, no
   brands, logos or text). Also add two thin pointers to it:
   - a Claude Code skill, `.claude/skills/art-studio/SKILL.md`, with frontmatter `name` and a `description` that says when to use it
   - a Cursor rule, `.cursor/rules/art-studio.mdc`, applied when working under `tools/studio/` or `art/`
5. **Tests** (`tools/studio/tests/`):
   - paths: union, subtract, offset and mirror on known shapes; `to_d(parse(d))` round-trips
   - guides: values match the teardrop
   - drafts: create, write, revert
   - lint: one passing fixture and one failing fixture per rule
   - legibility: a bare item scores 1.0; a shape over the eyes scores below 0.6
   - render: the sheet has the expected size, and a 48 px render of `hair_bob` matches a stored PNG within a tolerance

   Skip the Skia tests cleanly when Skia isn't installed.

**Acceptance:**
- An agent following the guide turns each of two prompts into a draft that passes lint with no
  errors, with no hand-written coordinates outside the guide or geometry helpers:
  - "a slouchy knit beanie with a pom-pom"
  - "a short curly hairstyle"
- Put both sheets in the PR, and add both to the master-plan review queue for the user.
- The Python render of `hair_bob` at 512 matches `app/src/test/snapshots/packs/emoji_core/hair_512.png` by eye.

## ST-2 · S2: real resolver, try-on, legibility parity

Branch `tools/studio-s2`.

1. A `studioResolve` Gradle `JavaExec` task on the **unit-test** runtime classpath. Recipe JSON goes
   in; the resolved draw list comes out as JSON: layers, parts, colors, masks, suppressed props.
   It runs the real `AvatarResolver`, `CompatibilityEngine`, `PublishedMasks` and `ColorSlots`.
   Put its `main` in `app/src/test/java/app/idl/studio/`. Nothing goes in `src/main`.
2. A **try-on matrix** view and CLI: draft × 3–6 items × 4 skin tones, drawn from the resolved draw
   list. It shows conflicts the resolver reports.
3. A **parity test**: 20 fixed recipes rendered by the Python renderer and by `PackContactSheetTest`
   style rendering agree within a per-pixel tolerance. Record the tolerance in the report.
4. **F-33**: use the Studio's zone-based measure (or the same idea in Kotlin) for `LegibilityTest`.

**Acceptance:** try-on output equals the app's resolution for the 20 parity recipes. A draft that would
fail the new `LegibilityTest` is blocked by `studio lint` first.

## ST-3 · S3: promote and retire

Branch `tools/studio-s3`.

1. `promote <draftId> [--pack emoji_core]`. It:
   - writes `art/<pack>/<id>.svg`
   - adds or updates the manifest entry: label, a11y label, category, tier, collection, license `proprietary-idl`, `contentVersion`, `colorSlots`, `compatibleBases`, conflicts
   - runs `asset_pipeline.py build` and `check`
   - records the category contact sheets (`PackContactSheetTest`)
   - commits on `art/<slug>` from `origin/main`
   - opens a PR with the sheets and a metadata table, and adds review-queue rows

   Refuse if lint has errors.
2. Editing a shipped item bumps `contentVersion` in the SVG and manifest together.
3. `retire <old> --to <new>` writes the `retired` mapping and a migration test case. It refuses a removal
   without a mapping.
4. **Art PRs are merged by the user, not by Cursor**, until about 20 Studio items have shipped (D-50).
   Tooling PRs (ST-1..ST-3 themselves) follow D-49 as usual.

**Acceptance:** one command takes an accepted draft to a green PR with no hand edits. Show it with the
ST-1 beanie (premium, collection `winter`) and leave that PR for the user.

## Out of scope here

S4 (expression editing, parametric families), S5 (store tools) and S6 (chat panel). AP-13 and AP-15 stay
🎨 and wait for the user's art direction.
