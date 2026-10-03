# iDL Data Model

Two layers: **server (Postgres / Supabase)** is authoritative; **client (Room)** is a cache of
what the signed-in user is allowed to see.

## 1. Domain enums (shared, stored as snake_case strings)

| Enum | Values |
| --- | --- |
| Mood | neutral good happy excited sleepy tired low_energy stressed anxious sad sick focused chaotic social overwhelmed |
| Availability | available text_only call_ok gaming busy do_not_disturb afk offline |
| Intent | no_preference invite_me want_company need_memes ask_later check_in celebrating |
| ActivityType | none working coding gaming vr watching listening reading traveling exercising sleeping custom |
| PresenceSource (priority) | manual 100 · vrcq 80 · desktop_bridge 70 · discord/steam/xbox/external_sdk 60 · android_local 40 · inferred 10 |
| BaseForm | human blob robot ghost cat fox bear alien pixel |
| Expression | neutral happy excited sleepy tired sad anxious angry sick focused overwhelmed social mischievous afk dnd |
| HeadAccessory | none headphones vr_headset beanie crown wizard_hat cat_ears helmet sleep_cap |
| FaceAccessory | none glasses sunglasses |
| BodyAccessory | none hoodie blanket |
| Prop | none coffee tea controller book phone microphone potion pizza keyboard wrench sword gamepad |
| Scene | plain_gradient cozy_bedroom desk_setup campfire dungeon_tavern space_station rainy_window neon_city forest cloudscape |
| Audience | nobody only_me friends close_friends circles individuals |
| VisibilityCategory | avatar mood availability intent activity_category activity_name joinable status_note external_links last_updated |

Unknown enum strings from the server decode to a safe default (`neutral`, `none`, …) so an
older client never crashes on a newer server.

## 2. Postgres schema (proposal, Supabase)

```sql
create table profiles (
  id uuid primary key references auth.users(id) on delete cascade,
  username citext unique not null check (username ~ '^[a-z0-9_]{3,20}$'),
  display_name text not null check (char_length(display_name) between 1 and 40),
  bio text check (char_length(bio) <= 160),
  pronouns text check (char_length(pronouns) <= 24),
  time_zone text,
  profile_visibility text not null default 'friends',
  invisible boolean not null default false,   -- user-level, never expires (D-16)
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table avatar_configurations (
  user_id uuid primary key references profiles(id) on delete cascade,
  config jsonb not null,               -- AvatarConfiguration, validated by check fn
  render_version int not null default 1,
  updated_at timestamptz not null default now()
);

create table friendships (                -- one row per unordered pair, user_a < user_b
  user_a uuid references profiles(id) on delete cascade,
  user_b uuid references profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_a, user_b),
  check (user_a < user_b)
);

create table close_friends (              -- directed: owner marks friend as close
  owner_id uuid references profiles(id) on delete cascade,
  friend_id uuid references profiles(id) on delete cascade,
  primary key (owner_id, friend_id)
);

create table friend_invites (
  id uuid primary key default gen_random_uuid(),
  inviter_id uuid not null references profiles(id) on delete cascade,
  code text unique not null,              -- 10 char base32, shown as link + QR
  invitee_id uuid references profiles(id),-- set when redeemed (pending approval)
  status text not null default 'open',    -- open | pending | accepted | declined | revoked | expired
  expires_at timestamptz not null default now() + interval '7 days',
  created_at timestamptz not null default now()
);

create table blocks (
  blocker_id uuid references profiles(id) on delete cascade,
  blocked_id uuid references profiles(id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id)
);

create table circles (                    -- v0.5
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references profiles(id) on delete cascade,
  name text not null check (char_length(name) between 1 and 32),
  created_at timestamptz not null default now()
);
create table circle_memberships (
  circle_id uuid references circles(id) on delete cascade,
  member_id uuid references profiles(id) on delete cascade,
  primary key (circle_id, member_id)
);

create table presence_states (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles(id) on delete cascade,
  source text not null,
  priority int not null,
  envelope jsonb not null,                -- normalized Presence Envelope
  started_at timestamptz not null default now(),
  expires_at timestamptz not null check (expires_at <= started_at + interval '7 days'),
  updated_at timestamptz not null default now(),
  unique (user_id, source)                -- one live state per source
);
create index on presence_states (expires_at);

create table presence_events (            -- audit / latency metrics, no note text
  id bigserial primary key,
  user_id uuid not null,
  source text not null,
  kind text not null,                     -- set | clear | expire | invisible_on | invisible_off
  at timestamptz not null default now()
);

create table privacy_rules (              -- PresenceAudienceRule
  owner_id uuid references profiles(id) on delete cascade,
  category text not null,
  audience text not null,                 -- nobody | only_me | friends | close_friends | circles | individuals
  audience_ids uuid[] not null default '{}',
  primary key (owner_id, category)
);

create table reactions (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references profiles(id) on delete cascade,
  recipient_id uuid not null references profiles(id) on delete cascade,
  template text not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '24 hours',
  dismissed_at timestamptz
);

create table devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references profiles(id) on delete cascade,
  fcm_token text not null unique,
  platform text not null default 'android',
  created_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);

create table notification_preferences (
  user_id uuid primary key references profiles(id) on delete cascade,
  reactions boolean not null default true,
  presence_updates boolean not null default false,
  friend_requests boolean not null default true,
  quiet_hours int4range
);

create table integration_consents (       -- v1.0
  user_id uuid references profiles(id) on delete cascade,
  provider text not null,
  scopes text[] not null,
  granted_at timestamptz not null default now(),
  revoked_at timestamptz,
  primary key (user_id, provider)
);

create table audit_log (
  id bigserial primary key,
  actor_id uuid, action text not null, target_id uuid, at timestamptz not null default now()
);
```

### Row-level security (core)

* `presence_states`, `avatar_configurations`, `privacy_rules`: **no direct SELECT for other
  users**. Friends read presence only via the `security definer` function
  `presence_view_for(viewer uuid, owner uuid)` which: checks friendship, checks neither side
  blocked, drops expired rows, returns only the resting avatar if the owner is invisible, resolves precedence, and strips
  every category the owner's `privacy_rules` deny to `viewer`. The REST endpoint
  `/presence/friends` is a view over this function.
* Owner has full CRUD on their own rows (`auth.uid() = user_id`).
* `reactions`: INSERT only if sender and recipient are friends and not blocked; SELECT by
  sender or recipient; UPDATE (`dismissed_at`) by recipient only.
* Removing a friendship or inserting a block deletes `close_friends`, `circle_memberships`
  and open invites for the pair in the same transaction (trigger), and writes `audit_log`.

## 3. Client cache (Room, v1)

| Table | Key | Notes |
| --- | --- | --- |
| `session` | singleton id=0 | own userId, username, displayName, invisible flag |
| `avatars` | userId | `AvatarConfig` as JSON blob (own avatar) |
| `own_presence` | source | own `PresenceState` JSON by source (manual only in v0.1) + `pendingSync` |
| `friends` | userId | displayName, username, status (accepted/incoming/outgoing), isCloseFriend, fetchedAt |
| `friend_presence` | userId | server-filtered `PresenceView` JSON (incl. composed + resting avatar) + `fetchedAt` |
| `reactions` | id | inbox + sent, `dismissedAt`, `expiresAt`, `direction` |
| `privacy_rules` | category | own rules |
| `widget_subscriptions` | appWidgetId | `kind` (solo/self), `friendUserId?` |
| `sync_state` | singleton | last attempt / success / error (drives stale hints) |

Value objects are stored as JSON blobs (always read/written whole) to keep migrations trivial
while the model moves; relational columns are used where we query (ids, status, timestamps).

Room schema is exported to `app/schemas/` and versioned; destructive migration is **not**
allowed after alpha starts.

## 4. Presence Envelope (wire format)

See `IDL_API_CONTRACT.md §4`. Every source (manual, VRCQ, desktop, SDK) produces the same
envelope; precedence is applied on the envelope list, not on source-specific payloads.
