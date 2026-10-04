# Room migration test — checkpoint

Date: 2026-10-04. Branch: `test/room-migration-f13` from `origin/main` (`5756cf5`).

## Acceptance

| Criterion | Status |
| --- | --- |
| `MigrationTestHelper` test for `MIGRATION_1_2` using exported schemas 1 and 2 | Met |
| Representative v1 rows survive | Met (one row in each v1 table) |
| v2 columns have their defaults | Met: new tables are empty; `tetherActivatedEpochMs` is null when omitted |
| `androidx.room:room-testing` called out | Met. Same Room 2.6.1 artifact, test-only. Not a new vendor |
| Device run on emulator-5554 | Met |

## What changed

`Migration1To2Test` creates a v1 database from `app/schemas/app.idl.data.local.IdlDatabase/1.json`, inserts a session, avatar, own presence, friend, friend presence, reaction, privacy rules, widget subscription and sync state, then runs `MIGRATION_1_2`. `runMigrationsAndValidate` checks the result against `2.json`. The test then reads every inserted row back and checks `user_economy` and `friend_tethers` are empty. Inserting a tether without `tetherActivatedEpochMs` stores null.

Android test assets include `app/schemas` so `MigrationTestHelper` can find the exported files.

## Files

- `gradle/libs.versions.toml` — `room-testing` at the existing Room version
- `app/build.gradle.kts` — androidTest assets + `androidTestImplementation`
- `app/src/androidTest/java/app/idl/data/local/Migration1To2Test.kt`
- `master-plan.md`
- `docs/handoff/reports/2026-10-04-room-migration-f13.md`

## Commands

```
ANDROID_SERIAL=emulator-5554 scripts/check.sh --device
```

From Git Bash.

- JVM: 161 tests, 0 failed, 1 skipped
- lint: 0 errors, 40 warnings
- Device (Pixel 9 AVD, emulator-5554): 16 tests, 0 failed, 0 skipped
- All checks passed

## Deviations

None. The Charge tables have no SQL `DEFAULT` clauses. "Defaults" here means the tables start empty and the only nullable v2 column, `tetherActivatedEpochMs`, is null when the insert omits it.

## Known limitations

Schema 3 is not in this change. The next Room version still needs its own migration test.

## Commits

Recorded on `test/room-migration-f13`.
