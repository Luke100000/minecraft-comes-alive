package net.conczin.mca.util.network.datasync;

import net.conczin.mca.FamilyTreeTestSupport;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
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
        // Common unit tests do not run the Fabric/NeoForge serializer registration event.
        EntityDataSerializers.registerSerializer(MCAEntityDataSerializers.COMPOUND_TAG);
        memories = CParameter.create("Memories", new CompoundTag());
        data = new CDataManager.Builder<>(TestEntity.class,
                serializer -> SynchedEntityData.defineId(TestEntity.class, serializer))
                .addAll(memories).build();
    }

    @Test
    void trackedCompoundCopiesOwnNestedTags() {
        CompoundTag original = new CompoundTag();
        CompoundTag nested = new CompoundTag();
        nested.putInt("hearts", 3);
        original.put("player", nested);

        CompoundTag first = MCAEntityDataSerializers.COMPOUND_TAG.copy(original);
        CompoundTag second = MCAEntityDataSerializers.COMPOUND_TAG.copy(original);
        assertNotSame(original, first);
        assertNotSame(first, second);

        first.getCompound("player").orElseThrow().putInt("hearts", 12);
        assertEquals(3, original.getCompound("player").orElseThrow().getInt("hearts").orElseThrow());
        assertEquals(3, second.getCompound("player").orElseThrow().getInt("hearts").orElseThrow());
    }

    @Test
    void missingPersistedCompoundsDoNotShareDefaults() {
        CDataParameter<CompoundTag> parameter = CParameter.create("Memories", new CompoundTag());
        CompoundTag first = parameter.load(new CompoundTag(), null);
        CompoundTag second = parameter.load(new CompoundTag(), null);

        assertNotSame(parameter.getDefault(), first);
        assertNotSame(first, second);
        first.putInt("hearts", 9);
        assertEquals(0, second.getInt("hearts").orElse(0));
    }

    @Test
    void savingTrackedCompoundsDetachesLiveMemory() {
        CDataParameter<CompoundTag> parameter = CParameter.create("Memories", new CompoundTag());
        CompoundTag live = new CompoundTag();
        CompoundTag nested = new CompoundTag();
        nested.putInt("hearts", 4);
        live.put("player", nested);

        CompoundTag saved = new CompoundTag();
        parameter.save(saved, live, null);
        live.getCompound("player").orElseThrow().putInt("hearts", 15);

        assertEquals(4, saved.getCompound("Memories").orElseThrow()
                .getCompound("player").orElseThrow().getInt("hearts").orElseThrow());
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
        assertEquals(42, data.get(villager, memories).getInt("hearts").orElseThrow());
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

        assertEquals(5, saved.getCompound("Memories").orElseThrow()
                .getCompound("player").orElseThrow().getInt("hearts").orElseThrow());
        assertFalse(saved.getCompound("Memories").orElseThrow().contains("later"));
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
        protected void readAdditionalSaveData(ValueInput input) {
        }

        @Override
        protected void addAdditionalSaveData(ValueOutput output) {
        }

        @Override
        public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
            return false;
        }
    }
}
