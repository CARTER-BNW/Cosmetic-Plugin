package com.jbac.cosmetics.listener;

import com.jbac.cosmetics.CosmeticPlugin;
import com.jbac.cosmetics.util.Sched;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Join: warm the ownership cache and hand over queued coins. Quit: drop the cache. */
public final class PlayerConnectionListener implements Listener {

    private final CosmeticPlugin plugin;

    public PlayerConnectionListener(CosmeticPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        plugin.ownership().load(p.getUniqueId()).whenComplete((v, ex) -> {
            if (ex != null) {
                plugin.getSLF4JLogger().error("Could not load purchases for {}", p.getName(), ex);
            }
        });
        Sched.mainLater(() -> {
            if (p.isOnline()) {
                plugin.coins().claim(p, true);
            }
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.ownership().evict(event.getPlayer().getUniqueId());
    }
}
