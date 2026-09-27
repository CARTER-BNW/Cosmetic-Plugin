package com.jbac.cosmetics.gui;

import com.jbac.cosmetics.CosmeticPlugin;
import com.jbac.cosmetics.catalog.Category;
import com.jbac.cosmetics.catalog.CosmeticItem;
import com.jbac.cosmetics.config.PluginConfig;
import com.jbac.cosmetics.purchase.PurchaseService;
import com.jbac.cosmetics.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Paginated item grid for one category, or for every category when used as "My Cosmetics" (category == null).
 * Owned items get a glint and "Owned"; unowned show price and buy/unaffordable; hidden items only show once owned.
 */
public final class CategoryMenu extends Menu {

    private static final int[] GRID = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int SLOT_BACK = 0;
    private static final int SLOT_BALANCE = 4;
    private static final int SLOT_FILTER = 8;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_PAGE = 49;
    private static final int SLOT_NEXT = 53;

    private record Entry(CosmeticItem item, boolean owned) {}

    private final @Nullable Category category;
    private final boolean ownedOnly;
    private int page;

    public CategoryMenu(CosmeticPlugin plugin, Player viewer, @Nullable Category category, boolean ownedOnly) {
        super(plugin, viewer, 6, title(plugin, category, ownedOnly));
        this.category = category;
        this.ownedOnly = ownedOnly;
    }

    private static Component title(CosmeticPlugin plugin, @Nullable Category category, boolean ownedOnly) {
        if (category == null || ownedOnly) {
            return plugin.messages().raw("gui.owned-title");
        }
        return plugin.messages().raw("gui.category-title", Placeholder.component("category", Text.mm(category.name())));
    }

    private List<Entry> entries() {
        Collection<CosmeticItem> source = category == null ? plugin.catalog().items() : category.items();
        List<Entry> out = new ArrayList<>();
        for (CosmeticItem item : source) {
            boolean owned = plugin.ownership().isOwned(viewer, item);
            if (item.hidden() && !owned) {
                continue;
            }
            if (ownedOnly && !owned) {
                continue;
            }
            out.add(new Entry(item, owned));
        }
        return out;
    }

    @Override
    protected void draw() {
        List<Entry> entries = entries();
        int pages = Math.max(1, (int) Math.ceil(entries.size() / (double) GRID.length));
        page = Math.max(0, Math.min(page, pages - 1));
        int coins = plugin.wallet().count(viewer);

        set(SLOT_BACK, button(Material.ARROW, "gui.back", null), click -> new HubMenu(plugin, viewer).open());
        set(SLOT_BALANCE, balanceHead(coins), null);
        if (category != null) {
            set(SLOT_FILTER, button(ownedOnly ? Material.LIME_DYE : Material.GRAY_DYE, "gui.owned-filter", "gui.owned-filter-lore"),
                    click -> new CategoryMenu(plugin, viewer, category, !ownedOnly).open());
        }

        for (int i = 0; i < GRID.length; i++) {
            int index = page * GRID.length + i;
            if (index >= entries.size()) {
                break;
            }
            Entry e = entries.get(index);
            set(GRID[i], render(e, coins), click -> onItemClick(e.item(), e.owned()));
        }

        if (page > 0) {
            set(SLOT_PREV, button(Material.ARROW, "gui.prev-page", null), click -> {
                page--;
                refresh();
            });
        }
        set(SLOT_PAGE, button(Material.PAPER, "gui.page", null,
                Placeholder.unparsed("page", String.valueOf(page + 1)),
                Placeholder.unparsed("pages", String.valueOf(pages))), null);
        if (page < pages - 1) {
            set(SLOT_NEXT, button(Material.ARROW, "gui.next-page", null), click -> {
                page++;
                refresh();
            });
        }
        fillEmpty();
    }

    private ItemStack render(Entry e, int coins) {
        CosmeticItem item = e.item();
        boolean owned = e.owned();
        boolean unaffordable = !owned && item.purchasable() && coins < item.price();
        ItemStack s = unaffordable && plugin.config().unaffordableStyle() == PluginConfig.UnaffordableStyle.RED_GLASS
                ? ItemStack.of(Material.RED_STAINED_GLASS_PANE)
                : plugin.icons().icon(item.icon());
        s.setData(DataComponentTypes.CUSTOM_NAME, Text.item(item.name()));

        List<Component> lore = new ArrayList<>();
        for (String line : item.lore()) {
            lore.add(Text.item(line));
        }
        lore.add(Component.empty());
        if (owned) {
            lore.add(noItalic(plugin.messages().raw("gui.lore-owned")));
            if (item.hasUseCommands()) {
                lore.add(noItalic(plugin.messages().raw("gui.lore-use")));
            }
            s.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        } else if (item.purchasable()) {
            var price = Placeholder.unparsed("price", String.valueOf(item.price()));
            var have = Placeholder.unparsed("coins", String.valueOf(coins));
            lore.add(noItalic(plugin.messages().raw("gui.lore-price", price)));
            lore.add(noItalic(plugin.messages().raw(unaffordable ? "gui.lore-unaffordable" : "gui.lore-buy", price, have)));
        } else {
            lore.add(noItalic(plugin.messages().raw("gui.lore-display-only")));
        }
        s.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        return s;
    }

    private void onItemClick(CosmeticItem item, boolean owned) {
        if (owned) {
            if (item.hasUseCommands()) {
                viewer.closeInventory();
                PurchaseService.runCommands(viewer, item, item.consoleUseCommands(), item.playerUseCommands(), "-", plugin.getSLF4JLogger());
            }
            return;
        }
        if (!item.purchasable()) {
            return;
        }
        int coins = plugin.wallet().count(viewer);
        if (coins < item.price()) {
            plugin.messages().send(viewer, "not-enough",
                    Placeholder.unparsed("missing", String.valueOf(item.price() - coins)),
                    Placeholder.component("item", Text.item(item.name())));
            return;
        }
        new ConfirmMenu(plugin, viewer, item, this).open();
    }
}
