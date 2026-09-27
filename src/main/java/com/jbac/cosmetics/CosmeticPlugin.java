package com.jbac.cosmetics;

import com.jbac.cosmetics.catalog.Catalog;
import com.jbac.cosmetics.catalog.CatalogLoader;
import com.jbac.cosmetics.catalog.IconFactory;
import com.jbac.cosmetics.coin.CoinItem;
import com.jbac.cosmetics.coin.CoinService;
import com.jbac.cosmetics.coin.CoinWallet;
import com.jbac.cosmetics.coin.LegacyCoinMatcher;
import com.jbac.cosmetics.command.OpenCommand;
import com.jbac.cosmetics.command.ShopAdminCommand;
import com.jbac.cosmetics.command.SkyCoinsCommand;
import com.jbac.cosmetics.config.Messages;
import com.jbac.cosmetics.config.PluginConfig;
import com.jbac.cosmetics.db.Database;
import com.jbac.cosmetics.gui.Menu;
import com.jbac.cosmetics.gui.MenuListener;
import com.jbac.cosmetics.listener.CoinGuardListener;
import com.jbac.cosmetics.listener.PlayerConnectionListener;
import com.jbac.cosmetics.ownership.ConsoleGranter;
import com.jbac.cosmetics.ownership.LuckPermsGranter;
import com.jbac.cosmetics.ownership.OwnershipService;
import com.jbac.cosmetics.ownership.PermissionGranter;
import com.jbac.cosmetics.purchase.PurchaseService;
import com.jbac.cosmetics.util.Sched;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Plugin lifecycle and wiring. Everything else is reached through the accessors below. */
public final class CosmeticPlugin extends JavaPlugin {

    private PluginConfig pluginConfig;
    private Messages messages;
    private Catalog catalog = Catalog.empty();
    private IconFactory icons;
    private Database database;
    private CoinItem coinItem;
    private CoinWallet wallet;
    private List<LegacyCoinMatcher> legacyMatchers = List.of();
    private CoinService coins;
    private PermissionGranter granter;
    private OwnershipService ownership;
    private PurchaseService purchases;

    @Override
    public void onEnable() {
        Sched.init(this);
        saveDefaultConfig();
        saveResourceIfMissing("catalog.yml");
        saveResourceIfMissing("messages.yml");
        icons = new IconFactory();

        reloadAll().forEach(p -> getSLF4JLogger().warn("catalog: {}", p));

        database = new Database(new File(getDataFolder(), pluginConfig.databaseFile()), getSLF4JLogger());
        try {
            database.open();
        } catch (Exception e) {
            getSLF4JLogger().error("Could not open the SQLite database; disabling. "
                    + "sqlite-jdbc comes from plugin.yml `libraries`, which needs internet on first boot.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        coins = new CoinService(this);
        granter = chooseGranter();
        ownership = new OwnershipService(this);
        purchases = new PurchaseService(this);

        registerCommand("cosmeticshop", new ShopAdminCommand(this));
        registerCommand("skycoins", new SkyCoinsCommand(this));
        OpenCommand.register(this);
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new MenuListener(), this);
        getServer().getPluginManager().registerEvents(new CoinGuardListener(this), this);

        for (Player online : Bukkit.getOnlinePlayers()) {   // /reload or late enable
            ownership.load(online.getUniqueId());
        }

        getSLF4JLogger().info("Enabled. Catalog: {} categories, {} items. DB schema v{}. Coin: {} v{}. Permissions via {}.",
                catalog.categories().size(), catalog.items().size(), database.schemaVersion(),
                pluginConfig.coin().material(), coinItem.version(), granter.describe());
    }

    @Override
    public void onDisable() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                online.closeInventory();
            }
        }
        if (database != null) {
            database.close();
        }
    }

    /** Re-reads config.yml, messages.yml and catalog.yml. Returns catalog problems for the caller to report. */
    public List<String> reloadAll() {
        reloadConfig();
        pluginConfig = PluginConfig.from(getConfig(), getSLF4JLogger());
        messages = Messages.load(new File(getDataFolder(), "messages.yml"), getResource("messages.yml"), getSLF4JLogger());
        coinItem = new CoinItem(this, pluginConfig.coin());
        wallet = new CoinWallet(coinItem, pluginConfig.coin().countOffhand());
        legacyMatchers = pluginConfig.legacyEnabled()
                ? LegacyCoinMatcher.fromConfig(pluginConfig.legacyMatchers(), getSLF4JLogger())
                : List.of();
        List<String> problems = new ArrayList<>();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "catalog.yml"));
        catalog = CatalogLoader.load(yaml, problems::add);
        icons.clearCache();
        for (Player online : Bukkit.getOnlinePlayers()) {   // menus hold references to the old catalogue
            if (online.getOpenInventory().getTopInventory().getHolder(false) instanceof Menu) {
                online.closeInventory();
            }
        }
        return problems;
    }

    private PermissionGranter chooseGranter() {
        PluginConfig.GrantMode mode = pluginConfig.grantMode();
        boolean lpPresent = getServer().getPluginManager().isPluginEnabled("LuckPerms");
        if (mode != PluginConfig.GrantMode.CONSOLE && lpPresent) {
            try {
                return new LuckPermsGranter();
            } catch (Throwable t) {
                getSLF4JLogger().warn("LuckPerms is present but its API could not be used; falling back to console commands", t);
            }
        } else if (mode == PluginConfig.GrantMode.LUCKPERMS_API) {
            getSLF4JLogger().warn("permission-grant is luckperms-api but LuckPerms is not enabled; falling back to console commands");
        }
        if (!lpPresent) {
            getSLF4JLogger().warn("LuckPerms not found: ownership permissions will be granted with console `lp user` commands, "
                    + "which also need LuckPerms. Install LuckPerms on this server.");
        }
        return new ConsoleGranter(getSLF4JLogger());
    }

    private void saveResourceIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    private void registerCommand(String name, TabExecutor executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getSLF4JLogger().error("Command '{}' is missing from plugin.yml", name);
            return;
        }
        cmd.setExecutor(executor);
        cmd.setTabCompleter(executor);
    }

    public PluginConfig config() { return pluginConfig; }
    public Messages messages() { return messages; }
    public Catalog catalog() { return catalog; }
    public IconFactory icons() { return icons; }
    public Database database() { return database; }
    public CoinItem coinItem() { return coinItem; }
    public CoinWallet wallet() { return wallet; }
    public List<LegacyCoinMatcher> legacyMatchers() { return legacyMatchers; }
    public CoinService coins() { return coins; }
    public PermissionGranter granter() { return granter; }
    public OwnershipService ownership() { return ownership; }
    public PurchaseService purchases() { return purchases; }
}
