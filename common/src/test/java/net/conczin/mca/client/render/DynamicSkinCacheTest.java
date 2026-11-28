package net.conczin.mca.client.render;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void invalidatingSourceCancelsPendingSlimConversion() throws ReflectiveOperationException {
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath("immersive_library", "42");
        Map<ResourceLocation, Object> pending = pendingSlimTextures();
        Object request = new Object();

        try {
            pending.put(source, request);
            DynamicSkinCache.invalidateSourceTexture(source);
            assertFalse(pending.containsKey(source), "A replaced source must not publish an old conversion");
        } finally {
            pending.remove(source);
        }
    }

    @Test
    void clearingCachesCancelsPendingSlimConversions() throws ReflectiveOperationException {
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath("test", "pending_slim");
        Map<ResourceLocation, Object> pending = pendingSlimTextures();

        try {
            pending.put(source, new Object());
            DynamicSkinCache.clear();
            assertFalse(pending.containsKey(source), "A resource reload must discard pending conversions");
        } finally {
            pending.remove(source);
        }
    }

    @Test
    void invalidationAllowsRetryAfterFailedSlimConversion() throws ReflectiveOperationException {
        ResourceLocation source = ResourceLocation.fromNamespaceAndPath("immersive_library", "42");
        Set<ResourceLocation> failed = failedSlimTextures();

        try {
            failed.add(source);
            DynamicSkinCache.invalidateSourceTexture(source);
            assertFalse(failed.contains(source), "A replaced source should be eligible for conversion again");
        } finally {
            failed.remove(source);
        }
    }

    @Test
    void failedSlimConversionHistoryIsBounded() throws ReflectiveOperationException {
        Set<ResourceLocation> failed = failedSlimTextures();
        Set<ResourceLocation> previous = new HashSet<>(failed);
        try {
            failed.clear();
            ResourceLocation oldest = ResourceLocation.fromNamespaceAndPath("test", "failed_0");
            for (int i = 0; i < 129; i++) {
                failed.add(ResourceLocation.fromNamespaceAndPath("test", "failed_" + i));
            }
            assertEquals(128, failed.size(), "Failed lookups must not accumulate without a limit");
            assertFalse(failed.contains(oldest), "Older failures must be eligible for retry after eviction");
        } finally {
            failed.clear();
            failed.addAll(previous);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, ResourceLocation> slimTextureCache() throws ReflectiveOperationException {
        Field field = DynamicSkinCache.class.getDeclaredField("SLIM_TEXTURE_CACHE");
        field.setAccessible(true);
        return (Map<ResourceLocation, ResourceLocation>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, Object> pendingSlimTextures() throws ReflectiveOperationException {
        Field field = DynamicSkinCache.class.getDeclaredField("SLIM_TEXTURE_PENDING");
        field.setAccessible(true);
        return (Map<ResourceLocation, Object>) field.get(null);
    }

    @SuppressWarnings("unchecked")
    private static Set<ResourceLocation> failedSlimTextures() throws ReflectiveOperationException {
        Field field = DynamicSkinCache.class.getDeclaredField("SLIM_TEXTURE_FAILED");
        field.setAccessible(true);
        return (Set<ResourceLocation>) field.get(null);
    }
}
