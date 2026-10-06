# Checkpoint report: wordmark on the splash screen (2026-10-06)

Branch `brand/splash-wordmark`, from `origin/main` `20306e8`. Author: Claude. Follows D-44.

## What was implemented

| Requirement | Status |
|---|---|
| W1 wordmark on the splash screen | ✅ Android 12+: `values-v31` sets `windowSplashScreenAnimatedIcon` to `splash_wordmark` on `idl_splash_bg`. Android 8–11: `Theme.Idl.Starting` uses `splash_background.xml` (wordmark centred at 288 dp) as the launch window |
| Light and dark mode | ✅ Light: plum letters on cream. Dark: cream letters on plum. Through `values-night` color resources |
| The app looks unchanged after launch | ✅ `MainActivity` calls `setTheme(R.style.Theme_Idl)` before `super.onCreate`, so the screens keep `idl_background` |
| Fits the Android 12+ splash safe area | ✅ The wordmark is about 166 × 84 dp inside the 192 dp circle of the 288 dp icon canvas |
| No new dependency | ✅ Platform splash attributes only. `androidx.core:core-splashscreen` isn't used (it would need approval) |
| Reproducible art | ✅ `gen_brand.py` generates `splash_wordmark.xml` from the same geometry as the wordmark SVGs |

## Files

- Created: `app/src/main/res/drawable/splash_wordmark.xml` (generated),
  `app/src/main/res/drawable/splash_background.xml`, `app/src/main/res/values-v31/themes.xml`,
  `app/src/test/java/app/idl/brand/SplashWordmarkTest.kt`,
  `app/src/test/snapshots/brand/splash_{light,dark}.png`,
  `docs/handoff/reports/screenshots/splash-wordmark-{light,dark,after}-api37.png`, this report
- Changed: `AndroidManifest.xml` (MainActivity theme), `MainActivity.kt` (`setTheme`),
  `values/colors.xml` and `values-night/colors.xml` (`idl_splash_bg`, `idl_wordmark_letters`,
  `idl_wordmark_line`), `values/themes.xml` (`Theme.Idl.Starting`),
  `docs/art/brand/gen_brand.py`, `docs/art/brand/README.md`, `master-plan.md` (§8)

## Commands and results

- `python docs/art/brand/gen_brand.py`: the launcher drawables are byte-identical. The only new output is `splash_wordmark.xml`.
- `./gradlew testDebugUnitTest --tests '*SplashWordmarkTest*' -Proborazzi.test.record=true`: 3/3 pass.
- `scripts/check.sh`: **227 tests, 0 failed, 1 skipped** (+3). Lint **0 errors, 42 warnings**.
  The first draft declared the vector at 288 dp and added a `VectorRaster` warning (over 200 dp).
  It's now declared at 192 dp with a 288-unit viewport. The splash view and the layer-list both
  size it to 288 dp, so the goldens didn't change.
- Device: `installDebug` on `emulator-5554` (API 37). A cold start shows the wordmark splash in
  light and in dark mode (`cmd uimode night yes`), then the onboarding screen on the normal app
  background. No crash in logcat.

## Deviations and notes

- The wordmark replaces the launcher icon as the Android 12+ splash icon, rather than going in
  the platform's bottom "branding image" slot. Google discourages that slot, and the centred
  wordmark is easier to read.
- Not checked on an Android 8–11 device. The launch window is covered by the `SplashWordmarkTest`
  render of `splash_background.xml`.

## Known limitations

- The emulator's debug cold start took about 6 s (`am start -W` TotalTime 6012 ms), so the splash
  stays up that long. That's app start-up time, not the splash, and I didn't compare it with a
  build without the splash.
- The onboarding screen still shows "iDL" as plain text, not the wordmark.

## Commits

See `git log --oneline origin/main..brand/splash-wordmark`.
