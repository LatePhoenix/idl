-- Behaviour and lockdown tests for the RPC API, executed as the real `authenticated` / `anon`
-- roles with JWT claims set the way PostgREST sets them.

-- Users: ari, mo, kit (owner of an invite), eve (stranger).
insert into auth.users (id) values
  ('00000000-0000-4000-8000-000000000001'),
  ('00000000-0000-4000-8000-000000000002'),
  ('00000000-0000-4000-8000-000000000003'),
  ('00000000-0000-4000-8000-000000000004');

-- ---- Lockdown -------------------------------------------------------------------------------
set role anon;
do $$ begin
  perform public.me();
  raise exception 'anon must not execute RPCs';
exception when insufficient_privilege then null;
end $$;
reset role;

select test.login('00000000-0000-4000-8000-000000000001');
set role authenticated;
do $$ begin
  perform 1 from public.profiles;
  raise exception 'authenticated must not read tables directly';
exception when insufficient_privilege then null;
end $$;
do $$ begin
  perform idl_private.presence_view(auth.uid(), auth.uid(), now());
  raise exception 'authenticated must not reach idl_private';
exception when insufficient_privilege then null;
end $$;
-- Calls before a profile exists are rejected with 404 (client then shows profile setup).
do $$ begin
  perform public.me();
  raise exception 'expected no_profile';
exception when sqlstate 'PT404' then null;
end $$;

-- ---- Registration ---------------------------------------------------------------------------
select test.assert(public.register_profile('Ari', 'Ari') ->> 'username' = 'ari', 'register lowercases');
select test.assert(public.register_profile('ignored', 'X') ->> 'username' = 'ari', 'register is idempotent');
reset role;

select test.login('00000000-0000-4000-8000-000000000002');
set role authenticated;
do $$ begin
  perform public.register_profile('ari', 'Another Ari');
  raise exception 'expected taken';
exception when sqlstate 'PT400' then
  null;
end $$;
do $$ begin
  perform public.register_profile('a!', 'Mo');
  raise exception 'expected invalid';
exception when sqlstate 'PT400' then null;
end $$;
select public.register_profile('mobot', 'Mo');
reset role;

select test.login('00000000-0000-4000-8000-000000000003');
set role authenticated;
select public.register_profile('kit', 'Kit');
create temp table kit_invite as select public.create_invite() as inv;
select test.assert((select inv ->> 'code' from kit_invite) ~ '^[A-Z2-9]{5}-[A-Z2-9]{5}$', 'invite code format');
select test.assert(public.create_invite() = (select inv from kit_invite), 'active invite is reused');
reset role;

select test.login('00000000-0000-4000-8000-000000000004');
set role authenticated;
select public.register_profile('eve', 'Eve');
reset role;

-- ---- Invite -> request -> accept -------------------------------------------------------------
select test.login('00000000-0000-4000-8000-000000000001');
set role authenticated;
do $$ begin
  perform public.redeem_invite('NOPE-NOPE');
  raise exception 'expected invalid code';
exception when sqlstate 'PT400' then null;
end $$;
select test.assert(
  public.redeem_invite('idl://invite/' || lower((select inv ->> 'code' from kit_invite))) ->> 'status' = 'outgoing',
  'redeem creates outgoing request (link form, any case)');
select test.assert(public.friends() -> 0 ->> 'status' = 'outgoing', 'friends lists outgoing');
do $$ begin
  perform public.friend_presence_one('00000000-0000-4000-8000-000000000003');
  raise exception 'pending request must not grant access';
exception when sqlstate 'PT403' then null;
end $$;
reset role;

select test.login('00000000-0000-4000-8000-000000000003');
set role authenticated;
select test.assert(public.friends() -> 0 ->> 'status' = 'incoming', 'kit sees incoming');
select test.assert(public.accept_request('00000000-0000-4000-8000-000000000001') ->> 'status' = 'accepted', 'accept');
reset role;
select test.assert(
  exists (select 1 from public.push_outbox where recipient_id = '00000000-0000-4000-8000-000000000001'
          and payload ->> 'type' = 'friend_accepted'),
  'acceptance is pushed to the requester');

-- Ari and Mo become friends through Mo's invite.
select test.login('00000000-0000-4000-8000-000000000002');
set role authenticated;
create temp table mo_invite as select public.create_invite() ->> 'code' as code;
reset role;
select test.login('00000000-0000-4000-8000-000000000001');
set role authenticated;
select public.redeem_invite((select code from mo_invite));
reset role;
select test.login('00000000-0000-4000-8000-000000000002');
set role authenticated;
select public.accept_request('00000000-0000-4000-8000-000000000001');

-- ---- Presence + privacy ---------------------------------------------------------------------
-- Mo sets a status; Ari is a friend but not on Mo's close list: avatar + availability only.
select public.put_presence(jsonb_build_object(
  'source', 'manual', 'mood', 'stressed', 'availability', 'busy', 'note', 'deadline day',
  'activity', jsonb_build_object('type', 'coding', 'source', 'manual', 'joinable', false),
  'visual', jsonb_build_object('props', jsonb_build_array('keyboard')),
  'startedAt', test.ts(), 'expiresAt', test.ts(interval '2 hours'), 'updatedAt', test.ts(), 'priority', 100));
do $$ begin
  perform public.put_presence(jsonb_build_object('source', 'vrcq', 'startedAt', test.ts(), 'expiresAt', test.ts(interval '1 hour')));
  raise exception 'clients must not write automated sources';
exception when sqlstate 'PT403' then null;
end $$;
do $$ begin
  perform public.put_presence(jsonb_build_object('source', 'manual', 'mood', 'happy', 'startedAt', test.ts(), 'expiresAt', test.ts(interval '8 days')));
  raise exception 'expected max duration';
exception when sqlstate 'PT400' then null;
end $$;
do $$ begin
  perform public.put_presence(jsonb_build_object('source', 'manual', 'mood', 'ecstatic', 'startedAt', test.ts(), 'expiresAt', test.ts(interval '1 hour')));
  raise exception 'expected enum validation';
exception when sqlstate 'PT400' then null;
end $$;
do $$ begin
  perform public.put_presence(jsonb_build_object('source', 'manual', 'note', repeat('x', 81), 'startedAt', test.ts(), 'expiresAt', test.ts(interval '1 hour')));
  raise exception 'expected note length';
exception when sqlstate 'PT400' then null;
end $$;
reset role;
select test.assert(
  (select count(*) from public.push_outbox where recipient_id = '00000000-0000-4000-8000-000000000001'
   and payload = jsonb_build_object('type', 'presence_changed', 'userId', '00000000-0000-4000-8000-000000000002')) = 1,
  'presence change fans out an ids-only push to friends');
select test.assert(
  not exists (select 1 from public.push_outbox where recipient_id = '00000000-0000-4000-8000-000000000004'),
  'strangers get no pushes');

select test.login('00000000-0000-4000-8000-000000000001');
set role authenticated;
create temp table mo_view as select public.friend_presence_one('00000000-0000-4000-8000-000000000002') as v;
select test.assert((select v ->> 'availability' from mo_view) = 'busy', 'friend sees availability');
select test.assert((select not (v ?| array['mood', 'note', 'activityType', 'intent']) from mo_view), 'non-close friend sees no mood/note/activity');
select test.assert((select not (v ? 'mood') and (v #>> '{visual,expressionId}') is null from mo_view), 'hidden mood does not leak through expression');
select test.assert((select v #>> '{visual,propAssetId}' from mo_view) = 'prop_keyboard', 'avatar props are visible');
select test.assert((select v ? 'expiresAt' from mo_view), 'expiry travels with status');
select test.assert(jsonb_array_length(public.friend_presence()) = 2, 'friend_presence lists accepted friends only');
reset role;

-- Mo marks Ari close: mood + note become visible.
select test.login('00000000-0000-4000-8000-000000000002');
set role authenticated;
select public.set_close_friend('00000000-0000-4000-8000-000000000001', true);
do $$ begin
  perform public.set_close_friend('00000000-0000-4000-8000-000000000004', true);
  raise exception 'close friend requires friendship';
exception when sqlstate 'PT403' then null;
end $$;
reset role;
select test.login('00000000-0000-4000-8000-000000000001');
set role authenticated;
select test.assert(public.friend_presence_one('00000000-0000-4000-8000-000000000002') ->> 'note' = 'deadline day', 'close friend sees note');
select test.assert(public.friend_presence_one('00000000-0000-4000-8000-000000000002') #>> '{visual,expressionId}' = 'anxious', 'close friend sees mood expression');
reset role;

-- Mo restricts notes to nobody, then goes invisible.
select test.login('00000000-0000-4000-8000-000000000002');
set role authenticated;
select public.put_privacy_rules('{"rules":{"status_note":{"category":"status_note","audience":"nobody","ids":[]}}}');
select test.assert(public.get_privacy_rules() #>> '{rules,status_note,audience}' = 'nobody', 'rules round trip');
do $$ begin
  perform public.put_privacy_rules('{"rules":{"status_note":{"category":"status_note","audience":"public","ids":[]}}}');
  raise exception 'expected invalid audience';
exception when sqlstate 'PT400' then null;
end $$;
reset role;
select test.login('00000000-0000-4000-8000-000000000001');
set role authenticated;
select test.assert(not (public.friend_presence_one('00000000-0000-4000-8000-000000000002') ? 'note'), 'nobody hides note from close friend');
reset role;
select test.login('00000000-0000-4000-8000-000000000002');
set role authenticated;
select public.set_invisible(true);
select test.assert((public.me() ->> 'invisible')::boolean, 'me reports invisible');
reset role;
select test.login('00000000-0000-4000-8000-000000000001');
set role authenticated;
select test.assert(
  public.friend_presence_one('00000000-0000-4000-8000-000000000002') - 'identity'
    = jsonb_build_object('userId', '00000000-0000-4000-8000-000000000002'),
  'invisible friend shows no status at all');
reset role;

-- ---- Reactions -------------------------------------------------------------------------------
select test.login('00000000-0000-4000-8000-000000000001');
set role authenticated;
select count(public.send_reaction('00000000-0000-4000-8000-000000000002', 'coffee')) from generate_series(1, 10);
do $$ begin
  perform public.send_reaction('00000000-0000-4000-8000-000000000002', 'coffee');
  raise exception 'expected rate limit';
exception when sqlstate 'PT429' then null;
end $$;
do $$ begin
  perform public.send_reaction('00000000-0000-4000-8000-000000000004', 'heart');
  raise exception 'strangers cannot be reacted to';
exception when sqlstate 'PT403' then null;
end $$;
do $$ begin
  perform public.send_reaction('00000000-0000-4000-8000-000000000002', 'slap');
  raise exception 'unknown template';
exception when sqlstate 'PT400' then null;
end $$;
reset role;
select test.login('00000000-0000-4000-8000-000000000002');
set role authenticated;
select test.assert(jsonb_array_length(public.reaction_inbox()) = 10, 'inbox has reactions');
create temp table first_reaction as select (public.reaction_inbox() -> 0 ->> 'id')::uuid as id;
reset role;
-- Another user cannot dismiss Mo's reaction.
select test.login('00000000-0000-4000-8000-000000000004');
set role authenticated;
select public.dismiss_reaction((select id from first_reaction));
reset role;
select test.login('00000000-0000-4000-8000-000000000002');
set role authenticated;
select test.assert(jsonb_array_length(public.reaction_inbox()) = 10, 'strangers cannot dismiss');
select public.dismiss_reaction((select id from first_reaction));
select test.assert(jsonb_array_length(public.reaction_inbox()) = 9, 'dismissed reaction leaves inbox');

-- ---- Block revokes immediately ----------------------------------------------------------------
select public.block_user('00000000-0000-4000-8000-000000000001');
select test.assert(jsonb_array_length(public.reaction_inbox()) = 0, 'block removes reactions');
select test.assert(public.blocked_users() -> 0 ->> 'username' = 'ari', 'blocked list');
reset role;
select test.login('00000000-0000-4000-8000-000000000001');
set role authenticated;
do $$ begin
  perform public.friend_presence_one('00000000-0000-4000-8000-000000000002');
  raise exception 'blocked user must lose access';
exception when sqlstate 'PT403' then null;
end $$;
do $$ begin
  perform public.redeem_invite((select code from mo_invite));
  raise exception 'blocked user cannot re-redeem';
exception when sqlstate 'PT400' then null;
end $$;
select test.assert(
  not exists (select 1 from jsonb_array_elements(public.friends()) f where f ->> 'username' = 'mobot'),
  'blocked friend gone from list');
reset role;
select test.assert(exists (select 1 from public.audit_log where action = 'user.blocked'), 'block is audited');

-- ---- Remove friend --------------------------------------------------------------------------
select test.login('00000000-0000-4000-8000-000000000003');
set role authenticated;
select public.remove_friend('00000000-0000-4000-8000-000000000001');
reset role;
select test.login('00000000-0000-4000-8000-000000000001');
set role authenticated;
select test.assert(jsonb_array_length(public.friend_presence()) = 0, 'removal revokes access');
reset role;

-- ---- Schema 3 put_avatar catalog checks -----------------------------------------------------
select test.login('00000000-0000-4000-8000-000000000001');
set role authenticated;
select test.assert(
  public.put_avatar('{"baseAssetId":"base_teardrop","paletteAssetId":"palette_sunny","schemaVersion":3,
    "restingExpressionId":"happy","familyId":"teardrop_face"}'::jsonb) ->> 'baseAssetId' = 'base_teardrop',
  'put_avatar accepts free schema 3');
select test.assert(public.get_avatar() ->> 'paletteAssetId' = 'palette_sunny', 'get_avatar returns stored recipe');
do $$ begin
  perform public.put_avatar('{"baseAssetId":"base_blob","paletteAssetId":"palette_sunny","schemaVersion":3}'::jsonb);
  raise exception 'retired base must be rejected';
exception when sqlstate 'PT400' then null;
end $$;
do $$ begin
  perform public.put_avatar('{"baseAssetId":"base_teardrop","paletteAssetId":"palette_nope","schemaVersion":3}'::jsonb);
  raise exception 'unknown palette must be rejected';
exception when sqlstate 'PT400' then null;
end $$;
do $$ begin
  perform public.put_avatar('{"baseForm":"fox","expression":"happy"}'::jsonb);
  raise exception 'v1 avatar must be rejected by put_avatar';
exception when sqlstate 'PT400' then null;
end $$;
reset role;

select 'behavior: all assertions passed' as result;
