package net.conczin.mca.entity.ai.brain.tasks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

final class LongDistanceWalkTarget {
    private static final int MAX_RANDOM_POS_ATTEMPTS = 32;
    private static final int HORIZONTAL_SEARCH_RANGE = 15;
    private static final int VERTICAL_SEARCH_RANGE = 7;
    private static final double MAX_ANGLE = Math.PI * 0.5D;

    private LongDistanceWalkTarget() {
    }

    static boolean isWithinDirectPathRange(PathfinderMob mob, BlockPos target, int configuredMaxDistance) {
        double directPathRange = Math.min(configuredMaxDistance, mob.getAttributeValue(Attributes.FOLLOW_RANGE));
        return mob.blockPosition().distSqr(target) <= directPathRange * directPathRange;
    }

    static Optional<Vec3> findIntermediatePosition(PathfinderMob mob, BlockPos target, int configuredMaxDistance) {
        for (int attempt = 0; attempt < MAX_RANDOM_POS_ATTEMPTS; attempt++) {
            Vec3 candidate = DefaultRandomPos.getPosTowards(
                    mob,
                    HORIZONTAL_SEARCH_RANGE,
                    VERTICAL_SEARCH_RANGE,
                    Vec3.atBottomCenterOf(target),
                    MAX_ANGLE
            );
            if (candidate != null
                    && isWithinDirectPathRange(mob, BlockPos.containing(candidate), configuredMaxDistance)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }
}
