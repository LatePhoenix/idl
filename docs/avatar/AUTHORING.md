# Authoring an avatar item

Pictures are SVG sources. `tools/asset_pipeline.py` compiles them to the JSON the app draws. The pipeline uses only the Python standard library.

```text
art/<packId>/<assetId>.svg
        │  python tools/asset_pipeline.py build <packId>
        ▼
app/src/main/assets/packs/<packId>/vN/pictures/<assetId>.json
```

`python tools/asset_pipeline.py check` rebuilds every pack into a temporary directory and fails if a committed picture differs. `scripts/check.ps1`, `scripts/check.sh`, and CI run it.

## Add an item

1. Draw `art/<packId>/<assetId>.svg`. The file name, `data-id`, and the manifest `id` match.
2. Add the asset to that pack's `manifest.json`: category, label, `render.file`, license, tier, `contentVersion`, and `colorSlots` for every slot the picture uses.
3. Run `python tools/asset_pipeline.py build <packId>`.
4. Run `python tools/asset_pipeline.py check`.
5. Look at the category contact sheet (AP-4's `PackContactSheetTest`, once that pull request has landed) at 48, 96, and 512 px, on a light ground and a dark ground.

No Kotlin change is required to add a picture. The manifest entry is JSON.

## SVG subset

The root is `svg` with `viewBox="0 0 1024 1024"`, `data-schema-version` (`1` or `2`), `data-id`, and `data-content-version`.

Drawable elements are `path`, `rect`, `circle`, `ellipse`, and `polygon`. A `g` may carry `transform` (`translate`, `scale`, `rotate`, `matrix`). Arcs are converted to cubics. A `g` transform is baked into the commands.

Each drawable element has `data-part`, `data-z`, and either `data-slot` or `data-gradient`. Optional attributes:

| Attribute | Meaning |
| --- | --- |
| `data-fill-rule` | `nonzero` (default) or `evenodd` |
| `data-opacity` | `0`..`1` |
| `data-clip` and `data-clip-mode` | Clip to a path in this file. Mode is `intersect` or `difference` |
| `data-clip-path` | On a `path` that is not drawn. Defines the clip named by the attribute |
| `data-allow-overflow` | `true` lets the geometry leave the band box |
| `data-stroke-slot`, `data-stroke-width`, `data-stroke-cap`, `data-stroke-join` | Schema 2. Width is 16..96. Caps: `butt`, `round`, `square`. Joins: `miter`, `round`, `bevel`. Round is the default |
| `data-tags` | Schema 2. Space-separated tags, for example `hair_back` |
| `data-publish-mask` | Schema 2. Publishes this part's path under that name |
| `data-clip-by` | Schema 2. Space-separated `mask:mode` subscriptions. Applied in AP-8 |

Gradients live in `defs`. `gradientUnits` is `userSpaceOnUse`. Each `stop` has `offset` and `data-slot`, and may have `data-alpha`. The drawable points at the gradient with `data-gradient`.

Text, images, filters, scripts, `style`, `class`, and external references are rejected.

## Commands

`d` is an SVG path in the 1024 view box. A path with no arc and no group transform is copied into the JSON unchanged, so the shipped pictures stay byte-identical. Rects, ellipses, polygons, arcs, and transformed groups are converted to `M`, `L`, `C`, `Q`, and `Z`. Smooth `S` and `T` commands survive only on a path that is copied unchanged.

## Review checklist

- The shape reads at 48 px and still looks like the same object at 512 px.
- Fills use slots, not hard-coded colors.
- Schema 1 pictures do not carry strokes, tags, or masks.
- `contentVersion` on the SVG matches the manifest.
- `python tools/asset_pipeline.py check` passes.
- Contact sheets for the category are updated when those goldens exist.
