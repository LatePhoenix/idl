"""Generates the iDL launcher icon (R1) and wordmark (W1) from one geometry source.

Outputs:
  app/src/main/res/drawable/ic_launcher_foreground.xml   full-color foreground layer
  app/src/main/res/drawable/ic_launcher_monochrome.xml   themed-icon layer (features cut out)
  docs/art/brand/launcher-icon.svg                       108x108 master with background
  docs/art/brand/wordmark-light.svg / wordmark-dark.svg  W1 wordmark
  app/src/main/res/drawable/splash_wordmark.xml          W1 as the splash-screen icon
  app/src/main/res/drawable/wordmark.xml                 W1 cropped for use inside screens

Run from the repo root: python docs/art/brand/gen_brand.py
All transforms are baked into the path data, so the drawables need no groups.
"""
import json
import math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]

CORAL = "#FF8E6E"
PLUM = "#3A2247"
PLUM_DEEP = "#2A1430"
CREAM = "#FFF1E1"
CREAM_LETTER = "#FFE9D2"
BLUSH = "#EE6355"

# Teardrop face, narrow end down (D-42, D-45). One source: config/teardrop_silhouette.json.
def load_teardrop():
    data = json.loads((ROOT / "config" / "teardrop_silhouette.json").read_text(encoding="utf-8"))
    cmds = []
    for seg in data["path"]:
        pts = tuple(tuple(p) for p in seg.get("points", []))
        cmds.append((seg["op"], *pts))
    return cmds


TEARDROP = load_teardrop()
WINK_ARC = ((38, 50), (44, 44), (50, 50))      # closed left eye, quadratic
SMILE_WINK = ((45, 60), (55, 70), (64, 59))    # lopsided smile, quadratic
SMILE = ((45, 60), (54, 69), (63, 60))
FEATURE_STROKE = 5.5
KAPPA = 0.5522847498


# ---- affine helpers -------------------------------------------------------

def mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def translate(x, y):
    return [[1, 0, x], [0, 1, y], [0, 0, 1]]


def scale(s):
    return [[s, 0, 0], [0, s, 0], [0, 0, 1]]


def rotate(deg, cx, cy):
    r = math.radians(deg)
    c, s = math.cos(r), math.sin(r)
    return mul(translate(cx, cy), mul([[c, -s, 0], [s, c, 0], [0, 0, 1]], translate(-cx, -cy)))


def chain(*ms):
    out = translate(0, 0)
    for m in ms:
        out = mul(out, m)
    return out


def apply(m, p):
    x, y = p
    return (m[0][0] * x + m[0][1] * y + m[0][2], m[1][0] * x + m[1][1] * y + m[1][2])


def scale_of(m):
    return math.hypot(m[0][0], m[1][0])


def fmt(v):
    s = f"{v:.2f}".rstrip("0").rstrip(".")
    return "0" if s == "-0" else s


def pt(p):
    return f"{fmt(p[0])},{fmt(p[1])}"


# ---- shapes as path data ---------------------------------------------------

def path_data(cmds, m):
    out = []
    for c in cmds:
        op, pts = c[0], c[1:]
        out.append(op + " ".join(pt(apply(m, p)) for p in pts) if pts else "Z")
    return " ".join(out)


def ellipse_cmds(cx, cy, rx, ry):
    kx, ky = rx * KAPPA, ry * KAPPA
    return [
        ("M", (cx + rx, cy)),
        ("C", (cx + rx, cy + ky), (cx + kx, cy + ry), (cx, cy + ry)),
        ("C", (cx - kx, cy + ry), (cx - rx, cy + ky), (cx - rx, cy)),
        ("C", (cx - rx, cy - ky), (cx - kx, cy - ry), (cx, cy - ry)),
        ("C", (cx + kx, cy - ry), (cx + rx, cy - ky), (cx + rx, cy)),
        ("Z",),
    ]


def stadium_cmds(x, y, w, h):
    """Vertical rounded rect whose corner radius is half its width."""
    r = w / 2
    k = r * KAPPA
    cx, top, bot = x + r, y + r, y + h - r
    return [
        ("M", (x, top)),
        ("C", (x, top - k), (cx - k, y), (cx, y)),
        ("C", (cx + k, y), (x + w, top - k), (x + w, top)),
        ("L", (x + w, bot)),
        ("C", (x + w, bot + k), (cx + k, y + h), (cx, y + h)),
        ("C", (cx - k, y + h), (x, bot + k), (x, bot)),
        ("Z",),
    ]


def quad_cmds(q):
    return [("M", q[0]), ("Q", q[1], q[2])]


def stroke_outline(q, width):
    """Outline of a round-capped stroke along a quadratic curve.

    Each side is the curve offset by half the width, with its control point placed where the
    offset control-polygon legs meet (Tiller-Hanson). Exact enough for these gentle curves.
    """
    p0, p1, p2 = q
    h = width / 2

    def unit(a, b):
        d = math.hypot(b[0] - a[0], b[1] - a[1])
        return ((b[0] - a[0]) / d, (b[1] - a[1]) / d)

    def add(p, v, k):
        return (p[0] + v[0] * k, p[1] + v[1] * k)

    t0, t2 = unit(p0, p1), unit(p1, p2)
    n0, n2 = (-t0[1], t0[0]), (-t2[1], t2[0])

    def control(side):
        a, b = add(p0, n0, side * h), add(p2, n2, side * h)
        # Intersect a + s*t0 with b - r*t2.
        det = t0[0] * -t2[1] - t0[1] * -t2[0]
        s_ = ((b[0] - a[0]) * -t2[1] - (b[1] - a[1]) * -t2[0]) / det
        return add(a, t0, s_)

    k = h * KAPPA
    l0, l2, r0, r2 = add(p0, n0, h), add(p2, n2, h), add(p0, n0, -h), add(p2, n2, -h)
    tip2, tip0 = add(p2, t2, h), add(p0, t0, -h)
    return [
        ("M", l0),
        ("Q", control(1), l2),
        ("C", add(l2, t2, k), add(tip2, n2, k), tip2),
        ("C", add(tip2, n2, -k), add(r2, t2, k), r2),
        ("Q", control(-1), r0),
        ("C", add(r0, t0, -k), add(tip0, n0, -k), tip0),
        ("C", add(tip0, n0, k), add(l0, t0, -k), l0),
        ("Z",),
    ]


# ---- R1 launcher icon -------------------------------------------------------

ICON_TILT = rotate(-10, 54, 56)
FACE = chain(ICON_TILT, translate(55, 41.5), scale(0.66), translate(-54, -55.5))
STEM = stadium_cmds(47.5, 64, 15, 21)


def vector(paths):
    body = "\n".join(paths)
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated by docs/art/brand/gen_brand.py. Edit the script, not this file. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="108dp" android:height="108dp"\n'
        '    android:viewportWidth="108" android:viewportHeight="108">\n'
        f"{body}\n"
        "</vector>\n"
    )


def argb(hex_rgb):
    return "#FF" + hex_rgb[1:]


def fill(d, color, even_odd=False):
    rule = '\n        android:fillType="evenOdd"' if even_odd else ""
    return f'    <path\n        android:fillColor="{argb(color)}"{rule}\n        android:pathData="{d}" />'


def stroke(d, color, width):
    return (
        f'    <path\n        android:strokeColor="{argb(color)}"\n        android:strokeWidth="{fmt(width)}"\n'
        f'        android:strokeLineCap="round"\n        android:pathData="{d}" />'
    )


def foreground():
    w = FEATURE_STROKE * scale_of(FACE)
    return vector([
        fill(path_data(STEM, ICON_TILT), PLUM),
        fill(path_data(TEARDROP, FACE), CORAL),
        fill(path_data(ellipse_cmds(38, 61, 5, 3), FACE), BLUSH),
        fill(path_data(ellipse_cmds(71, 60, 4.5, 2.8), FACE), BLUSH),
        stroke(path_data(quad_cmds(WINK_ARC), FACE), PLUM, w),
        fill(path_data(ellipse_cmds(64, 48, 6, 6), FACE), PLUM),
        stroke(path_data(quad_cmds(SMILE_WINK), FACE), PLUM, w),
    ])


def monochrome():
    # Themed icons only use alpha, so the features must be holes in the face.
    face = " ".join([
        path_data(TEARDROP, FACE),
        path_data(stroke_outline(WINK_ARC, FEATURE_STROKE), FACE),
        path_data(ellipse_cmds(64, 48, 6, 6), FACE),
        path_data(stroke_outline(SMILE_WINK, FEATURE_STROKE), FACE),
    ])
    return vector([
        fill(path_data(STEM, ICON_TILT), "#FFFFFF"),
        fill(face, "#FFFFFF", even_odd=True),
    ])


def svg_face(expr):
    blush = (f'<ellipse cx="38" cy="61" rx="5" ry="3" fill="{BLUSH}"/>'
             f'<ellipse cx="71" cy="60" rx="4.5" ry="2.8" fill="{BLUSH}"/>')
    line = f'fill="none" stroke="{{c}}" stroke-width="{FEATURE_STROKE}" stroke-linecap="round"'
    if expr == "wink":
        feats = (f'<path d="M38 50 Q44 44 50 50" {line}/><circle cx="64" cy="48" r="6" fill="{{c}}"/>'
                 f'<path d="M45 60 Q55 70 64 59" {line}/>')
    else:
        feats = (f'<circle cx="44" cy="48" r="6" fill="{{c}}"/><circle cx="64" cy="48" r="6" fill="{{c}}"/>'
                 f'<path d="M45 60 Q54 69 63 60" {line}/>')
    td = "M54 26 C70 26 81 38 81 53 C81 66 71 74 62 81 Q54 87 46 81 C37 74 27 66 27 53 C27 38 38 26 54 26 Z"
    return f'<path d="{td}" fill="{CORAL}"/>' + blush + feats


def icon_svg():
    face = svg_face("wink").replace("{c}", PLUM)
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="1080" height="1080">\n'
        "<!-- iDL launcher icon R1. Adaptive-icon canvas: launchers show the centre 72x72. -->\n"
        f'<rect width="108" height="108" fill="{CREAM}"/>\n'
        '<g transform="rotate(-10 54 56)">'
        f'<rect x="47.5" y="64" width="15" height="21" rx="7.5" fill="{PLUM}"/>'
        f'<g transform="translate(55 41.5) scale(0.66) translate(-54 -55.5)">{face}</g></g>\n'
        "</svg>\n"
    )


def wordmark_svg(letters, background):
    face = svg_face("wink").replace("{c}", PLUM if letters == PLUM else PLUM_DEEP)
    bg = f'<rect x="-12" y="-6" width="148" height="86" fill="{background}"/>\n' if background else ""
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="-12 -6 148 86" width="592" height="344">\n'
        "<!-- iDL wordmark W1: leaning i with the teardrop face as its dot. -->\n"
        f"{bg}"
        f'<g transform="rotate(-10 17 55)"><rect x="10" y="42" width="14" height="28" rx="7" fill="{letters}"/>'
        f'<g transform="translate(17.5 21) scale(0.62) translate(-54 -55.5)">{face}</g></g>\n'
        f'<path fill-rule="evenodd" fill="{letters}" d="M36 33 Q36 26 43 26 H56 A24 22 0 0 1 56 70 H43 '
        'Q36 70 36 63 Z M50 39 H55 A11 9 0 0 1 55 57 H50 Z"/>\n'
        f'<path d="M97 33 V63 H120" fill="none" stroke="{letters}" stroke-width="14" '
        'stroke-linecap="round" stroke-linejoin="round"/>\n'
        "</svg>\n"
    )


# ---- W1 wordmark as the splash-screen icon -----------------------------------

# Android 12+ splash icon without a background: 288 dp canvas, content inside a 192 dp circle.
# The wordmark's ink spans about x -5..127 and y 3..70; scaled 1.257 it is 166 x 84 dp.
SPLASH_PLACE = chain(translate(144, 144), scale(1.257), translate(-61, -36.5))
SPLASH_TILT = chain(SPLASH_PLACE, rotate(-10, 17, 55))
SPLASH_FACE = chain(SPLASH_TILT, translate(17.5, 21), scale(0.62), translate(-54, -55.5))
D_OUTER = [
    ("M", (36, 33)), ("Q", (36, 26), (43, 26)), ("L", (56, 26)),
    ("C", (56 + 24 * KAPPA, 26), (80, 48 - 22 * KAPPA), (80, 48)),
    ("C", (80, 48 + 22 * KAPPA), (56 + 24 * KAPPA, 70), (56, 70)),
    ("L", (43, 70)), ("Q", (36, 70), (36, 63)), ("Z",),
]
D_COUNTER = [
    ("M", (50, 39)), ("L", (55, 39)),
    ("C", (55 + 11 * KAPPA, 39), (66, 48 - 9 * KAPPA), (66, 48)),
    ("C", (66, 48 + 9 * KAPPA), (55 + 11 * KAPPA, 57), (55, 57)),
    ("L", (50, 57)), ("Z",),
]
L_STROKE = [("M", (97, 33)), ("L", (97, 63)), ("L", (120, 63))]


# Inline wordmark (onboarding): the ink box plus a 2-unit margin, 136 x 71 units.
INLINE_PLACE = translate(7, -1)


def wordmark_vector(place, size_dp, viewport, note):
    """W1 as a vector drawable placed by [place] in [viewport].

    Colors are resources, so values-night switches the letters and face lines for dark mode.
    """
    tilt = chain(place, rotate(-10, 17, 55))
    face = chain(tilt, translate(17.5, 21), scale(0.62), translate(-54, -55.5))
    feature = FEATURE_STROKE * scale_of(face)
    letters, line = "@color/idl_wordmark_letters", "@color/idl_wordmark_line"

    def shape(d, color, even_odd=False):
        rule = '\n        android:fillType="evenOdd"' if even_odd else ""
        return f'    <path\n        android:fillColor="{color}"{rule}\n        android:pathData="{d}" />'

    def line_path(d, color, width):
        return (
            f'    <path\n        android:strokeColor="{color}"\n        android:strokeWidth="{fmt(width)}"\n'
            f'        android:strokeLineCap="round"\n        android:strokeLineJoin="round"\n'
            f'        android:pathData="{d}" />'
        )

    paths = [
        shape(path_data(stadium_cmds(10, 42, 14, 28), tilt), letters),
        shape(path_data(TEARDROP, face), argb(CORAL)),
        shape(path_data(ellipse_cmds(38, 61, 5, 3), face), argb(BLUSH)),
        shape(path_data(ellipse_cmds(71, 60, 4.5, 2.8), face), argb(BLUSH)),
        line_path(path_data(quad_cmds(WINK_ARC), face), line, feature),
        shape(path_data(ellipse_cmds(64, 48, 6, 6), face), line),
        line_path(path_data(quad_cmds(SMILE_WINK), face), line, feature),
        shape(path_data(D_OUTER, place) + " " + path_data(D_COUNTER, place), letters, even_odd=True),
        line_path(path_data(L_STROKE, place), letters, 14 * scale_of(place)),
    ]
    return (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<!-- Generated by docs/art/brand/gen_brand.py. Edit the script, not this file. -->\n"
        f"<!-- {note} -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        f'    android:width="{fmt(size_dp[0])}dp" android:height="{fmt(size_dp[1])}dp"\n'
        f'    android:viewportWidth="{fmt(viewport[0])}" android:viewportHeight="{fmt(viewport[1])}">\n'
        + "\n".join(paths) + "\n</vector>\n"
    )


def splash_vector():
    # Lint's VectorRaster check flags intrinsic sizes over 200 dp. The splash view and
    # splash_background.xml both size the drawable to 288 dp themselves.
    return wordmark_vector(SPLASH_PLACE, (192, 192), (288, 288),
                           "W1 on the 288-unit Android 12+ splash icon canvas, inside the 192-unit safe circle.")


def inline_vector():
    return wordmark_vector(INLINE_PLACE, (136, 71), (136, 71),
                           "W1 cropped to its ink, for use inside screens such as onboarding.")


def main():
    res = ROOT / "app/src/main/res/drawable"
    brand = ROOT / "docs/art/brand"
    (res / "ic_launcher_foreground.xml").write_text(foreground(), encoding="utf-8", newline="\n")
    (res / "ic_launcher_monochrome.xml").write_text(monochrome(), encoding="utf-8", newline="\n")
    (res / "splash_wordmark.xml").write_text(splash_vector(), encoding="utf-8", newline="\n")
    (res / "wordmark.xml").write_text(inline_vector(), encoding="utf-8", newline="\n")
    (brand / "launcher-icon.svg").write_text(icon_svg(), encoding="utf-8", newline="\n")
    (brand / "wordmark-light.svg").write_text(wordmark_svg(PLUM, None), encoding="utf-8", newline="\n")
    (brand / "wordmark-dark.svg").write_text(wordmark_svg(CREAM_LETTER, PLUM), encoding="utf-8", newline="\n")
    # Probe points for LauncherIconSnapshotTest, in 108-unit canvas space.
    for name, p in [("forehead", apply(FACE, (54, 36))), ("open eye", apply(FACE, (64, 48))),
                    ("stem", apply(ICON_TILT, (55, 76)))]:
        print(f"{name}: {fmt(p[0])}, {fmt(p[1])}")
    # Probe points for SplashWordmarkTest, in 288-unit splash canvas space.
    for name, p in [("splash D stem", apply(SPLASH_PLACE, (40, 48))), ("splash forehead", apply(SPLASH_FACE, (54, 36))),
                    ("splash open eye", apply(SPLASH_FACE, (64, 48)))]:
        print(f"{name}: {fmt(p[0])}, {fmt(p[1])}")


if __name__ == "__main__":
    main()
