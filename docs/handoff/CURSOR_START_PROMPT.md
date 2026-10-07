# Cursor kickoff prompts

Paste one of these into a **new Cursor agent chat** opened on this repo.

## Review the repo, then run the program to the end (2026-10-07)

```text
You're running the iDL avatar program on your own (decision D-49). The plan was re-ordered on
2026-10-07: the status table in docs/avatar/AVATAR_PROGRAM.md §5 is now in execution order and
includes the Art Studio (ST rows) and the art-direction rows (AD rows), specified in
docs/handoff/STUDIO_TASK.md.

1. Read AGENTS.md, docs/handoff/CURSOR_RUNBOOK.md, docs/avatar/AVATAR_PROGRAM.md (§0–§5),
   docs/handoff/STUDIO_TASK.md, docs/avatar/ART_STUDIO.md and docs/avatar/ART_STYLE_GUIDE.md.
2. Review where things stand: master-plan.md §1, §4.0, the open findings (F-32..F-35), the §6 open
   questions, and the three newest reports in docs/handoff/reports/. If a doc contradicts the
   code or the status table, fix the doc first in a small docs PR.
3. Do the runbook's §1 session-start steps.
4. Take the first ⬜ row whose dependencies are ✅ and complete it end to end exactly as the
   runbook says, then merge your own PR when everything is green. Keep going row after row.
5. Never do a 👤 row. Stop only for a runbook §7 condition, or when every remaining row depends
   on AD-2 (the user's art-direction pick). Then tell me exactly what to decide and where to look.

Don't ask me for confirmation between items.
```

## Start or continue the avatar program (original)

```text
You're running the iDL avatar program on your own (decision D-49).

1. Read AGENTS.md, docs/handoff/CURSOR_RUNBOOK.md and docs/avatar/AVATAR_PROGRAM.md in full,
   then docs/avatar/ART_STYLE_GUIDE.md.
2. Do the runbook's §1 session-start steps.
3. Take the next item from the program's §5 status table and complete it end to end exactly
   as the runbook says: branch, design note if required, small commits with scripts/check.sh
   passing before each one, the extra --device/--sql checks when required, self-review,
   checkpoint report, PR, CI, review comments, then merge your own PR when everything is green.
4. Then continue with the next item. Keep going until the program is done or a runbook §7 stop
   condition happens. If you stop, follow §7 and tell me exactly what you need.

Don't ask me for confirmation between items. Do stop for anything in runbook §7.
```

## Resume after a stop or a new chat

```text
Continue the iDL avatar program. Do docs/handoff/CURSOR_RUNBOOK.md §1 first (including any
open PR of yours and the state of main), read the latest report in docs/handoff/reports/,
then carry on from the status table in docs/avatar/AVATAR_PROGRAM.md §5 under the runbook.
```

## Address the user's art feedback

```text
Read the "User review queue" in master-plan.md §4.1. For every row with a verdict that asks for
changes, create a fix item: a branch avatar/review-<date>-<slug>, the change, re-recorded and
reviewed contact sheets, then the normal runbook PR flow. Update the verdict cell to "fixed in
PR #n". Then resume the program.
```
