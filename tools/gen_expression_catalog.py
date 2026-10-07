"""Generate the expression catalog from the pinned Unicode emoji-test file.

Stdlib only. `check` rebuilds the JSON and the markdown into a temp directory
and fails if either differs from the committed file.
"""

import json
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SCOPE_PATH = ROOT / "config" / "emoji_face_scope.json"
EMOJI_TEST = ROOT / "config" / "unicode" / "emoji-test-18.0.txt"
CATALOG_PATH = ROOT / "config" / "expression_catalog.json"
ASSETS_CATALOG = ROOT / "app" / "src" / "main" / "assets" / "expression_catalog.json"
DOC_PATH = ROOT / "docs" / "avatar" / "EXPRESSION_CATALOG.md"

SKIN = {"1F3FB", "1F3FC", "1F3FD", "1F3FE", "1F3FF"}
# Not an emotion on the teardrop. Skull is already in explicitHeadCodepoints.
EXTRA_EXCLUDED = {("2620", "FE0F")}

# eyes, brows, mouth, overlays, mood, intensity, items
# mood is a Mood wire name, or None. Priority 1 is assigned separately.
# The program text says 16 moods. Mood.entries has these 15. The catalog follows the enum.


def r(eyes, brows, mouth, overlays=(), mood=None, intensity="medium", items=()):
    return (eyes, brows, mouth, tuple(overlays), mood, intensity, tuple(items))


# One row per fully-qualified face that survives the scope filter.
RULES = {
    "grinning face": r("open", "neutral", "grin", intensity="high"),
    "grinning face with big eyes": r("big", "neutral", "grin", intensity="high"),
    "grinning face with smiling eyes": r("smiling", "none", "grin", mood="happy", intensity="high"),
    "beaming face with smiling eyes": r("smiling", "none", "beam", mood="happy", intensity="high"),
    "grinning squinting face": r("squint", "none", "grin", mood="happy", intensity="high"),
    "grinning face with sweat": r("open", "neutral", "grin", ("sweat",), intensity="high"),
    "rolling on the floor laughing": r("squint", "none", "grin", ("joy_tears", "rofl"), mood="happy", intensity="high"),
    "face with tears of joy": r("squint", "none", "grin", ("joy_tears",), mood="happy", intensity="high"),
    "slightly smiling face": r("open", "neutral", "slight_smile", mood="good", intensity="low"),
    "upside-down face": r("open", "neutral", "slight_smile", ("upside_down",), intensity="low"),
    "melting face": r("closed", "none", "slight_smile", ("melt",)),
    "cracking face": r("open", "neutral", "grin", ("crack",)),
    "winking face": r("wink", "neutral", "slight_smile", intensity="low"),
    "smiling face with smiling eyes": r("smiling", "none", "smile", mood="happy"),
    "smiling face with halo": r("smiling", "none", "smile", ("halo",)),
    "smiling face with hearts": r("smiling", "none", "smile", ("hearts",)),
    "smiling face with heart-eyes": r("heart", "none", "smile", intensity="high"),
    "star-struck": r("star", "none", "grin", mood="excited", intensity="high"),
    "face blowing a kiss": r("open", "neutral", "kiss", ("kiss_mark",)),
    "kissing face": r("open", "neutral", "kiss"),
    "smiling face": r("smiling", "none", "smile", mood="good"),
    "kissing face with closed eyes": r("closed", "none", "kiss"),
    "kissing face with smiling eyes": r("smiling", "none", "kiss"),
    "smiling face with tear": r("smiling", "none", "smile", ("tears",)),
    "face savoring food": r("closed", "none", "savor"),
    "face with tongue": r("open", "neutral", "tongue"),
    "winking face with tongue": r("wink", "neutral", "tongue"),
    "zany face": r("wide", "raised", "tongue", mood="chaotic", intensity="high"),
    "squinting face with tongue": r("squint", "none", "tongue"),
    "money-mouth face": r("smiling", "none", "money"),
    "smiling face with open hands": r("smiling", "none", "smile", ("hand",), mood="social"),
    "face with hand over mouth": r("open", "raised", "none", ("hand",)),
    "face with open eyes and hand over mouth": r("wide", "raised", "none", ("hand",)),
    "face with peeking eye": r("peek", "neutral", "none", ("hand",), intensity="low"),
    "shushing face": r("open", "neutral", "none", ("hand",), intensity="low"),
    "thinking face": r("open", "raised", "slight_frown", ("hand",), mood="focused"),
    "saluting face": r("open", "neutral", "slight_smile", ("hand",), intensity="low"),
    "zipper-mouth face": r("open", "neutral", "zipper", intensity="low"),
    "face with raised eyebrow": r("open", "raised", "neutral", intensity="low"),
    "neutral face": r("open", "neutral", "neutral", mood="neutral", intensity="low"),
    "expressionless face": r("open", "none", "neutral", intensity="low"),
    "face without mouth": r("open", "neutral", "none", intensity="low"),
    "dotted line face": r("dotted", "none", "none", intensity="low"),
    "face in clouds": r("closed", "none", "neutral", ("clouds",), intensity="low"),
    "smirking face": r("open", "raised", "smirk", intensity="low"),
    "unamused face": r("open", "angry", "slight_frown", intensity="low"),
    "face with rolling eyes": r("rolling", "none", "neutral"),
    "grimacing face": r("open", "worried", "grimace"),
    "face exhaling": r("closed", "none", "none", ("breath",), intensity="low"),
    "lying face": r("open", "neutral", "smile", ("long_nose",), intensity="low"),
    "shaking face": r("open", "neutral", "slight_smile", ("shake",)),
    "head shaking horizontally": r("open", "neutral", "slight_smile", ("shake_horizontal",), intensity="low"),
    "head shaking vertically": r("smiling", "none", "smile", ("shake_vertical",), intensity="low"),
    "relieved face": r("closed", "none", "slight_smile", intensity="low"),
    "pensive face": r("open", "worried", "slight_frown", mood="sad", intensity="low"),
    "sleepy face": r("open", "none", "neutral", ("zzz",), mood="sleepy", intensity="low"),
    "drooling face": r("closed", "none", "drool", ("drool",)),
    "sleeping face": r("closed", "none", "none", ("zzz",), mood="sleepy"),
    "face with bags under eyes": r("bags", "worried", "slight_frown", mood="low_energy"),
    "face with medical mask": r("open", "worried", "none", ("medical_mask",), mood="sick"),
    "face with thermometer": r("open", "worried", "slight_frown", ("thermometer",), mood="sick"),
    "face with head-bandage": r("open", "worried", "slight_frown", ("bandage",), mood="sick"),
    "nauseated face": r("squint", "worried", "wavy", mood="sick"),
    "face vomiting": r("squint", "worried", "open", ("vomit",), mood="sick", intensity="high"),
    "sneezing face": r("squint", "worried", "open", ("sneeze",), mood="sick"),
    "hot face": r("squint", "angry", "frown", ("heat",), intensity="high"),
    "cold face": r("squint", "worried", "grimace", ("cold",)),
    "woozy face": r("uneven", "none", "wavy"),
    "face with crossed-out eyes": r("crossed", "none", "open", intensity="high"),
    "face with spiral eyes": r("spiral", "none", "open", mood="overwhelmed", intensity="high"),
    "exploding head": r("wide", "raised", "open", ("explode",), mood="chaotic", intensity="high"),
    "cowboy hat face": r("squint", "none", "smile", items=("headwear_cowboy_hat",)),
    "partying face": r("smiling", "none", "grin", mood="social", intensity="high", items=("headwear_party",)),
    "disguised face": r(
        "open", "raised", "neutral", intensity="low",
        items=("eyewear_glasses", "nose_disguise", "facial_hair_mustache"),
    ),
    "smiling face with sunglasses": r("smiling", "none", "smile", items=("eyewear_sunglasses",)),
    "nerd face": r("open", "raised", "smile", mood="focused", items=("eyewear_glasses",)),
    "face with monocle": r("open", "raised", "slight_smile", mood="focused", intensity="low", items=("eyewear_monocle",)),
    "confused face": r("open", "worried", "diagonal", intensity="low"),
    "face with diagonal mouth": r("open", "neutral", "diagonal", intensity="low"),
    "worried face": r("open", "worried", "slight_frown", mood="anxious", intensity="low"),
    "slightly frowning face": r("open", "neutral", "slight_frown", intensity="low"),
    "frowning face": r("open", "worried", "frown"),
    "face with open mouth": r("wide", "raised", "open"),
    "hushed face": r("wide", "raised", "o", intensity="low"),
    "astonished face": r("wide", "raised", "open", intensity="high"),
    "flushed face": r("wide", "worried", "none", ("blush",)),
    "distorted face": r("wide", "worried", "wavy", ("distort",), intensity="high"),
    "pleading face": r("pleading", "worried", "slight_frown"),
    "face holding back tears": r("open", "worried", "slight_frown", ("holding_tears",), mood="sad"),
    "frowning face with open mouth": r("wide", "worried", "frown", intensity="high"),
    "anguished face": r("wide", "worried", "frown", mood="sad", intensity="high"),
    "fearful face": r("wide", "worried", "open", mood="anxious", intensity="high"),
    "anxious face with sweat": r("wide", "worried", "frown", ("sweat",), mood="anxious", intensity="high"),
    "sad but relieved face": r("closed", "worried", "slight_frown", ("sweat",), mood="sad"),
    "crying face": r("closed", "worried", "frown", ("tears",), mood="sad"),
    "loudly crying face": r("squint", "worried", "open", ("tears",), mood="sad", intensity="high"),
    "face screaming in fear": r("wide", "worried", "scream", mood="anxious", intensity="high"),
    "confounded face": r("squint", "angry", "grimace", mood="stressed"),
    "persevering face": r("squint", "worried", "grimace", mood="stressed"),
    "disappointed face": r("open", "worried", "slight_frown", mood="sad", intensity="low"),
    "downcast face with sweat": r("closed", "worried", "slight_frown", ("sweat",), mood="stressed"),
    "weary face": r("closed", "worried", "wavy", mood="tired"),
    "tired face": r("squint", "worried", "wavy", mood="tired"),
    "yawning face": r("closed", "none", "yawn", mood="tired"),
    "face with steam from nose": r("open", "angry", "frown", ("steam",), intensity="high"),
    "enraged face": r("wide", "angry", "frown", intensity="high"),
    "angry face": r("open", "angry", "frown"),
    "face with symbols on mouth": r("open", "angry", "symbols", intensity="high"),
    "smiling face with horns": r("smiling", "none", "smile", ("horns",)),
    "angry face with horns": r("open", "angry", "frown", ("horns",), intensity="high"),
}

# The automatic face for each Mood. Exactly one, and it must carry that mood.
PRIORITY = {
    "neutral": "neutral face",
    "good": "slightly smiling face",
    "happy": "smiling face with smiling eyes",
    "excited": "star-struck",
    "sleepy": "sleeping face",
    "tired": "yawning face",
    "low_energy": "face with bags under eyes",
    "stressed": "persevering face",
    "anxious": "anxious face with sweat",
    "sad": "crying face",
    "sick": "face with thermometer",
    "focused": "face with monocle",
    "chaotic": "zany face",
    "social": "partying face",
    "overwhelmed": "face with spiral eyes",
}

# Expression enum wire name -> catalog id. Saved data keeps these names.
ALIASES = {
    "neutral": "neutral_face",
    "happy": "smiling_face_with_smiling_eyes",
    "excited": "star_struck",
    "sleepy": "sleeping_face",
    "tired": "yawning_face",
    "sad": "crying_face",
    "anxious": "anxious_face_with_sweat",
    "angry": "angry_face",
    "sick": "face_with_thermometer",
    "focused": "face_with_monocle",
    "overwhelmed": "face_with_spiral_eyes",
    "social": "partying_face",
    "mischievous": "smirking_face",
    "afk": "sleeping_face",
    "dnd": "shushing_face",
}

# Priority-1 faces whose mood emoji is not that face, or whose alias is a judgment call.
REVIEW = {
    "face with bags under eyes": "low_energy's emoji is a battery, not a face",
    "zany face": "chaotic's emoji is a cyclone; zany is the closest face",
    "smirking face": "mischievous has no mood emoji; smirk rather than a tongue-wink",
    "shushing face": "dnd's emoji is a moon; shushing is the closest face",
    "sleeping face": "afk aliases the same face as sleepy",
}


def expression_id(name):
    out = []
    prev = False
    for ch in name.lower():
        if ch.isascii() and ch.isalnum():
            out.append(ch)
            prev = False
        elif not prev:
            out.append("_")
            prev = True
    return "".join(out).strip("_")


def load_scope():
    scope = json.loads(SCOPE_PATH.read_text(encoding="utf-8"))
    wanted = set(scope["includeSubgroups"]) | set(scope["decomposeSubgroups"])
    heads = {tuple(cp.split()) if " " in cp else (cp,) for cp in scope["explicitHeadCodepoints"]}
    # explicit heads are single code points. Match if any code point is listed.
    head_cps = set(scope["explicitHeadCodepoints"])
    return scope, wanted, head_cps


def parse_faces(wanted, head_cps):
    subgroup = None
    faces = []
    seen = set()
    for line in EMOJI_TEST.read_text(encoding="utf-8").splitlines():
        if line.startswith("# subgroup:"):
            subgroup = line.split(":", 1)[1].strip()
            continue
        if subgroup not in wanted or "; fully-qualified" not in line:
            continue
        left, right = line.split("#", 1)
        codepoints = tuple(left.split(";")[0].strip().split())
        if any(cp in SKIN for cp in codepoints):
            continue
        if any(cp in head_cps for cp in codepoints) or codepoints in EXTRA_EXCLUDED:
            continue
        name = right.strip().split(" ", 2)[2]
        if name in seen:
            raise SystemExit(f"duplicate face name: {name}")
        seen.add(name)
        faces.append({"codepoints": list(codepoints), "cldrName": name, "subgroup": subgroup})
    return faces


def build():
    scope, wanted, head_cps = load_scope()
    header = EMOJI_TEST.read_text(encoding="utf-8").splitlines()[1]
    if scope["emojiTestDate"] not in header:
        raise SystemExit(f"emoji-test header {header!r} does not contain {scope['emojiTestDate']}")
    faces = parse_faces(wanted, head_cps)
    names = {face["cldrName"] for face in faces}
    missing = names - RULES.keys()
    unused = RULES.keys() - names
    if missing or unused:
        raise SystemExit(f"rule mismatch\nmissing: {sorted(missing)}\nunused: {sorted(unused)}")
    moods = (
        "neutral", "good", "happy", "excited", "sleepy", "tired", "low_energy",
        "stressed", "anxious", "sad", "sick", "focused", "chaotic", "social", "overwhelmed",
    )
    priority_names = set(PRIORITY.values())
    if set(PRIORITY) != set(moods) or len(priority_names) != len(moods):
        raise SystemExit(f"priority map must cover {moods}")
    expressions = []
    for face in faces:
        eyes, brows, mouth, overlays, mood, intensity, items = RULES[face["cldrName"]]
        is_priority = face["cldrName"] in priority_names
        if is_priority and mood != next(k for k, v in PRIORITY.items() if v == face["cldrName"]):
            raise SystemExit(f"priority mood mismatch for {face['cldrName']}")
        decompose = face["subgroup"] in scope["decomposeSubgroups"]
        if items and not decompose:
            raise SystemExit(f"items on a non-decomposed face: {face['cldrName']}")
        if decompose and not items:
            raise SystemExit(f"decomposed face has no items: {face['cldrName']}")
        if face["subgroup"] == "face-hand" and "hand" not in overlays:
            raise SystemExit(f"face-hand entry missing the hand overlay: {face['cldrName']}")
        expressions.append({
            "codepoints": face["codepoints"],
            "cldrName": face["cldrName"],
            "subgroup": face["subgroup"],
            "expressionId": expression_id(face["cldrName"]),
            "mood": mood,
            "priority": 1 if is_priority else 2,
            "handOverlay": face["subgroup"] == "face-hand",
            "intensity": intensity,
            "parts": {"eyes": eyes, "brows": brows, "mouth": mouth},
            "overlays": list(overlays),
            "decomposedItems": list(items),
            "needsReview": face["cldrName"] in REVIEW,
        })
    ids = [row["expressionId"] for row in expressions]
    if len(ids) != len(set(ids)):
        raise SystemExit("duplicate expressionId")
    id_set = set(ids)
    for wire, target in ALIASES.items():
        if target not in id_set:
            raise SystemExit(f"alias {wire} -> {target} is not a catalog id")
    catalog = {
        "unicodeVersion": scope["unicodeVersion"],
        "emojiTestDate": scope["emojiTestDate"],
        "source": "config/unicode/emoji-test-18.0.txt",
        "aliases": ALIASES,
        "reviewNotes": {expression_id(name): note for name, note in REVIEW.items()},
        "expressions": expressions,
        "shapes": shape_counts(expressions),
    }
    return catalog


def shape_counts(expressions):
    buckets = {"eyes": {}, "brows": {}, "mouths": {}, "overlays": {}}
    for row in expressions:
        for key, bucket in (("eyes", "eyes"), ("brows", "brows"), ("mouth", "mouths")):
            name = row["parts"][key]
            buckets[bucket][name] = buckets[bucket].get(name, 0) + 1
        for overlay in row["overlays"]:
            buckets["overlays"][overlay] = buckets["overlays"].get(overlay, 0) + 1
    return {
        bucket: [{"id": name, "count": count} for name, count in sorted(counts.items(), key=lambda item: (-item[1], item[0]))]
        for bucket, counts in buckets.items()
    }


def emit_json(catalog):
    return json.dumps(catalog, indent=2, ensure_ascii=True) + "\n"


def emit_doc(catalog):
    shapes = catalog["shapes"]
    lines = [
        "# Expression catalog",
        "",
        "Generated by `tools/gen_expression_catalog.py` from the pinned Unicode emoji-test file.",
        "Do not edit this file by hand. It is the art plan for AP-6 and AP-13.",
        "",
        f"Unicode {catalog['unicodeVersion']}, emoji-test date {catalog['emojiTestDate']}.",
        f"{len(catalog['expressions'])} expressions.",
        f"{sum(len(shapes[k]) for k in ('eyes', 'brows', 'mouths', 'overlays'))} distinct shapes "
        f"({len(shapes['eyes'])} eyes, {len(shapes['brows'])} brows, {len(shapes['mouths'])} mouths, "
        f"{len(shapes['overlays'])} overlays).",
        "",
        "Priority 1 is the automatic face for a mood. Priority 2 may still name a mood.",
        "`handOverlay` is true for the face-hand subgroup. `decomposedItems` are AP-15 items",
        "for face-hat and face-glasses; the expression is the face underneath.",
        "",
        "## Needs a look",
        "",
        "| Catalog id | Why |",
        "| --- | --- |",
    ]
    for eid, note in catalog["reviewNotes"].items():
        lines.append(f"| `{eid}` | {note} |")
    lines += ["", "## Expression aliases", "", "Today's `Expression` wire names. Saved data does not change.", "", "| Wire name | Catalog id |", "| --- | --- |"]
    for wire, target in catalog["aliases"].items():
        lines.append(f"| `{wire}` | `{target}` |")
    lines += ["", "## Shapes", ""]
    for title, key in (("Eyes", "eyes"), ("Brows", "brows"), ("Mouths", "mouths"), ("Overlays", "overlays")):
        lines += [f"### {title}", "", "| Shape | Expressions |", "| --- | --- |"]
        for row in shapes[key]:
            lines.append(f"| `{row['id']}` | {row['count']} |")
        lines.append("")
    lines += [
        "## Expressions",
        "",
        "| Id | Mood | Pri | Eyes | Brows | Mouth | Overlays | Items |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for row in catalog["expressions"]:
        mood = row["mood"] or ""
        overlays = ", ".join(row["overlays"])
        items = ", ".join(row["decomposedItems"])
        parts = row["parts"]
        lines.append(
            f"| `{row['expressionId']}` | {mood} | {row['priority']} | `{parts['eyes']}` | "
            f"`{parts['brows']}` | `{parts['mouth']}` | {overlays} | {items} |"
        )
    lines.append("")
    return "\n".join(lines)


def write_outputs(directory):
    catalog = build()
    json_path = directory / "expression_catalog.json"
    doc_path = directory / "EXPRESSION_CATALOG.md"
    json_path.write_text(emit_json(catalog), encoding="utf-8", newline="\n")
    doc_path.write_text(emit_doc(catalog), encoding="utf-8", newline="\n")
    return catalog


def check():
    with tempfile.TemporaryDirectory() as tmp:
        write_outputs(Path(tmp))
        actual_json = (Path(tmp) / "expression_catalog.json").read_text(encoding="utf-8")
        actual_doc = (Path(tmp) / "EXPRESSION_CATALOG.md").read_text(encoding="utf-8")
    committed_json = CATALOG_PATH.read_text(encoding="utf-8")
    committed_assets = ASSETS_CATALOG.read_text(encoding="utf-8")
    committed_doc = DOC_PATH.read_text(encoding="utf-8")
    if actual_json != committed_json or actual_json != committed_assets or actual_doc != committed_doc:
        raise SystemExit("expression catalog is stale; run tools/gen_expression_catalog.py")


def main(argv):
    if len(argv) > 1 and argv[1] == "check":
        check()
        return
    catalog = write_outputs(ROOT / "config")
    # write_outputs puts the doc next to the json. Move it to docs.
    generated_doc = ROOT / "config" / "EXPRESSION_CATALOG.md"
    DOC_PATH.write_text(generated_doc.read_text(encoding="utf-8"), encoding="utf-8", newline="\n")
    generated_doc.unlink()
    ASSETS_CATALOG.write_text(CATALOG_PATH.read_text(encoding="utf-8"), encoding="utf-8", newline="\n")
    print(f"wrote {len(catalog['expressions'])} expressions")


if __name__ == "__main__":
    main(sys.argv)
