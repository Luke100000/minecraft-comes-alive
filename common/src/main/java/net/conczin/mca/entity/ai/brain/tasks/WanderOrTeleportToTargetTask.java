package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.conczin.mca.entity.ai.navigation.TeleportBlockBlacklist;
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
    private Detour detour;

    private enum DetourPhase { FLANKING, CROSSING }

    private static final class Detour {
        private final BlockPos destination;
        private final BlockPos step;
        private double distance;
        private DetourPhase phase = DetourPhase.FLANKING;

        private Detour(BlockPos destination, BlockPos step, double distance) {
            this.destination = destination.immutable();
            this.step = step.immutable();
            this.distance = distance;
        }
    }

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
        if (this.detour != null && !this.detour.destination.equals(destination)) {
            clearDetour();
        }

        if (this.detour == null) {
            if (completedPath.canReach()
                    || MCAGroundPathNavigation.isUsefulPartialPath(completedPath, destination)) {
                return false;
            }
            return beginDetour(entity, walkTarget, destination);
        }

        boolean routeChanged = entity.getNavigation() instanceof MCAGroundPathNavigation navigation
                && navigation.consumeRetryInvalidation(destination);
        // Short flank legs deliberately move sideways, so immediately searching
        // HOME again often just returns the route back to the obstacle's start.
        // Re-probe after enough lateral travel to attempt a crossing, or a route change.
        Path destinationPath = this.detour.phase == DetourPhase.CROSSING || routeChanged
                || this.detour.distance >= MCAGroundPathNavigation.getOrdinaryPathLength(entity)
                ? entity.getNavigation().createPath(destination, 0)
                : null;
        if (destinationPath != null && destinationPath.canReach()) {
            clearDetour();
            clearFailure(entity);
            entity.getNavigation().moveTo(destinationPath, walkTarget.getSpeedModifier());
            return true;
        }

        if (this.detour.phase == DetourPhase.CROSSING) {
            if (MCAGroundPathNavigation.isUsefulPartialPath(destinationPath, destination)) {
                entity.getNavigation().moveTo(destinationPath, walkTarget.getSpeedModifier());
                return true;
            }
            clearDetour();
            return false;
        }

        if (this.detour.distance >= MCAGroundPathNavigation.getOrdinaryPathLength(entity)) {
            Path crossing = tryCrossObstacle(entity, destination);
            if (crossing != null) {
                this.detour.phase = DetourPhase.CROSSING;
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
                this.detour = new Detour(destination, candidate.subtract(origin), horizontalDistance(origin, candidate));
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
        double flankX = this.detour.step.getX() * 0.5D;
        double flankZ = this.detour.step.getZ() * 0.5D;
        double stepDistance = Math.sqrt(flankX * flankX + flankZ * flankZ);
        double maxDetourDistance = Math.max(
                Config.getInstance().getVillagerPathfindingDistance(),
                MCAGroundPathNavigation.getOrdinaryPathLength(entity)
        );
        if (stepDistance < 1.0D || this.detour.distance + stepDistance > maxDetourDistance) {
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
        this.detour.distance += progress;
        return path;
    }

    private Path tryCrossObstacle(Mob entity, BlockPos destination) {
        double flankLength = horizontalDistance(BlockPos.ZERO, this.detour.step);
        if (flankLength < 1.0D) {
            return null;
        }

        double forwardX = this.detour.step.getZ() / flankLength;
        double forwardZ = -this.detour.step.getX() / flankLength;
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
        this.detour = null;
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

    private static void tryTeleport(ServerLevel world, Mob entity, BlockPos targetPos) {
        for (int attempt = 0; attempt < 10; attempt++) {
            int dx = entity.getRandom().nextInt(7) - 3;
            int dy = entity.getRandom().nextInt(3) - 1;
            int dz = entity.getRandom().nextInt(7) - 3;
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2) {
                continue;
            }
            BlockPos candidate = targetPos.offset(dx, dy, dz);
            if (canTeleportTo(world, entity, candidate)) {
                entity.teleportTo(candidate.getX() + 0.5D, candidate.getY(), candidate.getZ() + 0.5D);
                return;
            }
        }
    }

    private static boolean canTeleportTo(ServerLevel world, Mob entity, BlockPos pos) {
        if (WalkNodeEvaluator.getPathTypeStatic(entity, pos.mutable()) != PathType.WALKABLE
                || TeleportBlockBlacklist.isBlocked(world.getBlockState(pos.below()))) {
            return false;
        }
        return world.noCollision(entity, entity.getBoundingBox().move(pos.subtract(entity.blockPosition())));
    }
}
