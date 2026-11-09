#!/usr/bin/env python3
"""Stop hook: run the repo's fast checks before Claude ends its turn.

Checks are listed in .claude/hooks/stop_checks.json:
  {"checks": [{"name": "...", "when": "<regex on changed paths>", "run": "<shell cmd>",
               "timeout": 120}],
   "related_tests": {"dir": "tests", "cmd": "python -m pytest -q -x"}}

Changed .py/.js/.json files are always syntax-checked. A check runs only if a file changed on this branch (uncommitted, untracked, or
committed since the default branch) matches its "when" regex. "related_tests"
runs tests/test_*<module>*.py for each changed Python module.

Results are cached per working-tree state, so an unchanged tree isn't re-tested.
On failure Claude is told to fix it (exit 2), once; if it's already continuing
because of this hook, failures are only reported, to avoid loops.
"""
import glob
import hashlib
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import tempfile

ROOT = os.environ.get("CLAUDE_PROJECT_DIR") or os.getcwd()
CONFIG = os.path.join(ROOT, ".claude", "hooks", "stop_checks.json")


def git(*args):
    p = subprocess.run(["git", "-C", ROOT, *args], capture_output=True, text=True)
    return p.stdout if p.returncode == 0 else ""


def changed_files():
    files = set()
    for line in git("status", "--porcelain").splitlines():
        files.add(line[3:].split(" -> ")[-1].strip('"'))
    base = ""
    for ref in ("origin/HEAD", "origin/main", "origin/master"):
        base = git("merge-base", "HEAD", ref).strip()
        if base:
            break
    if base:
        files.update(git("diff", "--name-only", base, "HEAD").splitlines())
    return sorted(f for f in files if f)


def tree_state(files):
    h = hashlib.sha1(git("rev-parse", "HEAD").encode())
    h.update(git("diff", "HEAD").encode())
    for f in files:
        try:
            h.update(f.encode() + str(os.path.getmtime(os.path.join(ROOT, f))).encode())
        except OSError:
            pass
    return h.hexdigest()


def run(name, cmd, timeout):
    try:
        p = subprocess.run(cmd, shell=True, cwd=ROOT, capture_output=True, text=True,
                           timeout=timeout)
    except subprocess.TimeoutExpired:
        return f"[{name}] timed out after {timeout}s: {cmd}"
    if p.returncode != 0:
        out = (p.stdout + p.stderr).strip().splitlines()
        return f"[{name}] FAILED: {cmd}\n" + "\n".join(out[-40:])
    return None


def main():
    try:
        data = json.load(sys.stdin)
    except ValueError:
        data = {}
    if not os.path.exists(CONFIG):
        return 0
    with open(CONFIG, encoding="utf-8") as fh:
        cfg = json.load(fh)

    files = changed_files()
    if not files:
        return 0
    marker = os.path.join(tempfile.gettempdir(),
                          "claude-stopchecks-" + hashlib.sha1(ROOT.encode()).hexdigest()[:10])
    state = tree_state(files)
    try:
        if open(marker).read() == state:
            return 0
    except OSError:
        pass

    jobs = []
    # Always: syntax-check changed Python / JS / JSON files (same rules as post_edit_check).
    existing = [f for f in files if os.path.isfile(os.path.join(ROOT, f))]
    py = [f for f in existing if f.endswith(".py")]
    if py:
        if shutil.which("ruff"):
            jobs.append(("syntax", "ruff check --quiet --no-cache --isolated --select "
                         "E9,F63,F7,F82 --output-format concise " + " ".join(map(shlex.quote, py)), 60))
        else:
            jobs.append(("syntax", f"{shlex.quote(sys.executable)} -m py_compile "
                         + " ".join(map(shlex.quote, py)), 60))
    if shutil.which("node"):
        for f in existing:
            if f.endswith((".js", ".mjs", ".cjs")):
                jobs.append(("syntax", "node --check " + shlex.quote(f), 30))
    for f in existing:
        if f.endswith(".json"):
            jobs.append(("json", f"{shlex.quote(sys.executable)} -c 'import json,sys; "
                         f"json.load(open(sys.argv[1]))' " + shlex.quote(f), 30))

    for check in cfg.get("checks", []):
        if any(re.search(check["when"], f) for f in files):
            jobs.append((check["name"], check["run"], check.get("timeout", 120)))

    rel = cfg.get("related_tests")
    if rel:
        tests = set()
        for f in files:
            if not f.endswith(".py") or not os.path.exists(os.path.join(ROOT, f)):
                continue
            stem = os.path.splitext(os.path.basename(f))[0]
            if f.startswith(rel["dir"] + "/") and stem.startswith("test_"):
                tests.add(f)
            elif not stem.startswith("test_"):
                for t in glob.glob(os.path.join(ROOT, rel["dir"], f"test_*{stem}*.py")):
                    tests.add(os.path.relpath(t, ROOT))
        if tests:
            jobs.append(("related tests", rel["cmd"] + " " + " ".join(sorted(tests)),
                         rel.get("timeout", 180)))

    failures = [r for r in (run(*j) for j in jobs) if r]
    if not failures:
        with open(marker, "w") as fh:
            fh.write(state)
        return 0

    report = "\n\n".join(failures)
    if data.get("stop_hook_active"):
        print(json.dumps({"systemMessage": "Stop checks still failing:\n" + report[:2000]}))
        return 0
    sys.stderr.write("Fast checks failed on files changed in this branch. Fix them (or "
                     "explain to the user why they fail) before finishing:\n\n" + report + "\n")
    return 2


if __name__ == "__main__":
    sys.exit(main())
