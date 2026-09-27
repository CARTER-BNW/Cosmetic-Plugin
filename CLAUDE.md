# Cosmetic Plugin

## Purpose
Minecraft Java plugin for Paper 26.2 - player cosmetics (private repo)
Scope not yet confirmed — Phase 0 checkpoint in @docs/PHASES.md.

## Stack & tooling
- Java 21+ (verify the exact minimum Paper 26.2 requires in Phase 0)
- Paper API 26.2 from the PaperMC Maven repo — plain paper-api unless NMS access is needed (then paperweight-userdev)
- Build tool: Gradle (Kotlin DSL) unless Phase 0 decides otherwise
- Windows / PowerShell; local Paper test server lives in `run/` (git-ignored)

## Commands
- Build: `.\gradlew build` — jar lands in `build/libs/` (once the Gradle project exists)
- Test: copy the jar to `run/plugins/` and start the Paper server (set up in Phase 1)

## Architecture map
No code yet. Add one line per file as files are created, stating what that file owns.


## Project rules
- Discover a trap → log it in @docs/GOTCHAS.md immediately (`/gotcha`).
- Phase gates: don't start the next phase until the current phase's checkpoints are verified (`/phase-gate`).
- End of session: new vN entry in @STATUS.md, refresh HANDOFF.md, commit & push (`/session-wrap`).
- Never commit data dumps, logs, or secrets — .gitignore covers these; keep it that way.

## Pointers
- @HANDOFF.md — read first in a new session: live state + next actions
- @STATUS.md — reverse-chron session log
- @docs/PHASES.md — roadmap with phase checkpoints
- @docs/GOTCHAS.md — known traps
