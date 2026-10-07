"""Write the shared mood-face SVGs for AP-6 batch 1. Stdlib only.

Neutral and happy pictures already exist. These are the extra shapes the
priority-1 faces need, plus the overlay set from the style guide.
"""

import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art" / "emoji_core"
MANIFEST = ROOT / "app/src/main/assets/packs/emoji_core/v2/manifest.json"

K = 0.5522847498
LX, RX, EY = 375, 650, 420


def fmt(n):
    text = f"{n:.1f}".rstrip("0").rstrip(".")
    return text if text not in {"", "-0"} else "0"


def oval(cx, cy, rx, ry):
    return (
        f"M {fmt(cx - rx)} {fmt(cy)} "
        f"C {fmt(cx - rx)} {fmt(cy - ry * K)} {fmt(cx - rx * K)} {fmt(cy - ry)} {fmt(cx)} {fmt(cy - ry)} "
        f"C {fmt(cx + rx * K)} {fmt(cy - ry)} {fmt(cx + rx)} {fmt(cy - ry * K)} {fmt(cx + rx)} {fmt(cy)} "
        f"C {fmt(cx + rx)} {fmt(cy + ry * K)} {fmt(cx + rx * K)} {fmt(cy + ry)} {fmt(cx)} {fmt(cy + ry)} "
        f"C {fmt(cx - rx * K)} {fmt(cy + ry)} {fmt(cx - rx)} {fmt(cy + ry * K)} {fmt(cx - rx)} {fmt(cy)} Z"
    )


def pair(path_for):
    return path_for(LX) + " " + path_for(RX)


def wedge(cx, y, half, depth, up):
    sign = -1 if up else 1
    return (
        f"M {fmt(cx - half)} {fmt(y)} "
        f"C {fmt(cx - half * 0.35)} {fmt(y + sign * depth)} {fmt(cx + half * 0.35)} {fmt(y + sign * depth)} {fmt(cx + half)} {fmt(y)} "
        f"C {fmt(cx + half * 0.45)} {fmt(y + sign * depth * 0.62)} {fmt(cx - half * 0.45)} {fmt(y + sign * depth * 0.62)} {fmt(cx - half)} {fmt(y)} Z"
    )


def spiral(cx, cy):
    # Separate beads. A single filled ribbon collapses into a blob at this size.
    blobs = []
    for i in range(7):
        t = i / 6
        ang = -math.pi / 2 + t * 1.35 * 2 * math.pi
        radius = 12 + t * 46
        blobs.append(oval(cx + radius * math.cos(ang), cy + radius * math.sin(ang), 13, 13))
    return " ".join(blobs)


def svg(asset_id, parts, schema=1):
    rows = []
    for part in parts:
        attrs = [
            f'data-part="{part["id"]}"',
            f'data-z="{part["z"]}"',
            f'data-slot="{part["slot"]}"',
        ]
        if part.get("fill"):
            attrs.append(f'data-fill-rule="{part["fill"]}"')
        attrs.append(f'd="{part["d"]}"')
        rows.append("  <path " + " ".join(attrs) + "/>")
    body = "\n".join(rows)
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"\n'
        f'     data-schema-version="{schema}" data-id="{asset_id}"\n'
        '     data-content-version="1">\n'
        f"{body}\n"
        "</svg>\n"
    )


def eyes(asset_id, slots):
    parts = []
    for index, (slot, maker) in enumerate(slots):
        parts.append({"id": f"eyes_{index}", "z": 50, "slot": slot, "d": pair(maker)})
    return svg(asset_id, parts)


def one(asset_id, slot, d, part="shape", z=50, fill=None):
    return svg(asset_id, [{"id": part, "z": z, "slot": slot, "d": d, "fill": fill}])


def write_all():
    files = {
        "eyes_round_closed": eyes("eyes_round_closed", [
            ("eye.primary", lambda cx: wedge(cx, EY + 8, 78, 36, up=True)),
        ]),
        "eyes_round_wide": eyes("eyes_round_wide", [
            ("eye.white", lambda cx: oval(cx, EY, 58, 64)),
            ("eye.primary", lambda cx: oval(cx, EY + 6, 24, 28)),
        ]),
        "eyes_round_squint": eyes("eyes_round_squint", [
            ("eye.primary", lambda cx: wedge(cx, EY + 4, 72, 22, up=True)),
        ]),
        "eyes_round_star": eyes("eyes_round_star", [
            ("eye.primary", lambda cx: (
                f"M {fmt(cx)} {fmt(EY - 58)} L {fmt(cx + 16)} {fmt(EY - 16)} L {fmt(cx + 58)} {fmt(EY)} "
                f"L {fmt(cx + 16)} {fmt(EY + 16)} L {fmt(cx)} {fmt(EY + 58)} L {fmt(cx - 16)} {fmt(EY + 16)} "
                f"L {fmt(cx - 58)} {fmt(EY)} L {fmt(cx - 16)} {fmt(EY - 16)} Z"
            )),
        ]),
        "eyes_round_spiral": eyes("eyes_round_spiral", [
            ("eye.primary", lambda cx: spiral(cx, EY)),
        ]),
        "eyes_round_bags": eyes("eyes_round_bags", [
            ("eye.primary", lambda cx: oval(cx, EY - 8, 30, 32)),
            ("eye.primary", lambda cx: wedge(cx, EY + 34, 48, 36, up=False)),
        ]),
        "brows_round_worried": one(
            "brows_round_worried", "hair.primary",
            wedge(390, 348, 80, 48, up=False).replace("M 310", "M 310", 1)
            + " "
            + wedge(634, 348, 80, 48, up=False),
            part="brows",
        ),
        "brows_round_raised": one(
            "brows_round_raised", "hair.primary",
            wedge(375, 292, 78, 28, up=True) + " " + wedge(650, 292, 78, 28, up=True),
            part="brows",
        ),
        "mouth_round_slight_smile": one("mouth_round_slight_smile", "mouth", wedge(512, 575, 90, 48, up=False), part="mouth"),
        "mouth_round_grin": one("mouth_round_grin", "mouth", wedge(512, 540, 140, 130, up=False), part="mouth"),
        "mouth_round_frown": one("mouth_round_frown", "mouth", wedge(512, 660, 110, 90, up=True), part="mouth"),
        "mouth_round_slight_frown": one("mouth_round_slight_frown", "mouth", wedge(512, 640, 90, 42, up=True), part="mouth"),
        "mouth_round_open": one("mouth_round_open", "mouth", oval(512, 640, 72, 86), part="mouth"),
        "mouth_round_yawn": one("mouth_round_yawn", "mouth", oval(512, 670, 48, 120), part="mouth"),
        "mouth_round_grimace": one(
            "mouth_round_grimace", "mouth",
            "M 390 590 C 430 650 470 540 512 620 C 554 700 594 540 634 590 C 600 670 424 670 390 590 Z",
            part="mouth",
        ),
        "mouth_round_tongue": svg("mouth_round_tongue", [
            {"id": "mouth", "z": 50, "slot": "mouth", "d": oval(512, 600, 90, 46)},
            {"id": "tongue", "z": 50, "slot": "mouth", "d": "M 456 620 C 468 760 556 760 568 620 C 540 710 484 710 456 620 Z"},
        ]),
        "mouth_round_asleep": one("mouth_round_asleep", "mouth", wedge(512, 600, 36, 14, up=False), part="mouth"),
        "overlay_sweat_drop": one("overlay_sweat_drop", "overlay.sweat", "M 760 220 C 800 300 800 370 760 410 C 720 370 720 300 760 220 Z", part="sweat", z=110),
        "overlay_teardrops": one(
            "overlay_teardrops", "overlay.tears",
            "M 390 490 C 420 570 420 640 390 680 C 360 640 360 570 390 490 Z "
            "M 634 490 C 664 570 664 640 634 680 C 604 640 604 570 634 490 Z",
            part="tears", z=110,
        ),
        "overlay_zzz_marks": svg("overlay_zzz_marks", [
            {"id": "z_large", "z": 110, "slot": "overlay.zzz", "d": zed(690, 150, 120, 90, 18)},
            {"id": "z_small", "z": 110, "slot": "overlay.zzz", "d": zed(820, 250, 90, 70, 14)},
        ]),
        "overlay_thermo_mark": svg("overlay_thermo_mark", [
            {"id": "stem", "z": 110, "slot": "overlay.thermo", "d": "M 774 180 L 806 180 L 806 390 L 774 390 Z"},
            {"id": "bulb", "z": 110, "slot": "overlay.thermo", "d": oval(790, 420, 36, 36)},
        ]),
        "overlay_cheek_blush": one(
            "overlay_cheek_blush", "overlay.blush",
            oval(300, 560, 42, 26) + " " + oval(724, 560, 42, 26),
            part="blush", z=110,
        ),
        "overlay_heart_marks": one(
            "overlay_heart_marks", "overlay.hearts",
            "M 250 250 C 250 210 310 200 320 240 C 330 200 390 210 390 250 C 390 310 320 360 320 360 C 320 360 250 310 250 250 Z "
            "M 680 200 C 680 164 732 156 742 192 C 752 156 804 164 804 200 C 804 252 742 296 742 296 C 742 296 680 252 680 200 Z",
            part="hearts", z=110,
        ),
        "overlay_steam_puffs": one(
            "overlay_steam_puffs", "overlay.steam",
            oval(460, 250, 36, 22) + " " + oval(530, 200, 42, 26) + " " + oval(600, 150, 30, 18),
            part="steam", z=110,
        ),
        "overlay_sparkle_marks": svg("overlay_sparkle_marks", [
            {"id": "spark_left", "z": 110, "slot": "overlay.sparkle", "d": star(250, 240, 48)},
            {"id": "spark_right", "z": 110, "slot": "overlay.sparkle", "d": star(770, 190, 40)},
        ]),
        "overlay_monocle_lens": svg("overlay_monocle_lens", [
            {"id": "rim", "z": 110, "slot": "glasses.frame", "d": oval(650, 420, 70, 70) + " " + oval(650, 420, 48, 48), "fill": "evenodd"},
            {"id": "chain", "z": 110, "slot": "glasses.frame", "d": "M 640 486 L 660 486 L 676 548 L 656 548 Z"},
        ]),
        "overlay_party_cone": svg("overlay_party_cone", [
            {"id": "cone", "z": 110, "slot": "overlay.hat", "d": "M 512 20 L 720 250 L 304 250 Z"},
            {"id": "band", "z": 110, "slot": "overlay.hat", "d": "M 330 220 L 694 220 L 694 250 L 330 250 Z"},
        ]),
    }
    for asset_id, text in files.items():
        (ART / f"{asset_id}.svg").write_text(text, encoding="utf-8", newline="\n")
    return files


def zed(x, y, w, h, t):
    return (
        f"M {x} {y} L {x + w} {y} L {x + w} {y + t} L {x + t * 2} {y + t} "
        f"L {x + w} {y + h - t} L {x + w} {y + h} L {x} {y + h} L {x} {y + h - t} "
        f"L {x + w - t * 2} {y + h - t} L {x} {y + t} Z"
    )


def star(cx, cy, r):
    return (
        f"M {cx} {cy - r} L {cx + r * 0.28} {cy - r * 0.28} L {cx + r} {cy} "
        f"L {cx + r * 0.28} {cy + r * 0.28} L {cx} {cy + r} L {cx - r * 0.28} {cy + r * 0.28} "
        f"L {cx - r} {cy} L {cx - r * 0.28} {cy - r * 0.28} Z"
    )


ASSETS = {
    "eyes_round_closed": ("face_eye", "Closed eyes", {"eye.primary": "#3B2A1A"}),
    "eyes_round_wide": ("face_eye", "Wide eyes", {"eye.white": "#FFF6E8", "eye.primary": "#3B2A1A"}),
    "eyes_round_squint": ("face_eye", "Squinting eyes", {"eye.primary": "#3B2A1A"}),
    "eyes_round_star": ("face_eye", "Star eyes", {"eye.primary": "#3B2A1A"}),
    "eyes_round_spiral": ("face_eye", "Spiral eyes", {"eye.primary": "#3B2A1A"}),
    "eyes_round_bags": ("face_eye", "Tired eyes", {"eye.primary": "#3B2A1A"}),
    "brows_round_worried": ("face_brow", "Worried brows", {"hair.primary": "#5B3A29"}),
    "brows_round_raised": ("face_brow", "Raised brows", {"hair.primary": "#5B3A29"}),
    "mouth_round_slight_smile": ("face_mouth", "Slight smile", {"mouth": "#6B2E1F"}),
    "mouth_round_grin": ("face_mouth", "Grin", {"mouth": "#6B2E1F"}),
    "mouth_round_frown": ("face_mouth", "Frown", {"mouth": "#6B2E1F"}),
    "mouth_round_slight_frown": ("face_mouth", "Slight frown", {"mouth": "#6B2E1F"}),
    "mouth_round_open": ("face_mouth", "Open mouth", {"mouth": "#6B2E1F"}),
    "mouth_round_yawn": ("face_mouth", "Yawn", {"mouth": "#6B2E1F"}),
    "mouth_round_grimace": ("face_mouth", "Grimace", {"mouth": "#6B2E1F"}),
    "mouth_round_tongue": ("face_mouth", "Tongue", {"mouth": "#6B2E1F"}),
    "mouth_round_asleep": ("face_mouth", "Asleep mouth", {"mouth": "#6B2E1F"}),
    "overlay_sweat_drop": ("expression_overlay", "Sweat drop", {"overlay.sweat": "#7EC8E3"}),
    "overlay_teardrops": ("expression_overlay", "Tears", {"overlay.tears": "#7EC8E3"}),
    "overlay_zzz_marks": ("expression_overlay", "Sleep Zs", {"overlay.zzz": "#5B3A29"}),
    "overlay_thermo_mark": ("expression_overlay", "Thermometer", {"overlay.thermo": "#D64545"}),
    "overlay_cheek_blush": ("expression_overlay", "Blush", {"overlay.blush": "#F0A0A8"}),
    "overlay_heart_marks": ("expression_overlay", "Hearts", {"overlay.hearts": "#E25B7A"}),
    "overlay_steam_puffs": ("expression_overlay", "Steam", {"overlay.steam": "#C8D0D8"}),
    "overlay_sparkle_marks": ("expression_overlay", "Sparkles", {"overlay.sparkle": "#F0C84A"}),
    "overlay_monocle_lens": ("expression_overlay", "Monocle", {"glasses.frame": "#2B2B2B"}),
    "overlay_party_cone": ("expression_overlay", "Party hat", {"overlay.hat": "#E25B7A"}),
}

# Procedural defaults keep non-teardrop moods on the parts they already used.
# Teardrop parts are the vector drawings. Overlays listed here are teardrop-only
# except overlay_blush, which already ships for every base.
EXPRESSIONS = [
    ("neutral_face", "Neutral face", "eyes_open", "brows_flat", "mouth_flat", [],
     "eyes_round_neutral", "brows_round_relaxed", "mouth_round_neutral", []),
    ("slightly_smiling_face", "Slightly smiling face", "eyes_happy_arc", "brows_relaxed", "mouth_smile", ["overlay_blush"],
     "eyes_round_neutral", "brows_round_relaxed", "mouth_round_slight_smile", []),
    ("smiling_face_with_smiling_eyes", "Smiling face with smiling eyes", "eyes_happy_arc", "brows_relaxed", "mouth_smile", ["overlay_blush"],
     "eyes_round_happy", "brows_round_relaxed", "mouth_round_smile", []),
    ("star_struck", "Star-struck", "eyes_sparkle", "brows_raised", "mouth_big_grin", [],
     "eyes_round_star", "brows_round_raised", "mouth_round_grin", ["overlay_sparkle_marks"]),
    ("sleeping_face", "Sleeping face", "eyes_closed_line", "brows_relaxed", "mouth_small_o", [],
     "eyes_round_closed", "brows_round_relaxed", "mouth_round_asleep", ["overlay_zzz_marks"]),
    ("yawning_face", "Yawning face", "eyes_half_lid", "brows_flat", "mouth_flat", [],
     "eyes_round_closed", "brows_round_relaxed", "mouth_round_yawn", []),
    ("face_with_bags_under_eyes", "Face with bags under eyes", "eyes_half_lid", "brows_flat", "mouth_flat", [],
     "eyes_round_bags", "brows_round_worried", "mouth_round_slight_frown", []),
    ("persevering_face", "Persevering face", "eyes_wide", "brows_worried", "mouth_wavy", [],
     "eyes_round_squint", "brows_round_worried", "mouth_round_grimace", []),
    ("anxious_face_with_sweat", "Anxious face with sweat", "eyes_wide", "brows_worried", "mouth_wavy", [],
     "eyes_round_wide", "brows_round_worried", "mouth_round_frown", ["overlay_sweat_drop"]),
    ("crying_face", "Crying face", "eyes_tearful", "brows_worried", "mouth_frown", [],
     "eyes_round_closed", "brows_round_worried", "mouth_round_frown", ["overlay_teardrops"]),
    ("face_with_thermometer", "Face with thermometer", "eyes_half_lid", "brows_worried", "mouth_wavy", [],
     "eyes_round_neutral", "brows_round_worried", "mouth_round_slight_frown", ["overlay_thermo_mark"]),
    ("face_with_monocle", "Face with monocle", "eyes_narrow", "brows_determined", "mouth_flat", [],
     "eyes_round_neutral", "brows_round_raised", "mouth_round_slight_smile", ["overlay_monocle_lens"]),
    ("zany_face", "Zany face", "eyes_side_glance", "brows_determined", "mouth_smirk", [],
     "eyes_round_wide", "brows_round_raised", "mouth_round_tongue", []),
    ("partying_face", "Partying face", "eyes_wink", "brows_raised", "mouth_big_grin", [],
     "eyes_round_happy", "brows_round_raised", "mouth_round_grin", ["overlay_party_cone"]),
    ("face_with_spiral_eyes", "Face with spiral eyes", "eyes_spiral", "brows_raised", "mouth_open_o", [],
     "eyes_round_spiral", "brows_round_raised", "mouth_round_open", ["overlay_steam_puffs"]),
]


def asset_entry(asset_id):
    category, label, slots = ASSETS[asset_id]
    return {
        "id": asset_id,
        "category": category,
        "accessibilityLabel": label,
        "render": {"type": "vector", "file": f"pictures/{asset_id}.json"},
        "collection": "emoji_core",
        "license": "proprietary-idl",
        "contentVersion": 1,
        "colorSlots": slots,
        "compatibleBases": ["base_teardrop"],
    }


def expression_entry(row):
    eid, label, eyes, brows, mouth, kept, t_eyes, t_brows, t_mouth, extra = row
    entry = {
        "id": eid,
        "label": label,
        "eyes": eyes,
        "brows": brows,
        "mouth": mouth,
        "baseOverrides": {
            "base_teardrop": {"eyes": t_eyes, "brows": t_brows, "mouth": t_mouth},
        },
    }
    overlays = list(kept) + list(extra)
    if overlays:
        entry["overlays"] = overlays
    return entry


def indent_block(value):
    dumped = json.dumps(value, indent=2)
    lines = dumped.splitlines()
    return lines[0] + "\n" + "\n".join("  " + line for line in lines[1:])


def patch_manifest():
    text = MANIFEST.read_text(encoding="utf-8")
    if '"id": "eyes_round_closed"' not in text:
        marker = '\n  ],\n  "expressionOverrides":'
        assets = [asset_entry(asset_id) for asset_id in ASSETS]
        block = ",\n".join(json.dumps(asset, indent=2) for asset in assets)
        padded = "\n".join("    " + line if line else line for line in block.splitlines())
        if marker not in text:
            raise SystemExit("manifest insertion point missing")
        text = text.replace(marker, ",\n" + padded + marker, 1)
    if '"expressions"' not in text:
        marker = '  "expressionOverrides":'
        expressions = [expression_entry(row) for row in EXPRESSIONS]
        block = '  "expressions": ' + indent_block(expressions) + ",\n"
        if marker not in text:
            raise SystemExit("expressionOverrides missing")
        text = text.replace(marker, block + marker, 1)
    MANIFEST.write_text(text, encoding="utf-8", newline="\n")


def main():
    written = write_all()
    patch_manifest()
    print(f"wrote {len(written)} svgs")


if __name__ == "__main__":
    main()
