# Avatar program: creation and customization

**Status:** plan of record, 2026-10-06 (Claude, for the user). Cursor implements it end to end
(D-49), following [`docs/handoff/CURSOR_RUNBOOK.md`](../handoff/CURSOR_RUNBOOK.md).
**Replaces** the AV table in `master-plan.md` §4.1 as the avatar to-do list. The master plan
still holds project status, findings and the work log.

---

## 0. How to use this document

1. The **status table in §5** is the single list of work. Take the first row whose status is ⬜
   and whose dependencies are all ✅. Work rows strictly in order unless the table says a row
   may run in parallel.
2. Each row links to a spec in §6. The spec is binding. Anything it doesn't decide, decide
   yourself within §2 and §3, and record the choice in the PR description and the checkpoint
   report under "Deviations and choices". If a choice would contradict a decision (`D-xx`) or an
   invariant, **stop** (runbook §7).
3. Rows marked **design note first** need a short design note committed as the first commit of
   the branch: `docs/handoff/AP-<n>-<slug>.md`, using the template in §6.0. Then implement it.
4. When a row merges, set it to ✅ in §5 in the same PR, and add the work-log line in
   `master-plan.md` §8.

Read these before the first item, and again whenever a spec cites them:
`AGENTS.md`, `master-plan.md` §0–§1, `docs/IDL_DECISIONS.md` (at least D-24–D-30b and D-35–D-49),
`docs/ARCHITECTURE.md`, `docs/ASSET_SPEC.md`, `docs/AVATAR_RECIPE_SCHEMA.md`,
[`ART_STYLE_GUIDE.md`](ART_STYLE_GUIDE.md).

---

## 1. Vision (the user's words, made concrete)

> "The base shape for the avatar is limited to a shape that matches the face of the app icon.
> Users can change colors, hair styles, hair colors (primary color plus a secondary highlight
> color), facial hair styles and colors, accessories like glasses, and clothes like a shirt worn
> under the avatar head. Ideally the avatars can make ALL possible emoji expressions. The more
> accessories and customizations we can make easily usable, the better. Most items in the future
> Charge store will be used with the avatar, so it needs to be robust."

When the program is done, a user can:

1. Create an avatar in under two minutes (quick creator), or fine-tune every part (full editor).
2. Choose skin color, hairstyle, hair color plus an independent highlight color, facial hair and
   its color, eye color, eyewear, headwear, jewelry, a top, optional outerwear, a background and a
   frame, each with its own colors.
3. See their avatar as a head-and-shoulders bust in the app, and as a large, readable head with
   a collar on the 48 px widget.
4. Show **every** face emoji in the scope file as an expression, either as the automatic face
   for a mood or picked directly as part of a status (shown only to friends who may see mood).
5. Preview premium items (try-on) before the store exists. Saving one needs an entitlement.

And a developer (Cursor) can add a new item by adding one SVG source file and one manifest
entry, with no Kotlin change. The pipeline validates it, generates the picture JSON, thumbnail
and contact sheet, and the test suite catches anything that breaks legibility, layering or
privacy.

---

## 2. Binding decisions and invariants

- **One base (D-45, D-46).** `base_teardrop` is the only head. Its outline is generated from
  `config/teardrop_silhouette.json`. Every item is drawn for that one shape, so there's no
  per-family fitting.
- **Body and framing (D-47).** The body (neck, shoulders, torso) is identity. Widgets use head
  framing and the app uses bust framing (§3.3).
- **Free baseline (D-48).** Enforced by a pack test (§3.8).
- **Original art only (D-42).** `license: proprietary-idl`. No Noto, Twemoji, OpenMoji or other
  third-party glyphs, and no tracing of them. Use Unicode names and descriptions only as a
  reference for *which* emotion to draw.
- **Vector IR on Canvas (D-39, ADR 0001).** No SVG parsing at runtime and no AndroidSVG. SVG is
  an authoring format only: the pipeline compiles it to picture JSON.
- **Privacy (invariants 1–2, D-24).** Expression is mood data. If a viewer can't see mood,
  nothing derived from mood reaches their render. That covers the expression, its overlays,
  `mood:*` overrides and a status-chosen face. Identity (skin, hair, clothes, accessories)
  renders whenever the avatar is visible.
- **Saved avatars never break (invariant 5).** Every removed or renamed id gets a `retired`
  mapping. No destructive Room migrations.
- **Determinism (invariant 8).** The same recipe and presence give the same layers, render key
  and pixels. Randomize takes a seed.
- **Expressions are never paywalled (invariant 6, §17.3).**
- **Availability is never color-only (D-28),** and presence chrome (frame, availability glyph,
  activity badge, reactions) stays above every character band.
- **Charge (D-30, D-30a, D-30b, D-32).** Cosmetics only. No Charge notifications, no streaks. The
  store spends Charge only after the server ledger exists (C.3, F-04), which isn't in this
  program.

---

## 3. Target architecture

Most of the machinery exists already: recipe schema 3 (`AvatarConfiguration`), `AssetRegistry`,
`CompatibilityEngine`, `AvatarResolver`, `CompositeOrder`, `VectorPicture` with its validator, the
Canvas vector renderer, `ColorSlots`, and render keys and caches. The program extends it.
**Don't fork it.**

### 3.1 Canvas and coordinate spaces

- **Character space** is the existing 1024 grid. The head sits where it does today (crown y 96,
  width 132–892, rounded chin about y 912 after AP-1).
- Parts on body bands (34, 36, 38), rear-hair parts on band 20, and background parts may extend
  into the **body region**: x −256..1280, y −16..1536. The validator allows that only for those
  bands. Every other part keeps the existing −16..1040 limit (`allowOverflow` stays the explicit
  escape hatch).
- Pictures keep `viewBox: 1024`. The body region is overflow geometry, not a new viewBox.

### 3.2 Layer bands

The existing bands stay. Add three body bands between rear jewelry and the head:

| z | Content | Notes |
| --- | --- | --- |
| 0 | Background | Fills the framing viewport (§3.3), not the 1024 square |
| 10 | Rear props | |
| 20 | Rear hair, hood backs | Behind the body, so long hair falls behind the shoulders |
| 30 | Rear jewelry | Earring backs |
| **34** | **Body**: neck, shoulders, torso (skin slots) | New. Part of `base_teardrop` |
| **36** | **Tops** (shirts, tees, sweaters) | New |
| **38** | **Outerwear and collars** (jackets, hoodies, open shirts) | New |
| 40 | Head (face, shade, outline) | The chin covers the top of the neck and collar |
| 50 | Expression: eyes, brows, mouth; face details | |
| 60 | Facial hair | |
| 70 | Front hair, bangs | |
| 80 | Eyewear | |
| 90 | Front jewelry, headwear | Necklaces sit below the chin, so no overlap |
| 100 | Mouth-held props | |
| 110 | Foreground effects, expression hands and overlays | Face-hand emoji hands live here |
| 200+ | Presence chrome | Unchanged, and never on a part |

Update `VectorPictureValidator.CHARACTER_BANDS`, `CompositeOrder` and the band tables in
`docs/ARCHITECTURE.md` §5 and `docs/ASSET_SPEC.md` §1 together (AP-3).

### 3.3 Framing (D-47)

Add a pure-domain `Framing` enum (`HEAD`, `BUST`) and `RenderTarget.framing`. The renderer
maps the framing viewport onto the output square. Background parts fill the viewport.

| Framing | Viewport (character space) | Targets | Effect |
| --- | --- | --- | --- |
| `HEAD` | origin (−40, 0), size 1104 | `COMPACT_WIDGET`, `CIRCLE_WIDGET`, `FRIEND_TILE`, `NOTIFICATION`, `STANDARD_WIDGET`, `LARGE_WIDGET` | Face about 7% smaller than today. Neck and collar visible at the bottom |
| `BUST` | origin (−128, 32), size 1280 | `PROFILE`, `SHARE_CARD`, editor previews, export | Head and shoulders. Face 20% smaller |

These numbers are the starting point. AP-3 may tune each by up to ±5% after reviewing contact
sheets at 48 px and 512 px, and records the final numbers here and in the report.
The framing is part of the render key.

### 3.4 Categories and editor tabs

Keep `AssetCategory` wire names stable. Add only what an editor tab needs.

| Editor tab | Category (wire) | Multiple? | Status |
| --- | --- | --- | --- |
| Skin | (base color slots) | | exists: `face.*` slots on `base_teardrop` |
| Hair | `HAIR` (`hair`) | no | exists |
| Facial hair | `FACIAL_HAIR` (`facial_hair`) | yes (beard plus mustache) | exists. Becomes `multiple` in AP-8 |
| Eyes | (expression eye color slot) | | color only |
| Face details | `SIGNATURE_FEATURE` (`signature_feature`) | yes | freckles, beauty marks, face paint |
| Eyewear | `FACE_ACCESSORY` (`face_accessory`) | no | exists |
| Headwear | `HEAD_ACCESSORY` (`head_accessory`) | no | exists |
| Jewelry | **new** `JEWELRY` (`jewelry`) | yes | earrings, piercings, necklaces |
| Tops | **new** `TOP` (`top`) | no | shirts, tees, sweaters |
| Outerwear | **new** `OUTERWEAR` (`outerwear`) | no | jackets, hoodies, open shirts |
| Props | `FOREGROUND_PROP`, `BODY_ACCESSORY` | | exist (status props stay semantic) |
| Background | `SCENE` (`scene`) | no | exists |
| Frame | `FRAME` (`frame`) | no | exists (chrome band) |

Expressions use the existing `FACE_EYE`, `FACE_BROW`, `FACE_MOUTH` and `EXPRESSION_OVERLAY`
categories plus `ExpressionDef`.

### 3.5 Color slots and links

- **Slots** (dot-separated, `<item>.<role>`): `face.primary|shadow|highlight`, `outline`,
  `eye.primary`, `eye.white`, `mouth`, `brow.primary`, `hair.primary|highlight|shadow`,
  `beard.primary|shadow`, `glasses.frame|lens`, `metal.primary`, `gem.primary`,
  `top.primary|secondary|accent`, `outer.primary|secondary`, `hat.primary|secondary`,
  `accessory.primary|secondary`, `bg.primary|secondary`.
- **Derivation (AP-9).** `shadow` and `highlight` derive from their `primary` in OKLCH
  (shadow: L −0.12, chroma ×1.05; highlight: L +0.10, chroma ×0.9), clamped to sRGB. This
  replaces the per-channel mix and bumps `RENDER_VERSION`.
- **Links (AP-9).** These are data in the pack defaults, not Kotlin branches. Unless the user
  sets the target slot or unlinks it, `brow.primary` and `beard.primary` follow
  `hair.primary`. The user's **hair highlight** is `hair.highlight`: it's linked (derived) by
  default, and choosing a highlight color writes an override and adds the slot to
  `unlinkedSlots`.
- **Palettes (AP-9).** Curated swatches per slot family: skin (at least 16 natural tones from
  very light to very deep, plus 8 fantasy), hair (at least 20 natural and fantasy), eye
  (at least 10), clothing (at least 24). Plus the custom picker. All free (D-48).

### 3.6 Occlusion and layering (AP-8)

Generic data, not `if (assetId == …)` code:

- An asset may **publish masks**: named clip paths in its picture (for example a hat publishes
  `occlude.hair_top`; glasses publish `occlude.temple`).
- A part may **subscribe**: `clipBy: [{ mask: "occlude.hair_top", mode: "difference" }]`. If no
  asset in the draw list publishes that mask, the clip is a no-op.
- Hair pictures split into parts tagged `hair_top`, `hair_side` and `hair_back`, so a brimmed hat
  hides only the top. Glasses arms tuck under side hair. Beards keep their mouth hole (existing
  clip).
- `FaceOcclusion` keeps working: full-face masks and VR headsets hide eyes. Sunglasses *tint*
  (lens opacity at most 0.75), so the expression still reads.
- **Hands.** Expression overlays with hands (face-hand subgroup) take the `prop_hand` anchor.
  While one shows, a foreground prop on that anchor is suppressed for that render. The prop
  returns when the expression ends, and the saved recipe never changes.
- **Compatibility** stays declarative (`conflictsWith`, `requires`, `fallback`). Every new
  conflict must be symmetric (the existing test).

### 3.7 Expressions

- `config/expression_catalog.json` (AP-5) lists every in-scope face emoji with an
  `expressionId`, its parts, its overlays, an optional mood, and a priority.
- An expression is a combination of shared part assets: eye shapes, brow shapes, mouth shapes and
  overlays. Shapes are reused across expressions. The catalog's shape list is the art to-do list.
- Each of the 16 `Mood` values has exactly one priority-1 expression (the automatic face).
- Users can also pick **any** expression as part of a status (AP-14). It's mood data: the server
  sends it only to viewers who may see mood, and it expires with the status.
- Today's `Expression` enum wire names stay as aliases of catalog ids, so saved data never
  changes.

### 3.8 Store readiness (AP-10)

- `AssetDef` gains `price` (Charge, null for free), `collection` (exists), `releaseTag`
  (for example `core`, `2026_winter`) and `storeVisible`. `tier` (`FREE`/`PREMIUM`) exists.
- A domain `Entitlements` interface (`owns(assetId): Boolean`, owned set). There's a local
  implementation for demo mode, with a **debug-only** "Unlock all items" setting. The server
  implementation waits for C.3.
- Rendering never checks entitlements: a friend sees whatever is saved. Saving checks them. The
  editor can preview a locked item (try-on), but `prepareForWrite` refuses to save a recipe
  containing an unowned premium item and names it. `put_avatar` re-checks on the server (AP-7
  adds the hook, which allows free-tier ids only until C.3).
- **D-48 guard test.** Every expression, color, swatch and core semantic prop is `FREE`. There
  are at least 12 free hairstyles covering the textures in D-48, and at least 1 free item in each
  of facial hair, eyewear, top, background and frame. The test fails the build if a change
  breaks the baseline.

### 3.9 Authoring pipeline (AP-4)

```text
art/<packId>/<assetId>.svg        (source of truth: hand-written or script-generated SVG)
art/<packId>/generators/*.py      (optional parametric generators that write the SVGs)
        │  python tools/asset_pipeline.py build <packId>
        ▼
app/src/main/assets/packs/<packId>/v<n>/pictures/<assetId>.json   (picture IR, committed)
        │  ./gradlew testDebugUnitTest --tests '*PackContactSheetTest*'
        ▼
app/src/test/snapshots/packs/<packId>/<category>_{48,96,512}.png  (per-category contact sheets, goldens)
```

- **SVG subset.** `path`, `rect`, `circle`, `ellipse`, `polygon`, `g` with `transform`. Arcs are
  converted to cubics. Each drawable element carries `data-part`, `data-z`, `data-slot` (or a
  `data-gradient` reference), and optionally `data-clip`, `data-clip-mode`, `data-publish-mask`,
  `data-tags` and `data-opacity`. Strokes are allowed (picture format v2, below). Anything else is
  an error: text, images, filters, external references, scripts, CSS beyond `fill`/`stroke`
  attributes.
- **Picture format v2.** Adds optional `stroke: {slot, width, cap, join}` (round caps and joins by
  default; width 16–96), `tags`, and `publishMasks`/`clipBy` (§3.6). Version 1 pictures keep
  loading. The validator enforces v2.
- **`tools/asset_pipeline.py`** uses only the Python standard library (no pip). Commands:
  `build <pack>`, `validate <pack>` (exits nonzero on error), `check` (rebuilds everything to a
  temporary directory and fails if a committed picture differs). CI runs `check`.
- Existing hand-written pictures get SVG sources once (AP-4), so every shipped picture has a
  source.

---

## 4. Testing strategy (applies to every item)

| Layer | What | Where |
| --- | --- | --- |
| Domain unit tests | Resolver, compatibility, slots and links, framing, entitlements, catalog, migrations. Pure JVM | `app/src/test/.../domain/avatar/` |
| Pack tests | Every picture validates. Every manifest entry has an SVG source, a thumbnail, labels, a license and symmetric conflicts. The D-48 baseline holds. Retired ids resolve. `tools/asset_pipeline.py check` is clean | `AssetPackTest`, new `PackIntegrityTest` |
| Combination property test | 2,000 seeded random recipes (fixed seeds), drawing only from compatible items: all resolve with no issues and a stable render key across two runs. All render at 48 px and every tenth at 512 px without throwing. No layer is outside its band. Keep the test under 60 s on CI | new `AvatarCombinationTest` |
| Legibility tests | For every hair, headwear, eyewear and facial-hair item over the neutral face at 48 px (`COMPACT_WIDGET`): enough eye and mouth pixels stay visible (thresholds set in AP-8 from the neutral baseline). Sunglasses use the tinted-lens rule | new `LegibilityTest` |
| Privacy tests | Mood hidden means no mood-derived layer, for every expression in the catalog and every `mood:*` override. Composition vectors where specs say | existing resolver tests plus `contract/` |
| Snapshot goldens | Per-category contact sheets at 48, 96 and 512 px, light and dark, both framings where relevant. Widget goldens. Editor screenshots via device tests | Roborazzi (`scripts/check.sh` verifies) |
| Device tests | Vector slice screen, editor flows, widget render, on `emulator-5554` | `scripts/check.sh --device` |
| Server tests | SQL suite, privacy vectors, PostgREST IT | `scripts/check.sh --sql`, CI `backend` |

Rules:
- Every bug fix gets a regression test (AGENTS.md).
- Never re-record a golden to make a failure go away. Re-record only when pixels are *meant* to
  change, look at every changed image, and list each one with its reason in the PR.
- When the device or SQL suites apply to a change (the runbook says when), run them before
  opening the PR.

---

## 5. Status table

Legend: ⬜ not started · 🔨 in progress (a branch exists) · ✅ merged · ⏸ blocked (see the report) ·
🎨 art made with the Art Studio, following `docs/avatar/STUDIO_AGENT_GUIDE.md` (written in ST-1) ·
👤 the user's row: Cursor never does it.

**Rows are in execution order (re-planned 2026-10-07).** Take the first ⬜ row whose dependencies are ✅.
ST and AD rows are specified in [`docs/handoff/STUDIO_TASK.md`](../handoff/STUDIO_TASK.md). When every remaining
row depends on an unfinished 👤 row, stop (runbook §7) and tell the user exactly what to decide.

**Next (2026-10-07):** AP-11, the editor screen. Art direction is recorded (D-53). The restyle waits until the editor and widgets are in. ST-2 PR 5, the pilot real-art import, waits for a file in `art/incoming/`.

| # | Item | Depends on | Device? | Server? | Status |
| --- | --- | --- | --- | --- | --- |
| AP-1 | Brand silhouette is the avatar head (D-45) | | yes | | ✅ |
| AP-2 | F-31: pack directory per asset | | | | ✅ |
| AP-3 | Body, body bands and framing (D-47) | AP-1 | yes | | ✅ |
| AP-4 | Authoring pipeline and picture format v2 | AP-2, AP-3 | | | ✅ |
| AP-5 | Expression catalog (was AV.3) | | | | ✅ |
| AP-6 | Expression system and art batch 1 (16 mood faces) | AP-4, AP-5 | yes | | ✅ |
| AP-7 | Schema 3 saved everywhere, teardrop-only migration (D-41, D-46; was AV.4). **Design note first** | AP-6 | yes | yes | ✅ |
| AP-8 | Occlusion, layering and legibility engine | AP-4 | | | ✅ |
| AP-9 | Color system: OKLCH, links, palettes | AP-4 | | | ✅ |
| ST-1 | Art Studio S1: kits, drafts, lint, PNG renders, agent guide | | | | ✅ |
| AD-1 | Style exploration sheets for the user (Q17) | ST-1 | | | ✅ |
| AP-10 | Store-ready plumbing and the D-48 guard | AP-7 | | | ✅ |
| ST-2 | Art import (D-51): `tools/import_art.py` for `art/incoming/`, review sheets, legibility parity (F-33). Spec: `ART_INTERCHANGE.md` §9. Task: `docs/handoff/ART_IMPORT_TASK.md`. Pilot art (PR 5) waits for a real studio export | ST-1 | | | ✅ |
| ST-3 | Art Studio S3: retire (promote is the importer, D-51) | ST-2 | | | ✅ |
| AP-11 | Editor: quick creator, full editor, export (was AV.6). **Design note first** | AP-7, AP-8, AP-9, AP-10 | yes | | ✅ |
| AP-12 | Widget cutover and polish (was AV.8, F-09) | AP-7, AP-6 | yes | | ✅ |
| AD-2 | Art direction picked (Q17, Q16). Delegated 2026-10-07. Recorded as D-53. Restyle is AD-3, after the editor and widgets | AD-1 | | | ✅ |
| AD-3 | Apply the art direction: record the decision, restyle shipped art if needed | AD-2, ST-3 | yes | | ⬜ |
| AP-13 | Expression art batch 2: the rest of the catalog 🎨 | AP-6, AP-8, AD-3 | | | ⬜ |
| AP-15 | Content waves (AP-15.1–15.9) 🎨 | AP-11, AD-3 | per wave | | ⬜ |
| AP-14 | Status face picker: any expression as a status. **Design note first** | AP-13, AP-11 | yes | yes | ⬜ |
| AP-16 | Hardening, performance, accessibility, final report | all above | yes | yes | ⬜ |

---

## 6. Specs

### 6.0 Design-note template (for rows marked "design note first")

`docs/handoff/AP-<n>-<slug>.md`, at most about two pages:
1. Goal and the spec rows it covers.
2. Data and wire changes: before and after JSON, migrations, the server contract.
3. Privacy analysis: which fields each viewer can see, and how each test proves it.
4. Test plan, mapped to the acceptance list.
5. PR split.
6. Risks and rollback.

Commit it first. If it reveals a needed decision, stop (runbook §7).

---

### AP-1 · Brand silhouette is the avatar head (D-45)

Branch `avatar/ap-1-brand-silhouette`. One PR.

1. Add `config/teardrop_silhouette.json`: the 108-unit path from `docs/art/brand/gen_brand.py`
   (`TEARDROP`), plus the mapping to the 1024 grid: x' = 512 + (x − 54)·760/54,
   y' = 96 + (y − 26)·760/54. Mapped path:
   `M 512 96 C 737.2 96 892 264.9 892 476 C 892 659 751.3 771.6 624.6 870.1 Q 512 954.5 399.4 870.1 C 272.7 771.6 132 659 132 476 C 132 264.9 286.8 96 512 96 Z`.
   Reference widths: y 430 → 135–889; y 600 → 154–870; y 700 → 212–812; y 800 → 308–716;
   y 870 → 392–632; chin bottom about y 912.
2. `gen_brand.py` reads `TEARDROP` from that file. Rerunning it must leave every brand output
   byte-identical (check with `git diff`).
3. `base_teardrop` `face`: exactly the mapped path, generated from the config (a script under
   `tools/` or a JVM generator test), never hand-typed. Remove the neck column from the face.
   The neck moves to the body in AP-3. Until AP-3 lands, keep a temporary `neck` part on band 40,
   *drawn before the face* and starting at y ≤ 860, so nothing looks broken in between.
4. `outline`: an even-odd ring generated from the canonical path, offset inward by 36 units
   (Tiller-Hanson offsets as in `gen_brand.py` `stroke_outline`). Re-fit `shade` to the new chin.
5. Re-check anchors against the new shape. The brand art puts the eye line near y 420 and the
   mouth near y 580. Re-place eyes, brows, mouths, hair, facial hair and glasses that no longer
   hug the outline. Bump `contentVersion` on every changed picture.
6. Update `docs/ASSET_SPEC.md` (geometry numbers) and the master-plan §5 decision list.

**Tests:** a new JVM test samples at least 200 points along the canonical path and along the
`face` commands and asserts the maximum deviation is ≤ 2 units. The neck's top is ≥ 20 units
inside the chin, and its width is ≤ 240 at every y. Re-record the vector goldens and contact sheets
and review them. Procedural widget goldens must not change.
**Acceptance:** side-by-side old base, new base and brand mark at 512 and 48 px in the report.
`check.sh` and `--device` pass.

### AP-2 · F-31 pack directory per asset

Branch `avatar/ap-2-pack-dirs`. Exactly the fix and verify steps in `master-plan.md` F-31. Small
PR. Mark F-31 ✅ in the master plan.

### AP-3 · Body, body bands and framing (D-47)

Branch `avatar/ap-3-body-framing`. Two PRs: (a) domain and renderer, (b) art and goldens.

1. Add bands 34, 36 and 38 (§3.2) to `VectorPictureValidator`, `CompositeOrder` and the docs.
   Body-region bounds for body bands, band 20 and background (§3.1).
2. `Framing` enum, `RenderTarget.framing` (§3.3), framing in the render key, and the renderer's
   viewport transform. Backgrounds fill the viewport.
3. `base_teardrop` gains a `body` part (band 34, `face.*` slots): neck about 240 wide from y ≤ 860
   (behind the chin) down to about y 1000, then shoulders curving out to about x −160..1184 by
   y ≈ 1200, then straight down to y 1536. Draw it with a gentle, friendly slope that reads as
   shoulders at 512 px and as a collar-width neck at 48 px. Remove AP-1's temporary neck.
4. Provide **one** free default top so no avatar is shirtless by default: `top_crew_tee`
   (band 36, `top.primary`, with a visible crew collar at y ≈ 960–1010). It becomes the pack
   default `TOP`. Add the `TOP` and `OUTERWEAR` categories, and `JEWELRY` too, so AP-15 doesn't
   reopen the enum.
5. `EmojiSlice` and the Vector slice screen show both framings.

**Tests:** framing unit tests (viewport math, target → framing, the render key differs per
framing). The validator accepts body-region geometry only on the allowed bands. Goldens at 48 px
(`COMPACT_WIDGET`, head framing) and 512 px (`PROFILE`, bust framing). At 48 px, the collar
color must cover at least 3 pixel rows under the chin (pixel assertion).
**Acceptance:** contact sheet of both framings, light and dark. The face at 48 px is no more
than 8% smaller than before AP-3 (measure the head's pixel width). Device screenshots.

### AP-4 · Authoring pipeline and picture format v2

Branch `avatar/ap-4-pipeline`. Two or three PRs: (a) picture format v2 (strokes, tags, masks
syntax: parse, validate and render; v1 still loads), (b) `tools/asset_pipeline.py` plus SVG
sources for every shipped picture plus the CI `check` step, (c) `PackContactSheetTest` and
per-category goldens.

1. Picture v2 as in §3.9. The renderer draws strokes with the resolved slot color. Masks are
   parsed and validated here and *applied* in AP-8.
2. The pipeline (§3.9), using only the standard library. `build` writes pictures in a stable
   order with stable number formatting, so output is byte-reproducible. Unit tests for the
   pipeline live in `tools/tests/` and run with `python -m unittest` from `scripts/check.sh` and
   `scripts/check.ps1` (add the step to both, and to CI). Ubuntu runners call it `python3`, so use
   `python3` in the workflow, and in `check.sh` use whichever of `python3` and `python` exists.
3. Write SVG sources for every existing `emoji_core` picture (round-trip: build from the SVG
   must equal the shipped JSON, or the shipped JSON is regenerated with the goldens re-reviewed).
4. `PackContactSheetTest`: for each category, a grid of every asset on the neutral default
   avatar at 48, 96 and 512 px, in head framing for 48 and 96 and bust framing for 512, light
   and dark. These are the review artifact for every content PR.
5. Write `docs/avatar/AUTHORING.md`: how to add an item end to end, the SVG attributes, the
   commands, and the review checklist.

**Acceptance:** `python tools/asset_pipeline.py check` passes locally and in CI. Adding a
throwaway test asset needs no Kotlin change (show this in the report, then remove the asset).

### AP-5 · Expression catalog (was AV.3)

Branch `avatar/ap-5-expression-catalog`. The spec is `master-plan.md` §4.1 "AV.3 spec",
unchanged, with these additions:
- Cover `includeSubgroups` plus the decomposition of `face-hat` and `face-glasses` (those yield
  items for AP-15 and an expression). `explicitHeadCodepoints` and the deferred subgroups are out
  (D-46: no other heads).
- For each expression, add `handOverlay` (true for face-hand) and `intensity` hints if useful.
- Output the distinct shape list with counts. That is the art plan for AP-6 and AP-13.

### AP-6 · Expression system and art batch 1

Branch `avatar/ap-6-expressions-b1`. Two PRs: (a) system, (b) art for the 16 mood faces.

1. `ExpressionDef` resolution from the catalog. `restingExpressionId` and mood semantics use
   catalog ids. `Expression` enum names are aliases (a test checks every enum value maps).
2. Shared part assets: eye, brow and mouth shapes plus overlays (tears, sweat drop, blush, Zs,
   spiral eyes, hearts, steam puff, sparkles), drawn per the style guide on the AP-1 face. Eye
   shapes use `eye.primary` (user eye color) and `eye.white` where whites show.
3. Batch 1: the 16 priority-1 expressions (one per `Mood`), plus neutral and happy redrawn on the
   new geometry if AP-1 didn't already.
4. Retire procedural expression parts that vector parts replace (`retired` mappings).

**Tests:** every `Mood` resolves to its priority-1 expression. Mood hidden gives neutral eyes and
mouth with no overlays (privacy). Contact sheet of the 16 faces at 48, 96 and 512 px. Each face is
distinguishable at 48 px: a test checks that no two batch-1 faces render pixel-identical at
48 px. The user's review is asynchronous (review queue).

### AP-7 · Schema 3 saved everywhere and the teardrop-only migration (was AV.4). **Design note first.**

Branch `avatar/ap-7-schema3-persist`. Split as the design note decides, at least: (a) client
storage and migration, (b) server and contract.

Requirements:
1. **Room:** the avatars table stores schema 3 `AvatarConfiguration` JSON. The migration from
   database version 2 (exported schema) converts v1 rows with `LegacyAvatarMigration`, which
   now maps **every** legacy base to `base_teardrop` (D-46) and keeps hair, accessory and
   color intent where a mapping exists. It isn't destructive, and it's tested with the exported
   schemas, as F-13 was.
2. **Load and save** go through `AvatarConfiguration.decode` and `prepareForWrite` (F-29 ✅).
   `NeedsAppUpdate` shows "Update the app to edit this avatar" and doesn't save.
3. **Retire the legacy procedural bases** (`base_blob`, `base_bot`, `base_ghost`, `base_critter`,
   `base_orb` and any other non-teardrop base) to `base_teardrop` in the manifests.
4. **Avatar Studio (old screen)** keeps working until AP-11 replaces it: remove its base picker,
   and save through the new path.
5. **Server:** a new migration file (never edit a merged one) stores schema 3 in
   `avatar_configurations`. `put_avatar` validates ids against a server asset catalog generated
   from the manifests (Phase 3 in `docs/IDL_AVATAR_CREATOR_PLAN.md`), rejects unknown and
   retired ids, and adds the entitlement hook (free tier only until C.3). `presence_view` returns
   PresenceView v2 (identity plus field-filtered semantics, client composes; D-24).
   `visual.expression` is present only when mood is visible (F-19 vector).
6. **Contract:** regenerate `contract/privacy_vectors.json` with
   `-Pidl.updateGolden=true` after the Kotlin reference changes. The SQL must match it exactly.
   Add `contract/composition_vectors.json` (filtered view plus pack → resolved layers). Do the
   mutation check: leak mood on purpose and confirm the vectors fail; record that in the report.
7. **Fake backend and the demo world** move to schema 3. Demo friends get distinct teardrop
   looks (hair, colors, tops), so the home screen shows variety.
8. Widgets now draw vector bases through the shared renderer. Keep availability and activity
   glyphs (D-28), and re-record the widget goldens with review.

**Acceptance:** `check.sh`, `--sql` and `--device` pass. CI `backend` is green. Upgrading a demo
install from `main` to this build keeps the user's avatar (a manual device check: install the
old APK, create an avatar, install the new APK, take screenshots). Mark F-19 and F-29 ✅.

### AP-8 · Occlusion, layering and legibility engine

Branch `avatar/ap-8-occlusion`. One or two PRs.

1. Apply `publishMasks` and `clipBy` (§3.6) in the compositor and renderer. Deterministic order:
   masks resolve from the final draw list, sorted by band, then asset id, then part index.
2. Split the existing hair pictures into `hair_top`, `hair_side` and `hair_back` parts. Add
   sample assets proving each case: a brimmed cap that publishes `occlude.hair_top`; glasses
   whose arms tuck under side hair; a beard with its mouth hole; a hood (outerwear) whose back
   part sits on band 20.
3. `FACIAL_HAIR` becomes `multiple` (a beard plus a mustache). Conflicts are declared where two
   items overlap badly.
4. The hand-overlay rule from §3.6. Item-transform limits per category (eyewear: vertical nudge
   ±24; headwear: scale 0.9–1.1; everything else: none until a spec allows it). The editor uses
   these limits.
5. `LegibilityTest` (§4) with thresholds taken from the neutral baseline, documented in the test.

**Tests:** unit tests per mask case. The combination property test (§4) lands here.

### AP-9 · Color system

Branch `avatar/ap-9-color`. One or two PRs.

1. OKLCH conversion in `domain` (pure Kotlin, tested against reference values). Derived shadow and
   highlight (§3.5). Bump `RENDER_VERSION` and re-record goldens with review.
2. Slot links as pack data, plus resolver support. Unlinking writes `unlinkedSlots`.
3. Swatch sets (§3.5) as pack data (`palettes` in the manifest defaults), with names for
   accessibility ("Deep brown", "Copper"). The skin set must span very light to very deep with
   warm and cool undertones. Check contrast against the outline color.
4. Contrast warnings as a pure function (for example, hair the same as skin, or a top the same
   as the background), shown in the editor later.

**Tests:** OKLCH round trips, link resolution, unlink, and that every swatch has a name.

### AP-10 · Store-ready plumbing and the D-48 guard

Branch `avatar/ap-10-store-ready`. One PR.

1. `AssetDef` fields from §3.8, the `Entitlements` interface, the local implementation, and the
   debug-only "Unlock all items" setting (in the existing debug settings, never in release
   builds).
2. `prepareForWrite` returns a new `AvatarWrite.NeedsEntitlement(assetIds)` for unowned premium
   items. The old Studio and the AP-11 editor show it.
3. The D-48 guard test (§3.8).
4. Mark a handful of existing or sample items `PREMIUM` with a price, to exercise the path.

**Tests:** entitlement unit tests, the guard test, and a save refused then allowed after unlock.

### AP-11 · Editor: quick creator, full editor, export (was AV.6). **Design note first.**

Branch `avatar/ap-11-editor`. Several PRs, each shippable: (a) editor state and undo/redo in
pure Kotlin (`domain` or a view model with no Android types in its logic), (b) full editor UI,
(c) quick creator plus first-run, (d) export.

1. **Full editor** (`docs/ARCHITECTURE.md` §8, master plan §8): a large bust preview, a live
   48 px head-framing preview beside it, the tab strip from §3.4, an item grid with thumbnails
   from the pipeline, a color row for the current item's slots (swatches, custom picker,
   link/unlink for highlight, brows and beard), undo/redo, a seeded randomize (compatible free
   items only, repeatable), reset category, reset all, before/after, and save through
   `prepareForWrite`. Locked premium items show a lock with try-on.
2. **Quick creator** (first run and "Start over"): skin → hair and hair color → top → done, with
   the 48 px preview always visible. Under 2 minutes.
3. **Export:** transparent PNG, solid PNG and background PNG at 512, 1024 and 2048 (rendered at
   size, never upscaled), recipe JSON, and the share sheet.
4. Replace the old Avatar Studio and its v1 `AvatarConfig` editing. Replace the onboarding hero
   row (fox, ghost, robot) with three teardrop demo avatars.
5. Accessibility: every grid tile and swatch has a label, TalkBack order is coherent, and layouts
   hold at 200% font size.

**Tests:** editor-state unit tests (undo/redo invariants, randomize repeatability over 1,000
seeds with zero conflicts). Instrumented Compose tests for the core flows (create, change hair
color, save, undo, locked item refused). Device screenshots of every tab.
**Acceptance:** a first-run user reaches a saved, customized avatar in under 2 minutes on the
emulator (time the instrumented flow).

### AP-12 · Widget cutover and polish (was AV.8, F-09)

Branch `avatar/ap-12-widget`. One PR.

1. Widgets render through the vector pipeline with head framing (§3.3). Availability glyphs and
   activity badges are intact (D-28). Wallpaper contrast (F-07) is intact.
2. Delete `PlaceholderFrame` and any procedural painter no asset uses any more (F-09 ✅). Keep
   procedural presence chrome until vector chrome replaces it.
3. Performance: a cold widget render and a cached render, measured on the emulator and recorded
   in the report.

**Tests:** widget goldens at every widget target, light and dark, with each availability state.
Device screenshots of real home-screen widgets.

### AP-13 · Expression art batch 2: the rest of the catalog

🎨 **Made with the Art Studio by Cursor after AD-3 (D-50 as amended 2026-10-07).** The spec below still defines done.

Branch per subgroup, `avatar/ap-13-<subgroup>`. One PR per catalog subgroup, in catalog order.
Each PR draws the new shapes it needs, wires its expressions, extends the contact sheet, and
keeps `LegibilityTest` and the privacy tests green. Hand-overlay expressions follow §3.6.
**Acceptance for the row:** every catalog expression resolves with no fallback, and the
`ExpressionCoverageTest` (added in the first PR) reports 100%.

### AP-14 · Status face picker. **Design note first.**

Branch `avatar/ap-14-status-face`. Server and client.

1. Status drafts and presence carry an `expressionId` (a catalog id; the old `expression` enum
   values are accepted as aliases). Precedence: an explicit face beats the mood's automatic face
   while the status lasts. It expires with the status (invariant 3) and is mood-category privacy
   (invariant 2).
2. Server: store, filter (only to viewers who may see mood) and return it in
   `presence_view.visual.expression`. Privacy vectors and SQL updated. Composition vectors cover
   it. Mutation check.
3. Status Deck: a "Face" grid grouped by catalog subgroup, with search and recents. Quick states
   may set a face.
4. Every face is free (invariant 6).

**Tests:** privacy vectors (hidden mood means no face), expiry, precedence, instrumented
Status Deck flow, device screenshots.

### AP-15 · Content waves

🎨 **Made with the Art Studio by Cursor after AD-3 (D-50 as amended 2026-10-07).** The waves below still define done.

One branch and PR per wave: `avatar/ap-15-<n>-<category>`. Each wave follows
[`AUTHORING.md`](AUTHORING.md) and the style guide, passes the pack, legibility and combination
tests, and adds its contact sheets. Free and premium counts are minimums. More is better if
quality holds.

| Wave | Category | Free (D-48) | Premium | Must include |
| --- | --- | --- | --- | --- |
| 15.1 | Hair | 12 | 6 | Straight short and long, wavy, curly, coily, afro, locs, braids, buzz, bald, bun, ponytail, mohawk |
| 15.2 | Facial hair | 6 | 3 | Stubble, full beard, goatee, mustaches (2), sideburns; long beard (premium) |
| 15.3 | Tops | 8 | 8 | Tee, V-neck, collared shirt, sweater, tank, turtleneck, hoodie front, dress top; patterned and themed (premium) |
| 15.4 | Outerwear | 2 | 6 | Open shirt, zip hoodie; jackets and coats (premium) |
| 15.5 | Eyewear | 6 | 4 | Round, square, cat-eye, rimless, sunglasses (tinted), reading |
| 15.6 | Headwear | 4 | 10 | Beanie, cap, headband, bucket hat; party, crown, wizard and themed (premium). Headphones stay semantic and free |
| 15.7 | Jewelry and face details | 4 | 10 | Stud earrings, hoops, nose stud, freckles (free face detail), necklaces, chains |
| 15.8 | Backgrounds and frames | 6 + 4 | 8 + 6 | Plain colors and soft shapes free; scenes and patterns premium |
| 15.9 | Decomposed emoji items | per catalog | per catalog | Cowboy hat, party hat, monocle, nerd glasses and so on, from `face-hat`/`face-glasses` |

### AP-16 · Hardening, performance, accessibility, final report

Branch `avatar/ap-16-hardening`.
1. Full regression sweep: `check.sh --sql --device`, all goldens reviewed, and the combination
   test raised to 10,000 seeds once (record the time; keep 2,000 in CI).
2. Performance: decode once per picture (`VectorPictureCache` hit rate), editor preview
   responsiveness, and widget render time. Record against `docs/ROADMAP.md` "Performance
   targets".
3. Accessibility: TalkBack labels for every asset and swatch, avatar descriptions built from
   labels (no free text), 200% font size, and contrast.
4. Docs: `ARCHITECTURE.md`, `ASSET_SPEC.md`, `AVATAR_RECIPE_SCHEMA.md` and this file are brought
   up to date. The master plan marks the program done.
5. Final report `docs/handoff/reports/<date>-avatar-program-final.md`: everything shipped,
   inventory counts per category (free and premium), known gaps, and the user review queue.

---

## 7. Program definition of done

- Every row in §5 is ✅, with CI green on `main`.
- At least 100 expressions resolve (the whole catalog), and each Mood has its automatic face.
- The AP-15 minimum inventory is shipped, and the D-48 guard passes.
- The editor creates, edits, saves, exports and refuses unowned premium items, on the emulator.
- Widgets render the vector avatar with head framing, chrome intact, at every widget target.
- Privacy vectors, composition vectors and the mutation checks pass.
- There's no open P0 or P1 finding introduced by the program.

## 8. Out of scope (later, each needs its own spec)

Spending Charge and the store screen (needs C.3), remote pack delivery, user-generated items,
animation, per-friend looks UI (U.3, D-35), saved looks and identity slots UI, weather visuals
(D-37), thought bubbles (U.1), and seasonal content beyond AP-15.
