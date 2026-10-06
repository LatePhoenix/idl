"""Write the brand-matched base_teardrop picture and the features refit to it.

Run from the repo root: python tools/gen_teardrop_base.py

The face outline is the mapped path from config/teardrop_silhouette.json. The ring is that
path offset inward by 36 units. Feature paths are the AP-1 placements (eye line y 420,
mouth y 580) and are regenerated here so they are not drifted by hand.
"""
import json
from pathlib import Path

from teardrop_geometry import (
    chin_bottom,
    ellipse_commands,
    mapped_segments,
    nearest_distance,
    neck_commands,
    offset_inward,
    outside_points,
    parse_commands,
    path_commands,
    sample,
    shade_commands,
    x_range_at,
)

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "app/src/main/assets/packs/emoji_core/v2"
PICTURES = PACK / "pictures"

EYE_L = (375, 420)
EYE_R = (650, 420)
MOUTH = (512, 580)


def dump(path, data):
    path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8", newline="\n")


def picture(asset_id, version, parts, clips=None):
    data = {
        "schemaVersion": 1,
        "id": asset_id,
        "contentVersion": version,
        "viewBox": 1024,
        "parts": parts,
    }
    if clips:
        data["clipPaths"] = clips
    return data


def part(part_id, z, fill, commands, fill_rule=None, opacity=None, clip=None):
    body = {"id": part_id, "zBand": z, "fill": fill, "commands": commands}
    if opacity is not None:
        body["opacity"] = opacity
    if clip is not None:
        body["clip"] = clip
    if fill_rule is not None:
        body["fillRule"] = fill_rule
    return body


def slot(name):
    return {"slot": name}


def require_inside(name, commands, silhouette, slack=8):
    bad = outside_points(commands, silhouette, slack)
    if bad:
        sample_at = ", ".join(f"({p[0]:.0f},{p[1]:.0f})" for p in bad[:6])
        raise SystemExit(f"{name} leaves the silhouette: {sample_at}")


def base_picture(silhouette):
    face = path_commands(silhouette)
    inner = path_commands(offset_inward(silhouette))
    radial = {
        "radial": {
            "cx": 390,
            "cy": 270,
            "r": 680,
            "stops": [
                {"offset": 0.0, "slot": "face.highlight"},
                {"offset": 1.0, "slot": "face.primary"},
            ],
        }
    }
    # Neck first so the face covers it. The chin overlaps the neck until AP-3 adds the body.
    parts = [
        part("neck", 40, slot("face.primary"), neck_commands()),
        part("face", 40, radial, face),
        part("shade", 40, slot("face.shadow"), shade_commands()),
        part("outline", 40, slot("outline"), f"{face} {inner}", fill_rule="evenodd"),
    ]
    return picture("base_teardrop", 2, parts)


def eyes_neutral():
    commands = ellipse_commands(*EYE_L, 40, 52) + " " + ellipse_commands(*EYE_R, 40, 52)
    return picture("eyes_round_neutral", 3, [part("eyes", 50, slot("eye.iris"), commands)])


def eyes_happy():
    # Closed crescents on the eye line. Centres sit on x 375 and 650.
    commands = (
        "M 303 420 C 341 356 409 356 447 420 C 411 398 339 398 303 420 Z "
        "M 578 420 C 616 356 684 356 722 420 C 686 398 614 398 578 420 Z"
    )
    return picture("eyes_round_happy", 3, [part("eyes", 50, slot("eye.iris"), commands)])


def brows():
    # Bottoms near y 342, about 78 above the eye line and clear of the eye tops at y 368.
    commands = (
        "M 305 342 C 345 306 405 306 445 342 C 407 328 343 328 305 342 Z "
        "M 580 342 C 620 306 680 306 720 342 C 682 328 618 328 580 342 Z"
    )
    return picture("brows_round_relaxed", 3, [part("brows", 50, slot("hair.primary"), commands)])


def mouth_neutral():
    commands = ellipse_commands(*MOUTH, 96, 22)
    return picture("mouth_round_neutral", 3, [part("mouth", 50, slot("mouth"), commands)])


def mouth_smile():
    commands = "M 424 562 C 456 668 568 668 600 562 C 564 628 460 628 424 562 Z"
    return picture("mouth_round_smile", 3, [part("mouth", 50, slot("mouth"), commands)])


def glasses():
    frames = " ".join([
        ellipse_commands(*EYE_L, 84, 84),
        ellipse_commands(*EYE_L, 60, 60),
        ellipse_commands(*EYE_R, 84, 84),
        ellipse_commands(*EYE_R, 60, 60),
        "M 447 406 L 578 406 L 578 434 L 447 434 Z",
    ])
    lenses = ellipse_commands(*EYE_L, 56, 56) + " " + ellipse_commands(*EYE_R, 56, 56)
    parts = [
        part("frames", 80, slot("glasses.frame"), frames, fill_rule="evenodd"),
        part("lenses", 80, slot("glasses.lens"), lenses, opacity=0.35),
    ]
    return picture("glasses_round_wire", 3, parts)


def hair_bob():
    cap = "M 512 34 C 590 34 630 78 600 118 C 575 148 449 148 424 118 C 394 78 434 34 512 34 Z"
    # Side masses end at the jaw. The inner edge stays inside the new, narrower chin.
    right = "M 905 420 C 965 540 980 660 910 760 C 860 810 740 800 680 750 C 750 680 810 540 860 440 Z"
    left = "M 119 420 C 59 540 44 660 114 760 C 164 810 284 800 344 750 C 274 680 214 540 164 440 Z"
    parts = [
        part("back", 20, slot("hair.shadow"), f"{cap} {right} {left}"),
        part(
            "bangs",
            70,
            slot("hair.primary"),
            "M 300 312 C 320 188 400 150 468 214 C 498 246 486 288 444 312 "
            "C 380 328 322 324 300 312 Z "
            "M 724 312 C 704 188 624 150 556 214 C 526 246 538 288 580 312 "
            "C 644 328 702 324 724 312 Z",
        ),
        part(
            "highlight",
            80,
            slot("hair.highlight"),
            "M 548 196 C 600 230 655 268 700 292 C 668 308 600 286 542 230 Z",
        ),
    ]
    return picture("hair_bob", 2, parts)


def hair_long():
    cap = "M 512 34 C 590 34 630 78 600 118 C 575 148 449 148 424 118 C 394 78 434 34 512 34 Z"
    right = (
        "M 855 260 C 930 420 990 700 880 1040 L 600 1040 "
        "C 430 960 560 860 700 720 C 800 520 830 360 845 280 Z"
    )
    left = (
        "M 169 260 C 94 420 34 700 144 1040 L 424 1040 "
        "C 594 960 464 860 324 720 C 224 520 194 360 179 280 Z"
    )
    # Bangs end at y 340, twenty-eight above the open-eye tops at y 368.
    bangs = (
        "M 300 324 C 320 176 400 138 468 202 C 498 234 486 300 444 324 "
        "C 380 340 322 336 300 324 Z "
        "M 724 324 C 704 176 624 138 556 202 C 526 234 538 300 580 324 "
        "C 644 340 702 336 724 324 Z"
    )
    parts = [
        part("back", 20, slot("hair.shadow"), f"{cap} {right} {left}"),
        part("bangs", 70, slot("hair.primary"), bangs),
        part(
            "highlight",
            80,
            slot("hair.highlight"),
            "M 548 196 C 600 230 655 268 700 312 C 668 328 600 286 542 230 Z",
        ),
    ]
    return picture("hair_long_straight", 2, parts)


def beard():
    commands = (
        "M 360 640 C 310 740 360 840 460 900 C 495 928 529 928 564 900 "
        "C 664 840 714 740 664 640 C 600 710 512 748 424 710 C 390 680 372 658 360 640 Z"
    )
    hole = ellipse_commands(512, 590, 120, 75)
    parts = [part("beard", 60, slot("beard.primary"), commands, clip={"id": "mouth_hole", "mode": "difference"})]
    return picture("beard_full", 2, parts, clips=[{"id": "mouth_hole", "commands": hole}]), commands


def mustache():
    # Sits on the upper lip. The mouth corners at x 416 and 608 stay outside it.
    commands = "M 440 548 C 478 522 502 546 512 572 C 522 546 546 522 584 548 C 548 578 512 592 476 578 Z"
    return picture("mustache_classic", 2, [part("mustache", 60, slot("beard.primary"), commands)]), commands


def stubble(silhouette):
    # Cheek and chin dots, kept off the mouth and inside the new jaw.
    centers = []
    for y, xs in (
        (640, (348, 676)),
        (700, (330, 410, 490, 570, 650, 710)),
        (770, (370, 450, 512, 574, 660)),
        (830, (430, 490, 544, 600)),
    ):
        span = x_range_at(silhouette, y)
        for x in xs:
            if span is None or x < span[0] + 28 or x > span[1] - 28:
                continue
            mouth = ((x - 512) / 130) ** 2 + ((y - 590) / 80) ** 2
            if mouth < 1:
                continue
            centers.append((x, y))
    if len(centers) < 12:
        raise SystemExit(f"stubble only placed {len(centers)} dots")
    commands = " ".join(ellipse_commands(x, y, 11, 11) for x, y in centers)
    return picture(
        "stubble",
        2,
        [part("stubble", 60, slot("beard.primary"), commands, opacity=0.4)],
    )


def main():
    silhouette = mapped_segments()
    chin = chin_bottom(silhouette)
    if not 908 <= chin <= 916:
        raise SystemExit(f"chin bottom {chin} is not about 912")

    base = base_picture(silhouette)
    shade = base["parts"][2]["commands"]
    require_inside("shade", shade, silhouette)
    shade_clearance = min(nearest_distance(p, silhouette) for p in sample(parse_commands(shade), 40))
    if shade_clearance < 48:
        raise SystemExit(f"shade comes within {shade_clearance:.1f} of the outline")

    happy = eyes_happy()["parts"][0]["commands"]
    brow = brows()["parts"][0]["commands"]
    neutral_mouth = mouth_neutral()["parts"][0]["commands"]
    smile = mouth_smile()["parts"][0]["commands"]
    frames = glasses()["parts"][0]["commands"]
    beard_pic, beard_commands = beard()
    mustache_pic, mustache_commands = mustache()
    for name, commands in (
        ("happy eyes", happy),
        ("brows", brow),
        ("mouth", neutral_mouth),
        ("smile", smile),
        ("glasses", frames),
        ("mustache", mustache_commands),
    ):
        require_inside(name, commands, silhouette)

    # The beard covers the chin tip, which narrows to a point, and may hang just below it.
    # Above that it has to stay on the jaw.
    beard_bad = []
    for point in sample(parse_commands(beard_commands), 24):
        if point[1] > chin - 36:
            continue
        span = x_range_at(silhouette, point[1])
        if span is None or point[0] < span[0] - 16 or point[0] > span[1] + 16:
            beard_bad.append(point)
    if beard_bad:
        where = ", ".join(f"({p[0]:.0f},{p[1]:.0f})" for p in beard_bad[:6])
        raise SystemExit(f"beard leaves the jaw: {where}")

    files = {
        "base_teardrop.json": base,
        "eyes_round_neutral.json": eyes_neutral(),
        "eyes_round_happy.json": happy and eyes_happy(),
        "brows_round_relaxed.json": brows(),
        "mouth_round_neutral.json": mouth_neutral(),
        "mouth_round_smile.json": mouth_smile(),
        "glasses_round_wire.json": glasses(),
        "hair_bob.json": hair_bob(),
        "hair_long_straight.json": hair_long(),
        "beard_full.json": beard_pic,
        "mustache_classic.json": mustache_pic,
        "stubble.json": stubble(silhouette),
    }
    for name, data in files.items():
        dump(PICTURES / name, data)
    print(f"wrote {len(files)} pictures; chin y {chin:.1f}")


if __name__ == "__main__":
    main()
