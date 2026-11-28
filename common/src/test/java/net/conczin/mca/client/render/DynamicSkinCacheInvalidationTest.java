package net.conczin.mca.client.render;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicSkinCacheInvalidationTest {
    @Test
    void replacingLibraryTextureEvictsDependentStitchedSkinsAndFaces() throws ReflectiveOperationException {
        Object key = skinKeyWithHair("immersive_library:42");
        Object unrelatedKey = skinKeyWithHair("immersive_library:43");
        Map<Object, ResourceLocation> skins = getField("CACHE");
        Map<Object, ResourceLocation> faces = getField("FACE_CACHE");
        Set<Object> incompleteSkins = getField("INCOMPLETE_CACHE");
        Set<Object> incompleteFaces = getField("INCOMPLETE_FACE_CACHE");
        ResourceLocation derivedSkin = ResourceLocation.fromNamespaceAndPath("test", "stitched");
        ResourceLocation derivedFace = ResourceLocation.fromNamespaceAndPath("test", "face");
        try {
            skins.put(key, derivedSkin);
            faces.put(key, derivedFace);
            skins.put(unrelatedKey, derivedSkin);
            faces.put(unrelatedKey, derivedFace);
            incompleteSkins.add(key);
            incompleteFaces.add(key);

            DynamicSkinCache.invalidateSourceTexture(ResourceLocation.fromNamespaceAndPath("immersive_library", "42"));

            assertFalse(skins.containsKey(key));
            assertFalse(faces.containsKey(key));
            assertFalse(incompleteSkins.contains(key));
            assertFalse(incompleteFaces.contains(key));
            assertTrue(skins.containsKey(unrelatedKey));
            assertTrue(faces.containsKey(unrelatedKey));
        } finally {
            skins.remove(key);
            faces.remove(key);
            skins.remove(unrelatedKey);
            faces.remove(unrelatedKey);
            incompleteSkins.remove(key);
            incompleteFaces.remove(key);
        }
    }

    private static Object skinKeyWithHair(String hair) throws ReflectiveOperationException {
        Class<?> keyType = Class.forName("net.conczin.mca.client.render.DynamicSkinCache$SkinKey");
        RecordComponent[] components = keyType.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            Class<?> type = components[i].getType();
            args[i] = type == boolean.class ? false : type == float.class ? 0.0F : type == int.class ? 0 : "";
            if (components[i].getName().equals("hair")) {
                args[i] = hair;
            }
        }
        Constructor<?> constructor = keyType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        return constructor.newInstance(args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(String name) throws ReflectiveOperationException {
        Field field = DynamicSkinCache.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(null);
    }
}
