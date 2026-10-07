# Art Studio agent guide

**For:** Claude Code and Cursor when making avatar art with the Studio (D-50).
**Authority:** [`ART_STUDIO.md`](ART_STUDIO.md) §4–§5, [`ART_STYLE_GUIDE.md`](ART_STYLE_GUIDE.md),
[`AUTHORING.md`](AUTHORING.md), [`docs/handoff/STUDIO_TASK.md`](../handoff/STUDIO_TASK.md).

The Studio is operator-only. It never ships. Drafts live in `.studio/` (gitignored) until
`promote` (ST-3). Do not hand-edit generated picture JSON under `app/src/main/assets/packs/`.

## Setup (once per machine)

```bash
python tools/studio/studio.py setup          # creates tools/studio/.venv, installs skia + numpy
python tools/studio/studio.py serve          # http://127.0.0.1:8765 — Catalog / Workbench / Drafts
```

S0 commands (`serve`, `list`, `show`) are standard-library only. Everything else re-runs under
`.venv` when needed, or tells you to run `setup`.

## Prompt-to-item loop (concrete commands)

From `ART_STUDIO.md` §4. One prompt → one draft that passes lint. Keep revisions; never overwrite
history by hand.

```bash
# 1. Interpret — pick category and id (kit idPrefix + slug)
python tools/studio/studio.py kit head_accessory
python tools/studio/studio.py guides

# 2. Plan — start a draft; put the prompt in meta
python tools/studio/studio.py draft new hat_beanie_slouch \
  --category head_accessory \
  --prompt "a slouchy knit beanie with a pom-pom" \
  --group winter --label "Slouch beanie"

# Optional starter SVG with skeleton comments:
python tools/studio/studio.py kit head_accessory --skeleton hat_beanie_slouch > /tmp/hat.svg

# 3. Draft — build paths with geometry helpers (never freehand coords outside guides)
python tools/studio/studio.py geom offset '@guide:head_top_cap' 24
python tools/studio/studio.py geom ellipse 512 40 70 70
python tools/studio/studio.py geom subtract '@guide:head_outline' '@part:hat_beanie_slouch/crown'
# Path refs: @guide:<name>  @part:<draftId>/<part>  @file:<path>

# 4. Write — compiles, lints, renders the standard sheet
python tools/studio/studio.py draft write hat_beanie_slouch path/to.svg --note "crown + pom"

# 5. Inspect
python tools/studio/studio.py lint hat_beanie_slouch
python tools/studio/studio.py lint hat_beanie_slouch --json
python tools/studio/studio.py draft show hat_beanie_slouch
python tools/studio/studio.py render hat_beanie_slouch
# Sheet: .studio/renders/<id>-r<NNN>.png

# 6. Revise (max ~3 rounds). Each write is a new revision.
python tools/studio/studio.py draft revert hat_beanie_slouch 2
python tools/studio/studio.py draft set hat_beanie_slouch label="Slouch beanie" tier=premium
python tools/studio/studio.py draft set hat_beanie_slouch slot:hat.primary=#3A5A8C

# 7. Operator review — Drafts tab in the web UI (polls ~1.5s). Copy sheets into the PR
#    under docs/handoff/sheets/ and add rows to master-plan.md §4.1.

# Promote is ST-3 (not available in S1).
```

Hair example:

```bash
python tools/studio/studio.py draft new hair_short_curly \
  --category hair \
  --prompt "a short curly hairstyle" \
  --label "Short curly"
# Build top/shade/highlight from guides.head_top_cap + smooth/offset; write; lint; render.
```

## Vocabulary

| Term | Meaning |
| --- | --- |
| **Kit** | `tools/studio/kits/<category>.json`: skeleton parts, bands, slots, try-on sets, tips |
| **Guides** | Named geometry on the teardrop (`guides` command / `/api/guides`): `head_outline`, `head_top_cap`, `hairline_max`, `eye_zone`, … |
| **Draft** | WIP under `.studio/drafts/<id>/` with `meta.json` + `rev-NNN.svg` |
| **Lint** | Pipeline validator + style rules + (when possible) 48 px legibility |
| **Sheet** | Standard PNG: head 48(4×)/96/192 light+dark, bust 256, kit try-ons, two skins, four expressions |
| **Geom** | Deterministic path ops: union, subtract, intersect, offset, mirror, thicken, ellipse, … |

Categories with kits in S1: `hair`, `head_accessory`, `face_accessory`, `facial_hair`.

## Content rules (style guide §6 + §3)

- **No text, letters, numbers, logos, brands or trademarks** on any part (clothes included).
- No smoking, vaping, alcohol, drug or weapon props. Mouth props: lollipop, straw, whistle.
- No stereotyped ethnic features. Variation is hair, color and accessories — not face shape.
- Cultural/religious headwear stays free and goes in the user review queue before release.
- Every fill/stroke uses a **slot**. Item shade bottom-right; highlight upper-left. One shade shape.
- Hairline / hat lower edge ≤ y 330 at the centre (`hairline_max`). Stay out of `eye_zone` unless
  the item's job is to cover eyes (`coversEyes` on draft meta).
- Hair needs a visible `hair.highlight` part. Headwear should publish `occlude.hair_top`.
- Minimum filled detail ~32 units. Design for **48 px** first.
- Original art only (`proprietary-idl`). Never trace third-party emoji.

## What not to touch

- App Kotlin / production deps / CI (Studio deps stay in `tools/studio/requirements.txt`).
- Merged `supabase/migrations/`, `contract/privacy_vectors.json` by hand.
- Shipped `art/` until ST-3 `promote`. S1 only writes `.studio/`.

## Checks

```bash
python -m unittest discover -s tools/studio/tests -t tools/studio
# Skia tests skip cleanly when skia is not installed.
python tools/studio/studio.py setup   # then re-run to execute Skia tests
```
