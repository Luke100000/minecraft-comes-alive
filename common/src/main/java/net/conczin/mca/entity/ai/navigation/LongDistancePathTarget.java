package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;

/**
 * Marks a long-distance destination owned by ExtendedWalkTowardsTask so that
 * task can distinguish its own walk target from targets published by other AI.
 * Path horizon selection itself belongs to MCAGroundPathNavigation.
 */
public final class LongDistancePathTarget extends BlockPosTracker {
    public LongDistancePathTarget(BlockPos target) {
        super(target);
    }
}
