# Track 0.1–0.2 checkpoint — widget presence, badges, D-31

Date: 2026-10-04. Branch: `fix/widget-presence-f01-f03`. Findings: F-01, F-02, F-03.

The PR #5 follow-up (F-02 for non-close friends, F-21, F-22) is at the bottom of this file. It supersedes the "stays CONTEXT" deviation and the 1×1 seed note below.

## Acceptance criteria

| Criterion | Status |
| --- | --- |
| Widget render uses `restingAvatar` + `VisiblePresence`, not the composed avatar | Met. `WidgetRenderInputs.from` |
| Badges come from resolved `avail_*` / `badge_*` layers and the manifest `glyph` | Met. `PlaceholderFrames` |
| Slot priority is the highest among sources that name the same asset | Met. `AvatarResolver.pick` |
| D-31 recorded | Met. `docs/IDL_DECISIONS.md` |
| End-to-end QuickState `"vr"` with signature glasses keeps the headset | Met. JVM test |
| Widget-path bitmaps differ for every `Availability` | Met. `WidgetRenderPathTest` on Pixel 9 emulator |
| `WidgetRenderInputs.from(model)` unit test; 1×1 and 2×2 device check | Met. See screenshots |
| `scripts/check.sh` passes | Met. See commands |
| `master-plan.md` updated | Met |

## What was implemented

- **F-03.** `WidgetModel` carries a friend `PresenceView` or the owner's `ResolvedPresence`. `WidgetRenderInputs.from` migrates only the resting avatar and passes `LegacyAvatarMigration.presence`. Glance `LocalSize` selects `COMPACT_WIDGET` (1×1), `STANDARD_WIDGET` (wide), or `LARGE_WIDGET` (square 2×2). The widget content description uses the resolver's `accessibilityDescription`.
- **F-01.** `PlaceholderFrames` adds `AVAILABILITY_BADGE` and `ACTIVITY_BADGE` only when the resolver kept those layers, and stores the manifest glyph. The painter draws that glyph. The v1 in-app path still uses `StatusGlyphs` (F-08, Track 0.6).
- **F-02 / D-31.** `pick` keeps the first resolved asset and raises its priority when a later source names the same id. An explicit `head_vr_headset` is also `activity:vr`, so the slot is `ACTIVITY` and signature glasses drop as `CONFLICT`. Identity is not rewritten; glasses return when the status ends.

## Files

Created:

- `app/src/main/java/app/idl/widget/WidgetRenderInputs.kt`
- `app/src/test/java/app/idl/widget/WidgetRenderInputsTest.kt`
- `docs/handoff/reports/2026-10-04-track-0-widget.md`
- `docs/handoff/reports/screenshots/widget-1x1-busy.png`
- `docs/handoff/reports/screenshots/widget-2x2-busy-vr.png`
- `docs/handoff/reports/screenshots/widget-glasses.png`
- `docs/handoff/reports/screenshots/widget-vr-over-glasses.png`
- `docs/handoff/reports/screenshots/widget-standard-resting.png`

Changed:

- `app/src/main/java/app/idl/domain/avatar/AvatarResolver.kt`
- `app/src/main/java/app/idl/domain/avatar/PlaceholderFrame.kt`
- `app/src/main/java/app/idl/avatar/AvatarRenderer.kt`
- `app/src/main/java/app/idl/widget/WidgetData.kt`
- `app/src/main/java/app/idl/widget/Widgets.kt`
- `app/src/main/res/xml/solo_friend_widget_info.xml`
- `app/src/main/res/xml/self_widget_info.xml`
- `app/src/test/java/app/idl/domain/avatar/AvatarResolverTest.kt`
- `app/src/test/java/app/idl/domain/avatar/PlaceholderFrameTest.kt`
- `app/src/androidTest/java/app/idl/RenderingAndCacheTest.kt`
- `docs/IDL_DECISIONS.md`
- `master-plan.md`

## Commands

```text
ANDROID_SERIAL=emulator-5554 "C:\Program Files\Git\bin\bash.exe" scripts/check.sh --device
```

- JVM: **149 tests, 0 failed, 1 skipped** (`SupabaseRestIT`; Docker was not part of this run)
- Lint: **0 errors, 39 warnings**
- Instrumented (`emulator-5554`, Pixel 9 AVD): **11 tests, 0 failed**, including `WidgetRenderPathTest.availabilityGlyphsDifferOnTheWidgetPath` and `vrHeadsetSurvivesSignatureGlassesOnTheWidgetPath`

After adding the screenshot log line, the same class was re-run with `am instrument` (2 tests, OK) and the PNGs were pulled from `/storage/emulated/0/Android/data/app.idl.debug/files/`.

## Deviations

- D-31 is the priority rule the audit specified, not a new priority level for every explicit accessory. An explicit accessory that no higher-priority source also names stays `CONTEXT` and still yields to a signature. The "In VR" quick state wins because it sets both the headset and `activity:vr`.
- `maxResizeHeight` went from 200dp to 320dp, and a 180dp square Glance breakpoint was added, so a 2×2 cell can select `LARGE_WIDGET`.
- `StatusGlyphs` remains for the v1 painter. Track 0.6 (F-08) deletes that duplication.
- The resonating corner mark is unchanged (F-05).

## Limitations

- The screenshots are the widget painter's bitmaps, rendered on the Pixel 9 emulator. A widget was not pinned on the launcher, so this is not a Pixel Launcher frame.
- The first composition for a non-compact cell shows the 1×1 bitmap until `LocalSize` re-resolves.
- Snapshot tests in CI are still F-14.

## Commits

```text
ca70087 Restore widget presence so badges and an explicit VR headset survive.
5aa7da0 Record the Track 0.1 commit hash in the checkpoint report.
```

PR: https://github.com/LatePhoenix/idl/pull/5

Range: `git log --oneline origin/main..HEAD`.

## PR #5 follow-up — F-02 remaining, F-21, F-22

`origin/main` (`f330a08`, feature ideas and D-35…D-38) is merged. D-31 stays ahead of D-32. Both sides of `master-plan.md` are kept.

| Criterion | Status |
| --- | --- |
| Explicit status accessories use `LayerPriority.STATUS` and beat a signature for every viewer | Met. Non-close friend: headset at STATUS, glasses `CONFLICT`, `activityType` null. Close friend: ACTIVITY |
| Mood override wins only while that mood is visible | Met. Hidden mood leaves the signature glasses |
| Default 2×2 (110×110, avatar drawn at 76 dp) uses `STANDARD_WIDGET` | Met. Sleepy keeps `body_blanket` and `prop_tea`. Square 180×180 is LARGE |
| Registry and `from()` failures stay inside the fallback | Met. `renderCatching`; a throwing registry returns `model` to null and reports the failure |
| Mo in VR on a pinned widget shows the headset | Met. `widget-vr-over-glasses-nonclose.png` |
| Ari sleepy at the default widget size shows blanket and tea | Met. `widget-2x2-default-sleepy.png` |
| `scripts/check.sh` and `--device` | Met. See commands |

### Commands

```text
"C:\Program Files\Git\bin\bash.exe" scripts/check.sh
ANDROID_SERIAL=emulator-5554 "C:\Program Files\Git\bin\bash.exe" scripts/check.sh --device
```

- JVM: **154 tests, 0 failed, 1 skipped** (`SupabaseRestIT`; Docker was not part of this run)
- Lint: **0 errors, 39 warnings**
- Instrumented (`emulator-5554`, Pixel 9 AVD, API 37): **12 tests, 0 failed**, including `WidgetRenderPathTest.nonCloseVrHeadsetAndSleepyBlanketAtTheDefaultWidgetSize`

### Device check

Demo account on the fake backend, Pixel 9 emulator. Pinned a friend widget for Mo (not on Mo's close list), then Settings → Alpha/debug → Demo friend Mo → In VR. The home-screen widget shows the visor, not the signature glasses, plus the text-only speech bubble. Ari's widget, after Simulate Sleepy, shows the blanket, the tea, and the sleepy marks.

The VR activity badge is not on Mo's widget. Activity category defaults to close friends, so a non-close viewer gets the headset (avatar) and the availability glyph, and does not get `activityType`. That is invariant 2, not a missed badge.

`widget-1x1-busy.png` is removed. `widget-2x2-default-busy.png` is the same busy availability re-rendered at `STANDARD_WIDGET`.

### Deviations

- `renderCatching` is the testable wrapper. `Widgets.render` calls it and logs `widget.render_failed` from the failure callback. An empty widget (no avatar) returns a null bitmap without logging.
- The square cell draws the avatar at 120 dp so its target is LARGE. Wide stays 92 dp.

### Limitations

- Wallpaper contrast is still hard-coded (F-07). Snapshot tests in CI are still F-14.
- The busy re-take is the widget painter's bitmap. The VR and sleepy shots are crops of the pinned launcher widgets.
