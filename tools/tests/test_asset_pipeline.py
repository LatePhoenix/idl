import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import asset_pipeline as pipeline

ROOT = Path(__file__).resolve().parents[2]
PICTURES = ROOT / "app" / "src" / "main" / "assets" / "packs" / "emoji_core" / "v2" / "pictures"


def svg(body: str, name: str = "box", schema: int = 1) -> str:
    return (
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" '
        f'data-schema-version="{schema}" data-id="{name}" data-content-version="1">\n'
        f"{body}\n</svg>\n"
    )


class PipelineTest(unittest.TestCase):
    def test_shipped_pictures_round_trip(self):
        for path in sorted(PICTURES.glob("*.json")):
            original = path.read_text(encoding="utf-8")
            picture = json.loads(original)
            built = pipeline.emit_picture(pipeline.parse_svg(pipeline.picture_to_svg(picture), path.with_suffix(".svg").name))
            self.assertEqual(original, built, path.name)

    def test_committed_sources_match_pictures(self):
        for path in sorted(PICTURES.glob("*.json")):
            source = ROOT / "art" / "emoji_core" / path.with_suffix(".svg").name
            built = pipeline.emit_picture(pipeline.parse_svg(source.read_text(encoding="utf-8"), source.name))
            self.assertEqual(path.read_text(encoding="utf-8"), built, path.name)

    def test_translated_rect_becomes_a_path(self):
        text = svg(
            '<g transform="translate(10 20)">'
            '<rect data-part="box" data-z="40" data-slot="face.primary" x="0" y="0" width="10" height="5"/>'
            "</g>"
        )
        picture = pipeline.parse_svg(text, "box.svg")
        self.assertEqual("M 10 20 L 20 20 L 20 25 L 10 25 Z", picture["parts"][0]["commands"])

    def test_arc_becomes_cubics_that_end_at_the_target(self):
        text = svg('<path data-part="arc" data-z="40" data-slot="face.primary" d="M 0 0 A 10 10 0 0 1 10 10"/>')
        commands = pipeline.parse_svg(text, "box.svg")["parts"][0]["commands"]
        self.assertNotIn("A ", commands)
        self.assertTrue(commands.endswith("10 10") or commands.endswith("10.0000 10.0000") or "10 10" in commands.split("Z")[0])
        self.assertIn("C ", commands)

    def test_text_is_rejected(self):
        text = svg('<text data-part="no" data-z="40" data-slot="face.primary">hi</text>')
        with self.assertRaises(pipeline.PipelineError):
            pipeline.parse_svg(text, "box.svg")

    def test_version_1_rejects_a_stroke(self):
        text = svg(
            '<path data-part="line" data-z="40" data-slot="face.primary" '
            'data-stroke-slot="outline" data-stroke-width="16" d="M 0 0 L 10 0"/>'
        )
        with self.assertRaises(pipeline.PipelineError):
            pipeline.parse_svg(text, "box.svg")

    def test_version_2_stroke_tags_and_mask_round_trip(self):
        picture = {
            "schemaVersion": 2,
            "id": "marked",
            "contentVersion": 1,
            "viewBox": 1024,
            "parts": [
                {
                    "id": "line",
                    "zBand": 40,
                    "fill": {"slot": "face.primary"},
                    "commands": "M 0 0 L 10 0",
                    "stroke": {"slot": "outline", "width": "16", "cap": "butt", "join": "bevel"},
                    "tags": ["hair_side"],
                    "clipBy": [{"mask": "occlude.hair_top", "mode": "difference"}],
                    "publishMask": "occlude.side",
                }
            ],
            "clipPaths": [],
        }
        built = pipeline.parse_svg(pipeline.picture_to_svg(picture), "marked.svg")
        self.assertEqual(picture["parts"][0]["stroke"], built["parts"][0]["stroke"])
        self.assertEqual(["hair_side"], built["parts"][0]["tags"])
        self.assertEqual(picture["parts"][0]["clipBy"], built["parts"][0]["clipBy"])
        self.assertEqual("occlude.side", built["parts"][0]["publishMask"])
        emitted = pipeline.emit_picture(built)
        self.assertIn('"cap": "butt"', emitted)
        self.assertIn('"join": "bevel"', emitted)

    def test_check_fails_when_a_picture_drifts(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            art = root / "art" / "emoji_core"
            art.mkdir(parents=True)
            pack = root / "app" / "src" / "main" / "assets" / "packs" / "emoji_core" / "v2" / "pictures"
            pack.mkdir(parents=True)
            source = svg('<path data-part="box" data-z="40" data-slot="face.primary" d="M 0 0 L 1 0"/>', name="box")
            (art / "box.svg").write_text(source, encoding="utf-8", newline="\n")
            picture = pipeline.parse_svg(source, "box.svg")
            (pack / "box.json").write_text(pipeline.emit_picture(picture).replace("M 0 0", "M 2 0"), encoding="utf-8", newline="\n")
            original_art, original_packs = pipeline.ART, pipeline.PACKS
            pipeline.ART, pipeline.PACKS = root / "art", root / "app" / "src" / "main" / "assets" / "packs"
            try:
                with self.assertRaises(pipeline.PipelineError):
                    pipeline.check()
            finally:
                pipeline.ART, pipeline.PACKS = original_art, original_packs


if __name__ == "__main__":
    unittest.main()
