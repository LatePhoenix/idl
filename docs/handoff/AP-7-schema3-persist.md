# AP-7 design note — schema 3 saved everywhere

Branch `avatar/ap-7-schema3-persist`. Covers AP-7 (was AV.4): D-41, D-46, F-19, F-29.

## 1. Goal

Every saved avatar is schema 3 `AvatarConfiguration` JSON, on device and on the server. Every legacy base resolves to `base_teardrop`. The viewer still composes the face from a filtered presence view. Mood-derived expression is absent when mood is hidden.

## 2. Data and wire

**Room today.** Database version 2. `avatars.json` is a string. Existing rows are v1 `AvatarConfig` (`baseForm`, colors, enum accessories). `LegacyAvatarMigration` already maps those enums to asset ids, but `baseId` still sends blob, bot, ghost, critter, and orb to their procedural bases.

**Room after.** Version 3. A non-destructive migration reads each `avatars.json`. A v1 object (has `baseForm`) is converted with `LegacyAvatarMigration`, then every base id is `base_teardrop`. A schema 2 or 3 object is decoded with `AvatarConfiguration.decode` and the same base rewrite. The row is written back. No table is dropped. The exported schema test from F-13 covers the upgrade.

**Manifests.** `retired` maps `base_blob`, `base_bot`, `base_ghost`, `base_critter`, and `base_orb` to `base_teardrop`. Saved ids still resolve. The resolver already follows `retired`.

**Client save.** Load and save go through `decode` and `prepareForWrite`. `schemaVersion` greater than 3 returns `NeedsAppUpdate` and is not written back.

**Server today.** `avatar_configurations.config` is v1 JSON. `put_avatar` checks `baseForm` against `base_form`.

**Server after.** A new migration file (the init migration is not edited). `put_avatar` accepts schema 3: required `baseAssetId` and `paletteAssetId`, `schemaVersion` 3, and every referenced id must be in a server catalog generated from the shipped manifests. Unknown and retired ids are rejected. Free tier only; the entitlement hook is a no-op check that the id is in the free catalog. `get_avatar` returns the stored object or a schema 3 default (`base_teardrop`, `palette_sunny`). `presence_view` returns PresenceView v2: identity plus field-filtered semantics. `visual.expression` is set only when mood is visible (F-19).

**Contract.** Regenerate `contract/privacy_vectors.json` from Kotlin after the view changes. Add `contract/composition_vectors.json`: filtered view plus pack versions to resolved layer ids. A deliberate mood leak must fail those vectors. That check is recorded in the report.

## 3. Privacy

The server still filters. The client does not hide a field the server sent.

| Viewer | Mood | Expression in the view | What the client may draw |
| --- | --- | --- | --- |
| Owner | visible | the mood's catalog face | that face, including overlays |
| Friend, mood shared | visible | the same | that face |
| Friend, mood hidden | absent | absent | resting neutral eyes and mouth, no mood overlays |
| Stranger | absent | absent | the same as hidden mood |

Tests: the existing hidden-mood resolver test, a contract vector with mood stripped, and a composition vector that asserts no `overlay_*` layer when mood is absent. The mutation check flips mood back on in the SQL fixture and expects the vector diff to fail.

## 4. Test plan

| Acceptance | Test |
| --- | --- |
| v1 row becomes schema 3 teardrop | Room migration test against the exported v2 schema. Each `BaseForm` lands on `base_teardrop`. Hair, accessory, and color ids are kept when a mapping exists. |
| Newer schema is not rewritten | `prepareForWrite` on schema 4 returns `NeedsAppUpdate`. |
| Retired bases resolve | Registry test: each retired base id canonicalizes to `base_teardrop`. |
| Studio still saves | The old studio has no base picker. Save goes through `prepareForWrite`. |
| Server rejects unknown and retired ids | SQL tests for `put_avatar`. |
| `presence_view` v2 matches Kotlin | `privacy_vectors.json` regenerated and compared. |
| Composition matches | `composition_vectors.json`. |
| Demo friends are distinct teardrops | Fake world fixture: different hair, colors, and tops. |
| Widgets draw the vector base | Re-record widget goldens. Availability and activity glyphs stay. |
| Upgrade keeps the avatar | Manual: install the previous APK, save an avatar, install this build, screenshot. Recorded in the report. |

`check.ps1`, `--device`, and `--sql` before the last PR. CI `backend` must be green.

## 5. PR split

1. **Client.** Room migration, teardrop-only `LegacyAvatarMigration`, retired bases, studio base picker removed, fake world and demo looks, widget golden re-record. Device check.
2. **Server and contract.** New migration, `put_avatar` catalog check, PresenceView v2, both vector files, the mutation check. SQL check.

F-19 and F-29 are marked done in the last commit of PR 2.

## 6. Risks and rollback

- Rewriting Room JSON is one-way for the base id. Hair and color mappings are kept so the avatar is still recognizable. Rollback is a revert of the app build; the server migration is a new file, so revert that commit rather than editing it.
- Widget goldens will change because procedural bases become the teardrop. That is the point of D-46. Each changed golden is listed in the PR.
- The server catalog is generated. A hand-edited id list would drift. The check command rebuilds it and diffs, the same way the expression catalog does.
