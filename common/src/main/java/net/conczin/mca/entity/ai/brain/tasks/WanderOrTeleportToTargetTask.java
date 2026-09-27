package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.conczin.mca.entity.ai.navigation.PathfindingBlacklist;
import net.minecraft.core.BlockPos;
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
        this.extendedMovementLifetime = walkTarget != null
                && walkTarget.getTarget() instanceof BlockPosTracker
                && MCAGroundPathNavigation.requiresExtendedPath(
                        entity,
                        walkTarget.getTarget().currentBlockPosition()
                );
        if (walkTarget != null
                && walkTarget.getTarget() instanceof BlockPosTracker
                && path != null
                && !entity.getNavigation().isStuck()
                && MCAGroundPathNavigation.isUsefulPartialPath(
                        path,
                        walkTarget.getTarget().currentBlockPosition()
                )) {
            if (entity instanceof VillagerEntityMCA villager) {
                WalkTargetFailureMemory.clear(villager);
            } else {
                entity.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            }
        }
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
        if (Config.SERVER.allowVillagerTeleporting.get()) {
            entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).ifPresent(walkTarget -> {
                BlockPos targetPos = walkTarget.getTarget().currentBlockPosition();

                // If the target is more than x blocks away, teleport to it immediately.
                if (!targetPos.closerToCenterThan(entity.position(), Config.SERVER.villagerMinTeleportationDistance.get())) {
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
