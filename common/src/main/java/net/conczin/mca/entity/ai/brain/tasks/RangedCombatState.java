package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.world.entity.LivingEntity;

import java.util.Optional;

public enum RangedCombatState {
    HOLD,
    STRAFE,
    APPROACH,
    REPOSITION,
    RECOVER,
    KITE,
    EMERGENCY_FLEE;

    public boolean suppressesRangedAttack() {
        return !permitsDrawing();
    }

    public boolean permitsDrawing() {
        return this != EMERGENCY_FLEE && this != REPOSITION && this != RECOVER;
    }

    public boolean permitsBowDrawContinuation() {
        return this != EMERGENCY_FLEE && this != RECOVER;
    }

    public static Optional<RangedCombatState> current(LivingEntity entity) {
        return entity.getBrain().getMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE);
    }
}
