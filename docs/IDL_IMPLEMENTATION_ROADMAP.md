# iDL Implementation Roadmap

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

1. Supabase project; apply `IDL_DATA_MODEL.md` schema + RLS + `presence_view_for`.
2. Golden test vectors: run `PrivacyFilterTest` cases against the SQL function in CI.
3. `SupabaseIdlBackend` (Ktor client) implementing `IdlBackend`; flag `REMOTE_BACKEND`.
4. Email magic-link auth; account deletion; replace demo onboarding.
5. FCM: `google-services.json`, `FirebasePushSource`, device registration, an edge function
   fanning out on `presence_states` changes (skips invisible changes).
6. QR **scanning** (CameraX + ZXing). Today: QR display + code/link entry.
7. Crash reporting vendor (D-07) + privacy-conscious analytics events.
8. CI (GitHub Actions): `./gradlew lint test assembleDebug`, plus an emulator job for `connectedDebugAndroidTest`.
9. Widget polish: wide-layout screenshots per launcher, OEM battery-optimizer testing.
10. Avatar art pass (illustrator replaces layer painters; bump `renderVersion`).

## Milestone 2 — v0.5 Social depth (only after the MVP loop shows retention)

Circles + circle-scoped rules → duo/circle/strip widgets → saved quick states, launcher
shortcuts, Quick Settings tile → postcards → deep links (Discord/Steam/VRChat/SMS) →
notification tuning → expanded cosmetics.

## Milestone 3 — v1.0 Platform (only with retention evidence)

VRCQ bridge (contract + normalizer already in `domain/Integrations.kt`) → desktop companion →
presence SDK + docs → creator packs → verified integrations → optional E2E small-circle notes.

## Explicit non-goals

See `IDL_PRODUCT_SPEC.md §7`.
