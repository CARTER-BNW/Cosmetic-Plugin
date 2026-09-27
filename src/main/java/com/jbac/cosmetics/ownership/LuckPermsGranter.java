package com.jbac.cosmetics.ownership;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.node.Node;
import net.luckperms.api.util.Tristate;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** LuckPerms API implementation. Only loaded when LuckPerms is present (see CosmeticPlugin#chooseGranter). */
public final class LuckPermsGranter implements PermissionGranter {

    private final LuckPerms lp;

    public LuckPermsGranter() {
        this.lp = LuckPermsProvider.get();
    }

    @Override
    public boolean has(Player player, String node) {
        return lp.getPlayerAdapter(Player.class).getPermissionData(player).checkPermission(node) == Tristate.TRUE;
    }

    @Override
    public CompletableFuture<Void> grant(UUID uuid, String name, List<String> nodes) {
        return lp.getUserManager().modifyUser(uuid, user -> {
            for (String n : nodes) {
                user.data().add(Node.builder(n).build());
            }
        });
    }

    @Override
    public CompletableFuture<Void> revoke(UUID uuid, String name, List<String> nodes) {
        return lp.getUserManager().modifyUser(uuid, user -> {
            for (String n : nodes) {
                user.data().remove(Node.builder(n).build());
            }
        });
    }

    @Override
    public String describe() {
        return "LuckPerms API";
    }
}
