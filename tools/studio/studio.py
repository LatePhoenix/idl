"""iDL Art Studio command line. Operator-only; never shipped. See docs/avatar/ART_STUDIO.md.

    python tools/studio/studio.py serve [--port 8765]
    python tools/studio/studio.py list [--category hair] [--pack emoji_core]
    python tools/studio/studio.py show <assetId>
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from engine import repo, server  # noqa: E402


def cmd_list(args: argparse.Namespace) -> int:
    rows = []
    for pack in repo.load_packs():
        if args.pack and pack.pack_id != args.pack:
            continue
        for asset in pack.assets:
            if args.category and asset["category"] != args.category:
                continue
            rows.append((
                asset["id"],
                asset["category"],
                asset.get("tier", "free").lower(),
                str(asset.get("contentVersion", "")),
                "svg" if pack.source_path(asset["id"]).is_file() else "-",
                f"{pack.pack_id}/v{pack.version}",
            ))
    header = ("id", "category", "tier", "cv", "source", "pack")
    widths = [max(len(r[i]) for r in rows + [header]) for i in range(len(header))]
    for row in [header] + sorted(rows, key=lambda r: (r[5], r[1], r[0])):
        print("  ".join(cell.ljust(widths[i]) for i, cell in enumerate(row)).rstrip())
    print(f"{len(rows)} assets")
    return 0


def cmd_show(args: argparse.Namespace) -> int:
    found = repo.find_asset(args.asset_id)
    if found is None:
        print(f"unknown asset: {args.asset_id}", file=sys.stderr)
        return 1
    pack, asset = found
    print(json.dumps(asset, indent=2))
    source = pack.source_path(asset["id"])
    print(f"\nsource: {source.relative_to(repo.ROOT).as_posix() if source.is_file() else 'missing'}")
    picture = pack.pictures.get(asset["id"])
    if picture:
        print(f"picture: schema {picture['schemaVersion']}, contentVersion {picture['contentVersion']}")
        for part in picture.get("parts", []):
            fill = part["fill"].get("slot") or ("radial" if "radial" in part["fill"] else "linear")
            extras = [
                f"stroke={part['stroke']['slot']}/{part['stroke']['width']:g}" if part.get("stroke") else "",
                f"clip={part['clip']['id']}:{part['clip']['mode']}" if part.get("clip") else "",
                f"tags={','.join(part['tags'])}" if part.get("tags") else "",
                f"publishes={part['publishMask']}" if part.get("publishMask") else "",
                f"clipBy={','.join(c['mask'] + ':' + c['mode'] for c in part['clipBy'])}" if part.get("clipBy") else "",
            ]
            print(f"  {part['id']:<16} band {part['zBand']:>3}  {fill:<18} {' '.join(e for e in extras if e)}".rstrip())
    return 0


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(prog="studio", description="iDL Art Studio (operator-only)")
    sub = parser.add_subparsers(dest="command", required=True)
    serve = sub.add_parser("serve", help="run the web UI on 127.0.0.1")
    serve.add_argument("--port", type=int, default=8765)
    listing = sub.add_parser("list", help="list assets")
    listing.add_argument("--category")
    listing.add_argument("--pack")
    show = sub.add_parser("show", help="show one asset's manifest entry and parts")
    show.add_argument("asset_id")
    args = parser.parse_args(argv)
    if args.command == "serve":
        server.serve(args.port)
        return 0
    return {"list": cmd_list, "show": cmd_show}[args.command](args)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
