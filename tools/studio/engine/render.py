"""Skia renderer for previews and sheets. Mirrors AvatarRenderer / CanvasVectorAssetRenderer /
CompositeOrder / ColorSlots the same way web/render.js does. Android draws with Skia too, so
anti-aliasing at 48 px is close to the device. PackContactSheetTest goldens stay the truth.
"""

from __future__ import annotations

from dataclasses import dataclass, field

import numpy as np
import skia

from . import paths

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
    try:
        if len(h) == 6:
            return (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)
        if len(h) == 8:
            return (int(h[2:4], 16), int(h[4:6], 16), int(h[6:8], 16), int(h[0:2], 16))
    except ValueError:
        return None
    return None


def _mix(c, toward, t):
    ch = lambda a, b: max(0, min(255, int(a * (1 - t) + b * t)))  # noqa: E731 (matches Kotlin toInt)
    return (ch(c[0], toward[0]), ch(c[1], toward[1]), ch(c[2], toward[2]), 255)


def resolve_colors(assets, overrides=None, unlinked=()):
    overrides = overrides or {}
    declared = {}
    for asset in assets:
        if not asset.get("picture"):
            continue
        for slot, hex_ in (asset.get("colorSlots") or {}).items():
            declared.setdefault(slot, hex_)
    colors = {}
    for slot in sorted(declared):
        colors[slot] = parse_hex(overrides.get(slot)) or _derived(slot, overrides, unlinked) or parse_hex(declared[slot]) or NEUTRAL
    return colors


def _derived(slot, overrides, unlinked):
    suffix = ".shadow" if slot.endswith(".shadow") else ".highlight" if slot.endswith(".highlight") else None
    if suffix is None or slot in unlinked:
        return None
    primary = parse_hex(overrides.get(slot[: -len(suffix)] + ".primary"))
    if primary is None:
        return None
    return _mix(primary, (0, 0, 0), 0.25) if suffix == ".shadow" else _mix(primary, (255, 255, 255), 0.30)


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


def draw_avatar(canvas: skia.Canvas, size: float, assets, *, framing="head", frame="squircle", overrides=None, unlinked=()):
    colors = resolve_colors(assets, overrides, unlinked)
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
        opacity = part.get("opacity", 1.0)
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


def render(assets, size=96, *, framing="head", frame="squircle", wall=None, overrides=None) -> np.ndarray:
    """RGBA pixels (size, size, 4). `wall` paints a wallpaper behind the frame."""
    surface = skia.Surface(size, size)
    canvas = surface.getCanvas()
    canvas.clear(skia.Color(*WALLS[wall]) if wall else skia.ColorTRANSPARENT)
    draw_avatar(canvas, size, assets, framing=framing, frame=frame, overrides=overrides)
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
