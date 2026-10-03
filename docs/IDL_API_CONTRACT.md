# iDL API Contract (v0.1 sketch)

JSON over HTTPS, `Authorization: Bearer <jwt>` (Supabase auth). Timestamps ISO-8601 UTC.
Implemented on Supabase as PostgREST views + RPC functions; paths below are the logical
contract mirrored by the Kotlin `IdlBackend` interface.

## 1. Errors

```json
{ "error": { "code": "forbidden", "message": "Not friends", "retryable": false } }
```

| HTTP | code | Client mapping |
| --- | --- | --- |
| 400 | `invalid` | `IdlError.Invalid` (show field error) |
| 401 | `unauthorized` | refresh token once, else sign out |
| 403 | `forbidden` | purge cached data for that user |
| 404 | `not_found` | purge cached data for that user |
| 409 | `conflict` | refetch then retry once |
| 429 | `rate_limited` | backoff per `Retry-After` |
| 5xx | `server` | WorkManager retry |

## 2. Endpoints

| Method | Path | Body → Response |
| --- | --- | --- |
| POST | `/auth/register` | `{email, password}` → session (Supabase) |
| POST | `/auth/login` | → session |
| GET | `/me` | → `Me {id, username, displayName, bio, pronouns, timeZone}` |
| PATCH | `/me/profile` | partial `Me` |
| GET | `/friends` | → `[Friend {userId, username, displayName, status, isCloseFriend}]` |
| POST | `/friend-invites` | `{}` → `{id, code, url, expiresAt}` |
| POST | `/friend-invites/redeem` | `{code}` → `Friend(status=outgoing)` |
| POST | `/friend-invites/{id}/accept` | → `Friend(status=accepted)` |
| POST | `/friend-invites/{id}/decline` | → 204 |
| POST | `/friendships/{userId}/remove` | → 204 |
| PUT | `/friendships/{userId}/close` | `{close: bool}` → 204 |
| POST | `/users/{userId}/block` | → 204 |
| DELETE | `/users/{userId}/block` | → 204 |
| GET | `/avatar` | → `AvatarConfiguration` |
| PUT | `/avatar` | `AvatarConfiguration` → same |
| GET | `/presence/me` | → `[PresenceEnvelope]` (all own sources) |
| PUT | `/presence/me` | `PresenceEnvelope` (source=manual) → stored envelope |
| DELETE | `/presence/me` | clear manual state → 204 |
| PUT | `/presence/me/invisible` | `{invisible: bool}` → 204 |
| GET | `/presence/friends` | → `[PresenceView]` (server-filtered, resolved) |
| GET | `/presence/friends/{userId}` | → `PresenceView` |
| POST | `/reactions` | `{recipientId, template}` → `Reaction` |
| GET | `/reactions/inbox` | → `[Reaction]` (not dismissed, not expired) |
| POST | `/reactions/{id}/dismiss` | → 204 |
| GET/PUT | `/privacy-rules` | `[{category, audience, audienceIds}]` |
| GET/POST/PATCH/DELETE | `/circles…` | v0.5 |
| POST | `/devices/register` | `{fcmToken}` → `{id}` |
| DELETE | `/devices/{id}` | → 204 |

Rate limits (alpha): presence PUT 30/min, reactions 60/hour per sender, invites 20/day.

## 3. PresenceView (what a friend receives)

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

## 4. Presence Envelope (what a source sends)

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

## 5. Push payloads (FCM data messages, no PII in notification body server-side)

```json
{ "type": "presence_changed", "userId": "u_ari" }
{ "type": "reaction_received", "reactionId": "r_123" }
{ "type": "friend_request", "inviteId": "i_9" }
{ "type": "friend_removed", "userId": "u_ari" }
```

The client fetches details after the push, so payloads never carry visible content and
privacy rules are applied at fetch time.

## 6. Future bridge contract (VRCQ, v1.0 — not implemented)

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
