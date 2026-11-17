package net.conczin.mca.client.render.layer;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerLayerTextureCacheTest {
    @Test
    void reloadDropsCachedMissingTexturesAndResolvedVariants() throws ReflectiveOperationException {
        Map<ResourceLocation, Boolean> existence = getCache("TEXTURE_EXIST_CACHE");
        Map<String, ResourceLocation> variants = getCache("TEXTURE_CACHE");
        ResourceLocation nowAvailable = ResourceLocation.fromNamespaceAndPath("test", "previously_missing");
        ResourceLocation variant = ResourceLocation.fromNamespaceAndPath("test", "variant");
        try {
            existence.put(nowAvailable, false);
            variants.put("test:old", variant);

            VillagerLayer.clearTextureCaches();

            assertFalse(existence.containsKey(nowAvailable));
            assertFalse(variants.containsKey("test:old"));
            assertTrue(existence.containsKey(ResourceLocation.fromNamespaceAndPath("mca", "temp")));
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
