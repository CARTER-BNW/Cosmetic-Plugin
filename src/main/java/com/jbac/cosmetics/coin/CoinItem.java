package com.jbac.cosmetics.coin;

import com.jbac.cosmetics.config.PluginConfig;
import com.jbac.cosmetics.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ItemLore;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;

/**
 * The SkyCoin item. Identity is the PDC tag {@code cosmeticplugin:skycoin} (an int version), never the look:
 * a renamed sunflower is not a coin, and a coin someone renamed still is.
 */
public final class CoinItem {

    private final NamespacedKey key;
    private final PluginConfig.CoinSpec spec;
    private final ItemStack template;

    public CoinItem(Plugin plugin, PluginConfig.CoinSpec spec) {
        this.key = new NamespacedKey(plugin, "skycoin");
        this.spec = spec;
        this.template = build();
    }

    private ItemStack build() {
        ItemStack s = ItemStack.of(spec.material());
        s.setData(DataComponentTypes.CUSTOM_NAME, Text.item(spec.name()));
        if (!spec.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : spec.lore()) {
                lore.add(Text.item(line));
            }
            s.setData(DataComponentTypes.LORE, ItemLore.lore(lore));
        }
        if (spec.glint()) {
            s.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
        }
        s.editPersistentDataContainer(pdc -> pdc.set(key, PersistentDataType.INTEGER, spec.version()));
        return s;
    }

    public NamespacedKey key() {
        return key;
    }

    public int version() {
        return spec.version();
    }

    /** Fresh stacks totalling {@code amount}, split at the material's max stack size. */
    public List<ItemStack> mint(int amount) {
        List<ItemStack> out = new ArrayList<>();
        int max = template.getMaxStackSize();
        while (amount > 0) {
            int n = Math.min(max, amount);
            ItemStack s = template.clone();
            s.setAmount(n);
            out.add(s);
            amount -= n;
        }
        return out;
    }

    public boolean isCoin(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Integer v = stack.getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return v != null && v <= spec.version();
    }
}
