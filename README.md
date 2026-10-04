# iDL — Friends at a Glance

*Set your vibe. See your people.* An Android-first ambient-presence app for close friends:
set an expressive avatar status, and friends see it on their home-screen widgets.

Closed-alpha foundation. By default it runs on an in-process **fake backend** with demo friends,
so nothing leaves the device. Set `supabase.url` and `supabase.anonKey` in `local.properties`
to use the real Supabase backend instead; see [supabase/README.md](supabase/README.md).

## Build & run

Requirements: JDK 17+ (21 works) and an Android SDK with platform 35. Set `sdk.dir` in
`local.properties`, or set `ANDROID_HOME`.

```bash
scripts/check.sh                      # unit tests + lint + debug build (see AGENTS.md)
./gradlew connectedDebugAndroidTest   # instrumented tests (needs emulator/device)
./gradlew installDebug                # app id: app.idl.debug
supabase/tests/run.sh                 # backend: privacy vectors + RLS/behaviour tests (needs Docker)
```

## Try the MVP loop

1. Create a demo account, then build your avatar.
2. Home → tap your card → **Status Deck** → tap a quick state (one tap) or compose your own.
3. **Widgets** (grid icon) → add *Your iDL* and pin a friend.
4. **Settings → Alpha / debug**: simulate a friend's status change (mock push), an incoming
   reaction, a status expiring in 1 minute, a friend removing you, or **offline mode**.
5. Tap a friend widget to open their profile, then send a reaction.
6. **Privacy** (shield icon): per-category audiences, Invisible mode, and preview-as-friend.

Demo invite codes: `KIT-2026`, `PIX-2026` (auto-accepted after 2 s). Rin has a pending request.

## Layout

```
app/src/main/java/app/idl/
  domain/   pure Kotlin: presence, precedence, expiry, privacy filter, avatar spec, reactions
  data/     Room cache, repositories, IdlBackend + FakeIdlBackend, push abstraction
  avatar/   deterministic Canvas renderer (shared by app and widgets)
  widget/   Glance widgets, pinning, config activity
  work/     WorkManager: expiry + reconcile
  ui/       Compose screens
docs/       product spec, architecture, data model, privacy, widgets, API, roadmap, decisions
```

## Status and roadmap

See [`master-plan.md`](master-plan.md) for the current state, open issues and roadmap.

## Known limitations

- The Supabase backend and auth are built and tested locally but haven't run against a real project yet. FCM delivery and crash reporting are still to do (see `docs/IDL_IMPLEMENTATION_ROADMAP.md`).
- QR codes are displayed, not scanned; use code or link entry instead.
- Circles, duo/circle widgets and integrations exist only as models, flags and placeholders.
- Avatar art is programmatic vector drawing. It's readable, but not final art.
- Dependencies are pinned to older versions that were cached locally (D-11); upgrading them is a separate task.
