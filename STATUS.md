# STATUS
_Last updated: 2026-09-28 (v2) by Claude_

## v2 (2026-09-28): Phases 1-4 built and bot-verified on a local Paper 26.2 server
- Plan approved: `D:\Claude\config\plans
ight-this-is-gonna-synchronous-rainbow.md`. Decisions: physical SkyCoin items, store-agnostic console delivery, config-driven catalogue, LuckPerms-node ownership.
- Toolchain: Gradle 9.8 wrapper (bootstrapped from the GitHub wrapper files), foojay-provisioned JDK 25 on the JDK 26 host, paper-api 26.2.build.129, run-paper 3.1 dev server in `run/` (port 25566, RCON 25576, offline mode + ViaVersion so a bot can join).
- Code (30 classes, `com.jbac.cosmetics`): coin item/wallet/service + legacy matchers, SQLite ledger/purchases/deliveries, catalogue loader, hand-rolled GUI (hub / category / confirm), purchase transaction with refunds, LuckPerms API granter with console fallback, admin + wallet commands, creative-mode coin guard, `/skycoins audit`.
- Verified with an offline mineflayer bot (`tools/testbot.js`, `tools/botdrive.py`, `tools/dev-scenarios.sh`): 21 scenarios green — delivery, full-inventory queue, offline queue, duplicate txn, take, inspect, legacy convert, hub layout, pagination, permission purchase, permission-less purchase, broken-command refund, unaffordable, shift-click guard, hidden items, My Cosmetics, revoke, spam-click, audit (ledger reconciles to the coin: 137 = 62 held + 75 queued).
- Converter `tools/dm2catalog.py` → `deploy/sky/catalog.yml`: 140 Sky items (12 name colours, 64 disguises incl. multi-node packs, 64 colour tags); 139 prices TODO, 48 HeadDatabase icons TODO. Re-runs keep edited prices.
- Bugs caught by the bot run and fixed: ownership-cache NPE on join (`Map.put` return value).
- Docs: README, CLAUDE.md, GOTCHAS (6 entries), STORE-INTEGRATION.md, DEPLOY.md.
- **Open:** John on Sky: `/data get entity @s SelectedItem` on the token; ask Tomo for the store platform and whether `/cosmetics` exists. Untested paths: console-granter fallback, `unaffordable-style: red-glass`, creative guard, kill-mid-purchase. Not started: EonSMP staging, Sky deploy, PlaceholderAPI expansion.

## v1 (2026-09-27): Project scaffold
- Scaffolded by /create_project: CLAUDE.md, HANDOFF.md, README.md, docs/PHASES.md, docs/GOTCHAS.md
- Project skills: session-wrap, phase-gate, gotcha
- git init on `main`, initial commit
- **Open:** confirm scope (Phase 0), define Phase 1+ checkpoints
