"""Validate and import art drops from the external iDL Art Studio.

Standard library only. Spec: docs/avatar/ART_INTERCHANGE.md §4–§9 (D-51, D-52).
Task: docs/handoff/ART_IMPORT_TASK.md.

Commands:
  check <dir>                 interchange checks; exit 1 when any problem is found
  import <dir> [--pack id]    copy a valid drop into the pack, rebuild, and archive it
  sheets <id>                 review sheet at 512 and 48 px (needs the Studio venv)
  retire <old> --to <new>     map a removed id onto its replacement
"""

from __future__ import annotations

import json
import math
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import asset_pipeline as pipeline
import gen_asset_catalog

ROOT = Path(__file__).resolve().parents[1]
KB = 1024
WARN_BYTES = 12 * KB
MAX_BYTES = 16 * KB
MIN_THICKNESS = 32.0
HEX = re.compile(r"^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$")
SNAKE = re.compile(r"^[a-z][a-z0-9]*(_[a-z0-9]+)*$")

# Category wire name → id prefixes (ART_INTERCHANGE.md §7–§8).
CATEGORY_PREFIXES = {
    "hair": ("hair_",),
    "facial_hair": ("beard_", "mustache_"),
    "head_accessory": ("hat_",),
    "face_accessory": ("glasses_",),
    "top": ("top_",),
    "foreground_prop": ("prop_",),
}

# Slots a category may use. Shade and highlight may be omitted from meta.json.
CATEGORY_SLOTS = {
    "hair": {"hair.primary", "hair.highlight", "hair.shadow", "outline"},
    "facial_hair": {"beard.primary", "beard.shadow", "outline"},
    "head_accessory": {"hat.primary", "hat.secondary", "hat.shadow", "outline"},
    "face_accessory": {"glasses.lens", "glasses.frame"},
    "top": {"top.primary", "top.secondary", "top.accent", "top.shadow", "outline"},
    "foreground_prop": {"accessory.primary", "accessory.secondary", "accessory.shadow", "outline"},
}

BODY_BANDS = {0, 20, 34, 36, 38}
PROVENANCE_KEYS = ("tool", "commit", "model", "controlnet", "seed", "prompt")
REVIEW_REMINDER = (
    "Review reminder: no letters, logos or brands (ART_STYLE_GUIDE.md §3). "
    "This check does not try to detect them."
)


class ImportError(Exception):
    """One interchange problem. `part` is the data-part id when the problem is on a part."""

    def __init__(self, message: str, part: str = "") -> None:
        super().__init__(message)
        self.part = part
        self.message = message

    def __str__(self) -> str:
        if self.part:
            return f"{self.part}: {self.message}"
        return self.message


# Review-sheet expressions. Smile is a shipped expression. Open keeps the neutral eyes and
# swaps in mouth_round_open, because no shipped expression is an open mouth on its own.
SHEET_EXPRESSIONS = (
    ("neutral", "neutral_face", None),
    ("smile", "smiling_face_with_smiling_eyes", None),
    ("open", "neutral_face", "mouth_round_open"),
)


def sheet_neighbors(assets: list[dict], asset_id: str) -> list[str]:
    """Shipped hats and glasses to show beside this item."""
    by_id = {asset["id"]: asset for asset in assets}
    asset = by_id[asset_id]
    accessory = {"head_accessory", "face_accessory"}
    wanted: set[str] = set()
    if asset["category"] == "head_accessory":
        wanted.update(item["id"] for item in assets if item["category"] == "face_accessory")
    elif asset["category"] == "face_accessory":
        wanted.update(item["id"] for item in assets if item["category"] == "head_accessory")
    elif asset["category"] == "hair":
        wanted.update(item["id"] for item in assets if item["category"] == "head_accessory")
    for other_id in asset.get("conflictsWith") or []:
        other = by_id.get(other_id)
        if other and other["category"] in accessory:
            wanted.add(other_id)
    for other in assets:
        if asset_id in (other.get("conflictsWith") or []) and other["category"] in accessory:
            wanted.add(other["id"])
    wanted.discard(asset_id)
    return sorted(wanted)


def main(argv: list[str]) -> int:
    if len(argv) < 2 or argv[1] not in {"check", "import", "sheets", "retire"}:
        print(
            "usage: import_art.py check <dir> | import <dir> [--pack id] | sheets <id> | retire <old> --to <new>",
            file=sys.stderr,
        )
        return 2
    if argv[1] == "check":
        if len(argv) != 3:
            print("usage: import_art.py check <dir>", file=sys.stderr)
            return 2
        return _check_cli(Path(argv[2]))
    if argv[1] == "sheets":
        return _sheets_cli(argv[2:])
    if argv[1] == "retire":
        return _retire_cli(argv[2:])
    return _import_cli(argv[2:])


def _retire_cli(argv: list[str]) -> int:
    if len(argv) != 3 or argv[1] != "--to" or argv[0].startswith("-") or argv[2].startswith("-"):
        print("usage: import_art.py retire <old> --to <new>", file=sys.stderr)
        return 2
    try:
        print(retire_asset(argv[0], argv[2]))
    except DropRejected as error:
        for issue in error.issues:
            print(issue, file=sys.stderr)
        return 1
    except (OSError, pipeline.PipelineError) as error:
        print(str(error), file=sys.stderr)
        return 1
    return 0


def retire_asset(old: str, new: str, paths: RepoPaths | None = None) -> str:
    """Record old → new and drop old from the shipped asset list. Refuses a removal with no replacement."""
    paths = paths or default_paths()
    if not SNAKE.match(old) or not SNAKE.match(new):
        raise DropRejected([ImportError("ids must be lowercase snake case")])
    if old == new:
        raise DropRejected([ImportError("cannot retire an id onto itself")])

    loaded: list[tuple[Path, dict]] = []
    new_path: Path | None = None
    old_path: Path | None = None
    for pack_dir in sorted(path for path in paths.packs.iterdir() if path.is_dir()):
        try:
            manifest_path = pipeline.version_dir(pack_dir) / "manifest.json"
        except pipeline.PipelineError:
            continue
        if not manifest_path.is_file():
            continue
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        loaded.append((manifest_path, manifest))
        for asset in manifest.get("assets", []):
            if asset.get("id") == new:
                new_path = manifest_path
            if asset.get("id") == old:
                old_path = manifest_path
        if old in (manifest.get("retired") or {}) and manifest["retired"][old] != new:
            raise DropRejected(
                [ImportError(f"{old} is already retired to {manifest['retired'][old]}")]
            )
    if new_path is None:
        raise DropRejected([ImportError(f"{new} is not a shipped asset")])
    if old_path is None:
        for _, manifest in loaded:
            if (manifest.get("retired") or {}).get(old) == new:
                return f"retired {old} -> {new}"
        raise DropRejected([ImportError(f"{old} is not a shipped asset")])

    target = old_path or new_path
    manifest = next(item for path, item in loaded if path == target)
    journal = _Journal()
    journal.file(target)
    journal.file(paths.catalog_json)
    journal.file(paths.catalog_sql)
    try:
        manifest["assets"] = [asset for asset in manifest.get("assets", []) if asset.get("id") != old]
        retired = manifest.setdefault("retired", {})
        retired[old] = new
        target.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")
        gen_asset_catalog.write(
            gen_asset_catalog.build(paths.packs),
            json_path=paths.catalog_json,
            sql_path=paths.catalog_sql,
        )
    except Exception:
        journal.restore()
        raise
    return f"retired {old} -> {new}"


def _sheets_cli(argv: list[str]) -> int:
    if len(argv) != 1 or argv[0].startswith("-"):
        print("usage: import_art.py sheets <id>", file=sys.stderr)
        return 2
    studio = ROOT / "tools" / "studio" / "studio.py"
    completed = subprocess.run([sys.executable, str(studio), "sheets", argv[0]], check=False)
    return completed.returncode


def _check_cli(directory: Path) -> int:
    try:
        issues, warnings = check_drop(directory)
    except OSError as error:
        print(str(error), file=sys.stderr)
        return 1
    for warning in warnings:
        print(f"warning: {warning}")
    print(REVIEW_REMINDER)
    if issues:
        for issue in issues:
            print(issue, file=sys.stderr)
        return 1
    print(f"{directory.name} ok")
    return 0


def _import_cli(argv: list[str]) -> int:
    if not argv or argv[0].startswith("-"):
        print("usage: import_art.py import <dir> [--pack id]", file=sys.stderr)
        return 2
    directory = Path(argv[0])
    pack = None
    index = 1
    while index < len(argv):
        if argv[index] == "--pack" and index + 1 < len(argv):
            pack = argv[index + 1]
            index += 2
            continue
        print("usage: import_art.py import <dir> [--pack id]", file=sys.stderr)
        return 2
    try:
        summary = import_drop(directory, pack=pack, paths=default_paths())
    except DropRejected as error:
        for issue in error.issues:
            print(issue, file=sys.stderr)
        return 1
    except (OSError, pipeline.PipelineError) as error:
        print(str(error), file=sys.stderr)
        return 1
    print(REVIEW_REMINDER)
    print(summary)
    return 0


def check_drop(directory: Path) -> tuple[list[ImportError], list[str]]:
    """Return (problems, warnings). Problems are empty when the drop is acceptable."""
    issues: list[ImportError] = []
    warnings: list[str] = []
    if not directory.is_dir():
        return [ImportError(f"{directory} is not a directory")], warnings

    folder_id = directory.name
    meta_path = directory / "meta.json"
    svg_path = directory / f"{folder_id}.svg"
    meta = _read_meta(meta_path, issues)
    if not svg_path.is_file():
        issues.append(ImportError(f"missing {folder_id}.svg"))
        return _sorted(issues), warnings

    svg_bytes = svg_path.read_bytes()
    if len(svg_bytes) > MAX_BYTES:
        issues.append(ImportError(f"SVG is {len(svg_bytes)} bytes; the limit is {MAX_BYTES} (16 KB)"))
    elif len(svg_bytes) > WARN_BYTES:
        warnings.append(f"SVG is {len(svg_bytes)} bytes, over {WARN_BYTES} (12 KB)")

    try:
        svg_text = svg_bytes.decode("utf-8")
    except UnicodeError:
        issues.append(ImportError("SVG is not UTF-8"))
        return _sorted(issues), warnings

    root = _parse_xml(svg_text, svg_path.name, issues)
    if root is None or meta is None:
        return _sorted(issues), warnings

    _check_identity(root, svg_path.name, folder_id, meta, issues)
    _check_forbidden_markup(root, svg_path.name, issues)
    if any(item.part == "" and "not valid XML" in item.message for item in issues):
        return _sorted(issues), warnings

    picture = _parse_picture(svg_text, svg_path.name, issues)
    if picture is None:
        return _sorted(issues), warnings

    category = str(meta.get("category", ""))
    _check_parts(picture, category, meta, issues)
    return _sorted(issues), warnings


def _read_meta(path: Path, issues: list[ImportError]) -> dict | None:
    if not path.is_file():
        issues.append(ImportError("missing meta.json"))
        return None
    try:
        meta = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as error:
        issues.append(ImportError(f"meta.json is not valid JSON: {error}"))
        return None
    if not isinstance(meta, dict):
        issues.append(ImportError("meta.json must be an object"))
        return None
    for key in (
        "id",
        "pack",
        "category",
        "accessibilityLabel",
        "tags",
        "tier",
        "colorSlots",
        "provenance",
    ):
        if key not in meta:
            issues.append(ImportError(f"meta.json is missing {key}"))
    if "category" in meta and meta["category"] not in CATEGORY_PREFIXES:
        issues.append(
            ImportError(
                f"meta.category {meta['category']!r} is not an import category "
                "(hair, facial_hair, head_accessory, face_accessory, top, foreground_prop)"
            )
        )
    if "tier" in meta and meta["tier"] not in {"free", "premium"}:
        issues.append(ImportError(f"meta.tier {meta['tier']!r} must be free or premium"))
    if "accessibilityLabel" in meta and (
        not isinstance(meta["accessibilityLabel"], str) or not meta["accessibilityLabel"].strip()
    ):
        issues.append(ImportError("meta.accessibilityLabel must be a non-empty string"))
    if "tags" in meta and (
        not isinstance(meta["tags"], list) or not all(isinstance(tag, str) and tag for tag in meta["tags"])
    ):
        issues.append(ImportError("meta.tags must be a list of strings"))
    slots = meta.get("colorSlots")
    if "colorSlots" in meta:
        if not isinstance(slots, dict) or not slots:
            issues.append(ImportError("meta.colorSlots must be a non-empty object"))
        else:
            for name, hex_color in slots.items():
                if not isinstance(hex_color, str) or not HEX.match(hex_color):
                    issues.append(ImportError(f"meta.colorSlots[{name}] must be a #RRGGBB hex color"))
    provenance = meta.get("provenance")
    if "provenance" in meta:
        if not isinstance(provenance, dict):
            issues.append(ImportError("meta.provenance must be an object"))
        else:
            for key in PROVENANCE_KEYS:
                if key not in provenance:
                    issues.append(ImportError(f"meta.provenance is missing {key}"))
    if "patternRegions" in meta and (
        not isinstance(meta["patternRegions"], list)
        or not all(isinstance(item, str) for item in meta["patternRegions"])
    ):
        issues.append(ImportError("meta.patternRegions must be a list of part ids"))
    return meta


def _parse_xml(text: str, name: str, issues: list[ImportError]):
    try:
        return ET.fromstring(text)
    except ET.ParseError as error:
        issues.append(ImportError(f"{name} is not valid XML: {error}"))
        return None


def _check_identity(root, name: str, folder_id: str, meta: dict, issues: list[ImportError]) -> None:
    if pipeline.local(root.tag) != "svg":
        issues.append(ImportError(f"{name} root must be svg"))
        return
    schema = root.attrib.get("data-schema-version", "")
    if schema != "2":
        issues.append(ImportError(f"{name} data-schema-version must be 2"))
    asset_id = root.attrib.get("data-id", "")
    meta_id = meta.get("id", "")
    if asset_id != folder_id or meta_id != folder_id:
        issues.append(
            ImportError(
                f"data-id {asset_id!r}, meta.id {meta_id!r} and the folder name {folder_id!r} must match"
            )
        )
    if not isinstance(meta_id, str) or not SNAKE.match(str(meta_id)):
        issues.append(ImportError(f"id {meta_id!r} must be lowercase snake case"))
    category = meta.get("category", "")
    prefixes = CATEGORY_PREFIXES.get(category, ())
    if isinstance(meta_id, str) and prefixes and not meta_id.startswith(prefixes):
        issues.append(
            ImportError(
                f"id {meta_id!r} must start with {' or '.join(prefixes)} for category {category}"
            )
        )


def _check_forbidden_markup(root, name: str, issues: list[ImportError]) -> None:
    for node in root.iter():
        tag = pipeline.local(node.tag)
        part = node.attrib.get("data-part", "")
        if tag in {"image", "filter", "text", "script", "use", "foreignObject"}:
            issues.append(ImportError(f"unsupported element <{tag}>", part))
        for key in node.attrib:
            bare = pipeline.local(key)
            if bare in {"fill", "style", "class", "filter"} or bare.startswith("on"):
                issues.append(ImportError(f"must not set {bare}", part or tag))
            if bare == "data-allow-overflow":
                issues.append(ImportError("must not set data-allow-overflow", part or tag))


def _parse_picture(text: str, name: str, issues: list[ImportError]) -> dict | None:
    try:
        return pipeline.parse_svg(text, name)
    except pipeline.PipelineError as error:
        issues.append(ImportError(str(error)))
        return None


def _check_parts(picture: dict, category: str, meta: dict, issues: list[ImportError]) -> None:
    parts = picture["parts"]
    ids = [part["id"] for part in parts]
    for part_id, count in sorted((part_id, ids.count(part_id)) for part_id in set(ids)):
        if count > 1:
            issues.append(ImportError(f"data-part is duplicated {count} times", part_id))

    allowed = CATEGORY_SLOTS.get(category, set())
    used_slots: set[str] = set()
    by_band: dict[int, list[dict]] = defaultdict(list)
    for part in parts:
        part_id = part["id"]
        slot = part.get("fill", {}).get("slot")
        if not slot:
            issues.append(ImportError("needs a flat data-slot fill", part_id))
            continue
        if category in CATEGORY_SLOTS and slot not in allowed:
            issues.append(ImportError(f"slot {slot} is not used by {category}", part_id))
        if not _derived_slot(slot):
            used_slots.add(slot)
        stroke = part.get("stroke")
        if stroke and not _derived_slot(stroke["slot"]):
            used_slots.add(stroke["slot"])
        _check_band(part, category, issues)
        _check_bounds(part, issues)
        _check_lens(part, meta, issues)
        by_band[part["zBand"]].append(part)

    declared = meta.get("colorSlots") if isinstance(meta.get("colorSlots"), dict) else {}
    for slot in sorted(used_slots - set(declared)):
        issues.append(ImportError(f"slot {slot} is used in the SVG but missing from meta.colorSlots"))

    for band, group in sorted(by_band.items()):
        line_parts = [part for part in group if _is_line_art(part, category)]
        if len(line_parts) != 1:
            found = ", ".join(part["id"] for part in line_parts) or "none"
            issues.append(
                ImportError(f"band {band} needs exactly one line-art part, found {len(line_parts)} ({found})")
            )
        shades = [part for part in group if str(part.get("fill", {}).get("slot", "")).endswith(".shadow")]
        if len(shades) > 1:
            issues.append(
                ImportError(
                    "has more than one shade on this band (" + ", ".join(part["id"] for part in shades) + ")",
                    shades[0]["id"],
                )
            )
        for part in group:
            if _is_line_art(part, category):
                continue
            thickness = region_thickness(part["commands"], part.get("fillRule", "nonzero"))
            if thickness < MIN_THICKNESS:
                issues.append(
                    ImportError(
                        f"region is {thickness:.1f} units thick; every region must be at least {MIN_THICKNESS:.0f}",
                        part["id"],
                    )
                )

    if category == "head_accessory":
        if not any(part.get("publishMask") == "occlude.hair_top" for part in parts):
            issues.append(ImportError("headwear must publish mask occlude.hair_top"))


def _derived_slot(slot: str) -> bool:
    return slot.endswith(".shadow") or slot.endswith(".highlight")


def _is_line_art(part: dict, category: str) -> bool:
    slot = part.get("fill", {}).get("slot")
    expected = "glasses.frame" if category == "face_accessory" else "outline"
    return part.get("fillRule") == "evenodd" and slot == expected


def _check_band(part: dict, category: str, issues: list[ImportError]) -> None:
    band = part["zBand"]
    tags = part.get("tags") or []
    part_id = part["id"]
    if category == "hair":
        if band == 20:
            if "hair_back" not in tags:
                issues.append(ImportError("band 20 hair must be tagged hair_back", part_id))
        elif band == 70:
            if "hair_top" not in tags:
                issues.append(ImportError("band 70 hair must be tagged hair_top", part_id))
            clips = part.get("clipBy") or []
            if not any(item["mask"] == "occlude.hair_top" and item["mode"] == "difference" for item in clips):
                issues.append(
                    ImportError('band 70 hair must set data-clip-by="occlude.hair_top:difference"', part_id)
                )
        else:
            issues.append(ImportError(f"hair must use band 20 or 70, not {band}", part_id))
    elif category == "facial_hair" and band != 60:
        issues.append(ImportError(f"facial hair must use band 60, not {band}", part_id))
    elif category == "head_accessory" and band != 90:
        issues.append(ImportError(f"headwear must use band 90, not {band}", part_id))
    elif category == "face_accessory" and band != 80:
        issues.append(ImportError(f"eyewear must use band 80, not {band}", part_id))
    elif category == "top" and band not in {36, 38}:
        issues.append(ImportError(f"tops must use band 36 or 38, not {band}", part_id))
    elif category == "foreground_prop" and band not in {100, 110}:
        issues.append(ImportError(f"props must use band 100 or 110, not {band}", part_id))


def _check_bounds(part: dict, issues: list[ImportError]) -> None:
    try:
        ops = pipeline.parse_path(part["commands"], part["id"])
    except pipeline.PipelineError as error:
        issues.append(ImportError(str(error), part["id"]))
        return
    band = part["zBand"]
    outside = False
    for _kind, coords in ops:
        for index in range(0, len(coords), 2):
            if not _inside(band, coords[index], coords[index + 1]):
                outside = True
                break
        if outside:
            break
    if outside:
        box = "the body region" if band in BODY_BANDS else "-16..1040"
        issues.append(ImportError(f"geometry is outside {box}", part["id"]))


def _inside(band: int, x: float, y: float) -> bool:
    body = band in BODY_BANDS
    min_x = -256.0 if body else -16.0
    max_x = 1280.0 if body else 1040.0
    max_y = 1536.0 if body else 1040.0
    return min_x <= x <= max_x and -16.0 <= y <= max_y


def _check_lens(part: dict, meta: dict, issues: list[ImportError]) -> None:
    if part.get("fill", {}).get("slot") != "glasses.lens":
        return
    opacity = part.get("opacity")
    if opacity is None:
        issues.append(ImportError("glasses.lens needs data-opacity of at most 0.75", part["id"]))
        return
    try:
        opacity_value = float(opacity)
    except (TypeError, ValueError):
        issues.append(ImportError(f"glasses.lens opacity {opacity!r} is not a number", part["id"]))
        return
    tags = meta.get("tags") if isinstance(meta.get("tags"), list) else []
    limit = 0.75 if "sunglasses" in tags else 0.35
    if opacity_value > limit + 1e-6:
        issues.append(
            ImportError(f"glasses.lens opacity {opacity_value} is above {limit}", part["id"])
        )


def region_thickness(commands: str, fill_rule: str) -> float:
    """Diameter of the largest inscribed circle, in character-grid units.

    Same measure as the studio's distance transform: 2 × the maximum distance
    from an inside pixel to the region's edge (ART_IMPORT_TASK.md PR 1).
    """
    try:
        ops = pipeline.parse_path(commands, "region")
    except pipeline.PipelineError:
        return 0.0
    contours = _contours(ops)
    if not contours:
        return 0.0
    points = [point for contour in contours for point in contour]
    min_x = min(point[0] for point in points)
    max_x = max(point[0] for point in points)
    min_y = min(point[1] for point in points)
    max_y = max(point[1] for point in points)
    if min(max_x - min_x, max_y - min_y) < MIN_THICKNESS:
        return min(max_x - min_x, max_y - min_y)
    mask, _scale = _rasterize(contours, fill_rule, min_x, min_y, max_x, max_y)
    if not mask:
        return 0.0
    return 2.0 * _max_inside_distance(mask)


def _contours(ops) -> list[list[tuple[float, float]]]:
    contours: list[list[tuple[float, float]]] = []
    current: list[tuple[float, float]] = []
    cursor = (0.0, 0.0)
    start = (0.0, 0.0)
    for kind, coords in ops:
        if kind == "M":
            if len(current) > 2:
                contours.append(current)
            cursor = (coords[0], coords[1])
            start = cursor
            current = [cursor]
        elif kind == "L":
            cursor = (coords[0], coords[1])
            current.append(cursor)
        elif kind == "C":
            end = (coords[4], coords[5])
            current.extend(
                _flatten_cubic(cursor, (coords[0], coords[1]), (coords[2], coords[3]), end)[1:]
            )
            cursor = end
        elif kind == "Q":
            end = (coords[2], coords[3])
            current.extend(_flatten_quad(cursor, (coords[0], coords[1]), end)[1:])
            cursor = end
        elif kind == "Z":
            if current and current[-1] != start:
                current.append(start)
            if len(current) > 2:
                contours.append(current)
            current = []
            cursor = start
    if len(current) > 2:
        contours.append(current)
    return contours


def _flatten_cubic(p0, p1, p2, p3, tol: float = 1.0, depth: int = 0) -> list[tuple[float, float]]:
    if depth > 12 or (
        _line_distance(p1, p0, p3) <= tol and _line_distance(p2, p0, p3) <= tol
    ):
        return [p0, p3]
    left, right = _split_cubic(p0, p1, p2, p3)
    return _flatten_cubic(*left, tol=tol, depth=depth + 1)[:-1] + _flatten_cubic(*right, tol=tol, depth=depth + 1)


def _flatten_quad(p0, p1, p2, tol: float = 1.0, depth: int = 0) -> list[tuple[float, float]]:
    if depth > 12 or _line_distance(p1, p0, p2) <= tol:
        return [p0, p2]
    a = ((p0[0] + p1[0]) / 2, (p0[1] + p1[1]) / 2)
    b = ((p1[0] + p2[0]) / 2, (p1[1] + p2[1]) / 2)
    mid = ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)
    return _flatten_quad(p0, a, mid, tol, depth + 1)[:-1] + _flatten_quad(mid, b, p2, tol, depth + 1)


def _split_cubic(p0, p1, p2, p3):
    def mid(a, b):
        return ((a[0] + b[0]) / 2, (a[1] + b[1]) / 2)

    p01 = mid(p0, p1)
    p12 = mid(p1, p2)
    p23 = mid(p2, p3)
    p012 = mid(p01, p12)
    p123 = mid(p12, p23)
    p0123 = mid(p012, p123)
    return (p0, p01, p012, p0123), (p0123, p123, p23, p3)


def _line_distance(point, start, end) -> float:
    dx = end[0] - start[0]
    dy = end[1] - start[1]
    length = dx * dx + dy * dy
    if length == 0:
        return math.hypot(point[0] - start[0], point[1] - start[1])
    t = max(0.0, min(1.0, ((point[0] - start[0]) * dx + (point[1] - start[1]) * dy) / length))
    return math.hypot(point[0] - (start[0] + t * dx), point[1] - (start[1] + t * dy))


def _rasterize(contours, fill_rule: str, min_x, min_y, max_x, max_y) -> tuple[list[bytearray], float]:
    """Even-odd or nonzero fill. One bytearray per row, 1 = inside. Includes a 1px empty margin."""
    origin_x = math.floor(min_x) - 1
    origin_y = math.floor(min_y) - 1
    width = math.ceil(max_x) - origin_x + 2
    height = math.ceil(max_y) - origin_y + 2
    width = max(width, 1)
    height = max(height, 1)
    edges = []
    for contour in contours:
        for (x0, y0), (x1, y1) in zip(contour, contour[1:]):
            if y0 == y1:
                continue
            if y0 < y1:
                edges.append((y0, y1, x0, (x1 - x0) / (y1 - y0), 1))
            else:
                edges.append((y1, y0, x1, (x0 - x1) / (y0 - y1), -1))
    rows = [bytearray(width) for _ in range(height)]
    for iy in range(height):
        y = origin_y + iy + 0.5
        hits = []
        for y0, y1, x0, slope, winding in edges:
            if y0 <= y < y1:
                hits.append((x0 + (y - y0) * slope, winding))
        if not hits:
            continue
        hits.sort(key=lambda item: item[0])
        row = rows[iy]
        if fill_rule == "evenodd":
            for index in range(0, len(hits) - 1, 2):
                _fill_span(row, hits[index][0], hits[index + 1][0], origin_x)
        else:
            winding = 0
            prev_x = None
            for x_hit, direction in hits:
                if winding != 0 and prev_x is not None:
                    _fill_span(row, prev_x, x_hit, origin_x)
                winding += direction
                prev_x = x_hit
    return rows, 1.0


def _fill_span(row: bytearray, x0: float, x1: float, origin_x: int) -> None:
    start = max(0, math.floor(x0 - origin_x))
    end = min(len(row), math.ceil(x1 - origin_x))
    for index in range(start, end):
        row[index] = 1


def _max_inside_distance(rows: list[bytearray]) -> float:
    """Euclidean distance transform. Returns the max distance of an inside pixel to the outside."""
    height = len(rows)
    width = len(rows[0]) if height else 0
    if width == 0:
        return 0.0
    inf = 1e15
    # Column distance to the nearest outside pixel, then across rows.
    columns = []
    for x in range(width):
        seed = [0.0 if rows[y][x] == 0 else inf for y in range(height)]
        columns.append(_edt_1d(seed))
    best = 0.0
    for y in range(height):
        if not any(rows[y]):
            continue
        seed = [columns[x][y] for x in range(width)]
        distances = _edt_1d(seed)
        for x, distance in enumerate(distances):
            if rows[y][x] and distance < inf and distance > best:
                best = distance
    return math.sqrt(best)


def _edt_1d(seeds: list[float]) -> list[float]:
    """Squared Euclidean distance transform of a 1D seed function (Felzenszwalb)."""
    count = len(seeds)
    if count == 0:
        return []
    vertices = [0] * count
    bounds = [0.0] * (count + 1)
    stack = 0
    vertices[0] = 0
    bounds[0] = float("-inf")
    bounds[1] = float("inf")
    for q in range(1, count):
        s = _edt_sep(seeds, q, vertices[stack])
        while s <= bounds[stack]:
            stack -= 1
            s = _edt_sep(seeds, q, vertices[stack])
        stack += 1
        vertices[stack] = q
        bounds[stack] = s
        bounds[stack + 1] = float("inf")
    stack = 0
    out = [0.0] * count
    for q in range(count):
        while bounds[stack + 1] < q:
            stack += 1
        delta = q - vertices[stack]
        out[q] = delta * delta + seeds[vertices[stack]]
    return out


def _edt_sep(seeds: list[float], q: int, vertex: int) -> float:
    return ((seeds[q] + q * q) - (seeds[vertex] + vertex * vertex)) / (2 * q - 2 * vertex)


def _sorted(issues: list[ImportError]) -> list[ImportError]:
    return sorted(issues, key=lambda issue: (issue.part, issue.message))


class DropRejected(Exception):
    def __init__(self, issues: list[ImportError]) -> None:
        super().__init__("\n".join(str(issue) for issue in issues))
        self.issues = issues


@dataclass(frozen=True)
class RepoPaths:
    art: Path
    packs: Path
    catalog_json: Path
    catalog_sql: Path
    incoming: Path


def default_paths() -> RepoPaths:
    return RepoPaths(
        art=ROOT / "art",
        packs=ROOT / "app" / "src" / "main" / "assets" / "packs",
        catalog_json=ROOT / "contract" / "asset_catalog.json",
        catalog_sql=ROOT / "supabase" / "seed" / "asset_catalog_seed.sql",
        incoming=ROOT / "art" / "incoming",
    )


def import_drop(directory: Path, pack: str | None = None, paths: RepoPaths | None = None) -> str:
    """Copy a valid drop into the pack. Refuses without writing when check finds a problem."""
    paths = paths or default_paths()
    issues, warnings = check_drop(directory)
    for warning in warnings:
        print(f"warning: {warning}")
    if issues:
        raise DropRejected(issues)

    asset_id = directory.name
    meta = json.loads((directory / "meta.json").read_text(encoding="utf-8"))
    pack_id = pack or str(meta["pack"])
    if not SNAKE.match(pack_id):
        raise DropRejected([ImportError(f"pack {pack_id!r} must be lowercase snake case")])
    svg_text = (directory / f"{asset_id}.svg").read_text(encoding="utf-8")
    picture = pipeline.parse_svg(svg_text, f"{asset_id}.svg")
    version = int(picture["contentVersion"])
    shipped = _shipped_version(paths, pack_id, asset_id)
    if shipped is not None and version <= shipped:
        raise DropRejected(
            [ImportError(f"content version {version} is not newer than shipped version {shipped}")]
        )

    slots = _fill_color_slots(meta["colorSlots"], _picture_slots(picture))
    entry = _manifest_entry(meta, pack_id, asset_id, version, slots)
    journal = _Journal()
    art_dir = paths.art / pack_id
    journal.file(art_dir / f"{asset_id}.svg")
    journal.file(art_dir / f"{asset_id}.provenance.json")
    manifest_path = _manifest_path(paths, pack_id)
    journal.file(manifest_path)
    journal.file(paths.catalog_json)
    journal.file(paths.catalog_sql)
    journal.json_dir(pipeline.pictures_dir(manifest_path.parent))
    try:
        _write_asset(paths, pack_id, asset_id, svg_text, entry, meta, version)
        pipeline.build_pack(pack_id, paths.packs, art_root=paths.art, version_pack=paths.packs / pack_id)
        gen_asset_catalog.write(
            gen_asset_catalog.build(paths.packs),
            json_path=paths.catalog_json,
            sql_path=paths.catalog_sql,
        )
        archive = paths.incoming / ".imported" / f"{asset_id}-v{version}"
        archive.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(directory), str(archive))
    except Exception:
        journal.restore()
        raise
    return _summary(asset_id, version, slots, picture, meta)


class _Journal:
    """Copies of files an import will replace, restored if a later step fails."""

    def __init__(self) -> None:
        self._files: list[tuple[Path, bytes | None]] = []
        self._json_dirs: list[tuple[Path, dict[str, bytes]]] = []

    def file(self, path: Path) -> None:
        self._files.append((path, path.read_bytes() if path.is_file() else None))

    def json_dir(self, path: Path) -> None:
        saved: dict[str, bytes] = {}
        if path.is_dir():
            for child in path.glob("*.json"):
                saved[child.name] = child.read_bytes()
        self._json_dirs.append((path, saved))

    def restore(self) -> None:
        for path, data in self._files:
            if data is None:
                path.unlink(missing_ok=True)
            else:
                path.write_bytes(data)
        for path, saved in self._json_dirs:
            if not path.is_dir():
                continue
            for child in path.glob("*.json"):
                if child.name not in saved:
                    child.unlink()
            for name, data in saved.items():
                (path / name).write_bytes(data)


def _shipped_version(paths: RepoPaths, pack_id: str, asset_id: str) -> int | None:
    versions: list[int] = []
    svg = paths.art / pack_id / f"{asset_id}.svg"
    if svg.is_file():
        root = ET.fromstring(svg.read_text(encoding="utf-8"))
        versions.append(int(root.attrib["data-content-version"]))
    manifest_path = _manifest_path(paths, pack_id)
    if manifest_path.is_file():
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        for asset in manifest.get("assets", []):
            if asset.get("id") == asset_id:
                versions.append(int(asset.get("contentVersion", 1)))
    return max(versions) if versions else None


def _manifest_path(paths: RepoPaths, pack_id: str) -> Path:
    return pipeline.version_dir(paths.packs / pack_id) / "manifest.json"


def _picture_slots(picture: dict) -> set[str]:
    slots: set[str] = set()
    for part in picture["parts"]:
        fill = part.get("fill") or {}
        if "slot" in fill:
            slots.add(fill["slot"])
        stroke = part.get("stroke") or {}
        if "slot" in stroke:
            slots.add(stroke["slot"])
    return slots


def _fill_color_slots(declared: dict, used: set[str]) -> dict:
    slots = dict(declared)
    missing = [slot for slot in sorted(used) if _derived_slot(slot) and slot not in slots]
    ordered = [slot for slot in missing if slot.endswith(".shadow")]
    ordered += [slot for slot in missing if slot.endswith(".highlight")]
    for slot in ordered:
        family = slot.rsplit(".", 1)[0]
        primary_name = family + ".primary"
        primary = slots.get(primary_name)
        if not isinstance(primary, str):
            raise DropRejected([ImportError(f"cannot derive {slot}; {primary_name} is missing")])
        kind = "shadow" if slot.endswith(".shadow") else "highlight"
        slots[slot] = derive_hex(primary, kind)
    return slots


def _manifest_entry(meta: dict, pack_id: str, asset_id: str, version: int, slots: dict) -> dict:
    entry = {
        "id": asset_id,
        "category": meta["category"],
        "accessibilityLabel": meta["accessibilityLabel"],
        "render": {"type": "vector", "file": f"pictures/{asset_id}.json"},
        "collection": pack_id,
        "license": "proprietary-idl",
        "contentVersion": version,
        "colorSlots": slots,
        "compatibleBases": ["base_teardrop"],
    }
    if meta.get("tags"):
        entry["tags"] = list(meta["tags"])
    if meta.get("tier"):
        entry["tier"] = meta["tier"]
    return entry


def _write_asset(
    paths: RepoPaths,
    pack_id: str,
    asset_id: str,
    svg_text: str,
    entry: dict,
    meta: dict,
    version: int,
) -> None:
    art_dir = paths.art / pack_id
    art_dir.mkdir(parents=True, exist_ok=True)
    (art_dir / f"{asset_id}.svg").write_text(svg_text, encoding="utf-8", newline="\n")
    sidecar = {
        "id": asset_id,
        "contentVersion": version,
        "provenance": meta["provenance"],
    }
    (art_dir / f"{asset_id}.provenance.json").write_text(
        json.dumps(sidecar, indent=2) + "\n",
        encoding="utf-8",
        newline="\n",
    )
    manifest_path = _manifest_path(paths, pack_id)
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    _upsert_asset(manifest, entry)
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8", newline="\n")


def _upsert_asset(manifest: dict, entry: dict) -> None:
    assets = manifest.setdefault("assets", [])
    for index, asset in enumerate(assets):
        if asset.get("id") != entry["id"]:
            continue
        updated = {}
        for key in asset:
            updated[key] = entry[key] if key in entry else asset[key]
        for key, value in entry.items():
            if key not in updated:
                updated[key] = value
        assets[index] = updated
        return
    assets.append(entry)


def _summary(asset_id: str, version: int, slots: dict, picture: dict, meta: dict) -> str:
    slot_text = ", ".join(f"{name}={value}" for name, value in slots.items())
    bands: dict[int, list[str]] = defaultdict(list)
    for part in picture["parts"]:
        bands[int(part["zBand"])].append(str(part["id"]))
    band_lines = [
        f"band {band}: {', '.join(sorted(names))}" for band, names in sorted(bands.items())
    ]
    notes = meta.get("reviewNotes") or []
    note_text = "; ".join(str(note) for note in notes) if isinstance(notes, list) else str(notes)
    lines = [f"{asset_id} version {version}", f"slots: {slot_text}", *band_lines]
    if note_text:
        lines.append(f"review: {note_text}")
    return "\n".join(lines)


def derive_hex(primary: str, kind: str) -> str:
    """Shadow and highlight hex, matching domain Oklch.kt including sRGB truncation."""
    argb = _hex_to_argb(primary)
    derived = _derive_shadow(argb) if kind == "shadow" else _derive_highlight(argb)
    return f"#{(derived >> 16) & 0xFF:02X}{(derived >> 8) & 0xFF:02X}{derived & 0xFF:02X}"


def _hex_to_argb(hex_color: str) -> int:
    return int(hex_color[1:7], 16)


def _cbrt(value: float) -> float:
    return math.copysign(abs(value) ** (1.0 / 3.0), value)


def _srgb_to_linear(channel: float) -> float:
    if channel <= 0.04045:
        return channel / 12.92
    return ((channel + 0.055) / 1.055) ** 2.4


def _linear_to_srgb(channel: float) -> int:
    clipped = min(1.0, max(0.0, channel))
    if clipped <= 0.0031308:
        encoded = 12.92 * clipped
    else:
        encoded = 1.055 * (clipped ** (1.0 / 2.4)) - 0.055
    return min(255, max(0, int(encoded * 255.0)))


def _from_argb(argb: int) -> tuple[float, float, float]:
    red = _srgb_to_linear(((argb >> 16) & 0xFF) / 255.0)
    green = _srgb_to_linear(((argb >> 8) & 0xFF) / 255.0)
    blue = _srgb_to_linear((argb & 0xFF) / 255.0)
    l_ = _cbrt(0.4122214708 * red + 0.5363325363 * green + 0.0514459929 * blue)
    m_ = _cbrt(0.2119034982 * red + 0.6806995451 * green + 0.1073969566 * blue)
    s_ = _cbrt(0.0883024619 * red + 0.2817188376 * green + 0.6299787005 * blue)
    lightness = 0.2104542553 * l_ + 0.7936177850 * m_ - 0.0040720468 * s_
    a = 1.9779984951 * l_ - 2.4285922050 * m_ + 0.4505937099 * s_
    b_lab = 0.0259040371 * l_ + 0.7827717662 * m_ - 0.8086757660 * s_
    chroma = math.sqrt(a * a + b_lab * b_lab)
    hue = math.degrees(math.atan2(b_lab, a))
    if hue < 0:
        hue += 360.0
    return lightness, chroma, hue


def _to_argb(lightness: float, chroma: float, hue: float) -> int:
    h_rad = math.radians(hue)
    a = chroma * math.cos(h_rad)
    b_lab = chroma * math.sin(h_rad)
    l_ = lightness + 0.3963377774 * a + 0.2158037573 * b_lab
    m_ = lightness - 0.1055613458 * a - 0.0638541728 * b_lab
    s_ = lightness - 0.0894841775 * a - 1.2914855480 * b_lab
    long_l = l_ * l_ * l_
    long_m = m_ * m_ * m_
    long_s = s_ * s_ * s_
    red = _linear_to_srgb(4.0767416621 * long_l - 3.3077115913 * long_m + 0.2309699292 * long_s)
    green = _linear_to_srgb(-1.2684380046 * long_l + 2.6097574011 * long_m - 0.3413193965 * long_s)
    blue = _linear_to_srgb(-0.0041960863 * long_l - 0.7034186147 * long_m + 1.7076147010 * long_s)
    return (red << 16) | (green << 8) | blue


def _derive_shadow(argb: int) -> int:
    lightness, chroma, hue = _from_argb(argb)
    return _to_argb(min(1.0, max(0.0, lightness - 0.12)), chroma * 1.05, hue)


def _derive_highlight(argb: int) -> int:
    lightness, chroma, hue = _from_argb(argb)
    return _to_argb(min(1.0, max(0.0, lightness + 0.10)), chroma * 0.9, hue)


if __name__ == "__main__":
    sys.exit(main(sys.argv))
