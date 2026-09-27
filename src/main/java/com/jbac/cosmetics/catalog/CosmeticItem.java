package com.jbac.cosmetics.catalog;

import java.util.List;

/**
 * One purchasable (or display-only) cosmetic as loaded from catalog.yml. Immutable.
 * price < 0 means display-only. Ownership = player holds ALL permissions, or a completed purchase row.
 */
public record CosmeticItem(
        String id,
        String categoryId,
        String name,
        String icon,
        List<String> lore,
        int price,
        List<String> permissions,
        List<String> consolePurchaseCommands,
        List<String> playerPurchaseCommands,
        List<String> consoleUseCommands,
        List<String> playerUseCommands,
        int sort,
        boolean hidden) {

    /** Prices at or above this are converter placeholders; the loader warns about them. */
    public static final int PLACEHOLDER_PRICE = 999_999;

    public boolean purchasable() {
        return price >= 0;
    }

    public boolean hasPermissions() {
        return !permissions.isEmpty();
    }

    public boolean hasUseCommands() {
        return !consoleUseCommands.isEmpty() || !playerUseCommands.isEmpty();
    }
}
