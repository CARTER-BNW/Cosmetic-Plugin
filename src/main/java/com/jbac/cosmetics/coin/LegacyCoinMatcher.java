package com.jbac.cosmetics.coin;

import com.jbac.cosmetics.util.Text;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Recognises coins issued before this plugin existed, so /skycoins convertlegacy can re-mint them. Config-driven. */
public interface LegacyCoinMatcher {

    boolean matches(ItemStack stack);

    String describe();

    static List<LegacyCoinMatcher> fromConfig(List<Map<?, ?>> raw, Logger log) {
        List<LegacyCoinMatcher> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            String type = String.valueOf(m.get("type")).toLowerCase(Locale.ROOT);
            switch (type) {
                case "material-name" -> {
                    Material mat = Material.matchMaterial(String.valueOf(m.get("material")));
                    String name = m.get("name-plain") == null ? "" : String.valueOf(m.get("name-plain")).trim();
                    if (mat == null || name.isEmpty()) {
                        log.warn("legacy-coins matcher material-name needs a valid material and name-plain: {}", m);
                    } else {
                        out.add(new MaterialName(mat, name));
                    }
                }
                case "nexo-id" -> {
                    String id = m.get("id") == null ? "" : String.valueOf(m.get("id")).trim();
                    if (id.isEmpty()) {
                        log.warn("legacy-coins matcher nexo-id needs an id: {}", m);
                    } else {
                        out.add(new NexoId(id));
                    }
                }
                default -> log.warn("legacy-coins matcher has unknown type '{}': {}", type, m);
            }
        }
        return List.copyOf(out);
    }

    /** Material plus display name compared as plain text (colour codes stripped). Checks custom_name, then item_name. */
    record MaterialName(Material material, String namePlain) implements LegacyCoinMatcher {
        @Override
        public boolean matches(ItemStack s) {
            if (s == null || s.isEmpty() || s.getType() != material) {
                return false;
            }
            Component custom = s.getData(DataComponentTypes.CUSTOM_NAME);
            if (custom != null && Text.plain(custom).trim().equalsIgnoreCase(namePlain)) {
                return true;
            }
            Component itemName = s.getData(DataComponentTypes.ITEM_NAME);
            return itemName != null && Text.plain(itemName).trim().equalsIgnoreCase(namePlain);
        }

        @Override
        public String describe() {
            return material.name() + " named '" + namePlain + "'";
        }
    }

    /** Nexo custom item: Nexo stamps its item id into the PDC under nexo:id. No Nexo dependency needed. */
    record NexoId(String id) implements LegacyCoinMatcher {
        private static final NamespacedKey KEY = Objects.requireNonNull(NamespacedKey.fromString("nexo:id"));

        @Override
        public boolean matches(ItemStack s) {
            if (s == null || s.isEmpty()) {
                return false;
            }
            String found = s.getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
            return id.equalsIgnoreCase(found);
        }

        @Override
        public String describe() {
            return "nexo item '" + id + "'";
        }
    }
}
