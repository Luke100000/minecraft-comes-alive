package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.PositionTracker;

import java.util.Set;

/**
 * A logical movement target that can be reached through any of several equivalent
 * physical navigation endpoints.
 */
public interface MultiTargetPositionTracker extends PositionTracker {
    Set<BlockPos> getPathTargets(Mob mob);

    boolean isReached(Mob mob, int closeEnoughDistance);
}
