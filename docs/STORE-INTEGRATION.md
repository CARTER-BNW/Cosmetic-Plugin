# Store integration — how the website delivers SkyCoins

The plugin does not talk to any store. Every store platform runs a **console command** on the server after a payment; ours is:

```
skycoins give <player|uuid> <amount> [reason] [txn:<id>]
```

- `<player|uuid>` — Mojang UUID preferred (works for offline players); a name is resolved via the usercache.
- `<amount>` — whole number of coins.
- `[reason]` — free text stored in the ledger `source` column, e.g. `store:tebex`.
- `[txn:<id>]` — the store's transaction id. **Strongly recommended.** It is UNIQUE in the ledger, so if the store retries the command (they do, after timeouts) the second run logs `duplicate txn, ignored` and gives nothing. Without it, retries pay twice.

Delivery rules:
- Player online with space → coins land in the inventory immediately.
- Player online, inventory full → coins are queued; the player is told to run `/skycoins claim`.
- Player offline → coins are queued and delivered on their next join.

So set the store's "require player online" option to **NO**; the plugin handles offline delivery itself.

## Tebex (Buycraft)
Package → Commands → "when purchased, execute on server", require online = no:

```
skycoins give {id} {quantity} store:tebex txn:{transaction}
```
`{id}` is the buyer's UUID, `{quantity}` the package quantity (use a literal number for fixed bundles, e.g. `100`).

## CraftingStore (what EonSMP already runs)
Package → Commands → Execute on purchase:

```
skycoins give %uuid% %amount% store:craftingstore txn:%transactionId%
```
Confirm the exact placeholder names in the CraftingStore dashboard for the package type in use; `%player%` (name) also works but UUID is safer.

## Anything else (custom site)
Any mechanism that can run a console command works: RCON (`mcrcon`), a scheduled task on the box, or a tiny HTTP-to-RCON bridge. Always pass `txn:` with a unique id per payment.

## Verifying a delivery
- Console log line: `[CosmeticPlugin] give 100 SkyCoins -> <uuid> (source store:tebex, txn ...)`
- Ledger row: `SELECT * FROM coin_ledger ORDER BY id DESC LIMIT 5;` in `plugins/CosmeticPlugin/data.db`
- Player side: `/skycoins balance` counts tagged coins in the inventory.
