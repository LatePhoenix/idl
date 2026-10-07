# 2026-10-06 — AP-6 expression system

Branch `avatar/ap-6-expressions-b1` from `origin/main` (`1c55d90`, merge of PR #39). This is pull request (a), the system. Art for the mood faces is pull request (b). The row stays 🔨.

## Acceptance

| Criterion | Result |
| --- | --- |
| Catalog resolution | Met. `ExpressionCatalog` reads the AP-5 JSON. Each `Mood` has one priority-1 id. Each `Expression` wire name aliases a catalog id. |
| Resolver uses a catalog face when the pack defines it | Met. A visible mood selects that id and its eyes and mouth. |
| Hidden mood stays neutral, with no catalog overlays | Met. `VisiblePresence.NONE` keeps `neutral` and does not draw the mood face. |
| Shared part art, batch 1, retirement, contact sheet, 48 px distinctness | Not this pull request. Those are (b). |

## Commands

```
scripts/check.ps1
```

Picture pipeline: **8 tests, OK**. Gradle: **250 tests, 0 failed, 1 skipped**. Lint **0 errors, 42 warnings**.

`--device` is not required for this pull request: no production render path loads the catalog yet, so pixels do not change. `--sql` is not required.

## Deviations

- Production `AvatarResolver` call sites still use an empty catalog. Passing the real catalog before the pack defines the new expression ids would record those ids as unknown drops. Pull request (b) registers the faces and passes the catalog in.

## Changed goldens

None.
