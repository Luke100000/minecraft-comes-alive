package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.ArrayList;
import java.util.List;

/**
 * Selects clear indoor standing positions, retaining bounded local strolls
 * and their retry cadence without choosing beds or foliage as destinations.
 */
public final class LocalInsideBrownianWalk {
    private LocalInsideBrownianWalk() {
    }

    public static BehaviorControl<PathfinderMob> create(float speedModifier) {
        WalkTargetRetryGate retryGate = new WalkTargetRetryGate(40L, 1.0D);
        return BehaviorBuilder.create(context -> context.group(context.absent(MemoryModuleType.WALK_TARGET))
                .apply(context, walkTarget -> (level, mob, gameTime) -> {
                    BlockPos origin = mob.blockPosition();
                    if (level.canSeeSky(origin)) {
                        return false;
                    }
                    if (!retryGate.tryReserve(origin, origin, gameTime)) {
                        PathRequestDiagnostics.recordDeferredProducerRetry(mob);
                        return false;
                    }

                    List<BlockPos> candidates = new ArrayList<>();
                    for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1))) {
                        // These are feet positions, not the supporting blocks that
                        // ground navigation can relocate onto a bed or up a wall.
                        if (!pos.equals(origin) && !level.canSeeSky(pos)
                                && !BedPoiCompatibility.isCompatibleBedState(level.getBlockState(pos))
                                && !BedPoiCompatibility.isCompatibleBedState(level.getBlockState(pos.below()))
                                && EnterBuildingTask.hasStandingSpace(level, mob, pos)) {
                            candidates.add(pos.immutable());
                        }
                    }
                    if (!candidates.isEmpty()) {
                        walkTarget.set(new WalkTarget(candidates.get(level.getRandom().nextInt(candidates.size())),
                                speedModifier, 0));
                    }
                    return true;
                }));
    }
}
