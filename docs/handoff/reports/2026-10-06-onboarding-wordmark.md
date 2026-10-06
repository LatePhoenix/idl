# Checkpoint report: wordmark on the onboarding screen (2026-10-06)

Branch `brand/onboarding-wordmark`, from `origin/main` `e96084d`. Author: Claude. Follows D-44.

## What was implemented

| Requirement | Status |
|---|---|
| W1 wordmark replaces the "iDL" text on onboarding | ✅ `OnboardingScreen` shows `R.drawable.wordmark` at 64 dp tall, keeping its 136:71 aspect |
| Light and dark mode | ✅ Plum letters in light mode, cream in dark mode, from the `idl_wordmark_*` color resources |
| Screen readers | ✅ `contentDescription = "iDL"`, so TalkBack reads the same text as before |
| Reproducible art | ✅ `gen_brand.py` now builds the splash and the inline wordmark from one `wordmark_vector()`. `splash_wordmark.xml` only gained a comment line; its paths are unchanged |

## Files

- Created: `app/src/main/res/drawable/wordmark.xml` (generated),
  `app/src/test/java/app/idl/brand/InlineWordmarkTest.kt`,
  `app/src/test/snapshots/brand/wordmark_{light,dark}.png`,
  `docs/handoff/reports/screenshots/onboarding-wordmark-{light,dark}-api37.png`, this report
- Changed: `ui/onboarding/OnboardingScreen.kt`, `res/drawable/splash_wordmark.xml` (comment),
  `docs/art/brand/gen_brand.py`, `docs/art/brand/README.md`, `master-plan.md` (§8)

## Commands and results

- `python docs/art/brand/gen_brand.py`: launcher drawables byte-identical. The splash drawable
  differs only by its new comment line.
- `./gradlew testDebugUnitTest --tests '*InlineWordmarkTest*' -Proborazzi.test.record=true`: 3/3 pass.
- `scripts/check.sh`: **230 tests, 0 failed, 1 skipped** (+3). Lint **0 errors, 42 warnings**.
  The splash and launcher goldens verified unchanged.
- Device: `installDebug` on `emulator-5554` (API 37). The onboarding screen shows the wordmark in
  light mode and dark mode (`cmd uimode night yes`).

## Deviations and notes

- There's no Compose screenshot test of the whole onboarding screen. `ui-test-junit4` is only on
  the androidTest classpath, and adding it to the unit tests would be a new dependency. The
  drawable is covered by `InlineWordmarkTest`, and the screen by the device screenshots.

## Known limitations

- The onboarding hero row still shows the old procedural fox, ghost and robot avatars, not the
  teardrop face.

## Commits

See `git log --oneline origin/main..brand/onboarding-wordmark`.
