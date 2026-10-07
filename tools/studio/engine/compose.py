"""Build the asset list for a look: base + expression + worn items, with drafts mixed in.

Mirrors compose() in web/render.js's caller (web/app.js). The real AvatarResolver arrives in S2.
"""

from __future__ import annotations

from . import repo

FACE_PARTS = {"face_eye": "eyes", "face_brow": "brows", "face_mouth": "mouth"}
SINGLE = {"hair", "face_accessory", "head_accessory", "top", "outerwear", "foreground_prop", "scene"}


class Library:
    """Every shipped vector asset, plus drafts layered on top (a draft shadows a shipped id)."""

    def __init__(self, drafts: dict[str, dict] | None = None):
        self.packs = repo.load_packs()
        self.pack = next(p for p in self.packs if any(a["category"] == "base" and a["id"] in p.pictures for a in p.assets))
        self.assets: dict[str, dict] = {}
        for pack in self.packs:
            for asset in pack.assets:
                if asset["id"] in pack.pictures:
                    self.assets[asset["id"]] = {**asset, "picture": pack.pictures[asset["id"]]}
        for asset_id, asset in (drafts or {}).items():
            self.assets[asset_id] = asset
        self.base = next(a for a in self.assets.values() if a["category"] == "base")

    def get(self, asset_id: str) -> dict:
        if asset_id not in self.assets:
            raise KeyError(f"unknown asset or draft: {asset_id}")
        return self.assets[asset_id]

    def by_category(self, category: str) -> list[dict]:
        return sorted((a for a in self.assets.values() if a["category"] == category), key=lambda a: a["id"])

    def expression_ids(self) -> list[str]:
        return [e["id"] for e in self.pack.manifest.get("expressions", [])]

    def expression(self, expression_id: str) -> dict:
        for e in self.pack.manifest.get("expressions", []):
            if e["id"] == expression_id:
                parts = (e.get("baseOverrides") or {}).get(self.base["id"]) or {}
                overlays = [o for o in e.get("overlays", []) if o in self.assets]
                return {"eyes": parts.get("eyes"), "brows": parts.get("brows"), "mouth": parts.get("mouth"), "overlays": overlays}
        raise KeyError(f"unknown expression: {expression_id}")

    def look(self, items: list[str] = (), expression: str = "neutral_face", *, top: bool = True) -> list[dict]:
        """Base, expression and items. A later item replaces an earlier one in a single-item category,
        and a face part replaces the expression's part."""
        face = self.expression(expression)
        chosen: dict[str, list[dict]] = {}
        if top:
            default_top = (self.pack.manifest.get("defaults") or {}).get("top")
            if default_top in self.assets:
                chosen["top"] = [self.assets[default_top]]
        for asset_id in items:
            asset = self.get(asset_id)
            category = asset["category"]
            if category in FACE_PARTS:
                face[FACE_PARTS[category]] = asset_id
            elif category in SINGLE:
                chosen[category] = [asset]
            else:
                chosen.setdefault(category, []).append(asset)
        out = [self.base]
        for key in ("eyes", "brows", "mouth"):
            if face.get(key) in self.assets:
                out.append(self.assets[face[key]])
        out += [self.assets[o] for o in face["overlays"]]
        for group in chosen.values():
            out += group
        seen, unique = set(), []
        for a in out:
            if a["id"] not in seen:
                seen.add(a["id"])
                unique.append(a)
        return unique
