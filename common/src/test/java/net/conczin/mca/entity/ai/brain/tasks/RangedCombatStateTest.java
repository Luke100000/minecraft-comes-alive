package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangedCombatStateTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void rangedCombatMemoryIsRegisteredWithVillagerBrain() {
        assertTrue(MemoryModuleTypeMCA.MEMORY_MODULES.containsValue(MemoryModuleTypeMCA.RANGED_COMBAT_STATE));
        assertTrue(VillagerTasksMCA.MEMORY_TYPES.contains(MemoryModuleTypeMCA.RANGED_COMBAT_STATE));
    }

    @Test
    void onlyEmergencyFleeSuppressesRangedAttack() {
        for (RangedCombatState state : RangedCombatState.values()) {
            if (state == RangedCombatState.EMERGENCY_FLEE) {
                assertTrue(state.suppressesRangedAttack());
            } else {
                assertFalse(state.suppressesRangedAttack(), state.name());
            }
        }
    }
}
