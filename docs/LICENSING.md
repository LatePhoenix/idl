# Licensing — bootstrap emoji art

**Status:** D-42 (2026-10-05) makes all avatar art original, so no Noto import is planned. These rules
stay only in case that decision changes. **No Noto files are in this repository.**  
`THIRD_PARTY_NOTICES.md` is created at the first import, not before. An empty notice file would claim attribution we have not copied.

## 1. Allowed source

Preferred bootstrap art: the official [googlefonts/noto-emoji](https://github.com/googlefonts/noto-emoji) repository, **svg/** only.

Pinned commit, inspected 2026-10-04:

| | |
| --- | --- |
| Commit | `e20cbc2bbec1926686be9f9bee7d1d2cfa1fea0e` |
| Date | 2026-09-24 |
| Subject | Merge pull request #568, Emoji 18 |
| Tree | `bffd3c9a184215699577064573a22881864d05e5` |

Do not track `main`. A later import may move the pin; the manifest records the sha actually copied.

## 2. License by directory

The repository root is not the license of every file. Checked against the upstream README and the files it points at:

| Path | License | Use |
| --- | --- | --- |
| `svg/LICENSE` | Apache-2.0. Copyright 2013 Google, Inc. | **The only art directory we may import.** |
| `fonts/` and the color-font sources | SIL Open Font License 1.1 | **Do not import.** OFL fonts cannot be relicensed, and reserved names apply. We do not ship a font. |
| `third_party/region-flags` | Public domain or exempt, per upstream | **Out of scope.** Flags are not avatars. |
| PNG builds | Apache-2.0 where they are Noto image resources, with the flag exception | Not the source of truth. The pipeline starts from SVG. |

Apache-2.0 on `svg/` requires that we keep the copyright notice and the license text with the distribution. Modified art must carry a notice that it was modified. Attribution belongs in `THIRD_PARTY_NOTICES.md` and in each manifest row (`license`, `sourceUrl`, `sourceCommit`, `modified`). It does not belong on the widget.

A 2021 upstream comment said SVGs embedded in the OFL font could be treated as OFL for a printed flyer. That comment is not a grant we rely on. We import the Apache-licensed `svg/` files, and we keep Apache notices.

## 3. Forbidden sources

- Gboard or any other proprietary APK, screenshot, or extraction
- Third-party emoji galleries
- The device emoji font at runtime
- Any file whose directory license was not opened and recorded

## 4. Brand

Do not use Google, Android, Gboard, or Noto as a product name, asset id prefix, or endorsement. "Noto" is a reserved name in the font metadata. Internal provenance may say `source: noto-emoji` in the manifest. User-visible strings say nothing about Google.

Original house-style art uses `license: proprietary-idl` (the current manifest default) and `modified: false`, with empty `sourceUrl` / `sourceCommit`.

## 5. Replacement

Asset ids and recipes must not contain "noto" or a Unicode code point. `hair_bob` can be redrawn. `emoji_u1f600` cannot, because the id would freeze the upstream glyph. When the house style replaces a picture, bump `contentVersion`, set `license` and clear or replace `sourceUrl`. Saved recipes keep the same id.

## 6. Unicode data

`config/emoji_face_scope.json` is an original filter list. It names subgroups and code points. It does not copy `emoji-test.txt`. Unicode's terms cover that data file if a later tool vendors a snapshot: https://www.unicode.org/terms_of_use.html and https://www.unicode.org/license.txt. Vendoring the full test file, if we do it, gets its own notice and a pinned URL (`/emoji/18.0/`, not `/emoji/latest/`).
