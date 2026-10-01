package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.conczin.mca.entity.ai.navigation.PathfindingBlacklist;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public class WanderOrTeleportToTargetTask extends MoveToTargetSink {
    private boolean extendedMovementLifetime;
    private BlockPos detourDestination;
    private BlockPos detourStep;
    private double detourDistance;

    @Override
    protected boolean timedOut(long gameTime) {
        return !this.extendedMovementLifetime && super.timedOut(gameTime);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Mob entity, long gameTime) {
        if (shouldYieldToEmergencyCombat(entity)) {
            return false;
        }

        boolean vanillaCanContinue = super.canStillUse(world, entity, gameTime);
        if (vanillaCanContinue) {
            return true;
        }

        WalkTarget walkTarget = entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        Path path = entity.getNavigation().getPath();
        if (walkTarget != null
                && path != null
                && entity.getNavigation().isDone()
                && !walkTargetReached(entity, walkTarget)
                && continueDetour(entity, walkTarget, path)) {
            return true;
        }
        if (walkTarget != null
                && path != null
                && entity.getNavigation().isDone()
                && !walkTargetReached(entity, walkTarget)
                && !entity.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
            if (entity instanceof VillagerEntityMCA villager) {
                WalkTargetFailureMemory.record(villager, walkTarget.getTarget().currentBlockPosition(), gameTime);
            } else {
                entity.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, gameTime);
            }
        }

        return false;
    }

    @Override
    protected void start(ServerLevel world, Mob entity, long gameTime) {
        WalkTarget walkTarget = entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        super.start(world, entity, gameTime);
        Path path = entity.getNavigation().getPath();
        // A nearby HOME can require a long detour. Keep its already-computed
        // reachable route alive instead of timing out and walking back toward HOME.
        boolean longReachableRoute = path != null && path.canReach() && path.getEndNode() != null
                && walkTarget != null && path.getTarget().equals(walkTarget.getTarget().currentBlockPosition())
                && path.getEndNode().walkedDistance > MCAGroundPathNavigation.getOrdinaryPathLength(entity);
        this.extendedMovementLifetime = walkTarget != null
                && walkTarget.getTarget() instanceof BlockPosTracker
                && (longReachableRoute || MCAGroundPathNavigation.requiresExtendedPath(
                        entity,
                        walkTarget.getTarget().currentBlockPosition()
                ));
        if (walkTarget != null
                && walkTarget.getTarget() instanceof BlockPosTracker
                && path != null
                && !entity.getNavigation().isStuck()
                && MCAGroundPathNavigation.isUsefulPartialPath(
                        path, walkTarget.getTarget().currentBlockPosition()
                )) {
            if (entity instanceof VillagerEntityMCA villager) {
                // A fresh useful segment is optimistic progress and must be able
                // to chain immediately. A previous failure episode for this same
                // destination must survive another no-movement partial attempt.
                BlockPos destination = walkTarget.getTarget().currentBlockPosition();
                boolean priorFailure = WalkTargetFailureMemory.hasFailureFor(villager, destination)
                        && villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        .filter(since -> since < gameTime).isPresent();
                if (!priorFailure) {
                    WalkTargetFailureMemory.clear(villager);
                }
            } else {
                entity.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            }
        }
    }

    @Override
    protected void stop(ServerLevel world, Mob entity, long gameTime) {
        clearDetour();
        this.extendedMovementLifetime = false;
        super.stop(world, entity, gameTime);
    }

    private boolean continueDetour(Mob entity, WalkTarget walkTarget, Path completedPath) {
        if (!(walkTarget.getTarget() instanceof BlockPosTracker)) {
            clearDetour();
            return false;
        }

        BlockPos destination = walkTarget.getTarget().currentBlockPosition();
        if (this.detourDestination != null && !this.detourDestination.equals(destination)) {
            clearDetour();
        }

        if (this.detourDestination == null) {
            if (completedPath.canReach()
                    || MCAGroundPathNavigation.isUsefulPartialPath(completedPath, destination)) {
                return false;
            }
            return beginDetour(entity, walkTarget, destination);
        }

        Path destinationPath = entity.getNavigation().createPath(destination, 0);
        if (destinationPath != null && destinationPath.canReach()) {
            clearDetour();
            clearFailure(entity);
            entity.getNavigation().moveTo(destinationPath, walkTarget.getSpeedModifier());
            return true;
        }

        if (BlockPos.ZERO.equals(this.detourStep)) {
            if (MCAGroundPathNavigation.isUsefulPartialPath(destinationPath, destination)) {
                entity.getNavigation().moveTo(destinationPath, walkTarget.getSpeedModifier());
                return true;
            }
            clearDetour();
            return false;
        }

        if (this.detourDistance >= MCAGroundPathNavigation.getOrdinaryPathLength(entity)) {
            Path crossing = tryCrossObstacle(entity, destination);
            if (crossing != null) {
                this.detourStep = BlockPos.ZERO;
                entity.getNavigation().moveTo(crossing, walkTarget.getSpeedModifier());
                return true;
            }
        }

        Path continuation = continueFlank(entity);
        if (continuation == null) {
            clearDetour();
            return false;
        }

        entity.getNavigation().moveTo(continuation, walkTarget.getSpeedModifier());
        return true;
    }

    private boolean beginDetour(Mob entity, WalkTarget walkTarget, BlockPos destination) {
        if (!hasWalkableApproach(entity, destination)) {
            return false;
        }

        BlockPos origin = entity.blockPosition();
        double dx = destination.getX() + 0.5D - entity.getX();
        double dz = destination.getZ() + 0.5D - entity.getZ();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        if (horizontalDistance < 1.0D) {
            return false;
        }

        double probeDistance = MCAGroundPathNavigation.getOrdinaryPathLength(entity) * 0.5D;
        double flankX = -dz / horizontalDistance * probeDistance;
        double flankZ = dx / horizontalDistance * probeDistance;
        int preferredSide = ((destination.getX() ^ destination.getZ() ^ entity.getId()) & 1) == 0 ? 1 : -1;
        for (int side : new int[]{preferredSide, -preferredSide}) {
            BlockPos candidate = BlockPos.containing(
                    entity.getX() + flankX * side,
                    entity.getY(),
                    entity.getZ() + flankZ * side
            );
            Path path = entity.getNavigation().createPath(candidate, 0);
            if (path != null && path.canReach() && path.getNodeCount() > 1) {
                this.detourDestination = destination.immutable();
                this.detourStep = candidate.subtract(origin);
                this.detourDistance = horizontalDistance(origin, candidate);
                this.extendedMovementLifetime = true;
                entity.getNavigation().moveTo(path, walkTarget.getSpeedModifier());
                return true;
            }
        }
        return false;
    }

    private static boolean hasWalkableApproach(Mob entity, BlockPos destination) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (WalkNodeEvaluator.getPathTypeStatic(entity, destination.relative(direction).mutable()) == PathType.WALKABLE) {
                return true;
            }
        }
        return false;
    }

    private Path continueFlank(Mob entity) {
        if (this.detourStep == null) {
            return null;
        }

        double flankX = this.detourStep.getX() * 0.5D;
        double flankZ = this.detourStep.getZ() * 0.5D;
        double stepDistance = Math.sqrt(flankX * flankX + flankZ * flankZ);
        double maxDetourDistance = Math.max(
                Config.getInstance().getVillagerPathfindingDistance(),
                MCAGroundPathNavigation.getOrdinaryPathLength(entity)
        );
        if (stepDistance < 1.0D || this.detourDistance + stepDistance > maxDetourDistance) {
            return null;
        }

        BlockPos origin = entity.blockPosition();
        BlockPos candidate = BlockPos.containing(
                entity.getX() + flankX,
                entity.getY(),
                entity.getZ() + flankZ
        );
        Path path = entity.getNavigation().createPath(candidate, 0);
        if (path == null
                || path.getNodeCount() < 2
                || (!path.canReach() && !MCAGroundPathNavigation.isUsefulPartialPath(path, candidate))
                || path.getEndNode() == null) {
            return null;
        }

        double progress = horizontalDistance(origin, path.getEndNode().asBlockPos());
        if (progress < 1.0D) {
            return null;
        }
        this.detourDistance += progress;
        return path;
    }

    private Path tryCrossObstacle(Mob entity, BlockPos destination) {
        double flankLength = horizontalDistance(BlockPos.ZERO, this.detourStep);
        if (flankLength < 1.0D) {
            return null;
        }

        double forwardX = this.detourStep.getZ() / flankLength;
        double forwardZ = -this.detourStep.getX() / flankLength;
        double destinationX = destination.getX() + 0.5D - entity.getX();
        double destinationZ = destination.getZ() + 0.5D - entity.getZ();
        if (forwardX * destinationX + forwardZ * destinationZ < 0.0D) {
            forwardX = -forwardX;
            forwardZ = -forwardZ;
        }

        double probeDistance = MCAGroundPathNavigation.getOrdinaryPathLength(entity) * 0.25D;
        BlockPos candidate = BlockPos.containing(
                entity.getX() + forwardX * probeDistance,
                entity.getY(),
                entity.getZ() + forwardZ * probeDistance
        );
        Path path = entity.getNavigation().createPath(candidate, 0);
        return path != null && path.canReach() && path.getNodeCount() > 1 ? path : null;
    }

    private static double horizontalDistance(BlockPos first, BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void clearFailure(Mob entity) {
        if (entity instanceof VillagerEntityMCA villager) {
            WalkTargetFailureMemory.clear(villager);
        } else {
            entity.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }
    }

    private void clearDetour() {
        this.detourDestination = null;
        this.detourStep = null;
        this.detourDistance = 0.0D;
    }

    private static boolean shouldYieldToEmergencyCombat(Mob entity) {
        if (RangedCombatState.current(entity).orElse(null) != RangedCombatState.EMERGENCY_FLEE) {
            return false;
        }

        WalkTarget walkTarget = entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        return walkTarget != null && !(walkTarget.getTarget() instanceof CombatEscapePositionTracker);
    }

    private static boolean walkTargetReached(Mob entity, WalkTarget walkTarget) {
        if (walkTarget.getTarget() instanceof MultiTargetPositionTracker multiTarget) {
            return multiTarget.isReached(entity, walkTarget.getCloseEnoughDist());
        }
        return walkTarget.getTarget().currentBlockPosition().distManhattan(entity.blockPosition())
                <= walkTarget.getCloseEnoughDist();
    }

    @Override
    protected void tick(ServerLevel world, Mob entity, long l) {
        if (Config.getInstance().allowVillagerTeleporting) {
            entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).ifPresent(walkTarget -> {
                BlockPos targetPos = walkTarget.getTarget().currentBlockPosition();

                // If the target is more than x blocks away, teleport to it immediately.
                if (!targetPos.closerToCenterThan(entity.position(), Config.getInstance().villagerMinTeleportationDistance)) {
                    tryTeleport(world, entity, targetPos);
                }
            });
        }

        super.tick(world, entity, l);
    }

    private void tryTeleport(ServerLevel world, Mob entity, BlockPos targetPos) {
        for (int i = 0; i < 10; ++i) {
            int j = this.getRandomInt(entity, -3, 3);
            int k = this.getRandomInt(entity, -1, 1);
            int l = this.getRandomInt(entity, -3, 3);
            boolean bl = this.tryTeleportTo(world, entity, targetPos, targetPos.getX() + j, targetPos.getY() + k, targetPos.getZ() + l);
            if (bl) {
                return;
            }
        }
    }

    private boolean tryTeleportTo(ServerLevel world, Mob entity, BlockPos targetPos, int x, int y, int z) {
        if (Math.abs((double) x - targetPos.getX()) < 2.0D && Math.abs((double) z - targetPos.getZ()) < 2.0D) {
            return false;
        } else if (!this.canTeleportTo(world, entity, new BlockPos(x, y, z))) {
            return false;
        } else {
            entity.teleportTo((double) x + 0.5D, y, (double) z + 0.5D);
            return true;
        }
    }

    private boolean canTeleportTo(ServerLevel world, Mob entity, BlockPos pos) {
        PathType pathNodeType = WalkNodeEvaluator.getPathTypeStatic(entity, pos.mutable());
        if (pathNodeType != PathType.WALKABLE) {
            return false;
        } else {
            if (!isAreaSafe(world, pos.below())) {
                return false;
            } else {
                BlockPos blockPos = pos.subtract(entity.blockPosition());
                return world.noCollision(entity, entity.getBoundingBox().move(blockPos));
            }
        }
    }

    private int getRandomInt(Mob entity, int min, int max) {
        return entity.getRandom().nextInt(max - min + 1) + min;
    }

    private boolean isAreaSafe(ServerLevel world, BlockPos pos) {
        // The following conditions define whether it is logically
        // safe for the entity to teleport to the specified pos within world
        return !PathfindingBlacklist.isBlocked(world.getBlockState(pos));
    }
}
