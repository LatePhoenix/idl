"""Shared teardrop geometry for the brand mark and the avatar base (D-45).

The 108-unit path lives in config/teardrop_silhouette.json. This module maps it onto the
1024 grid and builds the inward outline with the same Tiller-Hanson offset gen_brand.py
uses for stroked curves.
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
KAPPA = 0.5522847498
OUTLINE_INSET = 36.0


def load_config():
    return json.loads((ROOT / "config" / "teardrop_silhouette.json").read_text(encoding="utf-8"))


def map_point(x, y, mapping):
    scale = mapping["scaleNumerator"] / mapping["scaleDenominator"]
    return (
        mapping["translateX"] + (x - mapping["originX"]) * scale,
        mapping["translateY"] + (y - mapping["originY"]) * scale,
    )


def mapped_segments(config=None):
    """Closed path on the 1024 grid: list of ('C'|'Q', points) excluding the closing Z."""
    config = config or load_config()
    mapping = config["mapTo1024"]
    segments = []
    cursor = None
    for seg in config["path"]:
        op = seg["op"]
        pts = [map_point(p[0], p[1], mapping) for p in seg.get("points", [])]
        if op == "M":
            cursor = pts[0]
        elif op == "Z":
            break
        else:
            segments.append((op, [cursor, *pts]))
            cursor = pts[-1]
    return segments


def fmt(v):
    rounded = round(v, 1)
    if abs(rounded - round(rounded)) < 1e-9:
        return str(int(round(rounded)))
    return f"{rounded:.1f}"


def path_commands(segments):
    parts = [f"M {fmt(segments[0][1][0][0])} {fmt(segments[0][1][0][1])}"]
    for op, pts in segments:
        coords = " ".join(f"{fmt(x)} {fmt(y)}" for x, y in pts[1:])
        parts.append(f"{op} {coords}")
    parts.append("Z")
    return " ".join(parts)


def sample(segments, per_segment=80):
    points = []
    for op, pts in segments:
        for i in range(per_segment):
            points.append(point_at(op, pts, i / per_segment))
    return points


def point_at(op, pts, t):
    if op == "L":
        return lerp(pts[0], pts[1], t)
    if op == "Q":
        return quad(pts[0], pts[1], pts[2], t)
    if op == "C":
        return cubic(pts[0], pts[1], pts[2], pts[3], t)
    raise ValueError(op)


def lerp(a, b, t):
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def quad(p0, p1, p2, t):
    u = 1 - t
    return (
        u * u * p0[0] + 2 * u * t * p1[0] + t * t * p2[0],
        u * u * p0[1] + 2 * u * t * p1[1] + t * t * p2[1],
    )


def cubic(p0, p1, p2, p3, t):
    u = 1 - t
    return (
        u ** 3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t ** 3 * p3[0],
        u ** 3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t ** 3 * p3[1],
    )


def _unit(a, b):
    dx, dy = b[0] - a[0], b[1] - a[1]
    length = math.hypot(dx, dy)
    if length == 0:
        raise ValueError("zero-length offset edge")
    return (dx / length, dy / length)


def _inward_normal(tangent):
    """Left of travel. The canonical path is clockwise on the y-down grid, so left points in."""
    return (-tangent[1], tangent[0])


def _add(p, v, k):
    return (p[0] + v[0] * k, p[1] + v[1] * k)


def _intersect(origin, direction, other_origin, other_direction):
    det = direction[0] * other_direction[1] - direction[1] * other_direction[0]
    if abs(det) < 1e-9:
        return _add(origin, _add(other_origin, origin, -1), 0.5)
    delta = (other_origin[0] - origin[0], other_origin[1] - origin[1])
    s = (delta[0] * other_direction[1] - delta[1] * other_direction[0]) / det
    return _add(origin, direction, s)


def _offset_cubic(pts, distance):
    p0, p1, p2, p3 = pts
    t01, t12, t23 = _unit(p0, p1), _unit(p1, p2), _unit(p2, p3)
    n01, n12, n23 = _inward_normal(t01), _inward_normal(t12), _inward_normal(t23)
    a = _add(p0, n01, distance)
    b = _add(p1, n12, distance)
    c = _add(p2, n23, distance)
    d = _add(p3, n23, distance)
    c1 = _intersect(a, t01, b, t12)
    c2 = _intersect(b, t12, c, t23)
    return [a, c1, c2, d]


def _offset_quad(pts, distance):
    p0, p1, p2 = pts
    t01, t12 = _unit(p0, p1), _unit(p1, p2)
    n01, n12 = _inward_normal(t01), _inward_normal(t12)
    a = _add(p0, n01, distance)
    c = _add(p2, n12, distance)
    b = _intersect(a, t01, c, t12)
    return [a, b, c]


def offset_inward(segments, distance=OUTLINE_INSET):
    """Tiller-Hanson inward offset. Joins are welded because the brand path is smooth."""
    offset = []
    for op, pts in segments:
        if op == "C":
            offset.append((op, _offset_cubic(pts, distance)))
        elif op == "Q":
            offset.append((op, _offset_quad(pts, distance)))
        else:
            raise ValueError(op)
    # The end of each segment and the start of the next describe the same join. Average them
    # so the inner ring stays closed when the two polygon normals differ by a fraction.
    count = len(offset)
    for i in range(count):
        nxt = (i + 1) % count
        end = offset[i][1][-1]
        start = offset[nxt][1][0]
        welded = ((end[0] + start[0]) / 2, (end[1] + start[1]) / 2)
        offset[i][1][-1] = welded
        offset[nxt][1][0] = welded
    return offset


def x_range_at(segments, y, samples=400):
    """Horizontal span of the closed outline at y, from dense samples plus edge crossings."""
    hits = []
    flat = sample(segments, samples)
    flat.append(flat[0])
    for a, b in zip(flat, flat[1:]):
        y0, y1 = a[1], b[1]
        if (y0 - y) * (y1 - y) < 0 or abs(y0 - y) < 1e-6:
            if abs(y1 - y0) < 1e-9:
                hits.append(a[0])
            else:
                t = (y - y0) / (y1 - y0)
                if 0 <= t <= 1:
                    hits.append(a[0] + (b[0] - a[0]) * t)
    if not hits:
        return None
    return (min(hits), max(hits))


def chin_bottom(segments):
    return max(p[1] for p in sample(segments, 200))


def nearest_distance(point, segments, samples=60):
    best = None
    for op, pts in segments:
        for i in range(samples + 1):
            q = point_at(op, pts, i / samples)
            d = math.hypot(point[0] - q[0], point[1] - q[1])
            if best is None or d < best:
                best = d
    return best


def ellipse_commands(cx, cy, rx, ry):
    kx, ky = rx * KAPPA, ry * KAPPA
    return (
        f"M {fmt(cx + rx)} {fmt(cy)} "
        f"C {fmt(cx + rx)} {fmt(cy + ky)} {fmt(cx + kx)} {fmt(cy + ry)} {fmt(cx)} {fmt(cy + ry)} "
        f"C {fmt(cx - kx)} {fmt(cy + ry)} {fmt(cx - rx)} {fmt(cy + ky)} {fmt(cx - rx)} {fmt(cy)} "
        f"C {fmt(cx - rx)} {fmt(cy - ky)} {fmt(cx - kx)} {fmt(cy - ry)} {fmt(cx)} {fmt(cy - ry)} "
        f"C {fmt(cx + kx)} {fmt(cy - ry)} {fmt(cx + rx)} {fmt(cy - ky)} {fmt(cx + rx)} {fmt(cy)} Z"
    )


def body_commands():
    """Neck and shoulders for base_teardrop, band 34.

    The neck is 240 wide from y 840 (behind the chin) to y 1000. Shoulders curve out to
    about x -160..1184 by y 1200, then run straight to y 1536.
    """
    return (
        "M 392 840 L 392 1000 "
        "C 392 1100 -40 1160 -160 1200 "
        "L -160 1536 L 1184 1536 L 1184 1200 "
        "C 1064 1160 632 1100 632 1000 "
        "L 632 840 Z"
    )


def shirt_commands():
    """Crew tee. The collar band is y 948..1024 so it covers three pixels at 48 px head framing."""
    return (
        "M 404 948 L 404 1024 "
        "C 404 1120 20 1180 -80 1220 "
        "L -80 1480 L 1104 1480 L 1104 1220 "
        "C 1004 1180 620 1120 620 1024 "
        "L 620 948 Z"
    )


def shade_commands():
    """One bottom-right shade on the cheek. Light comes from the upper left.

    An ellipse stays about 50 units inside the outline, clear of the 36-unit ring.
    """
    return ellipse_commands(600, 760, 62, 44)


def parse_commands(d):
    """Absolute M/L/C/Q/Z only, which is all this pack uses."""
    tokens = d.replace(",", " ").split()
    segments = []
    i = 0
    cursor = None
    start = None
    while i < len(tokens):
        op = tokens[i]
        i += 1
        if op == "M":
            cursor = (float(tokens[i]), float(tokens[i + 1]))
            start = cursor
            i += 2
        elif op == "L":
            end = (float(tokens[i]), float(tokens[i + 1]))
            segments.append(("L", [cursor, end]))
            cursor = end
            i += 2
        elif op == "Q":
            c = (float(tokens[i]), float(tokens[i + 1]))
            end = (float(tokens[i + 2]), float(tokens[i + 3]))
            segments.append(("Q", [cursor, c, end]))
            cursor = end
            i += 4
        elif op == "C":
            c1 = (float(tokens[i]), float(tokens[i + 1]))
            c2 = (float(tokens[i + 2]), float(tokens[i + 3]))
            end = (float(tokens[i + 4]), float(tokens[i + 5]))
            segments.append(("C", [cursor, c1, c2, end]))
            cursor = end
            i += 6
        elif op == "Z":
            if start is not None and cursor != start:
                segments.append(("L", [cursor, start]))
            cursor = start
        else:
            raise ValueError(op)
    return segments


def outside_points(commands, silhouette, slack=2):
    """Samples that fall outside the silhouette, ignoring the region below the chin."""
    chin = chin_bottom(silhouette)
    bad = []
    for p in sample(parse_commands(commands), 24):
        if p[1] > chin + slack:
            continue
        span = x_range_at(silhouette, p[1])
        if span is None or p[0] < span[0] - slack or p[0] > span[1] + slack:
            bad.append(p)
    return bad


if __name__ == "__main__":
    segs = mapped_segments()
    print(path_commands(segs))
    for y in (200, 300, 336, 368, 420, 430, 472, 500, 580, 600, 680, 700, 740, 800, 840, 860, 870, 900):
        span = x_range_at(segs, y)
        print(f"y {y}: {span[0]:.1f}–{span[1]:.1f}" if span else f"y {y}: none")
    print("chin", round(chin_bottom(segs), 2))
    inner = offset_inward(segs)
    dists = [nearest_distance(p, segs) for p in sample(inner, 30)]
    print(f"offset dist min {min(dists):.2f} max {max(dists):.2f} mean {sum(dists)/len(dists):.2f}")
    print("SHADE outside", len(outside_points(shade_commands(), segs)))
    print("NECK", neck_commands())
