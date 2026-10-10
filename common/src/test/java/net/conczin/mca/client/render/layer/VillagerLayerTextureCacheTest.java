package net.conczin.mca.client.render.layer;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerLayerTextureCacheTest {
    @Test
    void reloadDropsCachedMissingTexturesAndResolvedVariants() throws ReflectiveOperationException {
        Map<Identifier, Boolean> existence = getCache("TEXTURE_EXIST_CACHE");
        Map<String, Identifier> variants = getCache("TEXTURE_CACHE");
        Identifier nowAvailable = Identifier.fromNamespaceAndPath("test", "previously_missing");
        Identifier variant = Identifier.fromNamespaceAndPath("test", "variant");
        try {
            existence.put(nowAvailable, false);
            variants.put("test:old", variant);

            VillagerLayer.clearTextureCaches();

            assertFalse(existence.containsKey(nowAvailable));
            assertFalse(variants.containsKey("test:old"));
            assertTrue(existence.containsKey(Identifier.fromNamespaceAndPath("mca", "temp")));
        } finally {
            existence.remove(nowAvailable);
            variants.remove("test:old");
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> getCache(String fieldName) throws ReflectiveOperationException {
        Field field = VillagerLayer.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Map<K, V>) field.get(null);
    }
}
