package com.jbac.cosmetics.catalog;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Turns an icon spec into an ItemStack. Spec is a Material name, or "head:" followed by a base64 texture.
 * All icon construction lives here so Paper API churn stays in one file.
 */
public final class IconFactory {

    private static final String HEAD_PREFIX = "head:";

    private final Map<String, ItemStack> cache = new HashMap<>();

    /** Always returns a fresh clone; callers may mutate it freely. */
    public ItemStack icon(String spec) {
        return cache.computeIfAbsent(spec, IconFactory::build).clone();
    }

    public void clearCache() {
        cache.clear();
    }

    public static boolean isValidSpec(String spec) {
        if (spec == null || spec.isBlank()) {
            return false;
        }
        if (spec.startsWith(HEAD_PREFIX)) {
            return spec.length() > HEAD_PREFIX.length();
        }
        Material m = Material.matchMaterial(spec);
        return m != null && m.isItem();
    }

    private static ItemStack build(String spec) {
        if (spec.startsWith(HEAD_PREFIX)) {
            return head(spec.substring(HEAD_PREFIX.length()));
        }
        Material m = Material.matchMaterial(spec);
        if (m == null || !m.isItem()) {
            m = Material.BARRIER;
        }
        return ItemStack.of(m);
    }

    private static ItemStack head(String base64Texture) {
        ItemStack stack = ItemStack.of(Material.PLAYER_HEAD);
        ResolvableProfile profile = ResolvableProfile.resolvableProfile()
                .addProperty(new ProfileProperty("textures", base64Texture))
                .build();
        stack.setData(DataComponentTypes.PROFILE, profile);
        return stack;
    }
}
