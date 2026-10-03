-- Test-only helpers, applied AFTER migrations (which revoke everything). Never deploy this.
create schema test;
grant usage on schema test to public;

create function test.assert(p_ok boolean, p_message text) returns void language plpgsql as $$
begin
  if p_ok is not true then raise exception 'ASSERTION FAILED: %', p_message; end if;
end $$;
grant execute on function test.assert(boolean, text) to public;

-- Stands in for GoTrue creating the auth user. Exposed so the Kotlin PostgREST test can use it.
create function public.test_create_auth_user(p_id uuid) returns void
language sql security definer set search_path = public, pg_temp as $$
  insert into auth.users (id) values (p_id) on conflict do nothing
$$;
grant execute on function public.test_create_auth_user(uuid) to anon, authenticated;

create function test.login(p_id uuid) returns void language sql as $$
  select set_config('request.jwt.claims', json_build_object('sub', p_id, 'role', 'authenticated')::text, false)
$$;
grant execute on function test.login(uuid) to public;

-- ISO-8601 timestamp relative to now, in the client's wire format.
create function test.ts(p_offset interval default interval '0') returns text language sql stable as $$
  select to_char((now() + p_offset) at time zone 'UTC', 'YYYY-MM-DD"T"HH24:MI:SS"Z"')
$$;
grant execute on function test.ts(interval) to public;
