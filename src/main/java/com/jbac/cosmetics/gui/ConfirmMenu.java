package com.jbac.cosmetics.gui;

import com.jbac.cosmetics.CosmeticPlugin;
import com.jbac.cosmetics.catalog.CosmeticItem;
import com.jbac.cosmetics.util.Sched;
import com.jbac.cosmetics.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** "Pay N SkyCoins for X?" Confirm hands off to PurchaseService; both buttons return to the category page. */
public final class ConfirmMenu extends Menu {

    private final CosmeticItem item;
    private final CategoryMenu back;

    public ConfirmMenu(CosmeticPlugin plugin, Player viewer, CosmeticItem item, CategoryMenu back) {
        super(plugin, viewer, 3, plugin.messages().raw("gui.confirm-title"));
        this.item = item;
        this.back = back;
    }

    @Override
    protected void draw() {
        TagResolver[] r = {
                Placeholder.unparsed("price", String.valueOf(item.price())),
                Placeholder.component("item", Text.item(item.name()))
        };
        ItemStack preview = plugin.icons().icon(item.icon());
        preview.setData(DataComponentTypes.CUSTOM_NAME, Text.item(item.name()));
        List<Component> lore = new ArrayList<>();
        for (String line : item.lore()) {
            lore.add(Text.item(line));
        }
        lore.add(Component.empty());
        lore.add(noItalic(plugin.messages().raw("gui.lore-price", r)));
        preview.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        set(13, preview, null);

        set(11, button(Material.LIME_STAINED_GLASS_PANE, "gui.confirm", "gui.confirm-lore", r), click -> {
            viewer.closeInventory();
            plugin.purchases().purchase(viewer, item, () -> Sched.mainLater(back::open, 1L));
        });
        set(15, button(Material.RED_STAINED_GLASS_PANE, "gui.cancel", null), click -> Sched.mainLater(back::open, 1L));
        fillEmpty();
    }
}
