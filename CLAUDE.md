# CLAUDE.md

## Context 25% rule

When **25% or less of the context window remains** (i.e. ~75% used), stop starting new work and hand off:

1. Finish or safely pause the current step (don't leave files half-edited or tests broken).
2. Write/overwrite `HANDOFF.md` at the repo root with:
   - **Goal** – what the user asked for
   - **Done** – what is complete (with commit hashes)
   - **In progress** – what was being worked on and its exact state
   - **Next steps** – ordered, concrete actions
   - **Key files** – paths that matter for the task
   - **Open questions / gotchas** – decisions pending, things that failed
3. Commit and push `HANDOFF.md` to the current working branch.
4. Tell the user the handoff is saved and ask them to run `/compact` (or start a fresh session that begins by reading `HANDOFF.md`).

After compacting or in a new session, read `HANDOFF.md` first and continue from **Next steps**.

The hook in `.claude/hooks/context_guard.py` (wired up in `.claude/settings.json`) measures context usage after each prompt and tool call and reminds Claude when the threshold is crossed. Tune it with `CLAUDE_CONTEXT_WINDOW` (default 200000) and `CLAUDE_CONTEXT_REMAINING_PCT` (default 25).
