package com.jbac.cosmetics.command;

import com.jbac.cosmetics.CosmeticPlugin;
import com.jbac.cosmetics.gui.HubMenu;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.BukkitCommand;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * The player-facing shop command. Its name and aliases come from config.yml (open-command) and it is
 * registered through the CommandMap, so a clash with another plugin's /cosmetics is fixed by editing config.
 */
public final class OpenCommand extends BukkitCommand {

    public static final String PERMISSION = "cosmeticplugin.use";

    private final CosmeticPlugin plugin;

    private OpenCommand(CosmeticPlugin plugin, String name, List<String> aliases) {
        super(name, "Open the cosmetic shop", "/" + name, aliases);
        this.plugin = plugin;
        setPermission(PERMISSION);
    }

    /** Registers the command from config. Changing the name later needs a server restart. */
    public static void register(CosmeticPlugin plugin) {
        String name = plugin.config().openCommandName();
        OpenCommand cmd = new OpenCommand(plugin, name, plugin.config().openCommandAliases());
        Bukkit.getCommandMap().register("cosmeticplugin", cmd);
        Command owner = Bukkit.getCommandMap().getCommand(name);
        if (owner == cmd) {
            plugin.getSLF4JLogger().info("Shop command registered as /{} (aliases {})", name, cmd.getAliases());
        } else {
            plugin.getSLF4JLogger().warn("/{} is already taken by another plugin; the shop is reachable as /cosmeticplugin:{}. "
                    + "Change open-command.name in config.yml.", name, name);
        }
    }

    @Override
    public boolean execute(@NonNull CommandSender sender, @NonNull String label, String @NonNull [] args) {
        if (!(sender instanceof Player p)) {
            plugin.messages().send(sender, "player-only");
            return true;
        }
        if (!testPermission(sender)) {
            return true;
        }
        new HubMenu(plugin, p).open();
        return true;
    }
}
