package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * A logical movement target that can be reached through any of several equivalent
 * physical navigation endpoints.
 */
public interface MultiTargetPositionTracker extends PositionTracker {
    Set<BlockPos> getPathTargets(Mob mob);

    boolean isReached(Mob mob, int closeEnoughDistance);

    /** Shared search entry point for initial movement and native route recomputation. */
    @Nullable
    default Path createPath(PathNavigation navigation, Mob mob, int reachRange) {
        Set<BlockPos> targets = getPathTargets(mob);
        return targets.isEmpty() ? null : navigation.createPath(targets, reachRange);
    }

    /** Shared arrival rule for semantic targets and ordinary vanilla position trackers. */
    static boolean isReached(Mob mob, PositionTracker target, int closeEnoughDistance) {
        if (target instanceof MultiTargetPositionTracker multiTarget) {
            return multiTarget.isReached(mob, closeEnoughDistance);
        }
        return target.currentBlockPosition().distManhattan(mob.blockPosition()) <= closeEnoughDistance;
    }
}
