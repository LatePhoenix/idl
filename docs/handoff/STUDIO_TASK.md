# Cursor task: Art Studio S1–S3 and the art-direction rows

**Written by:** Claude, 2026-10-07 · **Design:** [`docs/avatar/ART_STUDIO.md`](../avatar/ART_STUDIO.md) (D-50)
**Process:** [`CURSOR_RUNBOOK.md`](CURSOR_RUNBOOK.md) applies unchanged (branches, checks, PRs,
merging your own green PRs, stop conditions). Claude audits when you're done.

The Art Studio is a private, operator-only tool for making and managing avatar art. It's never
shipped and never runs on a server. The user decided on 2026-10-07 that **Cursor builds S1–S3**
(this amends D-50, which said Claude would). S0, the read-only viewer, is on `main` (PRs #45, #47). The status table in
`AVATAR_PROGRAM.md` §5 orders these rows among the program's (ST-1, AD-1, AP-10, ST-2, ST-3, AP-11, AP-12, AD-2, AD-3, …).
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

> **Re-scoped by D-51 (2026-10-07):** ST-2 is now the art importer and review sheets in
> `docs/avatar/ART_INTERCHANGE.md` §9 — the importer is specified in `ART_IMPORT_TASK.md`. Keep the legibility parity fix (F-33) below; the resolver and
> try-on parts apply only where the review sheets need them.

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

> **Re-scoped by D-51 (2026-10-07):** promote is the importer (ST-2). ST-3 keeps retire only.

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
4. Tooling PRs (ST-1..ST-3) follow D-49 as usual. Art PRs: the user merges the first one after AD-2 (the
   pilot). After that, Cursor merges its own green art PRs (D-50 as amended 2026-10-07).

**Acceptance:** one command takes an accepted draft to a green PR with no hand edits. Show it with the
ST-1 beanie (premium, collection `winter`), then close that PR unmerged. It's a demonstration in the
current style, and AD-2 decides the style.

## AD-1 · Style exploration sheets (after ST-1)

Branch `art/ad-1-style-exploration`. The user is unhappy with the avatar's look (Q17) and wants to
compare directions before more art is made. Give them pictures, not descriptions.

1. Use ST-1's renderer and geometry helpers. Work in a **sandbox**: `tools/studio/explore/<direction>/*.svg`.
   These files are never in a pack and the app never loads them. Don't touch `art/` or any manifest.
2. Make **at least six directions**, each a coherent set drawn in that style:
   - neutral, happy, sad and surprised expressions
   - three hairstyles: short, long and curly
   - a beanie, round glasses and a tee

   The directions:
   - **A Current:** the shipped art, as the baseline.
   - **B Bold and flat:** 40–48 unit outlines on every item, flat fills, no face gradient.
   - **C Big-eye soft:** larger eyes, a lower eye line, a smaller mouth, softer shapes.
   - **D Cel-shaded:** two-tone shading on the head and hair, a rim light.
   - **E Sticker:** a thick light sticker border around the whole silhouette plus a dark outline, so it reads on any wallpaper.
   - **F Your proposal:** explain why.

   **Hair is the user's biggest complaint:** in every direction, the hair must read as hair at 48 px
   (volume, a hairline, a part, strands), not as flat blobs.
3. **Head shape:** directions A–F keep the teardrop silhouette (D-45, D-46). Add one extra set **G** with a
   different head (for example a rounder teardrop or a circle), clearly labelled "reopens D-45/D-46".
4. For each direction, render one Studio sheet: head framing at 48 px (4×) and 96 px, bust at 512, on light
   and dark wallpapers. Also make one `overview.png` with every direction side by side at 96 px and 48 px.
5. Commit the sheets and sources to `docs/art/exploration/v3/` with a `README.md`: one paragraph per direction
   with its strengths and weaknesses at 48 px, and your recommendation. Add a row to the master-plan review
   queue, and put options A–G into Q17. Docs only, so merge it under D-49 and **continue with the next row**.
   Don't wait for the user's answer.

## AD-2 · The user picks the art direction

Answered 2026-10-07. The user delegated the pick and asked to treat shipped art as a placeholder.
Recorded as D-53: **F, with E's light border on widget framing only**, and Q16 **(a)** brows over
front hair. The head stays the teardrop. AD-3 applies it after the editor and the widgets.

## AD-3 · Apply the art direction

Branch `art/ad-3-<direction>`. Once Q17 is answered:

1. Record it as a new `D-` entry in `docs/IDL_DECISIONS.md` and update `ART_STYLE_GUIDE.md` to match:
   line weights, shading, proportions and the hair rules. If the user picked a new head (G), that reopens
   D-45 and D-46; record the user's words.
2. Update the kits (`tools/studio/kits/*.json`) and the lint numbers to the new guide.
3. Restyle every shipped picture in the new direction with the Studio. **Keep every asset id**, bump
   `contentVersion`, and re-record the goldens so saved avatars never break (invariant 5). A head change
   also needs the guides re-derived and every fitted item checked by lint and try-on.
4. This is the **pilot art PR**: the user merges it (D-50 as amended). Then AP-13 and AP-15 follow, made with
   the Studio, and Cursor merges those art PRs itself.

## Out of scope here

S4 (expression editing, parametric families), S5 (store tools) and S6 (chat panel) wait until the program
is done or the user asks.
