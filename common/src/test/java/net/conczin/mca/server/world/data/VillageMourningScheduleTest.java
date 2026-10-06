package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

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
    }

    @Test
    void nextMourningRunsBetweenThreeMinutesTwentySecondsAndSevenMinutesThirtySecondsLater() {
        long now = 200_000L;
        long first = Long.MIN_VALUE;
        boolean sawDifferentDelay = false;

        for (long seed = 0; seed < 20; seed++) {
            long next = Village.calculateNextMourningTime(now, RandomSource.create(seed));
            assertTrue(next >= now + 4_000L);
            assertTrue(next <= now + 9_000L);
            if (first == Long.MIN_VALUE) {
                first = next;
            } else if (next != first) {
                sawDifferentDelay = true;
            }
        }

        assertTrue(sawDifferentDelay);
    }

    @Test
    void deferredMourningRetriesFourMinutesLater() {
        long now = 200_000L;
        assertEquals(now + 4_800L, Village.calculateMourningRetryTime(now));
    }

    @Test
    void burstSizeAlwaysStaysBetweenTwoAndFour() {
        for (long seed = 0; seed < 20; seed++) {
            int burst = Village.calculateMourningBurstSize(RandomSource.create(seed));
            assertTrue(burst >= 2 && burst <= 4);
        }
    }

    @Test
    void ambientSafetyStopsAfterFourChecksWhenAnInitialGraveIsSafe() {
        List<BlockPos> graves = IntStream.range(0, 10)
                .mapToObj(index -> new BlockPos(index, 64, 0))
                .toList();
        AtomicInteger checked = new AtomicInteger();

        List<BlockPos> safe = Village.selectSafeMourningGraves(graves, RandomSource.create(1234L), grave -> {
            checked.incrementAndGet();
            return true;
        });

        assertEquals(4, checked.get());
        assertEquals(4, safe.size());
        assertEquals(4, safe.stream().distinct().count());
        assertTrue(graves.containsAll(safe));
    }

    @Test
    void ambientSafetyFallsBackPastFourUnsafeGravesAndStopsAtFirstSafeOne() {
        List<BlockPos> graves = IntStream.range(0, 10)
                .mapToObj(index -> new BlockPos(index, 64, 0))
                .toList();
        List<BlockPos> shuffled = new ArrayList<>(graves);
        Util.shuffle(shuffled, RandomSource.create(1234L));
        BlockPos safeGrave = shuffled.get(5);
        AtomicInteger checked = new AtomicInteger();

        List<BlockPos> safe = Village.selectSafeMourningGraves(graves, RandomSource.create(1234L), grave -> {
            checked.incrementAndGet();
            return grave.equals(safeGrave);
        });

        assertEquals(List.of(safeGrave), safe);
        assertEquals(6, checked.get());
    }

    @Test
    void ambientSafetyChecksAllGravesBeforeDeferringWhenAllAreUnsafe() {
        List<BlockPos> graves = IntStream.range(0, 10)
                .mapToObj(index -> new BlockPos(index, 64, 0))
                .toList();
        AtomicInteger checked = new AtomicInteger();

        List<BlockPos> safe = Village.selectSafeMourningGraves(graves, RandomSource.create(1234L), grave -> {
            checked.incrementAndGet();
            return false;
        });

        assertTrue(safe.isEmpty());
        assertEquals(graves.size(), checked.get());
    }

    @Test
    void ambientMourningOnlyRunsDuringDaytimeWindow() {
        assertFalse(Village.isAmbientMourningTime(0L));
        assertTrue(Village.isAmbientMourningTime(1_000L));
        assertTrue(Village.isAmbientMourningTime(6_000L));
        assertTrue(Village.isAmbientMourningTime(11_000L));
        assertFalse(Village.isAmbientMourningTime(12_000L));
        assertFalse(Village.isAmbientMourningTime(18_000L));
        assertFalse(Village.isAmbientMourningTime(23_999L));
        assertTrue(Village.isAmbientMourningTime(25_000L));
    }

    @Test
    void onlyNextMourningTimePersists() {
        CompoundTag tag = new Village(1, null).save();
        tag.putLong("nextMourningTime", 345_678L);
        tag.putInt("mourningRemaining", 8);
        tag.putLong("nextMourningBurstTime", 346_789L);

        Village loaded = new Village(tag, null);
        Village reloaded = new Village(loaded.save(), null);

        assertEquals(345_678L, reloaded.getNextMourningTime());
        assertFalse(reloaded.save().contains("mourningRemaining"));
        assertFalse(reloaded.save().contains("nextMourningBurstTime"));
        assertFalse(reloaded.save().contains("mourningGraveCache"));
        assertFalse(reloaded.save().contains("mourningGraveCacheInitialized"));
    }
}
