package com.jbac.cosmetics.catalog;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Parses catalog.yml. Tolerant: a bad item or category is reported and skipped, never fatal.
 * Works on any ConfigurationSection so unit tests can feed it a YamlConfiguration without a server.
 */
public final class CatalogLoader {

    private static final Pattern ID = Pattern.compile("[a-z0-9_]+");

    private CatalogLoader() {}

    /** Production entry point: icons are validated against the server's Material registry. */
    public static Catalog load(ConfigurationSection root, Consumer<String> problems) {
        return load(root, problems, IconFactory::isValidSpec);
    }

    /** {@code iconValid} is injectable so the loader can be unit-tested without a running server. */
    public static Catalog load(ConfigurationSection root, Consumer<String> problems, Predicate<String> iconValid) {
        ConfigurationSection cats = root.getConfigurationSection("categories");
        if (cats == null) {
            Object raw = root.get("categories");
            if (raw != null && !(raw instanceof Map<?, ?> m && m.isEmpty())) {
                problems.accept("'categories' must be a section");
            }
            return Catalog.empty();
        }

        List<Category> categories = new ArrayList<>();
        Set<String> seenItemIds = new HashSet<>();
        for (String catId : cats.getKeys(false)) {
            ConfigurationSection cs = cats.getConfigurationSection(catId);
            String where = "category '" + catId + "'";
            if (cs == null) {
                problems.accept(where + " must be a section");
                continue;
            }
            if (!ID.matcher(catId).matches()) {
                problems.accept(where + ": id must match [a-z0-9_]+");
                continue;
            }
            String icon = iconOr(cs.getString("icon"), "CHEST", where, problems, iconValid);
            List<CosmeticItem> items = new ArrayList<>();
            ConfigurationSection is = cs.getConfigurationSection("items");
            if (is != null) {
                for (String itemId : is.getKeys(false)) {
                    String itemWhere = "item '" + catId + "." + itemId + "'";
                    ConfigurationSection sec = is.getConfigurationSection(itemId);
                    if (sec == null) {
                        problems.accept(itemWhere + " must be a section");
                        continue;
                    }
                    if (!ID.matcher(itemId).matches()) {
                        problems.accept(itemWhere + ": id must match [a-z0-9_]+");
                        continue;
                    }
                    if (!seenItemIds.add(itemId)) {
                        problems.accept(itemWhere + ": id already used in another category");
                        continue;
                    }
                    CosmeticItem item = readItem(itemId, catId, sec, icon, itemWhere, problems, iconValid);
                    if (item != null) {
                        items.add(item);
                    }
                }
            }
            items.sort(Comparator.comparingInt(CosmeticItem::sort).thenComparing(CosmeticItem::id));
            categories.add(new Category(catId, cs.getString("name", catId), icon, cs.getInt("hub-slot", -1),
                    List.copyOf(cs.getStringList("lore")), cs.getInt("sort", 0), List.copyOf(items)));
        }
        categories.sort(Comparator.comparingInt(Category::sort).thenComparing(Category::id));
        return new Catalog(categories);
    }

    private static CosmeticItem readItem(String id, String catId, ConfigurationSection sec, String categoryIcon,
                                         String where, Consumer<String> problems, Predicate<String> iconValid) {
        int price = -1;
        if (sec.contains("price")) {
            if (!sec.isInt("price")) {
                problems.accept(where + ": price must be a whole number");
                return null;
            }
            price = sec.getInt("price");
            if (price < 0) {
                problems.accept(where + ": price must be >= 0 (omit price for display-only)");
                return null;
            }
            if (price >= CosmeticItem.PLACEHOLDER_PRICE) {
                problems.accept(where + ": price " + price + " looks like a placeholder (TODO: set price)");
            }
        }

        List<String> permissions = new ArrayList<>(stringList(sec, "permissions"));
        if (sec.isString("permission")) {
            permissions.add(sec.getString("permission"));
        }
        List<String> consolePurchase = stringList(sec, "purchase-commands.console");
        List<String> playerPurchase = stringList(sec, "purchase-commands.player");
        List<String> consoleUse = stringList(sec, "use-commands.console");
        List<String> playerUse = stringList(sec, "use-commands.player");

        if (price >= 0 && permissions.isEmpty() && consolePurchase.isEmpty()) {
            problems.accept(where + ": a purchasable item needs at least one permission or a console purchase command");
            return null;
        }

        String icon = iconOr(sec.getString("icon"), categoryIcon, where, problems, iconValid);
        return new CosmeticItem(id, catId, sec.getString("name", id), icon, List.copyOf(sec.getStringList("lore")),
                price, List.copyOf(permissions), consolePurchase, playerPurchase, consoleUse, playerUse,
                sec.getInt("sort", 0), sec.getBoolean("hidden", false));
    }

    /** Accepts either a single string or a list at {@code path}. */
    private static List<String> stringList(ConfigurationSection s, String path) {
        if (s.isString(path)) {
            return List.of(s.getString(path));
        }
        return List.copyOf(s.getStringList(path));
    }

    private static String iconOr(String spec, String fallback, String where, Consumer<String> problems,
                                 Predicate<String> iconValid) {
        if (spec == null || spec.isBlank()) {
            return fallback;
        }
        if (iconValid.test(spec)) {
            return spec;
        }
        problems.accept(where + ": unknown icon '" + spec + "', using " + fallback);
        return fallback;
    }
}
