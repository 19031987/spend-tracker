---
description: Review this branch's changes against main for bugs before merging
---
Review the changes on the current branch compared with the default branch:

1. `git fetch origin` then `git diff --stat origin/HEAD...HEAD` to see what changed; read only the changed hunks (`git diff origin/HEAD...HEAD -- <file>`), not whole files.
2. Look for: correctness bugs, missed edge cases, error handling gaps, secrets or personal data in the diff, debug leftovers, and tests that should have been added or updated (see CLAUDE.md for this repo's test rules).
3. Run the fast checks listed under "Commands" in CLAUDE.md.

Report findings ranked by severity, each with `file:line`, what goes wrong and a suggested fix. Don't change code unless asked. $ARGUMENTS
