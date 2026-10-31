package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.schedule.Activity;

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

    public boolean permitsAiming(Mob entity) {
        // Navigation owns movement yaw and jumping independently of bow use.
        // Ordinary steps must not reset a draw or switch combat-facing policy.
        return permitsBowDrawContinuation() && !entity.getBrain().isActive(Activity.PANIC);
    }

    public static Optional<RangedCombatState> current(LivingEntity entity) {
        return entity.getBrain().getMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE);
    }
}
