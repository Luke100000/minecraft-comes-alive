package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;

/**
 * Marks a persistent static destination that opts into MCA's extended
 * navigation, retry and detour recovery. Disposable walk targets intentionally
 * remain ordinary PositionTrackers so failed strolls can be abandoned cheaply.
 */
public final class PersistentPathTarget extends BlockPosTracker {
    public PersistentPathTarget(BlockPos target) {
        super(target);
    }
}
