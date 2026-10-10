package net.conczin.mca.client.render;

import net.minecraft.resources.Identifier;
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
    void replacingLibraryTextureEvictsDependentStitchedSkins() throws ReflectiveOperationException {
        Object key = skinKeyWithHair("immersive_library:42");
        Object unrelatedKey = skinKeyWithHair("immersive_library:43");
        Map<Object, Identifier> skins = getField("CACHE");
        Set<Object> incompleteSkins = getField("INCOMPLETE_CACHE");
        Identifier derivedSkin = Identifier.fromNamespaceAndPath("test", "stitched");
        try {
            skins.put(key, derivedSkin);
            skins.put(unrelatedKey, derivedSkin);
            incompleteSkins.add(key);

            DynamicSkinCache.invalidateSourceTexture(Identifier.fromNamespaceAndPath("immersive_library", "42"));

            assertFalse(skins.containsKey(key));
            assertFalse(incompleteSkins.contains(key));
            assertTrue(skins.containsKey(unrelatedKey));
        } finally {
            skins.remove(key);
            skins.remove(unrelatedKey);
            incompleteSkins.remove(key);
        }
    }

    private static Object skinKeyWithHair(String hair) throws ReflectiveOperationException {
        Class<?> keyType = Class.forName("net.conczin.mca.client.render.DynamicSkinCache$SkinKey");
        RecordComponent[] components = keyType.getRecordComponents();
        Object[] args = new Object[components.length];
        for (int i = 0; i < components.length; i++) {
            Class<?> type = components[i].getType();
            args[i] = type == boolean.class ? false : type == float.class ? 0.0F : type == int.class ? 0 : "";
            if (components[i].getName().equals("hairBase")) {
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
