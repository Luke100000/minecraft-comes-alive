package net.conczin.mca.util;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ExtensibleTypeRegistryTest {
    @Test
    void stringLookupMatchesParsingForLocalForeignAndMinecraftNames() {
        ExtensibleTypeRegistry<Object> registry = new ExtensibleTypeRegistry<>("mca", "test");
        for (String name : List.of("mca:rainbow", "addon:rainbow", "minecraft:rainbow", "mca:", "minecraft:")) {
            registry.register(ResourceLocation.parse(name), id -> new Object());
        }

        for (String name : List.of("rainbow", "mca:rainbow", "MCA:RAINBOW", "addon:rainbow",
                "ADDON:RAINBOW", ":rainbow", "minecraft:rainbow", "", ":", "mca:", "minecraft:",
                "unknown", "unknown:rainbow", "bad name", "mca:bad:path", "mca:../rainbow")) {
            assertEquals(registry.parse(name).flatMap(registry::get), registry.get(name), name);
        }
        assertTrue(registry.get((String) null).isEmpty());
    }

    @Test
    void registrationMakesEarlierMissesVisibleThroughEveryAlias() {
        ExtensibleTypeRegistry<Object> registry = new ExtensibleTypeRegistry<>("mca", "test");
        assertTrue(registry.get("new_trait").isEmpty());
        assertTrue(registry.get("mca:new_trait").isEmpty());

        Object value = registry.register(ResourceLocation.parse("mca:new_trait"), id -> new Object());

        assertSame(value, registry.get("new_trait").orElseThrow());
        assertSame(value, registry.get("MCA:NEW_TRAIT").orElseThrow());
        assertThrows(IllegalArgumentException.class,
                () -> registry.register(ResourceLocation.parse("mca:new_trait"), id -> new Object()));
        assertEquals(List.of(value), registry.all());
    }

    @Test
    void normalisationDoesNotDependOnTheDefaultLocale() {
        ExtensibleTypeRegistry<Object> registry = new ExtensibleTypeRegistry<>("mca", "test");
        Object value = registry.register(ResourceLocation.parse("mca:sirben"), id -> new Object());
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertSame(value, registry.get("SIRBEN").orElseThrow());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void completedConcurrentRegistrationsPublishBothLookupForms() {
        ExtensibleTypeRegistry<Object> registry = new ExtensibleTypeRegistry<>("mca", "test");
        List<CompletableFuture<Void>> registrations = java.util.stream.IntStream.range(0, 32)
                .mapToObj(index -> CompletableFuture.runAsync(() -> {
                    String name = "trait_" + index;
                    Object value = registry.register(ResourceLocation.parse("mca:" + name), id -> new Object());
                    assertSame(value, registry.get(name).orElseThrow());
                    assertSame(value, registry.get("mca:" + name).orElseThrow());
                }))
                .toList();

        CompletableFuture.allOf(registrations.toArray(CompletableFuture[]::new)).join();
        assertEquals(32, registry.size());
    }
}
