package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

public enum RangedCombatState {
    HOLD,
    STRAFE,
    APPROACH,
    REPOSITION,
    KITE,
    EMERGENCY_FLEE;

    public boolean suppressesRangedAttack() {
        return this == EMERGENCY_FLEE;
    }

    public static Optional<RangedCombatState> current(LivingEntity entity) {
        Optional<RangedCombatState> memory = entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.RANGED_COMBAT_STATE);
        return memory == null ? Optional.empty() : memory;
    }
}
