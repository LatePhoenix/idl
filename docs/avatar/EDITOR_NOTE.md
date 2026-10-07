# AP-11 editor note

**Date:** 2026-10-07. Binding for the editor pull requests. The program row wins where this is silent.

## What this slice is

One recipe (`AvatarConfiguration`), one session. The old Studio keeps editing v1 `AvatarConfig` until the UI pull request replaces it. This note does not restyle the avatar.

## Session

`EditorSession` is pure Kotlin in `domain/avatar`. It holds the recipe, an undo stack (50), a redo stack, and the recipe from when the session opened (before/after). A new edit clears redo. Undo at the start does nothing.

Wearing an item the user already wears toggles it off. A new item wins: anything it conflicts with is removed. An item that does not fit the base is refused. Premium items can be tried on. Save still goes through `prepareForWrite`, which refuses them when they are not owned.

| Tab | Where it is stored |
| --- | --- |
| Hair, facial hair, jewelry, tops, outerwear | `itemIds`, keyed by the category wire name |
| Headwear, eyewear, body, props, background, frame | the existing signature and default fields |
| Face details | `signatureFeatureAssetIds` |
| Expression | `restingExpressionId` |
| Skin and item color | `colorOverrides` and `unlinkedSlots` |

Eyes in the tab strip are a color on the current expression, not a separate asset grid.

## What the grids hide

Procedural hair, facial hair, tops, outerwear, jewelry, and signature features stay out of the grid and out of randomize. Nine procedural hairstyles draw nothing (F-37). Vector items, and the procedural bases, palettes, scenes, and frames the pack still uses, stay listed. Hidden store items stay out of the grid. A saved recipe that already wears one still renders.

## Randomize

Seeded with `kotlin.random.Random`. Same seed, same registry, same starting recipe: same result. It only picks free, compatible, vector items, and it never picks a pair that `conflictsWith` either way. It fills hair, facial hair (up to two), a top when the pack has one, outerwear, eyewear, headwear, jewelry (up to two), face details (up to two), and a resting expression. Base, palette, scene, and frame stay as they were.

## Later pull requests

- Full editor UI, then quick creator (skin, hair and hair color, top, done), then export at 512, 1024, and 2048, rendered at that size.
- The entitlement message in the UI uses the asset's accessibility label (F-40). The domain result still carries the ids.
- Replace the v1 Studio and the fox / ghost / robot onboarding row in the UI pull request.
