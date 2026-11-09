#!/usr/bin/env python3
"""PostToolUse hook: fast syntax check of the file Claude just edited.

Only reports real breakage (syntax errors, undefined names); it never
reformats files, so diffs stay small.
  .py   -> ruff (fatal rules only) if installed, else py_compile
  .js   -> node --check
  .json -> json parse
Exit code 2 sends the error back to Claude so it fixes it straight away.
"""
import json
import os
import shutil
import subprocess
import sys


def run(cmd):
    try:
        p = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    except (OSError, subprocess.TimeoutExpired):
        return None
    return p.returncode, (p.stdout + p.stderr).strip()


def check(path):
    ext = os.path.splitext(path)[1].lower()
    if ext == ".py":
        if shutil.which("ruff"):
            res = run(["ruff", "check", "--quiet", "--no-cache", "--isolated",
                       "--select", "E9,F63,F7,F82", "--output-format", "concise", path])
        else:
            res = run([sys.executable, "-m", "py_compile", path])
    elif ext in (".js", ".mjs", ".cjs") and shutil.which("node"):
        res = run(["node", "--check", path])
    elif ext == ".json":
        try:
            with open(path, encoding="utf-8") as fh:
                json.load(fh)
            return None
        except ValueError as exc:
            return f"Invalid JSON in {path}: {exc}"
        except OSError:
            return None
    else:
        return None
    if res and res[0] != 0:
        return res[1][-3000:] or f"Check failed for {path}"
    return None


def main():
    try:
        data = json.load(sys.stdin)
    except ValueError:
        return 0
    path = (data.get("tool_input") or {}).get("file_path") or ""
    if not path or not os.path.isfile(path):
        return 0
    problem = check(path)
    if problem:
        sys.stderr.write(f"Post-edit check failed - fix this before continuing:\n{problem}\n")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main())
