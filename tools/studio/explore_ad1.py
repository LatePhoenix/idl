"""AD-1 style exploration: build sandbox SVGs and comparison sheets.

Sandbox SVGs live under tools/studio/explore/<A–G>/. Sheets and copies of sources
go to docs/art/exploration/v3/. Never writes to art/ or pack manifests.

Usage (from repo root, with Studio venv):
  tools\\studio\\.venv\\Scripts\\python tools/studio/explore_ad1.py
"""

from __future__ import annotations

import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STUDIO = Path(__file__).resolve().parent
sys.path.insert(0, str(STUDIO))
sys.path.insert(0, str(ROOT / "tools"))

from engine import compose, paths, render  # noqa: E402
import asset_pipeline as pipeline  # noqa: E402

ART = ROOT / "art" / "emoji_core"
ACCEPT = STUDIO / "acceptance"
EXPLORE = STUDIO / "explore"
DOCS = ROOT / "docs" / "art" / "exploration" / "v3"

DIRECTIONS = ("A", "B", "C", "D", "E", "F", "G")

# Canonical look pieces for every direction sheet.
HAIRS = ("hair_short_crop", "hair_long_straight", "hair_short_curly")
EXPRS = (
    ("neutral", "neutral_face"),
    ("happy", "smiling_face_with_smiling_eyes"),
    ("sad", "crying_face"),
)
# Surprised is composed from face parts (no pack expression yet).
SURPRISED_PARTS = ("eyes_round_wide", "brows_round_raised", "mouth_round_open")
ACCESSORIES = ("hat_beanie_slouch", "glasses_round_wire", "top_crew_tee")

SLOTS = {
    "base_teardrop": {
        "category": "base",
        "colorSlots": {
            "face.primary": "#FF8E6E",
            "face.shadow": "#E0755A",
            "face.highlight": "#FFB59A",
            "outline": "#7A4E00",
        },
    },
    "base_round": {
        "category": "base",
        "colorSlots": {
            "face.primary": "#FF8E6E",
            "face.shadow": "#E0755A",
            "face.highlight": "#FFB59A",
            "outline": "#7A4E00",
        },
    },
    "hair_short_crop": {
        "category": "hair",
        "colorSlots": {
            "hair.primary": "#5B3A29",
            "hair.shadow": "#3E271B",
            "hair.highlight": "#8C7569",
        },
    },
    "hair_long_straight": {
        "category": "hair",
        "colorSlots": {
            "hair.primary": "#5B3A29",
            "hair.shadow": "#3E271B",
            "hair.highlight": "#8C7569",
        },
    },
    "hair_short_curly": {
        "category": "hair",
        "colorSlots": {
            "hair.primary": "#5B3A29",
            "hair.shadow": "#3E271B",
            "hair.highlight": "#8C7569",
        },
    },
    "hat_beanie_slouch": {
        "category": "head_accessory",
        "colorSlots": {
            "hat.primary": "#3A5A8C",
            "hat.secondary": "#2A4068",
            "hat.shadow": "#2B4369",
            "hat.highlight": "#6F8CB8",
            "outline": "#7A4E00",
        },
    },
    "glasses_round_wire": {
        "category": "face_accessory",
        "colorSlots": {"glasses.frame": "#2B2B2B", "glasses.lens": "#6FA8DC"},
    },
    "top_crew_tee": {
        "category": "top",
        "colorSlots": {"top.primary": "#3A7A6A"},
    },
    "eyes_round_neutral": {"category": "face_eye", "colorSlots": {"eye.iris": "#3B2212"}},
    "eyes_round_happy": {"category": "face_eye", "colorSlots": {"eye.iris": "#3B2212"}},
    "eyes_round_closed": {"category": "face_eye", "colorSlots": {"eye.iris": "#3B2212"}},
    "eyes_round_wide": {
        "category": "face_eye",
        "colorSlots": {"eye.white": "#FFFFFF", "eye.primary": "#3B2212"},
    },
    "brows_round_relaxed": {"category": "face_brow", "colorSlots": {"hair.primary": "#5B3A29"}},
    "brows_round_worried": {"category": "face_brow", "colorSlots": {"hair.primary": "#5B3A29"}},
    "brows_round_raised": {"category": "face_brow", "colorSlots": {"hair.primary": "#5B3A29"}},
    "mouth_round_neutral": {"category": "face_mouth", "colorSlots": {"mouth": "#7A3030"}},
    "mouth_round_smile": {"category": "face_mouth", "colorSlots": {"mouth": "#7A3030"}},
    "mouth_round_frown": {"category": "face_mouth", "colorSlots": {"mouth": "#7A3030"}},
    "mouth_round_open": {"category": "face_mouth", "colorSlots": {"mouth": "#7A3030"}},
    "overlay_teardrops": {
        "category": "expression_overlay",
        "colorSlots": {"overlay.primary": "#6FA8DC"},
    },
}

# Head outline (face fill) — teardrop, matching shipped base.
TEARDROP_FACE = (
    "M 512 96 C 737.2 96 892 264.9 892 476 C 892 659 751.3 771.6 624.6 870.1 "
    "Q 512 954.5 399.4 870.1 C 272.7 771.6 132 659 132 476 C 132 264.9 286.8 96 512 96 Z"
)
TEARDROP_BODY = (
    "M 392 840 L 392 1000 C 392 1100 -40 1160 -160 1200 L -160 1536 L 1184 1536 "
    "L 1184 1200 C 1064 1160 632 1100 632 1000 L 632 840 Z"
)
# Rounder head for direction G (reopens D-45/D-46). Circle centred on brand proportions.
ROUND_FACE = paths.ellipse(512, 480, 380, 400)
ROUND_BODY = (
    "M 360 820 L 360 1000 C 360 1100 -40 1160 -160 1200 L -160 1536 L 1184 1536 "
    "L 1184 1200 C 1064 1160 664 1100 664 1000 L 664 820 Z"
)

# Improved hair (volume, hairline, centre part, highlight strands) — shared geometry for B–G.
HAIR_SHORT = {
    "back": (
        "M 200 300 C 120 360 100 480 160 560 C 220 520 240 420 200 300 Z "
        "M 824 300 C 904 360 924 480 864 560 C 804 520 784 420 824 300 Z "
        "M 512 40 C 620 40 700 100 720 180 C 640 140 384 140 304 180 C 324 100 404 40 512 40 Z"
    ),
    "top": (
        # Left bang / right bang with a clear centre part at x≈512.
        "M 250 300 C 260 160 360 110 470 180 C 500 220 490 280 450 310 "
        "C 380 330 280 330 250 300 Z "
        "M 774 300 C 764 160 664 110 554 180 C 524 220 534 280 574 310 "
        "C 644 330 744 330 774 300 Z"
    ),
    "side": (
        "M 150 340 C 110 420 120 520 180 580 C 220 540 230 440 190 360 Z "
        "M 874 340 C 914 420 904 520 844 580 C 804 540 794 440 834 360 Z"
    ),
    "highlight": (
        "M 540 150 C 600 190 660 240 700 250 C 660 270 590 250 530 190 Z "
        "M 320 200 C 360 170 400 180 420 210 C 380 230 340 230 320 200 Z"
    ),
    "shade": (
        "M 520 120 L 720 120 C 740 160 750 220 740 280 L 520 280 Z"
    ),
}

HAIR_LONG = {
    "back": (
        "M 512 40 C 620 40 700 100 720 180 C 640 140 384 140 304 180 C 324 100 404 40 512 40 Z "
        "M 860 220 C 940 400 980 720 900 1080 L 620 1080 C 500 980 620 860 740 700 "
        "C 820 500 850 320 860 220 Z "
        "M 164 220 C 84 400 44 720 124 1080 L 404 1080 C 524 980 404 860 284 700 "
        "C 204 500 174 320 164 220 Z"
    ),
    "top": (
        "M 250 320 C 270 150 370 100 470 180 C 500 220 490 300 450 330 "
        "C 380 350 280 350 250 320 Z "
        "M 774 320 C 754 150 654 100 554 180 C 524 220 534 300 574 330 "
        "C 644 350 744 350 774 320 Z"
    ),
    "side": (
        "M 140 340 C 80 480 90 700 160 900 C 220 820 240 560 200 400 Z "
        "M 884 340 C 944 480 934 700 864 900 C 804 820 784 560 824 400 Z"
    ),
    "highlight": (
        "M 540 150 C 600 190 670 250 710 280 C 670 300 590 270 530 200 Z "
        "M 300 210 C 350 170 410 190 430 230 C 380 250 330 250 300 210 Z"
    ),
    "shade": "M 520 120 L 740 120 C 760 200 770 280 760 340 L 520 340 Z",
}

HAIR_CURLY = {
    "back": (
        "M 512 20 C 640 20 760 90 800 200 C 700 140 324 140 224 200 "
        "C 264 90 384 20 512 20 Z "
        "M 160 260 C 80 320 60 440 120 520 C 180 460 200 340 160 260 Z "
        "M 864 260 C 944 320 964 440 904 520 C 844 460 824 340 864 260 Z"
    ),
    "top": (
        # Clump curls with a visible part.
        "M 200 320 C 180 240 220 160 300 140 C 360 120 400 160 420 220 "
        "C 440 280 400 330 340 340 C 270 350 220 350 200 320 Z "
        "M 300 200 C 280 140 320 90 380 90 C 440 90 470 140 460 200 "
        "C 450 250 400 280 350 270 C 310 260 310 230 300 200 Z "
        "M 824 320 C 844 240 804 160 724 140 C 664 120 624 160 604 220 "
        "C 584 280 624 330 684 340 C 754 350 804 350 824 320 Z "
        "M 724 200 C 744 140 704 90 644 90 C 584 90 554 140 564 200 "
        "C 574 250 624 280 674 270 C 714 260 714 230 724 200 Z"
    ),
    "side": (
        "M 120 300 C 60 380 70 500 140 560 C 190 500 200 380 160 320 Z "
        "M 904 300 C 964 380 954 500 884 560 C 834 500 824 380 864 320 Z"
    ),
    "highlight": (
        "M 360 120 C 400 100 450 120 470 160 C 430 180 380 170 360 120 Z "
        "M 580 110 C 640 130 690 180 720 200 C 680 220 620 190 580 110 Z"
    ),
    "shade": "M 512 90 L 780 90 C 800 150 810 220 800 280 L 512 280 Z",
}


def _svg(asset_id: str, schema: int, parts: list[str], defs: str = "") -> str:
    body = "\n".join(parts)
    defs_block = f"\n  <defs>\n{defs}\n  </defs>" if defs else ""
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"\n'
        f'     data-schema-version="{schema}" data-id="{asset_id}"\n'
        f'     data-content-version="1">{defs_block}\n{body}\n</svg>\n'
    )


def _path_el(part: str, z: int, slot: str, d: str, **extra) -> str:
    attrs = [f'data-part="{part}"', f'data-z="{z}"', f'data-slot="{slot}"']
    for key, value in extra.items():
        if value is None:
            continue
        attrs.append(f'data-{key.replace("_", "-")}="{value}"')
    return f'  <path {" ".join(attrs)} d="{d}"/>'


def _outline_ring(face_d: str, width: float) -> str:
    """Evenodd ring: outer face grown by width/2 minus inner shrunk."""
    outer = paths.offset(face_d, width / 2)
    inner = paths.offset(face_d, -width / 2)
    return f"{outer} {inner}"


def _read_shipped(name: str) -> str:
    return (ART / f"{name}.svg").read_text(encoding="utf-8")


def _set_id(svg: str, asset_id: str) -> str:
    return re.sub(r'data-id="[^"]*"', f'data-id="{asset_id}"', svg, count=1)


def _replace_d(svg: str, part: str, new_d: str) -> str:
    pattern = rf'(data-part="{part}"[^>]*\sd=")([^"]*)(")'
    if not re.search(pattern, svg):
        return svg
    return re.sub(pattern, rf'\1{new_d}\3', svg, count=1)


def _transform_all_paths(svg: str, fn) -> str:
    def repl(match: re.Match) -> str:
        return match.group(1) + fn(match.group(2)) + match.group(3)

    return re.sub(r'(\sd=")([^"]+)(")', repl, svg)


def _remove_gradient_face(svg: str) -> str:
    """Flat face: drop radial gradient, fill face with face.primary."""
    svg = re.sub(r"\s*<defs>.*?</defs>\s*", "\n", svg, flags=re.S)
    svg = re.sub(r'\s*data-gradient="[^"]*"', ' data-slot="face.primary"', svg)
    return svg


def _scale_paths_about(svg: str, centres: list[tuple[float, float]], scale: float, dy: float = 0) -> str:
    """Scale each closed contour toward nearest centre, then nudge by dy."""

    def fn(d: str) -> str:
        out = []
        for contour in paths.contours(d):
            l, t, r, b = paths.bounds(contour)
            cx, cy = (l + r) / 2, (t + b) / 2
            nearest = min(centres, key=lambda c: (c[0] - cx) ** 2 + (c[1] - cy) ** 2)
            out.append(paths.transform(contour, scale=scale, cx=nearest[0], cy=nearest[1], dy=dy))
        return " ".join(out)

    return _transform_all_paths(svg, fn)


def hair_svg(asset_id: str, geom: dict, *, bold_outline: bool = False, cel: bool = False,
             soft: bool = False) -> str:
    top_d, back_d, side_d, hi_d = geom["top"], geom["back"], geom["side"], geom["highlight"]
    shade_d = geom.get("shade", "")
    if soft:
        # Slightly puff the silhouette for C.
        try:
            top_d = paths.offset(top_d, 6)
            back_d = paths.offset(back_d, 4)
            side_d = paths.offset(side_d, 4)
        except Exception:
            pass
    parts = [
        _path_el("back", 20, "hair.shadow", back_d, tags="hair_back"),
        _path_el("side", 70, "hair.primary", side_d, tags="hair_side"),
        _path_el(
            "top", 70, "hair.primary", top_d, tags="hair_top",
            clip_by="occlude.hair_top:difference",
        ),
    ]
    if cel and shade_d:
        parts.append(
            _path_el(
                "shade", 70, "hair.shadow", shade_d, tags="hair_top",
                clip_by="occlude.hair_top:difference",
            )
        )
    parts.append(
        _path_el(
            "highlight", 80, "hair.highlight", hi_d, tags="hair_top",
            clip_by="occlude.hair_top:difference",
        )
    )
    if bold_outline:
        # Dark silhouette rim on the outer hair mass for 48 px read.
        try:
            rim = paths.offset(paths.union(back_d, paths.union(top_d, side_d)), 8)
            shell = paths.subtract(rim, paths.union(back_d, paths.union(top_d, side_d)))
            parts.insert(0, _path_el("rim", 18, "outline", shell, tags="hair_back"))
        except Exception:
            pass
    return _svg(asset_id, 2, parts)


def base_svg(asset_id: str, face_d: str, body_d: str, *, flat: bool = False, outline_w: float = 36,
             cel: bool = False, sticker: bool = False) -> str:
    parts: list[str] = [_path_el("body", 34, "face.primary", body_d)]
    defs = ""
    if flat:
        parts.append(_path_el("face", 40, "face.primary", face_d))
    else:
        defs = (
            '    <radialGradient id="grad_face" gradientUnits="userSpaceOnUse" '
            'cx="390" cy="270" r="680">\n'
            '      <stop offset="0.0" data-slot="face.highlight"/>\n'
            '      <stop offset="1.0" data-slot="face.primary"/>\n'
            "    </radialGradient>"
        )
        parts.append(
            f'  <path data-part="face" data-z="40" data-gradient="grad_face" d="{face_d}"/>'
        )
    if cel:
        # Bottom-right two-tone shade + upper-left rim light.
        shade = paths.intersect(
            face_d,
            "M 520 400 L 920 400 L 920 900 L 400 900 Z",
        )
        rim = paths.intersect(
            face_d,
            "M 100 100 L 480 100 L 480 500 L 100 500 Z",
        )
        if shade:
            parts.append(_path_el("shade", 40, "face.shadow", shade))
        if rim:
            parts.append(_path_el("rim_light", 41, "face.highlight", rim))
    else:
        # Soft cheek shade (shipped style).
        parts.append(
            _path_el(
                "shade", 40, "face.shadow",
                "M 662 760 C 662 784.3 634.2 804 600 804 C 565.8 804 538 784.3 538 760 "
                "C 538 735.7 565.8 716 600 716 C 634.2 716 662 735.7 662 760 Z",
            )
        )
    ring = _outline_ring(face_d, outline_w)
    parts.append(
        _path_el("outline", 40, "outline", ring, fill_rule="evenodd")
    )
    if sticker:
        # Thick light sticker border outside the dark outline.
        try:
            outer = paths.offset(face_d, 56)
            mid = paths.offset(face_d, outline_w / 2 + 4)
            sticker_ring = f"{outer} {mid}"
            parts.append(
                _path_el("sticker", 39, "face.highlight", sticker_ring, fill_rule="evenodd")
            )
            # Darker outer lip for wallpaper contrast.
            dark_outer = paths.offset(face_d, 64)
            dark_ring = f"{dark_outer} {outer}"
            parts.insert(
                0,
                _path_el("sticker_dark", 38, "outline", dark_ring, fill_rule="evenodd"),
            )
        except Exception:
            pass
    return _svg(asset_id, 1, parts, defs=defs)


def style_accessory(svg: str, *, bold: bool = False, soft: bool = False) -> str:
    if soft:
        try:
            return _transform_all_paths(svg, lambda d: paths.offset(d, 3) if "M" in d else d)
        except Exception:
            return svg
    if bold:
        # Thicken frame rings slightly by growing evenodd shapes.
        try:
            return _transform_all_paths(svg, lambda d: paths.offset(d, 2))
        except Exception:
            return svg
    return svg


def write(direction: str, name: str, text: str) -> Path:
    dest = EXPLORE / direction
    dest.mkdir(parents=True, exist_ok=True)
    path = dest / f"{name}.svg"
    path.write_text(text, encoding="utf-8", newline="\n")
    return path


def generate_direction(letter: str) -> None:
    out = EXPLORE / letter
    if out.exists():
        shutil.rmtree(out)
    out.mkdir(parents=True)

    if letter == "A":
        # Baseline: only sandbox the beanie + curly (not in shipped pack). Shipped art is referenced.
        write("A", "hat_beanie_slouch", _set_id((ACCEPT / "hat_beanie_slouch.svg").read_text(encoding="utf-8"), "hat_beanie_slouch"))
        write("A", "hair_short_curly", _set_id((ACCEPT / "hair_short_curly.svg").read_text(encoding="utf-8"), "hair_short_curly"))
        (out / "README.md").write_text(
            "Direction A uses shipped `art/emoji_core/*.svg` for base, short/long hair, "
            "glasses, tee and expressions. Only `hat_beanie_slouch` and `hair_short_curly` "
            "are sandboxed here (from ST-1 acceptance drafts).\n",
            encoding="utf-8",
        )
        return

    flat = letter == "B"
    soft = letter == "C"
    cel = letter == "D"
    sticker = letter == "E"
    chunky = letter == "F"
    round_head = letter == "G"

    outline_w = 44 if letter in {"B", "E"} else (36 if letter != "F" else 40)
    face_d = ROUND_FACE if round_head else TEARDROP_FACE
    body_d = ROUND_BODY if round_head else TEARDROP_BODY
    base_id = "base_round" if round_head else "base_teardrop"

    write(
        letter,
        base_id,
        base_svg(base_id, face_d, body_d, flat=flat or sticker and False, outline_w=outline_w,
                 cel=cel, sticker=sticker),
    )
    # B: flat face explicitly
    if flat:
        write(letter, base_id, base_svg(base_id, face_d, body_d, flat=True, outline_w=44, cel=False, sticker=False))

    write(letter, "hair_short_crop", hair_svg("hair_short_crop", HAIR_SHORT, bold_outline=letter == "B", cel=cel, soft=soft or chunky))
    write(letter, "hair_long_straight", hair_svg("hair_long_straight", HAIR_LONG, bold_outline=letter == "B", cel=cel, soft=soft or chunky))
    write(letter, "hair_short_curly", hair_svg("hair_short_curly", HAIR_CURLY, bold_outline=letter == "B", cel=cel, soft=soft or chunky))

    beanie = (ACCEPT / "hat_beanie_slouch.svg").read_text(encoding="utf-8")
    glasses = _read_shipped("glasses_round_wire")
    tee = _read_shipped("top_crew_tee")
    if soft:
        beanie = style_accessory(beanie, soft=True)
        glasses = style_accessory(glasses, soft=True)
    if letter == "B":
        beanie = style_accessory(beanie, bold=True)
        glasses = style_accessory(glasses, bold=True)
    write(letter, "hat_beanie_slouch", _set_id(beanie, "hat_beanie_slouch"))
    write(letter, "glasses_round_wire", _set_id(glasses, "glasses_round_wire"))
    write(letter, "top_crew_tee", _set_id(tee, "top_crew_tee"))

    # Face parts
    for name in (
        "eyes_round_neutral", "eyes_round_happy", "eyes_round_closed", "eyes_round_wide",
        "brows_round_relaxed", "brows_round_worried", "brows_round_raised",
        "mouth_round_neutral", "mouth_round_smile", "mouth_round_frown", "mouth_round_open",
        "overlay_teardrops",
    ):
        src = ART / f"{name}.svg"
        if not src.is_file():
            continue
        text = _read_shipped(name)
        if soft and name.startswith("eyes"):
            text = _scale_paths_about(text, [(375, 420), (650, 420)], 1.28, dy=28)
        elif soft and name.startswith("mouth"):
            text = _scale_paths_about(text, [(512, 600)], 0.78, dy=10)
        elif soft and name.startswith("brows"):
            text = _scale_paths_about(text, [(375, 292), (650, 292)], 1.05, dy=36)
        elif chunky and name.startswith("eyes"):
            text = _scale_paths_about(text, [(375, 420), (650, 420)], 1.12, dy=0)
        write(letter, name, _set_id(text, name))

    note = {
        "B": "Bold and flat: 44-unit outlines, flat face fill, no face gradient, bold hair rim.",
        "C": "Big-eye soft: eyes ~1.28× and lowered, smaller mouth, softer hair puff.",
        "D": "Cel-shaded: two-tone shade on head/hair plus upper-left rim light.",
        "E": "Sticker: thick light sticker border + dark outer lip for wallpaper contrast.",
        "F": "Readable volume (proposal): 40-unit outlines, chunky hair clumps with a clear "
             "centre part and dual highlight strands sized for 48 px, slightly larger eyes.",
        "G": "Rounder circular head — labelled as reopening D-45/D-46. Same accessory set.",
    }[letter]
    (out / "README.md").write_text(note + "\n", encoding="utf-8")


def _coerce_picture_numbers(picture: dict) -> dict:
    """parse_svg keeps gradient numbers as strings; Skia needs floats."""

    def num(value):
        return float(value) if isinstance(value, str) else value

    for part in picture.get("parts", []):
        fill = part.get("fill") or {}
        for kind in ("radial", "linear"):
            grad = fill.get(kind)
            if not grad:
                continue
            for key in ("cx", "cy", "r", "x1", "y1", "x2", "y2"):
                if key in grad:
                    grad[key] = num(grad[key])
            for stop in grad.get("stops") or []:
                if "offset" in stop:
                    stop["offset"] = num(stop["offset"])
                if "alpha" in stop:
                    stop["alpha"] = num(stop["alpha"])
    return picture


def load_explore_assets(letter: str) -> dict[str, dict]:
    """Compile explore SVGs into Library draft overlays."""
    drafts: dict[str, dict] = {}
    folder = EXPLORE / letter
    for svg_path in sorted(folder.glob("*.svg")):
        asset_id = svg_path.stem
        meta = SLOTS.get(asset_id)
        if not meta:
            # Infer from shipped pack if possible.
            from engine import repo
            found = repo.find_asset(asset_id)
            if found:
                _, asset = found
                meta = {
                    "category": asset["category"],
                    "colorSlots": dict(asset.get("colorSlots") or {}),
                }
            else:
                continue
        try:
            picture = _coerce_picture_numbers(
                pipeline.parse_svg(svg_path.read_text(encoding="utf-8"), svg_path.name)
            )
        except pipeline.PipelineError as err:
            print(f"WARN {letter}/{asset_id}: {err}")
            continue
        drafts[asset_id] = {
            "id": asset_id,
            "category": meta["category"],
            "accessibilityLabel": asset_id,
            "colorSlots": meta["colorSlots"],
            "tier": "free",
            "contentVersion": 1,
            "picture": picture,
            "draft": True,
        }
    # Direction G: also alias base_round as the library base by replacing base_teardrop.
    if letter == "G" and "base_round" in drafts:
        round_asset = dict(drafts["base_round"])
        round_asset["id"] = "base_teardrop"
        drafts["base_teardrop"] = round_asset
    return drafts


def look(library: compose.Library, hair: str, expression: str, extras: list[str] | None = None):
    items = [hair, *(extras or [])]
    if expression == "surprised":
        return library.look([*items, *SURPRISED_PARTS], "neutral_face")
    return library.look(items, expression)


def direction_sheet(letter: str, library: compose.Library) -> "skia.Image":
    hair_default = "hair_short_crop"
    rows: list[tuple[str, list[render.Cell]]] = []

    # Size row: 48(4×) + 96 + bust 512, light and dark
    for wall in ("light", "dark"):
        base = look(library, hair_default, "neutral_face")
        rows.append((
            f"Sizes · {wall}",
            [
                render.Cell(base, "48 (4×)", size=48, zoom=4, wall=wall),
                render.Cell(base, "96", size=96, wall=wall),
                render.Cell(base, "bust 512", size=512, framing="bust", wall=wall),
            ],
        ))

    # Expressions
    expr_cells = []
    for label, expr_id in EXPRS:
        expr_cells.append(
            render.Cell(look(library, hair_default, expr_id), label, size=96, wall="light")
        )
    expr_cells.append(
        render.Cell(look(library, hair_default, "surprised"), "surprised", size=96, wall="light")
    )
    rows.append(("Expressions", expr_cells))

    # Hairstyles
    hair_cells = [
        render.Cell(look(library, h, "neutral_face"), h.replace("hair_", ""), size=96, wall="light")
        for h in HAIRS
    ]
    rows.append(("Hair", hair_cells))

    # Accessories on short hair
    acc_cells = [
        render.Cell(look(library, hair_default, "neutral_face", [a]), a.split("_")[0], size=96, wall="light")
        for a in ACCESSORIES
    ]
    # Full combo
    acc_cells.append(
        render.Cell(
            look(library, "hair_long_straight", "smiling_face_with_smiling_eyes",
                 ["hat_beanie_slouch", "glasses_round_wire"]),
            "combo",
            size=96,
            wall="dark",
        )
    )
    rows.append(("Accessories", acc_cells))

    title = f"AD-1 · {letter}"
    if letter == "G":
        title += " (reopens D-45/D-46)"
    return render.sheet(rows, title=title)


def overview_sheet(libraries: dict[str, compose.Library]):
    rows = []
    for size, zoom, label in ((96, 1, "96 px"), (48, 4, "48 px (4×)")):
        cells = []
        for letter in DIRECTIONS:
            lib = libraries[letter]
            assets = look(lib, "hair_short_crop", "neutral_face")
            tag = f"{letter}*" if letter == "G" else letter
            cells.append(render.Cell(assets, tag, size=size, zoom=zoom, wall="light"))
        rows.append((label, cells))
    # Dark 96 row
    cells = []
    for letter in DIRECTIONS:
        lib = libraries[letter]
        assets = look(lib, "hair_short_crop", "neutral_face")
        tag = f"{letter}*" if letter == "G" else letter
        cells.append(render.Cell(assets, tag, size=96, wall="dark"))
    rows.append(("96 px dark", cells))
    return render.sheet(rows, title="AD-1 overview · A–G (* G reopens D-45/D-46)")


def copy_sources_to_docs(letter: str) -> None:
    dest = DOCS / letter
    dest.mkdir(parents=True, exist_ok=True)
    src = EXPLORE / letter
    for path in src.iterdir():
        if path.suffix in {".svg", ".md"}:
            shutil.copy2(path, dest / path.name)


def main() -> int:
    print("Generating explore SVGs…")
    for letter in DIRECTIONS:
        generate_direction(letter)
        print(f"  {letter}: {len(list((EXPLORE / letter).glob('*.svg')))} svgs")

    print("Building libraries and sheets…")
    DOCS.mkdir(parents=True, exist_ok=True)
    libraries: dict[str, compose.Library] = {}
    for letter in DIRECTIONS:
        drafts = load_explore_assets(letter)
        # Direction A also needs beanie/curly; short/long from pack.
        libraries[letter] = compose.Library(drafts)
        # Ensure curly/beanie exist for A (they do via drafts).
        image = direction_sheet(letter, libraries[letter])
        out_dir = DOCS / letter
        out_dir.mkdir(parents=True, exist_ok=True)
        sheet_path = out_dir / "sheet.png"
        render.save_png(image, sheet_path)
        copy_sources_to_docs(letter)
        print(f"  sheet {sheet_path.relative_to(ROOT)} ({image.width()}×{image.height()})")

    overview = overview_sheet(libraries)
    overview_path = DOCS / "overview.png"
    render.save_png(overview, overview_path)
    print(f"  overview {overview_path.relative_to(ROOT)} ({overview.width()}×{overview.height()})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
