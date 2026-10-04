# Emoji-style avatar compositor

**Status:** Design, 2026-10-04 (D-39). Not implemented.  
**Does not replace** [`IDL_ARCHITECTURE.md`](IDL_ARCHITECTURE.md). App shell, privacy, presence, widgets, and the server stay as they are. This document is the avatar-art and editor design.

## 1. What this is

A deterministic layered vector compositor for a recognizable face. Identity (family, skin, hair, facial hair, glasses, jewelry, signature props) stays put. Expression (eyes, brows, mouth, tears, blush, and similar cues) changes with presence. Friends see the result on a home-screen widget, so the face has to read at 48 px.

Production rendering does not call an image model, paint pixels by hand, or ship a folder of finished PNGs as the source of truth. Concept art may be explored with generative tools. What ships is a reviewed vector picture with license metadata.

## 2. Fit to the existing app

| Prompt idea | iDL decision |
| --- | --- |
| New modules `avatar-core`, `avatar-renderer`, `avatar-assets`, `app` | **Rejected.** D-01 is one `:app` module. Domain code stays in `app.idl.domain.avatar` with no Android imports. Drawing stays in `app.idl.avatar`. |
| Editor app with no backend and no accounts | **Rejected as a second app.** The editor is a Compose screen in iDL. Accounts and the server already exist. The first editor slice does not need a new API. |
| `renderAvatar(recipe): Bitmap` | `AvatarRenderer.bitmap(resolved, registry, sizePx)` already returns a bitmap. The vector renderer is a delegate it calls for `render.type = vector` parts. |
| DataStore for editor prefs | Already used for lightweight preferences. Avatar recipes continue to live in the avatar model (Room JSON), not only in DataStore. |
| minSdk 26, Kotlin, Compose, Material 3, coroutines, kotlinx.serialization, manual DI | Already true (`minSdk` 26, D-02). |
| AndroidSVG | **Rejected.** See [ADR 0001](adr/0001-vector-asset-renderer.md). |

Availability and activity badges already draw on the widget (F-01, F-02, D-28). When this compositor becomes the widget painter it has to keep drawing them. Presence chrome stays outside the character stack (section 5).

## 3. Runtime shape

```text
AvatarConfiguration + VisiblePresence
        │
        ▼
AvatarResolver + CompatibilityEngine     (pure Kotlin, already shipped)
        │
        ▼
Resolved layers, each an asset id
        │
        ▼
AvatarRenderer (Canvas, app + widgets)
        │
        ├── procedural painter          (today's placeholder pack)
        ├── VectorAssetRenderer         (new; path IR from the pack)
        └── presence chrome             (frame, availability glyph, activity badge, reactions)
```

Privacy is unchanged (invariant 1, D-24). The server sends a filtered `VisiblePresence`. The client does not hide a field the server sent, and it does not invent a mood cue the server omitted. Tears, anger marks, blush, and expression ids are mood-derived. If `mood` is absent, those layers are absent. Skin, hair, and accessories are identity and may render whenever the avatar itself is visible.

Expiry, manual-over-automated precedence, and the clock stay in the presence resolver. The compositor does not call `Instant.now()` or `random()`.

## 4. Identity and expression

A recipe names a **family** and a **base asset**. The family selects which anchors and which items are legal. The base asset is the head (and ears, if that family draws them on the base).

| Family id | Role | Existing base it can sit beside |
| --- | --- | --- |
| `round_face` | Ordinary emoji face | `base_blob` until art replaces it |
| `human_head` | Head with neck and ears as separate anchors | none yet; do not revive the retired `human` id without a migration |
| `cat_face` | Cat head | `base_critter` is not the same silhouette; keep both ids |
| `monkey_face` | Monkey head | new |
| `robot_head` | Mechanical head | `base_bot` |
| `skull_head` | Skull | new |
| `fantasy_head` | Approved monsters and costume heads | new |
| `special_head` | Ghost, alien, and other approved heads | `base_ghost`, `base_orb` |

D-29 still holds for avatars already saved against `base_blob`, `base_bot`, `base_ghost`, `base_critter`, and `base_orb`. New emoji families are added. They do not rename those ids.

Expression is an `ExpressionDef`: eyes, brows, mouth, overlays. Swapping expression does not swap hair, beard, glasses, or the base. That split already exists. The emoji pack fills it with face-emoji expressions instead of a new schema.

## 5. Layer bands

Stable z-bands, not selection order. An asset may emit more than one part (glasses arms behind the head, frames in front). Presence chrome uses a higher range so a hat cannot cover the availability glyph.

| z | Character content |
| --- | --- |
| 0 | Background |
| 10 | Rear props |
| 20 | Rear hair |
| 30 | Rear jewelry |
| 40 | Head, ears, neck |
| 50 | Expression and facial detail |
| 60 | Beard, moustache, facial hair |
| 70 | Front hair and bangs |
| 80 | Eyewear |
| 90 | Front jewelry and head accessories |
| 95 | Expression overlays (procedural) |
| 100 | Mouth-held props |
| 105 | Foreground props (procedural) |
| 110 | Foreground effects on the character |

| z | iDL presence chrome (not part of the emoji editor's character) |
| --- | --- |
| 200 | Frame |
| 210 | Availability glyph (shape plus color, D-28) |
| 220 | Activity badge |
| 230 | Reaction overlays |

`AssetCategory.defaultZ` in the current manifest is the procedural painter's order. Do not renumber it in place. Vector parts carry their own `zBand`. `CompositeOrder` paints procedural categories on the bands in the table above, including 95 and 105, and paints vector parts on their own `zBand`. A category drawn by a vector asset is not also drawn procedurally. A later migration can align `defaultZ` with these bands once the procedural pack is no longer what widgets draw.

Masks are data on the part (`clip` referencing another part id, or a named occluder such as `head.front`). The first slice needs only clip-to-path. Hair-behind-head, beard-around-mouth, lens tint, and a pipe stem into the mouth are the cases that justify clips. Do not add Kotlin `if (assetId == ...)` branches for them.

## 6. Color

No global tint. Each filled part has a semantic slot. The pipeline assigns slots by hand. Matching a hex value is not a slot assignment, because one source yellow can be skin in one file and hair in another.

Slots used by the first packs:

`face.primary`, `face.shadow`, `face.highlight`, `outline`, `eye.iris`, `eye.white`, `mouth`, `hair.primary`, `hair.shadow`, `beard.primary`, `beard.shadow`, `glasses.frame`, `glasses.lens`, `metal.primary`, `gem.primary`, `accessory.primary`, `accessory.secondary`.

Quick mode offers curated swatches. Advanced mode offers HSV, hex, recents, favorites, alpha where the slot allows it, and reset. Highlight and shadow defaults are derived in OKLCH from the primary, then clamped to sRGB. The user can unlink a derived slot. A contrast warning is non-blocking.

Core expressions and the default face stay free (invariant 6). Charge may later gate extra accessories (D-30). It does not gate the emotional set.

## 7. Anchors

Bases declare anchors in the 1024×1024 space (the current manifest uses 0..1; the vector pack uses the 1024 grid and the loader scales). Required names for a compatible family:

`head_center`, `head_top`, `forehead`, `temple_left`, `temple_right`, `ear_left`, `ear_right`, `eye_line`, `nose`, `mouth`, `upper_lip`, `chin`, `jaw_left`, `jaw_right`, `neck`, `accessory_center`.

An item's placement is `anchor + asset offset + family override + optional user translate / uniform scale / rotation / flip`. The editor can reset that transform. Compatibility hides combinations that lack a required anchor. An advanced toggle may show them anyway.

## 8. Editor

One screen, one job: a recognizable avatar in under two minutes.

- Large preview, plus a 48 px preview.
- Category strip: Faces, Expressions, Hair, Facial hair, Eyes, Glasses, Hats, Jewelry, Mouth props, Other, Backgrounds, Colors.
- Asset grid with search.
- Colors for the current item.
- Undo, redo, randomize, save, duplicate, reset category, reset all, before/after.

Randomize takes a seed and the pack version and must repeat. It only picks compatible, non-conflicting items and default colors.

Export, when the slice can render vectors: transparent PNG, solid PNG, simple background PNG, square sizes 256 / 512 / 1024 / 2048, recipe JSON, and the Android share sheet. Export renders at the requested size. It does not scale the preview bitmap. Preview checks also cover 32, 48, 64, and 128 px.

## 9. What is deliberately out of this design

- A generative runtime.
- Scraping Gboard or any proprietary emoji pack.
- Food, flags, vehicles, hearts, and other non-head emoji.
- A new Gradle module split.
- Replacing the privacy filter or the presence model.
- Importing Noto files in this design step. The pin and the license rules are in [`LICENSING.md`](LICENSING.md). The scope file is [`config/emoji_face_scope.json`](../config/emoji_face_scope.json).
