package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.schedule.Activity;
import org.jetbrains.annotations.Nullable;

/**
 * Marks a persistent static destination that opts into MCA's extended
 * navigation, retry and detour recovery. Disposable walk targets intentionally
 * remain ordinary PositionTrackers so failed strolls can be abandoned cheaply.
 */
public final class PersistentPathTarget extends BlockPosTracker {
    @Nullable
    private final Activity requiredActivity;

    public PersistentPathTarget(BlockPos target) {
        this(target, null);
    }

    public PersistentPathTarget(BlockPos target, @Nullable Activity requiredActivity) {
        super(target);
        this.requiredActivity = requiredActivity;
    }

    public boolean isInactiveFor(Mob mob) {
        return requiredActivity != null && !mob.getBrain().isActive(requiredActivity);
    }
}
