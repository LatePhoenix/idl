"""Geometry helpers. Skipped when skia is not installed."""

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

from engine import paths  # noqa: E402


@unittest.skipUnless(HAS_SKIA, "skia-python not installed")
class PathsTest(unittest.TestCase):
    def test_union_covers_both_rects(self) -> None:
        a = "M 0 0 L 40 0 L 40 40 L 0 40 Z"
        b = "M 20 20 L 60 20 L 60 60 L 20 60 Z"
        u = paths.union(a, b)
        self.assertTrue(paths.contains(u, 10, 10))
        self.assertTrue(paths.contains(u, 50, 50))
        self.assertGreater(paths.area(u), paths.area(a))

    def test_subtract_removes_overlap(self) -> None:
        a = "M 0 0 L 40 0 L 40 40 L 0 40 Z"
        b = "M 0 0 L 20 0 L 20 40 L 0 40 Z"
        d = paths.subtract(a, b)
        self.assertFalse(paths.contains(d, 10, 20))
        self.assertTrue(paths.contains(d, 30, 20))

    def test_offset_grows_bounds(self) -> None:
        square = "M 100 100 L 200 100 L 200 200 L 100 200 Z"
        grown = paths.offset(square, 10)
        l, t, r, b = paths.bounds(grown)
        self.assertLess(l, 100)
        self.assertGreater(r, 200)

    def test_mirror_swaps_x_around_axis(self) -> None:
        left = "M 100 100 L 200 100 L 200 200 L 100 200 Z"
        right = paths.mirror(left, 512)
        # x' = 1024 - x → square lands at 824..924
        self.assertTrue(paths.contains(right, 874, 150))
        self.assertFalse(paths.contains(right, 150, 150))

    def test_to_d_parse_round_trip_preserves_fill(self) -> None:
        d = "M 10 10 L 90 10 L 90 90 L 10 90 Z"
        again = paths.to_d(paths.parse(d))
        self.assertAlmostEqual(paths.area(d), paths.area(again), delta=1.0)
        self.assertTrue(paths.contains(again, 50, 50))


if __name__ == "__main__":
    unittest.main()
