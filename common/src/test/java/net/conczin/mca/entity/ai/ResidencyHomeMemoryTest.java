package net.conczin.mca.entity.ai;

import com.google.common.collect.ImmutableList;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ResidencyHomeMemoryTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void clearingHomeAlsoClearsForcedHomeMarker() {
        Brain<VillagerEntityMCA> brain = new Brain<>(
                List.of(MemoryModuleType.HOME, MemoryModuleTypeMCA.FORCED_HOME),
                List.of(),
                ImmutableList.of(),
                () -> null
        );
        brain.setMemory(MemoryModuleType.HOME, GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO));
        brain.setMemory(MemoryModuleTypeMCA.FORCED_HOME, true);

        Residency.clearHomeMemories(brain);

        assertFalse(brain.hasMemoryValue(MemoryModuleType.HOME));
        assertFalse(brain.hasMemoryValue(MemoryModuleTypeMCA.FORCED_HOME));
    }
}
