"""Build ST-1 acceptance drafts from guides + geometry helpers (no freehand outside guides).

    python tools/studio/studio.py setup   # once
    tools/studio/.venv/Scripts/python tools/studio/acceptance_st1.py
"""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

STUDIO = Path(__file__).resolve().parent
sys.path.insert(0, str(STUDIO))

from engine import compose, drafts, guides, lint, paths, render  # noqa: E402

SHEETS = STUDIO.parents[1] / "docs" / "handoff" / "sheets"
SOURCES = STUDIO / "acceptance"


def _svg(asset_id: str, parts: list[tuple[str, int, str, str, dict]]) -> str:
    lines = [
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"',
        f'     data-schema-version="2" data-id="{asset_id}"',
        '     data-content-version="1">',
    ]
    for part_id, band, slot, d, extra in parts:
        attrs = [f'data-part="{part_id}"', f'data-z="{band}"', f'data-slot="{slot}"']
        if extra.get("tags"):
            attrs.append(f'data-tags="{" ".join(extra["tags"])}"')
        if extra.get("opacity") is not None:
            attrs.append(f'data-opacity="{extra["opacity"]}"')
        if extra.get("publishMask"):
            attrs.append(f'data-publish-mask="{extra["publishMask"]}"')
        if extra.get("clipBy"):
            attrs.append(f'data-clip-by="{" ".join(extra["clipBy"])}"')
        lines.append(f'  <path {" ".join(attrs)} d="{d}"/>')
    lines.append("</svg>")
    return "\n".join(lines) + "\n"


def _box(l: float, t: float, r: float, b: float) -> str:
    return f"M {l} {t} L {r} {t} L {r} {b} L {l} {b} Z"


def build_beanie() -> str:
    g = guides.guides()
    hairline = g["hairline_max"]["y"]
    # Character-band parts must stay inside -16..1040 (validator). Band 20 may use the body region.
    char_box = _box(-16, -16, 1040, 1040)
    body_box = _box(-256, -16, 1280, 1536)
    # Crown: grow the head top cap, lift for slouch, keep below hairline_max and in-box.
    crown = paths.offset(g["head_top_cap"]["d"], 28)
    crown = paths.transform(crown, dy=-24)
    clip = _box(-16, -16, 1040, hairline)
    crown = paths.intersect(paths.intersect(crown, clip), char_box)
    # Extra slouch volume above the crown (ellipse from crown point guide).
    cx, cy = g["crown"]["at"]
    slouch = paths.ellipse(cx, max(cy - 8, 40), 200, 100)
    crown = paths.union(crown, paths.intersect(slouch, clip))
    crown = paths.intersect(crown, char_box)
    # Pom-pom sitting on the slouch peak, kept inside the character box.
    top = max(paths.bounds(crown)[1], 8)
    pom = paths.intersect(paths.ellipse(cx, top + 36, 56, 56), char_box)
    # Cuff band near the lower edge (strip just above hairline).
    rows = g["head_rows"]["rows"]
    y_band = 300
    lx, rx = rows.get(str(y_band), [180, 844])
    band = _box(lx - 4, y_band - 24, rx + 4, y_band + 4)
    band = paths.intersect(band, paths.offset(crown, 6))
    band = paths.intersect(band, char_box)
    # Shade lower-right of crown; highlight upper-left.
    shade = paths.intersect(crown, _box(520, 120, 1040, hairline))
    highlight = paths.intersect(crown, _box(-16, -16, 480, 200))
    # Hair mask: slightly grown crown (+ pom) so hair stays under the hat.
    mask = paths.intersect(paths.union(paths.offset(crown, 10), paths.offset(pom, 6)), char_box)
    # Back of beanie (band 20), outside the head, inside the body region.
    back = paths.subtract(paths.offset(crown, 16), g["head_outline"]["d"])
    back = paths.intersect(back, body_box)
    return _svg("hat_beanie_slouch", [
        ("hair_mask", 90, "hat.primary", mask, {"opacity": 0, "publishMask": "occlude.hair_top"}),
        ("back", 20, "hat.secondary", back, {}),
        ("crown", 90, "hat.primary", crown, {}),
        ("band", 90, "hat.secondary", band, {}),
        ("pom", 90, "hat.secondary", pom, {}),
        ("shade", 90, "hat.shadow", shade, {}),
        ("highlight", 90, "hat.highlight", highlight, {}),
    ])


def build_curly() -> str:
    g = guides.guides()
    hairline = g["hairline_max"]["y"]
    char_box = _box(-16, -16, 1040, 1040)
    body_box = _box(-256, -16, 1280, 1536)
    # Bumpy curly silhouette: sample points around an offset top cap.
    cap = paths.offset(g["head_top_cap"]["d"], 40)
    clip = _box(-16, -16, 1040, hairline - 4)
    cap = paths.intersect(cap, clip)
    # Curl bumps via union of small ellipses along the outer edge (guide-derived centres).
    rows = g["head_rows"]["rows"]
    bumps = []
    for y, (lx, rx) in sorted((int(k), v) for k, v in rows.items() if int(k) <= hairline):
        bumps.append(paths.ellipse(lx - 18, y, 36, 34))
        bumps.append(paths.ellipse(rx + 18, y, 36, 34))
    bumps.append(paths.ellipse(512, g["crown"]["at"][1] - 28, 90, 70))
    for x in (360, 460, 560, 660):
        bumps.append(paths.ellipse(x, 140, 42, 38))
    top = paths.intersect(paths.union(cap, *bumps), char_box)
    top = paths.intersect(top, clip)
    # Keep expression zone clear: subtract eye_zone grown slightly.
    eye = g["eye_zone"]["box"]
    if eye:
        l, t, r, b = eye
        eye_pad = _box(l - 8, t - 8, r + 8, b + 8)
        top = paths.subtract(top, eye_pad)
    shade = paths.intersect(top, _box(520, 80, 1040, hairline))
    highlight = paths.intersect(top, _box(140, -16, 500, 180))
    # Side curls outside the head (still above hairline at centre).
    side_l = paths.ellipse(160, 380, 70, 110)
    side_r = paths.mirror(side_l, 512)
    side = paths.intersect(paths.subtract(paths.union(side_l, side_r), g["head_outline"]["d"]), char_box)
    back = paths.subtract(paths.offset(paths.intersect(cap, _box(200, -16, 824, 200)), 24),
                          g["head_outline"]["d"])
    back = paths.intersect(back, body_box)
    clip_by = ["occlude.hair_top:difference"]
    return _svg("hair_short_curly", [
        ("back", 20, "hair.shadow", back, {"tags": ["hair_back"]}),
        ("side", 70, "hair.primary", side, {"tags": ["hair_side"]}),
        ("top", 70, "hair.primary", top, {"tags": ["hair_top"], "clipBy": clip_by}),
        ("shade", 70, "hair.shadow", shade, {"tags": ["hair_top"], "clipBy": clip_by}),
        ("highlight", 70, "hair.highlight", highlight, {"tags": ["hair_top"], "clipBy": clip_by}),
    ])


def _ensure_draft(draft_id: str, category: str, prompt: str, label: str, svg: str) -> dict:
    if drafts.exists(draft_id):
        # Reset by writing a new revision on the existing draft.
        meta = drafts.load(draft_id)
    else:
        meta = drafts.new(draft_id, category, prompt=prompt, group="st-1-acceptance", label=label)
    meta = drafts.write(draft_id, svg, note="acceptance ST-1 from guides")
    picture = drafts.compile_draft(draft_id)
    library = compose.Library(drafts.assets_for_preview())
    issues = lint.lint(picture, meta, library)
    drafts.RENDERS.mkdir(parents=True, exist_ok=True)
    out = drafts.RENDERS / f"{draft_id}-r{meta['current']:03d}.png"
    render.save_png(render.standard_sheet(library, draft_id), out)
    return {"meta": meta, "issues": issues, "sheet": out}


def main() -> int:
    SOURCES.mkdir(parents=True, exist_ok=True)
    SHEETS.mkdir(parents=True, exist_ok=True)
    beanie_svg = build_beanie()
    curly_svg = build_curly()
    (SOURCES / "hat_beanie_slouch.svg").write_text(beanie_svg, encoding="utf-8")
    (SOURCES / "hair_short_curly.svg").write_text(curly_svg, encoding="utf-8")

    results = [
        _ensure_draft("hat_beanie_slouch", "head_accessory",
                      "a slouchy knit beanie with a pom-pom", "Slouch beanie", beanie_svg),
        _ensure_draft("hair_short_curly", "hair",
                      "a short curly hairstyle", "Short curly", curly_svg),
    ]
    ok = True
    for r in results:
        errors = [i for i in r["issues"] if i["level"] == "error"]
        dest = SHEETS / f"st-1-{r['meta']['id']}.png"
        shutil.copy2(r["sheet"], dest)
        print(f"{r['meta']['id']}: {len(errors)} error(s) -> {dest}")
        for i in r["issues"]:
            print(f"  {i['level']:<5} {i['rule']:<12} {i.get('part', '')} {i['message']}")
        if errors:
            ok = False
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
