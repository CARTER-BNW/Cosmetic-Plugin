package com.jbac.cosmetics.ownership;

import com.jbac.cosmetics.CosmeticPlugin;
import com.jbac.cosmetics.catalog.CosmeticItem;
import com.jbac.cosmetics.db.PurchaseDao;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Answers "does this player own this cosmetic?".
 * Items with permissions: owned when every node is effectively true (the server's convention).
 * Items without permissions: owned when a completed purchase row exists (cached per online player).
 */
public final class OwnershipService {

    private final CosmeticPlugin plugin;
    private final Map<UUID, Set<String>> purchased = new ConcurrentHashMap<>();

    public OwnershipService(CosmeticPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isOwned(Player player, CosmeticItem item) {
        if (item.hasPermissions()) {
            for (String node : item.permissions()) {
                if (!plugin.granter().has(player, node)) {
                    return false;
                }
            }
            return true;
        }
        return purchased.getOrDefault(player.getUniqueId(), Set.of()).contains(item.id());
    }

    /** Warm the purchase cache for a player (on join). */
    public CompletableFuture<Void> load(UUID uuid) {
        return plugin.database().query(c -> PurchaseDao.completedItems(c, uuid))
                .thenAccept(items -> {
                    Set<String> set = ConcurrentHashMap.newKeySet();
                    set.addAll(items);
                    purchased.put(uuid, set);
                });
    }

    public void evict(UUID uuid) {
        purchased.remove(uuid);
    }

    public void markOwned(UUID uuid, String itemId) {
        purchased.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(itemId);
    }

    public void unmark(UUID uuid, String itemId) {
        Set<String> set = purchased.get(uuid);
        if (set != null) {
            set.remove(itemId);
        }
    }
}
