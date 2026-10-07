"""Build picture JSON from the SVG subset in art/<packId>/.

Standard library only. Commands: build <pack>, validate <pack>, check.
"""

from __future__ import annotations

import json
import math
import re
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art"
PACKS = ROOT / "app" / "src" / "main" / "assets" / "packs"
SVG_NS = "http://www.w3.org/2000/svg"
KAPPA = 0.5522847498
ALLOWED = {
    "svg",
    "defs",
    "g",
    "path",
    "rect",
    "circle",
    "ellipse",
    "polygon",
    "linearGradient",
    "radialGradient",
    "stop",
}
DRAWABLE = {"path", "rect", "circle", "ellipse", "polygon"}
NUM = re.compile(r"-?\d+(?:\.\d+)?(?:e[-+]?\d+)?", re.IGNORECASE)
TOKEN = re.compile(r"[A-Za-z]|-?\d+(?:\.\d+)?(?:e[-+]?\d+)?")


class PipelineError(Exception):
    pass


def main(argv: list[str]) -> int:
    if len(argv) < 2 or argv[1] not in {"build", "validate", "check"}:
        print("usage: asset_pipeline.py build <pack> | validate <pack> | check", file=sys.stderr)
        return 2
    try:
        if argv[1] == "check":
            check()
        else:
            if len(argv) != 3:
                print(f"usage: asset_pipeline.py {argv[1]} <pack>", file=sys.stderr)
                return 2
            if argv[1] == "build":
                written = build_pack(argv[2], PACKS)
                print(f"wrote {len(written)} pictures")
            else:
                validate_pack(argv[2])
                print(f"{argv[2]} ok")
    except PipelineError as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


def check() -> None:
    packs = sorted(p.name for p in ART.iterdir() if p.is_dir()) if ART.exists() else []
    if not packs:
        raise PipelineError("art/ has no packs")
    with tempfile.TemporaryDirectory() as tmp:
        out = Path(tmp)
        for pack in packs:
            build_pack(pack, out)
            committed = pictures_dir(version_dir(PACKS / pack))
            built = pictures_dir(version_dir(out / pack))
            committed_files = {p.name: p for p in committed.glob("*.json")}
            built_files = {p.name: p for p in built.glob("*.json")}
            missing = sorted(set(committed_files) - set(built_files))
            extra = sorted(set(built_files) - set(committed_files))
            if missing or extra:
                raise PipelineError(
                    f"{pack} picture set differs: missing {missing or '[]'}, extra {extra or '[]'}"
                )
            for name in sorted(committed_files):
                left = committed_files[name].read_text(encoding="utf-8")
                right = built_files[name].read_text(encoding="utf-8")
                if left != right:
                    raise PipelineError(f"{pack} {name} does not match the SVG source")


def build_pack(
    pack: str,
    packs_root: Path,
    art_root: Path | None = None,
    version_pack: Path | None = None,
) -> list[Path]:
    version = version_dir(version_pack or (PACKS / pack))
    pictures = pictures_dir(packs_root / pack / version.name)
    pictures.mkdir(parents=True, exist_ok=True)
    written = []
    for svg in source_svgs(pack, art_root):
        picture = parse_svg(svg.read_text(encoding="utf-8"), svg.name)
        target = pictures / f"{picture['id']}.json"
        target.write_text(emit_picture(picture), encoding="utf-8", newline="\n")
        written.append(target)
    if not written:
        raise PipelineError(f"{pack} has no SVG sources")
    return written


def validate_pack(pack: str) -> None:
    found = False
    for svg in source_svgs(pack):
        found = True
        parse_svg(svg.read_text(encoding="utf-8"), svg.name)
    if not found:
        raise PipelineError(f"{pack} has no SVG sources")


def source_svgs(pack: str, art_root: Path | None = None) -> list[Path]:
    folder = (art_root or ART) / pack
    if not folder.is_dir():
        raise PipelineError(f"missing art pack {pack}")
    return sorted(p for p in folder.glob("*.svg") if p.is_file())


def version_dir(pack_root: Path) -> Path:
    if not pack_root.is_dir():
        raise PipelineError(f"missing pack directory {pack_root.relative_to(ROOT)}")
    versions = [p for p in pack_root.iterdir() if p.is_dir() and re.fullmatch(r"v\d+", p.name)]
    if len(versions) != 1:
        raise PipelineError(f"{pack_root.name} needs exactly one vN directory")
    return versions[0]


def pictures_dir(version: Path) -> Path:
    return version / "pictures"


def parse_svg(text: str, name: str) -> dict:
    try:
        root = ET.fromstring(text)
    except ET.ParseError as error:
        raise PipelineError(f"{name} is not valid XML: {error}") from error
    if local(root.tag) != "svg":
        raise PipelineError(f"{name} root must be svg")
    view_box = root.attrib.get("viewBox", "")
    if view_box != "0 0 1024 1024":
        raise PipelineError(f"{name} viewBox must be 0 0 1024 1024")
    schema = require_int(root.attrib.get("data-schema-version"), f"{name} data-schema-version")
    if schema not in (1, 2):
        raise PipelineError(f"{name} data-schema-version must be 1 or 2")
    asset_id = root.attrib.get("data-id", "")
    if not asset_id or asset_id != Path(name).stem:
        raise PipelineError(f"{name} data-id must match the file name")
    content = require_int(root.attrib.get("data-content-version"), f"{name} data-content-version")
    if content < 1:
        raise PipelineError(f"{name} data-content-version must be >= 1")
    gradients: dict[str, dict] = {}
    parts = []
    clips = []
    walk(root, identity(), name, gradients, parts, clips, schema)
    for part in parts:
        gradient_id = part.pop("_gradient", None)
        if gradient_id is not None:
            if gradient_id not in gradients:
                raise PipelineError(f"{name} part {part['id']} references missing gradient {gradient_id}")
            part["fill"] = gradients[gradient_id]
        clip = part.get("clip")
        if clip is not None and clip["id"] not in {item["id"] for item in clips}:
            raise PipelineError(f"{name} part {part['id']} clips to missing path {clip['id']}")
    return {
        "schemaVersion": schema,
        "id": asset_id,
        "contentVersion": content,
        "viewBox": 1024,
        "parts": parts,
        "clipPaths": clips,
    }


def walk(node, transform, name, gradients, parts, clips, schema, inherited=None) -> None:
    tag = local(node.tag)
    if tag not in ALLOWED:
        raise PipelineError(f"{name} has unsupported element {tag}")
    reject_foreign(node, name)
    if tag == "g":
        transform = multiply(transform, parse_transform(node.attrib.get("transform", ""), name))
    if tag == "linearGradient":
        gradients[gradient_id(node, name)] = linear_fill(node, name)
        return
    if tag == "radialGradient":
        gradients[gradient_id(node, name)] = radial_fill(node, name)
        return
    if tag == "stop":
        return
    if tag in DRAWABLE:
        if node.attrib.get("data-clip-path"):
            if tag != "path":
                raise PipelineError(f"{name} clip definitions must be path elements")
            clips.append(
                {
                    "id": node.attrib["data-clip-path"],
                    "commands": path_commands(node, transform, name),
                }
            )
        else:
            parts.append(part_from(node, transform, name, schema))
    for child in list(node):
        walk(child, transform, name, gradients, parts, clips, schema)


def part_from(node, transform, name, schema) -> dict:
    tag = local(node.tag)
    part_id = node.attrib.get("data-part", "")
    if not part_id:
        raise PipelineError(f"{name} {tag} is missing data-part")
    z_band = require_int(node.attrib.get("data-z"), f"{name} {part_id} data-z")
    slot = node.attrib.get("data-slot")
    gradient = node.attrib.get("data-gradient")
    if (slot is None) == (gradient is None):
        raise PipelineError(f"{name} part {part_id} needs data-slot or data-gradient")
    part = {
        "id": part_id,
        "zBand": z_band,
        "commands": geometry_commands(node, transform, name),
    }
    if slot is not None:
        part["fill"] = {"slot": slot}
    else:
        part["_gradient"] = gradient
    fill_rule = node.attrib.get("data-fill-rule", node.attrib.get("fill-rule", "nonzero"))
    if fill_rule not in {"nonzero", "evenodd"}:
        raise PipelineError(f"{name} part {part_id} has unknown fill rule {fill_rule}")
    if fill_rule != "nonzero":
        part["fillRule"] = fill_rule
    if "data-opacity" in node.attrib:
        part["opacity"] = number_token(node.attrib["data-opacity"], f"{name} part {part_id} opacity")
    if "data-clip" in node.attrib:
        mode = node.attrib.get("data-clip-mode", "")
        if mode not in {"intersect", "difference"}:
            raise PipelineError(f"{name} part {part_id} has unknown clip mode {mode}")
        part["clip"] = {"id": node.attrib["data-clip"], "mode": mode}
    if node.attrib.get("data-allow-overflow") == "true":
        part["allowOverflow"] = True
    uses_v2 = False
    if "data-stroke-slot" in node.attrib:
        uses_v2 = True
        width = number_token(node.attrib.get("data-stroke-width", ""), f"{name} part {part_id} stroke width")
        cap = node.attrib.get("data-stroke-cap", "round")
        join = node.attrib.get("data-stroke-join", "round")
        if cap not in {"butt", "round", "square"} or join not in {"miter", "round", "bevel"}:
            raise PipelineError(f"{name} part {part_id} has an unknown stroke cap or join")
        value = float(width)
        if value < 16 or value > 96:
            raise PipelineError(f"{name} part {part_id} stroke width {width} is outside 16..96")
        part["stroke"] = {"slot": node.attrib["data-stroke-slot"], "width": width, "cap": cap, "join": join}
    if "data-tags" in node.attrib:
        uses_v2 = True
        tags = [item for item in node.attrib["data-tags"].split() if item]
        if not tags:
            raise PipelineError(f"{name} part {part_id} has an empty data-tags")
        part["tags"] = tags
    if "data-clip-by" in node.attrib:
        uses_v2 = True
        part["clipBy"] = clip_by(node.attrib["data-clip-by"], name, part_id)
    if "data-publish-mask" in node.attrib:
        uses_v2 = True
        mask = node.attrib["data-publish-mask"]
        if not mask.strip():
            raise PipelineError(f"{name} part {part_id} publishes a blank mask")
        part["publishMask"] = mask
    if uses_v2 and schema != 2:
        raise PipelineError(f"{name} part {part_id} uses version 2 fields on schema 1")
    return part


def clip_by(text: str, name: str, part_id: str) -> list[dict]:
    items = []
    for piece in text.split():
        if ":" not in piece:
            raise PipelineError(f"{name} part {part_id} clipBy entry {piece} needs mask:mode")
        mask, mode = piece.split(":", 1)
        if not mask or mode not in {"intersect", "difference"}:
            raise PipelineError(f"{name} part {part_id} has unknown clipBy mode {mode}")
        items.append({"mask": mask, "mode": mode})
    if not items:
        raise PipelineError(f"{name} part {part_id} has an empty data-clip-by")
    return items


def geometry_commands(node, transform, name: str) -> str:
    tag = local(node.tag)
    if tag == "path":
        return path_commands(node, transform, name)
    if tag == "rect":
        return rect_commands(node, transform, name)
    if tag == "circle":
        return ellipse_commands(
            attr_float(node, "cx", name),
            attr_float(node, "cy", name),
            attr_float(node, "r", name),
            attr_float(node, "r", name),
            transform,
        )
    if tag == "ellipse":
        return ellipse_commands(
            attr_float(node, "cx", name),
            attr_float(node, "cy", name),
            attr_float(node, "rx", name),
            attr_float(node, "ry", name),
            transform,
        )
    return polygon_commands(node, transform, name)


def path_commands(node, transform, name: str) -> str:
    data = node.attrib.get("d", "").strip()
    if not data:
        raise PipelineError(f"{name} path is missing d")
    if "A" not in data and "a" not in data and transform == identity():
        return re.sub(r"\s+", " ", data)
    return format_ops(bake(parse_path(data, name), transform))


def rect_commands(node, transform, name: str) -> str:
    x = attr_float(node, "x", name)
    y = attr_float(node, "y", name)
    w = attr_float(node, "width", name)
    h = attr_float(node, "height", name)
    ops = [
        ("M", (x, y)),
        ("L", (x + w, y)),
        ("L", (x + w, y + h)),
        ("L", (x, y + h)),
        ("Z", ()),
    ]
    return format_ops(bake(ops, transform))


def ellipse_commands(cx, cy, rx, ry, transform) -> str:
    k = KAPPA
    ops = [
        ("M", (cx - rx, cy)),
        ("C", (cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry)),
        ("C", (cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy)),
        ("C", (cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry)),
        ("C", (cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy)),
        ("Z", ()),
    ]
    return format_ops(bake(ops, transform))


def polygon_commands(node, transform, name: str) -> str:
    points = node.attrib.get("points", "").strip()
    if not points:
        raise PipelineError(f"{name} polygon is missing points")
    nums = [float(item) for item in NUM.findall(points)]
    if len(nums) < 4 or len(nums) % 2:
        raise PipelineError(f"{name} polygon points must be x y pairs")
    ops = [("M", (nums[0], nums[1]))]
    for index in range(2, len(nums), 2):
        ops.append(("L", (nums[index], nums[index + 1])))
    ops.append(("Z", ()))
    return format_ops(bake(ops, transform))


def parse_path(data: str, name: str) -> list:
    tokens = TOKEN.findall(data)
    if not tokens:
        raise PipelineError(f"{name} path is empty")
    index = 0
    command = None
    cx = cy = 0.0
    sx = sy = 0.0
    ops = []

    def take(count: int) -> list[float]:
        nonlocal index
        values = []
        for _ in range(count):
            if index >= len(tokens) or tokens[index].isalpha():
                raise PipelineError(f"{name} path ended inside {command}")
            values.append(float(tokens[index]))
            index += 1
        return values

    while index < len(tokens):
        if tokens[index].isalpha():
            command = tokens[index]
            index += 1
        elif command is None:
            raise PipelineError(f"{name} path does not start with a command")
        relative = command.islower()
        kind = command.upper()
        if kind == "Z":
            ops.append(("Z", ()))
            cx, cy = sx, sy
            command = None
            continue
        if kind == "M":
            x, y = take(2)
            if relative:
                x += cx
                y += cy
            ops.append(("M", (x, y)))
            cx, cy = sx, sy = x, y
            command = "l" if relative else "L"
            continue
        if kind == "L":
            x, y = take(2)
        elif kind == "H":
            (x,) = take(1)
            y = cy
            if relative:
                x += cx
        elif kind == "V":
            (y,) = take(1)
            x = cx
            if relative:
                y += cy
        elif kind == "C":
            x1, y1, x2, y2, x, y = take(6)
            if relative:
                x1 += cx
                y1 += cy
                x2 += cx
                y2 += cy
                x += cx
                y += cy
            ops.append(("C", (x1, y1, x2, y2, x, y)))
            cx, cy = x, y
            continue
        elif kind == "Q":
            x1, y1, x, y = take(4)
            if relative:
                x1 += cx
                y1 += cy
                x += cx
                y += cy
            ops.append(("Q", (x1, y1, x, y)))
            cx, cy = x, y
            continue
        elif kind == "A":
            rx, ry, angle, large, sweep, x, y = take(7)
            if relative:
                x += cx
                y += cy
            ops.extend(arc_to_cubics(cx, cy, rx, ry, angle, large, sweep, x, y))
            cx, cy = x, y
            continue
        else:
            raise PipelineError(f"{name} path has unsupported command {command}")
        if relative and kind == "L":
            x += cx
            y += cy
        ops.append(("L", (x, y)))
        cx, cy = x, y
    return ops


def arc_to_cubics(x1, y1, rx, ry, angle, large, sweep, x2, y2) -> list:
    if math.isclose(x1, x2) and math.isclose(y1, y2):
        return []
    if rx == 0 or ry == 0:
        return [("L", (x2, y2))]
    rx, ry = abs(rx), abs(ry)
    phi = math.radians(angle)
    cos = math.cos(phi)
    sin = math.sin(phi)
    dx = (x1 - x2) / 2
    dy = (y1 - y2) / 2
    x1p = cos * dx + sin * dy
    y1p = -sin * dx + cos * dy
    radius = (x1p * x1p) / (rx * rx) + (y1p * y1p) / (ry * ry)
    if radius > 1:
        scale = math.sqrt(radius)
        rx *= scale
        ry *= scale
    sign = -1 if bool(large) == bool(sweep) else 1
    sq = max(0.0, (rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p) / (rx * rx * y1p * y1p + ry * ry * x1p * x1p))
    coef = sign * math.sqrt(sq)
    cxp = coef * rx * y1p / ry
    cyp = coef * -ry * x1p / rx
    cx = cos * cxp - sin * cyp + (x1 + x2) / 2
    cy = sin * cxp + cos * cyp + (y1 + y2) / 2
    start = math.atan2((y1p - cyp) / ry, (x1p - cxp) / rx)
    end = math.atan2((-y1p - cyp) / ry, (-x1p - cxp) / rx)
    delta = end - start
    if sweep and delta < 0:
        delta += 2 * math.pi
    elif not sweep and delta > 0:
        delta -= 2 * math.pi
    curves = []
    left = delta
    theta = start
    while abs(left) > 1e-9:
        step = max(-math.pi / 2, min(math.pi / 2, left))
        curves.append(arc_cubic(cx, cy, rx, ry, phi, theta, theta + step))
        theta += step
        left -= step
    return curves


def arc_cubic(cx, cy, rx, ry, phi, start, end):
    delta = end - start
    alpha = math.sin(delta) * (math.sqrt(4 + 3 * math.tan(delta / 2) ** 2) - 1) / 3
    cos = math.cos(phi)
    sin = math.sin(phi)

    def point(theta):
        px = rx * math.cos(theta)
        py = ry * math.sin(theta)
        return cx + cos * px - sin * py, cy + sin * px + cos * py

    def derivative(theta):
        dx = -rx * math.sin(theta)
        dy = ry * math.cos(theta)
        return cos * dx - sin * dy, sin * dx + cos * dy

    p1 = point(start)
    p2 = point(end)
    d1 = derivative(start)
    d2 = derivative(end)
    return (
        "C",
        (
            p1[0] + alpha * d1[0],
            p1[1] + alpha * d1[1],
            p2[0] - alpha * d2[0],
            p2[1] - alpha * d2[1],
            p2[0],
            p2[1],
        ),
    )


def bake(ops, transform) -> list:
    if transform == identity():
        return ops
    baked = []
    for kind, coords in ops:
        if kind == "Z":
            baked.append((kind, coords))
            continue
        mapped = []
        for index in range(0, len(coords), 2):
            mapped.extend(apply_point(transform, coords[index], coords[index + 1]))
        baked.append((kind, tuple(mapped)))
    return baked


def format_ops(ops) -> str:
    chunks = []
    for kind, coords in ops:
        if kind == "Z":
            chunks.append("Z")
        else:
            chunks.append(kind + " " + " ".join(format_num(value) for value in coords))
    return " ".join(chunks)


def format_num(value: float) -> str:
    if math.isclose(value, round(value), abs_tol=1e-6):
        return str(int(round(value)))
    return f"{value:.4f}".rstrip("0").rstrip(".")


def linear_fill(node, name: str) -> dict:
    require_user_space(node, name)
    return {
        "linear": {
            "x1": number_token(node.attrib.get("x1", "0"), name),
            "y1": number_token(node.attrib.get("y1", "0"), name),
            "x2": number_token(node.attrib.get("x2", "0"), name),
            "y2": number_token(node.attrib.get("y2", "0"), name),
            "stops": stops_of(node, name),
        }
    }


def radial_fill(node, name: str) -> dict:
    require_user_space(node, name)
    return {
        "radial": {
            "cx": number_token(node.attrib["cx"], f"{name} radial cx"),
            "cy": number_token(node.attrib["cy"], f"{name} radial cy"),
            "r": number_token(node.attrib["r"], f"{name} radial r"),
            "stops": stops_of(node, name),
        }
    }


def stops_of(node, name: str) -> list[dict]:
    stops = []
    for child in list(node):
        if local(child.tag) != "stop":
            raise PipelineError(f"{name} gradient contains {local(child.tag)}")
        stop = {
            "offset": number_token(child.attrib.get("offset", ""), f"{name} stop offset"),
            "slot": child.attrib.get("data-slot", ""),
        }
        if not stop["slot"]:
            raise PipelineError(f"{name} stop is missing data-slot")
        if "data-alpha" in child.attrib:
            stop["alpha"] = number_token(child.attrib["data-alpha"], f"{name} stop alpha")
        stops.append(stop)
    if len(stops) < 2:
        raise PipelineError(f"{name} gradient needs at least two stops")
    return stops


def gradient_id(node, name: str) -> str:
    ident = node.attrib.get("id", "")
    if not ident:
        raise PipelineError(f"{name} gradient is missing id")
    return ident


def require_user_space(node, name: str) -> None:
    if node.attrib.get("gradientUnits") != "userSpaceOnUse":
        raise PipelineError(f"{name} gradientUnits must be userSpaceOnUse")


def reject_foreign(node, name: str) -> None:
    for key in node.attrib:
        bare = local(key)
        if bare in {"href", "style", "class"} or bare.startswith("on"):
            raise PipelineError(f"{name} uses unsupported attribute {bare}")
    if local(node.tag) in {"text", "image", "filter", "script", "use", "foreignObject"}:
        raise PipelineError(f"{name} has unsupported element {local(node.tag)}")


def emit_picture(picture: dict) -> str:
    lines = ["{"]
    lines.append(f'  "schemaVersion": {picture["schemaVersion"]},')
    lines.append(f'  "id": {json.dumps(picture["id"])},')
    lines.append(f'  "contentVersion": {picture["contentVersion"]},')
    lines.append('  "viewBox": 1024,')
    lines.append('  "parts": [')
    part_lines = [emit_part(part) for part in picture["parts"]]
    lines.append(",\n".join(part_lines))
    if picture["clipPaths"]:
        lines.append("  ],")
        lines.append('  "clipPaths": [')
        clip_lines = [emit_clip(clip) for clip in picture["clipPaths"]]
        lines.append(",\n".join(clip_lines))
        lines.append("  ]")
    else:
        lines.append("  ]")
    lines.append("}")
    return "\n".join(lines) + "\n"


def emit_part(part: dict) -> str:
    rows = [
        f'      "id": {json.dumps(part["id"])},',
        f'      "zBand": {part["zBand"]},',
        '      "fill": ' + emit_fill(part["fill"], 8) + ",",
        f'      "commands": {json.dumps(part["commands"])}',
    ]
    if "fillRule" in part:
        rows[-1] += ","
        rows.append(f'      "fillRule": {json.dumps(part["fillRule"])}')
    if "opacity" in part:
        rows[-1] += ","
        rows.append(f'      "opacity": {part["opacity"]}')
    if "clip" in part:
        rows[-1] += ","
        rows.append(
            "      \"clip\": {\n"
            f'        "id": {json.dumps(part["clip"]["id"])},\n'
            f'        "mode": {json.dumps(part["clip"]["mode"])}\n'
            "      }"
        )
    if part.get("allowOverflow"):
        rows[-1] += ","
        rows.append('      "allowOverflow": true')
    if "stroke" in part:
        rows[-1] += ","
        rows.append('      "stroke": ' + emit_stroke(part["stroke"]))
    if part.get("tags"):
        rows[-1] += ","
        rows.append('      "tags": ' + json.dumps(part["tags"]))
    if part.get("clipBy"):
        rows[-1] += ","
        rows.append('      "clipBy": ' + emit_clip_by(part["clipBy"]))
    if "publishMask" in part:
        rows[-1] += ","
        rows.append(f'      "publishMask": {json.dumps(part["publishMask"])}')
    body = "\n".join(rows)
    return "    {\n" + body + "\n    }"


def emit_fill(fill: dict, indent: int) -> str:
    del indent
    if "slot" in fill:
        return '{\n        "slot": ' + json.dumps(fill["slot"]) + "\n      }"
    kind = "linear" if "linear" in fill else "radial"
    grad = fill[kind]
    rows = ["{", f'        "{kind}": ' + "{"]
    keys = ("x1", "y1", "x2", "y2") if kind == "linear" else ("cx", "cy", "r")
    for key in keys:
        rows.append(f'          "{key}": {grad[key]},')
    rows.append('          "stops": [')
    stop_rows = []
    for stop in grad["stops"]:
        stop_body = [
            "            {",
            f'              "offset": {stop["offset"]},',
            f'              "slot": {json.dumps(stop["slot"])}',
        ]
        if "alpha" in stop:
            stop_body[-1] += ","
            stop_body.append(f'              "alpha": {stop["alpha"]}')
        stop_body.append("            }")
        stop_rows.append("\n".join(stop_body))
    rows.append(",\n".join(stop_rows))
    rows.append("          ]")
    rows.append("        }")
    rows.append("      }")
    return "\n".join(rows)


def emit_stroke(stroke: dict) -> str:
    rows = [
        "{",
        f'        "slot": {json.dumps(stroke["slot"])},',
        f'        "width": {stroke["width"]}',
    ]
    if stroke.get("cap", "round") != "round":
        rows[-1] += ","
        rows.append(f'        "cap": {json.dumps(stroke["cap"])}')
    if stroke.get("join", "round") != "round":
        rows[-1] += ","
        rows.append(f'        "join": {json.dumps(stroke["join"])}')
    rows.append("      }")
    return "\n".join(rows)


def emit_clip_by(items: list[dict]) -> str:
    rows = ["["]
    bodies = []
    for item in items:
        bodies.append(
            "        {\n"
            f'          "mask": {json.dumps(item["mask"])},\n'
            f'          "mode": {json.dumps(item["mode"])}\n'
            "        }"
        )
    rows.append(",\n".join(bodies))
    rows.append("      ]")
    return "\n".join(rows)


def emit_clip(clip: dict) -> str:
    return (
        "    {\n"
        f'      "id": {json.dumps(clip["id"])},\n'
        f'      "commands": {json.dumps(clip["commands"])}\n'
        "    }"
    )


def picture_to_svg(picture: dict) -> str:
    """SVG source that builds back to this picture. Used to seed art/ from shipped JSON."""
    lines = [
        f'<svg xmlns="{SVG_NS}" viewBox="0 0 1024 1024"',
        f'     data-schema-version="{picture["schemaVersion"]}" data-id="{picture["id"]}"',
        f'     data-content-version="{picture["contentVersion"]}">',
    ]
    gradients = []
    for part in picture["parts"]:
        fill = part["fill"]
        if "slot" not in fill:
            kind = "linear" if "linear" in fill else "radial"
            gradients.append((f"grad_{part['id']}", kind, fill[kind]))
    if gradients:
        lines.append("  <defs>")
        for ident, kind, grad in gradients:
            lines.append(gradient_svg(ident, kind, grad))
        lines.append("  </defs>")
    for clip in picture.get("clipPaths", []):
        lines.append(f'  <path data-clip-path="{clip["id"]}" d="{clip["commands"]}"/>')
    for part in picture["parts"]:
        attrs = [
            f'data-part="{part["id"]}"',
            f'data-z="{part["zBand"]}"',
        ]
        fill = part["fill"]
        if "slot" in fill:
            attrs.append(f'data-slot="{fill["slot"]}"')
        else:
            attrs.append(f'data-gradient="grad_{part["id"]}"')
        if part.get("fillRule") and part["fillRule"] != "nonzero":
            attrs.append(f'data-fill-rule="{part["fillRule"]}"')
        if "opacity" in part:
            attrs.append(f'data-opacity="{part["opacity"]}"')
        if "clip" in part:
            attrs.append(f'data-clip="{part["clip"]["id"]}"')
            attrs.append(f'data-clip-mode="{part["clip"]["mode"]}"')
        if part.get("allowOverflow"):
            attrs.append('data-allow-overflow="true"')
        if "stroke" in part:
            stroke = part["stroke"]
            attrs.append(f'data-stroke-slot="{stroke["slot"]}"')
            attrs.append(f'data-stroke-width="{stroke["width"]}"')
            if stroke.get("cap", "round") != "round":
                attrs.append(f'data-stroke-cap="{stroke["cap"]}"')
            if stroke.get("join", "round") != "round":
                attrs.append(f'data-stroke-join="{stroke["join"]}"')
        if part.get("tags"):
            attrs.append(f'data-tags="{" ".join(part["tags"])}"')
        if part.get("clipBy"):
            attrs.append(
                'data-clip-by="' + " ".join(f'{item["mask"]}:{item["mode"]}' for item in part["clipBy"]) + '"'
            )
        if "publishMask" in part:
            attrs.append(f'data-publish-mask="{part["publishMask"]}"')
        attrs.append(f'd="{part["commands"]}"')
        lines.append("  <path " + " ".join(attrs) + "/>")
    lines.append("</svg>")
    return "\n".join(lines) + "\n"


def gradient_svg(ident: str, kind: str, grad: dict) -> str:
    if kind == "linear":
        opening = (
            f'    <linearGradient id="{ident}" gradientUnits="userSpaceOnUse" '
            f'x1="{grad["x1"]}" y1="{grad["y1"]}" x2="{grad["x2"]}" y2="{grad["y2"]}">'
        )
    else:
        opening = (
            f'    <radialGradient id="{ident}" gradientUnits="userSpaceOnUse" '
            f'cx="{grad["cx"]}" cy="{grad["cy"]}" r="{grad["r"]}">'
        )
    rows = [opening]
    for stop in grad["stops"]:
        alpha = f' data-alpha="{stop["alpha"]}"' if "alpha" in stop else ""
        rows.append(f'      <stop offset="{stop["offset"]}" data-slot="{stop["slot"]}"{alpha}/>')
    rows.append(f"    </{kind}Gradient>")
    return "\n".join(rows)


def number_token(text: str, label: str) -> str:
    if not re.fullmatch(r"-?\d+(\.\d+)?", text or ""):
        raise PipelineError(f"{label} must be a plain number")
    return text


def require_int(text, label: str) -> int:
    token = number_token(text or "", label)
    if "." in token:
        raise PipelineError(f"{label} must be an integer")
    return int(token)


def attr_float(node, key: str, name: str) -> float:
    if key not in node.attrib:
        raise PipelineError(f"{name} {local(node.tag)} is missing {key}")
    return float(number_token(node.attrib[key], f"{name} {key}"))


def identity():
    return (1.0, 0.0, 0.0, 1.0, 0.0, 0.0)


def multiply(left, right):
    a1, b1, c1, d1, e1, f1 = left
    a2, b2, c2, d2, e2, f2 = right
    return (
        a1 * a2 + c1 * b2,
        b1 * a2 + d1 * b2,
        a1 * c2 + c1 * d2,
        b1 * c2 + d1 * d2,
        a1 * e2 + c1 * f2 + e1,
        b1 * e2 + d1 * f2 + f1,
    )


def apply_point(matrix, x, y):
    a, b, c, d, e, f = matrix
    return (a * x + c * y + e, b * x + d * y + f)


def parse_transform(text: str, name: str):
    result = identity()
    if not text.strip():
        return result
    for kind, args in re.findall(r"(matrix|translate|scale|rotate)\s*\(([^)]*)\)", text):
        nums = [float(item) for item in NUM.findall(args)]
        if kind == "matrix":
            if len(nums) != 6:
                raise PipelineError(f"{name} matrix needs 6 numbers")
            item = tuple(nums)
        elif kind == "translate":
            if len(nums) not in (1, 2):
                raise PipelineError(f"{name} translate needs 1 or 2 numbers")
            item = (1, 0, 0, 1, nums[0], nums[1] if len(nums) == 2 else 0)
        elif kind == "scale":
            if len(nums) not in (1, 2):
                raise PipelineError(f"{name} scale needs 1 or 2 numbers")
            sy = nums[1] if len(nums) == 2 else nums[0]
            item = (nums[0], 0, 0, sy, 0, 0)
        else:
            if len(nums) not in (1, 3):
                raise PipelineError(f"{name} rotate needs 1 or 3 numbers")
            angle = math.radians(nums[0])
            cos = math.cos(angle)
            sin = math.sin(angle)
            item = (cos, sin, -sin, cos, 0, 0)
            if len(nums) == 3:
                cx, cy = nums[1], nums[2]
                item = multiply(multiply((1, 0, 0, 1, cx, cy), item), (1, 0, 0, 1, -cx, -cy))
        result = multiply(result, item)
    if not re.findall(r"(matrix|translate|scale|rotate)\s*\(", text):
        raise PipelineError(f"{name} has an unreadable transform")
    return result


def local(tag: str) -> str:
    return tag.split("}", 1)[-1]


if __name__ == "__main__":
    sys.exit(main(sys.argv))
