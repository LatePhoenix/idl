# Checkpoint report: launcher icon R1 and wordmark W1 (2026-10-05)

Branch `brand/launcher-icon`, from `origin/main` `037fdc1`. Author: Claude. Decision: D-44.

## What was implemented

The user wasn't happy with Gemini's icon ideas, so Claude drew two rounds of SVG concepts in
chat: six directions, then four "Little i" variations, then four launcher refinements and two
wordmarks. The user picked launcher **R1** (a lowercase "i" leaning 10°, whose dot is the
winking teardrop face) and wordmark **W1** (the same "i" followed by rounded "D" and "L").

| Acceptance (from the conversation) | Status |
|---|---|
| R1 is the app's launcher icon | ✅ `ic_launcher_foreground.xml` and `idl_launcher_bg` (#FFF1E1) |
| Works as an Android 13+ themed icon | ✅ new `ic_launcher_monochrome.xml` with the features cut out (even-odd). Before this, the monochrome layer reused the foreground, so the face showed as a blank blob |
| W1 wordmark available as files | ✅ `docs/art/brand/wordmark-light.svg`, `wordmark-dark.svg` |
| Reproducible art | ✅ `docs/art/brand/gen_brand.py` generates every file above from one geometry source |
| Snapshot test for the visual change (AGENTS.md) | ✅ `LauncherIconSnapshotTest`, 3 tests, 4 goldens in `app/src/test/snapshots/brand/` |

## Files

- Changed: `app/src/main/res/drawable/ic_launcher_foreground.xml`,
  `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` (monochrome → new drawable),
  `app/src/main/res/values/colors.xml` (`idl_launcher_bg`), `docs/IDL_DECISIONS.md` (D-44),
  `master-plan.md` (§5, §8)
- Created: `app/src/main/res/drawable/ic_launcher_monochrome.xml`,
  `app/src/test/java/app/idl/brand/LauncherIconSnapshotTest.kt`,
  `app/src/test/snapshots/brand/{launcher_48,launcher_192,launcher_foreground_432,launcher_themed_192}.png`,
  `docs/art/brand/{README.md,gen_brand.py,launcher-icon.svg,wordmark-light.svg,wordmark-dark.svg}`,
  this report

## Commands and results

- `python docs/art/brand/gen_brand.py`: regenerates the drawables and SVGs. Output is deterministic.
- `./gradlew testDebugUnitTest --tests '*LauncherIconSnapshotTest*' -Proborazzi.test.record=true`: 3/3 pass, goldens recorded.
- `scripts/check.sh`: **219 tests, 0 failed, 1 skipped** (216 before, plus 3 new). Lint **0 errors,
  42 warnings**, the same as the baseline in master-plan §1.2. Debug build OK.
- The first draft of the monochrome cut-outs used sampled polygons and added a lint `VectorPath`
  warning (3,013 characters). They now use Bézier offset curves, and the path is 780 characters, under lint's 800 limit.
- Not run: `--device`, `--sql` (no device or server code changed).

## Deviations and notes

- No spec in `docs/handoff/`. This was a direct user request made in the conversation, and it isn't a §4.0 item.
- Goldens were checked by eye: the 192 px composite matches the approved R1, and the themed
  preview shows the eyes and mouth as holes.
- A Roborazzi gotcha: deleting a golden isn't enough. `app/build/intermediates/roborazzi/` keeps
  a copy and recording copies it back. Delete both.

## Known limitations and open questions

- On very light wallpapers the cream tile has little edge contrast. The user picked R1 knowing
  this (the plum-tile R4 was the alternative).
- Not checked on a device launcher yet (no emulator run). Robolectric renders the vector layers,
  not a real launcher's mask.
- Still missing for the Play Store: a 512×512 icon PNG and a feature graphic.
- The app UI doesn't use the wordmark yet (the splash screen and the app bar could).
- `docs/art/app-icon-prompts.md` (the Gemini prompts) exists only as an untracked file in the main
  checkout. It's not part of this branch.

## Commits

```
b5ef4d7 Replace the placeholder launcher icon with the R1 leaning-i face.
(+ the docs commit that adds this report, D-44 and the master-plan entries)
```
