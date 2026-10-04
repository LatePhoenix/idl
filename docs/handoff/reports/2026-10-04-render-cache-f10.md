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
| Memory get/put/remove is safe when widgets render and purges run together | Met (`ByteLruCache` lock; concurrent JVM test) |
| Sign-out file deletion is off the main thread | Met (`renders.clear()` inside `withContext(Dispatchers.IO)`) |
| A render that finishes after its owner was purged leaves no file | Met (owner generation checked before the staged file is renamed) |

## What changed

Friend renders are stored at `cacheDir/renders/<userId>/<renderKey>.png`. The self widget uses `_self`. `deleteOwner` drops that directory and the matching memory keys. `clear` drops the whole tree.

The memory cache is a byte-budget LRU, not `android.util.LruCache` counted by entries. Eviction order is least-recently accessed. The budget is 8 MB (D-27). A unit test drives the same class with integer sizes so the rule does not depend on a device bitmap.

`publish` writes a temp file in the same directory and renames it onto `name.png`. If the write throws, the temp file is deleted and the previous png stays.

`ByteLruCache` locks get, put, remove, removePrefixed, and evictAll. `deleteOwner` and `clear` bump a generation for that owner, or for the whole cache, before they delete files. `commit` stages the new bytes, then renames only if that generation is still current. Otherwise it deletes the staged file. `SessionRepository.signOut` runs `renders.clear()` on `Dispatchers.IO`.

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

- JVM: 167 tests, 0 failed, 1 skipped
- lint: 0 errors, 39 warnings
- Device (Pixel 9 AVD, emulator-5554): 17 tests, 0 failed
- All checks passed

The follow-up (thread safety, IO sign-out clear, purge generation) was checked the same way. `ANDROID_SERIAL=emulator-5554` kept the run off the attached physical Pixel.

## Deviations

The memory cache is a small byte-budget LRU in `ByteLruCache` rather than `android.util.LruCache`. `android.util.LruCache` is not a real implementation on the JVM test classpath, and the eviction rule needed a unit test. Behavior matches the finding: size is `bitmap.byteCount`, budget is 8 MB, least-recently used entries go first.

This branch was not rebased onto `main`. PRs #10, #11, #13, #15, and #16 were still open, so `main` does not contain them yet.

## Known limitations

Disk trim is still a file-count cap (keep the newest 64 pngs once there are more than 80). It is not an 8 MB disk budget. D-27 names the 8 MB figure for the memory LRU.

## Commits

- `08be0d3` Clear friend renders on sign-out and purge, and cache them by bytes.
- Follow-up: lock the memory cache, drop a file when its owner was purged mid-render, and clear files on `Dispatchers.IO`.
- PR: https://github.com/LatePhoenix/idl/pull/14
