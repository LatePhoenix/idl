"""Standard sheet layout and hair_bob 48 px golden."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

try:
    import skia
    HAS_SKIA = True
except ImportError:
    HAS_SKIA = False
    skia = None  # type: ignore

from engine import compose, render  # noqa: E402

FIXTURES = Path(__file__).resolve().parent / "fixtures"
HAIR_48_GOLDEN = FIXTURES / "hair_bob_48.png"


@unittest.skipUnless(HAS_SKIA, "skia-python not installed")
class RenderTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.library = compose.Library()

    def test_standard_sheet_size(self) -> None:
        image = render.standard_sheet(self.library, "hair_bob")
        self.assertGreater(image.width(), 400)
        self.assertGreater(image.height(), 600)
        # Layout must be stable: same inputs → same pixel size.
        again = render.standard_sheet(self.library, "hair_bob")
        self.assertEqual((image.width(), image.height()), (again.width(), again.height()))

    def test_hair_bob_48_matches_golden(self) -> None:
        look = self.library.look(["hair_bob"], "neutral_face")
        pixels = render.render(look, 48, framing="head", frame="squircle", wall="light")
        if not HAIR_48_GOLDEN.is_file():
            self.fail(f"missing golden {HAIR_48_GOLDEN}; record with tests/record_hair_bob_48.py")
        data = skia.Data.MakeWithCopy(HAIR_48_GOLDEN.read_bytes())
        golden_img = skia.Image.MakeFromEncoded(data)
        golden = np.array(golden_img.toarray(colorType=skia.kRGBA_8888_ColorType))
        self.assertEqual(pixels.shape, golden.shape)
        diff = np.abs(pixels.astype(np.int16) - golden.astype(np.int16)).mean()
        self.assertLess(diff, 8.0, msg=f"mean abs channel diff {diff}")


if __name__ == "__main__":
    unittest.main()
