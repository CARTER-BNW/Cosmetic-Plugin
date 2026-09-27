# HANDOFF — read this first
_Last updated: 2026-09-28 (v2) by Claude_

## Live state
- **Phase 4 done, Phase 5 (hardening/deploy) in progress.** Plugin builds, loads on Paper 26.2, and every Phase 2-4 checkpoint that can be run locally passed via the bot scenarios (`run/scenarios.log`).
- Jar: `build/libs/CosmeticPlugin-0.1.0.jar`. Dev server: `.\gradlew runServer` (port 25566, RCON 25576 / `cosmeticdev`, offline mode, LuckPerms + ViaVersion in `run/plugins`).
- Sky catalogue generated at `deploy/sky/catalog.yml` — prices are all `999999` placeholders except one test value; 48 disguise icons need real heads.

## 30-second health check
```powershell
git status                      # clean tree, branch main
.\gradlew build                  # BUILD SUCCESSFUL, 14 tests
.\gradlew runServer             # then: python tools/rcon.py "cosmeticshop reload"
```
Full regression: start the server, `node tools/testbot.js > run/bot.log 2>&1 &`, then `bash tools/dev-scenarios.sh`.

## Next actions
1. **John, on Sky:** hold the token, run `/data get entity @s SelectedItem`, paste the output → fill `coin:` / `legacy-coins:` in config.yml. Ask Tomo: store platform (Tebex/CraftingStore/other) and whether `/cosmetics` already exists.
2. Set real prices in `deploy/sky/catalog.yml` (search `TODO: set price`); replace `PLAYER_HEAD` placeholders (`TODO icon`) with `head:<base64>` textures.
3. Stage on EonSMP: `Copy-Item build\libs\CosmeticPlugin-0.1.0.jar D:\EonServer\plugins\CosmeticPlugin.jar.new`, restart, run a real purchase with LuckPerms 5.5.59; try the CraftingStore command line from `docs/STORE-INTEGRATION.md`.
4. Remaining untested paths (Phase 5): console-granter fallback (uninstall LuckPerms on the dev server), `unaffordable-style: red-glass`, creative-mode guard (needs a creative client), kill the server mid-purchase.
5. Sky deploy per `docs/DEPLOY.md` (remove the two DeluxeMenus stub menus, wire the store).
