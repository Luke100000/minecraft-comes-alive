package net.conczin.mca.util.network.datasync;

import net.conczin.mca.FamilyTreeTestSupport;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class MutableTrackedDataTest {
    @BeforeAll
    static void bootstrap() {
        FamilyTreeTestSupport.bootstrapMinecraft();
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
}
