# Cosmetic Plugin

Paper 26.2 plugin for the Sky server: players browse and buy cosmetics in a chest GUI, paying with
**SkyCoins**, a physical in-game token that is sold for real money on the website.

> **Status:** Phases 1-3 built and verified on a local dev server; Phase 4 converter done. See `docs/PHASES.md`.

## How it works
- **Catalogue is config** (`catalog.yml`): categories of items, each with an icon, price, the LuckPerms
  permission nodes that mean "owned" on this server, and optional console/player commands to run on purchase.
  The plugin never hardcodes another cosmetics plugin.
- **SkyCoins are items** carrying a hidden tag; only tagged items count. The website's store runs
  `skycoins give <uuid> <amount> store:<name> txn:<id>` on the console; offline players get their coins on join.
- **Buying**: coins are removed, a purchase row and ledger entry are written, permissions are granted through the
  LuckPerms API, purchase commands run, and the GUI shows the item as Owned. Any failure refunds the coins.
- **Storage**: SQLite (`data.db`), loaded through `plugin.yml` `libraries` (no shading).

## Commands
| Command | Who | What |
|---|---|---|
| `/cosmetics` (aliases `/cosmetic`, `/skyshop`; name configurable) | players | open the shop |
| `/skycoins balance` / `claim` / `convertlegacy` | players | wallet |
| `/skycoins give\|take\|inspect\|audit` | admins / the store | credit, debit, identify tokens, ledger audit |
| `/cosmeticshop reload\|list\|info\|give\|revoke` | admins | catalogue + ownership admin |

## Build and run
```powershell
.\gradlew build          # jar in build\libs\
.\gradlew runServer      # Paper 26.2 dev server in run\ (port 25566)
```
Automated in-game checks: `node tools/testbot.js` + `python tools/botdrive.py` (see the file headers).
Catalogue from the old DeluxeMenus menus: `python tools/dm2catalog.py` (header shows the Sky invocation).

## Project docs
| File | What it is |
|---|---|
| `HANDOFF.md` | Current state, read first in a new session |
| `STATUS.md` | Session log |
| `docs/PHASES.md` | Roadmap and phase checkpoints |
| `docs/GOTCHAS.md` | Known traps |
| `docs/STORE-INTEGRATION.md` | The exact console line for Tebex / CraftingStore |
| `docs/DEPLOY.md` | Installing on Sky and staging on EonSMP |
| `deploy/sky/catalog.yml` | Generated Sky catalogue (140 items, prices TODO) |
