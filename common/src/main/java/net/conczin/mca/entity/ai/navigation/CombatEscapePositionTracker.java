package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.Path;

import java.util.List;
import java.util.Set;

/**
 * Marks a multi-target movement intent that already belongs to ranged-combat escape.
 * Generic movement may yield foreign navigation during an emergency without stopping
 * the escape path that replaced it.
 */
public interface CombatEscapePositionTracker extends MultiTargetPositionTracker {
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
