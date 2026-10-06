# Cursor kickoff prompts

Paste one of these into a **new Cursor agent chat** opened on this repo.

## Start or continue the avatar program

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
