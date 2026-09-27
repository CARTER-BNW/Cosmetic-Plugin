package com.jbac.cosmetics.coin;

import com.jbac.cosmetics.CosmeticPlugin;
import com.jbac.cosmetics.config.PluginConfig;
import com.jbac.cosmetics.db.DeliveryDao;
import com.jbac.cosmetics.db.LedgerDao;
import com.jbac.cosmetics.util.Sched;
import com.jbac.cosmetics.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.persistence.PersistentDataContainerView;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Everything that moves coins: store/admin gives, takes, refunds, the offline queue and legacy conversion.
 * Ledger first, inventory second, so the database is always at least as generous as the world.
 */
public final class CoinService {

    public enum Result { DELIVERED, PARTIAL_QUEUED, QUEUED_OFFLINE, DUPLICATE }

    public record GiveOutcome(Result result, int delivered, int queued) {}

    private final CosmeticPlugin plugin;

    public CoinService(CosmeticPlugin plugin) {
        this.plugin = plugin;
    }

    private CoinWallet wallet() {
        return plugin.wallet();
    }

    // ---- credits ---------------------------------------------------------------------------------------------

    /**
     * Credit {@code amount} coins to {@code target}. Safe to call from any thread; completes on the main thread.
     * {@code extTxn} (a store transaction id) makes the call idempotent.
     */
    public CompletableFuture<GiveOutcome> give(UUID target, int amount, String source, @Nullable String extTxn) {
        CompletableFuture<GiveOutcome> out = new CompletableFuture<>();
        plugin.database()
                .transaction(c -> LedgerDao.insert(c, target, amount, LedgerDao.KIND_GIVE, source, extTxn, null))
                .whenComplete((inserted, ex) -> Sched.main(() -> {
                    if (ex != null) {
                        out.completeExceptionally(ex);
                        return;
                    }
                    if (!inserted) {
                        plugin.getSLF4JLogger().warn("Duplicate txn {} from {} for {}: ignored", extTxn, source, target);
                        out.complete(new GiveOutcome(Result.DUPLICATE, 0, 0));
                        return;
                    }
                    plugin.getSLF4JLogger().info("give {} SkyCoins -> {} (source {}, txn {})", amount, target, source, extTxn);
                    deliver(target, amount, source, out);
                }));
        return out;
    }

    /** Puts coins in the player's hands (or the queue) without touching the ledger. */
    private void deliver(UUID target, int amount, String reason, CompletableFuture<GiveOutcome> out) {
        Player p = Bukkit.getPlayer(target);
        if (p == null) {
            queue(target, amount, reason).whenComplete((v, ex) -> Sched.main(() -> {
                if (ex != null) {
                    out.completeExceptionally(ex);
                } else {
                    out.complete(new GiveOutcome(Result.QUEUED_OFFLINE, 0, amount));
                }
            }));
            return;
        }
        int leftover = wallet().give(p, amount);
        int delivered = amount - leftover;
        if (leftover == 0) {
            plugin.messages().send(p, "coins-received", Placeholder.unparsed("amount", String.valueOf(delivered)));
            out.complete(new GiveOutcome(Result.DELIVERED, delivered, 0));
            return;
        }
        if (plugin.config().coin().overflow() == PluginConfig.Overflow.DROP) {
            for (ItemStack s : wallet().coin().mint(leftover)) {
                p.getWorld().dropItem(p.getLocation(), s);
            }
            plugin.messages().send(p, "coins-received", Placeholder.unparsed("amount", String.valueOf(amount)));
            out.complete(new GiveOutcome(Result.DELIVERED, amount, 0));
            return;
        }
        if (delivered > 0) {
            plugin.messages().send(p, "coins-received", Placeholder.unparsed("amount", String.valueOf(delivered)));
        }
        queue(target, leftover, reason).whenComplete((v, ex) -> Sched.main(() -> {
            if (ex != null) {
                out.completeExceptionally(ex);
                return;
            }
            plugin.messages().send(p, "coins-queued", Placeholder.unparsed("amount", String.valueOf(leftover)));
            out.complete(new GiveOutcome(Result.PARTIAL_QUEUED, delivered, leftover));
        }));
    }

    private CompletableFuture<Void> queue(UUID target, int amount, String reason) {
        plugin.getSLF4JLogger().info("queue {} SkyCoins for {} ({})", amount, target, reason);
        return plugin.database().execute(c -> DeliveryDao.add(c, target, amount, reason));
    }

    /** Coins owed after a failed purchase. Main thread. */
    public void refund(UUID target, int amount, String txn) {
        plugin.database()
                .execute(c -> LedgerDao.insert(c, target, amount, LedgerDao.KIND_REFUND, "purchase:" + txn, null, txn))
                .whenComplete((v, ex) -> Sched.main(() -> {
                    if (ex != null) {
                        plugin.getSLF4JLogger().error("Ledger write failed for refund of {} to {} (txn {})", amount, target, txn, ex);
                    }
                    deliver(target, amount, "refund:" + txn, new CompletableFuture<>());
                }));
    }

    // ---- debits ----------------------------------------------------------------------------------------------

    /** Removes coins from an online player's inventory. Main thread. Returns false (and removes nothing) if short. */
    public boolean take(Player p, int amount, String source) {
        if (wallet().count(p) < amount) {
            return false;
        }
        int removed = wallet().remove(p, amount);
        if (removed != amount) {
            plugin.getSLF4JLogger().error("take: planned {} but removed {} from {}", amount, removed, p.getName());
        }
        if (removed > 0) {
            plugin.database().execute(c ->
                    LedgerDao.insert(c, p.getUniqueId(), -removed, LedgerDao.KIND_TAKE, source, null, null));
        }
        return removed == amount;
    }

    // ---- queue -----------------------------------------------------------------------------------------------

    /** Hands over whatever is queued for the player. {@code quiet} suppresses the "nothing waiting" message. */
    public void claim(Player p, boolean quiet) {
        UUID id = p.getUniqueId();
        plugin.database().transaction(c -> DeliveryDao.takeAll(c, id)).whenComplete((total, ex) -> Sched.main(() -> {
            if (ex != null) {
                plugin.getSLF4JLogger().error("Could not read pending deliveries for {}", p.getName(), ex);
                return;
            }
            if (total <= 0) {
                if (!quiet) {
                    plugin.messages().send(p, "nothing-to-claim");
                }
                return;
            }
            if (!p.isOnline()) {
                queue(id, total, "requeue:offline");
                return;
            }
            int leftover = wallet().give(p, total);
            int delivered = total - leftover;
            plugin.getSLF4JLogger().info("claim: delivered {} of {} queued SkyCoins to {}", delivered, total, p.getName());
            if (delivered > 0) {
                plugin.messages().send(p, "coins-claimed", Placeholder.unparsed("amount", String.valueOf(delivered)));
            }
            if (leftover > 0) {
                queue(id, leftover, "requeue:full-inventory");
                plugin.messages().send(p, "coins-queued", Placeholder.unparsed("amount", String.valueOf(leftover)));
            }
        }));
    }

    // ---- legacy ----------------------------------------------------------------------------------------------

    /** Re-mints every stack matching a legacy matcher as tagged coins. Main thread. Returns the number converted. */
    public int convertLegacy(Player p) {
        List<LegacyCoinMatcher> matchers = plugin.legacyMatchers();
        if (matchers.isEmpty()) {
            return 0;
        }
        PlayerInventory inv = p.getInventory();
        CoinItem coin = wallet().coin();
        int converted = 0;
        for (int slot : wallet().slots()) {
            ItemStack s = inv.getItem(slot);
            if (s == null || s.isEmpty() || coin.isCoin(s) || matchers.stream().noneMatch(m -> m.matches(s))) {
                continue;
            }
            int amount = s.getAmount();
            List<ItemStack> minted = coin.mint(amount);
            inv.setItem(slot, minted.getFirst());
            for (int i = 1; i < minted.size(); i++) {
                for (ItemStack overflow : inv.addItem(minted.get(i)).values()) {
                    p.getWorld().dropItem(p.getLocation(), overflow);
                }
            }
            converted += amount;
        }
        if (converted > 0) {
            int total = converted;
            plugin.getSLF4JLogger().info("convertlegacy: {} coins re-minted for {}", total, p.getName());
            plugin.database().execute(c ->
                    LedgerDao.insert(c, p.getUniqueId(), total, LedgerDao.KIND_CONVERT, "legacy", null, null));
        }
        return converted;
    }

    /** Human-readable dump of an item, for identifying whatever token the server hands out today. */
    public List<String> describe(ItemStack s) {
        List<String> lines = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            lines.add("(empty hand)");
            return lines;
        }
        lines.add("material: " + s.getType().getKey() + " x" + s.getAmount());
        Component custom = s.getData(DataComponentTypes.CUSTOM_NAME);
        lines.add("custom_name: " + (custom == null ? "-" : Text.plain(custom)));
        Component itemName = s.getData(DataComponentTypes.ITEM_NAME);
        lines.add("item_name: " + (itemName == null ? "-" : Text.plain(itemName)));
        Object model = s.getData(DataComponentTypes.CUSTOM_MODEL_DATA);
        lines.add("custom_model_data: " + (model == null ? "-" : model.toString()));
        PersistentDataContainerView pdc = s.getPersistentDataContainer();
        if (pdc.getKeys().isEmpty()) {
            lines.add("pdc: (none)");
        } else {
            for (NamespacedKey key : pdc.getKeys()) {
                lines.add("pdc " + key + " = " + pdcValue(pdc, key));
            }
        }
        lines.add("is SkyCoin: " + wallet().coin().isCoin(s));
        for (LegacyCoinMatcher m : plugin.legacyMatchers()) {
            lines.add("legacy match [" + m.describe() + "]: " + m.matches(s));
        }
        return lines;
    }

    private static String pdcValue(PersistentDataContainerView pdc, NamespacedKey key) {
        if (pdc.has(key, PersistentDataType.STRING)) {
            return "\"" + pdc.get(key, PersistentDataType.STRING) + "\"";
        }
        if (pdc.has(key, PersistentDataType.INTEGER)) {
            return String.valueOf(pdc.get(key, PersistentDataType.INTEGER));
        }
        if (pdc.has(key, PersistentDataType.BYTE)) {
            return String.valueOf(pdc.get(key, PersistentDataType.BYTE));
        }
        if (pdc.has(key, PersistentDataType.LONG)) {
            return String.valueOf(pdc.get(key, PersistentDataType.LONG));
        }
        if (pdc.has(key, PersistentDataType.DOUBLE)) {
            return String.valueOf(pdc.get(key, PersistentDataType.DOUBLE));
        }
        return "(non-primitive)";
    }
}
