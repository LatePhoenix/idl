"""Head guides must match the shipped teardrop (style guide §2)."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

try:
    import skia  # noqa: F401
    HAS_SKIA = True
except ImportError:
    HAS_SKIA = False

from engine import guides  # noqa: E402


@unittest.skipUnless(HAS_SKIA, "skia-python not installed")
class GuidesTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        guides.guides.cache_clear()
        cls.g = guides.guides()

    def test_crown_and_chin_on_centreline(self) -> None:
        self.assertEqual(self.g["crown"]["at"][0], 512)
        self.assertEqual(self.g["chin"]["at"][0], 512)
        self.assertLessEqual(self.g["crown"]["at"][1], 120)
        self.assertGreaterEqual(self.g["chin"]["at"][1], 850)

    def test_hairline_max_is_style_guide_330(self) -> None:
        self.assertEqual(self.g["hairline_max"]["y"], 330)

    def test_head_box_matches_teardrop_silhouette(self) -> None:
        box = self.g["head_outline"]["box"]
        self.assertIsNotNone(box)
        l, t, r, b = box
        self.assertLessEqual(l, 140)
        self.assertGreaterEqual(r, 880)
        self.assertLessEqual(t, 100)
        self.assertGreaterEqual(b, 880)

    def test_eye_zone_around_eye_line(self) -> None:
        box = self.g["eye_zone"]["box"]
        self.assertIsNotNone(box)
        _l, t, _r, b = box
        self.assertLess(t, 430)
        self.assertGreater(b, 430)

    def test_overlay_svg_includes_head(self) -> None:
        svg = guides.overlay_svg()
        self.assertIn("<svg", svg)
        self.assertIn(self.g["head_outline"]["d"][:20], svg)


if __name__ == "__main__":
    unittest.main()
