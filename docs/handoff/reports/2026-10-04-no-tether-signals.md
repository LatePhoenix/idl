# No per-friend tether signals — checkpoint

Date: 2026-10-04. Branch: `charge/no-tether-signals-f05` from `origin/main` (`94295ce`).

## Acceptance

| Criterion | Status |
| --- | --- |
| Remove `AvatarBadges.resonating`, `WidgetModel.resonating`, and every caller (renderer, cache key, widget data, widget UI) | Met |
| Widget content descriptions no longer say ", resonating"; render keys do not include it | Met |
| Status Deck shows balance plus "Earning" / "Not earning" only. No tether count, no hourly rate. Banner stays compact | Met |
| Debug toggles that set `hasRemoteWidgetInstalled` only when the fake backend is active | Met (`BuildConfig.DEBUG && fake != null`) |
| First four D-30b guardrails added to `AGENTS.md` invariants | Met (invariants 10–13) |
| Unit test: no widget model or content description carries a per-friend tether indicator | Met (`WidgetTetherSignalTest`) |
| Friend widget render is identical whether or not that friend has pinned you | Met (`pinningYouDoesNotChangeTheFriendWidget`, bitmaps `sameAs`) |
| Compose test: quick states stay the first actionable row | Met (`quickStatesStayTheFirstActionableRow`) |
| Roborazzi goldens updated if widget images change | Not applicable on this branch. See deviations |
| Server ledger, spending, paid Charge | Out of scope (C.3, C.4, C.6) |

## What changed

The friend widget no longer paints a resonance dot, and `WidgetData.friend` no longer reads that friend's tether row. `RenderCache.key` hashes size, simplify threshold, availability, activity, and the avatar config. The Glance cache key is `"${resolved.renderKey}|$AVATAR_PX"`.

`ChargeBanner` is one row: the word Charge, the balance, and "Earning" or "Not earning". It is not clickable. `hourlyRate` is used only to choose that word. The content description is `"$balance Charge, $earning"`.

`FriendTether` and `ChargeEngine` are unchanged. The engine still needs the tether rows to accrue. That state does not reach widget pixels, content descriptions, or the Status Deck.

## Screenshot

Status Deck banner on Pixel_9 (AVD), emulator-5554, balance 40, earning:

![Charge banner](2026-10-04-charge-banner.png)

## Files

Changed:

- `AGENTS.md`
- `master-plan.md`
- `app/src/main/java/app/idl/avatar/AvatarRenderer.kt`
- `app/src/main/java/app/idl/avatar/RenderCache.kt`
- `app/src/main/java/app/idl/widget/WidgetData.kt`
- `app/src/main/java/app/idl/widget/Widgets.kt`
- `app/src/main/java/app/idl/ui/status/StatusDeckScreen.kt`
- `app/src/main/java/app/idl/ui/settings/SettingsScreen.kt`
- `app/src/androidTest/java/app/idl/RenderingAndCacheTest.kt`
- `app/src/androidTest/java/app/idl/StatusDeckTest.kt`

Created:

- `app/src/test/java/app/idl/widget/WidgetTetherSignalTest.kt`
- `docs/handoff/reports/2026-10-04-no-tether-signals.md`
- `docs/handoff/reports/2026-10-04-charge-banner.png`

## Commands

```
ANDROID_SERIAL=emulator-5554 scripts/check.sh --device
```

From Git Bash. This runs the unit/lint/debug section of `scripts/check.sh` and then `connectedDebugAndroidTest`.

- JVM: 161 tests, 0 failed, 1 skipped
- lint: 0 errors, 39 warnings
- Device (Pixel_9 AVD, emulator-5554): 15 tests, 0 failed, 0 skipped
- All checks passed

The screen was awake (`KEYCODE_WAKEUP`, `svc power stayon true`). An earlier run with the display asleep failed every Compose test with "No compose hierarchies found".

## Deviations

- F-06 is 🟡. The first four guardrails are invariants, the banner is compact, and the quick-state test is in. The in-app explanation of the 24 h cap is C.5 and was not built. Cosmetics-only spending is C.4.
- F-05's SQL check (no RPC returns pin state) is C.3. The server has no pin field yet, so there is nothing to leak. Not added here.
- Roborazzi goldens were not updated. They are not on `origin/main`. F-14 lives on `test/roborazzi-f14` (PR #11) and is unmerged. This branch has no `app/src/test/snapshots` and `scripts/check.sh` does not run `verifyRoborazziDebug`. Removing the dot would change those goldens once F-14 is on main.
- Instrumented tests use camelCase names. D8 rejects space characters in `SimpleName` before DEX version 040, so backtick names with spaces do not install.
- Debug toggles stay inside `BuildConfig.DEBUG && fake != null`. That is the existing guard. A release build never shows them, including a release demo.

## Known limitations

- The ledger is still client-authoritative (F-04, C.3). Spending and paid Charge were not started.
- `EconomyRepository` still logs `charge.accrued` with the aggregate rate and tether count. The log has no friend id and is not shown in the UI.
- The banner screenshot is the charge card only, captured by the quick-states test into `Downloads/idl-charge-banner.png` and copied here.

## Commits

- `b28e44e` Remove per-friend tether signals so a pin stays private.
