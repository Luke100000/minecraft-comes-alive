package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageMourningScheduleTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void legacyVillageLoadsWithoutScheduledMourning() {
        CompoundTag tag = new Village(1, null).save();
        tag.remove("nextMourningTime");
        tag.remove("mourningRemaining");
        tag.remove("nextMourningBurstTime");

        Village loaded = new Village(tag, null);

        assertEquals(0L, loaded.getNextMourningTime());
        assertEquals(0, loaded.getMourningRemaining());
        assertEquals(0L, loaded.getNextMourningBurstTime());
    }

    @Test
    void nextMourningFallsBetweenOneAndTwoMinecraftDays() {
        long now = 200_000L;
        long next = Village.calculateNextMourningTime(now, RandomSource.create(1234L));

        assertTrue(next >= now + 24_000L);
        assertTrue(next <= now + 48_000L);
    }

    @Test
    void nextBurstFallsBetweenTwoAndFourMinecraftHours() {
        long now = 200_000L;
        long next = Village.calculateNextMourningBurstTime(now, RandomSource.create(1234L));

        assertTrue(next >= now + 2_000L);
        assertTrue(next <= now + 4_000L);
    }

    @Test
    void sessionSizeScalesAndCaps() {
        assertEquals(3, Village.calculateMourningSessionSize(20));
        assertEquals(5, Village.calculateMourningSessionSize(50));
        assertEquals(7, Village.calculateMourningSessionSize(100));
        assertEquals(9, Village.calculateMourningSessionSize(150));
        assertEquals(12, Village.calculateMourningSessionSize(200));
        assertEquals(12, Village.calculateMourningSessionSize(1_000));
        assertEquals(12, Village.calculateMourningSessionSize(10_000));
    }

    @Test
    void burstSizeNeverExceedsRemainingBudget() {
        for (long seed = 0; seed < 20; seed++) {
            int burst = Village.calculateMourningBurstSize(8, RandomSource.create(seed));
            assertTrue(burst >= 2 && burst <= 4);
        }
        assertEquals(1, Village.calculateMourningBurstSize(1, RandomSource.create(1234L)));
    }

    @Test
    void mourningSessionStateRoundTripsThroughVillageNbtWithoutPersistingCache() {
        CompoundTag tag = new Village(1, null).save();
        tag.putLong("nextMourningTime", 345_678L);
        tag.putInt("mourningRemaining", 8);
        tag.putLong("nextMourningBurstTime", 346_789L);

        Village loaded = new Village(tag, null);
        Village reloaded = new Village(loaded.save(), null);

        assertEquals(345_678L, reloaded.getNextMourningTime());
        assertEquals(8, reloaded.getMourningRemaining());
        assertEquals(346_789L, reloaded.getNextMourningBurstTime());
        assertFalse(reloaded.save().contains("mourningGraveCache"));
        assertFalse(reloaded.save().contains("mourningGraveCacheInitialized"));
    }
}
