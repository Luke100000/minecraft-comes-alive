package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;

/**
 * Marks a real destination that should use MCA's extended path horizon without
 * replacing it with an artificial intermediate waypoint.
 */
public final class LongDistancePathTarget extends BlockPosTracker {
    private final int requestedPathLength;

    public LongDistancePathTarget(BlockPos target, int requestedPathLength) {
        super(target);
        this.requestedPathLength = requestedPathLength;
    }

    public int requestedPathLength() {
        return requestedPathLength;
    }

    public static boolean isNeeded(Mob mob, BlockPos target) {
        double normalPathRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        return mob.blockPosition().distSqr(target) > normalPathRange * normalPathRange;
    }
}
