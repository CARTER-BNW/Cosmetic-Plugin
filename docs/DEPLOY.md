# Deploying CosmeticPlugin

## Build
```powershell
.\gradlew build        # → build\libs\CosmeticPlugin-<version>.jar
```

## Requirements on the target server
- Paper 26.2 (Java 25+). Bukkit `api-version: '26.2'`.
- Internet on the **first** boot: `plugin.yml` `libraries:` downloads `sqlite-jdbc` into the server's `libraries/` folder. Offline servers: pre-place the jar there.
- Optional: LuckPerms (permission grants use its API; without it the plugin falls back to console `lp user ...`, which then also needs LuckPerms — so in practice install it). PlaceholderAPI is optional.

## First install
1. Copy the jar into `plugins/` and start the server once. It creates `plugins/CosmeticPlugin/{config.yml,catalog.yml,messages.yml,data.db}`.
2. `config.yml`: set `open-command.name` if `/cosmetics` already exists (check with `/plugins` and `/help cosmetics`); describe the coin under `coin:`; if old tokens exist, fill `legacy-coins.matchers` (identify the item with `/skycoins inspect` while holding it) and enable it.
3. `catalog.yml`: generate with `python tools/dm2catalog.py ...` (see the header of the file) and set real prices. Any price ≥ 999999 is a placeholder and is warned about on `/cosmeticshop reload`.
4. `/cosmeticshop reload` and fix anything the console reports.
5. Wire the store: see `STORE-INTEGRATION.md`.

## Sky server specifics
- DeluxeMenus stays. Only the stub is replaced: remove `skyshop` and `skyshop_shopmenu` from `plugins/DeluxeMenus/config.yml` (and their two YAML files) so `/skyshop` is free for our alias.
- Ownership permissions are whatever the existing cosmetic plugins check (NameColor, LibsDisguises, PetBlocks...); the catalog lists them per item. The plugin grants them through LuckPerms on purchase.
- Existing SkyCoin tokens: run `/skycoins convertlegacy` for players (or ask them to) after enabling the matcher, so old tokens become tagged coins the shop accepts.

## EonSMP staging (John's own server, `D:\EonServer`)
The live jar is locked while the server runs. Stage the update and let `start.bat` swap it on the next restart:
```powershell
Copy-Item build\libs\CosmeticPlugin-0.1.0.jar D:\EonServer\plugins\CosmeticPlugin.jar.new
```
`start.bat` renames `*.jar.new` → `*.jar` before launching. Never overwrite the live jar directly.

## Updating
Same as staging: drop the new jar (or `.jar.new` where the server is running) and restart. `data.db` migrations apply automatically and are logged as `Applying database migration vN`.

## Rollback
Stop the server, restore the previous jar, start. The database schema only ever gains tables/columns, so an older jar keeps working with a newer `data.db`.
