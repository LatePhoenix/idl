# Cursor runbook: running the avatar program on your own

**For:** Cursor (agent mode), from the user via Claude, 2026-10-06. **Authority:** D-49.
You implement [`docs/avatar/AVATAR_PROGRAM.md`](../avatar/AVATAR_PROGRAM.md) from start to finish.
You open, review and merge your own pull requests, and you stop only for the reasons in §7.

This runbook is the *how*. The program is the *what*. `AGENTS.md` (invariants, commands,
conventions, report format) always applies.

---

## 1. Start of every session

Run this every time you start or resume, even mid-item:

```bash
git fetch --prune origin
git status                      # must be clean; if not, see §8
gh pr list --author "@me" --state open
```

1. **If one of your PRs is open,** finish it first (§5 and §6). Never have two of your PRs open at
   once, unless the status table says two items may run in parallel.
2. **If `main` is red** (`gh run list --branch main --limit 3`), fixing it is the next item
   (§6.4).
3. Otherwise read `master-plan.md` §0–§1, then the status table in `AVATAR_PROGRAM.md` §5. The
   next item is the first ⬜ row whose dependencies are all ✅. Skip rows marked 🎨: Claude makes
   them with the Art Studio (D-50). AP-16 waits until they're ✅.
4. Read that item's spec in full, plus every document it cites. Read the code it touches before
   you change it.

Long sessions lose context. Start a **new Cursor chat for each item** (or each PR of a multi-PR
item). Everything you need to resume is in the repo: the status table, the design note, the
checkpoint reports and the branch.

## 2. Branch

```bash
git switch main && git pull --ff-only
git switch -c avatar/ap-<n>-<slug>          # the name the spec gives
```

- Always branch from an up-to-date `main`. Never commit to `main` directly.
- For a multi-PR item, use one branch per PR: `avatar/ap-<n>-<slug>-a`, `-b`, and so on. Start
  each from `main` after the previous one merges.
- Set the row to 🔨 in the first commit of the item, in the same commit as the design note if
  there is one.

## 3. Work in small, verified steps

1. **Design note first** if the row says so (`AVATAR_PROGRAM.md` §6.0). Commit it on its own.
2. Implement in commits that each build and pass tests. One concern per commit. Aim for PRs under
   about 800 changed lines, excluding generated pictures, goldens and lock data. Split larger work
   into the PRs the spec lists.
3. **Before every commit:** `scripts/check.sh` (on Windows without bash on PATH:
   `scripts/check.ps1`). It must print "All checks passed". Never commit red. Never weaken,
   skip or delete a test to get to green, and never add `@Ignore` to a failing test.
4. **Also run, before opening the PR:**
   - `scripts/check.sh --device` (with `ANDROID_SERIAL=emulator-5554`) when the row has
     "Device? yes", or when the change touches `ui/`, `widget/`, `avatar/` rendering, themes or
     the manifest. If the emulator isn't running, start it (`emulator -avd Pixel_9 &`, then wait
     for boot). If you can't, that's a stop condition for that row (§7).
   - `scripts/check.sh --sql` when the row has "Server? yes", or when the change touches
     `supabase/`, `contract/`, or wire types. Docker Desktop must be running.
5. **Goldens.** Re-record only when pixels are meant to change
   (`./gradlew recordRoborazziDebug`, or `-Proborazzi.test.record=true` for one test class).
   Open and look at **every** changed image. List each changed golden with a one-line reason in
   the PR. When you delete a golden, also delete its copy under
   `app/build/intermediates/roborazzi/`, or recording copies it back.
6. **Commit messages:** a sentence-case summary line that says what changed and why, ending
   with a period (match `git log`), then a short body if useful. Reference ids: `AP-7`, `F-29`.
   No secrets, no `local.properties`, no build output.

## 4. Before opening the PR: self-review

Go through this list against the full diff (`git diff main...HEAD`). Fix anything that fails.

- [ ] The spec's acceptance list is met, item by item, or each gap is explained.
- [ ] Invariants 1–13 in `AGENTS.md` hold. Pay special attention to privacy (no mood-derived
      layer without mood), determinism (no unseeded random, no map-order output, no
      `Instant.now()` in `domain/`), "saved avatars never break" (retired mappings, migrations with
      tests) and "domain stays Android-free".
- [ ] No recorded decision is contradicted, and no new decision was made silently.
- [ ] No new dependency (§7).
- [ ] No merged migration file was edited. New SQL goes in a new migration.
- [ ] `contract/privacy_vectors.json` changed only through `-Pidl.updateGolden=true`, and the SQL
      matches it.
- [ ] New code has tests. Every bug fix has a regression test.
- [ ] Every changed golden was reviewed and is listed.
- [ ] Docs touched by the change are updated (`ARCHITECTURE.md`, `ASSET_SPEC.md`,
      `AVATAR_RECIPE_SCHEMA.md`, `AUTHORING.md`, the style guide if a rule changed).
- [ ] The checkpoint report is written: `docs/handoff/reports/<YYYY-MM-DD>-ap-<n>-<slug>.md`, in
      the `AGENTS.md` format, with the real commands and results.
- [ ] `AVATAR_PROGRAM.md` §5 status and the `master-plan.md` §8 work-log line are updated (set ✅
      in the last PR of the item).
- [ ] Content PRs: user review queue rows added (`master-plan.md` §4.1).

## 5. Open the PR

```bash
git push -u origin HEAD
gh pr create --base main --title "<summary>" --body-file <prepared-body.md>
```

The PR body follows `.github/pull_request_template.md`: the summary, the spec item, the checks
run with their results, the changed goldens, screenshots or contact sheets for anything visual
(link the committed files with `?raw=true`), deviations, and the report path.

## 6. CI, review, merge

### 6.1 Wait for CI

```bash
gh pr checks <n> --watch --interval 30
```

- **Real failure:** `gh run view <run-id> --log-failed`. Fix it on the branch, run `check.sh`
  again, push. For snapshot diffs, download the `roborazzi-diffs` artifact
  (`gh run download <run-id> -n roborazzi-diffs`) and look at it.
- **Infrastructure failure** (a job cancelled with no steps, "not acquired by Runner", network
  errors in setup): `gh run rerun <run-id>`. Check `https://www.githubstatus.com` if it happens
  twice. After 3 reruns over at least an hour, stop (§7).

### 6.2 Review comments

CodeRabbit reviews PRs automatically.

```bash
gh pr view <n> --comments
gh api repos/{owner}/{repo}/pulls/<n>/comments --jq '.[] | {path, line, body}'
```

Handle every comment: fix it (and push), or reply on the thread with the reason it doesn't apply.
A comment that asks for a design or decision change is a stop condition (§7). Wait for re-review
if the bot re-runs. A "rate limited" review state isn't a blocker.

### 6.3 Merge

Merge only when **all** of these hold: your last local `check.sh` passed, every CI check is
green, the self-review (§4) is complete, and every review comment is handled.

```bash
gh pr merge <n> --merge --delete-branch
git switch main && git pull --ff-only
git branch -d <branch>
```

- Use merge commits (`--merge`), as the repo always has. Don't squash or rebase-merge.
- At the end of a whole AP item (its last PR), tag it:
  `git tag -a avatar-ap-<n> -m "AP-<n> <title>" && git push origin avatar-ap-<n>`.

### 6.4 After merge: watch `main`

`gh run list --branch main --limit 1`, then `gh run watch <id>`. If `main` turns red, the
next item is a fix on `fix/main-<slug>`, same process, before anything else. If a merged change has
to be backed out, use `git revert -m 1 <merge-sha>` on a branch with a PR. **Never** reset or
force-push `main`.

## 7. Stop conditions

Stop work on the current row and do the steps below if any of these happens:

1. The spec needs a decision that isn't recorded, or a change contradicts a recorded decision,
   an invariant or this runbook.
2. A new dependency (Gradle, npm or pip) seems necessary.
3. A destructive data change seems necessary (dropping data, a destructive Room migration, editing
   a merged SQL migration).
4. A test or CI failure you can't fix after three honest attempts, or infrastructure failing
   past §6.1.
5. A device-required or SQL-required check can't run (no emulator, no Docker).
6. A review comment asks for a design or decision change.
7. Anything that touches credentials, real Supabase or Firebase projects, billing, or
   publishing.

**How to stop:**
1. Commit and push the work in progress on its branch (it's fine for the work to be incomplete).
   If a PR is open, mark it draft: `gh pr ready <n> --undo`.
2. Write `docs/handoff/reports/<date>-BLOCKED-ap-<n>.md`: what you were doing, the exact error or
   question, the options with a recommendation, and what you'd do next.
3. Add the question to `master-plan.md` §6 "Open", and set the row to ⏸ in `AVATAR_PROGRAM.md` §5.
   Commit both on a `docs/blocked-ap-<n>` branch and merge it through the normal process (docs only).
4. If another ⬜ row has its dependencies met and doesn't depend on the blocked one, continue
   with it. Otherwise stop and summarize for the user in the chat.

## 8. Recovering from a messy state

- **Uncommitted changes you didn't make:** don't discard them. Run `git stash push -u -m
  "found at session start"`, note it in the report, and continue. Tell the user.
- **Your branch is behind `main`:** `git merge origin/main` on the branch (don't rebase a
  pushed branch), resolve conflicts, run `check.sh`, push.
- **`master-plan.md` conflicts** are usually two appended work-log lines. Keep both, in date
  order.
- **Gradle file locks on Windows:** `./gradlew --stop` before `clean`.

## 9. Things you never do

- Push to `main`, force-push any shared branch, or delete remote branches other than your own
  merged ones.
- Merge with a red or pending check, or with an unhandled review comment.
- Disable, skip or loosen tests, CI steps, lint or the validator to get to green.
- Add dependencies, edit merged migrations, or hand-edit `contract/privacy_vectors.json`.
- Change a recorded decision, the invariants, or this runbook. Propose changes in a report
  instead.
- Use third-party emoji art, trace it, or add brand logos or trademarks (D-42, style guide §6).
- Log note text, names or avatar content.

## 10. End of the program

When every row in `AVATAR_PROGRAM.md` §5 is ✅, do AP-16's final report, then tell the user in the
chat: what shipped, inventory counts, the review queue, and anything parked.
