# Gotchas

Format: Symptom / Cause / Fix. Add an entry the moment a trap is found (`/gotcha`).

## Claude Bash tool: commands over ~8 KB break with "unexpected EOF while looking for matching quote"
- **Symptom:** a Bash call that writes a big file (or several files) via `cat > f <<'EOF'` dies with a bash quote error and writes nothing. Small heredocs, even several per call, are fine.
- **Cause:** the tool mangles long commands (threshold somewhere around 8 KB); the heredoc body then gets parsed as shell.
- **Fix:** files under ~6 KB: one heredoc per Bash call. Bigger files: use the Write tool.

## `gh api --jq` under PowerShell 5.1 mangles the jq expression
- **Symptom:** `failed to parse jq expression` even though the filter is valid.
- **Cause:** PowerShell strips/rewrites the quotes inside the `--jq` argument.
- **Fix:** run `gh` through the Bash tool, or dump JSON and filter with `grep` / PowerShell objects.

## `JayPlugins/` must never be committed
- **Symptom:** `git add -A` would stage jars, Discord logs and `CoinsEngine/data.db` with 4930 player UUIDs/names.
- **Cause:** it is Tomo's raw server dump dropped into the project folder for reference.
- **Fix:** it is in `.gitignore`; the only inputs we need are copied to `reference/deluxemenus/`.

## plugin.yml `libraries:` needs internet on the server's first boot
- **Symptom:** plugin disables itself with "Could not open the SQLite database" on a fresh server.
- **Cause:** sqlite-jdbc is downloaded from Maven Central into the server's `libraries/` folder at load time, not shaded.
- **Fix:** allow the one-time download, or pre-place the jar; the plugin logs the exact reason.

## paper-api version must be pinned
- **Symptom:** a build that worked yesterday fails today with unrelated compile errors.
- **Cause:** `26.2.build.+` floats to the newest Paper build, which can change API.
- **Fix:** `build.gradle.kts` pins `26.2.build.129-stable`; bump deliberately.

## Dev server port
- **Symptom:** `runServer` fails to bind or connects you to the wrong server.
- **Cause:** John's EonSMP production server at `D:\EonServer` listens on 25565 with an auto-restart loop.
- **Fix:** `run/server.properties` sets `server-port=25566`; connect the client to `localhost:25566`.

## Driving the dev server with a mineflayer bot
- **Symptom:** the bot cannot join (protocol mismatch), dies to mobs and drops the test coins, or prints empty item names.
- **Cause:** mineflayer 4.37 speaks up to 1.21.4 while the server is 26.2; the dev world is normal difficulty; ViaVersion delivers item text as NBT compounds.
- **Fix:** `run/plugins` has ViaVersion + ViaBackwards and `online-mode=false`; run `difficulty peaceful` + `gamerule keepInventory true` before a session; use `!slot <n>` in `tools/testbot.js` for raw names/lore. `tools/dev-scenarios.sh` is the full regression.

## Ownership cache NPE on join (fixed 2026-09-28)
- **Symptom:** `Could not load purchases for <player>` with a NullPointerException every join.
- **Cause:** `Map.put(k, v)` returns the PREVIOUS value, so `map.put(k, newSet()).addAll(...)` dereferenced null.
- **Fix:** build the set first, then put. Caught only because the bot actually joined the server.
