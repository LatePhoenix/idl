"""Drafts: work-in-progress items in .studio/drafts/<assetId>/ (gitignored).

Each draft has meta.json and numbered SVG revisions (rev-001.svg, …). Nothing here touches
art/ or the pack until S3's promote.
"""

from __future__ import annotations

import json
import re
import time
from pathlib import Path

from . import kits, paths, repo

DRAFTS = repo.ROOT / ".studio" / "drafts"
RENDERS = repo.ROOT / ".studio" / "renders"
_ID = re.compile(r"^[a-z][a-z0-9_]{2,63}$")


class DraftError(ValueError):
    pass


def _dir(draft_id: str) -> Path:
    return DRAFTS / draft_id


def exists(draft_id: str) -> bool:
    return (_dir(draft_id) / "meta.json").is_file()


def load(draft_id: str) -> dict:
    path = _dir(draft_id) / "meta.json"
    if not path.is_file():
        raise DraftError(f"no draft {draft_id}")
    return json.loads(path.read_text(encoding="utf-8"))


def save(meta: dict) -> None:
    meta["updated"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
    _dir(meta["id"]).mkdir(parents=True, exist_ok=True)
    (_dir(meta["id"]) / "meta.json").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8")


def all_drafts() -> list[dict]:
    if not DRAFTS.is_dir():
        return []
    return [load(p.name) for p in sorted(DRAFTS.iterdir()) if (p / "meta.json").is_file()]


def new(draft_id: str, category: str, *, prompt: str = "", group: str = "", from_asset: str | None = None,
        label: str = "", tier: str = "free") -> dict:
    if not _ID.match(draft_id):
        raise DraftError("draft id: lowercase letters, digits and _, 3-64 chars, starting with a letter")
    if exists(draft_id):
        raise DraftError(f"draft {draft_id} exists")
    kit = kits.load(category)
    shipped = repo.find_asset(draft_id)
    if shipped and not from_asset:
        raise DraftError(f"{draft_id} is a shipped asset; use --from {draft_id} to edit it")
    meta = {
        "id": draft_id,
        "category": kit["category"],
        "group": group or draft_id,
        "prompt": prompt,
        "notes": "",
        "label": label,
        "tier": tier,
        "tinted": False,
        "colorSlots": dict(kit["colorSlots"]),
        "editing": from_asset,
        "contentVersion": 1,
        "revisions": [],
        "current": 0,
        "created": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
    }
    if from_asset:
        found = repo.find_asset(from_asset)
        if not found:
            raise DraftError(f"unknown asset {from_asset}")
        pack, asset = found
        meta["colorSlots"] = dict(asset.get("colorSlots", {}))
        meta["label"] = label or asset.get("accessibilityLabel", "")
        meta["tier"] = str(asset.get("tier", tier)).lower()
        meta["contentVersion"] = int(asset.get("contentVersion", 1)) + (0 if draft_id != from_asset else 1)
        source = pack.source_path(from_asset)
        save(meta)
        if source.is_file():
            text = source.read_text(encoding="utf-8")
            text = re.sub(r'data-id="[^"]*"', f'data-id="{draft_id}"', text, count=1)
            text = re.sub(r'data-content-version="\d+"', f'data-content-version="{meta["contentVersion"]}"', text, count=1)
            write(draft_id, text, note=f"copied from {from_asset}")
        return load(draft_id)
    save(meta)
    return meta


def write(draft_id: str, svg: str, *, note: str = "") -> dict:
    meta = load(draft_id)
    n = len(meta["revisions"]) + 1
    (_dir(draft_id) / f"rev-{n:03d}.svg").write_text(svg, encoding="utf-8")
    meta["revisions"].append({"n": n, "note": note, "time": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())})
    meta["current"] = n
    save(meta)
    return meta


def revert(draft_id: str, n: int) -> dict:
    meta = load(draft_id)
    if not any(r["n"] == n for r in meta["revisions"]):
        raise DraftError(f"{draft_id} has no revision {n}")
    return write(draft_id, svg_text(draft_id, n), note=f"revert to rev {n}")


def svg_text(draft_id: str, n: int | None = None) -> str:
    meta = load(draft_id)
    n = n or meta["current"]
    if not n:
        raise DraftError(f"{draft_id} has no revisions yet")
    return (_dir(draft_id) / f"rev-{n:03d}.svg").read_text(encoding="utf-8")


def set_meta(draft_id: str, **fields) -> dict:
    meta = load(draft_id)
    for key, value in fields.items():
        if key.startswith("slot:"):
            meta["colorSlots"][key[5:]] = value
        elif key in {"label", "notes", "prompt", "tier", "group"}:
            meta[key] = value
        elif key == "tinted":
            meta[key] = str(value).lower() in {"1", "true", "yes"}
        else:
            raise DraftError(f"unknown field {key}")
    save(meta)
    return meta


def compile_draft(draft_id: str, n: int | None = None) -> dict:
    """Picture JSON for a revision, through the real pipeline parser. Raises on invalid SVG."""
    text = svg_text(draft_id, n)
    try:
        return paths.pipeline.parse_svg(text, f"{draft_id}.svg")
    except paths.pipeline.PipelineError as error:
        raise DraftError(str(error)) from error


def as_asset(draft_id: str, n: int | None = None) -> dict:
    meta = load(draft_id)
    return {
        "id": draft_id,
        "category": meta["category"],
        "accessibilityLabel": meta.get("label") or draft_id,
        "colorSlots": meta["colorSlots"],
        "tier": meta.get("tier", "free"),
        "contentVersion": meta.get("contentVersion", 1),
        "picture": compile_draft(draft_id, n),
        "draft": True,
    }


def assets_for_preview() -> dict[str, dict]:
    """Every draft whose current revision compiles, keyed by id."""
    out = {}
    for meta in all_drafts():
        if not meta.get("current"):
            continue
        try:
            out[meta["id"]] = as_asset(meta["id"])
        except DraftError:
            continue
    return out
