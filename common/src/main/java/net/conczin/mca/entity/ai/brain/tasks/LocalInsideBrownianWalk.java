package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.InsideBrownianWalk;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;

import java.util.Set;

/**
 * Marks vanilla's indoor Brownian stroll as local so an adjacent step never
 * triggers MCA's extended detour search.
 */
public final class LocalInsideBrownianWalk {
    private LocalInsideBrownianWalk() {
    }

    public static BehaviorControl<PathfinderMob> create(float speedModifier) {
        BehaviorControl<PathfinderMob> vanilla = InsideBrownianWalk.create(speedModifier);
        WalkTargetRetryGate retryGate = new WalkTargetRetryGate(40L, 1.0D);
        return new BehaviorControl<>() {
            @Override
            public Set<MemoryModuleType<?>> getRequiredMemories() {
                return vanilla.getRequiredMemories();
            }

            @Override
            public Behavior.Status getStatus() {
                return vanilla.getStatus();
            }

            @Override
            public boolean tryStart(ServerLevel level, PathfinderMob mob, long gameTime) {
                BlockPos origin = mob.blockPosition();
                if (!retryGate.canReserve(origin, origin, gameTime)) {
                    PathRequestDiagnostics.recordDeferredProducerRetry(mob);
                    return false;
                }
                if (!vanilla.tryStart(level, mob, gameTime)) {
                    return false;
                }
                // Vanilla may reject before scanning (an existing target or open sky).
                // Only an accepted attempt consumes this producer's retry allowance.
                retryGate.tryReserve(origin, origin, gameTime);
                mob.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).ifPresent(target -> {
                    if (target.getTarget() instanceof BlockPosTracker) {
                        mob.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                                new WalkTarget(new BrownianTarget(target.getTarget().currentBlockPosition()),
                                        target.getSpeedModifier(), target.getCloseEnoughDist()));
                    }
                });
                return true;
            }

            @Override
            public void tickOrStop(ServerLevel level, PathfinderMob mob, long gameTime) {
                vanilla.tickOrStop(level, mob, gameTime);
            }

            @Override
            public void doStop(ServerLevel level, PathfinderMob mob, long gameTime) {
                vanilla.doStop(level, mob, gameTime);
            }

            @Override
            public String debugString() {
                return vanilla.debugString();
            }
        };
    }

    public static final class BrownianTarget extends BlockPosTracker {
        public BrownianTarget(BlockPos position) {
            super(position);
        }
    }
}
