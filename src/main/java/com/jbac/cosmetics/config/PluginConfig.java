package com.jbac.cosmetics.config;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Typed, validated view of config.yml. Invalid values fall back to defaults with a warning, never a crash. */
public record PluginConfig(
        String openCommandName,
        List<String> openCommandAliases,
        CoinSpec coin,
        boolean legacyEnabled,
        List<Map<?, ?>> legacyMatchers,
        String databaseFile,
        UnaffordableStyle unaffordableStyle,
        GrantMode grantMode,
        boolean debug) {

    public record CoinSpec(Material material, String name, List<String> lore, boolean glint, int version,
                           Overflow overflow, boolean countOffhand) {}

    public enum Overflow { QUEUE, DROP }
    public enum UnaffordableStyle { LORE, RED_GLASS }
    public enum GrantMode { AUTO, LUCKPERMS_API, CONSOLE }

    public static PluginConfig from(FileConfiguration c, Logger log) {
        String matName = c.getString("coin.material", "SUNFLOWER");
        Material material = Material.matchMaterial(matName);
        if (material == null || !material.isItem()) {
            log.warn("config coin.material '{}' is not an item; using SUNFLOWER", matName);
            material = Material.SUNFLOWER;
        }
        CoinSpec coin = new CoinSpec(
                material,
                c.getString("coin.name", "<gold><bold>SkyCoin"),
                List.copyOf(c.getStringList("coin.lore")),
                c.getBoolean("coin.glint", true),
                Math.max(1, c.getInt("coin.version", 1)),
                enumOr(c.getString("coin.give-overflow", "queue"), Overflow.QUEUE, log, "coin.give-overflow"),
                c.getBoolean("coin.count-offhand", false));

        return new PluginConfig(
                c.getString("open-command.name", "cosmetics").trim().toLowerCase(Locale.ROOT),
                List.copyOf(c.getStringList("open-command.aliases")),
                coin,
                c.getBoolean("legacy-coins.enabled", false),
                List.copyOf(c.getMapList("legacy-coins.matchers")),
                c.getString("database.file", "data.db"),
                enumOr(c.getString("gui.unaffordable-style", "lore"), UnaffordableStyle.LORE, log, "gui.unaffordable-style"),
                enumOr(c.getString("permission-grant", "auto"), GrantMode.AUTO, log, "permission-grant"),
                c.getBoolean("debug", false));
    }

    private static <E extends Enum<E>> E enumOr(String raw, E def, Logger log, String path) {
        String norm = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (E e : def.getDeclaringClass().getEnumConstants()) {
            if (e.name().equals(norm)) {
                return e;
            }
        }
        log.warn("config {} = '{}' is not valid; using {}", path, raw, def.name().toLowerCase(Locale.ROOT));
        return def;
    }
}
