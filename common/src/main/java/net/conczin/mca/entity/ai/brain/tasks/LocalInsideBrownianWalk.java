package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Selects short walks within the same resolved room used for shelter entry.
 */
public final class LocalInsideBrownianWalk {
    private LocalInsideBrownianWalk() {
    }

    public static BehaviorControl<PathfinderMob> create(float speedModifier) {
        WalkTargetRetryGate retryGate = new WalkTargetRetryGate(40L, 1.0D);
        return BehaviorBuilder.create(context -> context.group(context.absent(MemoryModuleType.WALK_TARGET))
                .apply(context, walkTarget -> (level, mob, gameTime) -> {
                    BlockPos origin = mob.blockPosition();
                    if (!(mob instanceof VillagerEntityMCA villager)) {
                        return false;
                    }
                    if (!retryGate.canReserve(origin, origin, gameTime)) {
                        PathRequestDiagnostics.recordDeferredProducerRetry(mob);
                        return false;
                    }
                    var room = SeekIndoorShelterTask.currentRoom(villager);
                    if (room.isEmpty()) return false;
                    List<BlockPos> candidates = new ArrayList<>();
                    for (BlockPos pos : room.orElseThrow().floorCells()) {
                        if (!pos.equals(origin) && pos.distSqr(origin) <= 16.0D
                                && EnterBuildingTask.isUsableFloor(level, mob, pos)) {
                            candidates.add(pos.immutable());
                        }
                    }
                    if (candidates.stream().anyMatch(pos -> pos.distSqr(origin) >= 4.0D)) {
                        // Prefer a short walk across the room, but keep small
                        // steps when furniture leaves only nearby floor space.
                        candidates.removeIf(pos -> pos.distSqr(origin) < 4.0D);
                    }
                    if (candidates.isEmpty()) return false;

                    BlockPos preferred = candidates.get(level.getRandom().nextInt(candidates.size()));
                    walkTarget.set(new WalkTarget(new RoomStrollTarget(preferred, Set.copyOf(candidates)), speedModifier, 0));
                    retryGate.tryReserve(origin, origin, gameTime);
                    return true;
                }));
    }

    /** Movement owns path selection; the producer only supplies equivalent room-local endpoints. */
    private static final class RoomStrollTarget implements MultiTargetPositionTracker {
        private final BlockPos preferred;
        private final Set<BlockPos> targets;

        private RoomStrollTarget(BlockPos preferred, Set<BlockPos> targets) {
            this.preferred = preferred.immutable();
            this.targets = targets;
        }

        @Override
        public Vec3 currentPosition() {
            return Vec3.atBottomCenterOf(preferred);
        }

        @Override
        public BlockPos currentBlockPosition() {
            return preferred;
        }

        @Override
        public boolean isVisibleBy(LivingEntity livingEntity) {
            return true;
        }

        @Override
        public Set<BlockPos> getPathTargets(Mob mob) {
            return targets;
        }

        @Override
        public boolean isReached(Mob mob, int closeEnoughDistance) {
            return targets.stream().anyMatch(target -> target.distManhattan(mob.blockPosition()) <= closeEnoughDistance);
        }
    }
}
