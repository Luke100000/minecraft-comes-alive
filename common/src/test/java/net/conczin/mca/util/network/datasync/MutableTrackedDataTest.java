package net.conczin.mca.util.network.datasync;

import net.conczin.mca.FamilyTreeTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MutableTrackedDataTest {
    private static CDataParameter<CompoundTag> memories;
    private static CDataManager<TestEntity> data;

    @BeforeAll
    static void bootstrap() {
        FamilyTreeTestSupport.bootstrapMinecraft();
        memories = CParameter.create("Memories", new CompoundTag());
        data = new CDataManager.Builder<>(TestEntity.class).addAll(memories).build();
    }

    @Test
    void newlyRegisteredEntitiesOwnIndependentMutableDefaults() {
        TestEntity first = new TestEntity();
        TestEntity second = new TestEntity();
        CompoundTag firstMemories = data.get(first, memories);
        CompoundTag secondMemories = data.get(second, memories);

        assertNotSame(firstMemories, secondMemories);
        firstMemories.putInt("relationship", 17);

        assertFalse(secondMemories.contains("relationship"));
        assertFalse(memories.getDefault().contains("relationship"));
    }

    @Test
    void replacingTrackedMemoriesMarksTheUpdatedEntityDirty() {
        TestEntity villager = new TestEntity();
        CompoundTag previous = data.get(villager, memories);
        CompoundTag updated = previous.copy();
        updated.putInt("hearts", 42);

        data.set(villager, memories, updated);

        assertFalse(previous.contains("hearts"));
        assertTrue(villager.getEntityData().isDirty());
        assertEquals(42, data.get(villager, memories).getInt("hearts"));
    }

    @Test
    void savedMemoriesDoNotRetainTheLiveTrackedTag() {
        CompoundTag live = new CompoundTag();
        CompoundTag relationship = new CompoundTag();
        relationship.putInt("hearts", 5);
        live.put("player", relationship);

        CompoundTag saved = new CompoundTag();
        memories.save(saved, live, null);
        relationship.putInt("hearts", 20);
        live.putString("later", "changed");

        assertEquals(5, saved.getCompound("Memories").getCompound("player").getInt("hearts"));
        assertFalse(saved.getCompound("Memories").contains("later"));
    }

    private static final class TestEntity extends Entity {
        private TestEntity() {
            super(EntityType.PIG, null);
        }

        @Override
        protected void defineSynchedData(SynchedEntityData.Builder builder) {
            data.register(builder);
        }

        @Override
        protected void readAdditionalSaveData(CompoundTag tag) {
        }

        @Override
        protected void addAdditionalSaveData(CompoundTag tag) {
        }
    }
}
