# CLAUDE.md

**Monarch Spend (SpendTracker)**: Android app (Kotlin, Jetpack Compose, SQLite/Room) that reads bank notifications and tracks money in/out. Package `com.spendtracker.app`. Current version is in `app/build.gradle.kts` (`versionCode` / `versionName`).

## Commands

```bash
python -m pytest -q test_spend_parser.py test_bank_deduplication.py test_categorization_engine.py  # fast (<1s), works anywhere
./gradlew :app:testDebugUnitTest      # Kotlin unit tests (needs JDK 17 + Android SDK, API 36)
./gradlew :app:assembleDebug          # debug APK
```
No Android SDK in cloud sessions: rely on the Python mirror tests there and let CI (`.github/workflows/build-apk.yml`, runs `:app:testReleaseUnitTest` then builds the signed APK) verify the Kotlin build.

## Layout

- `app/src/main/java/com/spendtracker/app/`
  - `parser/SpendParser.kt`: notification → transaction parsing (amounts, merchant, bank, IN/OUT)
  - `domain/`: `CategorizationEngine.kt`, `MonthMath.kt`, budget / cashflow / dashboard / goals / recurring logic
  - `data/`: Room entities, DAOs, `SpendRepository.kt`, `PlanningRepository.kt`, `UserPreferences.kt`
  - `service/SpendNotificationListenerService.kt`: notification listener
  - `ui/`: Compose screens + ViewModels, `theme/`, `components/`
- `app/src/test/java/...`: JVM unit tests (mirror the main package layout)
- Root `test_*.py`: Python mirrors of Kotlin logic; `simulator/` + `generate_simulator.py`: HTML simulator
- `docs/planning-features-spec.md`, `docs/research/`, `docs/play-release-setup.md`, `PLAY_STORE_GUIDE.md`

## Rules and gotchas

- **Python mirrors must stay in sync.** `test_spend_parser.py` mirrors `SpendParser.kt` 1:1 (same regexes, same precedence); `test_categorization_engine.py` mirrors `CategorizationEngine.kt`; `test_bank_deduplication.py` mirrors bank normalisation/account consolidation. Change the Kotlin and its mirror together, and add a regression test for every parsing bug.
- Add/update Kotlin unit tests in `app/src/test/` for domain and parser changes.
- User-visible releases: bump `versionCode` and `versionName` in `app/build.gradle.kts` and mention the version in the commit subject (e.g. `… (v2.1.2)`).
- Signing material (`*.jks`, `keystore.properties`) never goes in git; CI gets it from secrets (see `docs/play-release-setup.md`).
- Privacy: transaction data stays on device; don't add network calls or analytics without asking.
- Merges to `main` trigger the release build; work on feature branches.

## How to work in this repo

**Plan first.** For anything touching more than ~3 files or changing behaviour, propose a short plan (files, steps, how it will be tested) and wait for approval before editing. Save agreed plans with `/save-plan` (writes `docs/PLAN.md`) so they survive `/compact`.

**Keep context small:**
- Search before reading (`grep -n`, Glob); read line ranges of big files, never whole files you only need a function from. Don't re-read files you already read this session.
- Hand wide searches ("where is X used across the app?") to a subagent (Explore) and keep only its conclusion.
- Run tests with quiet flags (`-q`, `--quiet`) and pipe long output through `tail -n 40`.
- Make surgical edits; never re-emit a whole file for a small change.
- Unrelated new task → `/clear`. Same task, long history → `/compact` (see the 25% rule).

**Commits:** small and focused, imperative subject line (`Fix …`, `Add …`). Never commit secrets, `.env`, keystores or build output. Never force-push or rewrite shared branches.

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

## Claude Code setup in this repo (`.claude/`)

| What | File | Behaviour |
|---|---|---|
| Context guard | `hooks/context_guard.py` | After each prompt/tool call, warns once when ≤25% context is left |
| Secrets guard | `hooks/guard_secrets.py` | Blocks reading/editing `.env`, `secrets.env`, keystores, `*.pem` etc. (`*.example` files are fine) |
| Post-edit check | `hooks/post_edit_check.py` | After an edit: `.py` → ruff fatal errors (or py_compile), `.js` → `node --check`, `.json` → parse. Never reformats |
| Stop checks | `hooks/stop_checks.py` + `hooks/stop_checks.json` | Before Claude finishes, runs the fast checks relevant to files changed on the branch |
| Permissions | `settings.json` | Read-only git/search and test commands are pre-approved; force-push, `reset --hard` and reading secrets are denied |

Slash commands (`.claude/commands/`): `/handoff`, `/review`, `/check`, `/save-plan`.
Personal overrides go in `.claude/settings.local.json` (git-ignored by Claude Code), not here.
