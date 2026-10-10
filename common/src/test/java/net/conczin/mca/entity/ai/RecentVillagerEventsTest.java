package net.conczin.mca.entity.ai;

import net.conczin.mca.MCA;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RecentVillagerEventsTest {
    @Test
    void roundTripsRegisteredFactsAndIgnoresUnknownOnReadAndWrite() {
        RecentVillagerEvents events = new RecentVillagerEvents();
        events.record(RecentVillagerEvents.ATTACKED, 100L);
        events.record(MCA.locate("not_registered"), 200L);

        CompoundTag nbt = new CompoundTag();
        events.writeToNbt(nbt);
        nbt.getCompound(RecentVillagerEvents.NBT_KEY).putLong("addon:unknown", 300L);

        RecentVillagerEvents restored = new RecentVillagerEvents();
        restored.readFromNbt(nbt);

        assertTrue(restored.occurredWithin(RecentVillagerEvents.ATTACKED, 120L, 20L));
        assertFalse(restored.occurredWithin(MCA.locate("not_registered"), 200L, 100L));
        assertFalse(restored.occurredWithin(ResourceLocation.parse("addon:unknown"), 300L, 100L));
    }

    @Test
    void withinWindowIncludesExactBoundaryAndRejectsFutureOrNegativeWindows() {
        RecentVillagerEvents events = new RecentVillagerEvents();
        events.record(RecentVillagerEvents.CURED, 1_000L);

        assertTrue(events.occurredWithin(RecentVillagerEvents.CURED, 1_000L, 0L));
        assertTrue(events.occurredWithin(RecentVillagerEvents.CURED, 1_020L, 20L));
        assertFalse(events.occurredWithin(RecentVillagerEvents.CURED, 1_021L, 20L));
        assertFalse(events.occurredWithin(RecentVillagerEvents.CURED, 999L, 20L));
        assertFalse(events.occurredWithin(RecentVillagerEvents.CURED, 1_000L, -1L));
    }

    @Test
    void keepsOnlyLatestGameTimeForEachFact() {
        RecentVillagerEvents events = new RecentVillagerEvents();
        events.record(RecentVillagerEvents.REVIVED, 500L);
        events.record(RecentVillagerEvents.REVIVED, 450L);

        assertTrue(events.occurredWithin(RecentVillagerEvents.REVIVED, 510L, 10L));
        assertFalse(events.occurredWithin(RecentVillagerEvents.REVIVED, 511L, 10L));
    }

    @Test
    void recordsAOnceOccurredFactPermanentlyAcrossReloadsAndRepeatedCures() {
        RecentVillagerEvents events = new RecentVillagerEvents();
        assertFalse(events.hasOccurred(RecentVillagerEvents.CURED));
        events.record(RecentVillagerEvents.CURED, 1_000L);

        CompoundTag nbt = new CompoundTag();
        events.writeToNbt(nbt);
        RecentVillagerEvents restored = new RecentVillagerEvents();
        restored.readFromNbt(nbt);

        assertTrue(restored.hasOccurred(RecentVillagerEvents.CURED));
        assertFalse(restored.occurredWithin(RecentVillagerEvents.CURED, 50_000L, 24_000L));
        restored.record(RecentVillagerEvents.CURED, 50_000L);
        assertTrue(restored.hasOccurred(RecentVillagerEvents.CURED));
        assertTrue(restored.occurredWithin(RecentVillagerEvents.CURED, 50_000L, 0L));
        assertFalse(restored.hasOccurred(MCA.locate("not_registered")));
    }
}
