"""Draft create / write / revert."""

from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

try:
    import skia  # noqa: F401
    HAS_SKIA = True
except ImportError:
    HAS_SKIA = False

from engine import drafts  # noqa: E402

MINIMAL_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"
     data-schema-version="2" data-id="draft_test_item"
     data-content-version="1">
  <path data-part="top" data-z="70" data-slot="hair.primary" data-tags="hair_top"
        d="M 300 80 L 724 80 L 724 300 L 300 300 Z"/>
  <path data-part="shade" data-z="70" data-slot="hair.shadow" data-tags="hair_top"
        d="M 500 200 L 700 200 L 700 300 L 500 300 Z"/>
  <path data-part="highlight" data-z="70" data-slot="hair.highlight" data-tags="hair_top"
        d="M 340 100 L 480 100 L 480 180 L 340 180 Z"/>
</svg>
"""


@unittest.skipUnless(HAS_SKIA, "skia-python not installed")
class DraftsTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.drafts_dir = root / "drafts"
        self.renders_dir = root / "renders"
        self.patches = [
            mock.patch.object(drafts, "DRAFTS", self.drafts_dir),
            mock.patch.object(drafts, "RENDERS", self.renders_dir),
        ]
        for p in self.patches:
            p.start()

    def tearDown(self) -> None:
        for p in self.patches:
            p.stop()
        self.tmp.cleanup()

    def test_create_write_revert(self) -> None:
        meta = drafts.new("draft_test_item", "hair", prompt="unit test")
        self.assertEqual(meta["current"], 0)
        self.assertTrue(drafts.exists("draft_test_item"))

        meta = drafts.write("draft_test_item", MINIMAL_SVG, note="first")
        self.assertEqual(meta["current"], 1)
        self.assertIn("top", [p["id"] for p in drafts.compile_draft("draft_test_item")["parts"]])

        second = MINIMAL_SVG.replace("M 300 80", "M 310 80")
        meta = drafts.write("draft_test_item", second, note="second")
        self.assertEqual(meta["current"], 2)

        meta = drafts.revert("draft_test_item", 1)
        self.assertEqual(meta["current"], 3)
        self.assertIn("M 300 80", drafts.svg_text("draft_test_item"))


if __name__ == "__main__":
    unittest.main()
