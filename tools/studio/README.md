# iDL Art Studio

Operator-only tool for making and managing avatar art. Never shipped. Design and phases:
[`docs/avatar/ART_STUDIO.md`](../../docs/avatar/ART_STUDIO.md) (D-50).

## Run

```bash
python tools/studio/studio.py serve            # web UI on http://127.0.0.1:8765
python tools/studio/studio.py list --category hair
python tools/studio/studio.py show hair_bob
python -m unittest discover -s tools/studio/tests -t tools/studio
```

S0 needs only the Python standard library and a browser.

## Views (S0, read-only)

- **Catalog:** every vector asset in the live pack versions, with tier, content version and
  whether its SVG source exists. Select one for head and bust framing at 48 (shown 2×), 96 and
  128 px on light and dark wallpapers, its color slots, its parts, and its SVG source.
- **Workbench:** combine items and an expression, override slot colors, and preview head and bust
  framing at widget and profile sizes. The state is remembered in the browser.
  "Copy recipe JSON" copies the combination.
- **Expressions:** the 110-expression catalog. Shows which are in the pack, which can already be
  composed from existing shapes, and which shapes still need art.

## How the preview relates to the app

`web/render.js` mirrors `AvatarRenderer`, `CanvasVectorAssetRenderer`, `CompositeOrder` and
`ColorSlots`: draw order (band, category z, asset id, part index), framing viewports, slot
defaults and derived shadow and highlight, clips, strokes and gradients. It's a fast preview.
The contact sheets that `PackContactSheetTest` records stay the source of truth, and S2 adds the
real Kotlin resolver behind the try-on view.

Known differences: no outline-contrast override, no procedural (non-vector) layers, and an
expression's procedural overlays (for example `overlay_blush`) are skipped.
