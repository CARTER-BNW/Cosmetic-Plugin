# Cosmetic Plugin

## Purpose
Paper 26.2 plugin for Tomo's Sky server: players view and buy cosmetics in a chest GUI, paying with physical SkyCoin items bought on the website. Replaces an empty DeluxeMenus stub. Ownership = LuckPerms permission nodes (server convention), or a purchase row for permission-less items. Store-agnostic: the website runs `skycoins give <player> <amount> [reason] [txn:<id>]` on the console.
Plan of record: `D:\Claude\config\plans\right-this-is-gonna-synchronous-rainbow.md`. Phases in @docs/PHASES.md.

## Stack & tooling
- Java 25 (Paper 26.2 minimum) via Gradle toolchain; host JDK is 26 (`C:\Program Files\Java\jdk-26.0.1`); foojay resolver downloads JDK 25 automatically
- Gradle 9.8 wrapper (`.\gradlew`), Kotlin DSL; run-paper 3.1 spins up a dev server in `run/`
- paper-api 26.2.build.129-stable (compileOnly), LuckPerms API 5.5 (compileOnly, softdepend), sqlite-jdbc via plugin.yml `libraries` (never shaded)
- Adventure / MiniMessage for all text; Paper data components + PDC for items; hand-rolled GUI (no GUI library)
- JUnit 6 for pure classes only (no MockBukkit)
- Windows / PowerShell. Python 3.12 + PyYAML for `tools/dm2catalog.py`

## Commands
- Build + test: `.\gradlew build` → `build\libs\CosmeticPlugin-0.1.0.jar`
- Tests only: `.\gradlew test`
- Dev server: `.\gradlew runServer` (Paper 26.2 in `run/`, port 25566, plugin auto-copied, interactive console)
- Staging deploy (EonSMP, John's own server): copy the jar to `D:\EonServer\plugins\CosmeticPlugin.jar.new`; `start.bat` swaps it in on the next restart. Never hot-swap the live jar.

## Architecture map
- `CosmeticPlugin` — lifecycle, wiring, `reloadAll()`; accessors for everything else
- `util/Sched` — the only Bukkit scheduler call site; `thenMain` hops futures back to the main thread
- `util/Text` — MiniMessage helpers; `item()` strips Minecraft's default italics for item text
- `config/PluginConfig` — typed config.yml with validated defaults
- `config/Messages` — messages.yml → Components; bundled defaults fill missing keys
- `catalog/Catalog`, `Category`, `CosmeticItem` — immutable loaded catalogue and id lookups
- `catalog/CatalogLoader` — tolerant catalog.yml parser (bad item = reported + skipped); pure, unit-testable
- `catalog/IconFactory` — icon spec (Material or `head:<base64>`) → cached ItemStack; all icon API churn lives here
- `coin/CoinMath` — pure count / removal planning over slot amounts (smallest stacks first)
- `db/Database` — one SQLite connection, one worker thread, append-only migrations; `query/execute/transaction` return futures
- `command/ShopAdminCommand` — `/cosmeticshop reload|list|info`

## Project rules
- Discover a trap → log it in @docs/GOTCHAS.md immediately (`/gotcha`).
- Phase gates: don't start the next phase until the current phase's checkpoints are verified (`/phase-gate`).
- End of session: new vN entry in @STATUS.md, refresh HANDOFF.md, commit & push (`/session-wrap`).
- Never commit data dumps, logs, or secrets — .gitignore covers these; keep it that way. `JayPlugins/` is Tomo's server dump with player data: never commit it.
- Every DB access goes through `Database` futures; the main thread never touches the connection.
- Item construction only in `IconFactory` / `CoinItem`; scheduler calls only in `Sched`.
- The plugin never hardcodes another cosmetic plugin: everything is permissions + commands from catalog.yml.

## Pointers
- @HANDOFF.md — read first in a new session: live state + next actions
- @STATUS.md — reverse-chron session log
- @docs/PHASES.md — roadmap with phase checkpoints
- @docs/GOTCHAS.md — known traps
- `reference/deluxemenus/` — the 8 DeluxeMenus YAMLs the converter reads (copied from Tomo's dump)
