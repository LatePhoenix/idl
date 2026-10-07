"""Named geometry on the canonical teardrop, for drawing items that fit the head (ART_STUDIO.md §5.1).

Everything is derived from the shipped art: the head outline from base_teardrop, the eye, brow
and mouth zones from every expression shape in the pack, and the anchor points from the manifest.
If the head or an expression shape changes, the guides follow.
"""

from __future__ import annotations

from functools import lru_cache

from . import paths, repo

BASE_ID = "base_teardrop"
VIEW = 1024


def _union_bounds(boxes):
    boxes = [b for b in boxes if b]
    if not boxes:
        return None
    return (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))


def _picture_bounds(picture, band=None):
    boxes = []
    for part in picture.get("parts", []):
        if band is not None and part["zBand"] != band:
            continue
        box = list(paths.bounds(part["commands"]))
        if part.get("stroke"):
            half = part["stroke"]["width"] / 2
            box = [box[0] - half, box[1] - half, box[2] + half, box[3] + half]
        boxes.append(tuple(box))
    return _union_bounds(boxes)


def _round_box(box):
    return [round(v) for v in box] if box else None


@lru_cache(maxsize=1)
def guides() -> dict:
    pack = next(p for p in repo.load_packs() if BASE_ID in p.pictures)
    base = pack.pictures[BASE_ID]
    asset = pack.asset(BASE_ID)
    head = next(p["commands"] for p in base["parts"] if p["id"] == "face")
    body = next((p["commands"] for p in base["parts"] if p["id"] == "body"), None)
    hl, ht, hr, hb = paths.bounds(head)

    by_category: dict[str, list[dict]] = {}
    for a in pack.assets:
        if a["id"] in pack.pictures:
            by_category.setdefault(a["category"], []).append(pack.pictures[a["id"]])
    eyes = _union_bounds([_picture_bounds(p) for p in by_category.get("face_eye", [])])
    brows = _union_bounds([_picture_bounds(p) for p in by_category.get("face_brow", [])])
    mouth = _union_bounds([_picture_bounds(p) for p in by_category.get("face_mouth", [])])
    neutral_eyes = pack.pictures.get("eyes_round_neutral")
    eye_centres = []
    if neutral_eyes:
        for part in neutral_eyes["parts"]:
            for contour in paths.contours(part["commands"]):
                l, t, r, b = paths.bounds(contour)
                if 40 <= r - l < 200:
                    eye_centres.append([round((l + r) / 2), round((t + b) / 2)])
    eye_centres = sorted({tuple(c) for c in eye_centres})[:2]

    anchors = {k: [round(v["x"] * VIEW), round(v["y"] * VIEW)] for k, v in asset.get("anchors", {}).items()}
    safe = asset.get("faceSafeZone")
    expression_zone = _union_bounds([eyes, brows, mouth])

    # The head above the hairline guide: the region a hair cap or a hat crown may cover.
    hairline_y = 330
    top_cap = paths.intersect(head, f"M {hl - 20} {ht - 20} L {hr + 20} {ht - 20} L {hr + 20} {hairline_y} L {hl - 20} {hairline_y} Z")
    # Head rows: half-width of the head at a few heights, for placing things on the sides.
    rows = {}
    for y in (150, 200, 250, 300, 330, 380, 420, 476, 550, 650, 750, 850):
        xs = [x for x in range(int(hl) - 2, int(hr) + 3, 2) if paths.contains(head, x, y)]
        if xs:
            rows[str(y)] = [xs[0], xs[-1]]

    neck = None
    if body:
        neck = [392, 632]

    return {
        "viewBox": VIEW,
        "notes": [
            "Coordinates are the 1024 character grid: x right, y down. The head never moves or changes shape.",
            "Draw hair and hats relative to head_outline, crown and head_rows; keep expression_zone readable.",
            "Bands: 20 rear hair (outside the head only), 34 body, 36 tops, 38 outerwear, 40 head, 50 face, 60 facial hair, 70 front hair, 80 eyewear, 90 headwear/front jewelry, 110 overlays.",
        ],
        "head_outline": {"type": "path", "d": head, "box": _round_box((hl, ht, hr, hb)),
                         "about": "The face fill. Crown at the top, widest near y 476, rounded chin at the bottom."},
        "head_inner_ring": {"type": "value", "value": 36, "about": "The head's outline ring is 36 units wide inside head_outline."},
        "crown": {"type": "point", "at": [512, round(ht)], "about": "Top of the head."},
        "chin": {"type": "point", "at": [512, round(hb)], "about": "Bottom of the chin."},
        "head_rows": {"type": "rows", "rows": rows, "about": "Left and right edge x of the head at each y."},
        "hairline_max": {"type": "line", "y": hairline_y,
                         "about": "Front hair and hat brims end at or above this y at the centre (style guide §2)."},
        "head_top_cap": {"type": "path", "d": top_cap, "about": "The head above hairline_max. A hair cap or beanie covers about this."},
        "eye_centres": {"type": "points", "at": [list(c) for c in eye_centres], "about": "Centres of the neutral eyes."},
        "eye_zone": {"type": "box", "box": _round_box(eyes), "about": "Union of every eye shape. Only eyewear and masks may cover it."},
        "brow_zone": {"type": "box", "box": _round_box(brows), "about": "Union of every brow shape. Bangs end at least 20 above eye_zone's top."},
        "mouth_zone": {"type": "box", "box": _round_box(mouth), "about": "Union of every mouth shape. Facial hair keeps a hole here."},
        "expression_zone": {"type": "box", "box": _round_box(expression_zone), "about": "Eyes, brows and mouth together. Hair, hats and jewelry stay out of it."},
        "face_safe_zone": {"type": "box", "box": [round(safe["left"] * VIEW), round(safe["top"] * VIEW), round(safe["right"] * VIEW), round(safe["bottom"] * VIEW)] if safe else None,
                           "about": "The manifest's face safe zone."},
        "anchors": {"type": "points", "named": anchors, "about": "Manifest anchors (temples, ears, jaw, neck, prop hand…)."},
        "neck": {"type": "span", "x": neck, "about": "Neck sides. The collar line is near y 960–1010 (bust framing shows more)."},
        "body_outline": {"type": "path", "d": body, "about": "Neck, shoulders and torso (band 34). Tops and outerwear cover it."},
    }


def overlay_svg() -> str:
    """The guides drawn as an SVG for the web UI."""
    g = guides()
    out = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="-256 -16 1536 1552">',
           '<g fill="none" stroke-width="3" vector-effect="non-scaling-stroke">']
    out.append(f'<path d="{g["head_outline"]["d"]}" stroke="#00a0ff"/>')
    out.append(f'<path d="{g["head_top_cap"]["d"]}" stroke="#00c070" stroke-dasharray="10 8"/>')
    out.append(f'<line x1="0" x2="1024" y1="{g["hairline_max"]["y"]}" y2="{g["hairline_max"]["y"]}" stroke="#00c070"/>')
    for key, colour in (("eye_zone", "#ff3b7f"), ("brow_zone", "#ff9f1a"), ("mouth_zone", "#ff3b7f")):
        box = g[key]["box"]
        if box:
            out.append(f'<rect x="{box[0]}" y="{box[1]}" width="{box[2] - box[0]}" height="{box[3] - box[1]}" stroke="{colour}"/>')
    out.append("</g></svg>")
    return "".join(out)
