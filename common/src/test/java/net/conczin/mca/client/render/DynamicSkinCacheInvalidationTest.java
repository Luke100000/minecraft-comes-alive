package net.conczin.mca.client.render;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicSkinCacheInvalidationTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void appearanceCacheChangesForLocalState() throws ReflectiveOperationException {
        Object matching = keyFromVillager(0xFF6699BE, 0xFF6699BE);
        Object heterochromia = keyFromVillager(0xFF6699BE, 0xFF70975D);
        assertNotEquals(matching, heterochromia);
        assertEquals(0xFF6699BE, eyeColor(heterochromia, "rightNaturalEyeColor"));
        assertEquals(0xFF70975D, eyeColor(heterochromia, "leftNaturalEyeColor"));
    }

    private static Object keyFromVillager(int right, int left) throws ReflectiveOperationException {
        Genetics genetics = new Genetics(null) {
            @Override public Gender getGender() { return Gender.MALE; }
            @Override public float getGene(GeneType type) { return 0.0F; }
            @Override public int getNaturalEyeColor(boolean isLeft) { return isLeft ? left : right; }
        };
        Traits traits = new Traits(null) {
            @Override public boolean hasTrait(Trait trait) { return false; }
        };
        VillagerLike<?> villager = (VillagerLike<?>) Proxy.newProxyInstance(
                VillagerLike.class.getClassLoader(), new Class<?>[]{VillagerLike.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getGenetics")) return genetics;
                    if (method.getName().equals("getTraits")) return traits;
                    Class<?> type = method.getReturnType();
                    if (type == String.class) return "";
                    if (type == ResourceLocation.class) return ResourceLocation.parse("mca:skins/face/normal/0.png");
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == float.class) return 0.0F;
                    throw new IllegalStateException("Unexpected cache-key getter: " + method.getName());
                });
        Class<?> keyType = Class.forName("net.conczin.mca.client.render.DynamicSkinCache$SkinKey");
        var from = keyType.getDeclaredMethod("from", Entity.class, VillagerLike.class);
        from.setAccessible(true);
        return from.invoke(null, null, villager);
    }

    private static Object eyeColor(Object key, String field) throws ReflectiveOperationException {
        for (RecordComponent component : key.getClass().getRecordComponents()) {
            if (component.getName().equals(field)) {
                var accessor = component.getAccessor();
                accessor.setAccessible(true);
                return accessor.invoke(key);
            }
        }
        throw new NoSuchFieldException(field);
    }

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
            args[i] = type == boolean.class ? false
                    : type == float.class ? 0.0F
                    : type == int.class ? 0
                    : type == ResourceLocation.class ? ResourceLocation.fromNamespaceAndPath("mca", "skins/face/normal/0.png")
                    : "";
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
