# iDL Decision Log

Format: ID · decision · why · revisit when. Items marked **(confirm)** are assumptions that need
product-owner confirmation.

**D-01 · Single `:app` module, pure-Kotlin `domain` package.**
Fast builds, low ceremony. `domain` has no Android imports so it can be split out later.
Revisit at >30 k LOC or when a desktop bridge wants to share domain code.

**D-02 · Manual DI (`AppContainer`) instead of Hilt.**
~10 singletons; constructor injection is enough for tests. Revisit if the graph grows scoped
lifetimes (per-account, per-circle).

**D-03 · Backend: Supabase (Postgres + RLS + edge functions). (confirm)**
The privacy model is relational (friendships × rules × blocks × circles) and must be enforced
server-side; Postgres functions + RLS express it directly and are testable with SQL. Firebase
would push that logic into security rules over denormalised documents, which is hard to audit
for per-field audiences. FCM is still used for push delivery (Supabase has no native Android push).

**D-04 · Fake backend first.** No credentials exist. All UI, widget and privacy work runs
against `FakeIdlBackend`, which applies `PrivacyFilter` exactly as the server will.

**D-05 · Avatars rendered on-device from config (Canvas), not image assets or URLs.**
Deterministic, tiny, works offline and in widgets, no public image URLs (privacy rule 7).
Vector-drawn layers are less rich than illustrated art; art can later replace individual layer
painters without changing the config model (`renderVersion` gates it).

**D-06 · Glance for widgets, avatar as bitmap.** Glance covers sizing/click/actions; bitmap
avoids view-count limits and keeps parity with in-app avatar. Fallback to raw RemoteViews only if
Glance blocks a needed behaviour.

**D-07 · Crash reporting vendor deferred.** Resolved by D-33. Interface only. Crashlytics if we already
ship Firebase for FCM; Sentry if we want EU hosting.

**D-08 · Expiry evaluated at read time + one-shot WorkManager job.** Correct without relying
on background execution; jobs only refresh widgets/purge rows.

**D-09 · Manual source wins mood/availability/intent/note by default;** automated sources
contribute activity + visual hints only.

**D-10 · Conservative privacy defaults** (see Privacy Model §2): friends see avatar,
availability, last-updated; close friends additionally see mood, intent, activity category,
note; activity name / joinable / external links default to only-me.

**D-11 · Toolchain pinned to locally cached versions:** AGP 8.13.2, Gradle 8.14.5, Kotlin 2.0.21,
KSP 2.0.21-1.0.28, Room 2.6.1, Glance 1.1.1, Compose BOM 2024.12.01, compileSdk/targetSdk 35,
minSdk 26. Upgrade as a dedicated change.

**D-12 · Package/applicationId `app.idl`. (confirm)**

**D-13 · QR: display via ZXing core (pure Java, ~500 KB) now; scanning in Milestone 1.**
Invite code entry covers scanning's job for alpha.

**D-14 · Notes capped at 80 chars; max status duration 7 days; default 4 h. (confirm)**

**D-15 · "Close friends" is a built-in list in v0.1** (directed, owner-managed), so the
close_friends audience works before general circles ship in v0.5.

**D-16 · Invisible is a user-level flag, not a field on an expiring PresenceState.**
If invisibility lived on a state it would silently end when that state expired — a privacy
surprise. `PresenceState` therefore has no `isInvisible`; the server keeps it on the profile.

**D-17 · Friend views carry `restingAvatar` + `expiresAt`.** Lets the client revert expired
state correctly offline without knowing the friend's private base config.

**D-18 · Room stores value objects as JSON blobs** (see Data Model §3).

**D-19 · Test deps:** Espresso 3.7 / test runner 1.7 are required to run Compose UI tests on
API 36+ emulators (3.5 reflects on a removed `InputManager` API).

**D-20 · Supabase client = OkHttp + kotlinx.serialization, no supabase-kt.** We use four
GoTrue endpoints and one RPC pattern. A thin client keeps dependencies and APK size small and
makes error mapping explicit. Sessions live in app-private DataStore, excluded from backup;
moving them to Keystore-backed encryption is a follow-up.

**D-21 · RPC-only server API.** RLS is on with no policies and all table grants are revoked;
clients can call only `SECURITY DEFINER` RPCs that derive the caller from `auth.uid()`. That puts
privacy enforcement in one place (`idl_private.presence_view`), which is golden-tested against
the Kotlin reference. Realtime subscriptions on tables are therefore out; we use push instead.

**D-22 · Passwordless email one-time code** (not magic links, not passwords). No deep-link
plumbing and no password storage. Google sign-in can come later.

**D-23 · Push via an outbox table.** RPCs enqueue ids-only payloads in `push_outbox` inside the
same transaction as the change. A `push-fanout` edge function will deliver them through FCM.
Fan-out happens at enqueue time, so the friend list is evaluated transactionally.

**D-24 · Server filters semantics; client composes visuals.** Asset-pack compatibility rules,
fallbacks and per-user semantic overrides live client-side, and reimplementing them in SQL would
duplicate the engine. The server returns only fields the viewer may see (avatar visuals only when
`avatar` is visible, explicit expression only when `mood` is visible), and the client composes
from that filtered input. Privacy still comes before composition, because hidden fields never
reach the device. Golden vectors split into a filtered-view suite (server and Kotlin) and a
composition suite (Kotlin). Implemented in avatar Phase 3.

**D-25 · Asset packs are data plus code.** JSON manifests whose assets are `procedural` (named
painter, placeholder art) or `raster` (WebP layers, final art). Both use the same anchors,
z-order, compatibility and fallbacks, so final art can arrive late without blocking engineering.

**D-26 · Packs ship inside the APK** (`app/src/main/assets/packs/<packId>/v<n>/`) until
monetization requires remote delivery.

**D-27 · Render cache:** memory LRU plus disk under `cacheDir/renders`, keyed by a SHA-256
render key. Cached images contain only already-filtered state and are never exposed via URLs.

**D-28 · Availability is shape and color.** Every availability state has a distinct glyph; color
is never the only cue.

**D-29 · Legacy bases fold into the five MVP bases** (see IDL_AVATAR_CREATOR_PLAN §6.2). Art
stays placeholder until the product idea is validated.

**D-30 · Charge (user, 2026-10-04).** A passive resource earned while friends have mutually
pinned each other's widgets. It will be spent on customization and avatar accessories. Accrual
is computed on evaluation (no timers) with diminishing per-tether rates (10, 10, 8, 6, then 2
per hour) and a 24 h uncollected cap. The current implementation is a local prototype. Open
before shipping: the tether visibility model, guardrails against engagement pressure, and a
server-authoritative ledger before anything is spendable (master-plan F-04…F-06). Charge must
never gate the core vocabulary, privacy or widget features (master plan §17.3).

**D-30a · No per-friend tether visibility (user, 2026-10-04).** A user never learns that a
specific friend has pinned their widget. The UI shows only the balance and a qualitative
"earning" state (no tether count or exact rate, which would identify a friend when there are
few). The server uses pin state only inside the ledger and never returns it.

**D-30b · Charge guardrails (user, 2026-10-04).**
- no Charge notifications
- never show who has or hasn't pinned you
- no streaks or decay
- the 24 h cap is a ceiling, not a penalty
- Charge buys cosmetics only
- the balance stays on the Status Deck

**D-32 · Charge is purchasable (user, 2026-10-04).** Google Play Billing consumables, with every
purchase verified server-side (Play Developer API) before an idempotent ledger credit, and
refunds/voids handled via Real-time Developer Notifications. This makes the server-authoritative
ledger mandatory, and the core vocabulary, privacy, widget and accessibility features stay
free (master plan §17.3).

**D-33 · Crash reporting = Firebase Crashlytics (2026-10-04, delegated).** Firebase is required
for FCM anyway, so this adds no new vendor. It sits behind the existing `CrashReporter` seam.
- no PII and no custom keys with notes, names, usernames or avatar content
- users can turn it off in Settings
- no Google Analytics/Firebase Analytics SDK; product metrics are in-house, privacy-safe counters

Sentry stays the fallback if EU data residency becomes a requirement.

**D-34 · Real backend projects after the avatar work (user, 2026-10-04).** The Supabase and
Firebase projects are created once the user is happy with the avatars, avatar creation and
customization. Until then all server work is developed and tested against the local Docker stack.
Supersedes the "(confirm)" on D-07.

**D-35 · Per-friend looks may change anything (user, 2026-10-04).** A user can give each friend
a different avatar look, including base, palette and signature. The server picks the look for the
viewer inside `presence_view`, so a friend never receives another friend's look. Trade-off
accepted: recognizability comes from the friend's name shown alongside every render rather than
from fixed identity anchors (master plan §3.3).

**D-36 · Friends-first home (user, 2026-10-04).** The app opens on a scrollable list of friends.
Tapping a friend's avatar opens "how I look to them", where I can edit that look. What tapping a
friend's *home-screen widget* communicates is still undecided (master-plan Q11).

**D-37 · Live weather shapes my avatar for friends (user, 2026-10-04).**
- Opt-in, with a `weather` privacy category.
- Location (coarse, or a manually chosen city) is used only on the device; only a weather
  condition is published.
- It's an automated `android_local` presence source with about 3 h expiry, so manual status
  wins, Invisible hides it, and expiry clears it.

**D-38 · Platform account connections are on hold (user, 2026-10-04).** No Steam, Discord, Xbox,
PlayStation, Meta Quest or VRChat sign-ins or badges for now. Feasibility notes are in
master-plan §7.1-A.

## High-risk decisions to watch

1. **Server-side privacy function** correctness — a bug leaks fields to all friends. Mitigated
   by `contract/privacy_vectors.json`: Kotlin generates it and SQL must match it
   (`supabase/tests/run.sh`). A mutation check confirmed that leaking mood fails 3 of 29 vectors.
2. **Push fan-out cost/latency** — one presence change × N friends × M devices. Edge function
   must batch; payloads carry ids only.
3. **Widget update reliability** on OEM-skinned Android (battery optimisers kill background
   work). Mitigate with push-driven updates + honest stale display; measure refresh failure rate.
4. **Avatar art direction** — Canvas vector style may not hit the "expressive, high quality" bar.
   Layer painters are isolated so an illustrator can replace them.
5. **Invisible mode signal leakage** — toggling must not trigger visible pushes or timestamp changes.
