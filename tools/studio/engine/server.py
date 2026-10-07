"""Local HTTP server for the Studio web UI. Binds to 127.0.0.1 only."""

from __future__ import annotations

import json
import mimetypes
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlparse

from . import repo

WEB = Path(__file__).resolve().parents[1] / "web"


class Handler(BaseHTTPRequestHandler):
    server_version = "iDLStudio/1"

    def do_GET(self) -> None:  # noqa: N802 (http.server naming)
        url = urlparse(self.path)
        try:
            if url.path == "/api/state":
                self._json(repo.state())
            elif url.path == "/api/source":
                self._source(parse_qs(url.query))
            elif url.path == "/api/drafts":
                self._drafts_list()
            elif url.path.startswith("/api/drafts/"):
                self._draft_one(url.path[len("/api/drafts/"):])
            elif url.path == "/api/guides":
                self._guides()
            else:
                self._static(url.path)
        except Exception as error:  # the UI shows the message; the server keeps running
            self._send(500, "text/plain; charset=utf-8", f"{type(error).__name__}: {error}".encode())

    def do_POST(self) -> None:  # noqa: N802
        url = urlparse(self.path)
        length = int(self.headers.get("Content-Length", "0") or 0)
        body = self.rfile.read(length) if length else b"{}"
        try:
            payload = json.loads(body.decode() or "{}")
            if url.path.startswith("/api/drafts/") and url.path.endswith("/revert"):
                draft_id = url.path[len("/api/drafts/"):-len("/revert")]
                self._draft_revert(draft_id, int(payload["n"]))
            else:
                self._send(404, "text/plain; charset=utf-8", b"not found")
        except Exception as error:
            self._send(500, "text/plain; charset=utf-8", f"{type(error).__name__}: {error}".encode())

    def _source(self, query: dict[str, list[str]]) -> None:
        asset_id = query.get("id", [""])[0]
        found = repo.find_asset(asset_id)
        if found is None:
            self._send(404, "text/plain; charset=utf-8", b"unknown asset")
            return
        pack, _ = found
        path = pack.source_path(asset_id)
        if not path.is_file():
            self._send(404, "text/plain; charset=utf-8", b"no SVG source")
            return
        self._send(200, "text/plain; charset=utf-8", path.read_bytes())

    def _drafts_list(self) -> None:
        from . import drafts
        self._json(drafts.all_drafts())

    def _draft_one(self, draft_id: str) -> None:
        from . import compose, drafts, lint as lint_mod
        if not drafts.exists(draft_id):
            self._send(404, "text/plain; charset=utf-8", b"unknown draft")
            return
        meta = drafts.load(draft_id)
        out: dict = {"meta": meta, "svg": None, "picture": None, "lint": [], "error": None}
        if meta.get("current"):
            try:
                out["svg"] = drafts.svg_text(draft_id)
                out["picture"] = drafts.compile_draft(draft_id)
                library = compose.Library(drafts.assets_for_preview())
                out["lint"] = lint_mod.lint(out["picture"], meta, library)
            except Exception as error:
                out["error"] = str(error)
        self._json(out)

    def _draft_revert(self, draft_id: str, n: int) -> None:
        from . import drafts
        meta = drafts.revert(draft_id, n)
        self._json(meta)

    def _guides(self) -> None:
        from . import guides
        self._json({"guides": guides.guides(), "overlaySvg": guides.overlay_svg()})

    def _static(self, path: str) -> None:
        relative = "index.html" if path in ("", "/") else path.lstrip("/")
        target = (WEB / relative).resolve()
        if WEB not in target.parents or not target.is_file():
            self._send(404, "text/plain; charset=utf-8", b"not found")
            return
        kind = mimetypes.guess_type(target.name)[0] or "application/octet-stream"
        if kind.startswith("text/") or kind.endswith("javascript"):
            kind += "; charset=utf-8"
        self._send(200, kind, target.read_bytes())

    def _json(self, value: object) -> None:
        self._send(200, "application/json; charset=utf-8", json.dumps(value).encode())

    def _send(self, status: int, kind: str, body: bytes) -> None:
        self.send_response(status)
        self.send_header("Content-Type", kind)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format: str, *args: object) -> None:  # quiet by default
        pass


def serve(port: int) -> None:
    mimetypes.add_type("text/javascript", ".js")
    server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
    print(f"iDL Studio on http://127.0.0.1:{port}  (Ctrl+C stops it)", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
