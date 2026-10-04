-- iDL v0.1 schema. See docs/IDL_DATA_MODEL.md and docs/IDL_PRIVACY_MODEL.md.
--
-- Security posture: clients never touch tables directly. RLS is enabled with no policies and
-- all table privileges are revoked from anon/authenticated; the only API is the set of
-- SECURITY DEFINER functions at the bottom of this file, each of which derives the caller from
-- auth.uid(). idl_private.presence_view() is the server port of the Kotlin reference
-- PrivacyFilter and must match contract/privacy_vectors.json exactly.

create extension if not exists pgcrypto with schema extensions;
create schema if not exists idl_private;

-- ---------------------------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------------------------

create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  username text not null unique check (username ~ '^[a-z0-9_]{3,20}$'),
  display_name text not null check (char_length(display_name) between 1 and 40),
  bio text check (char_length(bio) <= 160),
  pronouns text check (char_length(pronouns) <= 24),
  time_zone text,
  invisible boolean not null default false, -- user-level, never expires (D-16)
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table public.avatar_configurations (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  config jsonb not null check (jsonb_typeof(config) = 'object'),
  render_version int not null default 1,
  updated_at timestamptz not null default now()
);

-- One row per unordered pair.
create table public.friendships (
  user_a uuid not null references public.profiles (id) on delete cascade,
  user_b uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_a, user_b),
  check (user_a < user_b)
);

create table public.friend_requests (
  from_id uuid not null references public.profiles (id) on delete cascade,
  to_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (from_id, to_id),
  check (from_id <> to_id)
);

create table public.friend_invites (
  id uuid primary key default gen_random_uuid(),
  inviter_id uuid not null references public.profiles (id) on delete cascade,
  code text not null unique,
  revoked boolean not null default false,
  expires_at timestamptz not null default now() + interval '7 days',
  created_at timestamptz not null default now()
);

-- Directed: owner has put friend on their close-friends list.
create table public.close_friends (
  owner_id uuid not null references public.profiles (id) on delete cascade,
  friend_id uuid not null references public.profiles (id) on delete cascade,
  primary key (owner_id, friend_id)
);

create table public.blocks (
  blocker_id uuid not null references public.profiles (id) on delete cascade,
  blocked_id uuid not null references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (blocker_id, blocked_id)
);

-- v0.5; present so circle audiences can be evaluated.
create table public.circles (
  id uuid primary key default gen_random_uuid(),
  owner_id uuid not null references public.profiles (id) on delete cascade,
  name text not null check (char_length(name) between 1 and 32),
  created_at timestamptz not null default now()
);

create table public.circle_memberships (
  circle_id uuid not null references public.circles (id) on delete cascade,
  member_id uuid not null references public.profiles (id) on delete cascade,
  primary key (circle_id, member_id)
);

-- envelope = the client's PresenceState JSON; columns mirror the fields we sort/filter on.
create table public.presence_states (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  source text not null,
  priority int not null,
  envelope jsonb not null,
  started_at timestamptz not null,
  expires_at timestamptz not null,
  updated_at timestamptz not null,
  unique (user_id, source),
  check (expires_at > started_at),
  check (expires_at <= started_at + interval '7 days')
);
create index presence_states_expires_at on public.presence_states (expires_at);

create table public.presence_events (
  id bigserial primary key,
  user_id uuid not null,
  source text not null,
  kind text not null, -- set | clear | invisible_on | invisible_off
  at timestamptz not null default now()
);

create table public.privacy_rules (
  owner_id uuid not null references public.profiles (id) on delete cascade,
  category text not null,
  audience text not null,
  audience_ids uuid[] not null default '{}',
  primary key (owner_id, category)
);

create table public.reactions (
  id uuid primary key default gen_random_uuid(),
  sender_id uuid not null references public.profiles (id) on delete cascade,
  recipient_id uuid not null references public.profiles (id) on delete cascade,
  template text not null,
  created_at timestamptz not null default now(),
  expires_at timestamptz not null default now() + interval '24 hours',
  dismissed_at timestamptz
);
create index reactions_recipient on public.reactions (recipient_id, created_at desc);

create table public.devices (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  fcm_token text not null unique,
  platform text not null default 'android',
  created_at timestamptz not null default now(),
  last_seen_at timestamptz not null default now()
);

create table public.notification_preferences (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  reactions boolean not null default true,
  presence_updates boolean not null default false,
  friend_requests boolean not null default true
);

create table public.integration_consents (
  user_id uuid not null references public.profiles (id) on delete cascade,
  provider text not null,
  scopes text[] not null,
  granted_at timestamptz not null default now(),
  revoked_at timestamptz,
  primary key (user_id, provider)
);

-- Ids-only push payloads awaiting delivery by the push-fanout edge function (Milestone 1, FCM).
create table public.push_outbox (
  id bigserial primary key,
  recipient_id uuid not null references public.profiles (id) on delete cascade,
  payload jsonb not null,
  created_at timestamptz not null default now(),
  sent_at timestamptz
);
create index push_outbox_pending on public.push_outbox (created_at) where sent_at is null;

create table public.audit_log (
  id bigserial primary key,
  actor_id uuid,
  action text not null,
  target_id uuid,
  at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------------------------
-- Private helpers (not exposed through PostgREST)
-- ---------------------------------------------------------------------------------------------

create function idl_private.fail(p_code text, p_message text, p_detail text default null)
returns void language plpgsql as $$
begin
  -- PTxxx SQLSTATEs make PostgREST answer with HTTP status xxx.
  raise exception using errcode = p_code, message = p_message, detail = coalesce(p_detail, '');
end $$;

create function idl_private.require_uid() returns uuid language plpgsql stable as $$
declare v uuid := auth.uid();
begin
  if v is null then perform idl_private.fail('PT401', 'unauthorized'); end if;
  if not exists (select 1 from public.profiles where id = v) then
    perform idl_private.fail('PT404', 'no_profile');
  end if;
  return v;
end $$;

create function idl_private.iso(p_ts timestamptz) returns text language sql immutable as $$
  select to_char(p_ts at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
$$;

create function idl_private.are_friends(a uuid, b uuid) returns boolean language sql stable as $$
  select exists (select 1 from public.friendships where user_a = least(a, b) and user_b = greatest(a, b))
$$;

create function idl_private.blocked_either(a uuid, b uuid) returns boolean language sql stable as $$
  select exists (
    select 1 from public.blocks
    where (blocker_id = a and blocked_id = b) or (blocker_id = b and blocked_id = a)
  )
$$;

create function idl_private.audit(p_actor uuid, p_action text, p_target uuid) returns void language sql as $$
  insert into public.audit_log (actor_id, action, target_id) values (p_actor, p_action, p_target)
$$;

-- Enqueue the same ids-only payload for every accepted, unblocked friend.
create function idl_private.notify_friends(p_owner uuid, p_payload jsonb) returns void language sql as $$
  insert into public.push_outbox (recipient_id, payload)
  select case when f.user_a = p_owner then f.user_b else f.user_a end, p_payload
  from public.friendships f
  where p_owner in (f.user_a, f.user_b)
$$;

create function idl_private.notify(p_recipient uuid, p_payload jsonb) returns void language sql as $$
  insert into public.push_outbox (recipient_id, payload) values (p_recipient, p_payload)
$$;

-- --- Enums (kept in sync with app.idl.domain) -------------------------------------------------

create function idl_private.enum_values(p_enum text) returns text[] language sql immutable as $$
  select case p_enum
    when 'mood' then array['neutral','good','happy','excited','sleepy','tired','low_energy','stressed',
                           'anxious','sad','sick','focused','chaotic','social','overwhelmed']
    when 'availability' then array['available','text_only','call_ok','gaming','busy','do_not_disturb','afk','offline']
    when 'intent' then array['no_preference','invite_me','want_company','need_memes','ask_later','check_in','celebrating']
    when 'activity' then array['none','working','coding','gaming','vr','watching','listening','reading',
                               'traveling','exercising','sleeping','custom']
    when 'source' then array['manual','vrcq','desktop_bridge','discord','steam','xbox','external_sdk',
                             'android_local','inferred']
    when 'category' then array['avatar','availability','last_updated','mood','intent','activity_category',
                               'status_note','activity_name','joinable','external_links']
    when 'audience' then array['nobody','only_me','friends','close_friends','circles','individuals']
    when 'reaction' then array['heart','coffee','tea','snack','hug','high_five','same','you_okay',
                               'want_company','join','call_later','im_around','nice','good_luck']
    when 'base_form' then array['human','blob','robot','ghost','cat','fox','bear','alien','pixel']
  end
$$;

create function idl_private.source_priority(p_source text) returns int language sql immutable as $$
  select case p_source
    when 'manual' then 100 when 'vrcq' then 80 when 'desktop_bridge' then 70
    when 'discord' then 60 when 'steam' then 60 when 'xbox' then 60 when 'external_sdk' then 60
    when 'android_local' then 40 else 10 end
$$;

create function idl_private.source_ordinal(p_source text) returns int language sql immutable as $$
  select array_position(idl_private.enum_values('source'), p_source)
$$;

create function idl_private.default_audience(p_category text) returns text language sql immutable as $$
  select case p_category
    when 'avatar' then 'friends' when 'availability' then 'friends' when 'last_updated' then 'friends'
    when 'mood' then 'close_friends' when 'intent' then 'close_friends'
    when 'activity_category' then 'close_friends' when 'status_note' then 'close_friends'
    else 'only_me' end
$$;

-- --- Avatar composition (mirrors AvatarConfig / AvatarComposer) --------------------------------

create function idl_private.default_avatar() returns jsonb language sql immutable as $$
  select '{"baseForm":"human","bodyColor":-11172,"faceStyle":"classic","expression":"neutral",
           "headAccessory":"none","faceAccessory":"none","bodyAccessory":"none","handProp":"none",
           "scene":"plain_gradient","frameStyle":"squircle","themeColor":-8622862,"renderVersion":1}'::jsonb
$$;

-- AvatarConfig.minimal(): base form and colours only.
create function idl_private.minimal_avatar(p_base jsonb) returns jsonb language sql immutable as $$
  select idl_private.default_avatar() || jsonb_build_object(
    'baseForm', p_base -> 'baseForm', 'bodyColor', p_base -> 'bodyColor',
    'themeColor', p_base -> 'themeColor', 'frameStyle', p_base -> 'frameStyle')
$$;

create function idl_private.expression_for(p_mood text, p_availability text) returns text language sql immutable as $$
  select case p_mood
    when 'neutral' then 'neutral' when 'good' then 'happy' when 'happy' then 'happy'
    when 'excited' then 'excited' when 'sleepy' then 'sleepy' when 'tired' then 'tired'
    when 'low_energy' then 'tired' when 'stressed' then 'anxious' when 'anxious' then 'anxious'
    when 'sad' then 'sad' when 'sick' then 'sick' when 'focused' then 'focused'
    when 'chaotic' then 'mischievous' when 'social' then 'social' when 'overwhelmed' then 'overwhelmed'
    else case p_availability when 'afk' then 'afk' when 'do_not_disturb' then 'dnd' end
  end
$$;

create function idl_private.compose_avatar(p_base jsonb, p_resolved jsonb) returns jsonb language sql immutable as $$
  select p_base || jsonb_build_object(
    'expression', coalesce(p_resolved #>> '{visual,expression}',
                           idl_private.expression_for(p_resolved ->> 'mood', p_resolved ->> 'availability'),
                           p_base ->> 'expression'),
    'handProp', coalesce(p_resolved #>> '{visual,props,0}', p_base ->> 'handProp'),
    'scene', coalesce(p_resolved #>> '{visual,scene}', p_base ->> 'scene'),
    'headAccessory', coalesce(p_resolved #>> '{visual,headAccessory}', p_base ->> 'headAccessory'),
    'bodyAccessory', coalesce(p_resolved #>> '{visual,bodyAccessory}', p_base ->> 'bodyAccessory'))
$$;

-- --- Presence resolution (mirrors PresenceResolver with default SourceSettings) ----------------

create function idl_private.resolve_presence(p_owner uuid, p_at timestamptz) returns jsonb
language plpgsql stable as $$
declare
  st record;
  automated boolean;
  v_mood text; v_availability text; v_intent text; v_note text; v_activity jsonb;
  v_expression text; v_props jsonb; v_scene text; v_head text; v_body text;
  contributors uuid[] := '{}';
  v_updated timestamptz; v_expires timestamptz;
begin
  for st in
    select id, source, envelope as e
    from public.presence_states
    where user_id = p_owner and expires_at > p_at
    order by priority desc, updated_at desc, idl_private.source_ordinal(source) asc
  loop
    automated := st.source <> 'manual';
    if not automated then
      if v_mood is null and st.e ->> 'mood' is not null then v_mood := st.e ->> 'mood'; contributors := contributors || st.id; end if;
      if v_availability is null and st.e ->> 'availability' is not null then v_availability := st.e ->> 'availability'; contributors := contributors || st.id; end if;
      if v_intent is null and st.e ->> 'intent' is not null then v_intent := st.e ->> 'intent'; contributors := contributors || st.id; end if;
      if v_note is null and coalesce(st.e ->> 'note', '') ~ '\S' then v_note := st.e ->> 'note'; contributors := contributors || st.id; end if;
    end if;
    if v_activity is null and jsonb_typeof(st.e -> 'activity') = 'object'
       and coalesce(st.e #>> '{activity,type}', 'none') <> 'none' then
      v_activity := st.e -> 'activity'; contributors := contributors || st.id;
    end if;
    if v_expression is null and st.e #>> '{visual,expression}' is not null then v_expression := st.e #>> '{visual,expression}'; contributors := contributors || st.id; end if;
    if v_props is null and jsonb_typeof(st.e #> '{visual,props}') = 'array' and jsonb_array_length(st.e #> '{visual,props}') > 0 then
      v_props := st.e #> '{visual,props}'; contributors := contributors || st.id;
    end if;
    if v_scene is null and st.e #>> '{visual,scene}' is not null then v_scene := st.e #>> '{visual,scene}'; contributors := contributors || st.id; end if;
    if v_head is null and st.e #>> '{visual,headAccessory}' is not null then v_head := st.e #>> '{visual,headAccessory}'; contributors := contributors || st.id; end if;
    if v_body is null and st.e #>> '{visual,bodyAccessory}' is not null then v_body := st.e #>> '{visual,bodyAccessory}'; contributors := contributors || st.id; end if;
  end loop;

  select max(updated_at), min(expires_at) into v_updated, v_expires
  from public.presence_states where id = any (contributors);

  return jsonb_build_object(
    'mood', v_mood, 'availability', v_availability, 'intent', v_intent, 'note', v_note,
    'activity', v_activity,
    'visual', jsonb_build_object('expression', v_expression, 'props', coalesce(v_props, '[]'::jsonb),
                                 'scene', v_scene, 'headAccessory', v_head, 'bodyAccessory', v_body),
    'updatedAt', idl_private.iso(v_updated),
    'expiresAt', idl_private.iso(v_expires),
    'isEmpty', cardinality(contributors) = 0);
end $$;

-- --- Privacy (mirrors PrivacyFilter) -------------------------------------------------------------

create function idl_private.can_see(p_owner uuid, p_viewer uuid, p_category text) returns boolean
language plpgsql stable as $$
declare v_audience text; v_ids uuid[];
begin
  if p_owner = p_viewer then return true; end if;
  if not idl_private.are_friends(p_owner, p_viewer) or idl_private.blocked_either(p_owner, p_viewer) then
    return false;
  end if;
  select audience, audience_ids into v_audience, v_ids
  from public.privacy_rules where owner_id = p_owner and category = p_category;
  if v_audience is null then
    v_audience := idl_private.default_audience(p_category);
    v_ids := '{}';
  end if;
  return case v_audience
    when 'friends' then true
    when 'close_friends' then exists (
      select 1 from public.close_friends where owner_id = p_owner and friend_id = p_viewer)
    when 'circles' then exists (
      select 1 from public.circle_memberships m join public.circles c on c.id = m.circle_id
      where c.owner_id = p_owner and m.member_id = p_viewer and m.circle_id = any (v_ids))
    when 'individuals' then p_viewer = any (v_ids)
    else false
  end;
end $$;

-- What p_viewer may see of p_owner at p_at; null when nothing at all (not friends / blocked).
create function idl_private.presence_view(p_viewer uuid, p_owner uuid, p_at timestamptz) returns jsonb
language plpgsql stable as $$
declare
  v_base jsonb; r jsonb; v_resting jsonb; v_avatar jsonb; v_act jsonb; v_view jsonb;
  v_invisible boolean; can_avatar boolean; can_mood boolean; cat_visible boolean; join_visible boolean;
begin
  select config into v_base from public.avatar_configurations where user_id = p_owner;
  v_base := coalesce(v_base, idl_private.default_avatar());
  r := idl_private.resolve_presence(p_owner, p_at);
  v_act := nullif(r -> 'activity', 'null'::jsonb);

  if p_viewer = p_owner then
    return jsonb_strip_nulls(jsonb_build_object(
      'userId', p_owner, 'avatar', idl_private.compose_avatar(v_base, r), 'restingAvatar', v_base,
      'mood', r -> 'mood', 'availability', r -> 'availability', 'intent', r -> 'intent',
      'activityType', v_act -> 'type', 'activityLabel', v_act -> 'label',
      'joinable', v_act -> 'joinable', 'joinUrl', v_act -> 'joinUrl',
      'note', r -> 'note', 'updatedAt', r -> 'updatedAt', 'expiresAt', r -> 'expiresAt'));
  end if;

  if not idl_private.are_friends(p_owner, p_viewer) or idl_private.blocked_either(p_owner, p_viewer) then
    return null;
  end if;

  can_avatar := idl_private.can_see(p_owner, p_viewer, 'avatar');
  v_resting := case when can_avatar then v_base else idl_private.minimal_avatar(v_base) end;

  -- Invisible and "no status" must look identical.
  select invisible into v_invisible from public.profiles where id = p_owner;
  if coalesce(v_invisible, false) or (r ->> 'isEmpty')::boolean then
    return jsonb_build_object('userId', p_owner, 'avatar', v_resting, 'restingAvatar', v_resting);
  end if;

  can_mood := idl_private.can_see(p_owner, p_viewer, 'mood');
  v_avatar := case
    when not can_avatar then v_resting
    -- Derived-leak rule: the expression must not reveal a hidden mood.
    when not can_mood then idl_private.compose_avatar(v_base, r) || jsonb_build_object('expression', v_base -> 'expression')
    else idl_private.compose_avatar(v_base, r)
  end;
  cat_visible := v_act is not null and idl_private.can_see(p_owner, p_viewer, 'activity_category');
  join_visible := cat_visible and idl_private.can_see(p_owner, p_viewer, 'joinable');

  v_view := jsonb_strip_nulls(jsonb_build_object(
    'userId', p_owner, 'avatar', v_avatar, 'restingAvatar', v_resting,
    'mood', case when can_mood then r -> 'mood' end,
    'availability', case when idl_private.can_see(p_owner, p_viewer, 'availability') then r -> 'availability' end,
    'intent', case when idl_private.can_see(p_owner, p_viewer, 'intent') then r -> 'intent' end,
    'activityType', case when cat_visible then v_act -> 'type' end,
    'activityLabel', case when cat_visible and idl_private.can_see(p_owner, p_viewer, 'activity_name') then v_act -> 'label' end,
    'joinable', case when join_visible then v_act -> 'joinable' end,
    'joinUrl', case when join_visible then v_act -> 'joinUrl' end,
    'note', case when idl_private.can_see(p_owner, p_viewer, 'status_note') then r -> 'note' end,
    'updatedAt', case when idl_private.can_see(p_owner, p_viewer, 'last_updated') then r -> 'updatedAt' end));

  -- Expiry travels with any visible status so caches can expire it offline.
  if v_view ?| array['mood', 'availability', 'intent', 'activityType', 'note'] or v_avatar <> v_resting then
    v_view := v_view || jsonb_build_object('expiresAt', r -> 'expiresAt');
  end if;
  return v_view;
end $$;

create function idl_private.me_json(p_uid uuid) returns jsonb language sql stable as $$
  select jsonb_build_object('userId', id, 'username', username, 'displayName', display_name, 'invisible', invisible)
  from public.profiles where id = p_uid
$$;

create function idl_private.friend_json(p_me uuid, p_other uuid, p_status text) returns jsonb language sql stable as $$
  select jsonb_build_object(
    'userId', p.id, 'username', p.username, 'displayName', p.display_name, 'status', p_status,
    'isCloseFriend', exists (select 1 from public.close_friends where owner_id = p_me and friend_id = p.id))
  from public.profiles p where p.id = p_other
$$;

create function idl_private.reaction_json(r public.reactions) returns jsonb language sql immutable as $$
  select jsonb_strip_nulls(jsonb_build_object(
    'id', r.id, 'senderId', r.sender_id, 'recipientId', r.recipient_id, 'template', r.template,
    'createdAt', idl_private.iso(r.created_at), 'expiresAt', idl_private.iso(r.expires_at),
    'dismissedAt', idl_private.iso(r.dismissed_at)))
$$;

create function idl_private.privacy_rules_json(p_owner uuid) returns jsonb language sql stable as $$
  select jsonb_build_object('rules', coalesce(jsonb_object_agg(category, jsonb_build_object(
    'category', category, 'audience', audience, 'ids', to_jsonb(audience_ids))), '{}'::jsonb))
  from public.privacy_rules where owner_id = p_owner
$$;

-- ---------------------------------------------------------------------------------------------
-- Public RPC API (POST /rest/v1/rpc/<name>). Every function derives the caller from auth.uid().
-- ---------------------------------------------------------------------------------------------

-- Account ----------------------------------------------------------------------------------------

create function public.register_profile(p_username text, p_display_name text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := auth.uid(); v_name text := lower(btrim(p_username));
begin
  if v_uid is null then perform idl_private.fail('PT401', 'unauthorized'); end if;
  if exists (select 1 from profiles where id = v_uid) then return idl_private.me_json(v_uid); end if;
  if v_name !~ '^[a-z0-9_]{3,20}$' then
    perform idl_private.fail('PT400', '3–20 letters, numbers or _', 'username');
  end if;
  if char_length(btrim(coalesce(p_display_name, ''))) not between 1 and 40 then
    perform idl_private.fail('PT400', 'Display name must be 1–40 characters', 'displayName');
  end if;
  if exists (select 1 from profiles where username = v_name) then
    perform idl_private.fail('PT400', 'That username is taken', 'username');
  end if;
  insert into profiles (id, username, display_name) values (v_uid, v_name, btrim(p_display_name));
  insert into notification_preferences (user_id) values (v_uid);
  perform idl_private.audit(v_uid, 'account.created', v_uid);
  return idl_private.me_json(v_uid);
end $$;

create function public.me() returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
begin
  return idl_private.me_json(idl_private.require_uid());
end $$;

create function public.get_avatar() returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  return coalesce((select config from avatar_configurations where user_id = v_uid), idl_private.default_avatar());
end $$;

create function public.put_avatar(p_config jsonb) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid(); v_config jsonb;
begin
  if jsonb_typeof(p_config) <> 'object' or not (p_config ->> 'baseForm' = any (idl_private.enum_values('base_form'))) then
    perform idl_private.fail('PT400', 'Invalid avatar', 'config');
  end if;
  v_config := idl_private.default_avatar() || p_config;
  insert into avatar_configurations (user_id, config, render_version, updated_at)
  values (v_uid, v_config, coalesce((v_config ->> 'renderVersion')::int, 1), now())
  on conflict (user_id) do update set config = excluded.config, render_version = excluded.render_version, updated_at = now();
  perform idl_private.notify_friends(v_uid, jsonb_build_object('type', 'presence_changed', 'userId', v_uid));
  return v_config;
end $$;

-- Presence ---------------------------------------------------------------------------------------

create function public.put_presence(p_envelope jsonb) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_uid uuid := idl_private.require_uid();
  v_source text := coalesce(p_envelope ->> 'source', 'manual');
  v_started timestamptz; v_expires timestamptz; v_updated timestamptz;
begin
  -- Automated sources write through their own (service-role) integrations, never the client.
  if v_source <> 'manual' then perform idl_private.fail('PT403', 'Only manual status can be set by the app', 'source'); end if;
  begin
    v_started := (p_envelope ->> 'startedAt')::timestamptz;
    v_expires := (p_envelope ->> 'expiresAt')::timestamptz;
    v_updated := coalesce((p_envelope ->> 'updatedAt')::timestamptz, v_started);
  exception when others then
    perform idl_private.fail('PT400', 'Invalid timestamps', 'expiresAt');
  end;
  if v_started is null or v_expires is null or v_expires <= v_started or v_expires <= now() then
    perform idl_private.fail('PT400', 'Status must expire in the future', 'expiresAt');
  end if;
  if v_expires > v_started + interval '7 days' or v_started > now() + interval '5 minutes' then
    perform idl_private.fail('PT400', 'Status can last at most 7 days', 'expiresAt');
  end if;
  if char_length(coalesce(p_envelope ->> 'note', '')) > 80 then
    perform idl_private.fail('PT400', 'Keep notes under 80 characters', 'note');
  end if;
  if (p_envelope ? 'mood' and not (p_envelope ->> 'mood' = any (idl_private.enum_values('mood'))))
     or (p_envelope ? 'availability' and not (p_envelope ->> 'availability' = any (idl_private.enum_values('availability'))))
     or (p_envelope ? 'intent' and not (p_envelope ->> 'intent' = any (idl_private.enum_values('intent'))))
     or (p_envelope ? 'activity' and not (coalesce(p_envelope #>> '{activity,type}', 'none') = any (idl_private.enum_values('activity')))) then
    perform idl_private.fail('PT400', 'Unknown status value', 'envelope');
  end if;

  insert into presence_states (user_id, source, priority, envelope, started_at, expires_at, updated_at)
  values (v_uid, v_source, idl_private.source_priority(v_source), p_envelope, v_started, v_expires, v_updated)
  on conflict (user_id, source) do update set
    priority = excluded.priority, envelope = excluded.envelope, started_at = excluded.started_at,
    expires_at = excluded.expires_at, updated_at = excluded.updated_at;
  insert into presence_events (user_id, source, kind) values (v_uid, v_source, 'set');
  if not (select invisible from profiles where id = v_uid) then
    perform idl_private.notify_friends(v_uid, jsonb_build_object('type', 'presence_changed', 'userId', v_uid));
  end if;
  return p_envelope;
end $$;

create function public.clear_presence() returns void
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  delete from presence_states where user_id = v_uid and source = 'manual';
  insert into presence_events (user_id, source, kind) values (v_uid, 'manual', 'clear');
  perform idl_private.notify_friends(v_uid, jsonb_build_object('type', 'presence_changed', 'userId', v_uid));
end $$;

-- Turning invisible on sends the same refresh push as clearing a status, so friends' widgets
-- drop the status promptly without learning that invisibility (rather than a clear) caused it.
create function public.set_invisible(p_invisible boolean) returns void
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  update profiles set invisible = p_invisible, updated_at = now() where id = v_uid;
  insert into presence_events (user_id, source, kind)
  values (v_uid, 'manual', case when p_invisible then 'invisible_on' else 'invisible_off' end);
  perform idl_private.audit(v_uid, case when p_invisible then 'invisible.on' else 'invisible.off' end, v_uid);
  perform idl_private.notify_friends(v_uid, jsonb_build_object('type', 'presence_changed', 'userId', v_uid));
end $$;

create function public.friend_presence() returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  return coalesce((
    select jsonb_agg(v) from (
      select idl_private.presence_view(v_uid, case when f.user_a = v_uid then f.user_b else f.user_a end, now()) as v
      from friendships f where v_uid in (f.user_a, f.user_b)
    ) views where v is not null), '[]'::jsonb);
end $$;

create function public.friend_presence_one(p_user_id uuid) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid(); v jsonb;
begin
  v := idl_private.presence_view(v_uid, p_user_id, now());
  if v is null then perform idl_private.fail('PT403', 'forbidden'); end if;
  return v;
end $$;

-- Friends ---------------------------------------------------------------------------------------

create function public.friends() returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  return coalesce((
    select jsonb_agg(x order by lower(x ->> 'displayName')) from (
      select idl_private.friend_json(v_uid, case when f.user_a = v_uid then f.user_b else f.user_a end, 'accepted') x
      from friendships f where v_uid in (f.user_a, f.user_b)
      union all
      select idl_private.friend_json(v_uid, r.from_id, 'incoming') from friend_requests r where r.to_id = v_uid
      union all
      select idl_private.friend_json(v_uid, r.to_id, 'outgoing') from friend_requests r where r.from_id = v_uid
    ) all_friends), '[]'::jsonb);
end $$;

create function public.create_invite() returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_uid uuid := idl_private.require_uid();
  v_inv friend_invites;
  alphabet constant text := 'ABCDEFGHJKMNPQRSTUVWXYZ23456789';
  v_code text; v_bytes bytea; i int;
begin
  select * into v_inv from friend_invites
  where inviter_id = v_uid and not revoked and expires_at > now() + interval '1 day'
  order by created_at desc limit 1;
  if v_inv.id is null then
    loop
      v_bytes := extensions.gen_random_bytes(10);
      v_code := '';
      for i in 0..9 loop
        v_code := v_code || substr(alphabet, (get_byte(v_bytes, i) % length(alphabet)) + 1, 1);
        if i = 4 then v_code := v_code || '-'; end if;
      end loop;
      exit when not exists (select 1 from friend_invites where code = v_code);
    end loop;
    insert into friend_invites (inviter_id, code) values (v_uid, v_code) returning * into v_inv;
  end if;
  return jsonb_build_object('code', v_inv.code, 'url', 'idl://invite/' || v_inv.code,
                            'expiresAt', idl_private.iso(v_inv.expires_at));
end $$;

create function public.redeem_invite(p_code text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare
  v_uid uuid := idl_private.require_uid();
  v_code text := upper(btrim(p_code));
  v_inviter uuid;
begin
  v_code := regexp_replace(v_code, '^(IDL://INVITE/|HTTPS?://[^/]+/I/)', '');
  select inviter_id into v_inviter from friend_invites
  where code = v_code and not revoked and expires_at > now();
  -- Blocked users get the same answer as a bad code.
  if v_inviter is null or v_inviter = v_uid or idl_private.blocked_either(v_uid, v_inviter) then
    perform idl_private.fail('PT400', 'That invite code isn''t valid', 'code');
  end if;
  if idl_private.are_friends(v_uid, v_inviter) then
    return idl_private.friend_json(v_uid, v_inviter, 'accepted');
  end if;
  if exists (select 1 from friend_requests where from_id = v_inviter and to_id = v_uid) then
    return public.accept_request(v_inviter);
  end if;
  insert into friend_requests (from_id, to_id) values (v_uid, v_inviter) on conflict do nothing;
  perform idl_private.notify(v_inviter, jsonb_build_object('type', 'friend_request', 'userId', v_uid));
  return idl_private.friend_json(v_uid, v_inviter, 'outgoing');
end $$;

create function public.accept_request(p_user_id uuid) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  delete from friend_requests where from_id = p_user_id and to_id = v_uid;
  if not found then perform idl_private.fail('PT404', 'not_found'); end if;
  insert into friendships (user_a, user_b) values (least(v_uid, p_user_id), greatest(v_uid, p_user_id))
  on conflict do nothing;
  delete from friend_requests where from_id = v_uid and to_id = p_user_id;
  perform idl_private.audit(v_uid, 'friend.accepted', p_user_id);
  perform idl_private.notify(p_user_id, jsonb_build_object('type', 'friend_accepted', 'userId', v_uid));
  return idl_private.friend_json(v_uid, p_user_id, 'accepted');
end $$;

create function public.decline_request(p_user_id uuid) returns void
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  -- Also cancels our own outgoing request to that user.
  delete from friend_requests where (from_id = p_user_id and to_id = v_uid) or (from_id = v_uid and to_id = p_user_id);
end $$;

create function idl_private.sever(p_a uuid, p_b uuid) returns void language sql as $$
  delete from public.friendships where user_a = least(p_a, p_b) and user_b = greatest(p_a, p_b);
  delete from public.friend_requests where (from_id = p_a and to_id = p_b) or (from_id = p_b and to_id = p_a);
  delete from public.close_friends where (owner_id = p_a and friend_id = p_b) or (owner_id = p_b and friend_id = p_a);
  delete from public.circle_memberships m using public.circles c
    where c.id = m.circle_id and ((c.owner_id = p_a and m.member_id = p_b) or (c.owner_id = p_b and m.member_id = p_a));
$$;

create function public.remove_friend(p_user_id uuid) returns void
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  perform idl_private.sever(v_uid, p_user_id);
  perform idl_private.audit(v_uid, 'friend.removed', p_user_id);
  perform idl_private.notify(p_user_id, jsonb_build_object('type', 'friend_removed', 'userId', v_uid));
end $$;

create function public.set_close_friend(p_user_id uuid, p_close boolean) returns void
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  if not idl_private.are_friends(v_uid, p_user_id) then perform idl_private.fail('PT403', 'forbidden'); end if;
  if p_close then
    insert into close_friends (owner_id, friend_id) values (v_uid, p_user_id) on conflict do nothing;
  else
    delete from close_friends where owner_id = v_uid and friend_id = p_user_id;
  end if;
  -- Their view of us changed.
  perform idl_private.notify(p_user_id, jsonb_build_object('type', 'presence_changed', 'userId', v_uid));
end $$;

create function public.block_user(p_user_id uuid) returns void
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  if p_user_id = v_uid or not exists (select 1 from profiles where id = p_user_id) then
    perform idl_private.fail('PT404', 'not_found');
  end if;
  insert into blocks (blocker_id, blocked_id) values (v_uid, p_user_id) on conflict do nothing;
  perform idl_private.sever(v_uid, p_user_id);
  delete from reactions where (sender_id = v_uid and recipient_id = p_user_id) or (sender_id = p_user_id and recipient_id = v_uid);
  perform idl_private.audit(v_uid, 'user.blocked', p_user_id);
  -- Indistinguishable from a removal for the blocked user.
  perform idl_private.notify(p_user_id, jsonb_build_object('type', 'friend_removed', 'userId', v_uid));
end $$;

create function public.unblock_user(p_user_id uuid) returns void
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  delete from blocks where blocker_id = v_uid and blocked_id = p_user_id;
  perform idl_private.audit(v_uid, 'user.unblocked', p_user_id);
end $$;

create function public.blocked_users() returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  return coalesce((
    select jsonb_agg(jsonb_build_object('userId', p.id, 'username', p.username, 'displayName', p.display_name,
                                        'status', 'accepted', 'isCloseFriend', false) order by p.display_name)
    from blocks b join profiles p on p.id = b.blocked_id where b.blocker_id = v_uid), '[]'::jsonb);
end $$;

-- Reactions --------------------------------------------------------------------------------------

create function public.send_reaction(p_recipient_id uuid, p_template text) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid(); v_row reactions;
begin
  if not (p_template = any (idl_private.enum_values('reaction'))) then
    perform idl_private.fail('PT400', 'Unknown reaction', 'template');
  end if;
  if not idl_private.are_friends(v_uid, p_recipient_id) or idl_private.blocked_either(v_uid, p_recipient_id) then
    perform idl_private.fail('PT403', 'forbidden');
  end if;
  if (select count(*) from reactions where sender_id = v_uid and recipient_id = p_recipient_id
      and created_at > now() - interval '1 hour') >= 10 then
    perform idl_private.fail('PT429', 'rate_limited');
  end if;
  insert into reactions (sender_id, recipient_id, template) values (v_uid, p_recipient_id, p_template)
  returning * into v_row;
  perform idl_private.notify(p_recipient_id,
    jsonb_build_object('type', 'reaction_received', 'reactionId', v_row.id, 'userId', v_uid));
  return idl_private.reaction_json(v_row);
end $$;

create function public.reaction_inbox() returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  return coalesce((
    select jsonb_agg(idl_private.reaction_json(r) order by r.created_at desc)
    from reactions r
    where r.recipient_id = v_uid and r.dismissed_at is null and r.expires_at > now()), '[]'::jsonb);
end $$;

create function public.dismiss_reaction(p_id uuid) returns void
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  update reactions set dismissed_at = coalesce(dismissed_at, now()) where id = p_id and recipient_id = v_uid;
end $$;

-- Privacy ----------------------------------------------------------------------------------------

create function public.get_privacy_rules() returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
begin
  return idl_private.privacy_rules_json(idl_private.require_uid());
end $$;

-- Replaces the caller's rule set; categories absent from p_rules fall back to defaults.
create function public.put_privacy_rules(p_rules jsonb) returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid(); k text; v jsonb;
begin
  if jsonb_typeof(p_rules -> 'rules') is distinct from 'object' then
    perform idl_private.fail('PT400', 'Invalid rules', 'rules');
  end if;
  delete from privacy_rules where owner_id = v_uid;
  for k, v in select * from jsonb_each(p_rules -> 'rules') loop
    if not (k = any (idl_private.enum_values('category')))
       or not (v ->> 'audience' = any (idl_private.enum_values('audience'))) then
      perform idl_private.fail('PT400', 'Invalid rule', k);
    end if;
    insert into privacy_rules (owner_id, category, audience, audience_ids)
    values (v_uid, k, v ->> 'audience',
            coalesce((select array_agg(x::uuid) from jsonb_array_elements_text(v -> 'ids') x), '{}'));
  end loop;
  perform idl_private.audit(v_uid, 'privacy.rules_changed', v_uid);
  perform idl_private.notify_friends(v_uid, jsonb_build_object('type', 'presence_changed', 'userId', v_uid));
  return idl_private.privacy_rules_json(v_uid);
end $$;

-- Devices ----------------------------------------------------------------------------------------

create function public.register_device(p_token text, p_platform text default 'android') returns jsonb
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid(); v_id uuid;
begin
  if char_length(coalesce(p_token, '')) not between 10 and 4096 then
    perform idl_private.fail('PT400', 'Invalid token', 'token');
  end if;
  insert into devices (user_id, fcm_token, platform) values (v_uid, p_token, coalesce(p_platform, 'android'))
  on conflict (fcm_token) do update set user_id = excluded.user_id, last_seen_at = now()
  returning id into v_id;
  return jsonb_build_object('id', v_id);
end $$;

create function public.delete_device(p_id uuid) returns void
language plpgsql security definer set search_path = public, pg_temp as $$
declare v_uid uuid := idl_private.require_uid();
begin
  delete from devices where id = p_id and user_id = v_uid;
end $$;

-- ---------------------------------------------------------------------------------------------
-- Lock down: RLS on, no policies, no table privileges; only the RPCs above are callable.
-- ---------------------------------------------------------------------------------------------

do $$
declare t text;
begin
  for t in select tablename from pg_tables where schemaname = 'public' loop
    execute format('alter table public.%I enable row level security', t);
  end loop;
end $$;

revoke all on all tables in schema public from anon, authenticated;
revoke all on all sequences in schema public from anon, authenticated;
revoke all on schema idl_private from public, anon, authenticated;
revoke execute on all functions in schema public from public, anon, authenticated;
revoke execute on all functions in schema idl_private from public, anon, authenticated;

grant execute on function
  public.register_profile(text, text), public.me(), public.get_avatar(), public.put_avatar(jsonb),
  public.put_presence(jsonb), public.clear_presence(), public.set_invisible(boolean),
  public.friend_presence(), public.friend_presence_one(uuid), public.friends(), public.create_invite(),
  public.redeem_invite(text), public.accept_request(uuid), public.decline_request(uuid),
  public.remove_friend(uuid), public.set_close_friend(uuid, boolean), public.block_user(uuid),
  public.unblock_user(uuid), public.blocked_users(), public.send_reaction(uuid, text),
  public.reaction_inbox(), public.dismiss_reaction(uuid), public.get_privacy_rules(),
  public.put_privacy_rules(jsonb), public.register_device(text, text), public.delete_device(uuid)
to authenticated;
