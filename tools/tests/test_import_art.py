"""Interchange checks for tools/import_art.py (ST-2 PR 1)."""

import json
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import import_art

ROOT = Path(__file__).resolve().parents[2]
FIXTURES = ROOT / "tools" / "testdata" / "art_incoming"
PYTHON = sys.executable


def messages(issues) -> str:
    return "\n".join(str(issue) for issue in issues)


class ImportArtCheckTest(unittest.TestCase):
    def test_fixtures_pass(self):
        for folder in sorted(path for path in FIXTURES.iterdir() if path.is_dir()):
            issues, _warnings = import_art.check_drop(folder)
            self.assertEqual(messages(issues), "", folder.name)

    def test_hair_fixture_warns_over_12kb_and_still_passes(self):
        issues, warnings = import_art.check_drop(FIXTURES / "hair_short_tufted")
        self.assertEqual(issues, [])
        self.assertTrue(any("12 KB" in warning for warning in warnings))

    def test_square_thickness_is_its_side(self):
        self.assertEqual(import_art.region_thickness("M 0 0 L 40 0 L 40 40 L 0 40 Z", "nonzero"), 40.0)
        self.assertLess(import_art.region_thickness("M 0 0 L 20 0 L 20 80 L 0 80 Z", "nonzero"), 32.0)

    def test_schema_must_be_2(self):
        issues = self.mutate_svg("hat_cuffed_beanie", lambda text: text.replace('data-schema-version="2"', 'data-schema-version="1"', 1))
        self.assertIn("data-schema-version must be 2", messages(issues))

    def test_id_must_match_folder_and_meta(self):
        issues = self.mutate_svg(
            "hat_cuffed_beanie",
            lambda text: text.replace('data-id="hat_cuffed_beanie"', 'data-id="hat_other"', 1),
        )
        self.assertIn("must match", messages(issues))

    def test_id_must_be_snake_case_with_the_category_prefix(self):
        def edit(meta):
            meta["category"] = "hair"

        issues = self.mutate_meta("hat_cuffed_beanie", edit)
        self.assertIn("must start with hair_", messages(issues))

    def test_duplicate_part_id_fails(self):
        issues = self.mutate_svg(
            "hat_cuffed_beanie",
            lambda text: text.replace('data-part="secondary"', 'data-part="primary"', 1),
        )
        self.assertIn("primary: data-part is duplicated", messages(issues))

    def test_fill_style_and_class_are_rejected(self):
        for attribute in ('fill="#000000"', 'style="opacity:1"', 'class="nope"'):
            issues = self.mutate_svg(
                "glasses_round_thick",
                lambda text, attribute=attribute: text.replace(
                    'data-part="lens"', f'data-part="lens" {attribute}', 1
                ),
            )
            name = attribute.split("=")[0]
            self.assertIn(f"lens: must not set {name}", messages(issues))

    def test_image_element_is_rejected(self):
        issues = self.mutate_svg(
            "glasses_round_thick",
            lambda text: text.replace("</svg>", '<image data-part="pic" href="x.png"/></svg>'),
        )
        self.assertIn("unsupported element <image>", messages(issues))

    def test_missing_slot_is_rejected(self):
        issues = self.mutate_svg(
            "glasses_round_thick",
            lambda text: text.replace(' data-slot="glasses.lens"', "", 1),
        )
        self.assertIn("data-slot", messages(issues))

    def test_hat_on_the_wrong_band_fails(self):
        issues = self.mutate_svg(
            "hat_cuffed_beanie",
            lambda text: text.replace('data-part="primary" data-z="90"', 'data-part="primary" data-z="40"', 1),
        )
        self.assertIn("primary: headwear must use band 90, not 40", messages(issues))

    def test_hair_top_must_clip_by_the_hat_mask(self):
        issues = self.mutate_svg(
            "hair_short_tufted",
            lambda text: text.replace(' data-clip-by="occlude.hair_top:difference"', "", 1),
        )
        text = messages(issues)
        self.assertIn("top_primary", text)
        self.assertIn("occlude.hair_top:difference", text)

    def test_overflow_attribute_outside_the_hat_box_fails(self):
        extra = (
            '<path data-part="spike" data-z="90" data-slot="hat.secondary" '
            'data-allow-overflow="true" d="M 0 -80 L 80 -80 L 80 40 L 0 40 Z"/>'
        )
        issues = self.mutate_svg("hat_cuffed_beanie", lambda text: text.replace("</svg>", extra + "</svg>"))
        text = messages(issues)
        self.assertIn("spike: must not set data-allow-overflow", text)
        self.assertIn("spike: geometry is outside -16..1040", text)

    def test_second_line_art_part_on_a_band_fails(self):
        extra = (
            '<path data-part="lines_2" data-z="90" data-slot="outline" data-fill-rule="evenodd" '
            'd="M 120 120 L 220 120 L 220 220 L 120 220 Z"/>'
        )
        issues = self.mutate_svg("hat_cuffed_beanie", lambda text: text.replace("</svg>", extra + "</svg>"))
        self.assertIn("band 90 needs exactly one line-art part, found 2", messages(issues))

    def test_thin_region_fails(self):
        extra = (
            '<path data-part="rib" data-z="90" data-slot="hat.secondary" '
            'd="M 200 200 L 220 200 L 220 400 L 200 400 Z"/>'
        )
        issues = self.mutate_svg("hat_cuffed_beanie", lambda text: text.replace("</svg>", extra + "</svg>"))
        self.assertIn("rib: region is", messages(issues))
        self.assertIn("at least 32", messages(issues))

    def test_used_slot_must_be_declared(self):
        def edit(meta):
            del meta["colorSlots"]["outline"]

        issues = self.mutate_meta("hat_cuffed_beanie", edit)
        self.assertIn("slot outline is used in the SVG but missing from meta.colorSlots", messages(issues))

    def test_shadow_slot_may_be_omitted_from_meta(self):
        issues, _warnings = import_art.check_drop(FIXTURES / "hat_cuffed_beanie")
        self.assertEqual(issues, [])
        meta = json.loads((FIXTURES / "hat_cuffed_beanie" / "meta.json").read_text(encoding="utf-8"))
        self.assertNotIn("hat.shadow", meta["colorSlots"])

    def test_color_slot_must_be_hex(self):
        def edit(meta):
            meta["colorSlots"]["hat.primary"] = "blue"

        issues = self.mutate_meta("hat_cuffed_beanie", edit)
        self.assertIn("meta.colorSlots[hat.primary] must be a #RRGGBB hex color", messages(issues))

    def test_svg_over_16kb_fails(self):
        issues = self.mutate_svg(
            "hat_cuffed_beanie",
            lambda text: text + "<!--" + ("x" * 9000) + "-->",
        )
        self.assertIn("16 KB", messages(issues))

    def test_lens_opacity_above_0_35_fails_without_a_sunglasses_tag(self):
        issues = self.mutate_svg(
            "glasses_round_thick",
            lambda text: text.replace('data-opacity="0.35"', 'data-opacity="0.5"', 1),
        )
        self.assertIn("lens: glasses.lens opacity 0.5 is above 0.35", messages(issues))

    def test_headwear_must_publish_the_hair_mask(self):
        issues = self.mutate_svg(
            "hat_cuffed_beanie",
            lambda text: text.replace(' data-publish-mask="occlude.hair_top"', "", 1),
        )
        self.assertIn("headwear must publish mask occlude.hair_top", messages(issues))

    def test_cli_accepts_a_fixture_and_rejects_a_broken_drop(self):
        ok = subprocess.run(
            [PYTHON, str(ROOT / "tools" / "import_art.py"), "check", str(FIXTURES / "glasses_round_thick")],
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(ok.returncode, 0, ok.stderr)
        self.assertIn("Review reminder", ok.stdout)
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / "glasses_round_thick"
            shutil.copytree(FIXTURES / "glasses_round_thick", dest)
            svg = dest / "glasses_round_thick.svg"
            svg.write_text(svg.read_text(encoding="utf-8").replace('data-schema-version="2"', 'data-schema-version="9"', 1), encoding="utf-8")
            bad = subprocess.run(
                [PYTHON, str(ROOT / "tools" / "import_art.py"), "check", str(dest)],
                capture_output=True,
                text=True,
                check=False,
            )
        self.assertEqual(bad.returncode, 1)
        self.assertIn("data-schema-version must be 2", bad.stderr)

    def mutate_svg(self, fixture: str, edit):
        return self._mutate(fixture, edit_svg=edit)

    def mutate_meta(self, fixture: str, edit):
        return self._mutate(fixture, edit_meta=edit)

    def _mutate(self, fixture: str, edit_svg=None, edit_meta=None):
        with tempfile.TemporaryDirectory() as tmp:
            dest = Path(tmp) / fixture
            shutil.copytree(FIXTURES / fixture, dest)
            if edit_svg is not None:
                svg = dest / f"{fixture}.svg"
                svg.write_text(edit_svg(svg.read_text(encoding="utf-8")), encoding="utf-8")
            if edit_meta is not None:
                meta_path = dest / "meta.json"
                meta = json.loads(meta_path.read_text(encoding="utf-8"))
                edit_meta(meta)
                meta_path.write_text(json.dumps(meta), encoding="utf-8")
            issues, _warnings = import_art.check_drop(dest)
            return issues


class ImportArtApplyTest(unittest.TestCase):
    """Import into a temp pack. Never writes the real art, manifest, or catalog."""

    def setUp(self):
        self.tmp = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, self.tmp, True)
        shutil.copytree(ROOT / "art" / "emoji_core", self.tmp / "art" / "emoji_core")
        packs = self.tmp / "packs"
        shutil.copytree(ROOT / "app" / "src" / "main" / "assets" / "packs" / "emoji_core", packs / "emoji_core")
        shutil.copytree(ROOT / "app" / "src" / "main" / "assets" / "packs" / "core_proto", packs / "core_proto")
        incoming = self.tmp / "art" / "incoming"
        incoming.mkdir()
        self.paths = import_art.RepoPaths(
            art=self.tmp / "art",
            packs=packs,
            catalog_json=self.tmp / "contract" / "asset_catalog.json",
            catalog_sql=self.tmp / "seed" / "asset_catalog_seed.sql",
            incoming=incoming,
        )
        self.real_catalog = (ROOT / "contract" / "asset_catalog.json").read_bytes()
        self.real_manifest = (
            ROOT / "app" / "src" / "main" / "assets" / "packs" / "emoji_core" / "v2" / "manifest.json"
        ).read_bytes()

    def tearDown(self):
        self.assertEqual((ROOT / "contract" / "asset_catalog.json").read_bytes(), self.real_catalog)
        self.assertEqual(
            (ROOT / "app" / "src" / "main" / "assets" / "packs" / "emoji_core" / "v2" / "manifest.json").read_bytes(),
            self.real_manifest,
        )
        self.assertFalse((ROOT / "art" / "emoji_core" / "hat_cuffed_beanie.svg").exists())

    def test_imports_each_fixture(self):
        for fixture in ("glasses_round_thick", "hat_cuffed_beanie", "hair_short_tufted"):
            drop = self.copy_drop(fixture)
            summary = import_art.import_drop(drop, paths=self.paths)
            self.assertIn(f"{fixture} version 1", summary)
            self.assertIn("band ", summary)
            self.assertFalse(drop.exists())
            self.assertTrue((self.paths.incoming / ".imported" / f"{fixture}-v1").is_dir())
            entry = self.asset(fixture)
            self.assertEqual(entry["contentVersion"], 1)
            self.assertEqual(entry["render"]["file"], f"pictures/{fixture}.json")
            self.assertEqual(entry["collection"], "emoji_core")
            self.assertEqual(entry["license"], "proprietary-idl")
            self.assertEqual(entry["compatibleBases"], ["base_teardrop"])
            self.assertNotIn("provenance", entry)
            picture = json.loads(self.picture(fixture).read_text(encoding="utf-8"))
            self.assertEqual(picture["id"], fixture)
            self.assertEqual(picture["contentVersion"], 1)
            sidecar = json.loads(
                (self.paths.art / "emoji_core" / f"{fixture}.provenance.json").read_text(encoding="utf-8")
            )
            self.assertEqual(sidecar["provenance"]["tool"], "idl-art-studio")
            catalog = json.loads(self.paths.catalog_json.read_text(encoding="utf-8"))
            self.assertIn(fixture, {row["assetId"] for row in catalog["assets"]})
        self.assertIn("review:", summary)
        hat = self.asset("hat_cuffed_beanie")
        self.assertEqual(hat["colorSlots"]["hat.primary"], "#3A6EA5")
        self.assertEqual(
            hat["colorSlots"]["hat.shadow"],
            import_art.derive_hex("#3A6EA5", "shadow"),
        )
        self.assertNotIn("hat.highlight", hat["colorSlots"])
        hair = self.asset("hair_short_tufted")
        self.assertEqual(hair["colorSlots"]["hair.shadow"], import_art.derive_hex("#4A3426", "shadow"))
        self.assertEqual(hair["colorSlots"]["hair.highlight"], import_art.derive_hex("#4A3426", "highlight"))

    def test_same_version_refuses_and_bump_updates_in_place(self):
        first = self.copy_drop("glasses_round_thick")
        import_art.import_drop(first, paths=self.paths)
        second = self.copy_drop("glasses_round_thick")
        with self.assertRaises(import_art.DropRejected) as caught:
            import_art.import_drop(second, paths=self.paths)
        self.assertIn("not newer", str(caught.exception))
        self.assertTrue(second.is_dir())
        self.assertEqual(self.asset("glasses_round_thick")["contentVersion"], 1)

        manifest = json.loads(self.manifest_path().read_text(encoding="utf-8"))
        for asset in manifest["assets"]:
            if asset["id"] == "glasses_round_thick":
                asset["conflictsWith"] = ["glasses_round_wire"]
        self.manifest_path().write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")

        bumped = self.copy_drop("glasses_round_thick")
        svg = bumped / "glasses_round_thick.svg"
        svg.write_text(
            svg.read_text(encoding="utf-8").replace('data-content-version="1"', 'data-content-version="2"', 1),
            encoding="utf-8",
        )
        summary = import_art.import_drop(bumped, paths=self.paths)
        self.assertIn("version 2", summary)
        updated = self.asset("glasses_round_thick")
        self.assertEqual(updated["contentVersion"], 2)
        self.assertEqual(updated["conflictsWith"], ["glasses_round_wire"])
        picture = json.loads(self.picture("glasses_round_thick").read_text(encoding="utf-8"))
        self.assertEqual(picture["contentVersion"], 2)
        sidecar = json.loads(
            (self.paths.art / "emoji_core" / "glasses_round_thick.provenance.json").read_text(encoding="utf-8")
        )
        self.assertEqual(sidecar["contentVersion"], 2)
        self.assertTrue((self.paths.incoming / ".imported" / "glasses_round_thick-v2").is_dir())

    def test_rejected_drop_does_not_write(self):
        drop = self.copy_drop("glasses_round_thick")
        svg = drop / "glasses_round_thick.svg"
        svg.write_text(
            svg.read_text(encoding="utf-8").replace('data-schema-version="2"', 'data-schema-version="9"', 1),
            encoding="utf-8",
        )
        with self.assertRaises(import_art.DropRejected):
            import_art.import_drop(drop, paths=self.paths)
        self.assertFalse((self.paths.art / "emoji_core" / "glasses_round_thick.svg").exists())
        self.assertFalse(self.paths.catalog_json.exists())
        self.assertTrue(drop.is_dir())

    def test_cli_import_uses_default_paths(self):
        drop = self.copy_drop("glasses_round_thick")
        original = import_art.default_paths
        import_art.default_paths = lambda: self.paths
        try:
            code = import_art.main(["import_art.py", "import", str(drop), "--pack", "emoji_core"])
            usage = import_art.main(["import_art.py", "import"])
        finally:
            import_art.default_paths = original
        self.assertEqual(code, 0)
        self.assertEqual(usage, 2)
        self.assertEqual(self.asset("glasses_round_thick")["id"], "glasses_round_thick")

    def test_shadow_hex_matches_oklch_port(self):
        # Same constants as OklchTest `hat primary derives the importer shadow and highlight`.
        self.assertEqual(import_art.derive_hex("#3A6EA5", "shadow"), "#104B82")
        self.assertEqual(import_art.derive_hex("#3A6EA5", "highlight"), "#5D8CBF")

    def copy_drop(self, fixture: str) -> Path:
        dest = self.paths.incoming / fixture
        if dest.exists():
            shutil.rmtree(dest)
        shutil.copytree(FIXTURES / fixture, dest)
        return dest

    def asset(self, asset_id: str) -> dict:
        manifest = json.loads(self.manifest_path().read_text(encoding="utf-8"))
        return next(item for item in manifest["assets"] if item["id"] == asset_id)

    def manifest_path(self) -> Path:
        return self.paths.packs / "emoji_core" / "v2" / "manifest.json"

    def picture(self, asset_id: str) -> Path:
        return self.paths.packs / "emoji_core" / "v2" / "pictures" / f"{asset_id}.json"


if __name__ == "__main__":
    unittest.main()
