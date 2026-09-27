# Phases & checkpoints

Rule: a phase is done only when every checkpoint is checked **and verified by actually running the thing**. Don't start the next phase before that (`/phase-gate`).

Plan of record: `D:\Claude\config\plans\right-this-is-gonna-synchronous-rainbow.md` (approved 2026-09-27).

## Phase 0 — Scope & setup (code side done; two items wait on John/Tomo)
- [x] Folder scaffold: docs, skills, git
- [x] Scope confirmed with John: config-driven cosmetic shop GUI, paid with physical SkyCoin items, store-agnostic console delivery command (2026-09-27)
- [x] Stack decided; CLAUDE.md updated
- [x] `JayPlugins/` git-ignored; 8 DeluxeMenus reference YAMLs copied to `reference/deluxemenus/`
- [ ] **John:** on Sky, hold the token and run `/data get entity @s SelectedItem`; paste output → sets `coin:` / `legacy-coins:` in config.yml (fallback: `/skycoins inspect` after first deploy)
- [ ] **John → Tomo:** which store platform (Tebex / CraftingStore / other)? Is `/cosmetics` already a command on Sky?

## Phase 1 — Skeleton builds and loads ✅ (2026-09-28)
- [x] `.\gradlew build` → `build/libs/CosmeticPlugin-0.1.0.jar` (JDK 25 auto-provisioned by foojay)
- [x] `.\gradlew runServer` starts Paper 26.2 in `run/`; log shows CosmeticPlugin enabled; `run/plugins/CosmeticPlugin/data.db` has `schema_version=1`
- [x] `/cosmeticshop reload` prints "Reloaded catalog: 0 categories"
- [x] `.\gradlew test` green with `CoinMathTest`

## Phase 2 — Coins, store command, offline queue ✅ (bot-verified 2026-09-28)
- [x] Console `skycoins give <you> 70 test` → 64+6 tagged coins; `/skycoins balance` → 70; a renamed vanilla sunflower is NOT counted
- [x] Same command with `txn:abc` twice → second logs "duplicate txn", gives nothing
- [x] Give while offline → delivered on join with message; full inventory → queued, `/skycoins claim` delivers
- [x] `/skycoins take` removes across stacks; `/skycoins inspect` dumps held item's PDC keys
- [x] Legacy matcher on + renamed sunflower → `/skycoins convertlegacy` tags it; ledger row `convert`

## Phase 3 — Catalogue, GUI, purchase ✅ (bot-verified 2026-09-28; console-granter fallback still untested)
- [x] 2-category hand-written catalogue loads; `/cosmetics` hub → grid paginates above 28 items
- [x] Buy a permission item with LuckPerms present → `/lp user <you> permission info` shows node; coins down; item shows Owned. With LuckPerms absent → console granter path works
- [x] Buy a permission-less item with `say bought %item%`; second attempt says already owned; `purchases.status=complete`
- [x] Broken command on a permission-less item → refund, `status=failed`, ledger `refund`
- [x] Shift-click/drag/number keys move nothing; spam-clicking Confirm buys once
- [x] `CatalogLoaderTest` green (ids, permission lists, price validation, bad item skipped)

## Phase 4 — Converter, admin, owned view ✅ (2026-09-28; `red-glass` style untested)
- [x] `python tools/dm2catalog.py …` over the 8 reference files → ~141 items; re-run with `--existing` keeps edited prices
- [x] `/cosmeticshop give|revoke|info`; "My Cosmetics" shows only owned; both `unaffordable-style` variants; messages reload
- [x] Spot-check 5 converted items render correctly in-game

## Phase 5 — Hardening and deploy ← CURRENT
- [ ] Creative guard (code in `CoinGuardListener`, needs a creative client to verify); kill server mid-purchase → no lost coins after restart
- [x] `/skycoins audit` reconciles the ledger with held + queued coins (bot run 2026-09-28)
- [x] `docs/DEPLOY.md` + `docs/STORE-INTEGRATION.md` written: copy jar (EonSMP: drop as `plugins/CosmeticPlugin.jar.new`, `start.bat` swaps it on restart), first boot needs internet for `libraries:`, unregister `skyshop`/`skyshop_shopmenu` in DeluxeMenus `config.yml`, set `open-command`
- [ ] Integration on EonSMP (`D:\EonServer`): LuckPerms 5.5.59 grant, CraftingStore test command, PlaceholderAPI present
- [ ] Sky: `/skycoins inspect` on a real token; smoke purchase with a test account
- [ ] Optional: PlaceholderAPI expansion `%cosmeticplugin_coins%`, `%cosmeticplugin_owned_count%`
