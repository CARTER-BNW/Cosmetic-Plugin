package com.jbac.cosmetics.ownership;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Grants, revokes and checks the permission nodes that represent cosmetic ownership on this server. */
public interface PermissionGranter {

    /**
     * True only when the node is explicitly (or via inheritance) set to true for the player.
     * "Undefined" counts as not owned, so operators are not treated as owning everything.
     */
    boolean has(Player player, String node);

    /** Persistently grant the nodes. Must be called on the main thread; completes on any thread. */
    CompletableFuture<Void> grant(UUID uuid, String name, List<String> nodes);

    CompletableFuture<Void> revoke(UUID uuid, String name, List<String> nodes);

    String describe();
}
