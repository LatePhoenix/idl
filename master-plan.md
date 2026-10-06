# iDL — Master Plan (living document)

> **This is the single source of truth for project status, next steps and open findings.**
> Cursor and Claude both read and update it. Detailed specs live in `docs/` and are linked
> from here; this file says *what state everything is in* and *what to do next*.

Last full review: **2026-10-05 by Claude** (everything through `origin/main` `7a175a6`, PR #23).

**Next work: the avatar program, [`docs/avatar/AVATAR_PROGRAM.md`](docs/avatar/AVATAR_PROGRAM.md)
§5,** run by Cursor on its own under [`docs/handoff/CURSOR_RUNBOOK.md`](docs/handoff/CURSOR_RUNBOOK.md)
(D-49). §4.0 below points there.

---

## 0. How to use this document

**Before starting work:** read §1 (state), then take the first ready item in §4.0 "Next up".
Read its linked finding or spec. An item marked **spec needed** waits for a task spec in
`docs/handoff/` (ask Claude) before any code is written (F-17 rule). An item marked **user**
waits for an answer in §6.

**While working:** follow `AGENTS.md` (invariants, commands, conventions). One finding or
task per branch/PR where practical. Reference IDs in commit messages (e.g. `Fixes F-01`).

**When finishing an item:**

1. Tick its checkbox and set its status here.
2. Add a line to §8 (Work log) with date, IDs and PR number.
3. If you made a product or architecture decision, add it to `docs/IDL_DECISIONS.md` and
   to §5 here.
4. Write the checkpoint report (`docs/handoff/reports/<date>-<task>.md`) and make sure it
   matches what was actually committed.

**Status legend:** ✅ done and verified · 🟡 partial · ⬜ not started · 🔴 broken/regressed ·
⏸ blocked on a decision.

**Priority legend:** **P0** fix before any new feature work · **P1** this milestone ·
**P2** next milestone · **P3** later/nice to have.

**Don't** mark anything ✅ unless it was built, tested, and (for anything visual or on the
home screen) checked on a device or by snapshot test. CI runs Roborazzi snapshot
verification (F-14, PR #11). Device tests are still local; there is no emulator job.

---

## 1. Current state (2026-10-05)

### 1.1 Branches and CI

| Item | State |
| --- | --- |
| Remote | `github.com/LatePhoenix/idl` (private) |
| `origin/main` | `5e84e57` (merge of PR #30, 2026-10-06). Start new work from an up-to-date `origin/main` |
| Open PRs | None |
| Merged branches still on GitHub | `avatar/emoji-core-slice`, `avatar/vector-domain`, `avatar/vector-renderer`, `chore/branch-cleanup`, `docs/phase-2-task`. Safe to delete |
| CI (`.github/workflows/ci.yml`) | ✅ Jobs: `android` (unit tests, Roborazzi verify, lint, debug build) and `backend` (SQL suite + PostgREST IT). Snapshot diffs upload as `roborazzi-diffs` (`actions/upload-artifact@v7`) when the android job fails. No emulator job |

### 1.2 Verified numbers (Claude, 2026-10-05)

| Check | Result |
| --- | --- |
| `scripts/check.sh` | ✅ 216 JVM tests, 0 failed, 1 skipped (`SupabaseRestIT` runs in CI's backend job). Lint 0 errors / 42 warnings. Run on PR #23's head, which merged unchanged as `7a175a6`. CI green |
| Instrumented tests (Pixel 9 emulator, API 37) | ✅ 21/21 pass on `emulator-5554`, including `VectorSliceDeviceTest`. `scripts/check.sh --device` waits for boot and unlocks first (F-20 ✅) |
| SQL suite (`supabase/tests/run.sh`) | ✅ in CI (29/29 privacy vectors + behaviour/RLS) |
| Domain purity | ✅ No `android.*` imports, no `Instant.now()` in `domain/` |
| Secrets | ✅ None tracked (`local.properties` gitignored; anon key only via BuildConfig) |

### 1.3 What exists, by area

| Area | Status | Notes |
| --- | --- | --- |
| App shell, theme, navigation, deep links | ✅ | Compose + Material 3, dark mode |
| Presence model, precedence, expiry, Invisible | ✅ | `domain/Presence*.kt`, `Expiry.kt` |
| Privacy filter (Kotlin reference) + golden vectors | ✅ | `domain/Privacy.kt`, `contract/privacy_vectors.json` |
| Fake backend + demo world | ✅ | Default when Supabase isn't configured |
| Supabase server (RPC-only, RLS lockdown, outbox) | 🟡 | Tested locally and in CI; **never applied to a real Supabase project** |
| Supabase client + email-code auth | 🟡 | REST tested end to end against PostgREST; GoTrue only against mocks |
| Status Deck (7 quick states, custom, expiry, Invisible) | ✅ | Master plan wants 10 editable presets (Avatar Phase 6) |
| Friends, invites (QR display + code), requests, block/remove | ✅ | QR **scanning** not built |
| Reactions (send, inbox, dismiss, expire, rate limit) | ✅ | |
| Privacy Center (per-category audience, preview-as-friend) | ✅ | Circles/individuals disabled in UI |
| Widgets: solo friend + self, pinning, config activity | 🟡 | Resting avatar plus presence (F-01, F-03 ✅). Explicit status accessories beat signatures for every viewer (F-02 ✅). The default 2×2 uses STANDARD (F-21 ✅). Render failures stay in the fallback (F-22 ✅). Wallpaper contrast follows the wallpaper (F-07 ✅) |
| Avatar v2 domain (model, pack, resolver, compat, migration) | ✅ | Avatar Phase 1, plus recipe schema 3 (PR #9). `itemIds`, color slots and vector assets resolve (Phase 2 PR A) |
| Vector avatar art (D-39) | 🟡 | `emoji_core` v1: original round face, neutral and happy, bob hair, full beard, wire glasses, soft background. `CanvasVectorAssetRenderer` draws it through the same compositor as the procedural layers. Only visible on the debug **Settings → Vector slice** screen: no saved avatar uses it, and widgets still show the procedural pack (ROADMAP step 10) |
| Avatar editor for the new recipe format | ⬜ | Avatar Studio still edits v1 `AvatarConfig`, and Room and the server store v1. D-41 switches the saved format (AV.4); the editor is AV.6 |
| Availability as shape glyphs (D-28) | ✅ | Drawn in the app and on the widget path. Glyph names come from the manifest (F-08 ✅) |
| Render cache | ✅ | Cleared on sign-out and friend purge. Per-friend files, 8 MB byte LRU, atomic writes. Memory access is locked (F-10 ✅, F-26 ✅) |
| Charge economy (passive, mutual widget tethers) | 🟡 | Local prototype. Per-friend signals removed (F-05 ✅, C.2 ✅). Guardrails recorded; the in-app cap explanation is still C.5 (F-06 🟡). Ledger is still client-side (F-04) |
| Push delivery (FCM) | ⬜ | Outbox and `register_device` exist; no edge function or app receiver |
| Crash reporting / analytics | ⬜ | `CrashReporter` seam only |

---

## 2. Reference map

| Document | Purpose |
| --- | --- |
| `AGENTS.md` | Agent rules: invariants, commands, conventions, report format |
| `docs/IDL_PRODUCT_SPEC.md` | Product principles, MVP scope, non-goals |
| `docs/IDL_DECISIONS.md` | Every recorded decision (D-01…D-43). **Don't reopen without asking the user** |
| `docs/IDL_ARCHITECTURE.md` · `IDL_DATA_MODEL.md` · `IDL_PRIVACY_MODEL.md` · `IDL_WIDGET_ARCHITECTURE.md` · `IDL_API_CONTRACT.md` | Technical design |
| `docs/IDL_AVATAR_CREATOR_MASTER_PLAN.md` | Avatar product requirements (cited as §n) |
| `docs/IDL_AVATAR_CREATOR_PLAN.md` | Avatar phases 1–9, with detail |
| `docs/ARCHITECTURE.md` and `docs/ROADMAP.md` | D-39 emoji-style compositor. Design only; does not replace the rows above |
| `docs/handoff/PHASE_1_TASK.md` | Avatar Phase 1 spec (done) |
| `docs/handoff/PHASE_2_TASK.md` | Avatar Phase 2 spec: vector renderer core, D-39 slice (ROADMAP steps 3–5, 8) |
| `docs/avatar/AVATAR_PROGRAM.md` | **The avatar program (plan of record, 2026-10-06):** vision, architecture, testing, status table AP-1…AP-16, specs |
| `docs/avatar/ART_STYLE_GUIDE.md` | Binding rules for avatar art |
| `docs/handoff/CURSOR_RUNBOOK.md` | How Cursor runs the program on its own: session start, branches, checks, PRs, CI, merging, stop conditions (D-49) |
| `docs/art/brand/` | Launcher icon, wordmark, splash and Play icon sources and generator (D-44) |
| `supabase/README.md` | Server: security model, local tests, real-project setup |
| `docs/IDL_IMPLEMENTATION_ROADMAP.md` | **Superseded by this file**; kept for history |

---

## 3. Open findings from the 2026-10-04 audit

Each finding has: ID · priority · status · evidence · fix · how to verify. Fix P0 items
first and **in order**. F-01, F-02 and F-03 share a root cause and should be fixed together
in one PR.

### P0 — regressions on the primary product surface (the home-screen widget)

#### F-01 · Widgets draw no availability glyph and no activity badge · ✅ P0
- **Evidence:** device probe (Pixel 9, 2026-10-04) rendered the widget path twice, with and
  without `AvatarBadges(BUSY, VR)`; the bitmaps were identical (`badgesDrawn=false`).
  The cause is that `PlaceholderFrames.from()` (`domain/avatar/PlaceholderFrame.kt`) never adds
  `Layer.AVAILABILITY_BADGE` / `Layer.ACTIVITY_BADGE`, and `AvatarRenderer.draw(canvas, frame, …)`
  draws only `frame.layers`. Only the Charge "resonating" dot is still drawn, because it's
  painted outside the layer list.
- **Impact:** breaks the master plan hierarchy item 3 ("are they available?") and D-28 on the
  home screen. This shipped in PR #3 right after PR #2 added the glyphs.
- **Fix:** take badges from the *resolved* layers (`avail_*` / `badge_*`, which the resolver
  already selects and simplifies per target), which requires F-03. Draw the glyph named by the
  manifest asset's `glyph` field (see F-08).
- **Verify:** an instrumented or Roborazzi test that renders the widget path and asserts the
  bitmap differs with vs without availability, for every `Availability` value.
- **Fixed (Track 0.1):** `PlaceholderFrames` copies `avail_*` / `badge_*` and their manifest
  glyphs. `WidgetRenderPathTest` on a Pixel 9 emulator asserts every availability bitmap differs,
  and the device renders are in `docs/handoff/reports/screenshots/`.

#### F-02 · VR headset disappears for anyone whose signature is glasses · ✅ P0
- **Evidence (two independent paths):**
  1. *Widget path:* `widget/Widgets.kt render()` runs `LegacyAvatarMigration.migrate()` on the
     friend's **composed** v1 avatar, which already contains status visuals. `sanitize()` treats
     the VR headset and glasses as two saved accessories in conflict and keeps the glasses (lower
     z), so the headset is removed **silently** (`dropped=[]`). Confirmed on device.
  2. *Resolver:* explicit status visuals (`VisiblePresence.headAccessoryAssetId`, which is what
     the "In VR" quick state produces) get `LayerPriority.CONTEXT`, which ranks below
     `SIGNATURE`. So the signature glasses win and the headset is dropped with `CONFLICT`.
     Confirmed by a JVM probe through QuickState → `PresenceResolver` →
     `LegacyAvatarMigration.presence` → `AvatarResolver`. Cursor's test 4 only covered the
     `activity:vr` semantic path, which works.
- **Impact:** the master plan's flagship scenario (§27, §11.1) fails.
- **Fix:**
  - (a) Never sanitize composed avatars. The widget builds identity from
    `PresenceView.restingAvatar` and passes status visuals as `VisiblePresence` (F-03).
  - (b) In the resolver, a chosen slot takes the **highest** priority among all sources that
    name the same asset (the explicit headset also matches `activity:vr` → ACTIVITY).
  - Also record in `IDL_DECISIONS.md` the rule for a temporary *explicit* accessory versus a
    conflicting signature in another slot. Recommended: the temporary choice wins and the
    signature uses its fallback or returns when the status ends, per §11.1.
- **Verify:** end-to-end test from `QuickState("vr")` with signature glasses → headset present,
  glasses `CONFLICT`. The same through the widget path on a device or snapshot test.
  Re-run the device probe.
- **Fixed (Track 0.1–0.2):** widgets migrate `restingAvatar` only. A slot takes the highest
  priority among sources that name the same asset (D-31), so the explicit headset is ACTIVITY
  and signature glasses are `CONFLICT`. JVM tests cover QuickState and the widget inputs;
  `widget-vr-over-glasses.png` is the device render.
- **Still broken (Claude review of PR #5, 2026-10-04):** the fix only works when the viewer can
  see the activity. With default rules a friend **not on the close list** gets the headset as an
  explicit visual but **no `activityType`** (`activity_category` defaults to close friends). No
  later source raises its priority, so it stays CONTEXT and loses to the SIGNATURE glasses again.
  Probe through `PrivacyFilter.viewFor(isCloseFriend = false)` → `LegacyAvatarMigration.presence`
  → resolver: `head_vr_headset` dropped `CONFLICT(face_glasses_round)`. That's the most common
  viewer. The PR's tests always set `activityType = VR`, so they miss it.
- **Fixed (PR #5 follow-up):** head and body accessories a status chooses are `LayerPriority.STATUS`,
  above `SIGNATURE` and below `ACTIVITY`. A non-close friend still sees the headset; a close friend
  keeps `ACTIVITY` when `activity:vr` names the same asset. Launcher check:
  `widget-vr-over-glasses-nonclose.png`.

#### F-03 · Widget rendering ignores presence; status is baked into identity · ✅ P0
- **Evidence:** `Widgets.kt render()` calls the resolver with the default
  `VisiblePresence.NONE`. Status visuals reach the resolver only as "saved identity" fields
  of the migrated composed avatar.
- **Impact:** semantic mappings, compact badge-vs-prop simplification, displacement, the
  accessibility sentence and the render key all ignore presence. It also causes F-02(1).
- **Fix:** `WidgetModel` carries the `PresenceView` (friend) or `ResolvedPresence` (self).
  Render with `configuration = migrate(restingAvatar)` and
  `presence = LegacyAvatarMigration.presence(view)`. Use the resolver's
  `accessibilityDescription` for the widget content description. Choose `RenderTarget` from
  the Glance size (`LocalSize`) instead of the constant `STANDARD_WIDGET`.
- **Verify:** unit test on a new pure function `WidgetRenderInputs.from(model)`; device check
  of 1×1 and 2×2 widgets.
- **Fixed (Track 0.1):** `WidgetModel` carries `PresenceView` or `ResolvedPresence`.
  `WidgetRenderInputs.from` builds the resolver request. Glance `LocalSize` picks compact /
  standard / large. The widget content description uses `accessibilityDescription`.
  Device renders: `widget-1x1-busy.png`, `widget-2x2-busy-vr.png`.

### P1 — correctness, privacy and design issues

#### F-04 · Charge balance is client-authoritative and can be farmed via the device clock · 🟡 P1 (blocker for paid Charge)
- **Evidence:** `ChargeEngine` uses device time; each evaluation after a gap longer than 24 h
  pays a full 24 h. Repeatedly moving the clock forward mints up to 24 h of Charge each time.
  The balance lives only in Room, and `SessionRepository.signOut()` (`clearAllTables`) wipes it.
- **Fix:** before Charge can be **spent**, move the ledger to the server: a `charge_ledger`
  table plus a `claim_charge()` RPC evaluated with server `now()`, with tethers derived from
  server-known widget subscriptions. The client keeps only a display cache. Until then, label
  Charge "preview" and don't build spending on it.
- **Now mandatory (D-32):** Charge can be bought with real money, so the ledger must be
  server-authoritative, with every purchase verified server-side before credit (C.6).
- **Verify:** SQL tests for accrual, the cap and clock independence; the client shows the
  server balance after reinstall.

#### F-05 · "Friend pinned my widget" is a new private behavioural signal · ✅ P1 (decided 2026-10-04)
- **Evidence:** `FriendTether.hasRemoteWidgetInstalled`, the per-friend "resonating" dot
  painted on that friend's widget, and the widget content description "…, resonating" tell me
  whether a *specific* friend has *me* pinned. That works like a read receipt, which the
  product spec says to avoid. It isn't covered by the privacy model or audience rules. Today
  only debug toggles set it (no server field yet).
- **Decision (user, 2026-10-04, D-30a):** no. A user must never learn that a *specific*
  friend has pinned their widget.
- **Fix:**
  - Remove the per-friend "resonating" dot (`AvatarBadges.resonating`,
    `WidgetModel.resonating`) and the "…, resonating" content description.
  - Charge UI shows the balance and a qualitative "earning" state. Don't show the tether count
    or the exact hourly rate: with few friends, either one reveals who pinned you, and a rate
    change reveals *when*.
  - Server side (C.3): widget-install state is used only inside the ledger RPC. No RPC or
    `PresenceView` field ever returns another user's pin state, and SQL tests assert this.
  - The debug toggles may stay, fake backend only.
- **Verify:** UI and widget tests assert no per-friend tether indicator; SQL behaviour test
  proves no RPC exposes `hasRemoteWidgetInstalled`-equivalent data.
- **Fixed (client, C.2):** the resonating dot, `AvatarBadges.resonating`, `WidgetModel.resonating`,
  and the ", resonating" content description are gone. Render keys no longer include it. The
  Status Deck banner shows the balance and "Earning" / "Not earning" only. Debug toggles that
  set `hasRemoteWidgetInstalled` stay behind the fake backend. The SQL assertion is C.3, not
  this change: no RPC returns pin state today because the server has no pin field yet.
- **Residual timing signal accepted (D-40):** "Earning" flips on when a friend you pinned pins you
  back, so with one pinned friend the moment is inferable. Accepted because it only covers friends
  you pinned yourself; no change needed in C.3.

#### F-06 · Charge guardrails · 🟡 P1 (approved 2026-10-04)
- **Context:** the user's intent (2026-10-04): Charge is a passive resource that encourages use
  and will be spent on customization and avatar accessories. Recorded as D-30.
- **Tensions with the product spec:**
  - "no engagement bait / no gamification that punishes absence"
  - "close friends, not follower counts": the per-tether rate encourages pinning more people,
    though the diminishing rates (10, 10, 8, 6, 2…) limit this
  - social pressure ("pin me back so I earn")
  - Monetization §17.3: the core vocabulary is never gated
- **Approved guardrails (user, 2026-10-04, D-30b). Treat these as invariants:**
  - no notifications about Charge, earnings or lost tethers
  - never show who has or hasn't pinned you (F-05)
  - no streaks, no decay, no "come back" mechanics
  - the 24 h cap is a ceiling, not a penalty, and is explained in-app (C.5)
  - Charge buys only cosmetics (avatar decorations, accessories, customizations), never status
    vocabulary, privacy, widget features or accessibility (master plan §17.3)
  - the balance stays on the Status Deck (user decision); keep it compact so it doesn't slow
    the one-tap flow
- **Fix:** add the first four to `AGENTS.md` invariants when the Charge UI is next touched,
  and add a test that the Status Deck quick states stay the first actionable row.
- **Partial (C.2):** invariants 10–13 are in `AGENTS.md`. The banner is compact and not
  clickable, and `StatusDeckTest.quickStatesStayTheFirstActionableRow` checks that the quick
  states are the first tagged action. Still open: explain the 24 h cap in-app (C.5). Cosmetics-only
  spending is C.4.

#### F-07 · Widgets hard-code dark-wallpaper contrast · ✅ P1
- **Fixed (2026-10-04).** Auto reads `WallpaperColors.getColorHints()` and maps
  `HINT_SUPPORTS_DARK_TEXT` to a light wallpaper. Settings offers Auto (default), Light, and Dark.
  API 26–30 and a null color result use a dark wallpaper, because `getColorHints()` is API 31.
  The mode is part of the render key. Widgets refresh from `OnColorsChangedListener` while the
  process is alive, and every existing widget update re-reads the wallpaper. No poll.

#### F-08 · Glyph names duplicated in code and manifest · ✅ P1
- `domain/StatusGlyphs.kt` hard-codes what `core_proto` already declares in each asset's
  `glyph`. **Fix:** the renderer reads `registry.asset(layer.assetId).glyph`; delete
  `StatusGlyphs` or keep it as a test-only cross-check.
- **Fixed:** resolved frames already carry the manifest glyph. The painter uses that field.
  The legacy badge path looks up `avail_*` / `badge_*` on the registry. `StatusGlyphs` now
  lives in the unit tests as a cross-check that every indicator and badge still declares
  the expected shape.

#### F-09 · `PlaceholderFrame` adapter is lossy · 🟡 P1 (resolved by Avatar Phase 2)
- It ignores resolved brows, eye and mouth families, essential overlays chosen by
  semantics/overrides, extras (sick → bandage), reactions and confetti. Critter without ears
  renders as a blob.
- **Fix in Phase 2:** paint each `ResolvedLayer` by `AssetDef.painterKey`, then delete the
  adapter.
- **Status (2026-10-05):** partly done. Since Phase 2 PR B the renderer is driven by
  `CompositeOrder`, and vector assets bypass the adapter. Procedural layers still go through it,
  so it retires category by category as vector art replaces procedural art.

#### F-10 · Render cache hygiene · ✅ P1
- Rendered friend images in `cacheDir/renders` survive friend removal, block and sign-out. They
  aren't displayed again, but revoked data should leave the device (privacy rules 5–6).
- `LruCache` counts entries, not bytes (D-27 targets about 8 MB; share-card renders are 1 MB each).
- File writes aren't atomic.
- **Fix:** clear the cache in `signOut()`; key or tag files by friend and delete them in
  `purgeUser`; size the LRU by `bitmap.byteCount`; write to a temp file then rename.
- **Fixed:** `SessionRepository.signOut()` clears memory and disk. Friend files live in a
  per-user directory and `purgeCachedUser` deletes only that directory (remove, block, decline,
  refresh purge, and the friend-removed push). The memory cache evicts by bitmap bytes with an
  8 MB budget (D-27). Publishes rename a temp file in the same directory, so a failed write
  leaves the previous png in place and no `.tmp`. Memory access is locked. A render that
  finishes after that owner was purged does not publish its file. Sign-out deletes the files
  on `Dispatchers.IO`.

#### F-11 · Accessibility sentence repeats itself · ✅ P1
- DND produces "Blob avatar, do not disturb, do not disturb" (expression label plus
  availability label). **Fix:** de-duplicate labels case-insensitively; add a test.
- **Fixed:** `describe` skips a label that is already in the sentence, ignoring case.
  Do not disturb is "Blob avatar, do not disturb".

#### F-12 · Unknown presence scene replaces the saved scene · ✅ P2
- `resolveFamily` returns the pack default on the first unknown ID instead of trying the next
  source. **Fix:** unknown non-identity sources fall through; only identity slots fall back to
  defaults. (Open question from Cursor's Phase 1 report.)
- **Fixed:** an unknown candidate is skipped. A presence scene of `scene_missing` keeps the
  saved scene. A saved scene that itself is unknown still uses the pack default, as does an
  unknown base.

#### F-13 · No Room migration test · ✅ P1
- DB v1→v2 (`MIGRATION_1_2`) has no `MigrationTestHelper` test, although schemas 1 and 2 are
  exported. Required by invariant 5 before the next schema change.
- **Fixed:** `Migration1To2Test` loads the exported schemas, inserts one row in every v1 table,
  migrates, and checks those rows plus empty `user_economy` / `friend_tethers` tables. The
  nullable `tetherActivatedEpochMs` defaults to null.

#### F-21 · Default-size widget is simplified as a 48 px compact render · ✅ P1 (from PR #5)
- `WidgetRenderInputs.targetFor()` maps the `SMALL` (110×110 dp) Glance bucket to
  `COMPACT_WIDGET`. But 110 dp is the widget's **minimum and default 2×2 size**
  (`minWidth/minHeight = 110dp`, `targetCell 2×2`); there's no 1×1 widget. So a freshly placed
  widget drops every body accessory and, whenever an activity badge shows, the prop: "Sleepy"
  loses its blanket and "Gaming" loses its controller, while the avatar is drawn at 76 dp.
  Before PR #5 the same widget used `STANDARD_WIDGET`.
- **Fix:** choose the target from the **avatar image's** drawn size, not the cell size. The
  SMALL bucket (76 dp avatar) maps to STANDARD, WIDE (92 dp) to STANDARD, and SQUARE to LARGE.
  Reserve COMPACT for a future 1×1 and dense circle widgets. Rename the screenshots so "1×1" isn't
  used for the 2×2 default.
- **Verify:** a unit test of `targetFor` for every declared Glance size, and a device render of
  the default widget showing the blanket and prop.
- **Fixed (PR #5 follow-up):** the target follows the avatar's drawn size. 110×110 and 250×110 are
  STANDARD; 180×180 is LARGE. Sleepy keeps `body_blanket` and tea.
  `widget-2x2-default-sleepy.png`, `widget-2x2-default-busy.png`.

### P2/P3 — process and maintenance

#### F-14 · CI can't catch visual or widget regressions · ✅ P1
- F-01 and F-02 merged with green CI because CI never rendered a bitmap.
- **Fixed (PR #11):** Roborazzi 1.60.0 + Robolectric 4.17 snapshot the widget path
  (`WidgetRenderInputs.from` → `AvatarResolver` → `AvatarRenderer.bitmap` at 256 px).
  Goldens live in `app/src/test/snapshots/widget/`. `verifyRoborazziDebug` runs in
  `scripts/check.sh` and the android CI job; diff images upload as `roborazzi-diffs` on failure.
  Availability bitmaps are also compared as raw pixels, so a missing glyph fails even if the
  goldens are re-recorded. `.github/pull_request_template.md` asks for a snapshot or a device
  screenshot on visual changes.
- Roborazzi 1.76.0 is built with Kotlin 2.3 metadata. This project is Kotlin 2.0.21, so 1.60.0
  (the newest release still built with 2.0.21) is the pin. No emulator job yet.

#### F-15 · Documentation drift · ✅ P2
- `AGENTS.md` "Current work" still pointed at Phase 1 (fixed in this commit).
- The README test counts are stale (78 → 141).
- `IDL_AVATAR_CREATOR_PLAN.md` §7 says Phase 1 isn't wired into the app, but widgets now use
  the resolver.
- **Fix:** keep counts and status here only; README links here.
- **Fixed:** §1.1 names `origin/main` `5b81bd6` and no longer says local main is behind.
  README points at §1.2 for counts. §7 of the creator plan says the widget path uses the
  resolver. The work log names PRs #7, #8 and #9. `docs/ROADMAP.md` no longer says D-31 is
  unrecorded.

#### F-16 · Checkpoint report inaccuracy · ✅ P3
- `docs/handoff/reports/2026-10-04-charge.md` says "Not committed" and "Commits: none", but it
  shipped in `0ea04a7`. Reports must be finalized after committing.
- **Fixed (2026-10-05):** the report now names `0ea04a7` and PR #1.

#### F-17 · Phase 2 work merged without a spec or device check · ✅ P3 (process)
- Glyphs, contrast, render cache and the widget resolver path landed piecemeal (PRs #2–#3).
  **Rule from now on:** each avatar phase gets a task spec in `docs/handoff/` first, and visual
  PRs need a device screenshot or snapshot test.
- **In effect:** Phase 2 PRs #21–#23 followed `docs/handoff/PHASE_2_TASK.md`, and every visual
  change came with goldens and device screenshots. The rule stays: see §0.

#### F-18 · CI action versions deprecated · ✅ P3
- `actions/checkout@v4`, `actions/setup-java@v4` and `gradle/actions/setup-gradle@v4` run on
  deprecated Node 20. `ubuntu-latest` moves to Ubuntu 26 on 2026-10-19. Bump to the current
  major versions and pin the runner image.
- **Fixed:** checkout v7, setup-java v6, setup-gradle v6 (all Node 24). Both jobs run on
  `ubuntu-24.04`. PR #10 run `37226686509`: android and backend success; logs have no Node 20
  deprecation warning.

#### F-19 · Defence in depth for explicit expressions · ⬜ P2 (with Avatar Phase 3)
- The resolver trusts `VisiblePresence.expressionId` even when `mood` is absent. That's
  acceptable under D-24 (the server never sends it then). Add a Phase 3 golden vector proving
  the server omits `visual.expression` when mood is hidden.

#### F-20 · Flaky first instrumented test on a cold emulator · ✅ P3
- `StatusDeckTest.quickStateIsOneTap` failed with "No compose hierarchies found" on the first
  run after the emulator booted, then passed twice. **Fix:** wait for boot and unlock the screen
  in `scripts/check.sh --device` (`adb wait-for-device`, `input keyevent 82`), or add a test
  rule that retries activity launch once.
- **Fixed:** `scripts/check.sh --device` and `scripts/check.ps1 -Device` wait for
  `sys.boot_completed=1`, then send keyevent 82, before the instrumented tests.

#### F-22 · Widget render errors bypass the fallback · ✅ P2 (from PR #5)
- `Widgets.kt render()` now reads `container.assetRegistry` and calls
  `WidgetRenderInputs.from()` (which runs `migrate`/`sanitize`) **outside** `runCatching`. A pack
  load or parse failure, or `error("pack default … is missing")`, propagates out of
  `provideGlance`, so Glance shows its generic error box instead of the designed fallback and
  `widget.render_failed` isn't logged. Before PR #5 both were inside `runCatching`.
- **Fix:** move both inside `runCatching`, or wrap `render()`'s whole body. Add a test with a
  registry that throws.
- **Fixed (PR #5 follow-up):** `WidgetRenderInputs.renderCatching` loads the registry and builds
  inputs inside the catch. A throw logs `widget.render_failed` and the widget gets a null bitmap.

#### F-23 · Snapshot diff upload still runs on Node 20 · ✅ P3
- `actions/upload-artifact@v4` in the android job runs on deprecated Node 20. The other actions
  were bumped in F-18.
- **Fixed:** the failure upload uses `actions/upload-artifact@v7` (Node 24). The step still
  uploads both Roborazzi directories, so `archive` stays at its default and the files are zipped.

#### F-24 · Widget snapshot matrix does not prove target simplification · ✅ P2
- `busy VR covers compact standard and large` asserts `avail_busy` and `head_vr_headset` at
  every target. Those layers are kept everywhere, so the three goldens do not show that compact
  drops anything.
- **Fixed:** `sleepy compact drops the blanket and keeps the tea` resolves the same sleepy friend
  at `COMPACT_WIDGET` and `STANDARD_WIDGET`. Compact drops `body_blanket` and keeps `prop_tea`
  (no activity badge, so the one prop stays). Standard keeps both. The bitmaps differ, and
  `sleepy_compact` is recorded.

#### F-25 · A missing registry drops availability glyphs · ✅ P1
- The `AvatarConfig` overloads of `AvatarRenderer.bitmap` and `draw` took
  `registry: AssetRegistry? = null` and looked up `avail_*` / `badge_*` with `registry?.asset`.
  A caller that omitted the registry painted availability as color only, which breaks D-28.
- **Fixed:** both parameters are a required `AssetRegistry`. `AvatarImage` already passed one.
  `AvatarRendererTest` passes the core pack for the expression and determinism bitmaps.

#### F-26 · Render cache memory access races widget renders · ✅ P1
- `ByteLruCache` mutated its map off a lock while widgets render on `Dispatchers.Default` and
  purges run from sync and push. A render that finished after `deleteOwner` could publish a file
  for an owner that had just been removed. `signOut` deleted those files on the caller thread.
- **Fixed in PR #14, before merge:** the byte cache and the publish path take a lock, a
  generation stamp rejects a late publish, and sign-out clears the files on `Dispatchers.IO`.
  See F-10.

#### F-27 · Schema 2 recipes are not upgraded on load · ✅ P1
- `migrateRecipe` had no production caller. A decoded schema 2 `AvatarConfiguration` stayed at
  schema 2.
- **Fixed:** `AvatarConfiguration.decode` decodes and migrates. The widget path migrates the
  saved avatar and a friend's `restingAvatar` through `LegacyAvatarMigration.migrate`, then
  `migrateRecipe`. `AvatarResolver.resolve` migrates before it builds the render key.

#### F-28 · Item order inside a category changes the render key · ✅ P2
- `withSortedOverrides` sorted ids inside each `itemIds` list. If that list were drawing order,
  two looks would share a cache entry.
- **Fixed:** the lists are unordered sets. Drawing order is the asset z-index. The render key
  still sorts the ids, and a test checks that reversing a list does not change the key.
  `docs/AVATAR_RECIPE_SCHEMA.md` says so.

#### F-29 · A newer avatar schema can be written back · 🟡 P1
- `IdlJson` drops unknown keys. Saving a decoded schema newer than `SCHEMA_VERSION` would
  overwrite fields this app does not know.
- **Guard exists, not wired yet:** `AvatarConfiguration.prepareForWrite` returns
  `NeedsAppUpdate` ("update the app to edit this avatar") for a newer schema, and
  `AvatarConfiguration.decode` decodes and migrates. Both are unit-tested. Nothing calls them
  yet: Room and the server still store v1 `AvatarConfig`, so no saved recipe exists.
- **Still to do:** wire `decode()` and `prepareForWrite()` into the load and save path when
  `AvatarConfiguration` becomes the persisted format (vector editor, `docs/ROADMAP.md` step 6).
  The editor must show the `NeedsAppUpdate` message instead of saving.

#### F-30 · Vector slice snapshots don't use widget targets · ✅ P2 (from PR #23, fixed in AV.2)
- `EmojiSlice` renders 48, 128 and 512 px with `RenderTarget.PROFILE`. The 48 px goldens
  therefore skip compact-widget simplification (`minSizePx`, `widgetSafe`, badge-versus-prop), so
  nothing yet shows what a small widget would drop from the vector slice.
- **Fix:** in `VectorSliceSnapshotTest`, render the slice recipes at `COMPACT_WIDGET` (48 px) and
  `STANDARD_WIDGET` (96 px), with targets chosen through `WidgetRenderInputs.targetFor`. Assert
  which vector layers survive at each target, and record goldens. Keep the existing PROFILE goldens.
- **Verify:** the new goldens plus layer assertions. Required before the widget cutover (ROADMAP step 10).
- **Done in AV.2.** Compact (48 px) and standard (96 px) goldens are under `app/src/test/snapshots/vector/widget/`. Targets come from `WidgetRenderInputs.targetFor`. The vector identity layers stay; `overlay_blush` drops because it is decoration. PROFILE goldens remain.

#### F-31 · Picture files are found by trying each pack directory · ✅ P3 (from PR #22)
- `AppContainer.vectorPictures` reads `render.file` by trying every `AssetPacks.SHIPPED` directory
  in order. If two packs ship the same relative file, the first pack wins and the wrong picture
  loads (it then fails validation and the layer is skipped). Safe today, because picture files
  are named after unique asset ids.
- **Fix:** `AssetPacks.registry` records each asset's pack directory, and the picture cache reads
  `<packDir>/<render.file>`. Keep the domain Android-free: the registry stores the path string, and
  `AppContainer` still opens the file.
- **Verify:** a JVM test with two packs that ship the same relative file name loads each pack's own picture.
- **Done in AP-2.** `AssetRegistry` records each manifest's directory. `VectorPictureCache` opens `<directory><render.file>`. `PackPictureCacheTest` loads both pictures.

---

## 4. Roadmap

Order of work (updated 2026-10-04 per user: real backend projects come once avatars feel right):

1. **Track 0** (audit fixes): ✅ done 2026-10-04
2. **The Avatar program (§4.1, D-41/D-42):** the original teardrop face, every smiley-face
   expression, schema 3 saved everywhere, the editor, and wave after wave of customization
   content. This is the product's "bread and butter" and the current focus. U.1–U.4 and
   Avatar Phases 6–7 follow or interleave.
3. Then, when the user is happy with avatars: **Milestone 1.5–1.6** (the user creates the
   Supabase + Firebase projects), **Charge C.3/C.6**, and the account-linked integrations
   (Steam, Spotify; §4.2) against the real backend. Avatar Phase 3 is pulled forward as AV.4
   and is tested on the local Docker stack.
4. Closed alpha → Avatar Phase 8 (final art) → v0.5 → v1.0.

Server-only work that doesn't need a real project (SQL migrations, golden vectors, local
PostgREST tests) can be done at any time.

### 4.0 Next up (start here)

**The avatar program is the work queue.** Take the next item from the status table in
[`docs/avatar/AVATAR_PROGRAM.md`](docs/avatar/AVATAR_PROGRAM.md) §5 (the first ⬜ row whose
dependencies are ✅) and follow [`docs/handoff/CURSOR_RUNBOOK.md`](docs/handoff/CURSOR_RUNBOOK.md).
Items outside the program (I.0 integration research, U.1 thought bubbles, Charge C.3+) wait until
the program is done or the user schedules them.

Mapping from the old queue: N.1 AV.2 ✅ (PR #27) · N.2 F-31 → AP-2 · N.3 AV.3 → AP-5 ·
N.4 Q14/Q15 answered (D-47, D-48) · N.5 AV.4 → AP-7 · N.6 AV.6 → AP-11 · N.7 I.0 and N.8 U.1 wait.

### 4.1 Avatar program (D-41, D-42): the bread and butter

> **Superseded as the to-do list on 2026-10-06** by [`docs/avatar/AVATAR_PROGRAM.md`](docs/avatar/AVATAR_PROGRAM.md)
> (AP-1…AP-16). The AV table and specs below stay for history and are cited by the program
> (AP-5 uses the AV.3 spec). AV.3 → AP-5, AV.4 → AP-7, AV.5 → AP-6 and AP-13, AV.6 → AP-11,
> AV.7 → AP-15, AV.8 → AP-12, AV.9 → partly AP-9, the rest later.

#### User review queue (asynchronous, D-49)

Cursor adds a row for every visual it ships. The user reviews when convenient. A row never
blocks merging; feedback becomes a new fix item.

| Date | Item | What to look at | User verdict |
| --- | --- | --- | --- |
| 2026-10-06 | AP-1 | Brand head vs the old base and the launcher, at 512 and 48: `docs/handoff/reports/screenshots/ap-1-compare-512.png`, `ap-1-compare-48.png`. Hair and facial hair on the new chin: `ap-1-contact-512.png`, `ap-1-contact-48.png`. | |
| 2026-10-06 | AP-3 framing | Bust at 512 and head at widget size, before the body is drawn: `app/src/test/snapshots/vector/neutral_512.png`, `contact_512.png`, `vector/widget/neutral_compact.png`. | |
| 2026-10-06 | AP-3 body | Shoulders and the default crew tee. Bust: `app/src/test/snapshots/vector/neutral_512.png`, `contact_512.png`, `contact_512_dark.png`, `contact_48.png`. Head: `contact_head_48.png`, `contact_head_48_dark.png`, `vector/widget/neutral_compact.png`, `vector/widget/neutral_standard.png`. | |

Goal: an original, teardrop-shaped emoji face that can express every standard smiley-face emotion
and be customized as far as the 48 px widget can still read. The renderer, compositor and recipe
format from Phase 2 stay (D-39). Every item ships original art (`license: proprietary-idl`).

| # | Item | Status | Depends on | Notes |
| --- | --- | --- | --- | --- |
| AV.1 | Decisions and design docs: D-41, D-42, D-43; scope file; ROADMAP, ARCHITECTURE and LICENSING notes | ✅ | | Done by Claude, 2026-10-05 |
| AV.2 | Teardrop base, re-placed expression parts, hair and facial hair redrawn for it (Q13), round face retired; F-30 | ✅ | | `emoji_core` v2. Report `docs/handoff/reports/2026-10-05-teardrop-base.md` |
| AV.3 | Expression catalog: every emotion in the scope file, mapped to parts, moods and overlays | ⬜ | | Spec below |
| AV.4 | Schema 3 saved everywhere (D-41): Room migration, server migration, `put_avatar` validation, `presence_view` v2 with client composition (Avatar Phase 3 core), golden vectors, F-29 wiring, F-19 | ⬜ | | Spec needed. Contract changes once |
| AV.5 | Expression art in batches: (1) the 16 `Mood` defaults, (2) the rest of the catalog | ⬜ | AV.2, AV.3 | Each batch: goldens at 48 / 96 / 512 and a contact sheet; free forever (invariant 6) |
| AV.6 | Editor (Avatar Phases 4–5 merged): category strip, grid, colors, undo/redo, seeded randomize, 48 px preview, save through `prepareForWrite`, export PNG and recipe JSON (ROADMAP 6–7) | ⬜ | AV.4 | Spec needed |
| AV.7 | Content waves, in this order: hair (10+), facial hair (6+), headwear, eyewear, jewelry and piercings, tops (after Q15), mouth and hand props, backgrounds, frames | ⬜ | AV.2; tops need Q15 | Each wave: original art, validator, goldens, 48 px review. Free baseline per Q14 |
| AV.8 | Widget cutover to vector art (ROADMAP step 10) | ⬜ | AV.4, AV.5 batch 1 | Must keep availability and activity glyphs |
| AV.9 | Customization depth: OKLCH color picker, patterns on tops, per-mood expression overrides (`semanticVisualOverrides`), saved looks, per-friend looks (U.3) | ⬜ | AV.6 | Later waves; each gets a spec |

**Rules for every AV item**
- Original art only (D-42). Pictures pass `VectorPictureValidator`. The validator is the CI gate.
- Each content PR adds goldens at `COMPACT_WIDGET` (48 px), `STANDARD_WIDGET` (96 px) and 512 px,
  plus a device screenshot of the Vector slice screen (extend that screen as content grows).
- Identity items never change with mood, and mood-derived parts never show when mood is hidden
  (invariant 2).
- Removing or renaming a shipped asset needs a `retired` mapping (invariant 5).
- Category additions follow PHASE_2 rules: new `AssetCategory` values only where an editor tab
  needs them; wire names stay stable.

#### AV.2 spec · Teardrop base and hair redraw

Branch `avatar/teardrop-base` from `origin/main`. Pack `emoji_core` moves to **v2**
(`packs/emoji_core/v2/`, with `v1` removed and every v1 id retired to its replacement in the v2
manifest). The pack version is in the render key, so caches rebuild once.

1. **`base_teardrop`** (BASE, `family: teardrop_face`, the new default vector base). Draw on the
   1024 grid:
   - crown at about y 96
   - widest at about y 430, about 760 wide (x 132–892)
   - sides curve in to a neck about 260 wide at y 930, then run straight off the bottom edge so
     tops can cover it later
   - a slight, not pointy, teardrop: the face must still read as a face at 48 px

   Parts: `face` (radial gradient, highlight upper left), `shade` (lower cheeks and neck),
   `outline` (even-odd ring), all band 40. Slots: `face.primary`, `face.shadow`,
   `face.highlight`, `outline`.

   Anchors are stored normalized (value / 1024), as the registry requires:

   | Anchor | Position (1024 grid) |
   | --- | --- |
   | `head_top` | (512, 96) |
   | `forehead` | (512, 250) |
   | `temple_left` / `temple_right` | (190, 360) / (834, 360) |
   | `eye_line` | (512, 430) |
   | `ear_left` / `ear_right` | (140, 470) / (884, 470) |
   | `nose` | (512, 520) |
   | `upper_lip` | (512, 610) |
   | `mouth` | (512, 640) |
   | `jaw_left` / `jaw_right` | (300, 700) / (724, 700) |
   | `chin` | (512, 790) |
   | `neck` | (512, 930) |
   | `accessory_center` | (512, 430) |

   Also keep the registry's required anchors. Write the face-safe zone too.
2. **Expression parts re-placed:** `eyes_round_neutral`, `eyes_round_happy`, `brows_round_relaxed`,
   `mouth_round_neutral` and `mouth_round_smile` move to the teardrop's eye line and mouth anchor
   (`contentVersion` 2). `expressionOverrides` for neutral and happy key on `base_teardrop`.
3. **Hair redraw (Q13).** The Phase 2 bob read as a hood. New rules:
   - Rear hair shows only outside the head silhouette, at the sides and top. Never draw a full
     circle behind the head.
   - Bangs follow a curved hairline with a visible parting, not a straight band.
   - Hair has a highlight strand (`hair.highlight`, derived), so it reads as hair at 48 px.

   Ship three styles: `hair_short_crop`, `hair_bob` (retires `hair_round_bob`) and
   `hair_long_straight`, which falls behind the shoulders.
4. **Facial hair redraw:**
   - `beard_full` (retires `beard_round_full`), shaped to the teardrop jaw, keeping the mouth hole
   - `stubble` (low-opacity texture shape)
   - `mustache_classic`
5. **Retire** `base_round_face` → `base_teardrop` in the v2 manifest. `glasses_round_wire` and
   `scene_round_soft` move to v2 unchanged unless they need re-placing. Glasses sit on `eye_line`.
6. **F-30 included:** `VectorSliceSnapshotTest` adds `COMPACT_WIDGET` and `STANDARD_WIDGET` renders
   with layer assertions. Update `EmojiSlice` recipes to the teardrop and the new hair styles.
7. **Acceptance:**
   - all existing procedural goldens unchanged; vector goldens re-recorded and reviewed
   - a contact sheet of every hair × facial hair combination at 48 px and 512 px attached to the PR
   - device screenshots of the Vector slice screen
   - nothing reads as a hood or a headband at 48 px
   - `scripts/check.sh` and `--device` pass

#### AV.3 spec · Expression catalog

Branch `avatar/expression-catalog`. No art and no Kotlin behavior change.

1. **Download the pinned file.** Get `emoji-test.txt` (URL and date in
   `config/emoji_face_scope.json`) into `config/unicode/emoji-test-18.0.txt`. It's Unicode data
   under the Unicode license; add that notice to `docs/LICENSING.md`.
2. **Generate** `config/expression_catalog.json` with a script under `tools/` (Kotlin JVM test or
   Python; no new Gradle dependency). Generated means checked in and reproducible. The script
   reads only `fully-qualified` entries in `includeSubgroups`, one entry per distinct face, with:
   - `codepoints`, `cldrName`, `subgroup`
   - `expressionId` (snake case from the CLDR name, e.g. `grinning_face_with_sweat`)
   - `mood`: the existing `Mood` it best expresses, or null
   - `parts`: proposed `eyes`, `brows`, `mouth` shape names; `overlays` (tears, sweat, blush,
     hearts, Zs, hand…)
   - `decomposedItems` for face-hat and face-glasses entries
   - `priority`: 1 = default face for a `Mood`, 2 = everything else
3. **Write** `docs/avatar/EXPRESSION_CATALOG.md`: a human-readable table generated from the
   JSON, plus the shared part vocabulary. Shapes are reused across expressions, so list the
   distinct eye, brow, mouth and overlay shapes and how many expressions use each. That list is
   AV.5's art to-do list.
4. **Mapping rules:**
   - each of the 16 `Mood` values has exactly one priority-1 expression
   - `Expression` enum values map onto catalog ids, keeping today's wire names as aliases (no
     saved-data change)
   - costume and animal heads are excluded (deferred in the scope file)
5. **Acceptance:**
   - a JVM test re-runs the generator and diffs the checked-in JSON
   - a test checks every `Mood` has one priority-1 expression
   - the report lists the count of expressions and distinct shapes, and the expressions whose
     mapping needs the user's eye (ambiguous moods)

### 4.2 Integrations (D-43): explore Spotify, Steam and other easy ones

The visuals already exist: `activity:listening` adds headphones and the listening badge, and
`activity:gaming` adds a controller and the gaming badge (core pack semantics). Integrations only
have to produce an automated activity. They never override a manual status (invariant 4).

| # | Item | Status | Notes |
| --- | --- | --- | --- |
| I.0 | Research note `docs/INTEGRATIONS_RESEARCH.md`: current terms, quotas and permissions for each candidate below; privacy design; recommendation | ⬜ | Ask Claude. Re-verify every API term at the time of writing |
| I.1 | **On-device "listening to music"** (no account): detect playback on the phone and publish `activity: listening` as `ANDROID_LOCAL` with a short expiry | ⬜ | Likely the easiest win. Options to weigh in I.0: `AudioManager.isMusicActive()` (no permission, but needs polling) or a notification listener with `MediaSessionManager` (event-driven and knows the app, but a sensitive permission with Play policy review). Never send track names unless the user shares `activity_name` |
| I.2 | **Spotify account link** (Web API "currently playing") | ⬜ | Needs OAuth and server polling; Spotify has tightened its developer quota rules, so verify whether a small app can get past development mode at all. If not, I.1 covers "listening" |
| I.3 | **Steam account link** (OpenID sign-in plus `GetPlayerSummaries` `gameextrainfo`) | ⬜ | Feasible (§7.1-A). Server-side polling with backoff, Steam Web API key on the server, works only when the user's game details are public. After the real backend (D-34). Source `STEAM` already exists |
| I.4 | **Other easy ones** for I.0 to rank: headphones connected (no permission), charging, a calendar busy block (on device only; titles never leave the phone), walking or driving (Activity Recognition), Twitch live (public API, server) | ⬜ | Each is opt-in, has a consent record (`integration_consents`), and gets an `activity_source` privacy category (default only me) |

### 4.3 Account and profile customization (exploration)

Customization beyond the avatar, to be designed after AV.6. None of these is scheduled yet:
- profile accent color and app theme
- alternate launcher icons
- widget style: frame shape, border, background, name label on or off
- custom quick states (Avatar Phase 6)
- bubble styles (U.1)
- a personal reaction set
- nameplate fonts within accessibility limits
- an optional pronouns field

Rules:
- Nothing becomes a follower count or a streak.
- Accessibility settings are never sold (§17.3, D-30b).
- Charge may sell cosmetics only (D-30).

### Track 0 · Audit fixes — ✅ done (2026-10-04)

| # | Item | Findings | Status |
| --- | --- | --- | --- |
| 0.1 | Widget render path rebuilt on `restingAvatar` + `VisiblePresence`; badges from resolved layers | F-01, F-02(1), F-03 | ✅ |
| 0.2 | Resolver: slot priority = highest among matching sources; record the D-31 rule; end-to-end tests | F-02(2) | ✅ |
| 0.3 | Snapshot tests (Roborazzi) for the widget path + target matrix, in CI | F-14 | ✅ |
| 0.4 | Render cache: clear on sign-out/purge, byte-sized LRU, atomic writes | F-10 | ✅ |
| 0.5 | Wallpaper-aware contrast for widgets | F-07 | ✅ |
| 0.6 | Glyphs from manifest; a11y de-duplication; scene fallthrough | F-08, F-11, F-12 | ✅ |
| 0.7 | Room migration test (1→2) | F-13 | ✅ |
| 0.9 | **PR #5 review fixes:** `LayerPriority.STATUS` for explicit status accessories; widget target from avatar size; render errors inside the fallback. Spec: `docs/handoff/PR5_FOLLOWUP_TASK.md` | F-02(3), F-21, F-22 | ✅ |
| 0.8 | Housekeeping: CI action bumps, README counts, sync local branches, delete merged branch, cold-emulator test flake | F-15, F-18, F-20 | ✅ |

### Milestone 0 · MVP foundation — ✅ done (2026-10-03)

Fake backend, presence, privacy, Status Deck, friends, reactions, widgets, docs. Details in
`docs/IDL_IMPLEMENTATION_ROADMAP.md` (historical).

### Milestone 1 · Real backend (gate for closed alpha)

| # | Item | Status | Notes |
| --- | --- | --- | --- |
| 1.1 | Schema, RLS lockdown, RPC API | ✅ | `supabase/migrations/20261003000000_idl_init.sql` |
| 1.2 | Golden privacy vectors (Kotlin ↔ SQL) | ✅ | 29 cases, mutation-checked |
| 1.3 | `SupabaseIdlBackend` + auto-selection | ✅ | |
| 1.4 | Email-code auth, refresh, sign-out | 🟡 | Needs a real project (after avatar work, D-34) |
| 1.5 | **Create the real Supabase project** and apply the migration; set the OTP email template | ⏸ | User action, after avatar work (D-34); see `supabase/README.md` |
| 1.6 | FCM: `push-fanout` edge function (drain `push_outbox`), `FirebasePushSource`, `register_device` from the app | ⏸ | Needs a Firebase project (after avatar work, D-34). The edge function can be written and unit-tested earlier |
| 1.7 | QR scanning (CameraX + ZXing) | ⬜ | Code entry works today |
| 1.8 | Crash reporting: Firebase Crashlytics per D-33 (no PII, Settings opt-out, no Google Analytics SDK); privacy-safe in-house analytics counters | ⬜ | Implement behind the `CrashReporter` seam; needs the Firebase project (1.6) |
| 1.9 | Account deletion RPC; pg_cron cleanup of expired rows; Keystore-backed session storage | ⬜ | |
| 1.10 | Invite-redeem attempt rate limit (brute-force guard) | ⬜ | Codes have ~49 bits; add a limit anyway |
| 1.11 | CI | ✅ | Running on GitHub; see F-14/F-18 for gaps |

### Avatar creator (spec: `docs/IDL_AVATAR_CREATOR_PLAN.md`)

| Phase | Scope | Status | Notes |
| --- | --- | --- | --- |
| 1 | Model v2, asset pack, compatibility, resolver, migration | ✅ | F-02(2) fixed in Track 0.2. F-11 and F-12 fixed in Track 0.6 |
| 2 | Renderer v2: paint `ResolvedLayer`s by painter key, per-base anchors, brows, availability glyphs, high-contrast and wallpaper modes, render cache, snapshot tests, RenderSheet exporter | 🟡 | Spec is `docs/handoff/PHASE_2_TASK.md`. PR A (sections 2–5, PR #21) and PR B (section 6, PR #22) are merged. PR C (sections 7–8, PR #23) ships `emoji_core` v1 and a debug-only Vector slice screen. Procedural goldens are unchanged; every render key changed once because the pack list changed. Editor, export, Noto import, and widget cutover are still open. F-09 stays 🟡. F-10 is fixed in Track 0.4 |
| 3 | Privacy contract v2 (D-24): server filters semantics, `PresenceView` v2, `asset_catalog`, new golden vectors | ⬜ | **Pulled forward as AV.4 (D-41).** Contract changes once. Include F-19 |
| 4 | Quick Creator + widget preview strip + accessibility | ⬜ | Merged into AV.6 |
| 5 | Avatar Lab (undo/redo, constrained random, saved looks, adaptive layouts) | ⬜ | Merged into AV.6 and AV.9. `material3-adaptive` approved |
| 6 | Status Deck v2: 10 editable presets (§9.3), semantic overrides editor, QS tile, launcher shortcuts, notification action, reaction overlays | ⬜ | Per-status audience deferred to Phase 9 |
| 7 | Widget hardening: size-aware targets, wallpaper colors, pre-warm cache, launcher QA matrix | ⬜ | Parts move into Track 0 |
| 8 | Final art (`core_launch` raster pack) | ⏸ | Blocked: placeholder art until the idea is validated (D-29) |
| 9 | Expansion: identity slots, duo/circle scenes, vibe suggestions, VRCQ hints, per-status audience, entitlements | ⬜ | |

### Charge economy (D-30)

| # | Item | Status | Notes |
| --- | --- | --- | --- |
| C.1 | Local prototype: `ChargeEngine`, tethers, Status Deck banner, debug toggles | ✅ | `0ea04a7`; 10 unit tests |
| C.2 | Apply the decided visibility model and guardrails: remove per-friend tether signals, show qualitative earning state | ✅ | F-05 ✅. F-06 🟡: first four guardrails are invariants; the in-app cap explanation is C.5 |
| C.3 | Server-authoritative ledger (`charge_ledger`, `claim_charge()` on server time) + server-known widget subscriptions; pin state never exposed | ⬜ | F-04, F-05. SQL can be built and tested locally now; goes live after 1.5 |
| C.4 | Spending: Charge → avatar decorations, accessories and customizations via `asset_catalog` tiers + an `entitlements` table, with a server-side `purchase_with_charge()` RPC | ⬜ | After Avatar Phase 3; never gates the §17.3 vocabulary |
| C.5 | Explain Charge in-app (cap, how tethers work) without pressure mechanics | ⬜ | Guardrails F-06 |
| C.6 | **Buy Charge (D-32):** Google Play Billing consumables; server-side purchase-token verification (Play Developer API, in an edge function) before credit; idempotent ledger entries; refund/void handling via Real-time Developer Notifications | ⬜ | Requires C.3 and the Play Console account. No client-side crediting |
| C.7 | Paid-currency compliance: Play policy for virtual currency, "Contains in-app purchases" listing, privacy policy update, refund/withdrawal terms (incl. EU), age rating, parental guidance | ⬜ | Before paid Charge ships; needs a human/legal review |

### User feature additions (from §7.1, scheduled 2026-10-04)

| # | Item | Status | Depends on | Notes |
| --- | --- | --- | --- | --- |
| U.1 | **Thought bubbles** (≤ 3 words and ≤ 24 chars): `bubble` on `PresenceState`/`VisiblePresence`, a bubble overlay category, Status Deck input, server validation in `put_presence`, `status_note` privacy category, dropped at compact sizes | ⬜ | Track 0 | §7.1-F. Small; good first feature after Track 0 |
| U.2 | **Friends-first home:** a scrollable list of all friends replaces the current grid; tapping a friend's avatar opens "how I look to them" (reusing `PrivacyFilter.viewFor` preview) with editing from there | ⬜ | Track 0 | §7.1-B, D-36. Decide where the friend's own status and reactions live on that page |
| U.3 | **Per-friend looks:** an avatar look per friend (any field may differ, D-35), with fallback to the default look. Server: `avatar_variants(owner, viewer, config)` chosen inside `presence_view`; golden vectors; per-viewer render cache keys and widget renders | ⬜ | U.2; avatar Phase 3 for the server part | Design with identity slots (Phase 9) and circles (v0.5) so a look can later be assigned to a circle too |
| U.4 | **Live weather on my avatar:** opt-in; on-device coarse location or chosen city; weather condition published as an `android_local` presence source with about 3 h expiry; `weather` privacy category + golden vectors; weather scene assets; hourly battery-aware fetch (e.g. Open-Meteo; check its terms) | ⬜ | avatar Phase 2 (scene layers); Phase 3 (privacy category) | §7.1-D, D-37 |
| U.5 | **Store:** browse and buy accessories, decorations and customizations with Charge; owned/free filter in the Avatar Lab | ⬜ | C.3, C.4, avatar Phase 3; C.6/C.7 for paid Charge | §7.1-E. Never sells §17.3 features |
| U.6 | **Widget tap action:** "communicate something from the friend" | ⏸ | Q11 | Today it keeps opening the friend profile |

### Closed alpha checklist (gate)

- [ ] Track 0 ✅ (2026-10-04). Still needed: widgets device-verified on Pixel Launcher + one OEM launcher
- [ ] Milestone 1 items 1.4–1.9 done
- [ ] Avatar Phase 3 landed (privacy contract v2)
- [ ] Real Supabase + Firebase projects; signed release build; crash reporting on
- [ ] Charge: per-friend tether signals removed (F-05); server ledger live if Charge is enabled (F-04)

### Milestone 2 · v0.5 Social depth (after the alpha shows retention)

Circles + circle-scoped rules → duo/circle/strip widgets → saved quick states, launcher
shortcuts, Quick Settings tile → postcards → deep links (Discord/Steam/VRChat/SMS) →
notification tuning → expanded cosmetics.

### Milestone 3 · v1.0 Platform (with retention evidence)

VRCQ bridge (contract and normalizer exist) → desktop companion → presence SDK + docs →
creator packs (after moderation) → verified integrations → optional E2E small-circle notes.

---

## 5. Decisions

All decisions live in `docs/IDL_DECISIONS.md` (D-01…D-49). The most relevant to current work:

- **D-21** RPC-only server API · **D-24** server filters semantics, client composes
- **D-25/26** asset packs as data plus code, shipped in the APK · **D-27** render cache
- **D-28** availability is never color-only · **D-29** legacy bases fold into five; placeholder art
- **D-30** Charge: passive resource from mutual widget tethers, spent on avatar decorations,
  accessories and customizations (2026-10-04, user)
  - **D-30a:** no per-friend tether visibility
  - **D-30b:** guardrails approved; the balance stays on the Status Deck
- **D-31** A temporary accessory chosen by the current status outranks a conflicting signature
  for the life of the status, **for every viewer**. Priority never depends on which fields the
  viewer may see. Explicit status accessories are `LayerPriority.STATUS` (above SIGNATURE); a slot
  also takes the highest priority among sources naming the same asset. Glasses return when the
  status ends; saved identity is never rewritten. (Clarified 2026-10-04 after the PR #5 review.)
- **D-32** Charge can be bought with real money via Google Play Billing; this requires a
  server-authoritative ledger with verified purchases (2026-10-04, user)
- **D-33** Crash reporting = Firebase Crashlytics, with no PII and an opt-out; no Google
  Analytics SDK (2026-10-04, delegated to Claude)
- **D-34** Real Supabase/Firebase projects get created once the user is happy with the avatars,
  avatar creation and customization (2026-10-04, user)
- **D-35** Per-friend looks may change anything (base, palette, signature, accessories, …)
- **D-36** The app home is a friends list; tapping a friend's avatar opens "how I look to them"
- **D-37** Live weather where I am shapes how my avatar appears to friends (opt-in, coarse,
  on-device location; only the weather condition is shared)
- **D-38** Platform account connections (Steam, Discord, Xbox, PlayStation, Meta Quest,
  VRChat) are on hold
- **D-39** Emoji-style layered vectors are the bootstrap avatar art (2026-10-04, user).
  Canvas and the single module stay. Noto SVG is a pinned, replaceable source, not imported
  yet. Specs under `docs/ARCHITECTURE.md` and `docs/ROADMAP.md`. The widget already draws
  availability and activity badges; a vector painter has to keep drawing them.
- **D-40** The "Earning" banner may reveal when a pin you made became mutual; accepted, since it
  only concerns friends you pinned yourself (2026-10-04, user)
- **D-41** Schema 3 is the saved avatar format everywhere, now; this pulls Avatar Phase 3 forward
  as AV.4 (2026-10-05, user, Q12 = a)
- **D-42** Original teardrop emoji face (narrow end down as a pseudo neck), every smiley-face
  emotion, built for maximum customization; no Noto import (2026-10-05, user)
- **D-43** Integrations reopened for exploration: Spotify, Steam, other easy ones; research
  first, on-device ones may come before the real backend (2026-10-05, user; amends D-38)
- **D-44** Launcher icon R1 (leaning "i" whose dot is the winking teardrop face, cream tile) and
  wordmark W1; art in `docs/art/brand/` (2026-10-05, user)
- **D-45** The avatar head is the brand teardrop, from `config/teardrop_silhouette.json` (2026-10-05, user). On the 1024 grid: crown y 96, widest 132–892 at y 476, chin bottom y 912. Eye line y 420, mouth y 580. The neck is a separate part behind the chin.
- **D-46** Teardrop is the only avatar base; other families and legacy bases retire to it (2026-10-06, user)
- **D-47** Bust framing in the app, head-and-collar framing on widgets (2026-10-06, user; Q15 = c)
- **D-48** Generous free baseline: every expression and color, 12+ hairstyles, basics in each category (2026-10-06, user; Q14)
- **D-49** Cursor runs the avatar program on its own and merges its own green PRs (2026-10-06, user)

---

## 6. Open questions for the user

Answered 2026-10-04:

| Q | Answer | Recorded as |
| --- | --- | --- |
| Per-friend tether visibility? | **No** | D-30a, F-05 |
| Charge guardrails? Balance on the Status Deck? | **Yes** to both | D-30b, F-06 |
| What does Charge buy; is Charge purchasable? | Avatar decorations, accessories, customizations; **yes**, purchasable | D-30, D-32, C.4, C.6, C.7 |
| Crash-reporting vendor? | Delegated → **Firebase Crashlytics** | D-33, 1.8 |
| When are the real projects created? | Once avatars, creation and customization feel right | D-34, roadmap order |
| "Earning" reveals when a friend you pinned pins you back: batch, drop, or accept? | **Accept** (only friends you pinned yourself) | D-40, F-05 |

Answered 2026-10-04 (feature ideas, §7.1):

| Q | Answer | Recorded as |
| --- | --- | --- |
| Q6 Spark vs Charge? | "Spark" was a mistake; the name stays **Charge**, and the mutual-pin rule (D-30) is unchanged | Proposal C withdrawn |
| Q7 What may a per-friend look change? | **Anything**, including base, palette and signature | D-35 |
| Q8 Tap targets? | In the app, tapping a friend's avatar opens "how I look to them" (config). Tapping a friend's **home-screen widget** should communicate something *from* that friend: **not designed yet** | D-36; open Q11 |
| Q9 Whose weather? | Live weather where **I** am changes how **my** avatar is presented to friends | D-37 |
| Q10 Platform account connections? | **Hold off** for now. Reopened for exploration 2026-10-05 | D-38, D-43 |
| Q12 How does the editor save? | **(a)** schema 3 everywhere, now (2026-10-05) | D-41, AV.4 |
| Q13 Redraw the hair? | **Yes**, on the new teardrop base (2026-10-05) | D-42, AV.2 |
| Q14 What is always free? | **The generous baseline** Claude recommended (2026-10-06) | D-48 |
| Q15 How much body shows, for shirts? | **(c)** bust in the app, head and collar on widgets (2026-10-06) | D-47 |

Open:

11. **Widget tap action (Q8 follow-up):** what should tapping a friend's home-screen widget
    communicate from that friend? Until decided, it keeps opening the friend profile.
    Ideas to consider: their latest reaction to you, their bubble or note, a "they're
    around" prompt, or a quick-react sheet.

Add new questions here as they come up.

---

## 7. Ideas and proposals

### 7.1 Feature ideas (user, 2026-10-04): decided, see the status line on each

Each proposal has a summary, constraints (feasibility, privacy and conflicts with existing
decisions), proposed placement, and the decisions it needs. Once confirmed, move it into §4 with
task IDs and record a decision in `docs/IDL_DECISIONS.md`.

#### A · Platform sign-ins with presence badges

**Status: reopened for exploration (D-43, 2026-10-05).** Spotify and Steam come first, through
§4.2. The feasibility table below is from 2026-10-04 and gets re-verified in I.0. Feature flags
stay off until an item ships.

**Idea:** link Steam, Discord, Xbox, PlayStation, Meta Quest and VRChat accounts. Each shows its
own badge while the user is seen on that platform (playing, online, in a world). Proposed badge
looks: Steam = round Steam logo; Discord = blue game controller; Xbox = round Xbox logo;
PlayStation = PlayStation logo; Meta Quest = round Meta "M"; VRChat = "VRC" chat logo.

**Constraints:**
- **Integration policy** (product spec non-goals, D-09, feature flags in `Core.kt`): only
  official, user-authorized APIs; **no scraping**; integrations are optional enrichment and may
  only add an activity badge, never override manual mood or availability.
- **Feasibility per platform** (re-verify current API terms before building):

  | Platform | Official path | Assessment |
  | --- | --- | --- |
  | Steam | OpenID sign-in + Steam Web API `GetPlayerSummaries` (current game when the profile is public) | ✅ Feasible. Needs a Steam Web API key and server-side polling with a modest interval (no push from Steam) |
  | Discord | OAuth2 identifies the user but does **not** expose online presence to third-party apps. Presence needs a bot sharing a server with the user (privileged presence intent) or Discord's game-oriented SDK | ⚠️ Hard. Needs research and possibly a Discord app review |
  | Xbox | Xbox services presence normally requires a registered title / partner program; third-party wrappers are unofficial | ⚠️ Unclear. Research the official access route first |
  | PlayStation | No public third-party presence API; known endpoints are unofficial | ❌ Not feasible under the no-scraping policy unless Sony offers a program |
  | Meta Quest | No public API exposing a user's online status to other apps | ❌ Not feasible as a sign-in. Alternative: an iDL Quest companion app that reports its own presence (fits the v1.0 SDK/bridge plan) |
  | VRChat | No official public API for third parties; unofficial API use is against our policy (master plan, product spec) | ➡️ Use the planned **VRCQ / desktop bridge** (local, user-authorized), which already exists as a contract (`EnvelopeNormalizer.fromVrcq`) |

- **Logos are trademarks.** Using official logos needs each brand's guidelines (and sometimes
  permission), so get a legal review before shipping. Until then, use neutral glyphs (e.g. a
  controller, a headset). The asset manifest's `license` field must record the terms for any
  logo asset. The "blue controller" idea for Discord avoids the logo issue but could be confused
  with the generic gaming badge.
- **Privacy:** the platform *source* is new information. Add a visibility category
  `activity_source` (default `only_me`, like `activity_name`), filter it in `presence_view`, and
  extend the golden vectors. Account linking needs explicit consent records
  (`integration_consents` already exists) and one-tap revoke that deletes that source's states.
- **Server cost:** polling platforms server-side scales with linked users. Use
  backoff/inactivity-aware intervals and never poll from the phone.

**Placement:** Milestone 3 (v1.0 platform). The **Steam** link is the best first integration once
the real backend exists (after D-34). VRChat goes through the VRCQ bridge.
**Needs:** Q10; per-platform API research spike; legal review of logos.

#### B · Friends-first home + per-friend avatars

**Status: ✅ decided, scheduled as U.2 / U.3** (D-35, D-36). The user chose that per-friend looks may change *everything*, so the "keep base/palette fixed" recommendation below is superseded. Keep the friend's name visible next to every render so they stay identifiable.

**Idea:** the app opens on a scrollable list of all iDL friends. Tapping a friend's avatar opens
a page showing **how my avatar appears to that friend**, where I can change my avatar. I can
configure my avatar **differently for each friend**. Not every friend needs a widget.

**Constraints:**
- **Already partly exists:** Privacy Center "preview as a friend" computes exactly what a friend
  sees (`PrivacyFilter.viewFor`). The new page can reuse it.
- **Per-friend avatars affect the core architecture:**
  - The server must return a viewer-specific identity: a new `avatar_variants(owner, viewer | circle,
    config)` table, chosen inside `presence_view` (D-24). This needs new golden vectors.
  - Render-cache keys and widget renders become per viewer.
  - It overlaps with **identity slots** (avatar Phase 9) and **circles** (v0.5). Design them
    together: "a look" assigned to a friend, a circle, or the default.
- **Recognizability** (master plan §3.3, §8.5): the avatar must stay identifiable as *you*.
  Recommend that per-friend looks may change accessories, props, scene, bubble and expression
  style, but **not** base, palette or signature (Q7).
- **Privacy:** a per-friend look must never reveal another friend's look, and should pass
  through the same category filtering.
- **Home redesign** replaces today's grid plus "my card" (`ui/home/HomeScreen.kt`). Q8 decides
  how the friend's own status and reactions are reached.

**Placement:** avatar Phase 5/6 timeframe (it's avatar customization, the current focus), after
Track 0. The server part lands with avatar Phase 3 (privacy contract v2), so the contract changes
once. **Needs:** Q7, Q8.

#### C · "Spark" from friends pinning your widget

**Status: ❌ withdrawn (Q6, 2026-10-04).** "Spark" was a mistake; it stays **Charge** with the mutual-pin rule (D-30). The notes below are kept only as history.

**Idea:** when a friend uses your avatar widget on their phone, it generates **Spark**.

**Constraints:**
- This looks like Charge (D-30) under a new name, with a different rule. Today Charge accrues
  only for *mutual* pins (you pin them **and** they pin you); the idea credits you when *they*
  pin *you* (Q6).
- **D-30a (no per-friend pin visibility):** one-way crediting makes pins easier to infer, since
  any balance change after a friend adds a widget reveals it. Mitigations: credit server-side in
  daily batches, show only a qualitative earning state, never show per-friend sources.
- **Farming and abuse:** one-way crediting rewards getting pinned. Combined with purchasable Spark
  (D-32), add anti-abuse rules: a pin counts only from accepted friends, with per-friend daily
  caps and diminishing returns, verified server-side (C.3).
- If confirmed: rename Charge → Spark in code, UI and docs in one PR, and update D-30.

**Placement:** Charge track (C.2/C.3). **Needs:** Q6.

#### D · Weather background addon

**Status: ✅ decided, scheduled as U.4** (D-37). Option (b) chosen: my live weather shapes how
*my* avatar appears to friends. Design constraints that follow from that choice:

- **Opt-in**, off by default. It gets a new privacy category `weather` (default
  `close_friends` once enabled), filtered in `presence_view`, with golden vectors.
- **Location never leaves the phone.** Coarse location or a manually chosen city is used
  on-device only. The device publishes just a weather *condition* (clear, cloudy, rain, snow,
  storm, fog, plus day/night).
- It's sent as an automated presence source (`android_local`, priority 40) with a short expiry
  (about 3 h). That means manual status wins (D-09), Invisible hides it, and expiry clears it
  if updates stop.
- The resolver maps conditions to weather **scene layers** that sit below explicit status
  scenes and above the default scene.

**Idea:** the widget background updates automatically to the current weather where you are.

**Constraints:**
- **Location:** the product spec puts exact location out of scope. Use **coarse** location
  (`ACCESS_COARSE_LOCATION`), or let the user pick a city manually (no permission at all). The
  manual option is the privacy-friendly default.
- **Whose weather (Q9):**
  - (a) the viewer's own weather on their own home screen: no data leaves the device, simplest
    and private;
  - (b) my weather shown on friends' widgets: reveals approximate location over time, so it
    needs a privacy category (e.g. `weather`, default `only_me`), golden vectors and opt-in.
- **Implementation:**
  - weather becomes a **scene override layer** in the resolver (below explicit status scenes,
    above default scenes);
  - a small set of weather scenes in the asset pack (clear, cloudy, rain, snow, storm,
    night variants);
  - fetch from a no-key API such as Open-Meteo (check its attribution/licence terms) via
    WorkManager at most hourly, battery-aware, never polling for widgets.
- It must stay subordinate to the face (master plan §6.5) and simplified at compact sizes.

**Placement:** avatar Phase 7 (widget hardening) or right after. **Needs:** Q9.

#### E · Store for accessories, decorations and customizations

**Status: ✅ scheduled as U.5** (follows C.3 and avatar Phase 3; ships with or after C.6/C.7).

**Idea:** a store where Spark (Charge) is spent on accessories, decorations and customizations.

**Constraints:** this is the UI for C.4 (spend), and with C.6 (buy Spark via Play Billing) it
needs the server-authoritative ledger first (F-04, D-32), plus `asset_catalog` tiers and an
`entitlements` table (avatar Phase 3). Never sell core vocabulary, privacy, widget or
accessibility features (§17.3, D-30b). Owned items show in the Avatar Lab with an owned/free
filter (avatar plan Phase 5).

**Placement:** after avatar Phase 3 + C.3; ship together with or after C.6/C.7.

#### F · Thought bubbles (three words max)

**Status: ✅ scheduled as U.1** (good early win after Track 0).

**Idea:** a small word or thought bubble on the avatar, at most three words.

**Constraints:**
- **Validation:** "3 words" doesn't work for languages without spaces (CJK, Thai). Use
  **≤ 3 words and ≤ 24 characters**, validated in the client and server (`put_presence`).
- **Legibility:** at 48 px a bubble can't be read. Show it at standard/large widget sizes and
  in-app; at compact sizes drop it (`TARGET_SIMPLIFIED`) or show a "…" bubble glyph.
- **Privacy:** it's free text like the status note. Either reuse the `status_note` category or
  add `bubble` (recommend reusing `status_note`, so it's one setting). It expires with the
  status, and is never logged (`IdlLog` rule).
- **Model:** add `bubble: String?` to `PresenceState`/`VisiblePresence` and a
  `speech_bubble`/`thought_bubble` overlay asset category with anchors per base.

**Placement:** avatar Phase 6 (Status Deck v2). Small and self-contained, so it's a good early
win once Track 0 is done.

### 7.2 Parking lot (not scheduled)

- RenderSheet contact-sheet exporter for comparing art directions at widget size (Avatar Phase 2).
- On-device "vibe" keyword suggestions mapping to catalog tags, before any LLM (Phase 9).
- Widget "stale" indicator styling pass; show "updated Xh ago" consistently.
- Expiry labels round down ("7h left" right after setting 8h); consider "~8h left".
- Replace the brittle `lastSyncError.contains("Offline")` check on Home with a typed sync state.

---

## 8. Work log

| Date | Who | What | Refs |
| --- | --- | --- | --- |
| 2026-10-03 | Claude | Milestone 0 foundation; docs; 63 unit + 8 instrumented tests | `aaed762` |
| 2026-10-03 | Claude | Supabase backend, golden vectors, client, CI workflow | `52c95aa` |
| 2026-10-03 | Claude | Avatar creator plan; Cursor handoff (AGENTS.md, rules, Phase 1 spec) | `46ef1ed` |
| 2026-10-04 | Cursor | Avatar Phase 1 (pack, resolver, compat, migration); 127 tests | `b42a628`…`0a6d2f0`, PR #1 |
| 2026-10-04 | Cursor | Charge prototype (local), Room v2 | `0ea04a7`, PR #1 |
| 2026-10-04 | Cursor | Availability shape glyphs | `d4a336e`, PR #2 |
| 2026-10-04 | Cursor | Widgets painted from resolved avatar (introduced F-01–F-03) | `16f0343`, PR #3 |
| 2026-10-04 | User/Claude | Feature ideas A–F analysed (§7.1); answers Q6–Q10 → D-35…D-38; scheduled U.1–U.6; platform sign-ins on hold; Spark withdrawn; Q11 open | docs/feature-ideas |
| 2026-10-04 | User/Claude | Decisions D-30a/b, D-32, D-33, D-34 recorded; roadmap reordered (avatars before real backend projects) | this PR |
| 2026-10-04 | Claude | Full audit: 141 JVM ✅, 9/9 device ✅ (one flake), CI ✅; device and JVM probes confirmed F-01/F-02; this master plan; findings F-01…F-20 | this commit |
| 2026-10-04 | Cursor | Track 0.1–0.2: widget presence, badges from resolved layers, D-31 slot priority. 149 JVM, 11/11 device | `ca70087`, PR #5 |
| 2026-10-04 | Claude | Review of PR #5: F-01/F-03 fixed; F-02 still fails for non-close friends; new F-21, F-22; D-31 clarified; follow-up spec `docs/handoff/PR5_FOLLOWUP_TASK.md` | PR #5 |
| 2026-10-04 | Cursor | PR #5 follow-up: STATUS accessories, default 2×2 widget uses STANDARD, render failures stay in the fallback. 154 JVM, 12/12 device | PR #5 |
| 2026-10-04 | Cursor | Avatar base exploration v2: the family picks were withdrawn, and blob A's light and dark 48px sheets are the temporary stand-in | `docs/handoff/reports/2026-10-04-avatar-bases-v2.md`, PR #7 |
| 2026-10-04 | Cursor | D-39: emoji-style vector compositor design. Canvas path IR, no Noto import yet | `docs/ARCHITECTURE.md`, `docs/ROADMAP.md`, PR #8 |
| 2026-10-04 | Cursor | Avatar recipe schema 3: pack, family, colors, transforms, background. Schema 2 still decodes | `AvatarConfiguration.migrateRecipe`, PR #9 |
| 2026-10-04 | Cursor | Remove per-friend tether signals. Status Deck shows balance and earning / not earning. Guardrails 10–13 in AGENTS.md. 161 JVM, 15/15 device | `b28e44e`, PR #12, F-05 ✅, F-06 🟡, C.2 ✅ |
| 2026-10-04 | Cursor | CI actions bumped to checkout v7, setup-java v6, setup-gradle v6; runner pinned to ubuntu-24.04 | F-18, PR #10 |
| 2026-10-04 | Cursor | F-14: JVM widget snapshots in CI. 165 tests, 0 failed, 1 skipped | `3555e44`, PR #11 |
| 2026-10-04 | Cursor | Room 1→2 migration test with the exported schemas. 161 JVM, 16/16 device | `492019e`, PR #13, F-13 ✅, Track 0.7 |
| 2026-10-04 | Cursor | Glyphs from the manifest, de-duplicated accessibility sentences, unknown scenes fall through. 163 JVM, 15/15 device | `cf8134b`, PR #15, F-08 ✅, F-11 ✅, F-12 ✅, Track 0.6 |
| 2026-10-04 | Cursor | Widget outline contrast follows the wallpaper. Auto / Light / Dark setting. 167 JVM | `820cb81`, PR #16, F-07 ✅, Track 0.5 ✅ |
| 2026-10-04 | Cursor | Render cache: sign-out clear, per-friend files, 8 MB byte LRU, atomic writes. 165 JVM, 17/17 device | `08be0d3`, PR #14, F-10 ✅, Track 0.4 |
| 2026-10-04 | Cursor | Render cache memory access is locked. A purged owner does not keep a late file. Sign-out deletes files on IO. 167 JVM, 17/17 device | `6826e6e`, PR #14 |
| 2026-10-04 | Cursor | Snapshot follow-ups: upload-artifact v7, sleepy compact vs standard, required asset registry on the config renderer. 181 JVM, 18/18 device | `492a5ca`, PR #17, F-23 ✅, F-24 ✅, F-25 ✅, F-26 ✅ |
| 2026-10-04 | Cursor | Housekeeping and schema 3 follow-ups: PowerShell check script, boot wait, doc drift, recipe load and write guard. 183 JVM, 18/18 device | `a88b1de`, PR #18, F-15 ✅, F-20 ✅, F-27 ✅, F-28 ✅, F-29 🟡 |
| 2026-10-04 | Claude | Audit of PRs #17–#18. Merged #17 into #18 and renumbered #18's findings to F-27–F-29 (they collided with #17's F-23–F-25). F-29 set to 🟡: the write guard isn't wired because no recipe is persisted yet, so the unused Avatar Studio `recipe` parameter was removed. D-40 recorded (accept the Earning timing signal). 184 JVM, 18/18 device, check.ps1 matches | PR #18, F-29, D-40 |
| 2026-10-04 | Claude | Phase 2 task spec: picture IR, path parser, validator, `itemIds`, color slots, compositor order, Canvas vector renderer, original `emoji_core` slice, debug screen; three PRs | `docs/handoff/PHASE_2_TASK.md` |
| 2026-10-04 | Cursor | Deleted merged branches: avatar-creator, avatar/schema-3, charge/no-tether-signals-f05, ci/action-bumps-f18, docs/avatar-bases-v2, docs/emoji-avatar-architecture, docs/feature-ideas, docs/master-plan, fix/glyphs-a11y-scenes-f08-f11-f12, fix/render-cache-f10, fix/widget-presence-f01-f03, test/roborazzi-f14, test/room-migration-f13, test/snapshot-followups, widget/wallpaper-contrast-f07, chore/docs-tooling-schema3, and local milestone-1-supabase | branch cleanup |
| 2026-10-04 | Cursor | Phase 2 PR A: vector picture model, path parser, validator, item ids, color slots, composite order. JVM only. Existing Roborazzi goldens unchanged. 207 JVM. F-09 stays 🟡 | `7f01c94`, PR #21, `docs/handoff/reports/2026-10-04-vector-domain.md` |
| 2026-10-04 | Claude | Audit of PR #21 (Phase 2 PR A): no blocking issues. Added a broader procedural-order parity test (face styles and worn items, verified by a band mutation) and a registry error for an expression redefined by a second pack | PR #21 |
| 2026-10-04 | Cursor | Phase 2 PR B: picture cache, Canvas vector renderer, compositor-driven resolved bitmap. Procedural goldens unchanged, plus a test-only `vector_dot` golden. 213 JVM, 18/18 device. F-09 stays 🟡 | `e7813de`, `8281a04`, PR #22, `docs/handoff/reports/2026-10-04-vector-renderer.md` |
| 2026-10-04 | Claude | Audit of PR #22 (Phase 2 PR B): goldens verified before and after the refactor. Fixed a vector scene dropping the frame clip (regression test added). 214 JVM, 18/18 device | PR #22 |
| 2026-10-04 | Cursor | Phase 2 PR C: original `emoji_core` slice and a debug-only Vector slice screen. Procedural pixels unchanged. 216 JVM, 21/21 device. F-09 stays 🟡 | `6bc0a4a`, `0a28713`, PR #23, `docs/handoff/reports/2026-10-04-emoji-core-slice.md` |
| 2026-10-04 | Claude | Audit of PR #23 (Phase 2 PR C): acceptance met; 216 JVM, 21/21 device; follow-ups: compact-target slice snapshots before the widget cutover, and the hair art reads as a hood at small sizes | PR #23 |
| 2026-10-05 | Claude | Merged PR #23. Phase 2 vector core done (PRs #21–#23). Plan refreshed for handoff: §4.0 Next up, F-30 and F-31 added, F-16 and F-17 closed, F-09 status, Q12 (editor persistence) and Q13 (hair art) opened | `7a175a6` |
| 2026-10-05 | Claude | Avatar vision recorded: D-41 (schema 3 saved everywhere), D-42 (original teardrop face, every smiley emotion, maximum customization, no Noto), D-43 (integrations exploration). New §4.1 Avatar program with AV.2 and AV.3 specs, §4.2 Integrations, §4.3 account customization; Next up rewritten; Q14 and Q15 opened; scope file adds face-hand | this PR |
| 2026-10-05 | Cursor | AV.2: teardrop base and hair redraw in `emoji_core` v2. Procedural goldens unchanged. 220 JVM, 21/21 device, lint 0/42. F-30 ✅ | `b49afe0`, PR #27, `docs/handoff/reports/2026-10-05-teardrop-base.md` |
| 2026-10-05 | Claude | Brand: launcher icon R1 and wordmark W1 chosen (D-44). New adaptive foreground, cut-out monochrome layer, cream background; SVG masters and generator in `docs/art/brand/`; `LauncherIconSnapshotTest` with 4 goldens | `docs/handoff/reports/2026-10-05-launcher-icon.md`, branch `brand/launcher-icon` |
| 2026-10-06 | Claude | Play Store icon: `docs/art/brand/playstore-icon-512.png`, rendered from the launcher drawables; `LauncherIconSnapshotTest` fails if it goes stale (`-Pidl.updateGolden=true` refreshes it) | `docs/handoff/reports/2026-10-06-playstore-icon.md`, branch `brand/playstore-icon` |
| 2026-10-06 | Claude | Splash screen shows the W1 wordmark: Android 12+ splash icon (`values-v31`) and an Android 8–11 launch theme, light and dark; `MainActivity` switches to `Theme.Idl` before drawing. 227 JVM, lint 0/42, checked on `emulator-5554` (API 37) | `docs/handoff/reports/2026-10-06-splash-wordmark.md`, branch `brand/splash-wordmark` |
| 2026-10-06 | Claude | Onboarding shows the W1 wordmark instead of "iDL" text: new generated `wordmark.xml` (cropped, light/dark colors), `InlineWordmarkTest`. 230 JVM, lint 0/42, checked on `emulator-5554` (API 37) | `docs/handoff/reports/2026-10-06-onboarding-wordmark.md`, branch `brand/onboarding-wordmark` |
| 2026-10-06 | User/Claude | Avatar program planned: `docs/avatar/AVATAR_PROGRAM.md` (AP-1…AP-16), `ART_STYLE_GUIDE.md`, `docs/handoff/CURSOR_RUNBOOK.md`; Cursor rules and PR template updated; D-45…D-49 recorded; Q14 and Q15 answered | branch `docs/avatar-program` |
| 2026-10-06 | Cursor | AP-1: the avatar head is the brand teardrop. 234 JVM, 21/21 device, lint 0/42 | `docs/handoff/reports/2026-10-06-ap-1-brand-silhouette.md` |
| 2026-10-06 | Cursor | AP-2: each picture loads from its own pack directory (F-31). 235 JVM, 21/21 device, lint 0/42 | `docs/handoff/reports/2026-10-06-ap-2-pack-dirs.md` |
| 2026-10-06 | Cursor | AP-3 framing: head and bust viewports, body bands. Body art is the next PR. 241 JVM, 21/21 device, lint 0/42 | `docs/handoff/reports/2026-10-06-ap-3-framing.md` |
| 2026-10-06 | Cursor | AP-3 body: neck, shoulders and the default crew tee. 243 tests, 0 failed, 1 skipped. Lint 0 errors, 42 warnings. Device check passed on emulator-5554 (21 instrumented tests). | `docs/handoff/reports/2026-10-06-ap-3-body.md` |
| 2026-10-06 | Cursor | AP-4 picture format v2: strokes, tags and mask syntax. Masks are applied in AP-8. 246 tests, 0 failed, 1 skipped. Lint 0 errors, 42 warnings. Device check passed on emulator-5554. | `docs/handoff/reports/2026-10-06-ap-4-picture-v2.md` |
