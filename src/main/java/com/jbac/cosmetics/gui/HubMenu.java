package com.jbac.cosmetics.gui;

import com.jbac.cosmetics.CosmeticPlugin;
import com.jbac.cosmetics.catalog.Category;
import com.jbac.cosmetics.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** First screen: balance, one icon per category, "My Cosmetics", close. Grows from 3 to 6 rows as categories are added. */
public final class HubMenu extends Menu {

    public HubMenu(CosmeticPlugin plugin, Player viewer) {
        super(plugin, viewer, rowsFor(plugin.catalog().categories().size()), plugin.messages().raw("gui.hub-title"));
    }

    private static int rowsFor(int categories) {
        int gridRows = Math.max(1, (int) Math.ceil(categories / 7.0));
        return Math.min(6, gridRows + 2);
    }

    @Override
    protected void draw() {
        int rows = getInventory().getSize() / 9;
        set(4, balanceHead(plugin.wallet().count(viewer)), null);

        List<Integer> free = new ArrayList<>();
        for (int r = 1; r <= rows - 2; r++) {
            for (int c = 1; c <= 7; c++) {
                free.add(r * 9 + c);
            }
        }
        List<Category> categories = plugin.catalog().categories();
        Map<Integer, Category> placed = new LinkedHashMap<>();
        for (Category c : categories) {                      // explicit hub-slot first
            if (c.hubSlot() >= 0 && free.contains(c.hubSlot()) && !placed.containsKey(c.hubSlot())) {
                placed.put(c.hubSlot(), c);
            }
        }
        for (Category c : categories) {                      // then next free slot
            if (placed.containsValue(c)) {
                continue;
            }
            for (int slot : free) {
                if (!placed.containsKey(slot)) {
                    placed.put(slot, c);
                    break;
                }
            }
        }
        for (Map.Entry<Integer, Category> e : placed.entrySet()) {
            Category category = e.getValue();
            set(e.getKey(), categoryIcon(category), click -> new CategoryMenu(plugin, viewer, category, false).open());
        }

        int last = (rows - 1) * 9;
        set(last + 4, button(Material.BOOK, "gui.owned-filter", "gui.owned-filter-lore"),
                click -> new CategoryMenu(plugin, viewer, null, true).open());
        set(last + 8, button(Material.BARRIER, "gui.close", null), click -> viewer.closeInventory());
        fillEmpty();
    }

    private ItemStack categoryIcon(Category c) {
        ItemStack s = plugin.icons().icon(c.icon());
        s.setData(DataComponentTypes.CUSTOM_NAME, Text.item(c.name()));
        List<Component> lore = new ArrayList<>();
        for (String line : c.lore()) {
            lore.add(Text.item(line));
        }
        lore.add(noItalic(plugin.messages().raw("gui.category-count",
                Placeholder.unparsed("count", String.valueOf(c.items().size())))));
        s.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return s;
    }
}
