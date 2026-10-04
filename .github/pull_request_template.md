## Checklist

- [ ] Invariants in `AGENTS.md` checked (privacy stays server-side, hidden state does not leak into the render, expiry uses `IdlClock`, saved avatars still resolve, domain stays Android-free, the render is deterministic)
- [ ] `scripts/check.sh` run
- [ ] Visual or widget changes include an updated snapshot (`app/src/test/snapshots`) or a device screenshot
- [ ] `master-plan.md` and the checkpoint report (`docs/handoff/reports/`) updated when the task finishes
