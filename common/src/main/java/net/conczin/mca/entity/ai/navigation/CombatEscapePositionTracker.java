package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Marks a multi-target movement intent that already belongs to ranged-combat escape.
 * Generic movement may yield foreign navigation during an emergency without stopping
 * the escape path that replaced it.
 */
public interface CombatEscapePositionTracker extends MultiTargetPositionTracker {
    @Override
    @Nullable
    default Path createPath(PathNavigation navigation, Mob mob, int reachRange) {
        Path partial = null;
        Path emergency = null;
        int attempts = 0;
        for (Set<BlockPos> tier : getRankedPathTargets(mob)) {
            if (tier.isEmpty() || attempts >= 12) {
                continue;
            }
            attempts++;
            Path candidate = navigation.createPath(tier, reachRange);
            if (candidate == null) {
                continue;
            }
            if (!isPathSafe(mob, candidate)) {
                if (emergency == null && candidate.canReach()
                        && isDegradedEmergencyPathSafe(mob, candidate)) {
                    emergency = candidate;
                }
                continue;
            }
            if (candidate.canReach()) {
                return candidate;
            }
            if (partial == null) {
                partial = candidate;
            }
        }
        return emergency != null ? emergency : partial;
    }

    default List<Set<BlockPos>> getRankedPathTargets(Mob mob) {
        return List.of(getPathTargets(mob));
    }

    default boolean isPathSafe(Mob mob, Path path) {
        return true;
    }

    /** Only combat emergency planners may allow a path with degraded clearance. */
    default boolean isDegradedEmergencyPathSafe(Mob mob, Path path) {
        return false;
    }
}
