"""Review sheets for an imported item. Skia only; CI does not run this."""

from __future__ import annotations

import sys
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[2]
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))

import import_art  # noqa: E402

from . import compose, render

ROOT = TOOLS.parent


def _look(library, items: list[str], expression: str, mouth: str | None) -> list[dict]:
    assets = library.look(items, expression)
    if not mouth:
        return assets
    replacement = library.get(mouth)
    return [replacement if item["category"] == "face_mouth" else item for item in assets]


def write_review(asset_id: str, out_dir: Path | None = None) -> Path:
    library = compose.Library()
    library.get(asset_id)
    catalog = [
        {"id": asset["id"], "category": asset["category"], "conflictsWith": asset.get("conflictsWith") or []}
        for asset in library.assets.values()
        if "picture" in asset
    ]
    neighbors = import_art.sheet_neighbors(catalog, asset_id)
    rows: list[tuple[str, list[render.Cell]]] = []
    for wall in ("light", "dark"):
        for label, expression, mouth in import_art.SHEET_EXPRESSIONS:
            look = _look(library, [asset_id], expression, mouth)
            rows.append(
                (
                    f"{wall} · {label}",
                    [
                        render.Cell(look, "512", size=512, wall=wall),
                        render.Cell(look, "48", size=48, zoom=4, wall=wall),
                    ],
                )
            )
    for neighbor in neighbors:
        look = _look(library, [asset_id, neighbor], "neutral_face", None)
        rows.append((f"with {neighbor}", [render.Cell(look, "512", size=512, wall="light")]))
    image = render.sheet(rows, title=asset_id)
    destination = out_dir or (ROOT / "docs" / "handoff" / "sheets" / asset_id)
    destination.mkdir(parents=True, exist_ok=True)
    path = destination / "review.png"
    render.save_png(image, path)
    return path
