"""Record tools/studio/tests/fixtures/hair_bob_48.png (run under Studio venv)."""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from engine import compose, render  # noqa: E402

OUT = Path(__file__).resolve().parent / "fixtures" / "hair_bob_48.png"


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    look = compose.Library().look(["hair_bob"], "neutral_face")
    pixels = render.render(look, 48, framing="head", frame="squircle", wall="light")
    import skia
    import numpy as np
    image = skia.Image.fromarray(np.ascontiguousarray(pixels), colorType=skia.kRGBA_8888_ColorType)
    render.save_png(image, OUT)
    print(OUT)


if __name__ == "__main__":
    main()
