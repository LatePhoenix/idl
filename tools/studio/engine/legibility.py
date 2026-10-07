"""How much of the expression survives an item at 48 px (ART_STUDIO.md §5.5).

For each expression in EXPRESSIONS, render at 48 px in head framing: the bare head (S), the face
(A) and the face wearing the item (B). Feature pixels are where A differs from S inside a zone
(each eye, the mouth, the brows). A feature pixel is kept when B still matches A. Eyewear also
gets a contrast score, so a tinted lens that darkens the eye but keeps it readable passes.

This is stricter than the app's LegibilityTest, which counts dark pixels anywhere on the canvas.
"""

from __future__ import annotations

import numpy as np

from . import guides, render

SIZE = 48
EXPRESSIONS = ("neutral_face", "star_struck", "crying_face")
TOLERANCE = 60  # sum of |ΔR|+|ΔG|+|ΔB|
ERROR_BELOW = 0.6
WARN_BELOW = 0.85
BROW_WARN_BELOW = 0.5


def _box_px(box):
    ox, oy, fsize = render.FRAMING["head"]
    s = SIZE / fsize
    l, t, r, b = box
    return (max(0, int((l - ox) * s)), max(0, int((t - oy) * s)), min(SIZE, int(np.ceil((r - ox) * s))), min(SIZE, int(np.ceil((b - oy) * s))))


def _zones():
    g = guides.guides()
    ex, ey, ex2, ey2 = g["eye_zone"]["box"]
    return {
        "left eye": (ex, ey, 512, ey2),
        "right eye": (512, ey, ex2, ey2),
        "mouth": tuple(g["mouth_zone"]["box"]),
        "brows": tuple(g["brow_zone"]["box"]),
    }


def _px(assets):
    return render.render(assets, SIZE, framing="head", frame="none", wall="light").astype(np.int32)[:, :, :3]


def _lum(px):
    return px[..., 0] * 0.299 + px[..., 1] * 0.587 + px[..., 2] * 0.114


def measure(library, asset_id: str, *, eyewear: bool = False) -> dict:
    """Per zone, the worst score across EXPRESSIONS (1.0 = untouched)."""
    worst: dict[str, float] = {}
    detail = []
    zones = {name: _box_px(box) for name, box in _zones().items()}
    for expression in EXPRESSIONS:
        face_assets = library.look([], expression)
        bare_assets = [a for a in face_assets if a["category"] not in ("face_eye", "face_brow", "face_mouth", "expression_overlay")]
        S, A, B = _px(bare_assets), _px(face_assets), _px(library.look([asset_id], expression))
        for name, (l, t, r, b) in zones.items():
            s, a, w = S[t:b, l:r], A[t:b, l:r], B[t:b, l:r]
            feature = np.abs(a - s).sum(axis=2) > TOLERANCE
            if feature.sum() == 0:
                continue
            kept = float((np.abs(w - a).sum(axis=2) <= TOLERANCE)[feature].mean())
            score = kept
            if eyewear and (~feature).any():
                contrast_a = abs(_lum(a)[feature].mean() - _lum(a)[~feature].mean())
                contrast_w = abs(_lum(w)[feature].mean() - _lum(w)[~feature].mean())
                if contrast_a > 0:
                    score = max(score, min(1.0, contrast_w / contrast_a))
            worst[name] = min(worst.get(name, 1.0), score)
            detail.append({"expression": expression, "zone": name, "score": round(score, 3), "pixels": int(feature.sum())})
    return {"zones": {k: round(v, 3) for k, v in worst.items()}, "detail": detail}


def issues(result: dict, *, covers_eyes: bool = False) -> list[dict]:
    out = []
    for zone, score in result["zones"].items():
        if zone == "brows":
            if score < BROW_WARN_BELOW:
                out.append({"level": "warn", "rule": "legibility", "message": f"brows: only {score:.0%} visible at 48 px (raised brows matter for surprise and worry)"})
            continue
        if covers_eyes and "eye" in zone:
            continue
        if score < ERROR_BELOW:
            out.append({"level": "error", "rule": "legibility", "message": f"{zone}: only {score:.0%} visible at 48 px (needs {ERROR_BELOW:.0%})"})
        elif score < WARN_BELOW:
            out.append({"level": "warn", "rule": "legibility", "message": f"{zone}: {score:.0%} visible at 48 px"})
    return out
