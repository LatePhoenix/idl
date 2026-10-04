# Render cache hygiene — checkpoint

Date: 2026-10-04. Branch: `fix/render-cache-f10` from `origin/main` (`5756cf5`).

## Acceptance

| Criterion | Status |
| --- | --- |
| Sign-out clears disk and memory | Met (`SessionRepository.signOut` → `RenderCache.clear`) |
| Files tagged by friend; purge deletes only that friend | Met (`purgeCachedUser` on remove, block, decline, revoked refresh, and friend-removed push) |
| LRU sized by `bitmap.byteCount`, about 8 MB | Met (`ByteLruCache`, `MEMORY_BUDGET_BYTES`) |
| Atomic write: temp file in the same directory, then rename | Met |
| Sign-out empties the cache | Met (`signOutEmptiesTheCache`) |
| Purging a friend deletes only that friend's files | Met (unit + `removingAFriendDeletesOnlyThatFriendsRenders`) |
| LRU evicts by bytes | Met |
| A partial write never leaves a readable corrupt file | Met (failed publish keeps the previous png and deletes the `.tmp`) |

## What changed

Friend renders are stored at `cacheDir/renders/<userId>/<renderKey>.png`. The self widget uses `_self`. `deleteOwner` drops that directory and the matching memory keys. `clear` drops the whole tree.

The memory cache is a byte-budget LRU, not `android.util.LruCache` counted by entries. Eviction order is least-recently accessed. The budget is 8 MB (D-27). A unit test drives the same class with integer sizes so the rule does not depend on a device bitmap.

`publish` writes `name.png.tmp` and renames it onto `name.png`. If the write throws, the temp file is deleted and the previous png stays.

## Files

- `app/src/main/java/app/idl/avatar/ByteLruCache.kt`
- `app/src/main/java/app/idl/avatar/RenderCache.kt`
- `app/src/main/java/app/idl/data/repo/Repositories.kt`
- `app/src/main/java/app/idl/data/push/PushHandler.kt`
- `app/src/main/java/app/idl/AppContainer.kt`
- `app/src/main/java/app/idl/widget/Widgets.kt`
- `app/src/test/java/app/idl/avatar/RenderCacheHygieneTest.kt`
- `app/src/androidTest/java/app/idl/avatar/RenderCacheRevocationTest.kt`
- `app/src/androidTest/java/app/idl/RenderingAndCacheTest.kt`
- `master-plan.md`
- `docs/handoff/reports/2026-10-04-render-cache-f10.md`

## Commands

```
ANDROID_SERIAL=emulator-5554 scripts/check.sh --device
```

From Git Bash. The emulator was already running on port 5554.

- JVM: 165 tests, 0 failed, 1 skipped
- lint: 0 errors, 39 warnings
- Device (Pixel 9 AVD, emulator-5554): 17 tests, 0 failed
- All checks passed

## Deviations

The memory cache is a small byte-budget LRU in `ByteLruCache` rather than `android.util.LruCache`. `android.util.LruCache` is not a real implementation on the JVM test classpath, and the eviction rule needed a unit test. Behavior matches the finding: size is `bitmap.byteCount`, budget is 8 MB, least-recently used entries go first.

## Known limitations

Disk trim is still a file-count cap (keep the newest 64 pngs once there are more than 80). It is not an 8 MB disk budget. D-27 names the 8 MB figure for the memory LRU.

## Commits

Recorded on `fix/render-cache-f10`.
