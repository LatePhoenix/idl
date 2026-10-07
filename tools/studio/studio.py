"""iDL Art Studio command line. Operator-only; never shipped. See docs/avatar/ART_STUDIO.md.

S0 (stdlib): serve, list, show
S1 (Skia venv): setup, guides, kit, draft, lint, render, geom
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import venv
from pathlib import Path

STUDIO_DIR = Path(__file__).resolve().parent
sys.path.insert(0, str(STUDIO_DIR))

from engine import repo, server  # noqa: E402

VENV_DIR = STUDIO_DIR / ".venv"
REQUIREMENTS = STUDIO_DIR / "requirements.txt"
SKIA_COMMANDS = frozenset({"guides", "kit", "draft", "lint", "render", "geom"})


def _venv_python() -> Path:
    if os.name == "nt":
        return VENV_DIR / "Scripts" / "python.exe"
    return VENV_DIR / "bin" / "python"


def _has_skia() -> bool:
    try:
        import skia  # noqa: F401
        return True
    except ImportError:
        return False


def _ensure_skia() -> None:
    """Re-exec under tools/studio/.venv when needed, or tell the user to run setup."""
    if _has_skia():
        return
    py = _venv_python()
    if py.is_file():
        current = Path(sys.executable).resolve()
        if current != py.resolve():
            os.execv(str(py), [str(py), str(Path(__file__).resolve()), *sys.argv[1:]])
    print(
        "Skia is not installed. Create the Studio venv:\n"
        "  python tools/studio/studio.py setup",
        file=sys.stderr,
    )
    raise SystemExit(1)


def cmd_setup(_args: argparse.Namespace) -> int:
    print(f"Creating venv at {VENV_DIR}")
    VENV_DIR.mkdir(parents=True, exist_ok=True)
    venv.EnvBuilder(with_pip=True, clear=False).create(VENV_DIR)
    py = _venv_python()
    subprocess.check_call([str(py), "-m", "pip", "install", "--upgrade", "pip"])
    subprocess.check_call([str(py), "-m", "pip", "install", "-r", str(REQUIREMENTS)])
    print(f"Done. Skia commands use {py}")
    return 0


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


def cmd_guides(args: argparse.Namespace) -> int:
    from engine import guides
    data = guides.guides()
    if args.json:
        # Paths are large; keep them for tooling that needs them.
        print(json.dumps(data, indent=2))
        return 0
    for key, value in data.items():
        if key in {"notes", "viewBox"}:
            continue
        about = value.get("about", "") if isinstance(value, dict) else ""
        kind = value.get("type", "") if isinstance(value, dict) else type(value).__name__
        summary = ""
        if isinstance(value, dict):
            if "box" in value and value["box"]:
                summary = f"box={value['box']}"
            elif "at" in value:
                summary = f"at={value['at']}"
            elif "y" in value:
                summary = f"y={value['y']}"
            elif "d" in value:
                summary = f"path ({len(value['d'])} chars)"
            elif "rows" in value:
                summary = f"{len(value['rows'])} rows"
            elif "named" in value:
                summary = f"{len(value['named'])} anchors"
            elif "value" in value:
                summary = str(value["value"])
            elif "x" in value:
                summary = f"x={value['x']}"
        print(f"{key:<18} {kind:<8} {summary}  {about}".rstrip())
    for note in data.get("notes", []):
        print(f"# {note}")
    return 0


def cmd_kit(args: argparse.Namespace) -> int:
    from engine import kits
    kit = kits.load(args.category)
    if args.skeleton:
        print(kits.skeleton_svg(args.category, args.skeleton), end="")
        return 0
    print(json.dumps({k: v for k, v in kit.items() if k != "skeleton"}, indent=2))
    print("\nskeleton:")
    for part in kit["skeleton"]:
        need = "required" if part.get("required") else "optional"
        print(f"  {part['part']:<12} band {part['band']:<3} {part.get('slot', '-'):<16} {need}  {part['about']}")
    print("\ntips:")
    for tip in kit.get("tips", []):
        print(f"  - {tip}")
    return 0


def _read_svg(path: str) -> str:
    if path == "-":
        return sys.stdin.read()
    return Path(path).read_text(encoding="utf-8")


def _library():
    from engine import compose, drafts
    return compose.Library(drafts.assets_for_preview())


def _print_lint(issues: list[dict]) -> None:
    if not issues:
        print("lint: clean")
        return
    for issue in issues:
        part = f" [{issue['part']}]" if issue.get("part") else ""
        print(f"{issue['level']:<5} {issue['rule']:<12}{part} {issue['message']}")
    errors = sum(1 for i in issues if i["level"] == "error")
    warns = sum(1 for i in issues if i["level"] == "warn")
    print(f"{errors} error(s), {warns} warning(s)")


def cmd_draft(args: argparse.Namespace) -> int:
    from engine import compose, drafts, lint as lint_mod, render

    action = args.draft_action
    try:
        if action == "new":
            meta = drafts.new(
                args.id, args.category, prompt=args.prompt or "", group=args.group or "",
                from_asset=args.from_asset, label=args.label or "",
            )
            print(json.dumps(meta, indent=2))
            return 0
        if action == "list":
            rows = drafts.all_drafts()
            if not rows:
                print("no drafts")
                return 0
            for meta in rows:
                print(f"{meta['id']:<24} {meta['category']:<16} rev {meta['current']:<3} "
                      f"group={meta.get('group', '')}  {meta.get('prompt', '')[:60]}")
            return 0
        if action == "show":
            meta = drafts.load(args.id)
            print(json.dumps(meta, indent=2))
            if meta.get("current"):
                print(f"\n--- rev-{meta['current']:03d}.svg ---")
                print(drafts.svg_text(args.id))
            return 0
        if action == "revert":
            meta = drafts.revert(args.id, args.n)
            print(f"reverted {args.id} to rev {args.n} → now rev {meta['current']}")
            return 0
        if action == "set":
            fields = {}
            for item in args.assignments:
                if "=" not in item:
                    print(f"expected key=value, got {item}", file=sys.stderr)
                    return 1
                key, value = item.split("=", 1)
                fields[key] = value
            meta = drafts.set_meta(args.id, **fields)
            print(json.dumps(meta, indent=2))
            return 0
        if action == "write":
            svg = _read_svg(args.file)
            meta = drafts.write(args.id, svg, note=args.note or "")
            picture = drafts.compile_draft(args.id)
            library = compose.Library(drafts.assets_for_preview())
            issues = lint_mod.lint(picture, meta, library)
            drafts.RENDERS.mkdir(parents=True, exist_ok=True)
            out = drafts.RENDERS / f"{args.id}-r{meta['current']:03d}.png"
            image = render.standard_sheet(library, args.id)
            render.save_png(image, out)
            print(f"draft {args.id} rev {meta['current']}")
            _print_lint(issues)
            print(f"sheet: {out}")
            return 1 if any(i["level"] == "error" for i in issues) else 0
    except drafts.DraftError as error:
        print(str(error), file=sys.stderr)
        return 1
    print(f"unknown draft action {action}", file=sys.stderr)
    return 1


def cmd_lint(args: argparse.Namespace) -> int:
    from engine import compose, drafts, lint as lint_mod
    try:
        meta = drafts.load(args.id)
        picture = drafts.compile_draft(args.id)
        library = compose.Library(drafts.assets_for_preview())
        issues = lint_mod.lint(picture, meta, library)
    except Exception as error:  # DraftError, PipelineError, KeyError
        print(str(error), file=sys.stderr)
        return 1
    if args.json:
        print(json.dumps(issues, indent=2))
    else:
        _print_lint(issues)
    return 1 if any(i["level"] == "error" for i in issues) else 0


def cmd_render(args: argparse.Namespace) -> int:
    from engine import compose, drafts, render

    library = _library()
    target = args.id
    with_items = [x.strip() for x in (args.with_items or "").split(",") if x.strip()]
    try:
        if drafts.exists(target) and drafts.load(target).get("current"):
            focus_id = target
        else:
            library.get(target)  # KeyError if unknown
            focus_id = target
        image = render.standard_sheet(
            library, focus_id, with_items=with_items, expression=args.expression or "neutral_face",
        )
        drafts.RENDERS.mkdir(parents=True, exist_ok=True)
        if args.out:
            out = Path(args.out)
        else:
            rev = drafts.load(focus_id)["current"] if drafts.exists(focus_id) else 0
            out = drafts.RENDERS / f"{focus_id}-r{rev:03d}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        render.save_png(image, out)
        print(out)
        return 0
    except Exception as error:
        print(str(error), file=sys.stderr)
        return 1


def _resolve_geom_arg(raw: str) -> str:
    """Expand @guide:name, @part:draftId/part, @file:path into path `d` strings."""
    from engine import drafts, guides

    if raw.startswith("@guide:"):
        name = raw[len("@guide:"):]
        g = guides.guides()
        if name not in g:
            raise ValueError(f"unknown guide {name}")
        entry = g[name]
        if isinstance(entry, dict) and "d" in entry:
            return entry["d"]
        if isinstance(entry, dict) and "box" in entry and entry["box"]:
            l, t, r, b = entry["box"]
            return f"M {l} {t} L {r} {t} L {r} {b} L {l} {b} Z"
        raise ValueError(f"guide {name} has no path or box")
    if raw.startswith("@part:"):
        rest = raw[len("@part:"):]
        if "/" not in rest:
            raise ValueError("@part: needs draftId/part")
        draft_id, part_id = rest.split("/", 1)
        picture = drafts.compile_draft(draft_id)
        for part in picture["parts"]:
            if part["id"] == part_id:
                return part["commands"]
        raise ValueError(f"{draft_id} has no part {part_id}")
    if raw.startswith("@file:"):
        return Path(raw[len("@file:"):]).read_text(encoding="utf-8").strip()
    return raw


def cmd_geom(args: argparse.Namespace) -> int:
    from engine import paths

    op = args.op
    raw = args.args
    try:
        if op == "ellipse":
            cx, cy, rx, ry = (float(x) for x in raw[:4])
            print(paths.ellipse(cx, cy, rx, ry))
            return 0
        if op == "offset":
            print(paths.offset(_resolve_geom_arg(raw[0]), float(raw[1])))
            return 0
        if op == "thicken":
            cap = raw[2] if len(raw) > 2 else "round"
            print(paths.thicken(_resolve_geom_arg(raw[0]), float(raw[1]), cap))
            return 0
        if op == "mirror":
            axis = float(raw[1]) if len(raw) > 1 else 512.0
            print(paths.mirror(_resolve_geom_arg(raw[0]), axis))
            return 0
        if op == "symmetric":
            axis = float(raw[1]) if len(raw) > 1 else 512.0
            print(paths.symmetric(_resolve_geom_arg(raw[0]), axis))
            return 0
        resolved = [_resolve_geom_arg(a) for a in raw]
        if op == "union":
            print(paths.union(*resolved))
        elif op == "subtract":
            print(paths.subtract(resolved[0], resolved[1]))
        elif op == "intersect":
            print(paths.intersect(resolved[0], resolved[1]))
        elif op == "simplify":
            print(paths.simplify(resolved[0]))
        elif op == "bounds":
            print(json.dumps(list(paths.bounds(resolved[0]))))
        elif op == "area":
            print(paths.area(resolved[0]))
        elif op == "to_d":
            print(paths.to_d(paths.parse(resolved[0])))
        else:
            print(f"unknown geom op {op}", file=sys.stderr)
            return 1
        return 0
    except Exception as error:
        print(str(error), file=sys.stderr)
        return 1


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

    setup = sub.add_parser("setup", help="create tools/studio/.venv and install requirements")

    guides_p = sub.add_parser("guides", help="print head guides")
    guides_p.add_argument("--json", action="store_true")

    kit_p = sub.add_parser("kit", help="show a category kit")
    kit_p.add_argument("category")
    kit_p.add_argument("--skeleton", metavar="ID", help="print a starter SVG for draft id")

    draft_p = sub.add_parser("draft", help="create and edit drafts")
    draft_sub = draft_p.add_subparsers(dest="draft_action", required=True)
    d_new = draft_sub.add_parser("new")
    d_new.add_argument("id")
    d_new.add_argument("--category", required=True)
    d_new.add_argument("--prompt", default="")
    d_new.add_argument("--group", default="")
    d_new.add_argument("--label", default="")
    d_new.add_argument("--from", dest="from_asset", default=None)
    draft_sub.add_parser("list")
    d_show = draft_sub.add_parser("show")
    d_show.add_argument("id")
    d_write = draft_sub.add_parser("write")
    d_write.add_argument("id")
    d_write.add_argument("file", help="SVG path or - for stdin")
    d_write.add_argument("--note", default="")
    d_revert = draft_sub.add_parser("revert")
    d_revert.add_argument("id")
    d_revert.add_argument("n", type=int)
    d_set = draft_sub.add_parser("set")
    d_set.add_argument("id")
    d_set.add_argument("assignments", nargs="+", help="key=value (label, notes, prompt, tier, group, tinted, slot:NAME)")

    lint_p = sub.add_parser("lint", help="lint a draft")
    lint_p.add_argument("id")
    lint_p.add_argument("--json", action="store_true")

    render_p = sub.add_parser("render", help="render the standard sheet for a draft or asset")
    render_p.add_argument("id")
    render_p.add_argument("--with", dest="with_items", default="")
    render_p.add_argument("--expression", default="neutral_face")
    render_p.add_argument("--out", default=None)

    geom_p = sub.add_parser("geom", help="geometry helpers")
    geom_p.add_argument("op", help="union|subtract|intersect|offset|mirror|symmetric|thicken|simplify|bounds|area|ellipse|to_d")
    geom_p.add_argument("args", nargs="*", help="paths or @guide:name / @part:id/part / @file:path")

    args = parser.parse_args(argv)
    if args.command in SKIA_COMMANDS:
        _ensure_skia()
    if args.command == "serve":
        server.serve(args.port)
        return 0
    handlers = {
        "list": cmd_list,
        "show": cmd_show,
        "setup": cmd_setup,
        "guides": cmd_guides,
        "kit": cmd_kit,
        "draft": cmd_draft,
        "lint": cmd_lint,
        "render": cmd_render,
        "geom": cmd_geom,
    }
    return handlers[args.command](args)


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
