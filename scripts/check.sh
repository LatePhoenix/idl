#!/usr/bin/env bash
# One command to verify the repo. Run before every commit and before asking for review.
#   scripts/check.sh            unit tests + snapshot verify + lint + debug build (always)
#   scripts/check.sh --sql      + Supabase SQL suite and Kotlin↔PostgREST IT (needs Docker)
#   scripts/check.sh --device   + instrumented tests (needs an emulator; set ANDROID_SERIAL
#                                 if more than one device is attached)
#   scripts/check.sh --all      everything
set -euo pipefail
cd "$(dirname "$0")/.."

sql=false; device=false
for arg in "$@"; do
  case "$arg" in
    --sql) sql=true ;;
    --device) device=true ;;
    --all) sql=true; device=true ;;
    *) echo "unknown option $arg" >&2; exit 2 ;;
  esac
done

echo "== Gradle: unit tests, snapshot verify, lint, debug build"
./gradlew verifyRoborazziDebug lintDebug assembleDebug --console=plain -q

summarize() {
  python - "$1" <<'PY'
import glob, re, sys
t = f = s = 0
for x in glob.glob(sys.argv[1] + "/*.xml"):
    m = re.search(r'tests="(\d+)" skipped="(\d+)" failures="(\d+)" errors="(\d+)"', open(x, encoding="utf-8").read())
    if m:
        t += int(m[1]); s += int(m[2]); f += int(m[3]) + int(m[4])
print(f"   {t} tests, {f} failed, {s} skipped")
PY
}
summarize app/build/test-results/testDebugUnitTest
lint=$(find app/build -name "lint-results-debug.txt" | head -1)
[[ -n "$lint" ]] && echo "   lint: $(tail -1 "$lint")"

if $sql; then
  if ! docker info >/dev/null 2>&1; then
    echo "Docker is not running: start Docker Desktop, then rerun with --sql." >&2
    exit 1
  fi
  echo "== Supabase SQL suite + PostgREST"
  supabase/tests/run.sh --rest
  trap 'supabase/tests/run.sh --down >/dev/null 2>&1 || true' EXIT
  echo "== Kotlin client against PostgREST"
  ./gradlew testDebugUnitTest --tests '*SupabaseRestIT*' -Pidl.postgrestUrl=http://localhost:54330 --console=plain -q
fi

adb_cmd() {
  if [[ -n "${ANDROID_SERIAL:-}" ]]; then
    adb -s "$ANDROID_SERIAL" "$@"
  else
    adb "$@"
  fi
}

if $device; then
  echo "== Wait for the device to finish booting"
  adb_cmd wait-for-device
  boot=""
  for _ in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30; do
    boot=$(adb_cmd shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
    [[ "$boot" == "1" ]] && break
    sleep 2
  done
  [[ "$boot" == "1" ]] || { echo "device did not finish booting" >&2; exit 1; }
  adb_cmd shell input keyevent 82
  echo "== Instrumented tests on ${ANDROID_SERIAL:-the attached device}"
  ./gradlew connectedDebugAndroidTest --console=plain -q
fi

echo "All checks passed."
