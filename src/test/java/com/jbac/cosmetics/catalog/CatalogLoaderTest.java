package com.jbac.cosmetics.catalog;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogLoaderTest {

    private static Catalog catalog;
    private static final List<String> problems = new ArrayList<>();

    @BeforeAll
    static void load() {
        var in = CatalogLoaderTest.class.getResourceAsStream("/catalog-sample.yml");
        var yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        // No server in unit tests: accept UPPER_CASE material names and head: specs instead of asking Material.
        catalog = CatalogLoader.load(yaml, problems::add, spec -> spec.startsWith("head:") || spec.matches("[A-Z_]+"));
    }

    @Test
    void categoriesSortedAndInvalidIdSkipped() {
        assertEquals(List.of("colour_tags", "name_colours"), catalog.categories().stream().map(Category::id).toList());
        assertTrue(problems.stream().anyMatch(p -> p.contains("Bad-Id")));
    }

    @Test
    void itemsSortedWithinCategory() {
        var ids = catalog.category("name_colours").orElseThrow().items().stream().map(CosmeticItem::id).toList();
        assertEquals(List.of("namecolour_display", "namecolour_red", "namecolour_dark_green"), ids);
    }

    @Test
    void permissionsAcceptListAndSingleString() {
        assertEquals(List.of("namecolor.color.darkgreen"), catalog.item("namecolour_dark_green").orElseThrow().permissions());
        assertEquals(List.of("namecolor.color.red"), catalog.item("namecolour_red").orElseThrow().permissions());
    }

    @Test
    void useCommandsAndDisplayOnlyParsed() {
        var green = catalog.item("namecolour_dark_green").orElseThrow();
        assertEquals(List.of("namecolor darkgreen"), green.playerUseCommands());
        assertTrue(green.hasUseCommands());
        var display = catalog.item("namecolour_display").orElseThrow();
        assertFalse(display.purchasable());
    }

    @Test
    void placeholderPriceIsReportedButKept() {
        assertTrue(catalog.item("namecolour_red").isPresent());
        assertTrue(problems.stream().anyMatch(p -> p.contains("namecolour_red") && p.contains("placeholder")));
    }

    @Test
    void invalidItemsAreSkippedWithProblems() {
        assertTrue(catalog.item("bad_price").isEmpty());
        assertTrue(catalog.item("no_way_to_deliver").isEmpty());
        assertTrue(problems.stream().anyMatch(p -> p.contains("bad_price")));
        assertTrue(problems.stream().anyMatch(p -> p.contains("no_way_to_deliver")));
    }

    @Test
    void duplicateIdAcrossCategoriesIsSkipped() {
        assertEquals("name_colours", catalog.item("namecolour_red").orElseThrow().categoryId());
        assertTrue(problems.stream().anyMatch(p -> p.contains("colour_tags.namecolour_red") && p.contains("already used")));
    }

    @Test
    void consoleCommandsAcceptSingleStringAndBadIconFallsBackToCategoryIcon() {
        var tag = catalog.item("ctag_black").orElseThrow();
        assertEquals(List.of("ct give %player% black 1"), tag.consolePurchaseCommands());
        assertEquals("head:eyJ0ZXh0dXJlcyI6e319", tag.icon());
        assertTrue(problems.stream().anyMatch(p -> p.contains("ctag_black") && p.contains("unknown icon")));
    }
}
