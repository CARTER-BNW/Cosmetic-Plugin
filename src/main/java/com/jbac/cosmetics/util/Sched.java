package com.jbac.cosmetics.util;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;

/** The only place the Bukkit scheduler is touched. Swap for the global region scheduler here if Folia ever matters. */
public final class Sched {

    private static Plugin plugin;

    private Sched() {}

    public static void init(Plugin p) {
        plugin = p;
    }

    /** Run on the main thread: immediately if already there, otherwise next tick. Dropped (with a log line) after disable. */
    public static void main(Runnable r) {
        if (Bukkit.isPrimaryThread()) {
            r.run();
            return;
        }
        if (!plugin.isEnabled()) {
            plugin.getSLF4JLogger().warn("Dropped a main-thread task scheduled after disable");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, r);
    }

    public static void mainLater(Runnable r, long ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, r, ticks);
    }

    /** Deliver a future's outcome on the main thread. */
    public static <T> void thenMain(CompletableFuture<T> future, Consumer<T> ok, Consumer<Throwable> fail) {
        future.whenComplete((value, ex) -> main(() -> {
            if (ex != null) {
                fail.accept(unwrap(ex));
            } else {
                ok.accept(value);
            }
        }));
    }

    private static Throwable unwrap(Throwable t) {
        return t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
    }
}
