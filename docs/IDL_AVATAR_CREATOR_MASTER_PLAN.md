# iDL Avatar Creator — Product and Technical Master Plan

**Project:** iDL — Friends at a Glance  
**Document purpose:** Implementation-ready specification for the iDL avatar creator, renderer, asset system, and widget-oriented visual identity.  
**Audience:** Claude Code, Cursor, Android engineers, backend engineers, UI/UX designers, 2D artists, technical artists.  
**Status:** Planning specification for Android-first MVP.

---

## 1. Executive Summary

iDL is an ambient social-presence application for close friends. Every user has a customizable, expressive avatar called an **iDL**. Friends can place one another’s iDL avatars on Android home-screen widgets. When a user changes their mood, availability, activity, accessories, prop, or scene, their friends see the updated visual state at a glance.

The avatar system is not cosmetic decoration. It is the product’s primary communication surface and strongest potential retention mechanism.

The design target is not a standard Unicode emoji picker, a Bitmoji clone, or a detailed 3D character creator. Build an original, widget-first, highly legible **2D modular character system**:

- Users make a recognizable identity in under two minutes.
- Friends identify each other at 48×48 pixels.
- Status changes alter expression and context without destroying identity.
- Asset packs create a sustainable cosmetic monetization layer without gating social utility.
- Rendering is deterministic, local-cache friendly, and compatible with Android widget constraints.

### Product thesis

> A user should be able to look at a tiny home-screen avatar and immediately understand: who this friend is, how they are doing, whether they are available, and what they are broadly doing.

---

## 2. Goals and Non-Goals

### 2.1 Goals

1. Create a visual system users want to keep visible on their home screens.
2. Ensure recognizability, mood, availability, and activity are understandable at small widget sizes.
3. Make avatar creation fast for casual users and deep enough for enthusiasts.
4. Separate persistent identity from temporary presence expression.
5. Support a scalable, versioned asset-pack pipeline.
6. Keep the canonical avatar state compact, deterministic, and platform-neutral.
7. Support privacy-aware visual state changes.
8. Permit future integrations such as VRCQ or a desktop presence bridge without coupling them to visual rendering.
9. Support future paid cosmetics without making basic communication states paid features.
10. Avoid dependency on third-party avatar systems or rendering rights.

### 2.2 Non-Goals

Do not build these in the initial avatar creator:

- Photorealistic human recreation.
- A Bitmoji, Memoji, ZEPETO, VRoid, or Ready Player Me clone.
- 3D humanoid rigging, skeletal animation, blendshapes, or Unity runtime rendering.
- Arbitrary AI-generated profile art as canonical avatars.
- User-uploaded visual assets in the MVP.
- Full freeform color pickers for every component.
- An unbounded asset combination system that permits clipping and unreadable results.
- Complex widget animation.
- A marketplace before asset versioning, moderation, licensing, and quality validation exist.

---

## 3. Visual Product Strategy

### 3.1 Recommended art direction

Use **Expressive Pocket Avatars**: original, soft, stylized, mostly non-human or semi-abstract mascot characters designed for very small display sizes.

Target traits:

- Premium 2D sticker-art quality.
- Rounded, distinctive silhouettes.
- Large readable eyes and clear mouth shapes.
- Restrained soft shading rather than flat default emoji styling.
- Bold outlines or controlled sticker outlines where needed for wallpaper contrast.
- One dominant face and one dominant prop at widget size.
- Calm, low-detail scenes that never compete with the face.
- Warm, playful, inclusive, slightly strange, and collectible.
- Not childish, not corporate, not photorealistic, not visually copied from Bitmoji/Memoji, and not dependent on anime conventions.

### 3.2 Why custom iDL art is necessary

Native Unicode emoji should only be used for early internal prototypes.

Problems with using actual system emoji as the final avatar system:

- Rendering varies by Android vendor and operating-system version.
- The application does not own a durable visual identity.
- Layered customization is inconsistent and often visually weak.
- Asset quality, lighting, outline, perspective, and animation cannot be controlled.
- Widget output becomes indistinguishable from generic launcher iconography.
- Cosmetic packs and original IP become structurally weaker.

The product should retain emoji’s semantic speed—sleepy, coffee, controller, blanket, VR headset—but translate those ideas into a proprietary visual language.

### 3.3 Widget-first hierarchy

Every render must communicate information in this order:

1. **Who is this?** Persistent base silhouette, palette, and signature accessory.
2. **How are they?** Expression and mood effects.
3. **Are they available?** Availability ring, tab, icon, or shape cue.
4. **What are they doing?** One primary activity or context prop/badge.
5. **Any secondary detail?** Background, note, reaction overlay, decorative frame.

If a visual feature does not strengthen one of these answers, reserve it for larger screens or remove it.

---

## 4. Avatar State Model

### 4.1 Fundamental render equation

```text
iDL Render = Persistent Identity + Temporary Expression + Temporary Context + Display Variant
```

| Component | Update cadence | Purpose |
|---|---:|---|
| Persistent identity | Rare | Establishes who the avatar belongs to |
| Temporary expression | Frequent | Communicates mood and emotional state |
| Temporary context | Frequent | Communicates activity, intent, and availability |
| Display variant | Per surface | Adapts visual output to widget/profile/share-card size |

### 4.2 Persistent identity

Persistent identity should remain recognizable despite mood and status changes.

```text
Base archetype
Palette family
Signature feature
Signature accessory
Default frame family
Default scene family
Style DNA
```

Examples:

```text
Main iDL:
- Rounded bot
- Midnight cyan palette
- Round glasses
- Headphones
- Holographic frame

Cozy night variant:
- Same rounded bot
- Same glasses
- Blanket and tea overlay
- Rainy window scene
- Low-light palette

VR persona:
- Same rounded bot
- Same glasses where compatible
- VR headset
- Neon scene
- VR activity badge
```

### 4.3 Temporary expression

Expression must map semantic mood states into compatible visual combinations.

```text
Eyes
Brows
Mouth
Expression overlays
Optional body/silhouette adjustment
```

Users should select semantic states such as `sleepy`, `focused`, or `mischievous`, not manually assemble every eye/brow/mouth combination for each status update.

### 4.4 Temporary context

Context reflects current availability, intent, and activity.

```text
Availability indicator
Activity badge
Contextual prop
Optional scene override
Optional temporary accessory
Optional reaction overlay
Status note outside avatar art when space allows
```

### 4.5 Display variants

A full profile image and a 48×48 widget render must not receive the same visual complexity.

```text
Compact widget: face, availability, one badge; aggressively omit low-priority detail.
Standard widget: face, availability, one prop/badge, simple scene.
Large widget: face, availability, prop, scene, optional short note.
Profile: full composition, decorative scene, reactions, richer details.
Share card: high-resolution composition plus display name and optional caption.
```

---

## 5. Base Archetypes

### 5.1 MVP base set

Launch with **five high-quality base families**, then expand only when art quality and asset compatibility are stable.

| Base | Purpose | Widget strengths |
|---|---|---|
| Blob | Universal, soft, expressive mascot | Excellent face readability |
| Bot | Technical, VR, developer, sci-fi identity | Strong visor and screen-face variants |
| Ghost | Cozy, sleepy, AFK-friendly | Distinct floaty silhouette |
| Critter | Cat/fox/rabbit/bear family | Natural attachment and collection appeal |
| Orb | Minimal, mature alternative to overt cuteness | Clean, elegant, highly legible |

### 5.2 Expansion base set

Add only after the core renderer and asset compatibility engine are validated.

| Base | Product role |
|---|---|
| Gremlin | Chaotic, gamer, mischievous personality |
| Astronaut | Strong VR and sci-fi identity |
| Pixel sprite | Retro style family and small-scale readability |
| Plant creature | Calm/cozy identity |
| Slime | High expression range and seasonal skins |
| Familiar | Fantasy/occult themed identity |

### 5.3 Base design requirements

Every base must:

- Be identifiable by silhouette at 48×48 px.
- Support the standard expression vocabulary.
- Have known anchors for headwear, facewear, props, and effects.
- Define safe face, eye, mouth, and accessory zones.
- Work against both dark and bright wallpapers.
- Support an accessibility-safe high-contrast render variant.
- Ship with a visual QA sheet at all target sizes.

---

## 6. Customization Taxonomy

### 6.1 Persistent identity modules

| Module | Example values | Role |
|---|---|---|
| Base | Blob, bot, ghost, critter, orb | Primary silhouette |
| Palette | Midnight cyan, sunset coral, moss, lavender, grayscale | Recognition and personal preference |
| Eye family | Dot, oval, visor, star, pixel, mono-eye | Face personality |
| Mouth family | Smile, flat, tiny-o, fang, screen glyph | Face personality |
| Signature feature | Ears, antennae, horns, leaf, tail hint, glow | Differentiation |
| Signature accessory | Glasses, beanie, headphones, hood, crown | Fast recognition |
| Frame family | Plain, sticker, holo, pixel, rune, industrial | Product/style flavor |
| Style DNA | Cozy tech, fantasy, retro, minimal, dreamy | Governs suggestions and default mappings |

### 6.2 Temporary expression modules

| Module | Examples |
|---|---|
| Eye state | Open, happy arc, half-lidded, closed, wide, tired, focused, spiral, heart, glitch, tearful |
| Brow state | None, raised, relaxed, determined, worried, angry, asymmetric |
| Mouth state | Neutral, smile, grin, tiny-o, flat, frown, laugh, fang, pout |
| Effect overlay | Sweat drop, blush, tear, sparkle, sleep Zs, steam puff, bandage, glitch, stars |
| Pose/silhouette effect | Bounce, slump, float, lean, vibration lines; usually in-app only |

### 6.3 Wearables and props

Organize every asset into a slot with explicit anchors and conflict rules.

```text
Head zone:
- Hats
- Headphones
- VR headsets
- Horns
- Ears
- Crowns
- Hoods
- Helmets

Face zone:
- Glasses
- Sunglasses
- Masks
- Visors
- Bandages
- Monocles
- Face paint

Body/front zone:
- Scarves
- Hoodies
- Capes
- Chains
- Badges
- Stickers

Foreground/hand zone:
- Coffee
- Tea
- Phone
- Controller
- Book
- Keyboard
- Potion
- Microphone
- Snack
- Tool/wrench
- Sword
- Open hand / invitation gesture
```

### 6.4 Core semantic props

Props must first communicate useful social context, then decoration.

| Prop | Primary semantic role |
|---|---|
| Coffee / tea | Waking, tired, cozy, working |
| Controller | Gaming |
| VR headset | In VR |
| Keyboard / laptop | Coding, working, focused |
| Phone | Text only / responding later |
| Book | Reading / unavailable |
| Blanket | Sleepy, sick, low energy |
| Microphone | Call okay, performing, streaming |
| Potion | Fantasy flavor, recovery, chaos |
| Snack / pizza | Movie night, casual hangout |
| Open hand | Invite me / want company |
| Wrench | Building, fixing, tinkering |

### 6.5 Scenes

Scenes must remain subordinate to the face.

```text
Neutral:
- Solid gradient
- Soft halo
- Sticker outline
- Geometric texture

Cozy:
- Rainy window
- Bedroom glow
- Campfire
- Night sky

Activity:
- Desk
- Neon arcade
- Space cockpit
- Tavern

Mood:
- Cloudy sky
- Sparkle field
- Forest
- Low-light vignette
```

At widget size, scenes are simplified color, lighting, and silhouette fields. Do not render detailed miniature environments that create visual noise.

---

## 7. Mood, Availability, and Status Mapping

### 7.1 Core state vocabulary

```text
Mood:
- neutral
- good
- happy
- excited
- sleepy
- tired
- low_energy
- stressed
- anxious
- sad
- sick
- focused
- chaotic
- social
- overwhelmed

Availability:
- available
- text_only
- call_ok
- gaming
- busy
- do_not_disturb
- afk
- offline

Intent:
- no_preference
- invite_me
- want_company
- need_memes
- ask_later
- check_in
- celebrating

Activity:
- none
- working
- coding
- gaming
- vr
- watching
- listening
- reading
- traveling
- exercising
- sleeping
- custom
```

### 7.2 Status visual mapping

| State | Visual shorthand |
|---|---|
| Happy | Upturned eyes/mouth, small sparkle |
| Sleepy | Closed/half-lidded eyes, sleep Zs, tea/blanket option |
| Busy | Determined brows, focus lines, desk/keyboard option |
| In VR | Clear headset silhouette, VR badge |
| Gaming | Controller prop, game badge |
| Text only | Speech bubble or phone icon; availability ring variation |
| Do not disturb | Crescent, shield, closed-eye state; avoid aggressive red-only communication |
| Sick | Bandage/thermometer option, muted palette; never require disclosure |
| Need company | Open-hand/campfire motif, gentle outward-wave effect |
| Celebrating | Confetti/starburst option |
| AFK | Dimmed/floating treatment and away/clock marker |

### 7.3 Precedence behavior

Manual status must override automated signals by default.

```text
Manual status: 100
VRCQ bridge: 80
Desktop bridge: 70
Verified external integration: 60
Android local signal: 40
Inferred/default state: 10
```

Rules:

1. The highest non-expired source wins for a field.
2. Manual mood, availability, intent, and note override automation by default.
3. An automated integration may contribute an activity badge without replacing manual mood.
4. The user may disable any source.
5. A state expires automatically and must render as stale or disappear truthfully.
6. Invisible mode prevents friend-facing presence publication.

---

## 8. Creator UX

### 8.1 Two-tier creator model

Implement two modes rather than one giant configuration interface.

#### Quick Creator

For first run and casual users.

```text
1. Pick your creature.
2. Pick a vibe.
3. Pick a palette.
4. Pick one signature accessory.
5. Preview on an Android widget.
6. Save.
```

Target completion time: under two minutes.

#### Avatar Lab

For users who want control.

```text
Identity
Face
Colors
Wearables
Props
Scenes
Frames
Saved looks
Preview modes
Accessibility
```

### 8.2 First-run creation flow

1. **Choose a base:** show five high-quality archetypes, not a vast grid.
2. **Choose a vibe:** Cozy, Chaotic, Techy, Cute, Cool, Fantasy, Spooky, Retro, Minimal, Dreamy.
3. **Choose a palette:** use curated palette families with accessible contrast checks.
4. **Choose a signature:** recommend one accessory or feature that will become recognizable.
5. **Preview status states:** show neutral, sleepy, busy, VR, and happy examples.
6. **Preview widget sizes:** show 1×1, 2×2, dark wallpaper, bright wallpaper, and circle-widget context.
7. **Save:** generate a deterministic `AvatarConfiguration` and local render cache.

### 8.3 Widget preview is mandatory

The avatar editor must preview the current configuration in real widget contexts.

```text
48×48 compact tile
64×64 friend tile
96×96 standard widget portrait
128×128 large widget portrait
Circle/group compact context
Dark wallpaper simulation
Bright wallpaper simulation
High-contrast simulation
```

Provide useful, specific feedback:

```text
Your headwear and background have low contrast at compact size.
Try a lighter outline, a darker scene, or a different frame.
```

### 8.4 Avatar Lab quality-of-life features

```text
- Undo and redo.
- Randomize the current category.
- Full “Surprise me” randomization constrained by compatibility rules.
- “Keep identity, change vibe.”
- Favorites.
- Recently used.
- Save named looks.
- Copy a look and edit it.
- Widget-only preview.
- Search/filter by collection, palette, item type, owned/free status, and mood.
- Clear accessibility labels for all visual assets.
```

### 8.5 Identity slots

Support one active identity in MVP. Design the schema to support multiple saved slots later.

Example future slots:

```text
Main iDL
Work Mode
VR Persona
Cozy Night
Chaos Goblin
Holiday Variant
```

A slot preserves identity anchors while allowing curated changes in context and scene.

---

## 9. Status Deck

The **Status Deck** is separate from Avatar Lab. It is the high-frequency state changer.

### 9.1 Entry points

```text
- Home screen inside the app
- User’s own iDL widget
- Launcher long-press shortcut
- Quick Settings tile
- Notification action
- Optional platform-safe lock-screen shortcut
```

### 9.2 Status Deck requirements

- One or two taps to apply a frequent state.
- Presets modify semantic presence plus visual context.
- Every status includes an expiration selector.
- Every status includes an audience selector or defaults to the user’s policy.
- Presets can be edited and saved.
- A manual change must not permanently mutate persistent identity unless the user explicitly saves it.

### 9.3 Initial presets

| Preset | Visual state | Presence state |
|---|---|---|
| At Desk | Focused face, keyboard, desk scene | `mood=focused`, `activity=coding/working`, `availability=busy` |
| In VR | Headset, neon frame, engaged eyes | `activity=vr`, `availability=gaming`, optional `intent=invite_me` |
| Gaming | Controller, active face | `activity=gaming`, `availability=gaming` |
| Low Battery Human | Blanket, tea, half-lidded eyes | `mood=tired`, `availability=text_only` |
| Text Only | Phone/soft speech icon | `availability=text_only` |
| Open to Talk | Warm expression, open hand | `availability=available`, `intent=want_company` |
| Do Not Disturb | Calm closed eyes, shield/crescent | `availability=do_not_disturb` |
| Need Company | Campfire/open seat motif | `intent=want_company`, `availability=available` |
| Chaos Mode | Mischievous face, unusual prop | `mood=chaotic`, `intent=need_memes` |
| AFK | Dim/float effect, away marker | `availability=afk` |

---

## 10. Asset System

### 10.1 Core principle

Do not hardcode every asset or combination in Android application code. Use versioned asset packs with explicit metadata.

### 10.2 Asset categories

```text
base
palette
face_eye
face_brow
face_mouth
expression_overlay
head_accessory
face_accessory
body_accessory
foreground_prop
scene
frame
availability_indicator
activity_badge
reaction_overlay
animation_metadata
```

### 10.3 Asset metadata requirements

Each asset requires metadata beyond its visual file.

```text
Asset ID
Asset pack ID
Asset pack version
Asset category
Slot
Collection
Availability tier
Compatible base types
Compatible palettes or style families
Conflicting assets
Required assets
Z-index
Anchor ID
Scale range
Rotation allowance
Face occlusion level
Widget-safe flag
Target-size simplification variant
Accessibility label
Content category
License / attribution metadata
Render version
```

### 10.4 Example asset manifest

```json
{
  "packId": "core_launch",
  "version": 1,
  "minimumRendererVersion": 1,
  "assets": [
    {
      "id": "base_blob_01",
      "type": "base",
      "slot": "base",
      "file": "base_blob_01.webp",
      "widgetSafe": true,
      "supportedSizes": [48, 64, 96, 128, 256, 512]
    },
    {
      "id": "headset_vr_01",
      "type": "head_accessory",
      "slot": "head",
      "file": "headset_vr_01.webp",
      "compatibleBases": ["blob", "bot", "critter"],
      "conflictsWith": ["wizard_hat_01", "large_horns_02"],
      "anchor": "head_center",
      "zIndex": 70,
      "requiresFaceMask": true,
      "widgetSafe": true,
      "accessibilityLabel": "VR headset"
    }
  ]
}
```

### 10.5 Asset pack rules

1. A pack version must never silently break an existing saved avatar.
2. Asset removals require a fallback mapping.
3. Every asset must be validated against supported bases and render targets.
4. Every paid/locked asset must have a server-authoritative entitlement check before profile publication.
5. The local device should cache assets needed for current friends’ visible avatars.
6. Asset files must never encode sensitive profile or presence data.

---

## 11. Compatibility and Wardrobe Intelligence

A robust avatar creator prevents bad combinations before they render.

### 11.1 Required compatibility rules

```text
- A VR headset may hide incompatible eyewear or use a visor-compatible glasses variant.
- Large antlers, ears, or horns may disable tall hats.
- A blanket may conceal lower-body/front accessories.
- A full face mask must select an eye-only or mask-compatible expression variant.
- “Sick” may add a bandage but must not remove a user’s signature glasses unless the combination is impossible.
- “In VR” adds a temporary headset overlay; it must not permanently overwrite saved identity.
- Compact widgets remove low-priority scenery, particles, and reaction effects before reducing face readability.
- Asset metadata must define fallbacks for unavailable or incompatible choices.
```

### 11.2 Resolution algorithm

```text
1. Load persistent AvatarConfiguration.
2. Load active PresenceState and verify it is valid/non-expired.
3. Select expression mapping for the active mood.
4. Select context mapping for availability, intent, and activity.
5. Merge persistent, expression, and context layers.
6. Validate slot conflicts and required dependencies.
7. Apply fallback asset substitutions where configured.
8. Apply display-variant simplification rules for requested target size.
9. Render layers in z-index order.
10. Cache output under deterministic render key.
```

### 11.3 Conflict resolution priority

```text
1. Safety/privacy visibility constraints
2. Persistent base silhouette
3. Face readability
4. Availability indicator
5. Mood expression
6. Activity badge
7. Signature accessory
8. Temporary contextual prop
9. Scene
10. Decorative overlays and reactions
```

---

## 12. Data Model

### 12.1 AvatarConfiguration

```kotlin
data class AvatarConfiguration(
    val id: String,
    val userId: String,
    val baseAssetId: String,
    val paletteAssetId: String,
    val eyeFamilyAssetId: String?,
    val mouthFamilyAssetId: String?,
    val signatureFeatureAssetIds: List<String>,
    val signatureHeadAccessoryAssetId: String?,
    val signatureFaceAccessoryAssetId: String?,
    val signatureBodyAccessoryAssetId: String?,
    val defaultPropAssetId: String?,
    val defaultSceneAssetId: String?,
    val defaultFrameAssetId: String?,
    val styleDna: StyleDna,
    val renderVersion: Int,
    val createdAt: Instant,
    val updatedAt: Instant
)
```

### 12.2 StyleDNA

```kotlin
data class StyleDna(
    val styleFamily: StyleFamily,
    val paletteFamily: String,
    val expressionIntensity: ExpressionIntensity,
    val sceneDetailPreference: SceneDetailPreference,
    val motionPreference: MotionPreference,
    val signatureAccessoryIds: List<String>,
    val semanticVisualOverrides: Map<PresenceSemanticKey, VisualOverride>
)
```

Example:

```json
{
  "styleFamily": "cozy_tech",
  "paletteFamily": "night_cyan",
  "expressionIntensity": "medium",
  "sceneDetailPreference": "low_detail",
  "motionPreference": "subtle",
  "signatureAccessoryIds": ["round_glasses", "headphones"],
  "semanticVisualOverrides": {
    "sleepy": {
      "props": ["tea"],
      "overlays": ["sleep_zs"],
      "eyes": "half_lidded"
    },
    "vr": {
      "headAccessory": "neon_vr_headset",
      "availabilityIndicator": "purple_signal_ring"
    }
  }
}
```

### 12.3 RenderRequest

```kotlin
data class AvatarRenderRequest(
    val avatarConfiguration: AvatarConfiguration,
    val presenceState: PresenceState?,
    val reactionOverlays: List<ReactionOverlay>,
    val target: RenderTarget,
    val sizePx: Int,
    val wallpaperContrastMode: WallpaperContrastMode,
    val accessibilityMode: AccessibilityRenderMode,
    val rendererVersion: Int
)
```

### 12.4 RenderTarget

```text
COMPACT_WIDGET
STANDARD_WIDGET
LARGE_WIDGET
CIRCLE_WIDGET
PROFILE
SHARE_CARD
NOTIFICATION
```

### 12.5 Render key

```text
renderKey = SHA-256(
  avatarConfiguration.version +
  activePresenceState.version +
  reactionOverlayVersions +
  target +
  sizePx +
  wallpaperContrastMode +
  accessibilityMode +
  rendererVersion
)
```

---

## 13. Rendering Architecture

### 13.1 MVP rendering recommendation

Implement a deterministic 2D layered renderer.

```text
Asset source: SVG or high-resolution raster master art.
Application assets: transparent WebP or PNG layer exports.
In-app rendering: Compose Canvas, bitmap composition, or controlled custom draw pipeline.
Widget output: cached flattened bitmap at target widget size.
Server source of truth: compact AvatarConfiguration + PresenceState JSON.
```

Jetpack Compose provides custom graphics APIs using `Canvas` and `DrawScope`, including coordinate transforms and controlled drawing operations. Use this to build a simple, testable 2D compositing path rather than importing a 3D engine. [web:42]

### 13.2 Layer order

```text
0. Base background / transparent canvas
1. Scene background
2. Base silhouette
3. Palette/shading layer
4. Signature body/background features
5. Eye layer
6. Brow layer
7. Mouth layer
8. Expression effects
9. Face accessory
10. Head accessory
11. Body accessory
12. Foreground prop
13. Availability indicator/frame
14. Activity badge
15. Reaction overlay
16. Optional short display text outside avatar bounds
```

### 13.3 Target sizes

```text
48×48 px       Compact widget / dense circle
64×64 px       Friend tile
96×96 px       Standard solo widget
128×128 px     Large solo widget
256×256 px     Profile and detail view
512×512 px     Share-card cache
1024×1024 px   Optional export/master-preview target
```

### 13.4 Simplification rules

| Render target | Must include | May omit first |
|---|---|---|
| 48 px compact | Base, face, availability, one badge | Scene detail, particles, reaction effect |
| 64 px tile | Base, face, availability, one prop/badge | Secondary accessories, scene detail |
| 96–128 px widget | Base, face, availability, prop, simple scene | Decorative particles |
| 256 px profile | Full core composition | Only extreme nonessential effects |
| Share card | Full composition plus optional copy | Nothing unless performance constrained |

### 13.5 Caching rules

1. Cache flattened outputs locally by render key.
2. Invalidate cache when asset-pack version, avatar configuration, presence state, target size, or renderer version changes.
3. Retain most recently used compact widget sizes aggressively.
4. Render the friend’s last known avatar locally while fresh presence/state sync is pending.
5. Never represent stale status as live; surface staleness through subtle non-intrusive UI outside the avatar when appropriate.
6. Pre-render common widget variants after a friend state update when the device is charging or otherwise within responsible background-work policy.

---

## 14. Android Implementation Requirements

### 14.1 Recommended Android stack

```text
Kotlin
Jetpack Compose
Material 3
Jetpack Glance for widgets where suitable
AppWidget/RemoteViews escape hatch where Glance constraints require it
Room for local cache
DataStore for preferences
WorkManager for persistent reconciliation and cache maintenance
Kotlin Coroutines and Flow
```

### 14.2 Widget constraints

Do not assume arbitrary Compose UI runs inside home-screen widgets.

Widgets should receive final bitmap output and a minimal set of text/action elements. The widget surface must be treated as a constrained, glanceable medium.

```text
- Render avatar art into widget-safe images.
- Keep tap targets simple and predictable.
- Use push-triggered updates for normal changes.
- Use WorkManager for durable reconciliation, retries, and cleanup.
- Do not aggressively poll external platforms.
- Support graceful offline / last-known state output.
```

### 14.3 Accessibility requirements

The creator is image-heavy; therefore accessibility cannot be retrofitted later.

```text
- Every selectable asset needs an accessible name and semantic state.
- Every swatch needs a color/name description.
- Every preview must expose meaningful content descriptions.
- Do not use color as the only availability cue.
- Support larger font and display scaling without breaking essential controls.
- Provide high-contrast render previews.
- Respect reduced-motion preferences for in-app animation.
- Ensure keyboard/navigation and TalkBack focus order are coherent.
```

Jetpack Compose supports semantic properties such as content descriptions and state descriptions for accessibility services; implement these for all art controls and visual-preview states. [web:43]

### 14.4 Adaptive UI requirements

The Avatar Lab must adapt to phones, tablets, foldables, orientation changes, and multi-window contexts.

```text
Compact: bottom sheet/category picker plus large preview.
Medium: two-pane preview and controls.
Expanded: persistent category rail, asset grid, multi-context preview panel.
```

Material 3 adaptive layouts are intended to adjust layouts across window sizes and device postures. Use them where their complexity is justified. [web:45][web:47]

---

## 15. Asset Production Pipeline

### 15.1 Art production workflow

```text
1. Visual direction board and approved base silhouettes.
2. Base-family turnaround and anchor map.
3. Expression sheet per base.
4. Accessory concept and compatibility map.
5. High-resolution source art creation.
6. Layer export at standard source resolution.
7. Asset metadata authoring.
8. Automated layer/anchor/size validation.
9. Render regression test sheet generation.
10. Human visual QA at all target sizes and wallpaper conditions.
11. Pack signing/versioning.
12. Distribution in app bundle or approved asset-pack delivery mechanism.
```

### 15.2 Artist deliverables per base

```text
- Base silhouette asset.
- Face-safe area guide.
- Eye anchor positions.
- Brow anchor positions.
- Mouth anchor positions.
- Headwear anchor positions.
- Facewear anchor positions.
- Prop anchor positions.
- Occlusion masks.
- High-contrast variant guidance.
- Small-size simplification guide.
- Expression reference sheet.
- Compatibility notes.
```

### 15.3 Asset QA matrix

Every new asset must be tested against:

```text
All compatible base archetypes
All known conflicting assets
All core expressions
Compact widget render
Standard widget render
Large widget render
Light wallpaper
Dark wallpaper
High-contrast mode
Offline cached render
Current renderer version
Previous supported renderer versions where compatibility is promised
```

---

## 16. Initial Content Plan

### 16.1 MVP inventory

Do not launch content-thin. Launch smaller, but with coherent visual coverage.

```text
5 base archetypes
12 curated palettes
10 core expression presets
12 essential accessories
10 essential props
6 simple scenes
4 frame families
6 Status Deck presets
8 lightweight reaction overlays
```

This produces substantial apparent variety while keeping the production system manageable.

### 16.2 MVP expressions

```text
Neutral
Happy
Excited
Sleepy
Tired
Focused
Stressed
Sick
Mischievous
Do not disturb
```

### 16.3 MVP accessories

```text
Round glasses
Sunglasses
Beanie
Headphones
VR headset
Hood
Cat ears
Wizard hat
Crown
Bandage
Visor
Helmet
```

### 16.4 MVP props

```text
Coffee
Tea
Controller
Book
Phone
Keyboard
Blanket
Microphone
Potion
Snack
```

### 16.5 Initial collections

| Collection | Purpose | Example assets |
|---|---|---|
| Core Companions | Broad neutral default identity | Blob, bot, ghost, orb, critter; glasses, tea, blanket |
| Cozy Signal | Ambient, warm presence | Rainy window, coffee, book, hoodie, night palette |
| Tech/VR | Natural fit for early VR/gaming users | VR visor, headset, keyboard, neon frame, holo badge |
| Quest Party | Social gaming energy | Controller, mic, party lights, invite gesture |
| Dungeon After Dark | Strong fantasy identity pack | Wizard hat, potion, tavern, lantern, rune frame |
| Pixel Relay | Retro alternative family | Pixel sprite, handheld, CRT, arcade scene |

The core launch experience must remain broad and welcoming. Tech/VR is an important early-adopter collection, not the entire product identity.

---

## 17. Monetization Rules

### 17.1 Free functionality

Never paywall the ability to communicate essential human state.

Free tier must include:

```text
Core avatar base choices
Core expression vocabulary
Mood and availability changes
Privacy controls
Friend widgets
Essential activity badges
Essential props: coffee, controller, VR headset, blanket, book, phone
Basic frames and scenes
Friend and circle functionality
```

### 17.2 Appropriate paid cosmetic surfaces

```text
Themed base families
Premium accessory packs
Premium scene packs
Advanced frames
Seasonal collections
Shared duo/circle scenes
In-app animation enhancements
Artist/creator packs after moderation infrastructure exists
```

### 17.3 Prohibited monetization

Do not sell or gate:

```text
Sad, sick, busy, DND, text-only, AFK, or other core status vocabulary
Privacy settings
Friend visibility
Basic widget functionality
Accessibility modes
Status expiration
Block/mute tools
```

---

## 18. Privacy and Safety Requirements

The avatar system must respect the broader iDL presence privacy model.

1. A user controls which audience sees their avatar appearance, mood, availability, activity, note, and external badges.
2. Invisible mode must prevent friend-facing presence publication.
3. An avatar should render safely even when some presence fields are hidden; hidden state must not leak through props, scenes, badges, or cached images.
4. The system must not infer sensitive health, location, or relationship information from avatar state.
5. User-visible render URLs must not expose raw platform identifiers or private state.
6. Friend removal/blocking must revoke future authorized avatar/presence access immediately.
7. Reactions must be dismissible and have an expiry policy.
8. If user-generated assets are ever introduced, require a separate moderation, reporting, copyright, and content-safety design before launch.

---

## 19. AI-Assisted Features

### 19.1 Recommended: catalog-based vibe suggestions

AI may help users choose from **owned, curated assets**. It must not become the source of canonical avatar art in the MVP.

Example input:

```text
“sleep-deprived cyber wizard who lives in VR”
“cozy orange cat that codes at night”
“space goblin with a coffee addiction”
“minimal black-and-white ghost”
“pastel dungeon healer”
```

Example output:

```text
Base: bot
Palette: midnight cyan
Accessory: wizard hat
Prop: coffee
Frame: glitch rune
Scene: neon study
Suggested default: Deep Work
```

### 19.2 Why arbitrary generated art is rejected

Do not use arbitrary external generative outputs as a canonical avatar layer because it creates:

```text
- Style inconsistency
- Poor tiny-widget legibility
- Layer incompatibility
- Moderation burden
- Intellectual-property uncertainty
- Caching and rendering complexity
- Weak monetizable visual identity
```

AI should configure the system, not replace the system.

---

## 20. Validation Plan

### 20.1 Visual prototype test

Before producing a large asset library, create three small competing art systems:

```text
System A: Premium sticker creatures
System B: Minimal techno-mascots
System C: Soft pixel avatars
```

For each system, produce:

```text
6 base examples
8 expression states
12 accessories
Core status examples: neutral, happy, sleepy, busy, in VR, gaming, text-only, DND, need company, sick
Widget mockups at 48 px, 64 px, 96 px, and 128 px
Light and dark wallpaper contexts
```

### 20.2 Test criteria

Run tests with approximately 10–20 prospective users from the likely audience: close friend groups, social VR participants, gamers, creators, and non-gamer friends.

Ask after a brief two-second exposure:

```text
Who is this?
What mood are they in?
Would you put this on your home screen?
Which style would you enjoy customizing?
Which one feels like a friend rather than an app icon?
Which one would you show someone else?
Which style would you pay a small amount to customize further?
Which looks best against your usual wallpaper?
```

### 20.3 Product decision metric

The most meaningful question is:

> Do users want to assign these characters to real people in their lives and keep them visible on their home screens?

A “cute” reaction is insufficient. Seek social attachment and repeated use intent.

### 20.4 Quantitative MVP metrics

```text
Avatar creation completion rate
Median time to first saved avatar
Percentage of users setting a signature accessory
Widget installation rate
Widget retention rate
Status update frequency
Saved preset use rate
Avatar Lab revisit rate
Cosmetic selection diversity
Render/cache error rate
Widget visual refresh success rate
Accessibility issue reports
Seven-day and thirty-day retention
```

---

## 21. Implementation Roadmap

### Phase A — Visual discovery

```text
- Produce mood boards and a written art-direction lock.
- Create three prototype systems: sticker, techno-mascot, pixel.
- Create widget-scale comparison mockups.
- Run short user tests.
- Select one primary direction and one alternate cosmetic family.
- Define the initial color, outline, shading, and typography system.
```

### Phase B — Technical foundation

```text
- Define AvatarConfiguration schema.
- Define AssetManifest schema.
- Implement asset registry and pack loader.
- Implement base anchors and slot system.
- Implement compatibility metadata and conflict evaluator.
- Implement deterministic renderer for local preview.
- Implement target-size simplification rules.
- Implement render-key cache.
- Add snapshot-style visual regression tests.
```

### Phase C — Creator MVP

```text
- Quick Creator flow.
- Five base archetypes.
- Core palette set.
- Core expressions.
- Essential accessories and props.
- Widget preview strip.
- Save/load avatar configuration.
- Randomize and undo/redo.
- Avatar Lab basic categories.
- Accessibility semantics.
```

### Phase D — Presence integration

```text
- Connect PresenceState to expression/context mapping.
- Build Status Deck presets.
- Implement status expiry.
- Apply visibility-aware render filtering.
- Render self-avatar in app and self widget.
- Render friend avatars from cached authorized state.
- Add reactions as temporary overlays.
```

### Phase E — Widget hardening

```text
- Solo friend widget.
- Own-status widget.
- Target-size render variants.
- Push-triggered refresh path.
- WorkManager reconciliation.
- Offline last-known output.
- Wallpaper contrast modes.
- Widget QA across major Android launchers/devices.
```

### Phase F — Expansion

```text
- Saved identity slots.
- Friend circle/Duo scenes.
- Expanded asset packs.
- Vibe suggestion system.
- VRCQ bridge visual hints.
- Desktop editor/bridge if validated.
- Creator-pack workflow only after moderation and entitlement architecture exists.
```

---

## 22. Engineering Acceptance Criteria

The avatar creator MVP is complete only when all of the following are true:

1. A new user can create a recognizable avatar in under two minutes.
2. The app stores the avatar as a deterministic configuration, not a one-off screenshot.
3. A user can set a signature accessory and preserve it across status changes where compatible.
4. A user can choose mood and availability through the Status Deck without entering Avatar Lab.
5. The renderer produces correct output for compact, standard, large, profile, and share targets.
6. A 48×48 render communicates an identifiable base, an expression, and an availability cue.
7. Incompatible accessories are blocked or resolved predictably.
8. Manual state overrides automated context by default.
9. A status can expire and the render reverts cleanly.
10. A friend-facing render filters hidden fields based on audience/privacy policy.
11. The app caches and displays last-known authorized output offline.
12. All selectable visual controls have accessible labels and state descriptions.
13. The creator works in dark mode and at enlarged system font/display settings.
14. Snapshot/visual regression tests cover core base-expression-accessory combinations.
15. All renderer and conflict-resolution unit tests pass.

---

## 23. Required Test Coverage

### Unit tests

```text
- Asset compatibility evaluation
- Conflicting accessory resolution
- Required dependency enforcement
- Base-specific fallback selection
- Expression mapping per supported base
- Presence precedence behavior
- Status expiration behavior
- Privacy filtering of visual state
- Render-key generation and invalidation
- Target-size simplification priority
- Asset-pack version fallback
```

### Visual/snapshot tests

```text
- Every base at every core expression
- Every core accessory on compatible bases
- Every status preset
- Compact/standard/large render targets
- Light/dark/high-contrast wallpaper contexts
- Signature accessory persistence through state changes
- Offline/cache fallback render
```

### UI tests

```text
- Quick Creator completion
- Avatar Lab category switching
- Save avatar configuration
- Randomize constrained result
- Status Deck quick state update
- Widget preview updates after state change
- Accessibility focus and labels for art selection
```

---

## 24. Implementation Warnings

### Warning 1: Do not optimize for the full-screen editor first

The home-screen widget is the product surface. Test every asset at compact size before approving it.

### Warning 2: Do not confuse variety with quality

Five excellent bases and 40 excellent assets outperform 30 mediocre bases and 300 mismatched assets.

### Warning 3: Do not allow unrestricted layer combinations

Unbounded freedom produces clipping, visual noise, broken semantics, and support burden. Curated constraints are a quality feature.

### Warning 4: Do not make visual status infer private data

A headset badge may disclose “in VR”; a scene could accidentally disclose more than a user intended. Privacy filtering must occur before render composition.

### Warning 5: Do not paywall basic emotional vocabulary

Monetize expressive themes, not fundamental human communication.

### Warning 6: Do not add a 3D engine because the project is avatar-related

iDL’s success condition is instantaneous, charming, highly legible 2D output at widget scale. A 3D stack adds cost without solving the core problem.

---

## 25. Art-Direction Brief

Use the following brief for artists, concept generation, and internal review:

> Design a family of compact, highly expressive digital companion characters for an Android home-screen social-presence app. Characters must remain recognizable at 48×48 pixels and communicate a friend’s mood, availability, and activity before the user reads any text. The style should be premium 2D sticker art: rounded silhouettes, bold readable facial features, restrained soft shading, strong accessory silhouettes, and calm backgrounds. Characters should feel warm, playful, inclusive, slightly strange, and collectible—not childish, corporate, photorealistic, anime-derived, or clones of Bitmoji/Memoji.
>
> Every avatar is constructed from interchangeable layers: base creature, color palette, eyes, mouth, expression effects, headwear, face accessories, handheld prop, background scene, status ring, activity badge, and temporary reaction overlay.
>
> Required initial visual states: neutral, happy, excited, sleepy, tired, focused, busy, stressed, sick, gaming, in VR, text-only, do-not-disturb, AFK, and want-company.
>
> Required initial accessory silhouettes: VR headset, headphones, coffee cup, tea mug, controller, phone, keyboard, blanket, book, potion, microphone, crown, beanie, wizard hat, glasses, sunglasses, and cat ears.
>
> Treat tiny-scale legibility as a hard constraint. The face and one dominant prop must remain understandable at 48×48 pixels. Use icon shapes, contrast, and silhouette—not small text or intricate detail—to communicate meaning.

---

## 26. Instructions for Claude Code / Cursor

When implementing this plan:

1. Audit the existing repository before modifying architecture.
2. Preserve existing code and identify extension points.
3. Create planning documents before large implementation changes.
4. Implement the data model, asset manifest, compatibility engine, and deterministic renderer before attempting a large editor UI.
5. Build the Quick Creator before the full Avatar Lab.
6. Add actual widget-sized previews early; do not defer widget testing until the end.
7. Use fake/demo data if backend credentials are unavailable.
8. Keep the renderer independent from any specific backend or external integration.
9. Implement accessibility semantics at the same time as visual controls.
10. Add tests for conflict resolution, visibility filtering, expiration, and target-size simplification before expanding the asset catalog.
11. Do not claim visual correctness without rendering and reviewing target-size output.
12. Report files changed, tests run, known limitations, and the next highest-value implementation step at each checkpoint.

---

## 27. Final Product Standard

The completed iDL avatar creator should make this experience possible:

> A friend sees a tiny cyan robot with familiar round glasses on their Android home screen. Tonight it has half-lidded eyes, a blanket, a tea mug, and a subtle text-only indicator. Tomorrow it has the same glasses and silhouette, but a VR headset, a neon border, and a joinable activity badge. The user does not need to open a feed or receive an intrusive notification to understand the change.

That is the standard: a tiny character that is unmistakably a specific friend, emotionally legible, technically reliable, privacy-respecting, and sufficiently charming that people voluntarily give it permanent home-screen real estate.
