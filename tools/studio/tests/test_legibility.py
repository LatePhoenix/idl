"""Legibility: bare face ~1.0; a blob over the eyes scores below 0.6."""

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

from engine import compose, drafts, legibility  # noqa: E402

EYE_BLOB = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"
     data-schema-version="2" data-id="legibility_eye_blob" data-content-version="1">
  <path data-part="top" data-z="70" data-slot="hair.primary" data-tags="hair_top"
        d="M 200 300 L 824 300 L 824 520 L 200 520 Z"/>
  <path data-part="shade" data-z="70" data-slot="hair.shadow" data-tags="hair_top"
        d="M 500 400 L 780 400 L 780 520 L 500 520 Z"/>
  <path data-part="highlight" data-z="70" data-slot="hair.highlight" data-tags="hair_top"
        d="M 240 320 L 400 320 L 400 400 L 240 400 Z"/>
</svg>
"""

BARE_HAIR = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"
     data-schema-version="2" data-id="legibility_bare_cap" data-content-version="1">
  <path data-part="top" data-z="70" data-slot="hair.primary" data-tags="hair_top"
        d="M 240 50 C 340 10 684 10 784 50 C 820 110 800 280 512 290 C 224 280 204 110 240 50 Z"/>
  <path data-part="shade" data-z="70" data-slot="hair.shadow" data-tags="hair_top"
        d="M 520 200 L 740 190 L 720 290 L 520 290 Z"/>
  <path data-part="highlight" data-z="70" data-slot="hair.highlight" data-tags="hair_top"
        d="M 300 70 L 460 60 L 440 150 L 300 130 Z"/>
</svg>
"""


@unittest.skipUnless(HAS_SKIA, "skia-python not installed")
class LegibilityTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        root = Path(self.tmp.name)
        self.patches = [
            mock.patch.object(drafts, "DRAFTS", root / "drafts"),
            mock.patch.object(drafts, "RENDERS", root / "renders"),
        ]
        for p in self.patches:
            p.start()

    def tearDown(self) -> None:
        for p in self.patches:
            p.stop()
        self.tmp.cleanup()

    def _draft(self, draft_id: str, svg: str) -> None:
        drafts.new(draft_id, "hair", prompt="legibility")
        drafts.write(draft_id, svg.replace("data-id=\"legibility_eye_blob\"", f'data-id="{draft_id}"')
                     .replace("data-id=\"legibility_bare_cap\"", f'data-id="{draft_id}"'))

    def test_bare_cap_keeps_eyes_readable(self) -> None:
        self._draft("legibility_bare_cap", BARE_HAIR)
        library = compose.Library(drafts.assets_for_preview())
        result = legibility.measure(library, "legibility_bare_cap")
        for zone in ("left eye", "right eye", "mouth"):
            if zone in result["zones"]:
                self.assertGreaterEqual(result["zones"][zone], 0.95, msg=result)

    def test_blob_over_eyes_scores_below_threshold(self) -> None:
        self._draft("legibility_eye_blob", EYE_BLOB)
        library = compose.Library(drafts.assets_for_preview())
        result = legibility.measure(library, "legibility_eye_blob")
        eye_scores = [result["zones"][z] for z in ("left eye", "right eye") if z in result["zones"]]
        self.assertTrue(eye_scores, msg=result)
        self.assertLess(min(eye_scores), 0.6, msg=result)


if __name__ == "__main__":
    unittest.main()
