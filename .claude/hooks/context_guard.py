#!/usr/bin/env python3
"""Context guard hook for Claude Code.

Runs on UserPromptSubmit and PostToolUse. Reads the session transcript, works
out how much of the context window the latest turn used, and once the
remaining context drops to the threshold (default 25%) injects an instruction
telling Claude to write a handoff note and ask for /compact.

It fires once per session (per threshold crossing) so it doesn't nag.

Tunables (environment variables):
  CLAUDE_CONTEXT_WINDOW            context window size in tokens (default 200000)
  CLAUDE_CONTEXT_REMAINING_PCT     remaining-% threshold that triggers (default 25)
"""
import json
import os
import sys
import tempfile

WINDOW = int(os.environ.get("CLAUDE_CONTEXT_WINDOW", "200000"))
THRESHOLD = float(os.environ.get("CLAUDE_CONTEXT_REMAINING_PCT", "25"))


def last_context_tokens(transcript_path):
    """Tokens in context as of the most recent main-chain assistant message."""
    tokens = None
    try:
        with open(transcript_path, encoding="utf-8") as fh:
            for line in fh:
                try:
                    entry = json.loads(line)
                except ValueError:
                    continue
                if entry.get("type") != "assistant" or entry.get("isSidechain"):
                    continue
                usage = (entry.get("message") or {}).get("usage") or {}
                total = (
                    usage.get("input_tokens", 0)
                    + usage.get("cache_creation_input_tokens", 0)
                    + usage.get("cache_read_input_tokens", 0)
                    + usage.get("output_tokens", 0)
                )
                if total:
                    tokens = total
    except OSError:
        return None
    return tokens


def main():
    try:
        data = json.load(sys.stdin)
    except ValueError:
        return 0
    event = data.get("hook_event_name", "PostToolUse")
    session = data.get("session_id", "unknown")
    transcript = data.get("transcript_path")
    if not transcript:
        return 0

    used = last_context_tokens(transcript)
    if not used:
        return 0
    remaining_pct = max(0.0, 100.0 * (WINDOW - used) / WINDOW)

    marker = os.path.join(tempfile.gettempdir(), f"claude-context-guard-{session}")
    if remaining_pct > THRESHOLD:
        # Back above the threshold (e.g. after /compact): re-arm.
        if os.path.exists(marker):
            os.remove(marker)
        return 0
    if os.path.exists(marker):
        return 0
    open(marker, "w").close()

    message = (
        f"CONTEXT RULE: only ~{remaining_pct:.0f}% of the context window remains "
        f"(~{used:,} of {WINDOW:,} tokens used). Follow the 'Context 25% rule' in "
        "CLAUDE.md now: finish or pause the current step, write HANDOFF.md "
        "(goal, done, in progress, next steps, key files, open questions), commit "
        "and push it to the working branch, then ask the user to run /compact "
        "(or start a fresh session that reads HANDOFF.md)."
    )
    print(json.dumps({
        "systemMessage": message,
        "hookSpecificOutput": {
            "hookEventName": event,
            "additionalContext": message,
        },
    }))
    return 0


if __name__ == "__main__":
    sys.exit(main())
