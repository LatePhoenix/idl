# iDL — Master Plan (living document)

> **This is the single source of truth for project status, next steps and open findings.**
> Cursor and Claude both read and update it. Detailed specs live in `docs/` and are linked
> from here; this file says *what state everything is in* and *what to do next*.

Last full review: **2026-10-04 by Claude** (audit of everything through `origin/main` `9f033e0`).

---

## 0. How to use this document

**Before starting work:** read §1 (state) and §3 (open findings). Pick the highest-priority
open item (§4 shows the order). Read its linked spec.

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
home screen) checked on a device or by snapshot test. CI being green isn't enough on its own:
CI has no device or snapshot tests yet (F-14).

---

## 1. Current state (2026-10-04)

### 1.1 Branches and CI

| Item | State |
| --- | --- |
| Remote | `github.com/LatePhoenix/idl` (private) |
| `origin/main` | `9f033e0`: everything below is merged here (PRs #1–#3) |
| Local `main` | 13 commits behind `origin/main`. Run `git checkout main && git pull` |
| `avatar-creator` | Same content as `origin/main` minus merge commits. Start new work from `origin/main` |
| `milestone-1-supabase` | Fully merged; can be deleted |
| CI (`.github/workflows/ci.yml`) | ✅ Green on every PR. Jobs: `android` (unit tests, lint, debug build) and `backend` (SQL suite + PostgREST IT). **No device or snapshot tests** |

### 1.2 Verified numbers (Claude, 2026-10-04 on `origin/main`)

| Check | Result |
| --- | --- |
| `scripts/check.sh` | ✅ 141 JVM tests, 0 failed, 1 skipped (`SupabaseRestIT` runs in CI's backend job). Lint 0 errors / 39 warnings |
| Instrumented tests (Pixel 9 emulator, API 37) | ✅ 9/9 pass. `StatusDeckTest.quickStateIsOneTap` failed once on a cold emulator, then passed twice (F-20) |
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
| Widgets: solo friend + self, pinning, config activity | 🔴 | **Regressed in PR #3: no availability/activity badges, VR headset lost** (F-01, F-02) |
| Avatar v2 domain (model, pack, resolver, compat, migration) | ✅ | Avatar Phase 1; 46+ tests |
| Availability as shape glyphs (D-28) | 🟡 | Drawn in the app; **not drawn on widgets** (F-01) |
| Render cache | 🟡 | Exists; hygiene issues (F-10) |
| Charge economy (passive, mutual widget tethers) | 🟡 | Local-only prototype; design open (F-04–F-06, D-30) |
| Push delivery (FCM) | ⬜ | Outbox and `register_device` exist; no edge function or app receiver |
| Crash reporting / analytics | ⬜ | `CrashReporter` seam only |

---

## 2. Reference map

| Document | Purpose |
| --- | --- |
| `AGENTS.md` | Agent rules: invariants, commands, conventions, report format |
| `docs/IDL_PRODUCT_SPEC.md` | Product principles, MVP scope, non-goals |
| `docs/IDL_DECISIONS.md` | Every recorded decision (D-01…D-30). **Don't reopen without asking the user** |
| `docs/IDL_ARCHITECTURE.md` · `IDL_DATA_MODEL.md` · `IDL_PRIVACY_MODEL.md` · `IDL_WIDGET_ARCHITECTURE.md` · `IDL_API_CONTRACT.md` | Technical design |
| `docs/IDL_AVATAR_CREATOR_MASTER_PLAN.md` | Avatar product requirements (cited as §n) |
| `docs/IDL_AVATAR_CREATOR_PLAN.md` | Avatar phases 1–9, with detail |
| `docs/handoff/PHASE_1_TASK.md` | Avatar Phase 1 spec (done) |
| `supabase/README.md` | Server: security model, local tests, real-project setup |
| `docs/IDL_IMPLEMENTATION_ROADMAP.md` | **Superseded by this file**; kept for history |

---

## 3. Open findings from the 2026-10-04 audit

Each finding has: ID · priority · status · evidence · fix · how to verify. Fix P0 items
first and **in order**. F-01, F-02 and F-03 share a root cause and should be fixed together
in one PR.

### P0 — regressions on the primary product surface (the home-screen widget)

#### F-01 · Widgets draw no availability glyph and no activity badge · 🔴 P0
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

#### F-02 · VR headset disappears for anyone whose signature is glasses · 🔴 P0
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

#### F-03 · Widget rendering ignores presence; status is baked into identity · 🔴 P0
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

#### F-05 · "Friend pinned my widget" is a new private behavioural signal · ⬜ P1 (decided 2026-10-04)
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

#### F-06 · Charge guardrails · ⬜ P1 (approved 2026-10-04)
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

#### F-07 · Widgets hard-code dark-wallpaper contrast · 🟡 P1
- `Widgets.kt` always uses `WallpaperContrastMode.DARK_WALLPAPER`, so light-wallpaper users get
  a light outline. **Fix:** derive the mode from `WallpaperManager.getWallpaperColors()`
  (`HINT_SUPPORTS_DARK_TEXT`, API 27+), with a user setting and an API 26 fallback (Avatar
  Phase 7 item, but it's shipping now).

#### F-08 · Glyph names duplicated in code and manifest · 🟡 P1
- `domain/StatusGlyphs.kt` hard-codes what `core_proto` already declares in each asset's
  `glyph`. **Fix:** the renderer reads `registry.asset(layer.assetId).glyph`; delete
  `StatusGlyphs` or keep it as a test-only cross-check.

#### F-09 · `PlaceholderFrame` adapter is lossy · 🟡 P1 (resolved by Avatar Phase 2)
- It ignores resolved brows, eye and mouth families, essential overlays chosen by
  semantics/overrides, extras (sick → bandage), reactions and confetti. Critter without ears
  renders as a blob.
- **Fix in Phase 2:** paint each `ResolvedLayer` by `AssetDef.painterKey`, then delete the
  adapter.

#### F-10 · Render cache hygiene · 🟡 P1
- Rendered friend images in `cacheDir/renders` survive friend removal, block and sign-out. They
  aren't displayed again, but revoked data should leave the device (privacy rules 5–6).
- `LruCache` counts entries, not bytes (D-27 targets about 8 MB; share-card renders are 1 MB each).
- File writes aren't atomic.
- **Fix:** clear the cache in `signOut()`; key or tag files by friend and delete them in
  `purgeUser`; size the LRU by `bitmap.byteCount`; write to a temp file then rename.

#### F-11 · Accessibility sentence repeats itself · ⬜ P1
- DND produces "Blob avatar, do not disturb, do not disturb" (expression label plus
  availability label). **Fix:** de-duplicate labels case-insensitively; add a test.

#### F-12 · Unknown presence scene replaces the saved scene · ⬜ P2
- `resolveFamily` returns the pack default on the first unknown ID instead of trying the next
  source. **Fix:** unknown non-identity sources fall through; only identity slots fall back to
  defaults. (Open question from Cursor's Phase 1 report.)

#### F-13 · No Room migration test · ⬜ P1
- DB v1→v2 (`MIGRATION_1_2`) has no `MigrationTestHelper` test, although schemas 1 and 2 are
  exported. Required by invariant 5 before the next schema change.

### P2/P3 — process and maintenance

#### F-14 · CI can't catch visual or widget regressions · ⬜ P1
- F-01 and F-02 merged with green CI. **Fix:** add Roborazzi + Robolectric (already approved)
  snapshot tests for the widget render path and the avatar target matrix, run in CI.
  Optionally add an emulator job for `connectedDebugAndroidTest`. Make "device-check visual
  changes" part of the PR template.

#### F-15 · Documentation drift · ⬜ P2
- `AGENTS.md` "Current work" still pointed at Phase 1 (fixed in this commit).
- The README test counts are stale (78 → 141).
- `IDL_AVATAR_CREATOR_PLAN.md` §7 says Phase 1 isn't wired into the app, but widgets now use
  the resolver.
- **Fix:** keep counts and status here only; README links here.

#### F-16 · Checkpoint report inaccuracy · ⬜ P3
- `docs/handoff/reports/2026-10-04-charge.md` says "Not committed" and "Commits: none", but it
  shipped in `0ea04a7`. Reports must be finalized after committing.

#### F-17 · Phase 2 work merged without a spec or device check · ⬜ P3 (process)
- Glyphs, contrast, render cache and the widget resolver path landed piecemeal (PRs #2–#3).
  **Rule from now on:** each avatar phase gets a task spec in `docs/handoff/` first, and visual
  PRs need a device screenshot or snapshot test.

#### F-18 · CI action versions deprecated · ⬜ P3
- `actions/checkout@v4`, `actions/setup-java@v4` and `gradle/actions/setup-gradle@v4` run on
  deprecated Node 20. `ubuntu-latest` moves to Ubuntu 26 on 2026-10-19. Bump to the current
  major versions and pin the runner image.

#### F-19 · Defence in depth for explicit expressions · ⬜ P2 (with Avatar Phase 3)
- The resolver trusts `VisiblePresence.expressionId` even when `mood` is absent. That's
  acceptable under D-24 (the server never sends it then). Add a Phase 3 golden vector proving
  the server omits `visual.expression` when mood is hidden.

#### F-20 · Flaky first instrumented test on a cold emulator · ⬜ P3
- `StatusDeckTest.quickStateIsOneTap` failed with "No compose hierarchies found" on the first
  run after the emulator booted, then passed twice. **Fix:** wait for boot and unlock the screen
  in `scripts/check.sh --device` (`adb wait-for-device`, `input keyevent 82`), or add a test
  rule that retries activity launch once.

---

## 4. Roadmap

Order of work (updated 2026-10-04 per user: real backend projects come once avatars feel right):

1. **Track 0** (audit fixes)
2. **Avatar Phases 2, 4, 5, 6, 7**: the avatar look, creation and customization. This is the
   current product focus.
3. Then, when the user is happy with avatars: **Milestone 1.5–1.6** (the user creates the
   Supabase + Firebase projects), and **Avatar Phase 3** and **Charge C.3/C.6** against the
   real backend. Phase 3 can be built and tested earlier against the local Docker stack.
4. Closed alpha → Avatar Phase 8 (final art) → v0.5 → v1.0.

Server-only work that doesn't need a real project (SQL migrations, golden vectors, local
PostgREST tests) can be done at any time.

### Track 0 · Audit fixes (do first)

| # | Item | Findings | Status |
| --- | --- | --- | --- |
| 0.1 | Widget render path rebuilt on `restingAvatar` + `VisiblePresence`; badges from resolved layers | F-01, F-02(1), F-03 | ⬜ |
| 0.2 | Resolver: slot priority = highest among matching sources; record the D-31 rule; end-to-end tests | F-02(2) | ⬜ |
| 0.3 | Snapshot tests (Roborazzi) for the widget path + target matrix, in CI | F-14 | ⬜ |
| 0.4 | Render cache: clear on sign-out/purge, byte-sized LRU, atomic writes | F-10 | ⬜ |
| 0.5 | Wallpaper-aware contrast for widgets | F-07 | ⬜ |
| 0.6 | Glyphs from manifest; a11y de-duplication; scene fallthrough | F-08, F-11, F-12 | ⬜ |
| 0.7 | Room migration test (1→2) | F-13 | ⬜ |
| 0.8 | Housekeeping: CI action bumps, README counts, sync local branches, delete merged branch, cold-emulator test flake | F-15, F-18, F-20 | ⬜ |

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
| 1 | Model v2, asset pack, compatibility, resolver, migration | ✅ | Reviewed 2026-10-04; F-02(2), F-11, F-12 to fix |
| 2 | Renderer v2: paint `ResolvedLayer`s by painter key, per-base anchors, brows, availability glyphs, high-contrast and wallpaper modes, render cache, snapshot tests, RenderSheet exporter | 🟡 | Partially landed out of order (glyphs, contrast, cache, widget path). **Needs a task spec** (`docs/handoff/PHASE_2_TASK.md`) covering F-01, F-03, F-07–F-10, F-14 |
| 3 | Privacy contract v2 (D-24): server filters semantics, `PresenceView` v2, `asset_catalog`, new golden vectors | ⬜ | Must land before the closed alpha (contract changes once). Include F-19 |
| 4 | Quick Creator + widget preview strip + accessibility | ⬜ | |
| 5 | Avatar Lab (undo/redo, constrained random, saved looks, adaptive layouts) | ⬜ | `material3-adaptive` approved |
| 6 | Status Deck v2: 10 editable presets (§9.3), semantic overrides editor, QS tile, launcher shortcuts, notification action, reaction overlays | ⬜ | Per-status audience deferred to Phase 9 |
| 7 | Widget hardening: size-aware targets, wallpaper colors, pre-warm cache, launcher QA matrix | ⬜ | Parts move into Track 0 |
| 8 | Final art (`core_launch` raster pack) | ⏸ | Blocked: placeholder art until the idea is validated (D-29) |
| 9 | Expansion: identity slots, duo/circle scenes, vibe suggestions, VRCQ hints, per-status audience, entitlements | ⬜ | |

### Charge economy (D-30)

| # | Item | Status | Notes |
| --- | --- | --- | --- |
| C.1 | Local prototype: `ChargeEngine`, tethers, Status Deck banner, debug toggles | ✅ | `0ea04a7`; 10 unit tests |
| C.2 | Apply the decided visibility model and guardrails: remove per-friend tether signals, show qualitative earning state | ⬜ | F-05, F-06; can be done now, locally |
| C.3 | Server-authoritative ledger (`charge_ledger`, `claim_charge()` on server time) + server-known widget subscriptions; pin state never exposed | ⬜ | F-04, F-05. SQL can be built and tested locally now; goes live after 1.5 |
| C.4 | Spending: Charge → avatar decorations, accessories and customizations via `asset_catalog` tiers + an `entitlements` table, with a server-side `purchase_with_charge()` RPC | ⬜ | After Avatar Phase 3; never gates the §17.3 vocabulary |
| C.5 | Explain Charge in-app (cap, how tethers work) without pressure mechanics | ⬜ | Guardrails F-06 |
| C.6 | **Buy Charge (D-32):** Google Play Billing consumables; server-side purchase-token verification (Play Developer API, in an edge function) before credit; idempotent ledger entries; refund/void handling via Real-time Developer Notifications | ⬜ | Requires C.3 and the Play Console account. No client-side crediting |
| C.7 | Paid-currency compliance: Play policy for virtual currency, "Contains in-app purchases" listing, privacy policy update, refund/withdrawal terms (incl. EU), age rating, parental guidance | ⬜ | Before paid Charge ships; needs a human/legal review |

### Closed alpha checklist (gate)

- [ ] Track 0 done; widgets device-verified on Pixel Launcher + one OEM launcher
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

All decisions live in `docs/IDL_DECISIONS.md` (D-01…D-34). The most relevant to current work:

- **D-21** RPC-only server API · **D-24** server filters semantics, client composes
- **D-25/26** asset packs as data plus code, shipped in the APK · **D-27** render cache
- **D-28** availability is never color-only · **D-29** legacy bases fold into five; placeholder art
- **D-30** Charge: passive resource from mutual widget tethers, spent on avatar decorations,
  accessories and customizations (2026-10-04, user)
  - **D-30a:** no per-friend tether visibility
  - **D-30b:** guardrails approved; the balance stays on the Status Deck
- **D-31 (to record with Track 0.2):** temporary explicit accessories versus signature conflicts.
- **D-32** Charge can be bought with real money via Google Play Billing; this requires a
  server-authoritative ledger with verified purchases (2026-10-04, user)
- **D-33** Crash reporting = Firebase Crashlytics, with no PII and an opt-out; no Google
  Analytics SDK (2026-10-04, delegated to Claude)
- **D-34** Real Supabase/Firebase projects get created once the user is happy with the avatars,
  avatar creation and customization (2026-10-04, user)

---

## 6. Open questions for the user

None open. Answered 2026-10-04:

| Q | Answer | Recorded as |
| --- | --- | --- |
| Per-friend tether visibility? | **No** | D-30a, F-05 |
| Charge guardrails? Balance on the Status Deck? | **Yes** to both | D-30b, F-06 |
| What does Charge buy; is Charge purchasable? | Avatar decorations, accessories, customizations; **yes**, purchasable | D-30, D-32, C.4, C.6, C.7 |
| Crash-reporting vendor? | Delegated → **Firebase Crashlytics** | D-33, 1.8 |
| When are the real projects created? | Once avatars, creation and customization feel right | D-34, roadmap order |

Add new questions here as they come up.

---

## 7. Ideas and parking lot (not scheduled)

- RenderSheet contact-sheet exporter for comparing art directions at widget size (Avatar Phase 2).
- On-device "vibe" keyword suggestions mapping to catalog tags, before any LLM (Phase 9).
- Widget "stale" indicator styling pass; show "updated Xh ago" consistently.
- Expiry labels round down ("7h left" right after setting 8h); consider "~8h left".
- Replace the brittle `lastSyncError.contains("Offline")` check on Home with a typed sync state.
- A PR template with a checklist: invariants, device screenshot, report updated.

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
| 2026-10-04 | User/Claude | Decisions D-30a/b, D-32, D-33, D-34 recorded; roadmap reordered (avatars before real backend projects) | this PR |
| 2026-10-04 | Claude | Full audit: 141 JVM ✅, 9/9 device ✅ (one flake), CI ✅; device and JVM probes confirmed F-01/F-02; this master plan; findings F-01…F-20 | this commit |
