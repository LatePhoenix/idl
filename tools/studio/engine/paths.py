"""Path data <-> Skia paths, plus the geometry helpers from ART_STUDIO.md §5.2.

Path strings use the picture subset (M, L, C, Q, Z, absolute). Parsing goes through
tools/asset_pipeline.py, so every SVG command the pipeline accepts works here too.
"""

from __future__ import annotations

import math
import sys
from pathlib import Path

import skia

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
import asset_pipeline as pipeline  # noqa: E402


class GeometryError(ValueError):
    pass


def parse(d: str) -> skia.Path:
    try:
        ops = pipeline.parse_path(d, "path")
    except pipeline.PipelineError as error:
        raise GeometryError(str(error)) from error
    path = skia.Path()
    for kind, c in ops:
        if kind == "M":
            path.moveTo(*c)
        elif kind == "L":
            path.lineTo(*c)
        elif kind == "C":
            path.cubicTo(*c)
        elif kind == "Q":
            path.quadTo(*c)
        elif kind == "Z":
            path.close()
        else:
            raise GeometryError(f"unsupported command {kind}")
    return path


def fmt(value: float) -> str:
    return pipeline.format_num(round(value, 1))


def to_d(path: skia.Path) -> str:
    """Serialize to absolute M/L/Q/C/Z. Conics (from circles and ovals) become quads."""
    out: list[str] = []
    iterator = skia.Path.Iter(path, False)
    while True:
        verb, pts = iterator.next()
        if verb == skia.Path.Verb.kDone_Verb:
            break
        if verb == skia.Path.Verb.kMove_Verb:
            out.append(f"M {fmt(pts[0].x())} {fmt(pts[0].y())}")
        elif verb == skia.Path.Verb.kLine_Verb:
            out.append(f"L {fmt(pts[1].x())} {fmt(pts[1].y())}")
        elif verb == skia.Path.Verb.kQuad_Verb:
            out.append(f"Q {fmt(pts[1].x())} {fmt(pts[1].y())} {fmt(pts[2].x())} {fmt(pts[2].y())}")
        elif verb == skia.Path.Verb.kConic_Verb:
            quads = skia.Path.ConvertConicToQuads(pts[0], pts[1], pts[2], iterator.conicWeight(), 2)
            for i in range(1, len(quads) - 1, 2):
                out.append(f"Q {fmt(quads[i].x())} {fmt(quads[i].y())} {fmt(quads[i + 1].x())} {fmt(quads[i + 1].y())}")
        elif verb == skia.Path.Verb.kCubic_Verb:
            out.append("C " + " ".join(f"{fmt(p.x())} {fmt(p.y())}" for p in pts[1:4]))
        elif verb == skia.Path.Verb.kClose_Verb:
            out.append("Z")
    return " ".join(out)


def is_empty(d: str) -> bool:
    return not d or not d.strip() or parse(d).isEmpty()


def bounds(d: str) -> tuple[float, float, float, float]:
    """Tight bounds (left, top, right, bottom)."""
    if not d:
        return (0.0, 0.0, 0.0, 0.0)
    r = parse(d).computeTightBounds()
    return r.left(), r.top(), r.right(), r.bottom()


def _op(a: str, b: str, op) -> str:
    result = skia.Op(parse(a), parse(b), op)
    if result is None:
        raise GeometryError("path operation failed")
    return to_d(result)


def union(*ds: str) -> str:
    out = ds[0]
    for d in ds[1:]:
        out = _op(out, d, skia.PathOp.kUnion_PathOp)
    return out


def subtract(a: str, b: str) -> str:
    return _op(a, b, skia.PathOp.kDifference_PathOp)


def intersect(a: str, b: str) -> str:
    return _op(a, b, skia.PathOp.kIntersect_PathOp)


def _stroke_outline(path: skia.Path, width: float, cap=skia.Paint.kRound_Cap) -> skia.Path:
    paint = skia.Paint(Style=skia.Paint.kStroke_Style, StrokeWidth=width, StrokeJoin=skia.Paint.kRound_Join, StrokeCap=cap)
    out = skia.Path()
    paint.getFillPath(path, out)
    return skia.Simplify(out) or out


def offset(d: str, distance: float) -> str:
    """Grow (distance > 0) or shrink (< 0) a closed outline, with round corners."""
    path = parse(d)
    if distance == 0:
        return to_d(path)
    ring = _stroke_outline(path, 2 * abs(distance))
    op = skia.PathOp.kUnion_PathOp if distance > 0 else skia.PathOp.kDifference_PathOp
    return to_d(skia.Op(path, ring, op))


def thicken(d: str, width: float, cap: str = "round") -> str:
    """A filled band of `width` along an open or closed path (a stroke turned into a fill)."""
    caps = {"round": skia.Paint.kRound_Cap, "butt": skia.Paint.kButt_Cap, "square": skia.Paint.kSquare_Cap}
    return to_d(_stroke_outline(parse(d), width, caps[cap]))


def transform(d: str, *, dx: float = 0, dy: float = 0, scale: float = 1, sx: float | None = None,
              sy: float | None = None, rotate: float = 0, cx: float = 512, cy: float = 512) -> str:
    m = skia.Matrix()
    m.preTranslate(dx, dy)
    m.preTranslate(cx, cy)
    m.preRotate(rotate)
    m.preScale(sx if sx is not None else scale, sy if sy is not None else scale)
    m.preTranslate(-cx, -cy)
    path = parse(d)
    path.transform(m)
    return to_d(path)


def mirror(d: str, axis: float = 512) -> str:
    """Reflect across the vertical line x = axis."""
    return transform(d, sx=-1, sy=1, cx=axis, cy=0)


def symmetric(d: str, axis: float = 512) -> str:
    """The shape plus its mirror image, merged."""
    return union(d, mirror(d, axis))


def simplify(d: str) -> str:
    """Remove self-overlaps and redundant contours."""
    return to_d(skia.Simplify(parse(d)))


def smooth_closed(points: list[tuple[float, float]], tension: float = 1.0) -> str:
    """A closed Catmull-Rom curve through points, as cubics. The easiest way to draw organic shapes."""
    if len(points) < 3:
        raise GeometryError("smooth_closed needs at least 3 points")
    n = len(points)
    out = [f"M {fmt(points[0][0])} {fmt(points[0][1])}"]
    for i in range(n):
        p0, p1, p2, p3 = points[i - 1], points[i], points[(i + 1) % n], points[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) * tension / 6, p1[1] + (p2[1] - p0[1]) * tension / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) * tension / 6, p2[1] - (p3[1] - p1[1]) * tension / 6)
        out.append(f"C {fmt(c1[0])} {fmt(c1[1])} {fmt(c2[0])} {fmt(c2[1])} {fmt(p2[0])} {fmt(p2[1])}")
    out.append("Z")
    return " ".join(out)


def smooth_open(points: list[tuple[float, float]], tension: float = 1.0) -> str:
    """An open Catmull-Rom curve through points (for strokes and guides)."""
    if len(points) < 2:
        raise GeometryError("smooth_open needs at least 2 points")
    pts = [points[0]] + list(points) + [points[-1]]
    out = [f"M {fmt(points[0][0])} {fmt(points[0][1])}"]
    for i in range(1, len(pts) - 2):
        p0, p1, p2, p3 = pts[i - 1], pts[i], pts[i + 1], pts[i + 2]
        c1 = (p1[0] + (p2[0] - p0[0]) * tension / 6, p1[1] + (p2[1] - p0[1]) * tension / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) * tension / 6, p2[1] - (p3[1] - p1[1]) * tension / 6)
        out.append(f"C {fmt(c1[0])} {fmt(c1[1])} {fmt(c2[0])} {fmt(c2[1])} {fmt(p2[0])} {fmt(p2[1])}")
    return " ".join(out)


def ellipse(cx: float, cy: float, rx: float, ry: float) -> str:
    path = skia.Path()
    path.addOval(skia.Rect.MakeLTRB(cx - rx, cy - ry, cx + rx, cy + ry))
    return to_d(path)


def contains(d: str, x: float, y: float) -> bool:
    return parse(d).contains(x, y)


def area(d: str, step: float = 4) -> float:
    """Approximate filled area by sampling (good enough for lint thresholds)."""
    if not d:
        return 0.0
    path = parse(d)
    l, t, r, b = bounds(d)
    count = 0
    y = t + step / 2
    while y < b:
        x = l + step / 2
        while x < r:
            if path.contains(x, y):
                count += 1
            x += step
        y += step
    return count * step * step


def contours(d: str) -> list[str]:
    """Split a path into its subpaths."""
    out = []
    for chunk in to_d(parse(d)).split("M ")[1:]:
        out.append("M " + chunk.strip())
    return out


def thin_area(d: str, minimum: float) -> float:
    """Area that disappears when the shape is opened by minimum/2: the parts thinner than `minimum`."""
    half = minimum / 2
    inner = offset(d, -half)
    opened = "" if is_empty(inner) else offset(inner, half)
    whole = area(d)
    return whole - (area(opened) if opened else 0.0)


def polyline_length(points: list[tuple[float, float]]) -> float:
    return sum(math.dist(a, b) for a, b in zip(points, points[1:]))
