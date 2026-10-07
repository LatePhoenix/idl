"""Category kits: tools/studio/kits/<category>.json (ART_STUDIO.md §5.1)."""

from __future__ import annotations

import json
from pathlib import Path

KITS = Path(__file__).resolve().parents[1] / "kits"


def names() -> list[str]:
    return sorted(p.stem for p in KITS.glob("*.json"))


def load(category: str) -> dict:
    path = KITS / f"{category}.json"
    if not path.is_file():
        raise ValueError(f"no kit for {category}; kits: {', '.join(names())}")
    return json.loads(path.read_text(encoding="utf-8"))


def skeleton_svg(category: str, asset_id: str) -> str:
    """A starting SVG with every skeleton part as an empty placeholder comment block."""
    kit = load(category)
    lines = [
        '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"',
        f'     data-schema-version="{kit["schemaVersion"]}" data-id="{asset_id}"',
        '     data-content-version="1">',
    ]
    for part in kit["skeleton"]:
        need = "required" if part.get("required") else "optional"
        lines.append(f'  <!-- {part["part"]} ({need}): {part["about"]} -->')
        if part.get("clipPath"):
            lines.append(f'  <path data-clip-path="{part["part"]}" d="M 0 0 Z"/>')
            continue
        attrs = [f'data-part="{part["part"]}"', f'data-z="{part["band"]}"', f'data-slot="{part["slot"]}"']
        if part.get("tags"):
            attrs.append(f'data-tags="{" ".join(part["tags"])}"')
        if part.get("opacity") is not None:
            attrs.append(f'data-opacity="{part["opacity"]}"')
        if part.get("publishMask"):
            attrs.append(f'data-publish-mask="{part["publishMask"]}"')
        if part.get("clipBy"):
            attrs.append(f'data-clip-by="{" ".join(part["clipBy"])}"')
        if part.get("clip"):
            clip_id, mode = part["clip"].split(":")
            attrs.append(f'data-clip="{clip_id}" data-clip-mode="{mode}"')
        lines.append(f'  <path {" ".join(attrs)} d="M 0 0 Z"/>')
    lines.append("</svg>")
    return "\n".join(lines) + "\n"
