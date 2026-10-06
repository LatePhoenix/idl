## Summary

<!-- What changed and why, in two or three sentences. -->

**Spec item:** <!-- e.g. AP-3 (docs/avatar/AVATAR_PROGRAM.md §6), or F-31 -->
**PR:** <!-- e.g. 1 of 2 (domain and renderer) -->

## Checks run

<!-- Paste the real results. -->
- `scripts/check.sh`: <!-- N tests, 0 failed, N skipped; lint 0 errors / N warnings -->
- `scripts/check.sh --device`: <!-- result, or "not applicable: <reason>" -->
- `scripts/check.sh --sql`: <!-- result, or "not applicable: <reason>" -->
- `python tools/asset_pipeline.py check`: <!-- once AP-4 has landed -->

## Changed goldens

<!-- Each changed or new golden with a one-line reason, or "none". -->

## Screenshots and contact sheets

<!-- Required for anything visual. Link committed files with ?raw=true. -->

## Deviations and choices

<!-- Anything the spec didn't decide that you decided, and anything not met. -->

## Self-review (docs/handoff/CURSOR_RUNBOOK.md §4)

- [ ] Acceptance list met, or gaps explained
- [ ] Invariants hold (privacy stays server-side, hidden state doesn't leak into the render, expiry uses `IdlClock`, saved avatars still resolve, domain stays Android-free, deterministic render)
- [ ] No decision contradicted, no new dependency, no merged migration edited
- [ ] Tests added; every bug fix has a regression test
- [ ] Every changed golden reviewed and listed
- [ ] Docs, status table, work log and checkpoint report updated

**Report:** `docs/handoff/reports/<date>-<item>.md`
