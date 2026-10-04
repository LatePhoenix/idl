# Phase 1 task: avatar model v2, asset pack, compatibility and resolution

**Branch:** `avatar-creator` · **Scope:** pure Kotlin in `app.idl.domain.avatar` plus one JSON
manifest and tests. **No** UI, renderer, Room, network, or SQL changes in this phase.

Context: `docs/IDL_AVATAR_CREATOR_PLAN.md` (Phase 1 + §6–7) and
`docs/IDL_AVATAR_CREATOR_MASTER_PLAN.md` (§n references). Decisions D-24…D-29 in
`docs/IDL_DECISIONS.md` are settled.

## 0. Already done (review, don't rewrite)

- `domain/avatar/AvatarModel.kt`: `AvatarConfiguration`, `StyleDna`, `SemanticVisualOverride`,
  `SemanticKey`, `IdentitySlot`, `RenderTarget`, `WallpaperContrastMode`,
  `AccessibilityRenderMode`, `VisiblePresence`, `AvatarRenderRequest`.
- `domain/avatar/AssetManifest.kt`: manifest schema (`AssetDef`, `ExpressionDef`,
  `SemanticMapping`, `PackDefaults`, `AssetManifest`) and `AssetRegistry` with `validate()`.

You may make small fixes to these (record them in your report). Don't restructure them.

## 1. Deliverables

### 1.1 `app/src/main/assets/packs/core_proto/v1/manifest.json`

The placeholder pack. Every asset uses `"render": {"type": "procedural"}`; the painter key
defaults to the asset ID, and Phase 2 maps painter keys to drawing code. Base anchors are
normalized 0..1. Use these for all five bases unless a base obviously needs different ones:
`head_top (0.5, 0.25)`, `face_center (0.5, 0.55)`, `eyes (0.5, 0.53)`, `mouth (0.5, 0.66)`,
`prop_hand (0.2, 0.8)`, `body (0.5, 0.9)`. Face-safe zone: `(0.28, 0.38, 0.72, 0.75)`.

Manifest header: `packId "core_proto"`, `version 1`, `minimumRendererVersion 2`, and
`defaults {base_blob, palette_sunny, scene_plain, frame_squircle, eyefam_round, mouthfam_classic}`.
`retired` is empty.

Every asset needs a human `accessibilityLabel` (e.g. "VR headset", "Sunny yellow").

| Category | IDs | Notes |
| --- | --- | --- |
| BASE | `base_blob`, `base_bot`, `base_ghost`, `base_critter`, `base_orb` | |
| PALETTE | `palette_sunny, _peach, _tan, _cocoa, _bubblegum, _sky, _mint, _lavender, _moonlight, _steel, _ember, _lime` | `colors.body` = `AvatarPalette.body[0..11]` in this order. `accent` = `AvatarPalette.theme[i]` with i = 0,2,4,5,3,1,3,0,0,1,2,3 respectively. `outline` = body darkened 35% (same formula as `AvatarRenderer.darken`). Store ints as signed 32-bit ARGB (what Kotlin `Int` serializes to). `name` = label |
| EYE_FAMILY | `eyefam_round`, `eyefam_dot`, `eyefam_visor` (bases: bot; fallback `eyefam_round`), `eyefam_pixel` | |
| MOUTH_FAMILY | `mouthfam_classic`, `mouthfam_fang`, `mouthfam_screen` (bases: bot; fallback `mouthfam_classic`) | |
| SIGNATURE_FEATURE | `feature_ears_cat`, `feature_ears_fox`, `feature_ears_bear`, `feature_muzzle` (all four: bases critter); `feature_antennae` (bases blob, bot, orb); `feature_blush`, `feature_freckles` (`minSizePx 64`, `widgetSafe false`) | ears and antennae `conflictsWith: ["head_helmet"]`; antennae also conflicts with `head_hood` |
| FACE_EYE | `eyes_open, eyes_happy_arc, eyes_sparkle, eyes_closed_line, eyes_half_lid, eyes_tearful, eyes_wide, eyes_angry_slant, eyes_spiral, eyes_narrow, eyes_wink, eyes_side_glance, eyes_dot`, `eyes_scan` (bases: bot) | the same 13 shapes as `domain/AvatarSpec.EyeShape`, plus the bot scan line |
| FACE_BROW | `brows_relaxed, brows_raised, brows_determined, brows_worried, brows_angry, brows_flat` | |
| FACE_MOUTH | `mouth_flat, mouth_smile, mouth_big_grin, mouth_small_o, mouth_frown, mouth_wavy, mouth_smirk, mouth_zip, mouth_open_o` | matches `MouthShape` |
| EXPRESSION_OVERLAY | `overlay_blush, overlay_sparkles (minSizePx 64), overlay_sleep_zs, overlay_tear, overlay_sweat, overlay_anger_mark, overlay_steam, overlay_afk_tag, overlay_dnd_bar, overlay_thermometer, overlay_bandage, overlay_confetti (minSizePx 96)` | |
| FACE_ACCESSORY | `face_glasses_round` (`conflictsWith: ["head_vr_headset"]`), `face_sunglasses` (same conflict), `face_visor` | |
| HEAD_ACCESSORY | `head_headphones, head_vr_headset (occlusion EYES), head_beanie, head_crown, head_wizard_hat, head_cat_ears, head_helmet, head_sleep_cap, head_hood` | |
| BODY_ACCESSORY | `body_hoodie (zIndex 15)`, `body_blanket` | the hood sits behind the head |
| FOREGROUND_PROP | `prop_coffee, prop_tea, prop_controller, prop_book, prop_phone, prop_keyboard, prop_microphone, prop_potion, prop_snack, prop_wrench, prop_sword, prop_gamepad, prop_open_hand` | `anchor: "prop_hand"` |
| SCENE | `scene_plain, scene_cozy_bedroom, scene_desk, scene_campfire, scene_tavern, scene_space_station, scene_rainy_window, scene_neon_city, scene_forest, scene_clouds` | |
| FRAME | `frame_squircle, frame_circle, frame_sticker, frame_pixel` | |
| AVAILABILITY_INDICATOR | `avail_<availability wire name>` for all 8 values | glyphs: available `circle`, text_only `speech_bubble`, call_ok `handset`, gaming `controller`, busy `hourglass`, do_not_disturb `crescent`, afk `clock`, offline `hollow_ring` |
| ACTIVITY_BADGE | `badge_<activity wire name>` for every `ActivityType` except `none` | glyph = activity wire name |
| REACTION_OVERLAY | `reaction_<template wire name>` for all 14 `ReactionTemplate`s | `minSizePx 96` |

**Expressions:** IDs are the `Expression` wire names. Eyes and mouths match today's
`AvatarSpec.face()`. Brows and overlays/extras are listed below.

| id | eyes | brows | mouth | overlays | extras |
| --- | --- | --- | --- | --- | --- |
| neutral | open | — | flat | | |
| happy | happy_arc | relaxed | smile | | blush |
| excited | sparkle | raised | big_grin | | sparkles |
| sleepy | closed_line | relaxed | small_o | sleep_zs | |
| tired | half_lid | flat | flat | | |
| sad | tearful | worried | frown | tear | |
| anxious | wide | worried | wavy | sweat | |
| angry | angry_slant | angry | frown | anger_mark | |
| sick | half_lid | worried | wavy | | thermometer, bandage |
| focused | narrow (bot: `eyes_scan`) | determined | flat | | |
| overwhelmed | spiral | raised | open_o | steam | |
| social | wink | raised | big_grin | | |
| mischievous | side_glance | determined | smirk | | |
| afk | dot | — | flat | afk_tag | |
| dnd | closed_line | relaxed | zip | dnd_bar | |

**Semantics** (keys are `SemanticKey.wire`):

- `mood:<m>` → `expressionId` per `AvatarComposer.expressionFor(m, null)`.
- `availability:<a>` → `availabilityIndicator: avail_<a>`. Also `availability:afk` →
  `expressionId afk`, and `availability:do_not_disturb` → `expressionId dnd`.
- `activity:<t>` (t ≠ none) → `activityBadge: badge_<t>`, plus:
  - vr → `headAccessoryAssetId head_vr_headset`
  - gaming → `propAssetId prop_controller`
  - coding, working → `prop_keyboard`
  - reading → `prop_book`
  - listening → `headAccessoryAssetId head_headphones`
- `intent:want_company` → `prop_open_hand`; `intent:celebrating` → `overlayAssetIds [overlay_confetti]`.

`AssetRegistry(listOf(manifest)).validate()` must return an empty list.

### 1.2 `domain/avatar/AssetPacks.kt`

```kotlin
object AssetPacks {
    val SHIPPED = listOf("packs/core_proto/v1/manifest.json")
    fun registry(read: (path: String) -> String): AssetRegistry
}
```

There's no Android code here. The Android `AssetManager` adapter comes in Phase 2. Tests read
from `app/src/main/assets/` (find the repo root the way `PrivacyVectorsTest.goldenFile()` does).

### 1.3 `domain/avatar/AvatarResolver.kt`: the master plan's §11.2 algorithm (minus drawing)

```kotlin
enum class LayerPriority { BASE, FACE, AVAILABILITY, EXPRESSION, ACTIVITY, SIGNATURE, CONTEXT, SCENE, DECORATION } // §11.3, highest first
enum class DropReason { UNKNOWN_ASSET, INCOMPATIBLE_BASE, CONFLICT, MISSING_REQUIREMENT, OCCLUDED, TARGET_SIMPLIFIED, DISPLACED }
data class ResolvedLayer(val assetId: String, val category: AssetCategory, val z: Int, val priority: LayerPriority, val variant: String? = null)
data class DroppedAsset(val assetId: String, val reason: DropReason, val detail: String? = null)
data class ResolvedAvatar(
    val baseAssetId: String, val paletteAssetId: String, val expressionId: String,
    val layers: List<ResolvedLayer>,      // sorted by z, then assetId
    val dropped: List<DroppedAsset>,      // sorted by assetId, then reason
    val sceneDetail: Boolean,
    val renderKey: String,                // lowercase hex SHA-256
    val accessibilityDescription: String, // e.g. "Critter avatar, sleepy, text only, in VR"
)
class AvatarResolver(private val registry: AssetRegistry) {
    fun resolve(request: AvatarRenderRequest): ResolvedAvatar
}
```

**Step 1, unknown IDs.** Canonicalize every ID through `registry.canonicalId`. An unknown base,
palette, scene, frame, eye family or mouth family falls back to `registry.defaults`. Any other
unknown ID is dropped with `UNKNOWN_ASSET`.

**Step 2, slot selection.** Each single slot takes the first non-null value from these sources,
in order:
1. `VisiblePresence` explicit fields.
2. `styleDna.semanticVisualOverrides` for each key in `presence.semanticKeys()` order.
3. Pack `semantics` in the same key order.
4. The configuration's signature or default.

| Slot | Sources | Priority of the chosen layer |
| --- | --- | --- |
| scene | presence → override → semantic → `defaultSceneAssetId` → defaults.scene | SCENE |
| head / body accessory | presence → override → semantic → signature | ACTIVITY if it came from an `activity:` semantic or override; CONTEXT if from presence or another key; SIGNATURE if signature |
| face accessory | signature only | SIGNATURE |
| prop | presence → override → semantic → `defaultPropAssetId` | ACTIVITY / CONTEXT as above |
| availability indicator | override → semantic for the availability key | AVAILABILITY |
| activity badge | semantic for the activity key | ACTIVITY |
| frame | `defaultFrameAssetId` → defaults.frame | BASE |

If a temporary choice occupies a slot that a signature would have filled, record the signature
as dropped with `DISPLACED`. The saved identity itself is never modified.

**Step 3, expression.** Take the first of: presence `expressionId`; override `expressionId` in key
order; `mood:` semantic; `availability:` semantic; `restingExpressionId`; `"neutral"`. Then take
the parts for the base (`partsFor`). An override's `eyesAssetId` replaces the eyes. The eye layer
gets `variant` = the resolved eye family (falling back to `eyefam_round` when the family is
incompatible with the base); the mouth gets the mouth family. Overlays are added at EXPRESSION
priority, extras at DECORATION. Overrides' `overlayAssetIds` and semantic overlays go in at
CONTEXT priority. Reactions go in at DECORATION.

**Step 4, base compatibility.** Any incompatible layer tries its `fallback` chain (same category,
compatible); if nothing fits, drop it with `INCOMPATIBLE_BASE`.

**Step 5, conflicts.** Sort candidates by `(priority, z, id)` and accept them in order. A candidate
that conflicts with an already-accepted layer tries its fallback chain; if nothing fits, drop it
with `CONFLICT` and `detail` = the winner's ID.

**Step 6, requirements.** Repeat until stable: drop any layer whose `requires` aren't all accepted
(`MISSING_REQUIREMENT`).

**Step 7, occlusion.** If any layer has occlusion `EYES`, drop the eyes and brows (`OCCLUDED`).
`FULL` also drops the mouth.

**Step 8, target simplification** (§13.4). Drop with `TARGET_SIMPLIFIED`:

| Target | Rules |
| --- | --- |
| all | assets with `minSizePx > sizePx`; below 64 px, any `widgetSafe == false` asset |
| COMPACT_WIDGET, CIRCLE_WIDGET, FRIEND_TILE, NOTIFICATION | keep only one of {activity badge, prop}, preferring the badge; drop body accessory, DECORATION, reactions |
| STANDARD_WIDGET | drop DECORATION; keep at most 1 reaction |
| LARGE_WIDGET | keep at most 1 reaction |
| PROFILE, SHARE_CARD | keep at most 3 reactions |

`sceneDetail` = `sizePx >= 96` (LOW_DETAIL needs ≥ 256) and the preference isn't NONE; with
NONE the scene becomes `defaults.scene`.

**Step 9, render key.** Hex SHA-256 (`java.security.MessageDigest`) of
`rendererVersion|packVersions|target|sizePx|contrast|a11y|canonicalJson(config)|canonicalJson(presence)|reactions`.
Canonical JSON uses `IdlJson`, with `semanticVisualOverrides` re-inserted in sorted key order so
map order can't change the key.

**Determinism:** no randomness, no reliance on hash-map iteration order, and the same request
always yields an equal `ResolvedAvatar`.

### 1.4 `domain/avatar/CompatibilityEngine.kt`

```kotlin
data class ConfigIssue(val assetId: String, val problem: String)
class CompatibilityEngine(private val registry: AssetRegistry) {
    fun validate(config: AvatarConfiguration): List<ConfigIssue>   // unknown IDs, base incompatibility, conflicts among identity assets
    fun sanitize(config: AvatarConfiguration): Pair<AvatarConfiguration, List<ConfigIssue>>
}
```

`sanitize` produces a configuration with no issues. When identity assets conflict, keep the
higher-priority one: features (BASE) beat accessories (SIGNATURE). Prefer an asset's fallback
over removing it. The resolver should reuse this engine's conflict and fallback logic rather
than duplicate it.

### 1.5 `domain/avatar/LegacyAvatarMigration.kt`

```kotlin
object LegacyAvatarMigration {
    fun migrate(v1: app.idl.domain.AvatarConfig): AvatarConfiguration       // then sanitize
    fun presence(view: app.idl.domain.PresenceView): VisiblePresence        // v1 visuals → asset IDs
    fun presence(resolved: app.idl.domain.ResolvedPresence): VisiblePresence
}
```

| v1 field | v2 |
| --- | --- |
| baseForm | HUMAN→`base_orb`; BLOB→`base_blob`; ROBOT→`base_bot`; GHOST→`base_ghost`; CAT→`base_critter` + `feature_ears_cat`; FOX→`base_critter` + `feature_ears_fox` + `feature_muzzle`; BEAR→`base_critter` + `feature_ears_bear`; ALIEN→`base_blob` + `feature_antennae`; PIXEL→`base_bot` + `eyefam_pixel` + `frame_pixel` (overrides frameStyle) |
| bodyColor | palette whose `colors.body` equals it, else `palette_sunny`. `themeColor` is dropped (the palette provides the accent); note this in the KDoc |
| faceStyle | CLASSIC → nothing; BLUSHY → `feature_blush`; FRECKLES → `feature_freckles` |
| expression | `restingExpressionId` = wire name |
| headAccessory / faceAccessory / bodyAccessory / handProp / scene | `head_<wire>`, `face_glasses_round` / `face_sunglasses`, `body_<wire>`, `prop_<wire>` (PIZZA → `prop_snack`), `scene_<wire>` (PLAIN_GRADIENT → `scene_plain`; SPACE_STATION → `scene_space_station`; DESK_SETUP → `scene_desk`; DUNGEON_TAVERN → `scene_tavern`; CLOUDSCAPE → `scene_clouds`); NONE → null |
| frameStyle | CIRCLE → `frame_circle`, SQUIRCLE → `frame_squircle` |

`presence(PresenceView)` copies only non-null fields. For visuals, map the composed
`avatar`/`restingAvatar` difference: a prop, scene, head or body accessory that differs from
`restingAvatar` becomes the explicit presence field. The expression is set only when
`view.mood != null`, which preserves today's derived-leak rule.

## 2. Tests (JUnit, `app/src/test/java/app/idl/domain/avatar/`)

Each item below is a required test or test group:

1. **Pack:** core_proto loads and `validate()` is empty; every `Mood`, `Availability` and
   non-none `ActivityType` resolves; every expression resolves on every base with no drops of
   face parts.
2. **Expression mapping per base:** focused on bot uses `eyes_scan`, other bases `eyes_narrow`.
   Every expression produces distinct (eyes, brows, mouth, overlays) on at least one base.
3. **Slot precedence:** presence beats override beats semantic beats signature, for every slot
   in the step 2 table.
4. **Signature persistence:** signature glasses survive "sick" (no conflict); glasses drop with
   `CONFLICT` under an activity:vr headset and return when the activity ends; the saved
   configuration is unchanged in both cases.
5. **Displacement:** a signature beanie plus activity:vr gives the headset, with the beanie
   `DISPLACED`.
6. **Conflicts and fallbacks:** ears plus helmet (signature) means the helmet is dropped, by
   priority; `eyefam_visor` on a non-bot base falls back to `eyefam_round`; conflicts are
   detected when only one side declares them.
7. **Requirements:** synthetic manifest in the test; a layer whose requirement is missing is
   dropped, transitively.
8. **Occlusion:** a VR headset removes eyes and brows but keeps the mouth.
9. **Target simplification:** a full table test per `RenderTarget` (compact keeps exactly one of
   badge/prop with the badge preferred, no reactions/extras/body; standard has no extras and ≤1
   reaction; profile has ≤3 reactions; `minSizePx` respected).
10. **Privacy (D-24):** with `mood == null`, no `mood:*` override or semantic is applied
    (expression = resting, no mood overlays). The same for activity (no headset or badge),
    intent and availability. Write one test per kind.
11. **Determinism and render key:** equal requests give equal results and keys; reordering the
    override map doesn't change the key; changing any one input (each field of the request,
    and pack version) changes the key.
12. **Unknown and retired IDs:** a synthetic retired mapping is followed; an unknown base falls
    back to the default; an unknown prop is dropped with `UNKNOWN_ASSET`.
13. **Migration:** every combination of v1 `BaseForm × HeadAccessory × FaceAccessory ×
    BodyAccessory × FaceStyle` (with prop/scene/expression cycled) migrates to a configuration
    with no `validate()` issues and resolves with no `UNKNOWN_ASSET`. Specific table rows are
    asserted for all 9 bases.
14. **Sanitize:** idempotent (`sanitize(sanitize(x)) == sanitize(x)`).

Existing tests must keep passing (`scripts/check.sh`).

## 3. Acceptance criteria

- [ ] All deliverables in §1 exist and match the specs above. Deviations are explained in the
      report.
- [ ] `scripts/check.sh` passes. Test count rises from 78 by at least 40.
- [ ] No Android imports anywhere in `domain/`.
- [ ] No changes outside `domain/avatar/`, `assets/packs/`, tests, and `docs/handoff/reports/`
      (plus small fixes to the two existing files, recorded in the report).
- [ ] KDoc on every public type explaining *why* where it isn't obvious; no commented-out code.
- [ ] Checkpoint report written per `AGENTS.md`.

## 4. Out of scope (later phases; don't start)

Renderer changes and painter mapping (Phase 2), Android asset loading (Phase 2), Room or server
schema (Phase 3), any UI (Phases 4–6), new dependencies.

## 5. Review checklist (what Claude will check)

- Invariants 2 (no visual leaks) and 8 (determinism) in `AGENTS.md`, by reading the code and
  adding adversarial tests.
- The slot-precedence table and the target table, implemented exactly.
- Manifest completeness against §1.1, by script.
- Tests assert behaviour, not just non-null results; no tests weakened to pass.
- `git diff main...avatar-creator --stat` stays inside the allowed paths.
