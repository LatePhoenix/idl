# iDL API Contract (v0.1, implemented)

JSON over HTTPS. Auth is Supabase GoTrue (passwordless email code); data is PostgREST **RPC
only**: `POST {SUPABASE_URL}/rest/v1/rpc/<name>` with headers `apikey: <anon key>` and
`Authorization: Bearer <access token>`, and the arguments as a JSON object of named parameters.
The server derives the caller from the JWT; no RPC takes a "current user" argument.
Timestamps are ISO-8601 UTC with second precision (`2026-10-03T14:00:00Z`).

Source of truth: `supabase/migrations/*.sql` (server) and
`app/.../data/remote/supabase/SupabaseIdlBackend.kt` (client). `SupabaseRestIT` exercises both
together against real PostgREST.

## 1. Errors

RPCs raise `PTxxx` SQLSTATEs, which PostgREST turns into HTTP status `xxx` with the body
`{"code":"PT400","message":"That username is taken","details":"username","hint":null}`.

| HTTP | Meaning | Client mapping |
| --- | --- | --- |
| 400 | validation (`details` = field, `message` = user-facing text) | `IdlError.Invalid(field, message)` |
| 401 | missing/expired JWT | refresh once and retry; else sign out locally |
| 403 | not friends / blocked / not allowed | `Forbidden` → purge cached data for that user |
| 404 | `no_profile` (signed in, no username yet) or unknown request | `NotFound` |
| 409 | conflict | `Conflict` |
| 429 | rate limited (reactions: 10/hour per recipient) | `RateLimited` |
| 5xx / network | server or transport failure | `Server` / `Offline` → WorkManager retry |

## 2. Auth (GoTrue)

| Call | Body | Notes |
| --- | --- | --- |
| `POST /auth/v1/otp` | `{email, create_user: true}` | emails a 6-digit code (template must include `{{ .Token }}`) |
| `POST /auth/v1/verify` | `{type: "email", email, token}` | → session (access + refresh token) |
| `POST /auth/v1/token?grant_type=refresh_token` | `{refresh_token}` | refreshed when <60 s from expiry, and after a 401 |
| `POST /auth/v1/logout` | — | best effort; local session and cache are wiped regardless |

## 3. RPCs

| RPC | Args | Returns |
| --- | --- | --- |
| `register_profile` | `p_username, p_display_name` | `Me` (idempotent for an existing profile) |
| `me` | — | `Me {userId, username, displayName, invisible}`; 404 `no_profile` before registration |
| `get_avatar` / `put_avatar` | — / `p_config` | `AvatarConfig` |
| `put_presence` | `p_envelope` (PresenceState, `source` must be `manual`) | stored envelope |
| `clear_presence` | — | 204 |
| `set_invisible` | `p_invisible` | 204 |
| `friend_presence` | — | `[PresenceView]` for all accepted friends (server-filtered) |
| `friend_presence_one` | `p_user_id` | `PresenceView`; 403 if not visible |
| `friends` | — | `[Friend {userId, username, displayName, status, isCloseFriend}]` (accepted, incoming, outgoing) |
| `create_invite` | — | `{code: "ABCDE-FGHJK", url: "idl://invite/…", expiresAt}` (7 days; reused while >1 day left) |
| `redeem_invite` | `p_code` (code or link) | `Friend` (`outgoing`, or `accepted` if they already asked us) |
| `accept_request` / `decline_request` | `p_user_id` | `Friend` / 204 |
| `remove_friend` | `p_user_id` | 204 |
| `set_close_friend` | `p_user_id, p_close` | 204 |
| `block_user` / `unblock_user` / `blocked_users` | `p_user_id` / — | 204 / `[Friend]` |
| `send_reaction` | `p_recipient_id, p_template` | `Reaction` |
| `reaction_inbox` | — | `[Reaction]` (not dismissed, not expired, newest first) |
| `dismiss_reaction` | `p_id` | 204 |
| `get_privacy_rules` / `put_privacy_rules` | — / `p_rules` (`PrivacyRules`, full replace) | `PrivacyRules` |
| `register_device` / `delete_device` | `p_token, p_platform` / `p_id` | `{id}` / 204 |

Circles RPCs arrive in v0.5; the tables and the circle audience evaluation already exist.

## 4. PresenceView (what a friend receives)

All fields optional; absent = not visible to this viewer.

```json
{
  "userId": "u_ari",
  "avatar": { "...AvatarConfiguration with resolved expression/props..." },
  "mood": "sleepy",
  "availability": "text_only",
  "intent": "ask_later",
  "activity": { "type": "vr", "label": null, "joinable": null },
  "note": "brb nap",
  "updatedAt": "2026-10-03T14:00:00Z",
  "expiresAt": "2026-10-03T22:00:00Z"
}
```

## 5. Presence Envelope (what a source sends)

```json
{
  "mood": "sleepy",
  "availability": "text_only",
  "intent": "ask_later",
  "activity": { "type": "vr", "label": "VRChat", "source": "vrcq", "joinable": false, "joinUrl": null },
  "visual": { "expression": "tired", "props": ["blanket", "tea"], "scene": "cozy_bedroom" },
  "audience": { "type": "circle", "ids": ["inner_circle"] },
  "note": null,
  "startedAt": "2026-10-03T14:00:00Z",
  "expiresAt": "2026-10-04T14:00:00Z",
  "source": "manual",
  "priority": 100,
  "invisible": false
}
```

Validation: `expiresAt > startedAt`, `expiresAt - startedAt <= 7d`, `note ≤ 80 chars`,
`joinUrl` must be https or a registered scheme (`discord:`, `steam:`, `vrchat:`) — v0.5.

## 6. Push payloads (FCM data messages, no PII in notification body server-side)

```json
{ "type": "presence_changed", "userId": "u_ari" }
{ "type": "reaction_received", "reactionId": "r_123", "userId": "u_ari" }
{ "type": "friend_request", "inviteId": "i_9" }
{ "type": "friend_removed", "userId": "u_ari" }
```

The client fetches details after the push, so payloads never carry visible content and
privacy rules are applied at fetch time.

## 7. Future bridge contract (VRCQ, v1.0 — not implemented)

```json
{
  "source": "vrcq",
  "activity": { "type": "vr", "label": "VRChat", "joinable": false },
  "visualHints": { "headAccessory": "vr_headset", "badge": "vr" },
  "privacy": { "worldNameVisible": false, "instanceVisible": false }
}
```

Normalized by `EnvelopeNormalizer` into a `PresenceEnvelope` with `priority = 80`; it may set
`activity` and visual hints but never mood/availability/intent/note unless the user opts in.
