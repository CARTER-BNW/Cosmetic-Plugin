package com.jbac.cosmetics.catalog;

import java.util.List;

/** A shop category as loaded from catalog.yml. Immutable. */
public record Category(String id, String name, String icon, int hubSlot, List<String> lore, int sort,
                       List<CosmeticItem> items) {}
