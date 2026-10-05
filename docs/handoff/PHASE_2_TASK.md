# Phase 2 task: vector renderer core and the first original picture set

**Scope:** `docs/ROADMAP.md` steps 3, 4, 5 and 8, which is the D-39 vector path inside Avatar
Phase 2 (`docs/IDL_AVATAR_CREATOR_PLAN.md`). Three PRs, in order (section 9). No editor UI, no
export, no Noto import, no server or Room changes.

Read first: `AGENTS.md`, `master-plan.md` §0, `docs/ARCHITECTURE.md`,
`docs/adr/0001-vector-asset-renderer.md`, `docs/ASSET_SPEC.md`, `docs/AVATAR_RECIPE_SCHEMA.md`.
D-24, D-28, D-31 and D-39 are settled. Don't reopen them.

## 0. Goal and exit

A recipe that names vector assets renders through the existing pipeline:
`AvatarConfiguration` → `AvatarResolver` → `AvatarRenderer` → bitmap. Vector parts interleave
with procedural layers in one stable z order. The availability glyph and activity badge still
draw on top. Every procedural render is **byte-identical** to today's.

**Exit:**

- Every existing Roborazzi golden passes **unchanged**. That proves the compositor refactor
  didn't move a pixel of the procedural pack.
- New goldens show the round-face slice at 48, 128 and 512 px.
- A debug-only screen shows the slice on a device.

## 1. Decisions this spec makes

These were open in `docs/ROADMAP.md`. The user can veto any of them in review.

| Topic | Choice | Why |
| --- | --- | --- |
| First art | **Original, hand-authored** pictures (`license: proprietary-idl`). No Noto files | ROADMAP step 4 recommends it. It keeps the license import out of the renderer change |
| Hair and facial hair | New `AssetCategory` values `HAIR` and `FACIAL_HAIR` (wire `hair`, `facial_hair`), selected through `AvatarConfiguration.itemIds` | The slice needs them; ROADMAP prefers new categories once they're needed. Glasses reuse `FACE_ACCESSORY`; the background reuses `SCENE` |
| Pack | A second pack, `emoji_core` v1, beside `core_proto` v1 | Keeps the replaceable D-39 art separate from the placeholder pack |
| Expressions on the new base | `emoji_core` adds per-base parts for existing expressions (`expressionOverrides`, section 3.3), not new expression ids | Expression ids are global and come from presence. A second pack must not redefine them |
| Placement | Pictures are authored **in final position** on the 1024 grid. No anchor fitting this phase | Anchors and family overrides are ROADMAP "Later" |
| Shade derivation | A simple mix rule now (section 4.4). OKLCH replaces it later behind the same function | Changing one primary color must not leave mismatched shadows |
| Item compatibility | Keep `compatibleBases`. Add a `family` field on bases only | One family this phase; `familyCompatibility` arrives with the second family |

## 2. Picture format (`VectorPicture`, domain)

Pure Kotlin in `app.idl.domain.avatar`, `@Serializable`, parsed with `IdlJson`. File:
`packs/emoji_core/v1/pictures/<assetId>.json`. Update `docs/ASSET_SPEC.md` §1 to match this
exactly.

```json
{
  "schemaVersion": 1,
  "id": "beard_round_full",
  "contentVersion": 1,
  "viewBox": 1024,
  "clipPaths": [
    { "id": "mouth_hole", "commands": "M 430 690 C ... Z" }
  ],
  "parts": [
    {
      "id": "beard",
      "zBand": 60,
      "fill": { "slot": "beard.primary" },
      "fillRule": "nonzero",
      "opacity": 1.0,
      "clip": { "id": "mouth_hole", "mode": "difference" },
      "commands": "M 300 640 C ... Z"
    },
    {
      "id": "face",
      "zBand": 40,
      "fill": {
        "radial": { "cx": 430, "cy": 400, "r": 620,
          "stops": [ { "offset": 0.0, "slot": "face.highlight" },
                     { "offset": 1.0, "slot": "face.primary" } ] }
      },
      "commands": "M 512 96 C ... Z"
    }
  ]
}
```

| Field | Rule |
| --- | --- |
| `schemaVersion` | `1`. Reject anything else |
| `id` | Equals the manifest asset id |
| `contentVersion` | ≥ 1. Equals the manifest entry's `contentVersion` |
| `viewBox` | `1024` |
| `fill` | Exactly one of `slot`, `linear {x1,y1,x2,y2,stops}` or `radial {cx,cy,r,stops}`. Coordinates are in viewBox units. Stops: ≥ 2, offsets strictly increasing within 0..1, each with `slot` and optional `alpha` (0..1) |
| `fillRule` | `nonzero` (default) or `evenodd`. Outlines are even-odd rings, because the pipeline turns strokes into fills |
| `opacity` | 0..1, default 1 (tinted lenses) |
| `clip` | Optional `{id, mode}` naming an entry in this picture's `clipPaths`. `mode` is `intersect` or `difference` (`Canvas.clipOutPath`, API 26) |
| `zBand` | One of the **character** bands in `ARCHITECTURE.md` §5: 0, 10, 20 … 110. Bands ≥ 200 are presence chrome and are rejected |
| `commands` | SVG path data, section 2.1 |

Parts draw in band order; within a band, in array order.

### 2.1 Path grammar (`PathData.parse`, domain)

Accept `M m L l H h V v C c S s Q q T t Z z`, including:
- implicit repeats ("M 0 0 10 10" is a moveto then a lineto)
- compact numbers (`-.5.5`, `1e-3`, `10-5`)
- commas or whitespace

`S`/`T` reflect the previous control point per the SVG spec. **Reject `A a`** (arcs), because
the pipeline converts arcs to cubics. Also reject NaN, infinity and unknown letters.

Output a list of **absolute** ops: `MoveTo`, `LineTo`, `QuadTo`, `CubicTo`, `Close`. The Canvas
side turns those into an `android.graphics.Path`. Parsing stays on the JVM so it's unit-testable
and usable by the validator.

### 2.2 Validator (`VectorPictureValidator`, domain)

`validate(picture, asset): List<String>`, where an empty list means valid. It checks:
- every rule in the field table above
- duplicate part or clip ids
- clip references that don't exist
- a fill slot not declared in the asset's `colorSlots`
- geometry outside −16..1040 unless the part has `"allowOverflow": true`

An `AssetPackTest` case runs it over every vector asset in every shipped pack. That's the CI
gate until the desktop pipeline (ROADMAP step 9) exists.

## 3. Manifest and registry changes (domain)

### 3.1 `AssetDef` additions

All defaulted, so the 146 procedural rows don't change:

- `render.type = "vector"` with `render.file = "pictures/<id>.json"`, relative to the pack
  directory. The `file` field already exists.
- `contentVersion: Int = 1`
- `colorSlots: Map<String, String> = emptyMap()`: slot → `#RRGGBB` or `#AARRGGBB`
- `family: String? = null`: bases only, e.g. `round_face`
- `defaultTransform: ItemTransform? = null`: reuse the schema 3 `ItemTransform`

Validation:
- a vector asset needs `file` and a non-empty `colorSlots`
- `family` is allowed only on `BASE`

### 3.2 Categories

Add `HAIR` and `FACIAL_HAIR` to `AssetCategory`, with `multiple = false` this phase. Choose
`defaultZ` values that don't collide with existing ones (the procedural painter never sees them).

### 3.3 `AssetManifest.expressionOverrides`

`Map<expressionId, Map<baseId, ExpressionParts>>`. `AssetRegistry` merges these into the named
`ExpressionDef.baseOverrides` in manifest order. It's a validation error to:
- override an expression no pack defines
- override the same (expression, base) pair twice
- name a part asset that doesn't exist

### 3.4 Families

Add `AssetRegistry.baseFamilies: Map<String, String>`, built from base `family` fields. Pass it
to `migrateRecipe` wherever the resolver and widget inputs call it today. That closes the
"familyId isn't read from the manifest" deviation from schema 3.

### 3.5 Shipped packs

`AssetPacks.SHIPPED = [core_proto/v1, emoji_core/v1]`, with `core_proto` first so its
`defaults` still win.

## 4. Resolver changes (domain)

### 4.1 `itemIds`

For each category key in sorted order:
- an unknown category wire name, or an id from another category, is dropped as `UNKNOWN_ASSET`
- an incompatible base is dropped as `INCOMPATIBLE_BASE`
- a conflict uses the existing conflict rules, with priority `SIGNATURE` (hair and facial hair
  are identity)

So an explicit status accessory (`STATUS`, D-31) still wins a conflict. Sizing rules
(`minSizePx`, `widgetSafe`, target simplification) apply as for every other layer.

### 4.2 Expressions

`partsFor(baseId)` already picks overrides. Eye and mouth family variants don't apply to vector
assets: a vector asset id is the whole choice.

### 4.3 `ResolvedAvatar` additions

- `colorSlots: Map<String, Int>`: final ARGB per slot, from section 4.4, sorted by key
- `itemTransforms: Map<String, ItemTransform>`: only for assets present in `layers`

### 4.4 Color resolution (`ColorSlots.resolve`, domain)

For each slot that any resolved vector asset declares, try in this order:

1. The recipe's `colorOverrides[slot]`. An invalid hex is ignored, never a crash.
2. Derived: for `x.shadow` / `x.highlight`, when `x.primary` is overridden and the slot isn't in
   `unlinkedSlots`. Shadow is primary mixed 25% toward black; highlight is primary mixed 30%
   toward white. Do it per channel, in the same way as `AvatarRenderer.mix`.
3. The asset's `colorSlots` default. Two assets that declare the same slot must give the same
   default; registry validation reports a mismatch as an error.
4. The neutral `#FF9E9E9E`.

### 4.5 Render key

Append `assetId@contentVersion` for every vector layer, sorted. An art change then invalidates
widget caches even when the pack version doesn't change.

### 4.6 Invariants (tests required)

- A hidden mood never brings in a mood-derived vector expression (invariant 2).
- The same request gives the same layers, colors and render key (invariant 8).
- `docs/ARCHITECTURE.md` §3: tears, blush and anger marks stay mood-derived. Hair, beard,
  glasses and skin are identity.

## 5. Compositor order (domain)

Add `CompositeOrder.ops(resolved, registry, pictureOf: (assetId) -> VectorPicture?): List<DrawOp>`,
with:
- `DrawOp.Procedural(category)`
- `DrawOp.VectorPart(assetId, partIndex)`
- a flag on each op saying whether it draws inside the frame clip or as chrome

Sort by (band, `category.defaultZ`, assetId, partIndex). Vector parts use their own `zBand`.
Procedural layers use this table, which **reproduces today's paint order exactly**:

| Procedural category | Band |
| --- | --- |
| SCENE | 0 |
| BODY_ACCESSORY | 10 |
| BASE, SIGNATURE_FEATURE | 40 |
| FACE_EYE, FACE_BROW, FACE_MOUTH | 50 |
| FACE_ACCESSORY | 80 |
| HEAD_ACCESSORY | 90 |
| EXPRESSION_OVERLAY | 95 |
| FOREGROUND_PROP | 105 |
| AVAILABILITY_INDICATOR (chrome) | 210 |
| ACTIVITY_BADGE (chrome) | 220 |

Add the 95 and 105 rows to `ARCHITECTURE.md` §5 as procedural-only bands.

A category drawn by a vector asset is not also drawn procedurally. With no vector assets, `ops`
must produce today's `Layer` order. Test that against `PlaceholderFrames` for every quick state.

## 6. Renderer (Android, `app.idl.avatar`)

- **`VectorPictureCache(read: (path) -> String)`.** Parses each picture once per
  `assetId@contentVersion` and holds the built `android.graphics.Path` objects.
  - Thread-safe, because widgets render concurrently (see F-26).
  - Never mutate a cached `Path`. Apply transforms with `Canvas.concat`.
  - Owned by `AppContainer`. Tests build one with a reader over `src/main/assets`.
- **`CanvasVectorAssetRenderer`** implements ADR 0001's `VectorAssetRenderer`:
  - scale is `sizePx / 1024`
  - the transform is the manifest `defaultTransform`, then the recipe `ItemTransform`. Translate,
    then rotate and scale about (512, 512), then flip horizontally about x = 512
  - fill with a slot color or a `LinearGradient` / `RadialGradient`
  - `opacity` multiplies alpha
  - clips use `clipPath` / `clipOutPath` inside `save` / `restore`
- **`AvatarRenderer.bitmap(resolved, registry, pictures, sizePx, …)`.**
  - `pictures` is **required**. A missing picture logs `avatar.vector_missing` (asset id only)
    and that asset is skipped, so the procedural fallback for that category still draws (see the
    fallback rule below).
  - It iterates `CompositeOrder.ops`. Procedural ops call the existing `Painter` methods through
    the `PlaceholderFrame` bridge.
  - Frame-clip ops go inside today's `save` / `restore` block; chrome goes after it.
- **Widget path.** `Widgets.kt` passes `container.vectorPictures`. No saved avatar uses vector
  assets yet, so widgets look the same. That's the expected result.
- **Fallback.** When a vector picture fails to load or validate at runtime:
  - with a `fallback` asset on the entry, draw that
  - otherwise skip the layer and log
  - never crash the widget; `renderCatching` already covers that

## 7. Art: `emoji_core` v1 (original, `proprietary-idl`, `modified: false`)

Simple geometric shapes on the 1024 grid, authored to sit on `base_round_face`. Quality bar:
readable at 48 px, recognizably an emoji face. It doesn't need to be beautiful.

| Asset id | Category | Parts and bands | Slots (defaults are suggestions) |
| --- | --- | --- | --- |
| `base_round_face` | BASE, `family: round_face` | `face` 40 (radial gradient), `shade` 40, `outline` 40 (even-odd ring) | `face.primary #FFC83D`, `face.shadow #E0A21A`, `face.highlight #FFE38A`, `outline #7A4E00` |
| `eyes_round_neutral` | FACE_EYE (bases: round face) | `eyes` 50 | `eye.iris #3B2A1A` |
| `eyes_round_happy` | FACE_EYE | `eyes` 50 (upward arcs as filled shapes) | `eye.iris` |
| `brows_round_relaxed` | FACE_BROW | `brows` 50 | `hair.primary #5B3A29` |
| `mouth_round_neutral` | FACE_MOUTH | `mouth` 50 | `mouth #6B2E1F` |
| `mouth_round_smile` | FACE_MOUTH | `mouth` 50 | `mouth` |
| `hair_round_bob` | HAIR | `back` **20** (behind the head), `bangs` **70** (over the forehead) | `hair.primary`, `hair.shadow #3E271B` |
| `beard_round_full` | FACIAL_HAIR | `beard` 60 with a `difference` clip `mouth_hole`, so the mouth shows | `beard.primary #5B3A29` |
| `glasses_round_wire` | FACE_ACCESSORY, `conflictsWith: [head_vr_headset]` | `frames` 80 (even-odd rings), `lenses` 80 at `opacity: 0.35`, so the eyes show through | `glasses.frame #2B2B2B`, `glasses.lens #6FA8DC` |
| `scene_round_soft` | SCENE | `bg` 0 (linear gradient) | `accessory.primary #DCEBFF`, `accessory.secondary #F6F0FF` |

`expressionOverrides`:
- `neutral` on `base_round_face` uses `eyes_round_neutral`, `brows_round_relaxed` and
  `mouth_round_neutral`
- `happy` on `base_round_face` uses `eyes_round_happy`, `brows_round_relaxed` and
  `mouth_round_smile`

Other expressions on the round face fall back to procedural parts. That's an accepted stand-in;
say so in the report.

This set exercises every ADR feature:
- a solid fill, a linear gradient and a radial gradient
- an even-odd fill and a `difference` clip
- opacity
- a split asset across bands (hair at 20 and 70)
- a presence-chrome-above-character check (the availability glyph over the bangs)

## 8. Debug screen

Add "Vector slice", reachable from Settings **only when `BuildConfig.DEBUG`**. It shows:

- **Fixed recipes:**
  - bare face, neutral
  - face, happy (with mood visible)
  - plus hair
  - plus beard
  - plus glasses
  - everything plus `scene_round_soft`
  - everything plus BUSY availability and the VR activity badge
- **Each recipe at 48, 128 and 512 px.** Render off the main thread and show the bitmaps.
- **Controls:**
  - swatch chips for `hair.primary`, which also checks that the shadow is derived
  - an "unlink hair.shadow" toggle
- **Render time:** the last 512 px render time in ms, from `SystemClock.elapsedRealtimeNanos`.
  Display only; it's not a gate.

No new dependency. Don't add it to navigation in release builds.

## 9. PR plan

Create each PR from an up-to-date `origin/main`, after the previous one merges.

| PR | Branch | Contents | Visual change |
| --- | --- | --- | --- |
| A | `avatar/vector-domain` | Sections 2–5: picture model, path parser, validator, manifest and registry changes, `itemIds` in the resolver, color slots, render key, `CompositeOrder`. JVM tests only | None. All goldens unchanged |
| B | `avatar/vector-renderer` | Section 6: picture cache, Canvas renderer, compositor-driven `AvatarRenderer`, wiring through `AppContainer` and widgets. Test fixtures may use a tiny test-only picture under `src/test/resources` | None for procedural. All goldens unchanged |
| C | `avatar/emoji-core-slice` | Sections 7–8: the pack, pictures, debug screen, new goldens | New goldens only |

## 10. Tests

**JVM (`src/test`):**

- `PathData`: every command, absolute and relative; implicit repeats; S/T reflection; compact
  numbers; rejection of arcs, NaN and unknown letters.
- `VectorPicture` parse:
  - round trip
  - unknown JSON keys are ignored, but `schemaVersion` 2 is rejected
- Validator: one test per rule in section 2.2.
- Registry:
  - `expressionOverrides` merge
  - the duplicate and unknown-expression errors
  - `baseFamilies`
  - `migrateRecipe` sets `familyId = round_face` for `base_round_face`
- Resolver:
  - `itemIds` (accepted, unknown category, wrong category, incompatible base)
  - a STATUS headset beats `glasses_round_wire` for a non-close friend (D-31)
  - a hidden mood gives no `eyes_round_happy` (invariant 2)
  - determinism
  - the render key changes with `contentVersion`
- `ColorSlots`: each step in section 4.4, including an invalid hex and `unlinkedSlots`.
- `CompositeOrder`:
  - procedural-only ops equal today's `Layer` order for every quick state
  - hair `back` sorts before the base and `bangs` after the eyes
  - chrome sorts last
- Snapshots: `WidgetSnapshotTest` passes **unchanged** in PRs A and B. PR C adds
  `VectorSliceSnapshotTest` goldens for the section 8 recipes at 48, 128 and 512 px.
- Pack: every vector asset validates, and every `render.file` exists.

**Device (`src/androidTest`):**

- the slice renders at 48 and 512 px with exact bitmap dimensions
- the same recipe twice gives `sameAs` bitmaps
- a picture loads once per `contentVersion` (a cache-hit counter)

## 11. Out of scope

Each of these is a later task:

- the editor slice and export (ROADMAP steps 6–7)
- the asset-pipeline CLI and the Noto import (step 9)
- anchors and family fitting
- OKLCH
- thumbnails
- `familyCompatibility`
- making vector art the default widget look (step 10)
- high-contrast mode
- the RenderSheet exporter and the legibility lint
- Room or server changes and `PresenceView` v2 (Phase 3)
- new dependencies

F-09 stays 🟡: the procedural bridge is still lossy, and vector art retires it category by
category.

## 12. Acceptance criteria

1. `scripts/check.sh` passes on every PR, and all pre-existing goldens are unchanged in A and B.
2. The device tests pass on `emulator-5554` with `scripts/check.sh --device`.
3. The section 8 screen on a device shows hair behind and in front of the head, the mouth
   visible through the beard, the eyes visible through the lenses, and the availability glyph
   above the bangs. Attach screenshots to PR C and to `docs/handoff/reports/screenshots/`.
4. The domain stays Android-free. There's no `random()`, no `Instant.now()`, and no ordering that
   depends on hash-map iteration.
5. Docs are updated: `ASSET_SPEC.md` §1 (format), `ARCHITECTURE.md` §5 (procedural bands),
   `ROADMAP.md` (steps 3–5 and 8 done), and `master-plan.md` (Phase 2 row, work log).
6. Each PR has a checkpoint report in the `AGENTS.md` format, finalized after committing.
