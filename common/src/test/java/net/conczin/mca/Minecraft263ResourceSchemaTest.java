package net.conczin.mca;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Minecraft263ResourceSchemaTest {
    private static final List<String> HEADSTONES = List.of(
            "wooden_upright_headstone",
            "cobblestone_upright_headstone",
            "slanted_headstone",
            "cobblestone_slanted_headstone",
            "gravelling_headstone",
            "cross_headstone",
            "deepslate_upright_headstone",
            "wooden_slanted_headstone",
            "deepslate_slanted_headstone",
            "golden_slanted_headstone",
            "golden_upright_headstone",
            "upright_headstone",
            "wall_headstone"
    );

    @Test
    void rootLocationCriterionUses263TypedPredicate() throws IOException {
        JsonElement player = resource("data/mca/advancement/root.json")
                .getAsJsonObject("criteria")
                .getAsJsonObject("village")
                .getAsJsonObject("conditions")
                .get("player");

        assertTrue(player.isJsonObject(), "26.3 location advancement player predicate must use the typed predicate object");
        JsonObject entityProperties = player.getAsJsonObject();
        assertEquals("minecraft:entity_properties", entityProperties.get("type").getAsString());
        assertEquals("this", entityProperties.get("entity").getAsString());
        assertEquals(
                "#minecraft:village",
                entityProperties.getAsJsonObject("predicate")
                        .getAsJsonObject("minecraft:location")
                        .get("structures")
                        .getAsString()
        );
    }

    @Test
    void bouquetRecipeAdvancementUsesItemTagPredicate() throws IOException {
        JsonObject predicate = resource("data/mca/advancement/recipes/misc/bouquet.json")
                .getAsJsonObject("criteria")
                .getAsJsonObject("has_item")
                .getAsJsonObject("conditions")
                .getAsJsonArray("items")
                .get(0)
                .getAsJsonObject();

        assertFalse(predicate.has("tag"), "legacy ItemPredicate.tag must not be used");
        assertEquals("#minecraft:small_flowers", predicate.get("items").getAsString());
    }

    @Test
    void headstoneSilkTouchPredicatesUse263TypedConditionSchema() throws IOException {
        for (String headstone : HEADSTONES) {
            JsonObject lootTable = resource("data/mca/loot_table/blocks/" + headstone + ".json");
            AtomicInteger matchToolCount = new AtomicInteger();
            assertMatchToolPredicates(lootTable, headstone, matchToolCount);
            assertEquals(2, matchToolCount.get(), headstone + " should contain its two Silk Touch tool predicates");
        }
    }

    @Test
    void zombieVillagerLootUses263LootingModifier() throws IOException {
        for (String gender : List.of("male", "female")) {
            JsonObject lootTable = resource("data/mca/loot_table/entities/" + gender + "_zombie_villager.json");
            JsonArray modifiers = lootTable.getAsJsonArray("pools")
                    .get(0).getAsJsonObject()
                    .getAsJsonArray("entries")
                    .get(0).getAsJsonObject()
                    .getAsJsonArray("modifier");

            JsonObject looting = modifiers.get(1).getAsJsonObject();
            assertEquals("minecraft:enchanted_count_increase", looting.get("type").getAsString());
            assertEquals("minecraft:looting", looting.get("enchantment").getAsString());
        }
    }

    private static void assertMatchToolPredicates(JsonElement element, String resourceName, AtomicInteger count) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                assertMatchToolPredicates(child, resourceName, count);
            }
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }

        JsonObject object = element.getAsJsonObject();
        if (object.has("type") && "minecraft:match_tool".equals(object.get("type").getAsString())) {
            count.incrementAndGet();
            JsonObject predicate = object.getAsJsonObject("predicate");
            assertFalse(predicate.has("enchantments"), resourceName + " still uses the legacy enchantments field");
            JsonArray enchantments = predicate.getAsJsonObject("predicates")
                    .getAsJsonArray("minecraft:enchantments");
            JsonObject silkTouch = enchantments.get(0).getAsJsonObject();
            assertFalse(silkTouch.has("enchantment"), resourceName + " still uses the singular enchantment field");
            assertEquals("minecraft:silk_touch", silkTouch.get("enchantments").getAsString());
        }

        for (var entry : object.entrySet()) {
            assertMatchToolPredicates(entry.getValue(), resourceName, count);
        }
    }

    private static JsonObject resource(String path) throws IOException {
        var stream = Objects.requireNonNull(
                Minecraft263ResourceSchemaTest.class.getClassLoader().getResourceAsStream(path),
                "Missing test resource " + path
        );
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
