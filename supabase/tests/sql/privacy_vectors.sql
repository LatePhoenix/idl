-- Asserts idl_private.presence_view() matches the Kotlin reference PrivacyFilter for every case in
-- contract/privacy_vectors.json. Run as superuser after migrations (see ../run.sh).
\set vectors `cat /work/contract/privacy_vectors.json`

create temp table vectors as select :'vectors'::jsonb as doc;

do $$
declare
  c jsonb; s jsonb; k text; v jsonb; cid text;
  v_owner uuid; v_viewer uuid; actual jsonb; expected jsonb;
  failures text[] := '{}'; total int := 0;
begin
  for c in select jsonb_array_elements(doc -> 'cases') from vectors loop
    total := total + 1;
    -- Fresh world per case.
    delete from public.profiles;
    delete from auth.users;

    v_owner := (c ->> 'ownerId')::uuid;
    v_viewer := (c ->> 'viewerId')::uuid;
    insert into auth.users (id) values (v_owner), (v_viewer) on conflict do nothing;
    insert into public.profiles (id, username, display_name, invisible)
      values (v_owner, 'owner', 'Owner', (c ->> 'ownerInvisible')::boolean);
    if v_viewer <> v_owner then
      insert into public.profiles (id, username, display_name) values (v_viewer, 'viewer', 'Viewer');
    end if;

    if c -> 'avatar' is not null then
      insert into public.avatar_configurations (user_id, config) values (v_owner, c -> 'avatar');
    end if;

    if v_viewer <> v_owner then
      if (c #>> '{relationship,isFriend}')::boolean then
        insert into public.friendships (user_a, user_b) values (least(v_owner, v_viewer), greatest(v_owner, v_viewer));
      end if;
      if (c #>> '{relationship,isCloseFriend}')::boolean then
        insert into public.close_friends (owner_id, friend_id) values (v_owner, v_viewer);
      end if;
      if (c #>> '{relationship,blocked}')::boolean then
        insert into public.blocks (blocker_id, blocked_id) values (v_owner, v_viewer);
      end if;
      for cid in select jsonb_array_elements_text(c #> '{relationship,circleIds}') loop
        insert into public.circles (id, owner_id, name) values (cid::uuid, v_owner, 'circle');
        insert into public.circle_memberships (circle_id, member_id) values (cid::uuid, v_viewer);
      end loop;
    end if;

    for s in select jsonb_array_elements(c -> 'states') loop
      insert into public.presence_states (user_id, source, priority, envelope, started_at, expires_at, updated_at)
      values (v_owner, s ->> 'source', (s ->> 'priority')::int, s,
              (s ->> 'startedAt')::timestamptz, (s ->> 'expiresAt')::timestamptz, (s ->> 'updatedAt')::timestamptz);
    end loop;

    for k, v in select key, value from jsonb_each(c #> '{rules,rules}') loop
      insert into public.privacy_rules (owner_id, category, audience, audience_ids)
      values (v_owner, k, v ->> 'audience',
              coalesce((select array_agg(x::uuid) from jsonb_array_elements_text(v -> 'ids') x), '{}'));
    end loop;

    actual := idl_private.presence_view(v_viewer, v_owner, (c ->> 'now')::timestamptz);
    expected := c -> 'expected';
    if actual is distinct from expected then
      failures := failures || format(E'%s\n    expected: %s\n    actual:   %s', c ->> 'name', expected, actual);
    end if;
  end loop;

  delete from public.profiles;
  delete from auth.users;

  if cardinality(failures) > 0 then
    raise exception E'% of % privacy vectors failed:\n  %', cardinality(failures), total, array_to_string(failures, E'\n  ');
  end if;
  raise notice 'privacy vectors: % / % passed', total, total;
end $$;
