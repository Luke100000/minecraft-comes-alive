package net.conczin.mca.client.render;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicSkinCacheTest {
    @Test
    void sameIdSourceReplacementEvictsDerivedSlimTexture() throws ReflectiveOperationException {
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath("immersive_library", "42");
        ResourceLocation derived = ResourceLocation.fromNamespaceAndPath("test", "derived_slim");
        Map<ResourceLocation, ResourceLocation> cache = slimTextureCache();

        try {
            cache.put(source, derived);
            assertTrue(cache.containsKey(source));

            DynamicSkinCache.invalidateSourceTexture(source);

            assertFalse(cache.containsKey(source), "same-ID source replacement must invalidate the derived slim texture");
        } finally {
            cache.remove(source);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, ResourceLocation> slimTextureCache() throws ReflectiveOperationException {
        Field field = DynamicSkinCache.class.getDeclaredField("SLIM_TEXTURE_CACHE");
        field.setAccessible(true);
        return (Map<ResourceLocation, ResourceLocation>) field.get(null);
    }
}
