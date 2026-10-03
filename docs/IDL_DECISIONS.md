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

**D-07 · Crash reporting vendor deferred. (confirm)** Interface only. Crashlytics if we already
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

## High-risk decisions to watch

1. **Server-side privacy function** correctness — a bug leaks fields to all friends. Mitigate
   with shared golden vectors and SQL tests in CI.
2. **Push fan-out cost/latency** — one presence change × N friends × M devices. Edge function
   must batch; payloads carry ids only.
3. **Widget update reliability** on OEM-skinned Android (battery optimisers kill background
   work). Mitigate with push-driven updates + honest stale display; measure refresh failure rate.
4. **Avatar art direction** — Canvas vector style may not hit the "expressive, high quality" bar.
   Layer painters are isolated so an illustrator can replace them.
5. **Invisible mode signal leakage** — toggling must not trigger visible pushes or timestamp changes.
