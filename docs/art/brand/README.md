# iDL brand marks

The launcher icon and wordmark chosen on 2026-10-05 (D-44). Both reuse the teardrop face from
D-42 as the dot of a lowercase "i" that leans 10 degrees.

| File | What it is |
|---|---|
| `launcher-icon.svg` | Launcher icon R1 on the 108×108 adaptive-icon canvas, with its background. Launchers show only the centre 72×72. |
| `wordmark-light.svg` | Wordmark W1, plum letters on a transparent background, for light surfaces. |
| `wordmark-dark.svg` | Wordmark W1, cream letters on plum, for dark surfaces. |
| `playstore-icon-512.png` | Google Play listing icon: 512×512, 32-bit PNG, full-bleed square (Play adds its own rounded mask). Rendered from the same drawables by `LauncherIconSnapshotTest`. |
| `gen_brand.py` | Generates all of the above, plus `ic_launcher_foreground.xml` and `ic_launcher_monochrome.xml` in `app/src/main/res/drawable/`. |

## Palette

| Name | Hex | Used for |
|---|---|---|
| Cream | `#FFF1E1` | Launcher background (`idl_launcher_bg`) |
| Coral | `#FF8E6E` | Face |
| Plum | `#3A2247` | Letters, stem, facial features |
| Blush | `#EE6355` | Cheeks |
| Letter cream | `#FFE9D2` | Letters on dark surfaces |

## Editing

Edit the geometry or colors in `gen_brand.py`, never the generated files. Then run:

```bash
python docs/art/brand/gen_brand.py
./gradlew recordRoborazziDebug
```

Then refresh the Play Store icon with `./gradlew testDebugUnitTest --tests '*LauncherIconSnapshotTest*' -Pidl.updateGolden=true`.
Without the flag, the test fails if `playstore-icon-512.png` no longer matches the drawables.

`LauncherIconSnapshotTest` checks the colors at fixed probe points that the script prints. If
the geometry moves, update those points in the test.

The monochrome layer (Android 13+ themed icons) uses only alpha, so the eyes and mouth are cut
out of the face with an even-odd fill instead of being drawn on top.

## Known limits

- On a very light wallpaper the cream tile has little edge contrast. The user accepted this when
  choosing R1 over the plum-tile option.
- There's no Play Store feature graphic (1024×500) yet.
- The wordmark letters are hand-drawn shapes, not a font. Text that sits next to them (such as the
  tagline) uses the app's normal font.
