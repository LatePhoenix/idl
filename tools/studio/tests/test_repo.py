"""Studio engine tests. Run: python -m unittest discover -s tools/studio/tests"""

from __future__ import annotations

import io
import json
import sys
import threading
import unittest
import urllib.request
from contextlib import redirect_stdout
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

import studio  # noqa: E402
from engine import repo, server  # noqa: E402


class RepoTest(unittest.TestCase):
    def test_every_vector_asset_has_a_picture_and_a_source(self) -> None:
        for pack in repo.load_packs():
            for asset in pack.assets:
                if asset.get("render", {}).get("type") != "vector":
                    continue
                with self.subTest(asset=asset["id"]):
                    self.assertIn(asset["id"], pack.pictures)
                    self.assertTrue(pack.source_path(asset["id"]).is_file())

    def test_state_has_a_vector_base_and_the_catalog(self) -> None:
        state = repo.state()
        bases = [a for p in state["packs"] for a in p["assets"] if a["category"] == "base" and a["picture"]]
        self.assertTrue(bases)
        self.assertGreaterEqual(len(state["catalog"]["expressions"]), 100)

    def test_show_prints_parts(self) -> None:
        out = io.StringIO()
        with redirect_stdout(out):
            self.assertEqual(studio.main(["show", "base_teardrop"]), 0)
        self.assertIn("band  40", out.getvalue())

    def test_show_unknown_asset_fails(self) -> None:
        with redirect_stdout(io.StringIO()):
            self.assertEqual(studio.main(["show", "no_such_asset"]), 1)


class ServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.httpd = ThreadingHTTPServer(("127.0.0.1", 0), server.Handler)
        cls.base = f"http://127.0.0.1:{cls.httpd.server_address[1]}"
        threading.Thread(target=cls.httpd.serve_forever, daemon=True).start()

    @classmethod
    def tearDownClass(cls) -> None:
        cls.httpd.shutdown()
        cls.httpd.server_close()

    def get(self, path: str) -> tuple[int, bytes]:
        try:
            with urllib.request.urlopen(self.base + path) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as error:
            return error.code, error.read()

    def test_state_and_index(self) -> None:
        status, body = self.get("/api/state")
        self.assertEqual(status, 200)
        self.assertIn("packs", json.loads(body))
        status, body = self.get("/")
        self.assertEqual(status, 200)
        self.assertIn(b"iDL Art Studio", body)

    def test_source_is_served(self) -> None:
        status, body = self.get("/api/source?id=hair_bob")
        self.assertEqual(status, 200)
        self.assertIn(b'data-id="hair_bob"', body)

    def test_paths_outside_web_are_refused(self) -> None:
        for path in ("/../engine/repo.py", "/%2e%2e/studio.py", "/..%2f..%2fAGENTS.md"):
            with self.subTest(path=path):
                status, _ = self.get(path)
                self.assertEqual(status, 404)


if __name__ == "__main__":
    unittest.main()
