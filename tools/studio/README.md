# iDL Art Studio

Operator-only tool for making and managing avatar art. Never shipped. Design and phases:
[`docs/avatar/ART_STUDIO.md`](../../docs/avatar/ART_STUDIO.md) (D-50).

## Run

```bash
python tools/studio/studio.py setup            # once: venv + skia-python + numpy
python tools/studio/studio.py serve            # web UI on http://127.0.0.1:8765
python tools/studio/studio.py list --category hair
python tools/studio/studio.py show hair_bob
python tools/studio/studio.py draft new …      # see docs/avatar/STUDIO_AGENT_GUIDE.md
python -m unittest discover -s tools/studio/tests -t tools/studio
```

S0 (`serve`, `list`, `show`) needs only the Python standard library and a browser. S1 commands
(`setup`, `guides`, `kit`, `draft`, `lint`, `render`, `geom`) use `tools/studio/.venv`.

## Views

- **Catalog:** every vector asset in the live pack versions, with tier, content version and
  whether its SVG source exists. Select one for head and bust framing at 48 (shown 2×), 96 and
  128 px on light and dark wallpapers, its color slots, its parts, and its SVG source.
- **Workbench:** combine items and an expression, override slot colors, and preview head and bust
  framing at widget and profile sizes. The state is remembered in the browser.
  "Copy recipe JSON" copies the combination.
- **Drafts (S1):** drafts under `.studio/drafts/`, grouped, with browser preview, lint, revision
  history (revert), and a guides overlay. Polls every 1.5s for CLI edits.
- **Expressions:** the 110-expression catalog. Shows which are in the pack, which can already be
  composed from existing shapes, and which shapes still need art.

## How the preview relates to the app

`web/render.js` and `engine/render.py` mirror `AvatarRenderer`, `CanvasVectorAssetRenderer`,
`CompositeOrder` and `ColorSlots` (AP-9 OKLCH + pack `slotLinks`): draw order (band, category z,
asset id, part index), framing viewports, slot defaults and derived shadow and highlight, clips,
strokes and gradients. The browser path is a fast preview; the Skia path writes PNG sheets.
The contact sheets that `PackContactSheetTest` records stay the source of truth, and S2 adds the
real Kotlin resolver behind the try-on view.

Known differences: no outline-contrast override, no procedural (non-vector) layers, and an
expression's procedural overlays (for example `overlay_blush`) are skipped.
