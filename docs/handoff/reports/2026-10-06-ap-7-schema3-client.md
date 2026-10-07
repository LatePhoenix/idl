# 2026-10-06 — AP-7 client: schema 3 on device

Branch `avatar/ap-7-schema3-persist`. This is pull request 1 of 2. The server and contract are the next PR. The row stays 🔨 until that PR merges. Do not tag `avatar-ap-7` yet.

## Acceptance

| Criterion | Result |
| --- | --- |
| v1 row becomes a schema 3 teardrop | Met. `Migration2To3Test` on the exported v2 schema. Every `BaseForm` lands on `base_teardrop`. Pixel keeps `eyefam_pixel` and `frame_pixel`. |
| Newer schema is not rewritten | Met. Schema 4 is left byte-for-byte. `prepareForWrite` still returns `NeedsAppUpdate`. The studio shows that message and does not save. |
| Retired bases resolve | Met. `base_blob`, `base_bot`, `base_ghost`, `base_critter`, and `base_orb` canonicalize to `base_teardrop`. |
| Studio still saves, with no base picker | Met. Save goes through `prepareForWrite`. Hair, a catalog expression, and `frame_pixel` stay when the studio does not edit them. |
| Demo friends are distinct teardrops | Met as a fixture. `DemoLooksTest`: eight recipes, each `base_teardrop`, distinct palette, hair, and top. |
| Widgets draw the vector base | Met. Widget goldens re-recorded. Availability and activity glyphs stay. |
| Upgrade keeps the avatar | Data path met by the Room test. The manual install-old-APK screenshot was not taken this session. |
| Server, `presence_view` v2, contract vectors, F-19, F-29 | Not this PR. |

## Commands

```
scripts/check.ps1
$env:ANDROID_SERIAL = "emulator-5554"; powershell -NoProfile -File scripts/check.ps1 -Device
```

Picture pipeline: **8 tests, OK**. Gradle: **261 tests, 0 failed, 1 skipped**. Lint **0 errors, 42 warnings**.

Device (`emulator-5554`, Pixel 9 AVD API 17): **22 tests, 0 failed**, timestamp 2026-10-07T02:18:38. The extra test against the previous 21 is `Migration2To3Test`. `--sql` waits for the server PR.

## Deviations

- In-app avatars draw the migrated teardrop, so a saved fox is no longer a fox. Demo hair and tops live on `DemoUser.recipe`. `PresenceView` is still v1, so the home screen shows the migrated accessories and colors, not those hair and top ids, until the server PR.
- A resume from the server still receives v1 `AvatarConfig` and rewrites it locally. Hair that existed only on the device is not on that payload yet.
- Species marks drop because their `compatibleBases` exclude `base_teardrop` (D-46). Helmet and blush stay.
- The manual upgrade screenshot is not in this report.

## Changed goldens

Every widget golden under `app/src/test/snapshots/widget/` except the two deletions redraws the teardrop. Light wallpaper uses a dark outline ring. Dark wallpaper uses a light ring. Availability dots, sleepy marks, and the VR visor stay.

Deleted, because the test no longer produces them: `ears_cat.png`, `ears_fox.png`.

`vector_dot.png`: the red test square sits on the teardrop, not the old blob.

## Commits

`8ce75c8` design note. `6f151ee` teardrop mapping and widget goldens. `96aae74` Room 3. `201eb1e` retired bases. `bd425d9` demo looks. This commit records the report and the review-queue row.
