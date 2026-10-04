#!/usr/bin/env bash
# Spins up Postgres (+ PostgREST with --rest), applies the Supabase auth shim and every migration,
# then runs the SQL test suites. Usage:
#   supabase/tests/run.sh           # schema + privacy vectors + behaviour tests, then tear down
#   supabase/tests/run.sh --rest    # same, then leave PostgREST on :54330 for the Kotlin IT
#   supabase/tests/run.sh --down    # tear down
set -euo pipefail
export MSYS_NO_PATHCONV=1 # keep Git Bash on Windows from rewriting /work paths
cd "$(dirname "$0")"

compose() { docker compose -f docker-compose.yml "$@"; }
psql_file() { compose exec -T db psql -U postgres -d postgres -v ON_ERROR_STOP=1 -q -o /dev/null -f "$1"; }

if [[ "${1:-}" == "--down" ]]; then compose down -v; exit 0; fi

compose down -v >/dev/null 2>&1 || true
compose up -d --wait db

echo "== shim"
psql_file /work/supabase/tests/sql/00_shim.sql
for migration in ../migrations/*.sql; do
  echo "== migration $(basename "$migration")"
  psql_file "/work/supabase/migrations/$(basename "$migration")"
done
psql_file /work/supabase/tests/sql/90_test_support.sql

echo "== privacy vectors"
psql_file /work/supabase/tests/sql/privacy_vectors.sql
echo "== behaviour"
psql_file /work/supabase/tests/sql/behavior.sql

if [[ "${1:-}" == "--rest" ]]; then
  compose up -d rest
  for _ in $(seq 1 30); do
    if curl -fs http://localhost:54330/ >/dev/null 2>&1; then echo "PostgREST ready on http://localhost:54330"; exit 0; fi
    sleep 1
  done
  echo "PostgREST did not become ready" >&2
  compose logs rest >&2
  exit 1
fi

compose down -v >/dev/null
echo "All SQL tests passed."
