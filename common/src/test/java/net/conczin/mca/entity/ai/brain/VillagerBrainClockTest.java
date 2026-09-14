package net.conczin.mca.entity.ai.brain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VillagerBrainClockTest {
    @Test
    void newMemoriesUseOverworldClockInsteadOfGameTime() {
        long gameTime = 24000L * 30L;
        long overworldClockTime = 24000L * 7L;

        assertEquals(overworldClockTime, VillagerBrain.memoryClockTime(gameTime, overworldClockTime));
    }
}
