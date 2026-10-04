"""Composite v2 expression sheets from faceless bases.

Vector bases keep the generated silhouette. Face glyphs are drawn here because
cropping the generated kit was unreliable. Pixel bases are authored 24x24 grids.
"""

from __future__ import annotations

import math
import os
from collections import deque

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.abspath(__file__))
BASES = os.path.join(ROOT, "bases")
PARTS = os.path.join(ROOT, "parts")
SHEETS = os.path.join(ROOT, "sheets")
SIZE = os.path.join(ROOT, "size-check")
GEN = r"C:\Users\mhbro\.cursor\projects\z-Singularity-idl\assets"

INK = (28, 32, 38, 255)
CREAM = (247, 241, 228, 255)
PINK = (255, 122, 154, 255)
BLUSH = (255, 150, 170, 255)
TEAR = (90, 176, 235, 255)
WHITE = (255, 255, 255, 255)
DARK_BG = (15, 23, 34, 255)

# eye, mouth, brows, overlays
EXPRS = [
    ("neutral", "oval", "line", None, []),
    ("smile", "oval", "smile", None, []),
    ("grin", "oval", "grin", None, []),
    ("laugh", "arc", "grin", None, []),
    ("heart", "heart", "smile", None, ["blush"]),
    ("wink", "wink", "tongue", None, []),
    ("surprised", "wide", "o", None, []),
    ("sad", "dot", "frown", None, ["tear"]),
    ("cry", "dot", "frown", None, ["streams"]),
    ("angry", "dot", "frown", "angry", ["steam"]),
    ("worried", "oval", "frown", "worried", ["sweat"]),
    ("sleepy", "sleepy", "line", None, ["z"]),
    ("focused", "focused", "line", "determined", []),
    ("smug", "smug", "smirk", None, []),
    ("embarrassed", "oval", "smile", None, ["bigblush"]),
    ("dizzy", "spiral", "wavy", None, []),
]

# Face boxes (x0, y0, x1, y1) on the 1024 source. Critter A skips the nose.
ANCHORS = {
    "blob-a": (290, 280, 750, 680),
    "blob-b": (270, 200, 760, 620),
    "bot-a": (340, 275, 685, 530),
    "bot-b": (280, 175, 745, 470),
    "ghost-a": (330, 250, 690, 560),
    "ghost-b": (330, 200, 690, 530),
    "critter-a": (300, 220, 720, 700),
    "critter-b": (280, 210, 740, 500),
    "orb-a": (320, 310, 700, 700),
    "orb-b": (300, 180, 720, 520),
}

VECTOR_SRC = {
    "blob-a": "blob-a.jpg",
    "blob-b": "blob-b.jpg",
    "bot-a": "bot-a.jpg",
    "bot-b": "bot-b.jpg",
    "ghost-a": "ghost-a.jpg",
    "ghost-b": "ghost-b-retry.jpg",
    "critter-a": "critter-a.jpg",
    "critter-b": "critter-b.jpg",
    "orb-a": "orb-a.jpg",
    "orb-b": "orb-b.jpg",
}


def luminance(rgb):
    r, g, b = rgb[:3]
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def sample_bg(im, box):
    x0, y0, x1, y1 = box
    px = im.load()
    cols = []
    for y in range(y0 + 4, y1 - 4, 12):
        for x in range(x0 + 4, x1 - 4, 12):
            cols.append(px[x, y])
    cols.sort(key=luminance)
    return cols[len(cols) // 2]


def key_exterior_white(im, thresh=242):
    im = im.convert("RGBA")
    w, h = im.size
    px = im.load()
    seen = bytearray(w * h)
    q = deque()
    for seed in ((0, 0), (w - 1, 0), (0, h - 1), (w - 1, h - 1)):
        q.append(seed)
    while q:
        x, y = q.popleft()
        i = y * w + x
        if seen[i]:
            continue
        seen[i] = 1
        r, g, b, a = px[x, y]
        if r >= thresh and g >= thresh and b >= thresh:
            px[x, y] = (r, g, b, 0)
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and not seen[ny * w + nx]:
                    q.append((nx, ny))
    return im


def shrink(box, pad):
    return (box[0] + pad, box[1] + pad, box[2] - pad, box[3] - pad)


def heart(draw, box, fill):
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    draw.ellipse((x0, y0, x0 + w * 0.68, y0 + h * 0.68), fill=fill)
    draw.ellipse((x0 + w * 0.32, y0, x1, y0 + h * 0.68), fill=fill)
    draw.polygon(
        [(x0 + w * 0.06, y0 + h * 0.40), (x1 - w * 0.06, y0 + h * 0.40), ((x0 + x1) / 2, y1)],
        fill=fill,
    )


def spiral(draw, box, fill, lw):
    cx = (box[0] + box[2]) / 2
    cy = (box[1] + box[3]) / 2
    rad = min(box[2] - box[0], box[3] - box[1]) * 0.46
    pts = []
    for i in range(32):
        a = i / 31 * 3.4 * math.pi
        r = rad * (i / 31)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    draw.line(pts, fill=fill, width=lw, joint="curve")


def draw_eye(draw, box, kind, fill, lw):
    if kind == "oval":
        draw.ellipse(box, outline=fill, width=lw)
        # highlight
        x0, y0, x1, y1 = box
        hw, hh = (x1 - x0) * 0.22, (y1 - y0) * 0.22
        draw.ellipse((x0 + (x1 - x0) * 0.22, y0 + (y1 - y0) * 0.18, x0 + (x1 - x0) * 0.22 + hw, y0 + (y1 - y0) * 0.18 + hh), fill=WHITE)
    elif kind == "dot":
        draw.ellipse(shrink(box, (box[2] - box[0]) * 0.18), fill=fill)
    elif kind == "arc":
        draw.arc(box, 200, 340, fill=fill, width=lw)
    elif kind == "wide":
        draw.ellipse(box, outline=fill, width=lw)
        x0, y0, x1, y1 = box
        draw.ellipse(shrink(box, (x1 - x0) * 0.28), fill=fill)
    elif kind == "heart":
        heart(draw, box, fill)
    elif kind == "sleepy":
        y = (box[1] + box[3]) / 2
        draw.line([(box[0], y), (box[2], y)], fill=fill, width=lw)
    elif kind == "focused":
        y = (box[1] + box[3]) / 2
        draw.line([(box[0], y), (box[2], y)], fill=fill, width=max(lw - 2, 4))
        draw.ellipse(shrink(box, (box[2] - box[0]) * 0.22), outline=fill, width=max(lw // 2, 4))
    elif kind == "spiral":
        spiral(draw, box, fill, max(lw // 2, 4))
    elif kind == "wink-open":
        draw_eye(draw, box, "oval", fill, lw)
    elif kind == "wink-shut":
        draw.arc(box, 200, 340, fill=fill, width=lw)


def draw_brows(draw, box, kind, fill, lw):
    x0, y0, x1, y1 = box
    mid = (x0 + x1) / 2
    gap = (x1 - x0) * 0.08
    y_hi = y0
    y_lo = y0 + (y1 - y0) * 0.55
    if kind == "angry":
        draw.line([(x0, y_hi), (mid - gap, y_lo)], fill=fill, width=lw)
        draw.line([(mid + gap, y_lo), (x1, y_hi)], fill=fill, width=lw)
    elif kind == "worried":
        draw.line([(x0, y_lo), (mid - gap, y_hi)], fill=fill, width=lw)
        draw.line([(mid + gap, y_hi), (x1, y_lo)], fill=fill, width=lw)
    elif kind == "determined":
        y = y0 + (y1 - y0) * 0.35
        drop = (y1 - y0) * 0.25
        draw.line([(x0, y), (mid - gap, y + drop)], fill=fill, width=lw)
        draw.line([(mid + gap, y + drop), (x1, y)], fill=fill, width=lw)


def draw_mouth(draw, box, kind, fill, lw, pink):
    if kind == "line":
        y = (box[1] + box[3]) / 2
        draw.line([(box[0], y), (box[2], y)], fill=fill, width=lw)
    elif kind == "smile":
        draw.arc(box, 10, 170, fill=fill, width=lw)
    elif kind == "grin":
        draw.chord(box, 5, 175, fill=fill)
        inner = shrink(box, lw * 0.85)
        if inner[2] > inner[0] and inner[3] > inner[1]:
            draw.chord(inner, 8, 172, fill=pink)
    elif kind == "frown":
        draw.arc(box, 190, 350, fill=fill, width=lw)
    elif kind == "o":
        cx = (box[0] + box[2]) / 2
        cy = (box[1] + box[3]) / 2
        r = min(box[2] - box[0], box[3] - box[1]) * 0.28
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), outline=fill, width=lw)
    elif kind == "tongue":
        draw.arc(box, 10, 170, fill=fill, width=lw)
        x0, y0, x1, y1 = box
        tw = (x1 - x0) * 0.28
        cx = (x0 + x1) / 2
        draw.ellipse((cx - tw, y0 + (y1 - y0) * 0.35, cx + tw, y1 + (y1 - y0) * 0.15), fill=pink, outline=fill, width=max(lw // 3, 3))
    elif kind == "smirk":
        x0, y0, x1, y1 = box
        draw.arc((x0, y0, x0 + (x1 - x0) * 0.72, y1), 10, 160, fill=fill, width=lw)
    elif kind == "wavy":
        x0, y0, x1, y1 = box
        cy = (y0 + y1) / 2
        amp = (y1 - y0) * 0.28
        pts = []
        for i in range(13):
            t = i / 12
            pts.append((x0 + t * (x1 - x0), cy + math.sin(t * math.pi * 2) * amp))
        draw.line(pts, fill=fill, width=lw, joint="curve")


def draw_overlay(draw, face, kind, fill, lw, dark_face):
    x0, y0, x1, y1 = face
    w, h = x1 - x0, y1 - y0
    steam_fill = CREAM if dark_face else WHITE
    if kind == "blush":
        rw, rh = w * 0.12, h * 0.08
        cy = y0 + h * 0.62
        draw.ellipse((x0 + w * 0.08, cy, x0 + w * 0.08 + rw, cy + rh), fill=BLUSH)
        draw.ellipse((x1 - w * 0.08 - rw, cy, x1 - w * 0.08, cy + rh), fill=BLUSH)
    elif kind == "bigblush":
        rw, rh = w * 0.22, h * 0.14
        cy = y0 + h * 0.58
        draw.ellipse((x0 + w * 0.02, cy, x0 + w * 0.02 + rw, cy + rh), fill=BLUSH)
        draw.ellipse((x1 - w * 0.02 - rw, cy, x1 - w * 0.02, cy + rh), fill=BLUSH)
    elif kind == "tear":
        tear = CREAM if dark_face else TEAR
        tx = x0 + w * 0.22
        ty = y0 + h * 0.42
        draw.polygon([(tx, ty), (tx - w * 0.045, ty + h * 0.12), (tx + w * 0.045, ty + h * 0.12)], fill=tear)
        draw.ellipse((tx - w * 0.05, ty + h * 0.08, tx + w * 0.05, ty + h * 0.20), fill=tear)
    elif kind == "streams":
        tear = CREAM if dark_face else TEAR
        for side in (0.22, 0.78):
            tx = x0 + w * side
            draw.line([(tx, y0 + h * 0.40), (tx - w * 0.02, y0 + h * 0.78)], fill=tear, width=lw)
    elif kind == "sweat":
        tear = CREAM if dark_face else TEAR
        tx = x1 - w * 0.12
        ty = y0 + h * 0.18
        draw.polygon([(tx, ty), (tx - w * 0.04, ty + h * 0.12), (tx + w * 0.04, ty + h * 0.12)], fill=tear)
        draw.ellipse((tx - w * 0.045, ty + h * 0.08, tx + w * 0.045, ty + h * 0.18), fill=tear, outline=fill, width=max(lw // 4, 2))
    elif kind == "steam":
        cx, cy = x1 - w * 0.08, y0 + h * 0.12
        r = w * 0.055
        for dx, dy in ((-r, r * 0.2), (r * 0.3, -r * 0.1), (0, r * 0.7)):
            draw.ellipse((cx + dx - r, cy + dy - r, cx + dx + r, cy + dy + r), fill=steam_fill, outline=fill, width=max(lw // 4, 2))
    elif kind == "z":
        zx0, zy0 = x1 - w * 0.22, y0 + h * 0.02
        zx1, zy1 = x1 - w * 0.02, y0 + h * 0.22
        draw.line([(zx0, zy0), (zx1, zy0), (zx0, zy1), (zx1, zy1)], fill=fill, width=lw)


def paint_expression(im, face, expr, critter_gap=False):
    eye_kind, mouth_kind, brows, overlays = expr
    bg = sample_bg(im, face)
    dark_face = luminance(bg) < 120
    fill = CREAM if dark_face else INK
    pink = PINK
    x0, y0, x1, y1 = face
    w, h = x1 - x0, y1 - y0
    lw = max(8, int(round(2.6 * im.size[0] / 48)))
    draw = ImageDraw.Draw(im)
    if critter_gap:
        # Eyes stay on the orange forehead. Mouth sits in the cream muzzle, above the neck.
        eye_box = (x0 + w * 0.08, y0 + h * 0.04, x1 - w * 0.08, y0 + h * 0.34)
        mouth_box = (x0 + w * 0.24, y0 + h * 0.58, x1 - w * 0.24, y0 + h * 0.78)
        brow_box = (x0 + w * 0.10, y0 + h * 0.02, x1 - w * 0.10, y0 + h * 0.16)
    else:
        eye_box = (x0, y0, x1, y0 + h * 0.52)
        mouth_box = (x0 + w * 0.16, y0 + h * 0.60, x1 - w * 0.16, y0 + h * 0.95)
        brow_box = (x0 + w * 0.06, y0, x1 - w * 0.06, y0 + h * 0.22)
    ew = eye_box[2] - eye_box[0]
    eh = eye_box[3] - eye_box[1]
    gap = ew * 0.10
    eye_w = (ew - gap) * 0.46
    left = (eye_box[0], eye_box[1] + eh * 0.18, eye_box[0] + eye_w, eye_box[3] - eh * 0.05)
    right = (eye_box[2] - eye_w, eye_box[1] + eh * 0.18, eye_box[2], eye_box[3] - eh * 0.05)
    if brows:
        draw_brows(draw, brow_box, brows, fill, lw)
    if eye_kind == "wink":
        draw_eye(draw, left, "wink-shut", fill, lw)
        draw_eye(draw, right, "oval", fill, lw)
    elif eye_kind == "smug":
        smug_l = (left[0], left[1] + eh * 0.15, left[2], left[3])
        draw_eye(draw, smug_l, "arc", fill, lw)
        draw_eye(draw, right, "oval", fill, lw)
    else:
        draw_eye(draw, left, eye_kind, fill, lw)
        draw_eye(draw, right, eye_kind, fill, lw)
    draw_mouth(draw, mouth_box, mouth_kind, fill, lw, pink)
    for extra in overlays:
        draw_overlay(draw, face, extra, fill, lw, dark_face)
    return dark_face


def sheet_from_cells(cells, bg, gap=16):
    cw, ch = cells[0].size
    sheet = Image.new("RGBA", (cw * 4 + gap * 5, ch * 4 + gap * 5), bg)
    for i, cell in enumerate(cells):
        r, c = divmod(i, 4)
        # row-major: index 0 is row 0 col 0. divmod(i, 4) gives (col, row) if we want row-major with 4 cols.
        row, col = divmod(i, 4)
        sheet.paste(cell, (gap + col * (cw + gap), gap + row * (ch + gap)), cell if cell.mode == "RGBA" else None)
    return sheet


def load_vector(name):
    im = Image.open(os.path.join(GEN, VECTOR_SRC[name])).convert("RGBA")
    im.save(os.path.join(BASES, f"{name}.png"))
    return im


def build_vector(name):
    base = load_vector(name)
    keyed = key_exterior_white(base)
    face = ANCHORS[name]
    cells = []
    for spec in EXPRS:
        cell = keyed.copy()
        paint_expression(cell, face, spec[1:], critter_gap=(name == "critter-a"))
        cells.append(cell)
    gap = 28
    # Sheets keep an opaque white page; cells themselves are transparent outside the silhouette.
    sheet_cells = []
    for cell in cells:
        page = Image.new("RGBA", cell.size, WHITE)
        page.paste(cell, (0, 0), cell)
        sheet_cells.append(page)
    sheet = sheet_from_cells(sheet_cells, (255, 255, 255, 255), gap=gap)
    sheet.convert("RGB").save(os.path.join(SHEETS, f"{name}.png"))
    small = []
    for cell in cells:
        small.append(cell.resize((48, 48), Image.Resampling.LANCZOS))
    white = sheet_from_cells(small, (255, 255, 255, 255), gap=4)
    dark = sheet_from_cells(small, DARK_BG, gap=4)
    white.convert("RGB").save(os.path.join(SIZE, f"{name}-white.png"))
    dark.convert("RGB").save(os.path.join(SIZE, f"{name}-dark.png"))
    # exact-pixel preview of the 48px sheet
    white.resize((white.size[0] * 4, white.size[1] * 4), Image.Resampling.NEAREST).convert("RGB").save(
        os.path.join(SIZE, f"{name}-white-x4.png")
    )
    dark.resize((dark.size[0] * 4, dark.size[1] * 4), Image.Resampling.NEAREST).convert("RGB").save(
        os.path.join(SIZE, f"{name}-dark-x4.png")
    )
    bg = sample_bg(base, face)
    print(f"{name} face={face} bg={bg} lum={luminance(bg):.0f}")


# --- pixel grids -----------------------------------------------------------

NAVY = (10, 31, 68, 255)
CYAN = (0, 214, 232, 255)
TEAL = (0, 140, 156, 255)
GB_OUT = (15, 56, 15, 255)
GB_SHADE = (48, 98, 48, 255)
GB_BODY = (139, 172, 15, 255)
GB_HI = (186, 214, 86, 255)


def empty_grid():
    return [[None for _ in range(24)] for _ in range(24)]


def outline_grid(grid, outline, protect=None):
    filled = [[grid[y][x] is not None for x in range(24)] for y in range(24)]
    out = [row[:] for row in grid]
    x0 = y0 = x1 = y1 = None
    if protect:
        x0, y0, x1, y1 = protect
    for y in range(24):
        for x in range(24):
            if not filled[y][x]:
                continue
            if protect and x0 <= x < x1 and y0 <= y < y1:
                continue
            edge = False
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if not (0 <= nx < 24 and 0 <= ny < 24 and filled[ny][nx]):
                    edge = True
            if edge:
                out[y][x] = outline
    return out


def grid_image(grid):
    im = Image.new("RGBA", (24, 24), (0, 0, 0, 0))
    px = im.load()
    for y in range(24):
        for x in range(24):
            if grid[y][x] is not None:
                px[x, y] = grid[y][x]
    return im


def make_pixel_a():
    g = empty_grid()
    cx, cy, r = 11.5, 10.0, 8.15
    for y in range(24):
        for x in range(24):
            if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                g[y][x] = CYAN
    for x in range(6, 10):
        g[17][x] = CYAN
        g[18][x] = CYAN
    for x in range(14, 18):
        g[17][x] = CYAN
        g[18][x] = CYAN
    for y in range(15, 19):
        for x in range(24):
            if g[y][x] == CYAN:
                g[y][x] = TEAL
    # flat face block, at least 12x8, pure cyan
    for y in range(6, 14):
        for x in range(6, 18):
            g[y][x] = CYAN
    g[4][7] = (255, 255, 255, 255)
    g[4][8] = (255, 255, 255, 255)
    g[5][7] = (255, 255, 255, 255)
    return outline_grid(g, NAVY)


def make_pixel_b():
    g = empty_grid()
    cx, cy, rx, ry = 11.5, 14.2, 8.2, 6.7
    for y in range(24):
        for x in range(24):
            if ((x + 0.5 - cx) / rx) ** 2 + ((y + 0.5 - cy) / ry) ** 2 <= 1:
                g[y][x] = GB_BODY
    for x in (8, 9, 14, 15):
        g[20][x] = GB_BODY
        g[21][x] = GB_BODY
    # sprout
    g[6][11] = GB_BODY
    g[6][12] = GB_BODY
    g[5][11] = GB_BODY
    g[5][12] = GB_BODY
    g[4][11] = GB_BODY
    g[4][12] = GB_BODY
    for x, y in (
        (8, 3), (9, 3), (9, 2), (10, 2), (10, 3),
        (13, 3), (14, 3), (14, 2), (15, 2), (15, 3),
        (7, 4), (8, 4), (9, 4),
        (14, 4), (15, 4), (16, 4),
    ):
        g[y][x] = GB_HI
    for y in range(17, 22):
        for x in range(24):
            if g[y][x] == GB_BODY:
                g[y][x] = GB_SHADE
    # Flat face must be at least 12x8 and must survive the outline pass.
    for y in range(10, 18):
        for x in range(5, 19):
            g[y][x] = GB_BODY
    g[8][8] = GB_HI
    g[8][9] = GB_HI
    g[9][8] = GB_HI
    return outline_grid(g, GB_OUT, protect=(6, 10, 18, 18))


def stamp(grid, ox, oy, pattern, color):
    for dy, row in enumerate(pattern):
        for dx, ch in enumerate(row):
            if ch == "#":
                x, y = ox + dx, oy + dy
                if 0 <= x < 24 and 0 <= y < 24 and grid[y][x] is not None:
                    grid[y][x] = color


# Patterns are drawn into the flat face. Origin for eyes is inside the block.
PIXEL_EYES = {
    "oval": [" ## ", "#  #", "#  #", " ## "],
    "dot": ["    ", " ## ", " ## ", "    "],
    "arc": ["    ", "#  #", " ## ", "    "],
    "wide": [" ## ", "#  #", "#  #", " ## "],
    "heart": ["# # ", "### ", " #  ", "    "],
    "sleepy": ["    ", "####", "    ", "    "],
    "focused": ["####", " #  ", " ## ", "    "],
    "spiral": ["### ", "# # ", " ## ", "    "],
    "shut": ["    ", "####", "    ", "    "],
}


def pixel_expr_grid(base, expr, ink, tear, eye_y=7, mouth_y=11, brow_y=6, z_at=(18, 5)):
    g = [row[:] for row in base]
    eye, mouth, brows, overlays = expr

    def eye_at(x, kind):
        pat = PIXEL_EYES[kind]
        stamp(g, x, eye_y, pat, ink)

    if brows == "angry":
        stamp(g, 6, brow_y, ["##  ", " ## "], ink)
        stamp(g, 13, brow_y, ["  ##", " ## "], ink)
    elif brows == "worried":
        stamp(g, 6, brow_y, [" ## ", "##  "], ink)
        stamp(g, 13, brow_y, ["##  ", "  ##"], ink)
    elif brows == "determined":
        stamp(g, 6, brow_y, ["### ", "  # "], ink)
        stamp(g, 13, brow_y, [" ###", " #  "], ink)

    if eye == "wink":
        eye_at(6, "shut")
        eye_at(13, "oval")
    elif eye == "smug":
        eye_at(6, "arc")
        eye_at(13, "oval")
    else:
        kind = {"oval": "oval", "dot": "dot", "arc": "arc", "wide": "wide", "heart": "heart",
                "sleepy": "sleepy", "focused": "focused", "spiral": "spiral"}[eye]
        eye_at(6, kind)
        eye_at(13, kind)

    mouths = {
        "line": ["          ", "##########", "          "],
        "smile": ["#        #", " #      # ", "  ######  "],
        "grin": [" ######## ", "#  ####  #", " ######## "],
        "frown": ["  ######  ", " #      # ", "#        #"],
        "o": ["   ####   ", "  #    #  ", "   ####   "],
        "tongue": ["#        #", " ####### #", "  #####   "],
        "smirk": ["#         ", " #######  ", "          "],
        "wavy": [" #  ##  # ", "#  ##  #  ", "          "],
    }
    stamp(g, 7, mouth_y, mouths[mouth], ink)
    dy = eye_y - 7
    if "blush" in overlays or "bigblush" in overlays:
        stamp(g, 5, 10 + dy, ["##"], BLUSH)
        stamp(g, 16, 10 + dy, ["##"], BLUSH)
    if "bigblush" in overlays:
        stamp(g, 4, 11 + dy, ["##"], BLUSH)
        stamp(g, 17, 11 + dy, ["##"], BLUSH)
    if "tear" in overlays:
        stamp(g, 7, 11 + dy, ["#", "#"], tear)
    if "streams" in overlays:
        stamp(g, 7, 10 + dy, ["#", "#", "#", "#"], tear)
        stamp(g, 16, 10 + dy, ["#", "#", "#", "#"], tear)
    if "sweat" in overlays:
        stamp(g, 17, 6 + dy, ["#", "#"], tear)
    if "steam" in overlays:
        stamp(g, 17, 5 + dy, ["##", "##"], CREAM)
    if "z" in overlays:
        stamp(g, z_at[0], z_at[1], ["###", " ##", "###"], ink)
    return g


def build_pixel(name, grid, ink, tear, **expr_at):
    raw = grid_image(grid)
    raw.save(os.path.join(BASES, f"{name}-24.png"))
    view = raw.resize((384, 384), Image.Resampling.NEAREST)
    # composite view sits on white for the base file
    base_view = Image.new("RGBA", (384, 384), WHITE)
    base_view.paste(view, (0, 0), view)
    base_view.convert("RGB").save(os.path.join(BASES, f"{name}.png"))
    cells_big = []
    cells_48 = []
    for spec in EXPRS:
        g = pixel_expr_grid(grid, spec[1:], ink, tear, **expr_at)
        im = grid_image(g)
        big = im.resize((384, 384), Image.Resampling.NEAREST)
        card = Image.new("RGBA", (384, 384), WHITE)
        card.paste(big, (0, 0), big)
        cells_big.append(card)
        small = im.resize((48, 48), Image.Resampling.NEAREST)
        cells_48.append(small)
    sheet = sheet_from_cells(cells_big, WHITE, gap=16)
    sheet.convert("RGB").save(os.path.join(SHEETS, f"{name}.png"))
    white = sheet_from_cells(cells_48, WHITE, gap=4)
    dark = sheet_from_cells(cells_48, DARK_BG, gap=4)
    white.convert("RGB").save(os.path.join(SIZE, f"{name}-white.png"))
    dark.convert("RGB").save(os.path.join(SIZE, f"{name}-dark.png"))
    white.resize((white.size[0] * 4, white.size[1] * 4), Image.Resampling.NEAREST).convert("RGB").save(
        os.path.join(SIZE, f"{name}-white-x4.png")
    )
    dark.resize((dark.size[0] * 4, dark.size[1] * 4), Image.Resampling.NEAREST).convert("RGB").save(
        os.path.join(SIZE, f"{name}-dark-x4.png")
    )


def save_generated_scraps():
    for src, dest in (
        ("pixel-a.jpg", "pixel-a-generated.png"),
        ("pixel-b.jpg", "pixel-b-generated.png"),
        ("ghost-b.jpg", "ghost-b-attempt1-hollow.png"),
        ("bot-a-retry.jpg", "bot-a-retry.png"),
        ("face-parts.jpg", None),
    ):
        im = Image.open(os.path.join(GEN, src)).convert("RGB")
        if dest:
            im.save(os.path.join(BASES, dest))
        else:
            im.save(os.path.join(PARTS, "face-parts.png"))


def authored_parts_sheet():
    """The glyph vocabulary, same strokes as the composites."""
    canvas = Image.new("RGBA", (1400, 900), WHITE)
    draw = ImageDraw.Draw(canvas)
    samples = [
        (80, 40, "oval"), (220, 40, "dot"), (360, 40, "arc"), (500, 40, "sleepy"),
        (640, 40, "wide"), (780, 40, "heart"), (920, 40, "spiral"), (1060, 40, "focused"),
    ]
    box_s = 110
    for x, y, kind in samples:
        draw_eye(draw, (x, y, x + box_s, y + box_s), kind, INK, 10)
    draw_brows(draw, (80, 220, 360, 300), "worried", INK, 12)
    draw_brows(draw, (420, 220, 700, 300), "angry", INK, 12)
    draw_brows(draw, (760, 220, 1040, 300), "determined", INK, 12)
    mouths = ["line", "smile", "grin", "frown", "o", "tongue", "smirk", "wavy"]
    for i, kind in enumerate(mouths):
        x = 40 + (i % 8) * 170
        draw_mouth(draw, (x, 400, x + 140, 520), kind, INK, 12, PINK)
    # overlays on a dummy face box
    for i, kind in enumerate(["blush", "bigblush", "tear", "streams", "sweat", "steam", "z"]):
        face = (40 + i * 190, 620, 200 + i * 190, 820)
        draw_overlay(draw, face, kind, INK, 12, False)
    canvas.convert("RGB").save(os.path.join(PARTS, "face-glyphs.png"))


def pixel_parts_sheet():
    ink = NAVY
    tiles = []
    labels_eyes = ["oval", "dot", "arc", "sleepy", "wide", "heart", "spiral", "focused", "shut"]
    sheet = Image.new("RGBA", (24 * 10, 24 * 6), (0, 0, 0, 0))
    # draw each eye pattern centered in a 24x24 tile
    def tile_with(rows, color, ox=4, oy=6):
        g = empty_grid()
        for y in range(4, 20):
            for x in range(4, 20):
                g[y][x] = (230, 236, 240, 255)
        stamp(g, ox, oy, rows, color)
        return grid_image(g)

    eyes = [tile_with(PIXEL_EYES[k], ink) for k in labels_eyes]
    mouths = ["line", "smile", "grin", "frown", "o", "tongue", "smirk", "wavy"]
    mouth_tiles = []
    mouth_map = {
        "line": ["          ", "##########", "          "],
        "smile": ["#        #", " #      # ", "  ######  "],
        "grin": [" ######## ", "#  ####  #", " ######## "],
        "frown": ["  ######  ", " #      # ", "#        #"],
        "o": ["   ####   ", "  #    #  ", "   ####   "],
        "tongue": ["#        #", " ####### #", "  #####   "],
        "smirk": ["#         ", " #######  ", "          "],
        "wavy": [" #  ##  # ", "#  ##  #  ", "          "],
    }
    for k in mouths:
        mouth_tiles.append(tile_with(mouth_map[k], ink, ox=6, oy=8))
    brows = [
        ["##  ", " ## ", "    ", "  ##", " ## "],
        [" ## ", "##  ", "    ", "##  ", "  ##"],
        ["### ", "  # ", "    ", " ###", " #  "],
    ]
    brow_tiles = [tile_with(b, ink, ox=6, oy=8) for b in brows]
    # scale 8x and arrange
    scale = 8
    canvas = Image.new("RGB", (scale * 24 * 9 + 40, scale * 24 * 4 + 40), (255, 255, 255))
    def paste_row(tiles, row):
        for i, t in enumerate(tiles):
            big = t.resize((24 * scale, 24 * scale), Image.Resampling.NEAREST)
            canvas.paste(big, (20 + i * (24 * scale + 8), 20 + row * (24 * scale + 12)), big)
    paste_row(eyes, 0)
    paste_row(brow_tiles, 1)
    paste_row(mouth_tiles, 2)
    overlays = []
    for rows, col in (
        (["##"], BLUSH),
        (["#", "#", "#"], TEAR),
        (["###", " ##", "###"], ink),
        (["##", "##"], CREAM),
    ):
        overlays.append(tile_with(rows, col, ox=8, oy=8))
    paste_row(overlays, 3)
    canvas.save(os.path.join(PARTS, "face-parts-pixel.png"))


def main():
    for d in (BASES, PARTS, SHEETS, SIZE):
        os.makedirs(d, exist_ok=True)
    save_generated_scraps()
    authored_parts_sheet()
    pixel_parts_sheet()
    for name in VECTOR_SRC:
        build_vector(name)
    build_pixel("pixel-a", make_pixel_a(), NAVY, TEAR)
    build_pixel(
        "pixel-b",
        make_pixel_b(),
        GB_OUT,
        (220, 236, 170, 255),
        eye_y=11,
        mouth_y=15,
        brow_y=10,
        z_at=(16, 10),
    )
    print("done")


if __name__ == "__main__":
    main()
