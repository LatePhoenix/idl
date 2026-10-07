"""Style lint: ART_STYLE_GUIDE.md and the picture validator as code (ART_STUDIO.md §5.4).

Errors block promotion. Warnings are shown to the operator and to Claude.
"""

from __future__ import annotations

from . import guides, kits, legibility, paths

CHARACTER_BANDS = {0, 10, 20, 30, 34, 36, 38, 40, 50, 60, 70, 80, 90, 100, 110}
BODY_REGION_BANDS = {0, 20, 34, 36, 38}
MIN_DETAIL = 32
OUTLINE_STROKE = (24, 32)
FEATURE_STROKE = (40, 64)


def _issue(level, rule, message, part=None):
    out = {"level": level, "rule": rule, "message": message}
    if part:
        out["part"] = part
    return out


def _control_points(d):
    pts = []
    for kind, coords in paths.pipeline.parse_path(d, "path"):
        pts += list(zip(coords[0::2], coords[1::2]))
    return pts


def _box_path(box):
    l, t, r, b = box
    return f"M {l} {t} L {r} {t} L {r} {b} L {l} {b} Z"


def _covered(d, box):
    """Area of d inside box."""
    try:
        return paths.area(paths.intersect(d, _box_path(box)))
    except paths.GeometryError:
        return 0.0


def validate(picture: dict, meta: dict) -> list[dict]:
    """Mirror of VectorPictureValidator for a draft."""
    out = []
    slots = set(meta["colorSlots"])
    ids = [p["id"] for p in picture["parts"]]
    for dup in sorted({i for i in ids if ids.count(i) > 1}):
        out.append(_issue("error", "validator", f"duplicate part id {dup}"))
    masks = [p.get("publishMask") for p in picture["parts"] if p.get("publishMask")]
    for dup in sorted({m for m in masks if masks.count(m) > 1}):
        out.append(_issue("error", "validator", f"publishes mask {dup} more than once"))
    for part in picture["parts"]:
        pid, band = part["id"], part["zBand"]
        if band not in CHARACTER_BANDS:
            out.append(_issue("error", "validator", f"zBand {band} is not a character band", pid))
        fill = part["fill"]
        used = [fill["slot"]] if fill.get("slot") else [s["slot"] for s in (fill.get("linear") or fill.get("radial") or {}).get("stops", [])]
        if part.get("stroke"):
            used.append(part["stroke"]["slot"])
            if not 16 <= part["stroke"]["width"] <= 96:
                out.append(_issue("error", "validator", f"stroke width {part['stroke']['width']} outside 16..96", pid))
        for slot in used:
            if slot not in slots:
                out.append(_issue("error", "validator", f"slot {slot} is not declared in the draft's colorSlots", pid))
        if not part.get("allowOverflow"):
            body = band in BODY_REGION_BANDS
            lo_x, hi_x, hi_y = (-256, 1280, 1536) if body else (-16, 1040, 1040)
            if any(not (lo_x <= x <= hi_x and -16 <= y <= hi_y) for x, y in _control_points(part["commands"])):
                out.append(_issue("error", "validator", f"geometry outside the {'body region' if body else '-16..1040 box'}", pid))
        if picture["schemaVersion"] == 1 and (part.get("stroke") or part.get("tags") or part.get("clipBy") or part.get("publishMask")):
            out.append(_issue("error", "validator", "version 2 fields on schema 1", pid))
    return out


def style(picture: dict, meta: dict) -> list[dict]:
    out = []
    kit = kits.load(meta["category"])
    g = guides.guides()
    head = g["head_outline"]["d"]
    parts = picture["parts"]
    drawn = [p for p in parts if p.get("opacity", 1) > 0]
    by_name = {p["id"]: p for p in parts}

    for part in parts:
        if part["zBand"] not in kit["bands"]:
            out.append(_issue("error", "bands", f"band {part['zBand']} is not allowed for {kit['category']} (allowed {kit['bands']})", part["id"]))
    for skel in kit["skeleton"]:
        if skel.get("required") and not skel.get("clipPath") and skel["part"] not in by_name:
            out.append(_issue("warn", "skeleton", f"missing the kit's '{skel['part']}' part: {skel['about']}"))
    if not drawn:
        out.append(_issue("error", "empty", "no visible parts"))
        return out

    for part in drawn:
        d, pid = part["commands"], part["id"]
        try:
            area = paths.area(d)
        except paths.GeometryError as error:
            out.append(_issue("error", "geometry", str(error), pid))
            continue
        if area < 32 * 32:
            out.append(_issue("warn", "detail", f"tiny part (area {area:.0f} < 1024 units²); it disappears at 48 px", pid))
        elif not part.get("stroke"):
            thin = paths.thin_area(d, MIN_DETAIL)
            if thin > 0.25 * area:
                out.append(_issue("warn", "detail", f"{thin / area:.0%} of the part is thinner than {MIN_DETAIL} units", pid))
        stroke = part.get("stroke")
        if stroke and stroke["slot"] == "outline" and not OUTLINE_STROKE[0] <= stroke["width"] <= OUTLINE_STROKE[1]:
            out.append(_issue("warn", "stroke", f"item outlines are {OUTLINE_STROKE[0]}-{OUTLINE_STROKE[1]} wide", pid))

    category = kit["category"]
    eye_zone, brow_zone = g["eye_zone"]["box"], g["brow_zone"]["box"]
    if category == "hair":
        front = [p for p in drawn if p["zBand"] == 70]
        for part in front:
            centre = paths.intersect(part["commands"], _box_path((400, -16, 624, 1040)))
            if not paths.is_empty(centre):
                bottom = paths.bounds(centre)[3]
                if bottom > g["hairline_max"]["y"] + 6:
                    out.append(_issue("error", "hairline", f"front hair reaches y {bottom:.0f} at the centre (limit {g['hairline_max']['y']})", part["id"]))
            if _covered(part["commands"], eye_zone) > 64:
                out.append(_issue("error", "eyes", "front hair covers the eye zone", part["id"]))
            elif _covered(part["commands"], brow_zone) > 400:
                out.append(_issue("warn", "brows", "front hair covers part of the brow zone (raised brows reach y ~271)", part["id"]))
        for part in [p for p in drawn if p["zBand"] == 20]:
            total = paths.area(part["commands"])
            outside = paths.area(paths.subtract(part["commands"], head)) if total else 0
            if total and outside < 0.15 * total:
                out.append(_issue("warn", "hair_back", "rear hair is almost all hidden behind the head", part["id"]))
        if not any(p["fill"].get("slot") == "hair.highlight" for p in drawn):
            out.append(_issue("error", "highlight", "hair needs a visible hair.highlight part (the user's highlight color)"))
        top = [p for p in drawn if "hair_top" in (p.get("tags") or [])]
        if top and not all(any(c["mask"] == "occlude.hair_top" for c in p.get("clipBy") or []) for p in top):
            out.append(_issue("warn", "masks", "hair_top parts should clipBy occlude.hair_top:difference so hats can hide them"))
        if not any(p["fill"].get("slot") == "hair.shadow" for p in drawn):
            out.append(_issue("warn", "shade", "no hair.shadow shade part"))
    elif category == "head_accessory":
        front = [p for p in drawn if p["zBand"] == 90]
        for part in front:
            if _covered(part["commands"], eye_zone) > 64 and not meta.get("coversEyes"):
                out.append(_issue("error", "eyes", "headwear covers the eye zone", part["id"]))
            centre = paths.intersect(part["commands"], _box_path((400, -16, 624, 1040)))
            if not paths.is_empty(centre) and paths.bounds(centre)[3] > g["hairline_max"]["y"] + 6:
                out.append(_issue("error", "lower_edge", f"lower edge at y {paths.bounds(centre)[3]:.0f} at the centre (limit {g['hairline_max']['y']})", part["id"]))
        if front:
            l, t, r, b = paths.bounds(paths.union(*[p["commands"] for p in front]))
            if t > 140:
                out.append(_issue("warn", "fit", f"the hat's top is at y {t:.0f}, below the head's crown (y 96): it sits on the forehead, not on the head"))
            if r - l < 560:
                out.append(_issue("warn", "fit", f"the hat is {r - l:.0f} wide; the head is ~680 wide at y 300, so it looks too small"))
        if not any(p.get("publishMask") == "occlude.hair_top" for p in parts):
            out.append(_issue("warn", "masks", "publish occlude.hair_top (an opacity-0 part is fine) so hair stays under the hat"))
    elif category == "face_accessory":
        limit = kit["rules"]["tintedMaxLensOpacity"] if meta.get("tinted") else kit["rules"]["maxLensOpacity"]
        for part in drawn:
            if part["fill"].get("slot") == "glasses.lens" and part.get("opacity", 1) > limit:
                out.append(_issue("error", "lens", f"lens opacity {part.get('opacity', 1)} > {limit}{' (tinted)' if meta.get('tinted') else ''}", part["id"]))
        frames = [p for p in drawn if p["fill"].get("slot") == "glasses.frame" or (p.get("stroke") or {}).get("slot") == "glasses.frame"]
        if frames:
            l, t, r, b = paths.bounds(paths.union(*[p["commands"] for p in frames]))
            if abs((l + r) / 2 - 512) > 12:
                out.append(_issue("warn", "centre", f"frames are centred at x {(l + r) / 2:.0f}, not 512"))
    elif category == "facial_hair":
        mouth = (392, 515, 632, 665)
        for part in drawn:
            clipped = part.get("clip") and part["clip"]["mode"] == "difference"
            if not clipped and _covered(part["commands"], mouth) > 600:
                out.append(_issue("error", "mouth", "facial hair covers the mouth without a mouth_hole difference clip", part["id"]))
    return out


def lint(picture: dict, meta: dict, library=None) -> list[dict]:
    out = validate(picture, meta) + style(picture, meta)
    if library is not None and not any(i["level"] == "error" and i["rule"] == "validator" for i in out):
        result = legibility.measure(library, meta["id"], eyewear=meta["category"] == "face_accessory")
        out += legibility.issues(result, covers_eyes=bool(meta.get("coversEyes")))
        out.append(_issue("info", "legibility", "48 px visibility: " + ", ".join(f"{k} {v:.0%}" for k, v in result["zones"].items())))
    order = {"error": 0, "warn": 1, "info": 2}
    return sorted(out, key=lambda i: order[i["level"]])
