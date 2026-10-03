# iDL — Friends at a Glance: Product Spec

> Pronounced "idle". *Set your vibe. See your people.*

## 1. What iDL is

An ambient social-presence layer for **close friends**. Each user has an expressive
layered avatar (an "iDL"). Friends pin each other's iDL to the Android home screen
as widgets. Changing your mood / availability / activity / props updates your
friends' widgets without anyone opening a feed or starting a chat.

iDL is **not** a feed, a chat app, or a replacement for Discord/Signal/Steam/VRChat.

## 2. Principles (binding)

1. Ambient presence, not a feed. No infinite scroll, ads, follower counts, ranking.
2. The home-screen widget is the primary surface.
3. The avatar must read at ~48 dp.
4. Changing status takes one or two actions (quick states in the Status Deck).
5. Every shared field is privacy-controlled; conservative defaults.
6. Every status expires.
7. Useful with zero integrations.
8. Integrations are optional enrichment and never break core presence.
9. Simple, deterministic, maintainable architecture.
10. Android first; no iOS until Android PMF.

## 3. Personas & core loop

* **Sharer** sets "sleepy · text only · 8 h" in two taps.
* **Glancer** sees the friend's widget change (avatar expression + moon badge).
* Glancer taps the widget → friend profile → sends "☕ coffee" or "want company?".
* Later the status expires and the widget falls back honestly ("last seen 9 h ago").

## 4. MVP (v0.1 closed alpha) scope

| Area | In v0.1 |
| --- | --- |
| Platform | Android only (minSdk 26, target 35) |
| Account | Demo/local account; real auth behind `AuthRepository` (Supabase planned) |
| Avatar | Layered builder: base, color, expression, head/face accessory, hand prop, scene, theme |
| Presence | Mood, availability, intent, activity, note, expiry, Invisible mode |
| Friends | Invite link + QR (display), invite code entry, request approval, remove, block |
| Widgets | Solo friend widget, "Your iDL" widget |
| Reactions | 14 templates, single recipient, dismissible, expiring |
| Privacy | Per-category audience: nobody / only me / friends / close friends; preview-as-friend |
| Notifications | Channels: reactions, presence, friend requests, system. Mock push in alpha |
| Offline | Room cache is the display source of truth |
| Debug | Demo-control screen to simulate friend presence changes & incoming reactions |

## 5. MVP acceptance criteria

A tester can:

1. Launch the app.
2. Create a demo account (display name + username).
3. Build a recognizable avatar.
4. Set mood, availability, intent, optional note, expiration.
5. See their avatar/status update in-app.
6. See their status in the "Your iDL" widget.
7. Add a demo friend (invite code) or accept a pending request.
8. View that friend's current or last-known state.
9. Pin that friend as a Solo widget.
10. See the widget update when demo presence changes (Debug → simulate).
11. Tap the widget to open the friend profile.
12. Send a reaction.
13. Control which categories a friend can see.
14. Enable Invisible mode.
15. See expired state revert cleanly (no stale "busy" after expiry).
16. Use the app offline after initial load.
17. Build, run and test from a clean checkout (`gradlew test assembleDebug`).

## 6. Later versions

* **v0.5 Social depth** — circles + circle-level privacy, duo/circle/strip widgets, saved quick
  states, launcher shortcuts, Quick Settings tile, postcards, deep links, notification tuning.
* **v1.0 Platform** — VRCQ bridge, desktop bridge, presence SDK, creator packs,
  verified integrations, optional E2E notes.

## 7. Non-goals

Full chat · public feed · discovery · ads · follower counts · public-by-default profiles ·
location tracking · mood analytics / health inference · scraping (VRChat, Steam, Discord, Xbox) ·
iOS · marketplace · payments · NFT · complex animated widgets · punishing streaks ·
read receipts · typing indicators.

## 8. Success metrics (privacy-conscious)

Account completion, avatar completion, invite sent / accepted rate, widget install rate and
7-day widget retention, status updates per active user, % statuses with expiry (target 100 %),
reaction send/receive rate, status→widget latency (p50/p95), widget refresh failure rate,
cache staleness, notification opt-in, D7/D30 retention. **Never** log note text, avatar
content, or identifiable social graph edges to analytics.
