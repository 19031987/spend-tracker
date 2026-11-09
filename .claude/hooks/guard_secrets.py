#!/usr/bin/env python3
"""PreToolUse hook: keep secrets out of Claude's context and out of edits.

Blocks Read/Edit/Write/MultiEdit/NotebookEdit on secret files, and Bash commands
that name them. Example/template files (.env.example, *.example, .env.development
when it is committed with fake values) are allowed.

Exit code 2 blocks the tool call and shows the reason to Claude.
"""
import json
import os
import re
import sys

# Basenames that are always secret.
SECRET_NAMES = {
    ".env", "secrets.env", "keystore.properties", "credentials.json",
    "service-account.json", "client_secret.json", ".netrc", ".pypirc",
}
SECRET_SUFFIXES = (".jks", ".keystore", ".p12", ".pfx", ".pem", ".key")
ALLOWED_SUFFIXES = (".example", ".sample", ".template", ".development", ".dist")


def is_secret(path):
    name = os.path.basename(path.strip().strip("'\""))
    if not name or name.endswith(ALLOWED_SUFFIXES):
        return False
    if name in SECRET_NAMES or name.endswith(SECRET_SUFFIXES):
        return True
    if name.startswith(".env.") or name.startswith("secrets."):
        return True
    if re.match(r"(service[-_]account|play[-_].*service).*\.json$", name, re.I):
        return True
    return False


def main():
    try:
        data = json.load(sys.stdin)
    except ValueError:
        return 0
    tool = data.get("tool_name", "")
    params = data.get("tool_input") or {}

    hits = []
    if tool == "Bash":
        cmd = params.get("command", "")
        for token in re.split(r"[\s;|&<>()=`]+", cmd):
            if token and is_secret(token):
                hits.append(token)
    else:
        for key in ("file_path", "notebook_path", "path"):
            if params.get(key) and is_secret(params[key]):
                hits.append(params[key])

    if hits:
        sys.stderr.write(
            f"Blocked by secrets guard: {', '.join(sorted(set(hits)))} looks like a secret "
            "file. Don't read, print or edit secrets. Use the matching *.example file, "
            "or ask the user to change the value themselves.\n"
        )
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
