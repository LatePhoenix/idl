# iDL Art Studio: design

**Status:** approved, 2026-10-06 (D-50). Q-S1..Q-S4 are answered in §12. Build order is §10.
**Audience:** the operator (the only user), and whoever builds it (Claude or Cursor).

---

## 1. What it's for

The operator isn't an artist, and the avatar's art and customization will make or break the app.
The Studio is a private, operator-only tool for producing and managing every piece of avatar art
with natural-language prompts:

- Create items: hairstyles, facial hair, eyewear, headwear, jewelry, tops, outerwear, props,
  backgrounds, frames. Prompt: "a slouchy knit beanie with a pom-pom, premium, winter drop."
- Change items: "make the brim wider", "the bun reads as a smudge at 48 px, simplify it",
  "give this jacket a contrasting collar on `outer.secondary`."
- Grow the expression set: fill every missing shape in the expression catalog, tune existing
  ones, and see every expression a shape change affects.
- Manage the catalog and the Charge store: tier, price, collection, release tag, store
  visibility, retirements, and the D-48 free-baseline guard, all in one view.
- Experiment with the base. The teardrop is decided (D-45, D-46), so base changes are
  sandboxed experiments until the operator reopens those decisions.

**Non-goals.** The Studio never ships in the app and never runs on a server. It's not a
user-generated-content system (that's out of scope in `AVATAR_PROGRAM.md` §8). It doesn't
replace the asset pipeline, the tests or code review. It feeds them.

## 2. The core bet: vector-native AI, not image generation

Image generators (raster diffusion models) make attractive pictures, but they're the wrong tool
for this app:

| Need | Raster image generation | Claude writing pipeline SVG |
| --- | --- | --- |
| Fits the slot color system (every fill on a slot, user recolors everything) | No. Pixels have baked-in colors. Vectorizing them gives hundreds of messy shapes | Yes. Every part carries `data-slot` from the start |
| Splits into parts that layer (hair top/side/back, brim masks, mouth holes) | No | Yes. Parts, bands, tags and masks are written on purpose |
| Lines up with the one canonical head (eye line, hairline, chin) | Approximately, and differently each time | Exactly, using the guides in §5 |
| Consistent style across hundreds of items | Drifts | Enforced by the kit, the lint and the style guide |
| Readable at 48 px | Often not | Measured on every draft (§5.5) |
| Small, editable, diffable | No | Yes. The SVG is the source of truth and lives in git |

So the Studio's AI **writes and edits SVG in the pipeline subset** (`docs/avatar/AUTHORING.md`)
and **looks at its own renders** to judge them. Raster generation is optional and only for
inspiration (§6.8). It never produces shipped art.

**Honest expectation.** An LLM drawing Bézier curves freehand gets mediocre results. What
makes it good is the scaffolding in §5: category kits with fixed anchors, geometry helpers that
do the hard math (offset the head outline, mirror, fit to the jaw), several variants per prompt,
a render-and-critique loop against the style guide, and the operator choosing. Expect simple,
clean, consistent emoji-style art, which is exactly the style guide's target. If the operator
later wants a signature look for a few flagship premium items, a human illustrator can draw them
into the same kit, and the Studio keeps everything around them consistent.

## 3. Shape of the tool

One engine, three front ends. Every front end calls the same commands, so anything the AI can do,
the operator can do by hand, and the reverse.

```text
             ┌──────────────── front ends ────────────────┐
  Claude Code (skill)     Studio web UI (localhost)     Studio chat panel (later, Claude API)
         │                         │                                  │
         └────────────┬────────────┴──────────────────────────────────┘
                      ▼
        tools/studio/  ── engine (Python CLI + local HTTP API) ──
          kits, geometry helpers, lint, render, compare, catalog, drafts, git
                      │                         │
                      ▼                         ▼
    tools/asset_pipeline.py              :app studioResolve (JVM, test classpath)
    (SVG → picture JSON, validate)       (real AvatarResolver → resolved draw list)
                      │
                      ▼
    art/<pack>/*.svg, packs/<pack>/vN/manifest.json, config/expression_catalog.json
    → branch art/<slug> → scripts/check → PR with contact sheets
```

### 3.1 Engine: `tools/studio/`

A Python package with a CLI (`python tools/studio/studio.py <command>`) and a small local HTTP
API that the web UI uses. It extends `tools/asset_pipeline.py` and doesn't fork it. Commands
(the full list is the tool surface in §4.2):

- **Read:** `list`, `show <assetId>`, `catalog`, `expressions`, `guides <category>`.
- **Draft:** `draft new <category>`, `draft edit <assetId>`, `draft write <draftId> <svg>`,
  `draft variants <draftId> <n>`. Drafts live in `.studio/drafts/` (gitignored) until promoted.
- **Check:** `lint <draftId>` (style guide rules as code, §5.4), `validate` (the pipeline
  validator), `legibility <draftId>` (§5.5).
- **See:** `render <draftId> --sizes 48,96,512 --framing head,bust --with hair_bob,cap_basic
  --theme light,dark` → PNG sheet. `compare <draftId>` → before/after sheet.
- **Ship:** `promote <draftId>` (writes `art/…svg`, the manifest entry, runs
  `asset_pipeline.py build`), `retire <assetId> --to <replacementId>`, `branch`, `check`,
  `pr`.

### 3.2 Web UI: the operator's eyes

A local page (`http://127.0.0.1:<port>`, never bound to other interfaces). No build step:
plain ES modules served by the engine. It also opens in the Claude desktop app's browser pane,
so the operator can prompt in Claude Code and watch the result appear beside the chat.

| View | What it does |
| --- | --- |
| **Workbench** | The current draft at 48/96/512 px, head and bust framing, light and dark, over a chosen base color. Variant strip (pick one, or "more like this"). Prompt box. Lint and legibility results. Undo history per draft |
| **Try-on matrix** | The draft combined with 3–6 hairstyles × eyewear × headwear × 4 skin tones. Catches clipping and conflicts before the tests do |
| **Catalog** | Every asset: thumbnail, category, tier, price, collection, release tag, store visibility, license, labels, status (draft, in PR, shipped, retired). Sort and filter. Bulk edits ("price all winter-drop headwear at 40") |
| **Expressions** | The 110-expression catalog as a grid of rendered faces. Shapes that don't exist yet are marked. Click a shape to see every expression that uses it, edit it once, and watch them all update |
| **Palettes** | AP-9 swatches per slot family, contrast results against outline and both wallpapers |
| **Base lab** | Sandboxed base experiments (§6.6) |
| **Store planner** | Collections and drops: what's in each, price spread, the D-48 guard status, what a free user gets |

### 3.3 Claude Code front end (first)

A project skill, `.claude/skills/art-studio/SKILL.md`, teaches Claude Code the engine commands,
the loop in §4 and the art rules. The operator types a prompt in Claude Code ("make three
beanie variants"). Claude runs the commands, looks at the renders (the Read tool shows images;
the browser pane shows the live Workbench), iterates, and reports back. Cost: the operator's
existing Claude plan, no API key.

### 3.4 Chat panel front end (later, optional)

A prompt box inside the Studio that calls the Claude API directly, with the §4.2 commands as
tools. This is nicer when the operator just wants to click around, but it needs an API key
(kept in an environment variable or the OS keychain, never in the repo) and costs per use.
Build it only if the Claude Code front end turns out to be clumsy (Q-S1).

## 4. How a prompt becomes an item

### 4.1 The loop

```text
prompt ─► 1 interpret ─► 2 plan ─► 3 draft ×N ─► 4 lint + validate ─► 5 render ─► 6 self-critique
                                        ▲                                              │
                                        └──────────── revise (max 3 rounds) ◄──────────┘
                                                                                       │
                     7 operator picks / refines in words ◄─────────────────────────────┘
                                        │
                                        ▼
            8 metadata (label, a11y, tier, price, collection, release tag, conflicts)
                                        │
                                        ▼
            9 promote ─► pipeline build ─► targeted tests ─► branch + PR with contact sheets
```

1. **Interpret.** Category, which slots, premium or free, release tag. Ask only if the category
   is ambiguous ("a bandana": headwear or face accessory?).
2. **Plan.** Pick the category kit (§5.1). Decide parts, bands, tags, masks to publish or
   subscribe to, and which slots. Write the plan in the draft's notes so a later edit knows the
   intent.
3. **Draft.** Write SVG against the kit's guides, using the geometry helpers (§5.2) for anything
   that must hug the head. Default N = 3 variants that differ in a real way (silhouette, not just
   color).
4. **Lint and validate.** The pipeline validator plus the style lint. Errors go back to step 3
   automatically.
5. **Render.** Head and bust, 48/96/512, light and dark, plus the try-on set for the category.
6. **Self-critique.** Claude looks at the renders against a fixed checklist (style guide §7 plus
   the category rules) and scores each variant. It revises up to 3 rounds and drops variants
   that still fail.
7. **Operator review.** The operator sees the surviving variants side by side and picks one or
   asks for changes in words. Every refinement is a new draft revision, so "go back two" works.
8. **Metadata.** Claude proposes the label, accessibility label, tier, price (from the price
   bands in §6.7), collection, release tag and any conflicts. The operator confirms.
9. **Promote.** Write `art/<pack>/<id>.svg` and the manifest entry, build, run the pack,
   legibility and combination tests for that category, record contact sheets, commit on
   `art/<slug>`, and open a PR (§8).

### 4.2 Tool surface (what the AI may call)

`list_assets`, `read_asset`, `get_guides(category)`, `get_style_rules(category)`,
`new_draft`, `write_draft_svg`, `edit_draft_parts` (replace one part by name),
`apply_geometry(op, args)`, `lint`, `validate`, `render(views)`, `legibility`,
`try_on(set)`, `set_metadata`, `list_expressions`, `edit_shape`, `promote`, `retire`.

Deliberately **not** in the surface: writing outside `art/`, the pack manifests,
`config/expression_catalog.json` and `.studio/`; running git commands other than the
Studio's own branch/commit/PR flow; touching app Kotlin. Engine changes are normal
development work, done in their own PRs.

## 5. What makes the AI's art good

### 5.1 Category kits

One kit per category in `tools/studio/kits/<category>.json`. A kit is data:

- **Guides:** named geometry from the canonical teardrop (`config/teardrop_silhouette.json`)
  and the style guide §2: crown point, hairline curve, temple points, ear points, eye line,
  eye centres, brow band, mouth box, jaw curve, chin, collar line, shoulder curves. The AI
  places shapes relative to guides ("brim sits on `hairline` offset −20"), not in raw numbers.
- **Skeleton:** the parts the category needs, with band, slot, tag and mask defaults. A hat
  kit has `crown`, `brim` (publishes `occlude.hair_top`), optional `band`, `shadow`,
  `highlight`. A hair kit has `hair_back` (z 20), `hair_side` and `hair_top` (z 70),
  `highlight`.
- **Rules:** the category's style rules as checks (§5.4).
- **Exemplars:** 2–4 good shipped items in that category. They're shown to the AI as reference
  every time, which keeps the style consistent as the library grows. The operator promotes a
  favorite to exemplar with one click.

### 5.2 Geometry helpers

Deterministic operations so the AI never hand-computes curves that must fit the head:

- `offset(path, d)`: grow or shrink an outline (hair cap = head crown offset +24).
- `follow(guide, from, to, thickness)`: a band along a guide (headband, beard along the jaw,
  collar along the shoulders).
- `mirror(part, axis=512)`: symmetric items are drawn once.
- `clip(part, guide|mask, mode)`, `union`, `subtract`: path booleans.
- `strands(region, count, curl, seed)`: hair strands and texture inside a region. Curl patterns
  (straight, wavy, curly, coily) are parameters, so one hairstyle comes in every texture (D-48).
- `smooth(path)`, `simplify(path, tolerance)`: clean shapes and drop detail below 32 units.
- `fit(part, box)`: scale and place within a guide box.

Path booleans and smoothing need a geometry library (Q-S3).

### 5.3 Parametric families

Where a family exists, the AI writes **parameters**, not paths. `art/<pack>/generators/*.py`
(already allowed by `AVATAR_PROGRAM.md` §3.9) produce the SVGs. Examples: hair (length, volume,
part side, bangs, texture), glasses (lens shape, frame weight, bridge), hats (crown height, brim
width, slouch), tops (neckline, sleeve, collar). "Make it longer" becomes a parameter change and
stays consistent. Parametric families are also how the catalog grows fast: one good hair
generator × 4 textures × 3 lengths is a lot of free baseline.

### 5.4 Style lint (the style guide as code)

Every rule in `ART_STYLE_GUIDE.md` that can be measured is checked on every draft: slots only,
stroke widths 40–64 for features and 24–32 for outlines, minimum detail 32 units, hairline at or
above y 330, bangs at least 20 above the eyes, headwear above y 330, lens opacity limits, hair
has a visible highlight, the beard keeps its mouth hole, `hair_back` outside the head silhouette,
nothing outside its band region, no text-like shapes. Errors block promotion. Warnings show in the
Workbench.

### 5.5 Legibility metric

The same measure as `LegibilityTest` (AP-8): over the neutral face at 48 px, the fraction of eye
and mouth pixels that stay visible, compared with the bare face. The Studio shows it live and
blocks promotion below the AP-8 thresholds, so a draft never reaches a PR that the test will
fail.

### 5.6 Rendering parity

Three tiers, cheapest first:

1. **Live preview (browser).** The UI draws the SVG sources directly, composited by band, with
   slot colors filled in. Instant, and good enough for the creative loop.
2. **Resolved preview (real Kotlin).** For try-on and expressions, `studioResolve` runs the
   real `AvatarResolver`, `CompatibilityEngine` and mask logic on the JVM (the domain is pure
   Kotlin) and returns the resolved draw list as JSON, which the browser draws. This keeps
   occlusion, conflicts and expression rules from drifting between the Studio and the app.
   It's a `JavaExec` task on the unit-test classpath, so no module split is needed.
3. **Truth (Roborazzi).** The contact sheets that `PackContactSheetTest` records go into the PR.
   A Studio parity test renders a fixed set with tiers 1–2 and compares them with the goldens
   within a tolerance, so a gap is caught.

## 6. Capabilities by area

### 6.1 New accessories and clothing

The §4 loop. Every item ends with a try-on matrix, metadata, and a PR.

### 6.2 Hair

Hair is the most-used category and the hardest to draw, so it gets the most scaffolding:
the hair kit's three-part split, the `strands` helper, parametric hair generators, and a texture
matrix (every style × straight/wavy/curly/coily, D-48). Try-on always includes headwear with a
brim (mask), glasses (arms under side hair) and long hair over the bust framing.

### 6.3 Expressions

- The **Expressions** grid renders all 110 catalog expressions. Missing shapes are a to-do list
  (it's AP-13's work list).
- "Make the happy eyes squintier" edits the shape `eyes_round_happy` and re-renders every
  expression that uses it, side by side before and after, so the blast radius is visible.
- New expressions: "add a 'determined' face" proposes catalog parts from existing shapes first,
  and only drafts a new shape when none fits. Mood mapping stays the operator's call, and the
  privacy rule (mood-derived parts only for viewers who may see mood) is enforced by the app,
  not the Studio.
- Overlays (tears, sweat, hearts, hands) get their own kit on bands 50/110.

### 6.4 Editing shipped items

`draft edit <assetId>` loads the source. On promote, the Studio bumps `contentVersion` on the
SVG and the manifest together (picture caches key on it), and re-records the contact sheets.
Saved avatars keep working because the id doesn't change.

### 6.5 Replacing and retiring

`retire <old> --to <new>` writes the `retired` mapping (invariant 5), keeps the old picture
until a pack version bump, and adds a migration test case. The Studio refuses a removal
without a mapping.

### 6.6 Base lab (guarded)

The teardrop head is decided (D-45, D-46) and many items are fitted to it. The Base lab lets the
operator try a different head shape or proportions in a **sandbox pack** and see every existing
item and expression re-rendered over it, with a list of items whose guides no longer fit.
Nothing from the Base lab reaches the real pack unless the operator reopens D-45/D-46 and the
program adds a migration item. Small tunes that keep the decision (a slightly fuller chin, the
shading on `face.shadow`) go through the normal loop on `base_teardrop`.

### 6.7 Charge store management

- **Catalog fields** come from AP-10 (`price`, `collection`, `releaseTag`, `storeVisible`,
  `tier`). The Studio edits them in bulk and shows the D-48 guard live: a change that would drop
  the free baseline below its minimums is blocked before the tests see it.
- **Price bands** (Q-S5): a small table per category (for example common, rare, signature) so
  the AI proposes consistent prices.
- **Drops:** a collection plus a release tag. The planner shows a drop's items, price spread and
  try-on sheet. Releasing a drop means flipping `storeVisible` in a PR.
- Shipping still means an app update until remote pack delivery exists (out of scope in
  `AVATAR_PROGRAM.md` §8). The Studio's outputs are already the files a remote pack would serve,
  so adding delivery later doesn't change the Studio.

### 6.8 Inspiration (optional)

The operator can attach a reference image (a sketch, a photo of a hat) to a prompt. Claude
reads it as a reference and draws an original item in the kit. Raster image generation for
mood boards is possible later through an image API, but it isn't needed and never produces
shipped art.

## 7. Content and IP rules for the AI

The kit prompts carry `ART_STYLE_GUIDE.md` §3 and §6: no text, logos, brands or trademarks; no
copying of recognizable characters or other apps' avatar items (references inspire, they're not
traced); no smoking, vaping, alcohol, drug or weapon props; cultural and religious headwear only
when the operator asks, free, and queued for the operator's review; no ethnic stereotyping, and
variation never changes the face shape.

## 8. How the work lands in the repo

- Drafts stay in `.studio/` (gitignored) and cost nothing until promoted.
- Promotion writes on a branch `art/<slug>` from up-to-date `origin/main`, one commit per item or
  per batch, runs `python tools/asset_pipeline.py check`, the category's pack, legibility and
  combination tests, and records its contact sheets. Then it opens a PR whose body has the
  contact sheets, the try-on matrix and the metadata table. It adds rows to the master-plan
  review queue (§4.1).
- CI and the runbook rules apply as usual. The operator merges art PRs, or allows the Studio to
  merge green art-only PRs under D-49's terms (Q-S6).
- A Studio PR touches only `art/`, the pack manifest, picture JSON, the expression catalog and
  snapshot goldens. If an item needs an engine change (a new band, a new mask type), the Studio
  stops and writes a task for the program instead.

## 9. Engine changes the app needs

Small, and mostly already planned:

| Change | Where | Status |
| --- | --- | --- |
| Masks, tags, `clipBy` | AP-8 | in progress |
| OKLCH derivation, links, palettes | AP-9 | planned |
| Store fields, `Entitlements`, D-48 guard | AP-10 | planned |
| `studioResolve` JavaExec entry point (recipe JSON in, resolved draw list JSON out) | new, test source set | Studio S2 |
| Legibility measure callable from outside the test | small refactor of AP-8's helper | Studio S2 |

## 10. Phases

| # | Phase | Delivers | Acceptance | Status |
| --- | --- | --- | --- | --- |
| S0 | Viewer | Engine skeleton, `list/show`, Catalog, Workbench and Expressions views (read-only), browser preview | Every shipped asset renders in the Studio at 48/96/512 and matches its contact sheet by eye | ✅ `tools/studio/` |
| S1 | Kits and drafts | Kits for hair, headwear, eyewear, facial hair; guides; geometry helpers; drafts and revisions; style lint; `render` to PNG from the CLI | Claude Code, via the skill, makes a beanie and a hairstyle that pass lint and validate, from one prompt each | ⬜ |
| S2 | Real resolve and legibility | `studioResolve`, try-on matrix, legibility metric, parity test | Try-on matches the app's occlusion; a draft that fails `LegibilityTest` is blocked in the Studio first | ⬜ |
| S3 | Promote and PR | `promote`, `retire`, branch, check, PR with sheets, review-queue rows | One prompt → a green PR that adds an item, with no hand edits | ⬜ |
| S4 | Expressions and families | Expressions grid, shape blast radius, parametric hair/glasses/hat generators, texture matrix | All missing catalog shapes drafted; one hair style shipped in 4 textures | ⬜ |
| S5 | Store | Bulk metadata, price bands, drops planner, live D-48 guard | A drop of 10 premium items planned and opened as one PR | ⬜ |
| S6 | Chat panel (optional) | Claude API prompt box in the UI | Only if Q-S1 says so | ⬜ |

S0–S3 is the useful minimum. Each phase is a PR.

S0 notes: the read-only Expressions grid landed early because it was cheap; S4 adds editing
and the blast-radius view. `render` to PNG moved to S1, because it needs a rasterizer (Q-S3).
Until then, Claude sees renders through the Studio page in the browser pane.

## 11. Timing with the avatar program

- **Don't wait for the whole program, and don't collide with it.** S0 and S1 live entirely in
  `tools/studio/`, `.claude/skills/` and this doc. They touch no app code and no files Cursor is
  editing, so they can start now.
- **S2–S3 need AP-8 (masks), AP-9 (slots and links) and AP-10 (store fields) merged**, so the
  Studio writes the final formats once. At the current pace that's the next few PRs.
- **Change to the program (D-50):** do AP-13 (expression art batch
  2) and AP-15 (content waves) **with the Studio** instead of having Cursor hand-write path
  data. Cursor continues AP-8 → AP-12 and AP-16 unchanged. That's where the Studio pays for
  itself: AP-15 alone is about 140 items.
- **Audit first.** Before S2 starts, audit the merged AP-8..AP-10 work (the usual audit
  pass), because the Studio builds on those formats.

## 12. Questions

Q-S1..Q-S4 were answered by the user on 2026-10-06 (D-50): every recommendation was accepted.
Q-S5 waits for S5. Q-S6 uses the recommendation as its default.

| # | Question | Answer or recommendation |
| --- | --- | --- |
| Q-S1 | AI front end: Claude Code skill only, or also an in-Studio chat panel using the Claude API (needs an API key, billed per use)? | **Decided:** Claude Code skill first (S0–S5). Add the panel only if it feels clumsy |
| Q-S2 | Who builds the Studio: Claude (this tool is design-heavy and art-judgment-heavy) or Cursor from a spec? | **Decided:** Claude builds S0–S3; Cursor can take S4–S5 from specs |
| Q-S3 | Tool-only Python dependencies (path booleans and smoothing, e.g. `skia-pathops`; a rasterizer for server-side renders, e.g. `resvg`) in an isolated `tools/studio/requirements.txt`. The app and `asset_pipeline.py` stay dependency-free | **Decided:** approved, tool-only, each one listed in `requirements.txt` |
| Q-S4 | Move AP-13 and AP-15 onto the Studio (§11)? | **Decided:** yes. They're 🎨 rows in the status table |
| Q-S5 | Price bands per category and rarity | Set at S5, after C.3 pricing exists |
| Q-S6 | May the Studio merge its own green, art-only PRs (like D-49), or does the operator merge every art PR? | **Default:** the operator merges art PRs until about 20 Studio items have shipped, then revisit |
