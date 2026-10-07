"""Lint: one passing and one failing fixture for representative rules."""

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

from engine import lint, paths  # noqa: E402

HAIR_META = {
    "id": "lint_hair",
    "category": "hair",
    "colorSlots": {
        "hair.primary": "#5B3A29",
        "hair.shadow": "#3E271B",
        "hair.highlight": "#8C7569",
    },
}

# Cap above hairline with shade + highlight — should pass style hairline/eyes/highlight.
PASS_HAIR = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"
     data-schema-version="2" data-id="lint_hair" data-content-version="1">
  <path data-part="top" data-z="70" data-slot="hair.primary" data-tags="hair_top"
        d="M 220 60 C 320 20 704 20 804 60 C 840 120 820 280 512 300 C 204 280 184 120 220 60 Z"/>
  <path data-part="shade" data-z="70" data-slot="hair.shadow" data-tags="hair_top"
        d="M 520 220 L 760 200 L 740 300 L 520 300 Z"/>
  <path data-part="highlight" data-z="70" data-slot="hair.highlight" data-tags="hair_top"
        d="M 300 80 L 460 70 L 440 160 L 300 140 Z"/>
</svg>
"""

# Front hair covering eyes and missing highlight.
FAIL_HAIR = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"
     data-schema-version="2" data-id="lint_hair_fail" data-content-version="1">
  <path data-part="top" data-z="70" data-slot="hair.primary" data-tags="hair_top"
        d="M 200 40 L 824 40 L 824 520 L 200 520 Z"/>
</svg>
"""

# Declared slot missing from part fill.
FAIL_VALIDATOR = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"
     data-schema-version="2" data-id="lint_hair_bad_slot" data-content-version="1">
  <path data-part="top" data-z="70" data-slot="no.such.slot"
        d="M 300 80 L 724 80 L 724 280 L 300 280 Z"/>
</svg>
"""

HAT_META = {
    "id": "lint_hat",
    "category": "head_accessory",
    "colorSlots": {
        "hat.primary": "#3A5A8C",
        "hat.secondary": "#2A4068",
        "hat.shadow": "#2B4369",
        "hat.highlight": "#6F8CB8",
        "outline": "#7A4E00",
    },
}

PASS_HAT = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"
     data-schema-version="2" data-id="lint_hat_pass" data-content-version="1">
  <path data-part="hair_mask" data-z="90" data-slot="hat.primary" data-opacity="0"
        data-publish-mask="occlude.hair_top"
        d="M 160 40 L 864 40 L 864 320 L 160 320 Z"/>
  <path data-part="crown" data-z="90" data-slot="hat.primary"
        d="M 180 50 C 280 10 744 10 844 50 C 880 140 860 320 512 320 C 164 320 144 140 180 50 Z"/>
  <path data-part="shade" data-z="90" data-slot="hat.shadow"
        d="M 520 200 L 800 180 L 780 320 L 520 320 Z"/>
</svg>
"""

FAIL_HAT_EYES = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024"
     data-schema-version="2" data-id="lint_hat_eyes" data-content-version="1">
  <path data-part="crown" data-z="90" data-slot="hat.primary"
        d="M 180 40 L 844 40 L 844 520 L 180 520 Z"/>
  <path data-part="shade" data-z="90" data-slot="hat.shadow"
        d="M 520 200 L 800 180 L 780 400 L 520 400 Z"/>
</svg>
"""


def _picture(svg: str) -> dict:
    # File stem must match data-id (asset_pipeline rule).
    import re
    match = re.search(r'data-id="([^"]+)"', svg)
    name = f"{match.group(1)}.svg" if match else "item.svg"
    return paths.pipeline.parse_svg(svg, name)


def _rules(issues: list[dict]) -> set[str]:
    return {i["rule"] for i in issues if i["level"] == "error"}


@unittest.skipUnless(HAS_SKIA, "skia-python not installed")
class LintTest(unittest.TestCase):
    def test_hair_pass_has_no_errors(self) -> None:
        issues = lint.style(_picture(PASS_HAIR), HAIR_META)
        self.assertEqual(_rules(issues), set(), msg=issues)

    def test_hair_fail_eyes_and_highlight(self) -> None:
        issues = lint.style(_picture(FAIL_HAIR), HAIR_META)
        rules = _rules(issues)
        self.assertIn("eyes", rules)
        self.assertIn("highlight", rules)

    def test_validator_fail_unknown_slot(self) -> None:
        issues = lint.validate(_picture(FAIL_VALIDATOR), HAIR_META)
        self.assertTrue(any(i["rule"] == "validator" and i["level"] == "error" for i in issues))

    def test_validator_pass_known_slots(self) -> None:
        issues = lint.validate(_picture(PASS_HAIR), HAIR_META)
        self.assertFalse(any(i["level"] == "error" for i in issues), msg=issues)

    def test_hat_pass_no_eye_errors(self) -> None:
        issues = lint.style(_picture(PASS_HAT), HAT_META)
        self.assertNotIn("eyes", _rules(issues), msg=issues)
        self.assertNotIn("lower_edge", _rules(issues), msg=issues)

    def test_hat_fail_covers_eyes(self) -> None:
        issues = lint.style(_picture(FAIL_HAT_EYES), HAT_META)
        self.assertIn("eyes", _rules(issues))


if __name__ == "__main__":
    unittest.main()
