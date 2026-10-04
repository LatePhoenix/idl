# Avatar recipe schema

**Status:** Design, 2026-10-04. Saved avatars today are `AvatarConfiguration` schema **2**. Do not add a second stored document.

## 1. Why there is no separate `AvatarRecipe` type

`AvatarConfiguration` is already the compact, deterministic identity: base, palette, eye and mouth family, signature items, resting expression, style, `schemaVersion`, `renderVersion`. Temporary expression and context come from `VisiblePresence` at render time, after the server has filtered them.

A parallel recipe JSON would fork Room, the server row, and the golden vectors. New emoji fields are a **schema 3 migration** of this object, plus manifest data. Export JSON is a serialization of the same object, not a new source of truth.

Storage metadata (owner, server timestamps) stays on the row, as the model comment already says. Optional user-facing name stays on `IdentitySlot`, which already has `name`.

## 2. Schema 2 (shipped)

```kotlin
AvatarConfiguration(
  baseAssetId, paletteAssetId,
  eyeFamilyAssetId, mouthFamilyAssetId,
  signatureFeatureAssetIds,
  signatureHeadAccessoryAssetId, signatureFaceAccessoryAssetId, signatureBodyAccessoryAssetId,
  defaultPropAssetId, defaultSceneAssetId, defaultFrameAssetId,
  restingExpressionId, styleDna,
  renderVersion = 2, schemaVersion = 2,
)
```

`StyleDna.semanticVisualOverrides` maps a semantic key (`mood:sleepy`) to extra visuals. Those overrides apply only when that key is present in `VisiblePresence`.

## 3. Schema 3 (proposed, not coded)

Add fields with defaults so a schema 2 payload still decodes:

| Field | Type | Default | Purpose |
| --- | --- | --- | --- |
| `packId` | string | `"core_proto"` | Which pack this was authored against. |
| `packVersion` | int | `1` | Asset-pack version. Randomize and cache include it. |
| `familyId` | string | derived from `baseAssetId` via the manifest | Round face, cat face, and the other families. |
| `itemIds` | map category → unordered set of ids | empty | Hair, beard, jewelry, and other multi-slots that schema 2 cannot name. List order is not drawing order; the asset z-index is. The render key sorts the ids. |
| `colorOverrides` | map slot → `#RRGGBB` or `#AARRGGBB` | empty | Unset slots use the asset default. |
| `unlinkedSlots` | list of slot names | empty | Slots the user detached from OKLCH derivation. |
| `itemTransforms` | map asset id → transform | empty | Translate, uniform scale, rotation, flip. Omitted means the asset default. |
| `background` | object | pack default scene | `transparent`, solid hex, or a named gradient/shape in the pack. |
| `randomSeed` | long or null | null | Set only when the user randomized. Same seed + pack version repeats. |

`schemaVersion` becomes 3. `renderVersion` bumps only when pixels for the same ids must change; a new field with a default does not bump it.

Expression remains `restingExpressionId` plus the presence override. It is not copied into `itemIds`.

Timestamps: `createdAt` and `updatedAt` stay on the server row. An exported file may copy them in. They are not an input to the render key.

## 4. Migration rules

- Unknown `schemaVersion` newer than the app: refuse to edit (`AvatarConfiguration.prepareForWrite` returns "update the app to edit this avatar"), still attempt to render known fields, and do not write the decoded object back. Unknown JSON keys are dropped on decode, so a write would destroy them.
- Missing asset: follow `retired`, then `fallback`, then the category default, then omit the layer. Keep the original id in the stored recipe.
- Retired pack: `retired` map on the manifest, already required by invariant 5.
- Schema 2 → 3: fill the new fields with defaults. `familyId` comes from the base asset's manifest family, or `round_face` only when the base is an explicit round-face id. Do not guess family from a display name.
- Color overrides never rewrite the asset's default. Reset clears the override.

Corrupt manifest: `AssetRegistry.validate()` already fails closed on structural errors. A vector picture that fails validation is skipped and recorded in the resolve log (`IdlLog`, no avatar content). The rest of the recipe still renders.

## 5. Render key

Deterministic string, order fixed, not map iteration order:

```text
v3 | packId | packVersion | renderVersion | family | base | expression
  | items in category order, ids sorted
  | colors in slot-name order
  | transforms in asset-id order
  | background
  | target | size | contrast | accessibility
  | presence fields that are allowed to affect pixels
```

Hidden presence fields are absent from the key because they are absent from `VisiblePresence`. Two viewers with different privacy results must not share a cache entry.

## 6. Export file

A share package is a zip or a pair of files:

- `avatar.json` — schema 3 configuration, plus `exportedAt` outside the render key
- `avatar.png` — rendered at the chosen size with the chosen background

The PNG is not sufficient to restore the avatar. The JSON is.
