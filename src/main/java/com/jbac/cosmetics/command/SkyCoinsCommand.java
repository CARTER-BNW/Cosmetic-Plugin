package com.jbac.cosmetics.command;

import com.jbac.cosmetics.CosmeticPlugin;
import com.jbac.cosmetics.coin.CoinService;
import com.jbac.cosmetics.db.DeliveryDao;
import com.jbac.cosmetics.db.LedgerDao;
import com.jbac.cosmetics.util.Sched;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * /skycoins balance [player] | claim | give (target) (amount) [reason] [txn:id] | take (player) (amount) [reason]
 *           | convertlegacy [player] | inspect
 * `give` is the line the web store runs on the console; see docs/STORE-INTEGRATION.md.
 */
public final class SkyCoinsCommand implements TabExecutor {

    public static final String PERM_ADMIN = "cosmeticplugin.coins.admin";
    private static final List<String> PLAYER_SUBS = List.of("balance", "claim", "convertlegacy");
    private static final List<String> ADMIN_SUBS = List.of("give", "take", "inspect", "audit");

    private final CosmeticPlugin plugin;

    public SkyCoinsCommand(CosmeticPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NonNull CommandSender sender, @NonNull Command command, @NonNull String label,
                             String @NonNull [] args) {
        if (args.length == 0) {
            plugin.messages().send(sender, "skycoins-usage");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "balance", "bal" -> balance(sender, args);
            case "claim" -> claim(sender);
            case "give" -> give(sender, args);
            case "take" -> take(sender, args);
            case "convertlegacy" -> convertLegacy(sender, args);
            case "inspect" -> inspect(sender);
            case "audit" -> audit(sender);
            default -> plugin.messages().send(sender, "skycoins-usage");
        }
        return true;
    }

    private void balance(CommandSender sender, String[] args) {
        if (args.length >= 2) {
            if (!sender.hasPermission(PERM_ADMIN)) {
                plugin.messages().send(sender, "no-permission");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "player-offline", Placeholder.unparsed("player", args[1]));
                return;
            }
            plugin.messages().send(sender, "balance-other",
                    Placeholder.unparsed("player", target.getName()),
                    Placeholder.unparsed("coins", String.valueOf(plugin.wallet().count(target))));
            return;
        }
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        plugin.messages().send(p, "balance", Placeholder.unparsed("coins", String.valueOf(plugin.wallet().count(p))));
    }

    private void claim(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        plugin.coins().claim(p, false);
    }

    private void give(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "skycoins-usage");
            return;
        }
        UUID target = resolve(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-not-found", Placeholder.unparsed("player", args[1]));
            return;
        }
        int amount = parseAmount(args[2]);
        if (amount <= 0) {
            plugin.messages().send(sender, "invalid-amount", Placeholder.unparsed("value", args[2]));
            return;
        }
        String txn = null;
        List<String> reasonWords = new ArrayList<>();
        for (int i = 3; i < args.length; i++) {
            if (args[i].regionMatches(true, 0, "txn:", 0, 4) && args[i].length() > 4) {
                txn = args[i].substring(4);
            } else {
                reasonWords.add(args[i]);
            }
        }
        String reason = reasonWords.isEmpty() ? "admin:" + sender.getName() : String.join(" ", reasonWords);
        String targetLabel = args[1];
        String txnLabel = txn == null ? "-" : txn;
        plugin.coins().give(target, amount, reason, txn).whenComplete((outcome, ex) -> {
            if (ex != null) {
                plugin.getSLF4JLogger().error("give failed for {}", targetLabel, ex);
                plugin.messages().send(sender, "coins-give-failed", Placeholder.unparsed("player", targetLabel));
                return;
            }
            if (outcome.result() == CoinService.Result.DUPLICATE) {
                plugin.messages().send(sender, "coins-duplicate-txn", Placeholder.unparsed("txn", txnLabel));
                return;
            }
            plugin.messages().send(sender, "coins-given",
                    Placeholder.unparsed("amount", String.valueOf(amount)),
                    Placeholder.unparsed("player", targetLabel),
                    Placeholder.unparsed("result", describe(outcome)));
        });
    }

    private static String describe(CoinService.GiveOutcome o) {
        return switch (o.result()) {
            case DELIVERED -> "delivered";
            case PARTIAL_QUEUED -> o.delivered() + " delivered, " + o.queued() + " queued (inventory full)";
            case QUEUED_OFFLINE -> "queued until they next join";
            case DUPLICATE -> "duplicate, ignored";
        };
    }

    private void take(CommandSender sender, String[] args) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (args.length < 3) {
            plugin.messages().send(sender, "skycoins-usage");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.messages().send(sender, "player-offline", Placeholder.unparsed("player", args[1]));
            return;
        }
        int amount = parseAmount(args[2]);
        if (amount <= 0) {
            plugin.messages().send(sender, "invalid-amount", Placeholder.unparsed("value", args[2]));
            return;
        }
        String reason = args.length > 3
                ? String.join(" ", List.of(args).subList(3, args.length))
                : "admin:" + sender.getName();
        if (!plugin.coins().take(target, amount, reason)) {
            plugin.messages().send(sender, "coins-take-not-enough",
                    Placeholder.unparsed("player", target.getName()),
                    Placeholder.unparsed("coins", String.valueOf(plugin.wallet().count(target))));
            return;
        }
        plugin.messages().send(target, "coins-taken", Placeholder.unparsed("amount", String.valueOf(amount)));
        plugin.messages().send(sender, "coins-taken-admin",
                Placeholder.unparsed("amount", String.valueOf(amount)),
                Placeholder.unparsed("player", target.getName()));
    }

    private void convertLegacy(CommandSender sender, String[] args) {
        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission(PERM_ADMIN)) {
                plugin.messages().send(sender, "no-permission");
                return;
            }
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "player-offline", Placeholder.unparsed("player", args[1]));
                return;
            }
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            plugin.messages().send(sender, "player-only");
            return;
        }
        if (!plugin.config().legacyEnabled() || plugin.legacyMatchers().isEmpty()) {
            plugin.messages().send(sender, "coins-legacy-disabled");
            return;
        }
        int converted = plugin.coins().convertLegacy(target);
        if (converted == 0) {
            plugin.messages().send(sender, "coins-nothing-to-convert");
            return;
        }
        plugin.messages().send(target, "coins-converted", Placeholder.unparsed("amount", String.valueOf(converted)));
        if (sender != target) {
            plugin.messages().send(sender, "coins-converted", Placeholder.unparsed("amount", String.valueOf(converted)));
        }
    }

    private void inspect(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return;
        }
        plugin.messages().send(p, "inspect-header");
        for (String line : plugin.coins().describe(p.getInventory().getItemInMainHand())) {
            plugin.messages().send(p, "inspect-line", Placeholder.unparsed("line", line));
        }
    }

    /** Ledger totals vs coins actually held by online players: the dupe/tamper smoke detector. */
    private void audit(CommandSender sender) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            plugin.messages().send(sender, "no-permission");
            return;
        }
        int held = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            held += plugin.wallet().count(p);
        }
        int heldOnline = held;
        int online = Bukkit.getOnlinePlayers().size();
        plugin.database().query(c -> Map.entry(LedgerDao.totalsByKind(c), DeliveryDao.pendingAll(c)))
                .whenComplete((r, ex) -> Sched.main(() -> {
                    if (ex != null) {
                        plugin.getSLF4JLogger().error("audit failed", ex);
                        return;
                    }
                    long net = 0;
                    plugin.messages().send(sender, "audit-header");
                    for (Map.Entry<String, Long> e : r.getKey().entrySet()) {
                        net += e.getValue();
                        line(sender, e.getKey(), String.valueOf(e.getValue()));
                    }
                    line(sender, "net credited (ledger)", String.valueOf(net));
                    line(sender, "queued for delivery", String.valueOf(r.getValue()));
                    line(sender, "held by " + online + " online players", String.valueOf(heldOnline));
                    line(sender, "note", "held + queued should never exceed net credited once everyone is online");
                }));
    }

    private void line(CommandSender sender, String key, String value) {
        plugin.messages().send(sender, "audit-line", Placeholder.unparsed("key", key), Placeholder.unparsed("value", value));
    }

    /** UUID string, online name, or a name the server has seen before. Null if unknown. */
    private static @Nullable UUID resolve(String arg) {
        try {
            return UUID.fromString(arg);
        } catch (IllegalArgumentException ignored) {
            // not a uuid
        }
        Player online = Bukkit.getPlayerExact(arg);
        if (online != null) {
            return online.getUniqueId();
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(arg);
        return cached == null ? null : cached.getUniqueId();
    }

    private static int parseAmount(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NonNull CommandSender sender, @NonNull Command command,
                                                @NonNull String label, String @NonNull [] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(PLAYER_SUBS);
            if (sender.hasPermission(PERM_ADMIN)) {
                subs.addAll(ADMIN_SUBS);
            }
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && sender.hasPermission(PERM_ADMIN)) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }
}
