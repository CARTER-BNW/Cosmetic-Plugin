package com.jbac.cosmetics.listener;

import com.jbac.cosmetics.CosmeticPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;

/**
 * Creative mode lets a client conjure any item it can describe, including a tagged coin it has seen once
 * (pick-block, ctrl-copy). Refuse every creative inventory action that involves a coin.
 */
public final class CoinGuardListener implements Listener {

    private final CosmeticPlugin plugin;

    public CoinGuardListener(CosmeticPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCreative(InventoryCreativeEvent event) {
        if (plugin.coinItem().isCoin(event.getCursor()) || plugin.coinItem().isCoin(event.getCurrentItem())) {
            event.setCancelled(true);
            plugin.getSLF4JLogger().warn("Blocked creative-mode coin action by {}", event.getWhoClicked().getName());
        }
    }
}
