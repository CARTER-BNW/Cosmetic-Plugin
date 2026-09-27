package com.jbac.cosmetics.command;

import com.jbac.cosmetics.CosmeticPlugin;
import com.jbac.cosmetics.catalog.Category;
import com.jbac.cosmetics.catalog.CosmeticItem;
import com.jbac.cosmetics.db.LedgerDao;
import com.jbac.cosmetics.db.PurchaseDao;
import com.jbac.cosmetics.util.Sched;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** /cosmeticshop reload | list | info (item) | give (player) (item) | revoke (player) (item) */
public final class ShopAdminCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("reload", "list", "info", "give", "revoke");

    private final CosmeticPlugin plugin;

    public ShopAdminCommand(CosmeticPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label,
                             String @NonNull [] args) {
        if (args.length == 0) {
            plugin.messages().send(sender, "admin.usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> reload(sender);
            case "list" -> list(sender);
            case "info" -> info(sender, args);
            case "give" -> give(sender, args);
            case "revoke" -> revoke(sender, args);
            default -> plugin.messages().send(sender, "admin.usage");
        }
        return true;
    }

    private void reload(CommandSender sender) {
        List<String> problems = plugin.reloadAll();
        problems.forEach(p -> plugin.getSLF4JLogger().warn("catalog: {}", p));
        plugin.messages().send(sender, "admin.reloaded",
                Placeholder.unparsed("categories", String.valueOf(plugin.catalog().categories().size())),
                Placeholder.unparsed("items", String.valueOf(plugin.catalog().items().size())),
                Placeholder.unparsed("problems", String.valueOf(problems.size())));
    }

    private void list(CommandSender sender) {
        for (Category c : plugin.catalog().categories()) {
            plugin.messages().send(sender, "admin.list-line",
                    Placeholder.unparsed("category", c.id()),
                    Placeholder.unparsed("count", String.valueOf(c.items().size())));
        }
    }

    private void info(CommandSender sender, String[] args) {
        CosmeticItem item = itemArg(sender, args, 1);
        if (item == null) {
            return;
        }
        line(sender, "id", item.id());
        line(sender, "category", item.categoryId());
        line(sender, "name", item.name());
        line(sender, "icon", item.icon());
        line(sender, "price", item.purchasable() ? String.valueOf(item.price()) : "display-only");
        line(sender, "permissions", String.join(", ", item.permissions()));
        line(sender, "purchase (console)", String.join(" | ", item.consolePurchaseCommands()));
        line(sender, "purchase (player)", String.join(" | ", item.playerPurchaseCommands()));
        line(sender, "use (console)", String.join(" | ", item.consoleUseCommands()));
        line(sender, "use (player)", String.join(" | ", item.playerUseCommands()));
        line(sender, "hidden", String.valueOf(item.hidden()));
    }

    /** Grant an item without charging: permissions via the granter, plus a completed purchase row at price 0. */
    private void give(CommandSender sender, String[] args) {
        Player target = playerArg(sender, args, 1);
        CosmeticItem item = itemArg(sender, args, 2);
        if (target == null || item == null) {
            return;
        }
        UUID id = target.getUniqueId();
        String txn = "admin-" + UUID.randomUUID();
        CompletableFuture<Void> granted = item.hasPermissions()
                ? plugin.granter().grant(id, target.getName(), item.permissions())
                : CompletableFuture.completedFuture(null);
        Sched.thenMain(granted, v -> {
            plugin.database().transaction(c -> {
                PurchaseDao.insert(c, txn, id, item.id(), 0, PurchaseDao.STATUS_COMPLETE);
                LedgerDao.insert(c, id, 0, LedgerDao.KIND_PURCHASE, "admin:" + sender.getName() + ":" + item.id(), null, txn);
                return null;
            });
            plugin.ownership().markOwned(id, item.id());
            plugin.getSLF4JLogger().info("admin {} gave {} to {} (txn {})", sender.getName(), item.id(), target.getName(), txn);
            plugin.messages().send(sender, "admin.given",
                    Placeholder.unparsed("item", item.id()), Placeholder.unparsed("player", target.getName()));
        }, ex -> {
            plugin.getSLF4JLogger().error("admin give of {} to {} failed", item.id(), target.getName(), ex);
            plugin.messages().send(sender, "admin.give-failed",
                    Placeholder.unparsed("item", item.id()), Placeholder.unparsed("player", target.getName()));
        });
    }

    private void revoke(CommandSender sender, String[] args) {
        Player target = playerArg(sender, args, 1);
        CosmeticItem item = itemArg(sender, args, 2);
        if (target == null || item == null) {
            return;
        }
        UUID id = target.getUniqueId();
        CompletableFuture<Void> revoked = item.hasPermissions()
                ? plugin.granter().revoke(id, target.getName(), item.permissions())
                : CompletableFuture.completedFuture(null);
        Sched.thenMain(revoked, v -> {
            plugin.database().execute(c -> PurchaseDao.revoke(c, id, item.id()));
            plugin.ownership().unmark(id, item.id());
            plugin.getSLF4JLogger().info("admin {} revoked {} from {}", sender.getName(), item.id(), target.getName());
            plugin.messages().send(sender, "admin.revoked",
                    Placeholder.unparsed("item", item.id()), Placeholder.unparsed("player", target.getName()));
        }, ex -> {
            plugin.getSLF4JLogger().error("admin revoke of {} from {} failed", item.id(), target.getName(), ex);
            plugin.messages().send(sender, "admin.give-failed",
                    Placeholder.unparsed("item", item.id()), Placeholder.unparsed("player", target.getName()));
        });
    }

    private @Nullable Player playerArg(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            plugin.messages().send(sender, "admin.usage");
            return null;
        }
        Player p = Bukkit.getPlayerExact(args[index]);
        if (p == null) {
            plugin.messages().send(sender, "player-offline", Placeholder.unparsed("player", args[index]));
        }
        return p;
    }

    private @Nullable CosmeticItem itemArg(CommandSender sender, String[] args, int index) {
        if (args.length <= index) {
            plugin.messages().send(sender, "admin.usage");
            return null;
        }
        CosmeticItem item = plugin.catalog().item(args[index]).orElse(null);
        if (item == null) {
            plugin.messages().send(sender, "admin.unknown-item", Placeholder.unparsed("item", args[index]));
        }
        return item;
    }

    private void line(CommandSender sender, String key, String value) {
        plugin.messages().send(sender, "admin.info-line",
                Placeholder.unparsed("key", key), Placeholder.unparsed("value", value.isEmpty() ? "-" : value));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command,
                                                @NonNull String label, String @NonNull [] args) {
        String last = args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(last)).toList();
        }
        boolean wantsItem = (args.length == 2 && args[0].equalsIgnoreCase("info"))
                || (args.length == 3 && (args[0].equalsIgnoreCase("give") || args[0].equalsIgnoreCase("revoke")));
        if (wantsItem) {
            return plugin.catalog().items().stream().map(CosmeticItem::id).filter(id -> id.startsWith(last)).toList();
        }
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(last)).toList();
        }
        return List.of();
    }
}
