"""Skia renderer for previews and sheets. Mirrors AvatarRenderer / CanvasVectorAssetRenderer /
CompositeOrder / ColorSlots the same way web/render.js does. Android draws with Skia too, so
anti-aliasing at 48 px is close to the device. PackContactSheetTest goldens stay the truth.
"""

from __future__ import annotations

import math
from dataclasses import dataclass, field

import numpy as np
import skia

from . import paths, repo

FRAMING = {"head": (-40.0, 0.0, 1104.0), "bust": (-128.0, 32.0, 1280.0)}
CATEGORY_Z = {
    "base": 20, "signature_feature": 30, "face_eye": 50, "face_brow": 60, "face_mouth": 70,
    "expression_overlay": 80, "face_accessory": 90, "head_accessory": 100, "body_accessory": 110,
    "foreground_prop": 120, "scene": 10, "frame": 130, "hair": 25, "facial_hair": 35, "jewelry": 95,
    "top": 36, "outerwear": 38,
}
NEUTRAL = (0x9E, 0x9E, 0x9E, 255)
WALLS = {"light": (0xEC, 0xE7, 0xDF), "dark": (0x1C, 0x1C, 0x1F)}


def parse_hex(raw):
    if not isinstance(raw, str):
        return None
    h = raw.lstrip("#")
    if any(c not in "0123456789abcdefABCDEF" for c in h):
        return None
    try:
        if len(h) == 6:
            return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)
        if len(h) == 8:
            return (int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16), int(h[0:2], 16))
    except ValueError:
        return None
    return None


def pack_slot_links() -> dict[str, str]:
    """Merge pack defaults.slotLinks across packs (same merge as AssetManifest / web UI)."""
    links: dict[str, str] = {}
    for pack in repo.load_packs():
        links.update((pack.manifest.get("defaults") or {}).get("slotLinks") or {})
    return links


def _srgb_to_linear(c: float) -> float:
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def _linear_to_srgb(c: float) -> int:
    clipped = max(0.0, min(1.0, c))
    encoded = 12.92 * clipped if clipped <= 0.0031308 else 1.055 * clipped ** (1 / 2.4) - 0.055
    return max(0, min(255, int(encoded * 255)))


def _from_argb(c: tuple[int, int, int, int]) -> tuple[float, float, float]:
    r, g, b = _srgb_to_linear(c[0] / 255), _srgb_to_linear(c[1] / 255), _srgb_to_linear(c[2] / 255)
    l_ = math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
    m_ = math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
    s_ = math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
    L = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
    a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
    b_lab = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
    chroma = math.sqrt(a * a + b_lab * b_lab)
    hue = math.degrees(math.atan2(b_lab, a))
    if hue < 0:
        hue += 360
    return L, chroma, hue


def _to_opaque(ok: tuple[float, float, float]) -> tuple[int, int, int, int]:
    L, chroma, hue = ok
    h_rad = math.radians(hue)
    a = chroma * math.cos(h_rad)
    b_lab = chroma * math.sin(h_rad)
    l_ = L + 0.3963377774 * a + 0.2158037573 * b_lab
    m_ = L - 0.1055613458 * a - 0.0638541728 * b_lab
    s_ = L - 0.0894841775 * a - 1.2914855480 * b_lab
    l, m, s = l_ ** 3, m_ ** 3, s_ ** 3
    return (
        _linear_to_srgb(+4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s),
        _linear_to_srgb(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s),
        _linear_to_srgb(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s),
        255,
    )


def derive_shadow(primary: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    L, chroma, hue = _from_argb(primary)
    return _to_opaque((max(0.0, min(1.0, L - 0.12)), chroma * 1.05, hue))


def derive_highlight(primary: tuple[int, int, int, int]) -> tuple[int, int, int, int]:
    L, chroma, hue = _from_argb(primary)
    return _to_opaque((max(0.0, min(1.0, L + 0.10)), chroma * 0.9, hue))


def resolve_colors(assets, overrides=None, unlinked=(), slot_links=None):
    """ColorSlots.resolve (AP-9): overrides, pack slot links, OKLCH shadow/highlight, defaults."""
    overrides = overrides or {}
    unlinked = set(unlinked)
    slot_links = slot_links if slot_links is not None else pack_slot_links()
    declared: dict[str, str] = {}
    for asset in assets:
        if not asset.get("picture"):
            continue
        for slot, hex_ in (asset.get("colorSlots") or {}).items():
            declared.setdefault(slot, hex_)
    slots = sorted(set(declared) | set(overrides) | set(slot_links))
    memo: dict[str, tuple[int, int, int, int]] = {}
    visiting: set[str] = set()

    def resolve_one(slot: str) -> tuple[int, int, int, int]:
        if slot in memo:
            return memo[slot]
        if slot in visiting:
            return NEUTRAL
        visiting.add(slot)
        try:
            override = parse_hex(overrides.get(slot))
            if override is not None:
                memo[slot] = override
                return override
            if slot not in unlinked:
                source = slot_links.get(slot)
                if source:
                    memo[slot] = resolve_one(source)
                    return memo[slot]
                suffix = ".shadow" if slot.endswith(".shadow") else ".highlight" if slot.endswith(".highlight") else None
                if suffix:
                    primary_slot = slot[: -len(suffix)] + ".primary"
                    if parse_hex(overrides.get(primary_slot)) is not None:
                        primary = resolve_one(primary_slot)
                        memo[slot] = derive_shadow(primary) if suffix == ".shadow" else derive_highlight(primary)
                        return memo[slot]
            memo[slot] = parse_hex(declared.get(slot)) or NEUTRAL
            return memo[slot]
        finally:
            visiting.discard(slot)

    for slot in slots:
        resolve_one(slot)
    return memo


def draw_ops(assets):
    ops = []
    for asset in sorted(assets, key=lambda a: a["id"]):
        picture = asset.get("picture")
        if not picture:
            continue
        for index, part in enumerate(picture["parts"]):
            ops.append((part["zBand"], CATEGORY_Z.get(asset["category"], 0), asset["id"], index, asset, part))
    ops.sort(key=lambda o: o[:4])
    return ops


def _color(c, opacity=1.0):
    return skia.Color(c[0], c[1], c[2], int(max(0, min(1, (c[3] / 255) * opacity)) * 255))


def _path(d, rule="nonzero"):
    path = paths.parse(d)
    path.setFillType(skia.PathFillType.kEvenOdd if rule == "evenodd" else skia.PathFillType.kWinding)
    return path


def draw_avatar(canvas: skia.Canvas, size: float, assets, *, framing="head", frame="squircle",
                overrides=None, unlinked=(), slot_links=None):
    colors = resolve_colors(assets, overrides, unlinked, slot_links)
    ops = draw_ops(assets)
    masks = {}
    for band, _, asset_id, index, asset, part in sorted(ops, key=lambda o: (o[0], o[2], o[3])):
        name = part.get("publishMask")
        if name and name not in masks:
            masks[name] = (asset_id, _path(part["commands"]))
    ox, oy, fsize = FRAMING[framing]
    canvas.save()
    if frame == "circle":
        clip = skia.Path()
        clip.addCircle(size / 2, size / 2, size / 2)
        canvas.clipPath(clip, doAntiAlias=True)
    elif frame == "squircle":
        clip = skia.Path()
        clip.addRRect(skia.RRect.MakeRectXY(skia.Rect.MakeWH(size, size), size * 0.26, size * 0.26))
        canvas.clipPath(clip, doAntiAlias=True)
    for band, _, asset_id, index, asset, part in ops:
        if band >= 200:
            continue
        picture = asset["picture"]
        canvas.save()
        if band != 0:
            scale = size / fsize
            canvas.scale(scale, scale)
            canvas.translate(-ox, -oy)
        else:
            canvas.scale(size / 1024, size / 1024)
        _apply_transform(canvas, asset.get("transform"))
        _apply_transform(canvas, asset.get("defaultTransform"))
        clip = part.get("clip")
        if clip:
            source = next((c for c in picture.get("clipPaths", []) if c["id"] == clip["id"]), None)
            if source:
                op = skia.ClipOp.kDifference if clip["mode"] == "difference" else skia.ClipOp.kIntersect
                canvas.clipPath(_path(source["commands"]), op, True)
        for sub in part.get("clipBy") or []:
            mask = masks.get(sub["mask"])
            if mask and mask[0] != asset_id:
                op = skia.ClipOp.kDifference if sub["mode"] == "difference" else skia.ClipOp.kIntersect
                canvas.clipPath(mask[1], op, True)
        path = _path(part["commands"], part.get("fillRule", "nonzero"))
        opacity = float(part.get("opacity", 1.0))
        fill = part["fill"]
        paint = skia.Paint(AntiAlias=True)
        if fill.get("slot"):
            paint.setColor(_color(colors.get(fill["slot"], NEUTRAL), opacity))
        else:
            grad = fill.get("linear") or fill.get("radial")
            stops = grad["stops"]
            cols = []
            for stop in stops:
                c = colors.get(stop["slot"], NEUTRAL)
                if stop.get("alpha") is not None:
                    c = (c[0], c[1], c[2], int(stop["alpha"] * 255))
                cols.append(_color(c))
            pos = [s["offset"] for s in stops]
            if fill.get("linear"):
                shader = skia.GradientShader.MakeLinear([skia.Point(grad["x1"], grad["y1"]), skia.Point(grad["x2"], grad["y2"])], cols, pos)
            else:
                shader = skia.GradientShader.MakeRadial(skia.Point(grad["cx"], grad["cy"]), grad["r"], cols, pos)
            paint.setShader(shader)
            paint.setAlphaf(max(0.0, min(1.0, opacity)))
        canvas.drawPath(path, paint)
        stroke = part.get("stroke")
        if stroke:
            sp = skia.Paint(AntiAlias=True, Style=skia.Paint.kStroke_Style, StrokeWidth=stroke["width"])
            sp.setStrokeCap({"butt": skia.Paint.kButt_Cap, "square": skia.Paint.kSquare_Cap}.get(stroke.get("cap"), skia.Paint.kRound_Cap))
            sp.setStrokeJoin({"miter": skia.Paint.kMiter_Join, "bevel": skia.Paint.kBevel_Join}.get(stroke.get("join"), skia.Paint.kRound_Join))
            sp.setColor(_color(colors.get(stroke["slot"], NEUTRAL), opacity))
            canvas.drawPath(path, sp)
        canvas.restore()
    canvas.restore()
    return colors


def _apply_transform(canvas, t):
    if not t:
        return
    if t.get("flipHorizontal"):
        canvas.translate(1024, 0)
        canvas.scale(-1, 1)
    s = t.get("scale", 1.0)
    if s != 1.0:
        canvas.translate(512, 512)
        canvas.scale(s, s)
        canvas.translate(-512, -512)
    if t.get("rotationDeg"):
        canvas.rotate(t["rotationDeg"], 512, 512)
    canvas.translate(t.get("translateX", 0), t.get("translateY", 0))


def render(assets, size=96, *, framing="head", frame="squircle", wall=None, overrides=None,
           unlinked=(), slot_links=None) -> np.ndarray:
    """RGBA pixels (size, size, 4). `wall` paints a wallpaper behind the frame."""
    surface = skia.Surface(size, size)
    canvas = surface.getCanvas()
    canvas.clear(skia.Color(*WALLS[wall]) if wall else skia.ColorTRANSPARENT)
    draw_avatar(canvas, size, assets, framing=framing, frame=frame, overrides=overrides,
                unlinked=unlinked, slot_links=slot_links)
    return surface.makeImageSnapshot().toarray(colorType=skia.kRGBA_8888_ColorType)


# ---------- sheets ----------

@dataclass
class Cell:
    assets: list
    label: str
    size: int = 96
    framing: str = "head"
    wall: str = "light"
    zoom: int = 1
    overrides: dict = field(default_factory=dict)


def sheet(rows: list[tuple[str, list[Cell]]], *, title: str = "") -> skia.Image:
    """Rows of labelled cells. A cell with zoom > 1 is drawn at its true size and scaled up with
    nearest-neighbour sampling, so 48 px detail is visible."""
    pad, label_h, row_title_h = 12, 18, 22
    font = skia.Font(skia.Typeface("Segoe UI"), 13)
    title_h = 30 if title else 0
    widths = [sum(c.size * c.zoom + pad for c in cells) + pad for _, cells in rows]
    heights = [row_title_h + max(c.size * c.zoom for c in cells) + label_h + pad for _, cells in rows]
    width, height = max(widths + [320]), title_h + sum(heights) + pad
    surface = skia.Surface(int(width), int(height))
    canvas = surface.getCanvas()
    canvas.clear(skia.Color(0xF6, 0xF4, 0xF0))
    ink = skia.Paint(AntiAlias=True, Color=skia.Color(0x33, 0x30, 0x2C))
    if title:
        canvas.drawString(title, pad, 21, skia.Font(skia.Typeface("Segoe UI"), 16), ink)
    y = title_h
    for (row_title, cells), row_h in zip(rows, heights):
        canvas.drawString(row_title, pad, y + 16, font, ink)
        x = pad
        top = y + row_title_h
        for cell in cells:
            box = cell.size * cell.zoom
            wall = skia.Paint(Color=skia.Color(*WALLS[cell.wall]))
            canvas.drawRect(skia.Rect.MakeXYWH(x - 4, top - 4, box + 8, box + 8), wall)
            pixels = render(cell.assets, cell.size, framing=cell.framing, wall=cell.wall, overrides=cell.overrides)
            image = skia.Image.fromarray(np.ascontiguousarray(pixels), colorType=skia.kRGBA_8888_ColorType)
            sampling = skia.SamplingOptions(skia.FilterMode.kNearest) if cell.zoom > 1 else skia.SamplingOptions(skia.FilterMode.kLinear)
            canvas.drawImageRect(image, skia.Rect.MakeXYWH(x, top, box, box), sampling)
            canvas.drawString(cell.label, x, top + box + 15, font, ink)
            x += box + pad
        y += row_h
    return surface.makeImageSnapshot()


def save_png(image: skia.Image, path) -> None:
    image.save(str(path), skia.kPNG)


def sheet_size(rows: list[tuple[str, list[Cell]]], *, title: str = "") -> tuple[int, int]:
    """Pixel size of a sheet built from the same layout as `sheet`."""
    pad, label_h, row_title_h = 12, 18, 22
    title_h = 30 if title else 0
    widths = [sum(c.size * c.zoom + pad for c in cells) + pad for _, cells in rows]
    heights = [row_title_h + max(c.size * c.zoom for c in cells) + label_h + pad for _, cells in rows]
    return max(widths + [320]), title_h + sum(heights) + pad


EXPRESSIONS_SHEET = ("neutral_face", "star_struck", "crying_face", "sleeping_face")
SKIN_TONES = (
    ("light", {"face.primary": "#F3E0C8"}),
    ("deep", {"face.primary": "#4A2810"}),
)


def standard_sheet(library, focus_id: str, *, with_items: list[str] | None = None,
                   expression: str = "neutral_face") -> skia.Image:
    """ST-1 standard render sheet: head 48(4×)/96/192 light+dark, bust 256, kit try-ons,
    two skins, four expressions at 96."""
    from . import kits  # local: avoid cycle at import for non-skia callers

    focus = library.get(focus_id)
    items = list(with_items or [])
    if focus_id not in items:
        items = [focus_id] + items
    base_look = library.look(items, expression)
    rows: list[tuple[str, list[Cell]]] = []

    for wall in ("light", "dark"):
        cells = [
            Cell(base_look, "48 (4×)", size=48, zoom=4, wall=wall),
            Cell(base_look, "96", size=96, wall=wall),
            Cell(base_look, "192", size=192, wall=wall),
        ]
        rows.append((f"Head · {wall}", cells))
    rows.append(("Bust 256", [Cell(base_look, "bust", size=256, framing="bust", wall="light")]))

    try:
        kit = kits.load(focus["category"])
        for i, set_ids in enumerate(kit.get("tryOn") or []):
            worn = library.look([focus_id, *set_ids], expression)
            rows.append((f"Try-on {i + 1}: {', '.join(set_ids)}",
                         [Cell(worn, "128", size=128, wall="light")]))
    except ValueError:
        pass

    skin_cells = [
        Cell(library.look(items, expression), label, size=96, wall="light", overrides=dict(ov))
        for label, ov in SKIN_TONES
    ]
    rows.append(("Skin tones", skin_cells))

    expr_cells = [
        Cell(library.look(items, expr), expr.replace("_", " "), size=96, wall="light")
        for expr in EXPRESSIONS_SHEET
    ]
    rows.append(("Expressions", expr_cells))
    return sheet(rows, title=focus_id)
