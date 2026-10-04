# iDL Implementation Roadmap

> **Superseded by [`master-plan.md`](../master-plan.md)** (2026-10-04). Kept for history; don't update.

Status legend: ✅ built, tested, and verified on the Pixel 9 emulator (API 37) · 🟡 partial · ⬜ not started

## Milestone 0 — Foundation (done in this pass)

| # | Item | Status | Notes |
| --- | --- | --- | --- |
| 1 | Gradle project, wrapper, version catalog | ✅ | `gradlew test assembleDebug` passes from a clean checkout |
| 2 | Domain models & enums | ✅ | `app.idl.domain`, no Android imports |
| 3 | Room schema (exported to `app/schemas/`) | ✅ | v1 |
| 4 | Repositories + `IdlBackend` interface | ✅ | |
| 5 | `FakeIdlBackend` + deterministic demo world | ✅ | 8 demo users, each exercising a different privacy case |
| 6 | Avatar spec + deterministic Canvas renderer | ✅ | 9 bases × 15 expressions; vector art is placeholder-quality |
| 7 | Presence resolver + expiry | ✅ | |
| 8 | Status Deck UI | ✅ | quick states, chips, note, duration, Invisible |
| 9 | Friends list + friend profile | ✅ | |
| 10 | Solo friend widget (+ config activity + in-app pinning) | ✅ | |
| 11 | Your iDL widget | ✅ | |
| 12 | Push abstraction + mock push (Settings → Alpha/debug) | ✅ | real FCM not wired |
| 13 | Privacy filter + Privacy Center + preview-as-friend | ✅ | circles/individuals: domain only, UI disabled |
| 14 | Unit tests (63): precedence, expiry, privacy, reactions, avatar spec, wire format, fake backend | ✅ | |
| 15 | Instrumented tests (8): Status Deck UI, avatar bitmaps, offline cache, widget data | ✅ | need an emulator/device |

## Milestone 1 — Real backend (closed alpha gate)

| # | Item | Status | Notes |
| --- | --- | --- | --- |
| 1 | Schema + RLS lockdown + RPC API (`supabase/migrations`) | ✅ | Tested on Postgres 16 with an auth shim; not yet applied to a real Supabase project |
| 2 | Golden privacy vectors shared by Kotlin and SQL | ✅ | 29 cases; a mutation check confirmed they catch leaks |
| 3 | `SupabaseIdlBackend` (OkHttp) behind `IdlBackend`, auto-selected when configured | ✅ | Unit tests (MockWebServer) + end-to-end against real PostgREST |
| 4 | Passwordless email-code auth, token refresh, sign-out, unauthorized → local wipe | 🟡 | GoTrue calls are tested against mocks only; needs a real project |
| 5 | FCM: `push-fanout` edge function, device registration from the app, `FirebasePushSource` | ⬜ | `push_outbox` + `register_device` RPC are in place |
| 6 | QR scanning (CameraX + ZXing) | ⬜ | |
| 7 | Crash reporting (D-07) + privacy-conscious analytics | ⬜ | |
| 8 | CI: Gradle tests + SQL suite + PostgREST IT | 🟡 | `.github/workflows/ci.yml` written; it hasn't run (no remote yet) |
| 9 | Account deletion, pg_cron cleanup, Keystore-backed session storage | ⬜ | |
| 10 | Widget polish on real launchers / OEM battery testing; avatar art pass | ⬜ | |

## Milestone 2 — v0.5 Social depth (only after the MVP loop shows retention)

Circles + circle-scoped rules → duo/circle/strip widgets → saved quick states, launcher
shortcuts, Quick Settings tile → postcards → deep links (Discord/Steam/VRChat/SMS) →
notification tuning → expanded cosmetics.

## Milestone 3 — v1.0 Platform (only with retention evidence)

VRCQ bridge (contract + normalizer already in `domain/Integrations.kt`) → desktop companion →
presence SDK + docs → creator packs → verified integrations → optional E2E small-circle notes.

## Explicit non-goals

See `IDL_PRODUCT_SPEC.md §7`.
