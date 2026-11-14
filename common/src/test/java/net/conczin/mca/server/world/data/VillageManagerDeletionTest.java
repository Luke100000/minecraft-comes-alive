package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VillageManagerDeletionTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void successfulRemovalMarksSavedDataDirtyAndExcludesOnlyRemovedVillage() throws ReflectiveOperationException {
        VillageManager manager = new VillageManager(null);
        villages(manager).put(10, new Village(10, null));
        villages(manager).put(20, new Village(20, null));
        manager.setDirty(false);

        assertTrue(manager.removeVillage(10));
        assertTrue(manager.isDirty(), "removing a persisted village must mark SavedData dirty");
        assertTrue(manager.getOrEmpty(10).isEmpty());
        assertTrue(manager.getOrEmpty(20).isPresent());
        CompoundTag saved = manager.save(new CompoundTag(), null);
        var list = saved.getList("villages", Tag.TAG_COMPOUND);
        assertEquals(1, list.size(), "only the retained village may remain in serialized data");
        assertEquals(20, list.getCompound(0).getInt("id"));
    }

    @Test
    void missingIdDoesNotDirtyOrMutateVillageData() throws ReflectiveOperationException {
        VillageManager manager = new VillageManager(null);
        villages(manager).put(20, new Village(20, null));
        CompoundTag before = manager.save(new CompoundTag(), null);
        manager.setDirty(false);

        assertFalse(manager.removeVillage(10));
        assertFalse(manager.isDirty(), "removing a nonexistent village must keep saved data clean");
        assertEquals(before, manager.save(new CompoundTag(), null));
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, Village> villages(VillageManager manager) throws ReflectiveOperationException {
        Field villages = VillageManager.class.getDeclaredField("villages");
        villages.setAccessible(true);
        return (Map<Integer, Village>) villages.get(manager);
    }
}
