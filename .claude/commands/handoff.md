---
description: Write HANDOFF.md (goal, done, in progress, next steps) and push it
---
Write or overwrite `HANDOFF.md` at the repo root so a fresh session can continue this work. Include:

- **Goal**: what the user asked for, in their words where possible
- **Done**: what is complete, with commit hashes (`git log --oneline` on this branch)
- **In progress**: what is half-done and its exact state (files, failing tests)
- **Next steps**: ordered, concrete actions
- **Key files**: the paths that matter for this task
- **Open questions / gotchas**: pending decisions, things that failed and why

Keep it under 60 lines. Then commit it (`docs: update handoff`) and push to the current branch.
Finally tell the user it's saved and suggest `/compact` or a fresh session that starts by reading `HANDOFF.md`.
$ARGUMENTS
