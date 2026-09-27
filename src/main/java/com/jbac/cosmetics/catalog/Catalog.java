package com.jbac.cosmetics.catalog;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The loaded catalogue: ordered categories plus id lookups. Replaced wholesale on reload. */
public final class Catalog {

    private static final Catalog EMPTY = new Catalog(List.of());

    private final List<Category> categories;
    private final Map<String, Category> categoriesById = new LinkedHashMap<>();
    private final Map<String, CosmeticItem> itemsById = new LinkedHashMap<>();

    public Catalog(List<Category> categories) {
        this.categories = List.copyOf(categories);
        for (Category c : this.categories) {
            categoriesById.put(c.id(), c);
            for (CosmeticItem i : c.items()) {
                itemsById.put(i.id(), i);
            }
        }
    }

    public static Catalog empty() {
        return EMPTY;
    }

    public List<Category> categories() {
        return categories;
    }

    public Collection<CosmeticItem> items() {
        return Collections.unmodifiableCollection(itemsById.values());
    }

    public Optional<CosmeticItem> item(String id) {
        return Optional.ofNullable(itemsById.get(id));
    }

    public Optional<Category> category(String id) {
        return Optional.ofNullable(categoriesById.get(id));
    }
}
