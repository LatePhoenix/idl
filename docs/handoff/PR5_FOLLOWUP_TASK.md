# PR #5 follow-up task: finish F-02, fix F-21 and F-22

**Branch:** `fix/widget-presence-f01-f03` (PR #5). Add commits to this branch; don't open a new PR.
**Source:** Claude's review of PR #5 (2026-10-04). Findings are in `master-plan.md` §3
(F-02 "Still broken", F-21, F-22); the rule is in `docs/IDL_DECISIONS.md` D-31 (clarified).

## 0. Merge `main` first

`main` has moved (PR #6 added feature ideas, track U and decisions D-35…D-38). Run
`git fetch origin && git merge origin/main`, then resolve the conflicts:

- `docs/IDL_DECISIONS.md`: keep **both** sets of entries. D-31 (the clarified text on this branch)
  sits before D-32; D-35…D-38 from `main` stay where they are.
- `master-plan.md`: keep everything from `main` (§6 answers, §7.1, the U track, the D-35…D-38
  bullets, work-log rows) **and** this branch's Track 0 statuses, F-01…F-03 notes, F-21/F-22 and
  work-log rows. Work-log rows stay in date order.

## 1. F-02: explicit status accessories must beat signatures for every viewer

**Problem:** with default privacy rules, a friend who isn't on the owner's close list receives
`headAccessoryAssetId = head_vr_headset` but **no `activityType`**. The headset therefore stays
`CONTEXT` and loses to the signature glasses (`SIGNATURE`).

**Change (`domain/avatar/AvatarResolver.kt`):**

1. Add `LayerPriority.STATUS` **between `ACTIVITY` and `SIGNATURE`**:
   `BASE, FACE, AVAILABILITY, EXPRESSION, ACTIVITY, STATUS, SIGNATURE, CONTEXT, SCENE, DECORATION`.
   Check every comparison and `when` over `LayerPriority` (resolver, `CompatibilityEngine`,
   tests) still means what it did.
2. In the head and body slots, sources become:
   - `VisiblePresence.headAccessoryAssetId` / `bodyAccessoryAssetId` → **STATUS** (was CONTEXT)
   - overrides and semantics from `activity:*` keys → ACTIVITY (unchanged)
   - overrides and semantics from other keys (mood, intent, availability) → **STATUS**
   - signature → SIGNATURE (unchanged)
3. Keep the D-31 "highest priority among sources naming the same asset" rule.
4. Don't change props, scenes or overlays (they have no conflicts with signatures in
   `core_proto`). Mention in the KDoc that STATUS is for accessories.

**Tests (JVM, `AvatarResolverTest`):**

- **Non-close friend:** signature glasses + "In VR" quick state → `PrivacyFilter.viewFor(…,
  Relationship(isFriend = true, isCloseFriend = false), PrivacyRules.DEFAULT, …)` →
  `LegacyAvatarMigration.presence(view)` → resolve at `STANDARD_WIDGET`. Expect the headset
  present at STATUS, glasses dropped with `CONFLICT`, and `activityType` null (proving nothing
  leaked).
- The same for a close friend: the headset is ACTIVITY (raised by `activity:vr`).
- A `mood:*` override head accessory that conflicts with a signature wins (STATUS) only while
  that mood is visible; with mood hidden, the signature shows. This keeps invariant 2.
- When the status ends, the signature returns and the saved configuration is unchanged.

## 2. F-21: choose the widget target from the avatar's drawn size

**Problem:** `WidgetRenderInputs.targetFor(110, 110)` returns `COMPACT_WIDGET`, but 110×110 dp
is the **default 2×2** widget (`minWidth/minHeight = 110dp`, `targetCell 2×2`). The compact
policy drops body accessories and, when there's a badge, the prop.

**Change:** map Glance sizes by the avatar image they draw (`WidgetBody`: 76 dp in SMALL, 92 dp
in WIDE):

| Glance size | Avatar drawn | Target |
| --- | --- | --- |
| SMALL 110×110 | 76 dp | `STANDARD_WIDGET` |
| WIDE 250×110 | 92 dp | `STANDARD_WIDGET` |
| SQUARE 180×180 | ≥ 92 dp | `LARGE_WIDGET` |

Keep `COMPACT_WIDGET` for a future 1×1 widget and dense circle widgets. Rename
`widget-1x1-busy.png` to `widget-2x2-default-busy.png` and re-take it.

**Tests:** a `targetFor` test for each declared size; a resolver test showing the "Sleepy" quick
state keeps `body_blanket` at the default widget target.

## 3. F-22: keep render failures inside the fallback

In `widget/Widgets.kt render()`, move `context.container.assetRegistry` and
`WidgetRenderInputs.from(…)` inside the `runCatching`, so any failure logs `widget.render_failed`
and returns `model to null` (the designed fallback). Add a JVM test that a throwing input
produces `model to null` (extract a small testable function if needed).

## 4. Device verification (required, CI has no device tests)

On `emulator-5554` with the fake backend:

1. Pin a friend widget for **Mo**. In the demo world Mo wears glasses and has **not** put you
   on their close list, which is exactly the failing case.
2. Settings → Alpha/debug → Demo friend **Mo** → simulate **In VR** → the widget shows the
   **headset** (not glasses) plus the VR and availability glyphs. Screenshot:
   `widget-vr-over-glasses-nonclose.png`.
3. Simulate **Sleepy** for Ari at the default widget size → the blanket and tea are visible.
   Screenshot: `widget-2x2-default-sleepy.png`.
4. Run `scripts/check.sh` and `ANDROID_SERIAL=emulator-5554 scripts/check.sh --device`.

## 5. Done means

- [ ] `origin/main` merged; conflicts resolved as in §0
- [ ] F-02 non-close-friend path fixed, with tests as in §1
- [ ] F-21 fixed, with tests and screenshots as in §2 and §4
- [ ] F-22 fixed, with a test as in §3
- [ ] `master-plan.md`: F-02, F-21, F-22 and Track 0.2/0.9 set to ✅ with one-line "Fixed:"
      notes; the §1.3 widgets row updated; a work-log row added
- [ ] The checkpoint report updated (`docs/handoff/reports/2026-10-04-track-0-widget.md`),
      including commands, counts and screenshots
- [ ] Everything pushed to PR #5
