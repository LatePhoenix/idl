---
name: art-studio
description: >
  Use when creating or editing iDL avatar art with the Art Studio (drafts, kits, guides,
  geom helpers, lint, PNG sheets). Trigger for beanie/hair/hat/glasses prompts, tools/studio/,
  art/, or STUDIO_AGENT_GUIDE / ART_STUDIO references. Not for app Kotlin or shipping promote
  until ST-3.
---

# Art Studio

Follow [`docs/avatar/STUDIO_AGENT_GUIDE.md`](../../../docs/avatar/STUDIO_AGENT_GUIDE.md) end to end.

Quick path:

1. `python tools/studio/studio.py setup` once if Skia commands fail.
2. `kit <category>` + `guides` → plan parts from the skeleton.
3. `draft new <id> --category … --prompt "…"` then build paths with `geom` (`@guide:…`, `@part:…`).
4. `draft write <id> file.svg` → lint + sheet under `.studio/renders/`.
5. Fix errors; `draft revert` if needed. Copy sheets into `docs/handoff/sheets/` for the PR.
6. Content rules: style guide §6 — no brands, logos or text.

Do not edit shipped `art/` until `promote` (ST-3). Do not add Skia to app/CI deps.
