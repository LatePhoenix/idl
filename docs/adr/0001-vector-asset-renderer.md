# ADR 0001 — Vector asset renderer

**Status:** Accepted (design). Not implemented.  
**Date:** 2026-10-04  
**Deciders:** user direction in D-39; engineering constraint from D-01, D-05, D-06.

## Context

iDL already paints avatars with `app.idl.avatar.AvatarRenderer` on `android.graphics.Canvas`. Widgets receive a bitmap of that same paint (D-06). Asset identity is a versioned manifest plus `AvatarConfiguration` (schema 2). Placeholder assets are procedural painters. The manifest already allows a `raster` file, which nothing in the MVP uses.

The emoji-style creator needs gradients, clips, per-part recoloring, and export at 256–2048 px from vectors. Two runtime approaches were compared. A third, drawing the device emoji font, is rejected: vendor and OS versions change the pixels, which breaks determinism and the "saved avatars never break" rule.

## Options

### A. Preprocessed path data, drawn by Android Canvas

An offline pipeline turns reviewed SVG into a small JSON picture: a 1024×1024 viewBox, path commands, fill (solid or linear/radial gradient), clip references, a semantic color slot, and a z-band. At runtime a `VectorAssetRenderer` replays that picture onto a `Canvas` with the recipe's colors and transform. `AvatarRenderer` keeps owning the bitmap, the widget path, and the presence chrome (availability glyph, activity badge, reactions).

### B. Runtime SVG through AndroidSVG

`com.caverock:androidsvg` 1.4 (Apache-2.0) parses SVG on device. Last Maven release: 28 May 2019. The repository has had later commits, including a promised 1.5 that has not shipped. Coil's maintainers have discussed leaving it because of unreleased parser bugs.

## Decision

**Choose A.** Hide it behind this interface in `app.idl.avatar` (Android stays out of `domain`):

```kotlin
interface VectorAssetRenderer {
    fun draw(
        canvas: Canvas,
        picture: VectorPicture,
        colors: Map<String, Int>,
        transform: ItemTransform,
        sizePx: Float,
    )
}
```

`VectorPicture` is loaded from the asset pack, cached by asset id + `contentVersion`, and never re-parsed from SVG on the UI thread.

## Comparison

| Criterion | A. Path IR + Canvas | B. AndroidSVG 1.4 |
| --- | --- | --- |
| Fidelity | Whatever the pipeline emits. Gradients and clips are first-class. Filters, text, and images are rejected before they ship. | Higher raw SVG coverage, including features we must not ship. |
| Recoloring | Each part names a color slot. The renderer substitutes that slot. Shading slots stay independent. | Requires mutating paints or styles inside a parsed DOM. Easy to tint the whole graphic by accident. |
| Widget export | Same `Canvas` that already produces widget bitmaps. Export size is the bitmap size. | A second rasterization path to keep in sync with widgets. |
| Determinism and golden tests | The IR is plain JSON. Checksums do not depend on an XML parser. | Parser bugs and library upgrades change pixels. |
| Memory | Decode once per content version. Cache the `Path` objects. | Re-parse or retain a live SVG document per asset. |
| Maintenance | No new dependency. Canvas is platform API. | Last release is seven years old. Adding it needs an explicit dependency approval. |
| House-style swap | A new pack ships new pictures under the same asset ids. | Same, but only if every future SVG stays inside AndroidSVG's dialect. |

## Consequences

- The asset pipeline is mandatory. Invalid SVG never reaches the device.
- The first vertical slice can keep today's procedural painters for any part that does not yet have a picture. `render.type` becomes `procedural`, `vector`, or `raster`.
- Supersampling, if measured to help 32–64 px previews, is a Canvas scale into a larger buffer and a downscale. It is not a reason to adopt a SVG library.
- Revisit B only if a maintained renderer exists and a measured Canvas gap (a specific SVG feature we are willing to support) cannot be expressed in the path IR.
