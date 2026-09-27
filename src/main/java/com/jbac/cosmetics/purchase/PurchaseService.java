package com.jbac.cosmetics.purchase;

import com.jbac.cosmetics.CosmeticPlugin;
import com.jbac.cosmetics.catalog.CosmeticItem;
import com.jbac.cosmetics.db.LedgerDao;
import com.jbac.cosmetics.db.PurchaseDao;
import com.jbac.cosmetics.util.Sched;
import com.jbac.cosmetics.util.Text;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The purchase transaction, always started on the main thread:
 * <ol>
 *   <li>debounce, re-check item / ownership / balance</li>
 *   <li>remove the coins (same tick as the count, so nothing can race)</li>
 *   <li>DB: purchase row (pending) + ledger debit</li>
 *   <li>grant the permissions</li>
 *   <li>run the purchase commands</li>
 *   <li>DB: status complete; cache; message</li>
 * </ol>
 * Any failure after step 2 refunds the coins and marks the row failed. Every log line carries the txn id.
 */
public final class PurchaseService {

    private final CosmeticPlugin plugin;
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    public PurchaseService(CosmeticPlugin plugin) {
        this.plugin = plugin;
    }

    /** {@code afterwards} runs on the main thread when the flow ends, whichever way (used to reopen the menu). */
    public void purchase(Player p, CosmeticItem requested, Runnable afterwards) {
        UUID id = p.getUniqueId();
        if (!inFlight.add(id)) {
            return;
        }
        boolean handedOff = false;
        try {
            CosmeticItem item = plugin.catalog().item(requested.id()).orElse(null);
            if (item == null || !item.purchasable()) {
                plugin.messages().send(p, "item-unavailable");
                return;
            }
            var itemName = Placeholder.component("item", Text.item(item.name()));
            if (plugin.ownership().isOwned(p, item)) {
                plugin.messages().send(p, "already-owned", itemName);
                return;
            }
            int have = plugin.wallet().count(p);
            if (have < item.price()) {
                plugin.messages().send(p, "not-enough", itemName,
                        Placeholder.unparsed("missing", String.valueOf(item.price() - have)));
                return;
            }
            int removed = plugin.wallet().remove(p, item.price());
            if (removed != item.price()) {
                if (removed > 0) {
                    plugin.wallet().give(p, removed);
                }
                plugin.getSLF4JLogger().error("purchase: could not remove {} coins from {} (removed {}); aborted", item.price(), p.getName(), removed);
                plugin.messages().send(p, "purchase-failed", Placeholder.unparsed("txn", "-"));
                return;
            }

            String txn = UUID.randomUUID().toString();
            plugin.getSLF4JLogger().info("purchase {} by {} for {} coins (txn {})", item.id(), p.getName(), item.price(), txn);
            handedOff = true;
            plugin.database().transaction(c -> {
                PurchaseDao.insert(c, txn, id, item.id(), item.price(), PurchaseDao.STATUS_PENDING);
                LedgerDao.insert(c, id, -item.price(), LedgerDao.KIND_PURCHASE, "purchase:" + item.id(), null, txn);
                return null;
            }).whenComplete((v, ex) -> Sched.main(() -> {
                if (ex != null) {
                    fail(p, item, txn, "database write", ex, afterwards);
                } else {
                    grant(p, item, txn, afterwards);
                }
            }));
        } finally {
            if (!handedOff) {
                inFlight.remove(id);
                afterwards.run();
            }
        }
    }

    private void grant(Player p, CosmeticItem item, String txn, Runnable afterwards) {
        CompletableFuture<Void> granted = item.hasPermissions()
                ? plugin.granter().grant(p.getUniqueId(), p.getName(), item.permissions())
                : CompletableFuture.completedFuture(null);
        Sched.thenMain(granted,
                v -> commands(p, item, txn, afterwards),
                ex -> fail(p, item, txn, "permission grant via " + plugin.granter().describe(), ex, afterwards));
    }

    private void commands(Player p, CosmeticItem item, String txn, Runnable afterwards) {
        boolean consoleOk = runCommands(p, item, item.consolePurchaseCommands(), item.playerPurchaseCommands(), txn, plugin.getSLF4JLogger());
        if (!consoleOk && !item.hasPermissions()) {
            // With no permission to prove ownership, a failed console command means nothing was delivered.
            fail(p, item, txn, "console purchase command returned false", null, afterwards);
            return;
        }
        complete(p, item, txn, afterwards);
    }

    private void complete(Player p, CosmeticItem item, String txn, Runnable afterwards) {
        plugin.database().execute(c -> PurchaseDao.setStatus(c, txn, PurchaseDao.STATUS_COMPLETE))
                .whenComplete((v, ex) -> {
                    if (ex != null) {
                        plugin.getSLF4JLogger().error("purchase {}: could not mark complete", txn, ex);
                    }
                });
        plugin.ownership().markOwned(p.getUniqueId(), item.id());
        plugin.getSLF4JLogger().info("purchase {} complete for {}", txn, p.getName());
        plugin.messages().send(p, "purchased",
                Placeholder.component("item", Text.item(item.name())),
                Placeholder.unparsed("price", String.valueOf(item.price())));
        p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        inFlight.remove(p.getUniqueId());
        afterwards.run();
    }

    private void fail(Player p, CosmeticItem item, String txn, String stage, @Nullable Throwable ex, Runnable afterwards) {
        plugin.getSLF4JLogger().error("purchase {} FAILED at {} for {} ({}); refunding {} coins", txn, stage, p.getName(), item.id(), item.price(), ex);
        plugin.database().execute(c -> PurchaseDao.setStatus(c, txn, PurchaseDao.STATUS_FAILED));
        plugin.coins().refund(p.getUniqueId(), item.price(), txn);
        plugin.messages().send(p, "purchase-failed", Placeholder.unparsed("txn", txn.substring(0, 8)));
        inFlight.remove(p.getUniqueId());
        afterwards.run();
    }

    /**
     * Runs console commands as the console and player commands as the player, expanding
     * %player% %uuid% %item% %txn%. Returns false if any CONSOLE command reported failure.
     */
    public static boolean runCommands(Player p, CosmeticItem item, List<String> console, List<String> player,
                                      String txn, Logger log) {
        boolean ok = true;
        for (String raw : console) {
            String cmd = expand(raw, p, item, txn);
            boolean result;
            try {
                result = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (RuntimeException e) {
                log.error("console command threw (txn {}): {}", txn, cmd, e);
                result = false;
            }
            if (!result) {
                log.warn("console command returned false (txn {}): {}", txn, cmd);
                ok = false;
            }
        }
        for (String raw : player) {
            String cmd = expand(raw, p, item, txn);
            try {
                if (!p.performCommand(cmd)) {
                    log.warn("player command returned false for {} (txn {}): {}", p.getName(), txn, cmd);
                }
            } catch (RuntimeException e) {
                log.error("player command threw for {} (txn {}): {}", p.getName(), txn, cmd, e);
            }
        }
        return ok;
    }

    private static String expand(String cmd, Player p, CosmeticItem item, String txn) {
        String s = cmd.startsWith("/") ? cmd.substring(1) : cmd;
        return s.replace("%player%", p.getName())
                .replace("%uuid%", p.getUniqueId().toString())
                .replace("%item%", item.id())
                .replace("%txn%", txn);
    }
}
