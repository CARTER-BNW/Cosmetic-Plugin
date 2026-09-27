#!/usr/bin/env bash
# End-to-end checks against the run-paper dev server (port 25566, RCON 25576) with tools/testbot.js joined as
# CosmeticTester. Expects the dev catalogue in run/plugins/CosmeticPlugin/catalog.yml (3 categories, 38 items).
# Run from the project root:  bash tools/dev-scenarios.sh | tee run/scenarios.log
set -u
cd "$(dirname "$0")/.."
D="python tools/botdrive.py"
BOT=CosmeticTester

relaunch_bot() {
  (nohup node tools/testbot.js > run/bot.log 2>&1 &)
  until grep -qE 'SPAWNED|KICKED|ERROR' run/bot.log 2>/dev/null; do sleep 1; done
  sleep 3
}
section() { echo; echo "################ $*"; }

section "0. clean slate: no coins, no perms, not op"
python tools/rcon.py "clear $BOT" "lp user $BOT permission unset cosmeticplugin.coins.admin" "lp user $BOT permission unset namecolor.color.darkgreen" "lp user $BOT permission unset namecolor.color.secret" "deop $BOT" >/dev/null
$D "bot:/skycoins balance" "sleep:600"

section "1. full inventory -> coins queued; clear -> /skycoins claim delivers"
$D "rcon:give $BOT stone 2304" "sleep:600" "bot:!inv"
$D "rcon:skycoins give $BOT 3 fulltest" "sleep:1200"
$D "rcon:clear $BOT stone" "sleep:600" "bot:/skycoins claim" "sleep:1200" "bot:!inv"

section "2. give 70 online -> 64+6 tagged coins, balance 73"
$D "rcon:skycoins give $BOT 70 test" "sleep:1000" "bot:!inv" "bot:/skycoins balance" "sleep:800"

section "3. renamed vanilla sunflower is NOT a coin (balance stays 73)"
$D "rcon:give $BOT sunflower[custom_name=\"SkyCoin\"] 3" "sleep:800" "bot:/skycoins balance" "sleep:800" "bot:!inv"

section "4. take 10 -> smallest stacks first (expect 63 tagged coins left)"
$D "rcon:skycoins take $BOT 10" "sleep:800" "bot:!inv" "bot:/skycoins balance" "sleep:800"

section "5. duplicate txn: second give with txn:bot1 does nothing (balance 68)"
$D "rcon:skycoins give $BOT 5 store:tebex txn:bot1" "rcon:skycoins give $BOT 5 store:tebex txn:bot1" "sleep:1200" "bot:/skycoins balance" "sleep:800"

section "6. inspect a held coin (admin perm), then a renamed sunflower"
$D "rcon:lp user $BOT permission set cosmeticplugin.coins.admin true" "sleep:800" "bot:!hotbar 0" "bot:/skycoins inspect" "sleep:1000"

section "7. legacy conversion: enable matcher, convert the 3 renamed sunflowers (balance 71)"
sed -i 's/^\(legacy-coins:\)$/\1/; /^legacy-coins:/,/^database:/ s/enabled: false/enabled: true/' run/plugins/CosmeticPlugin/config.yml
$D "rcon:cosmeticshop reload" "sleep:800" "bot:/skycoins convertlegacy" "sleep:1000" "bot:/skycoins balance" "sleep:800"

section "8. offline queue: quit, give 4, rejoin -> delivered on join (balance 75)"
echo '!quit' >> run/bot-cmds.txt; sleep 4
python tools/rcon.py "skycoins give $BOT 4 offline-test"
sleep 2; relaunch_bot
$D "sleep:2500" "bot:/skycoins balance" "sleep:800"

section "9. GUI hub: head at 4, categories 10/11/15, My Cosmetics 22, close 26"
$D "bot:/cosmetics" "sleep:1500"

section "10. pagination: filler_a (30 items) -> 28 on page 1, next at 53, 2 on page 2, back"
$D "bot:!click 10" "sleep:1500" "bot:!click 53" "sleep:1500" "bot:!click 0" "sleep:1200"

section "11. buy a permission item: Name Colours -> Dark Green (5) -> confirm"
$D "bot:!click 11" "sleep:1500"
$D "bot:!click 10" "sleep:1500"
$D "bot:!click 11" "sleep:3000"
$D "rcon:lp user $BOT permission check namecolor.color.darkgreen" "bot:/skycoins balance" "sleep:800"

section "12. owned item click runs its use command (me uses dark green)"
$D "bot:!click 10" "sleep:1500"

section "13. unaffordable item (Gold, 500): red lore, click -> not enough message"
$D "bot:/cosmetics" "sleep:1200" "bot:!click 11" "sleep:1200" "bot:!click 11" "sleep:1000"

section "14. permission-less item with console command: Colour Tags -> Black Tag (3)"
$D "bot:!click 0" "sleep:1200" "bot:!click 15" "sleep:1500"
$D "bot:!click 10" "sleep:1500"
$D "bot:!click 11" "sleep:3000"

section "15. broken console command: Broken Tag (2) -> refund, balance unchanged"
$D "bot:/skycoins balance" "sleep:600" "bot:!click 11" "sleep:1500"
$D "bot:!click 11" "sleep:3000"
$D "bot:/skycoins balance" "sleep:600"

section "16. shift-click and display-only item move nothing"
$D "bot:!shiftclick 12" "sleep:800" "bot:!click 12" "sleep:800" "bot:!inv"

section "17. hidden item appears only once owned (admin give namecolour_secret)"
$D "bot:!close" "rcon:cosmeticshop give $BOT namecolour_secret" "sleep:1500" "bot:/cosmetics" "sleep:1200" "bot:!click 11" "sleep:1500"

section "18. My Cosmetics shows only owned items"
$D "bot:!click 0" "sleep:1200" "bot:!click 22" "sleep:1500"

section "19. revoke -> Dark Green buyable again"
$D "bot:!close" "rcon:cosmeticshop revoke $BOT namecolour_dark_green" "sleep:1500" "bot:/cosmetics" "sleep:1200" "bot:!click 11" "sleep:1500"

section "20. spam-click confirm buys once (Red, 5)"
$D "bot:!click 12" "sleep:1500"
$D "bot:!click 11" "bot:!click 11" "bot:!click 11" "sleep:3000" "bot:/skycoins balance" "sleep:800"

section "21. audit + database"
$D "bot:!close" "rcon:skycoins audit" "sleep:1500" "bot:/skycoins audit" "sleep:1200"
python - <<'EOF'
import sqlite3
c = sqlite3.connect("run/plugins/CosmeticPlugin/data.db")
print("purchases:")
for r in c.execute("select substr(txn_id,1,8), item_id, price, status from purchases order by created_at"): print("  ", r)
print("ledger (last 12):")
for r in c.execute("select id, amount, kind, source, ext_txn, substr(coalesce(ref,''),1,8) from coin_ledger order by id desc limit 12"): print("  ", r)
print("pending:", c.execute("select count(*), coalesce(sum(amount),0) from pending_deliveries").fetchone())
EOF
echo; echo "################ done"
