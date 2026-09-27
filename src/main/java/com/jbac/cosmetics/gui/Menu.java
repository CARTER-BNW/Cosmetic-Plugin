package com.jbac.cosmetics.gui;

import com.jbac.cosmetics.CosmeticPlugin;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tiny GUI base: a chest inventory whose holder is this object, a slot -> click handler map, and a
 * {@link #draw()} that repaints in place. {@link MenuListener} cancels every click in any Menu view.
 */
public abstract class Menu implements InventoryHolder {

    protected final CosmeticPlugin plugin;
    protected final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, Consumer<ClickType>> handlers = new HashMap<>();

    protected Menu(CosmeticPlugin plugin, Player viewer, int rows, Component title) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    @Override
    public @NonNull Inventory getInventory() {
        return inventory;
    }

    public Player viewer() {
        return viewer;
    }

    /** Paint and show. Safe to call again later: repaints and re-opens (used to come back from a sub-menu). */
    public void open() {
        if (!viewer.isOnline()) {
            return;
        }
        refresh();
        viewer.openInventory(inventory);
    }

    /** Repaint in place without closing (no flicker). */
    public void refresh() {
        inventory.clear();
        handlers.clear();
        draw();
    }

    protected abstract void draw();

    protected void set(int slot, ItemStack item, @Nullable Consumer<ClickType> onClick) {
        inventory.setItem(slot, item);
        if (onClick == null) {
            handlers.remove(slot);
        } else {
            handlers.put(slot, onClick);
        }
    }

    protected void fillEmpty() {
        ItemStack filler = filler();
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
    }

    void handleClick(int slot, ClickType type) {
        Consumer<ClickType> h = handlers.get(slot);
        if (h != null) {
            h.accept(type);
        }
    }

    protected void onClose() {}

    void closed() {
        onClose();
    }

    // ---- item helpers -------------------------------------------------------------------------------------

    protected static Component noItalic(Component c) {
        return c.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    protected ItemStack filler() {
        ItemStack s = ItemStack.of(Material.BLACK_STAINED_GLASS_PANE);
        s.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        return s;
    }

    protected ItemStack button(Material material, String nameKey, @Nullable String loreKey, TagResolver... resolvers) {
        ItemStack s = ItemStack.of(material);
        s.setData(DataComponentTypes.CUSTOM_NAME, noItalic(plugin.messages().raw(nameKey, resolvers)));
        if (loreKey != null) {
            s.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(noItalic(plugin.messages().raw(loreKey, resolvers)))));
        }
        return s;
    }

    protected ItemStack balanceHead(int coins) {
        ItemStack s = ItemStack.of(Material.PLAYER_HEAD);
        s.setData(DataComponentTypes.PROFILE, ResolvableProfile.resolvableProfile(viewer.getPlayerProfile()));
        TagResolver r = Placeholder.unparsed("coins", String.valueOf(coins));
        s.setData(DataComponentTypes.CUSTOM_NAME, noItalic(plugin.messages().raw("gui.balance-name", r)));
        s.setData(DataComponentTypes.LORE, ItemLore.lore(List.of(noItalic(plugin.messages().raw("gui.balance-lore", r)))));
        return s;
    }
}
