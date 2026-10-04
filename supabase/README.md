# iDL on Supabase

`migrations/` is the whole server: schema, row-level security lockdown, and the RPC API the
Android client calls (`app/src/main/java/app/idl/data/remote/supabase/`).

## Security model in one paragraph

Clients never touch tables directly. RLS is enabled with **no policies**, and all table
privileges are revoked from `anon`/`authenticated`. The only callable surface is the
`SECURITY DEFINER` functions granted to `authenticated` at the bottom of the migration. Each one
derives the caller from `auth.uid()`. Friend presence is produced by `idl_private.presence_view()`,
the SQL port of the Kotlin reference `PrivacyFilter`. Both are checked against the same golden
vectors in `contract/privacy_vectors.json`.

## Testing locally (no Supabase account needed)

Requires Docker.

```bash
supabase/tests/run.sh                 # stock Postgres + auth shim: privacy vectors + behaviour/RLS tests
supabase/tests/run.sh --rest          # same, then keep PostgREST running on :54330
./gradlew testDebugUnitTest --tests '*SupabaseRestIT*' -Pidl.postgrestUrl=http://localhost:54330
supabase/tests/run.sh --down
```

`tests/sql/00_shim.sql` recreates just enough of Supabase (roles, `auth.users`, `auth.uid()`).
It is a stand-in, so run the migration against a real Supabase instance before the alpha
(below).

After an intentional change to privacy behaviour, update the Kotlin `PrivacyFilter`, regenerate
the vectors with `./gradlew testDebugUnitTest -Pidl.updateGolden=true`, review the JSON diff,
and make the SQL pass again.

## Setting up a real project

1. Create a Supabase project (dashboard), then link and push:
   ```bash
   supabase link --project-ref <ref>
   supabase db push
   ```
2. **Auth → Providers → Email**: enable it. Sign-in is passwordless.
3. **Auth → Email Templates → Magic Link**: include `{{ .Token }}` so users get a 6-digit code.
   The app does not handle magic-link deep links yet.
4. Add the following to `local.properties` (never committed). The anon key is the public client
   key; never put the service-role key in the app.
   ```properties
   supabase.url=https://<ref>.supabase.co
   supabase.anonKey=<anon public key>
   ```
5. Rebuild. Onboarding switches from demo mode to email sign-in automatically, and Settings
   shows "Connected to the iDL server".

## Not done yet (Milestone 1 remainder)

- `push-fanout` edge function: drain `push_outbox` and send FCM data messages. Pushes are
  already enqueued, with ids-only payloads.
- Scheduled cleanup of expired `presence_states` / `reactions` (pg_cron). Reads already ignore
  expired rows.
- Account deletion RPC.
- A CI job that applies the migration to the official Supabase Postgres image.
