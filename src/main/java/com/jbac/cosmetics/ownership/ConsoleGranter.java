package com.jbac.cosmetics.ownership;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.slf4j.Logger;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Fallback: runs `lp user (name) permission set (node) true` on the console. Main thread only. */
public final class ConsoleGranter implements PermissionGranter {

    private final Logger log;

    public ConsoleGranter(Logger log) {
        this.log = log;
    }

    @Override
    public boolean has(Player player, String node) {
        return player.isPermissionSet(node) && player.hasPermission(node);
    }

    @Override
    public CompletableFuture<Void> grant(UUID uuid, String name, List<String> nodes) {
        return run(name, nodes, "set", "true");
    }

    @Override
    public CompletableFuture<Void> revoke(UUID uuid, String name, List<String> nodes) {
        return run(name, nodes, "unset", "");
    }

    private CompletableFuture<Void> run(String name, List<String> nodes, String verb, String value) {
        for (String n : nodes) {
            String cmd = ("lp user " + name + " permission " + verb + " " + n + " " + value).trim();
            boolean ok;
            try {
                ok = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } catch (RuntimeException e) {
                log.error("Console permission command threw: {}", cmd, e);
                ok = false;
            }
            if (!ok) {
                return CompletableFuture.failedFuture(new IllegalStateException("console command failed: " + cmd));
            }
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public String describe() {
        return "console `lp user` commands";
    }
}
