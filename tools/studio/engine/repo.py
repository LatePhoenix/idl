"""Read-only view of the avatar art in the repository.

Packs live in app/src/main/assets/packs/<packId>/v<N>/. The highest N is the live version.
SVG sources live in art/<packId>/<assetId>.svg. The expression catalog is config/expression_catalog.json.
"""

from __future__ import annotations

import json
import re
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
PACKS = ROOT / "app" / "src" / "main" / "assets" / "packs"
ART = ROOT / "art"
CATALOG = ROOT / "config" / "expression_catalog.json"
_VERSION_DIR = re.compile(r"^v(\d+)$")


@dataclass
class Pack:
    pack_id: str
    version: int
    directory: Path
    manifest: dict
    pictures: dict[str, dict] = field(default_factory=dict)

    @property
    def assets(self) -> list[dict]:
        return self.manifest.get("assets", [])

    def asset(self, asset_id: str) -> dict | None:
        return next((a for a in self.assets if a["id"] == asset_id), None)

    def source_path(self, asset_id: str) -> Path:
        return ART / self.pack_id / f"{asset_id}.svg"


def _live_version(pack_dir: Path) -> tuple[int, Path] | None:
    versions = []
    for child in pack_dir.iterdir():
        match = _VERSION_DIR.match(child.name)
        if child.is_dir() and match and (child / "manifest.json").is_file():
            versions.append((int(match.group(1)), child))
    return max(versions) if versions else None


def load_pack(pack_id: str) -> Pack:
    live = _live_version(PACKS / pack_id)
    if live is None:
        raise FileNotFoundError(f"no versioned manifest under {PACKS / pack_id}")
    version, directory = live
    manifest = json.loads((directory / "manifest.json").read_text(encoding="utf-8"))
    pack = Pack(pack_id, version, directory, manifest)
    for asset in pack.assets:
        render = asset.get("render", {})
        if render.get("type") != "vector":
            continue
        picture = directory / render["file"]
        if picture.is_file():
            pack.pictures[asset["id"]] = json.loads(picture.read_text(encoding="utf-8"))
    return pack


def load_packs() -> list[Pack]:
    packs = []
    for pack_dir in sorted(PACKS.iterdir()):
        if pack_dir.is_dir() and _live_version(pack_dir):
            packs.append(load_pack(pack_dir.name))
    return packs


def load_catalog() -> dict:
    return json.loads(CATALOG.read_text(encoding="utf-8"))


def state() -> dict:
    """Everything the web UI needs in one document. Only packs with vector pictures are useful to it."""
    packs = []
    for pack in load_packs():
        assets = []
        for asset in pack.assets:
            entry = dict(asset)
            entry["picture"] = pack.pictures.get(asset["id"])
            entry["hasSource"] = pack.source_path(asset["id"]).is_file()
            assets.append(entry)
        packs.append({
            "packId": pack.pack_id,
            "version": pack.version,
            "defaults": pack.manifest.get("defaults", {}),
            "expressions": pack.manifest.get("expressions", []),
            "expressionOverrides": pack.manifest.get("expressionOverrides", {}),
            "retired": pack.manifest.get("retired", {}),
            "assets": assets,
        })
    return {"packs": packs, "catalog": load_catalog()}


def find_asset(asset_id: str) -> tuple[Pack, dict] | None:
    for pack in load_packs():
        asset = pack.asset(asset_id)
        if asset is not None:
            return pack, asset
    return None
