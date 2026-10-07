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
    server_version = "iDLStudio/0"

    def do_GET(self) -> None:  # noqa: N802 (http.server naming)
        url = urlparse(self.path)
        try:
            if url.path == "/api/state":
                self._json(repo.state())
            elif url.path == "/api/source":
                self._source(parse_qs(url.query))
            else:
                self._static(url.path)
        except Exception as error:  # the UI shows the message; the server keeps running
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
