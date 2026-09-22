package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.Config;
import net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.conczin.mca.entity.ai.navigation.PathfindingBlacklist;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public class WanderOrTeleportToTargetTask extends MoveToTargetSink {
    private static final int FAILED_PATH_RETRY_INTERVAL = 7;
    private static final long UNREACHABLE_PATH_RETRY_TICKS = 20L;
    private int failedPathRetryCooldown;

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Mob entity) {
        if (this.failedPathRetryCooldown > 0) {
            this.failedPathRetryCooldown--;
            return false;
        }

        boolean canStart = super.checkExtraStartConditions(world, entity);
        if (!canStart && entity.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
            this.failedPathRetryCooldown = FAILED_PATH_RETRY_INTERVAL - 1;
        }
        return canStart;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Mob entity, long gameTime) {
        if (shouldYieldToEmergencyCombat(entity)) {
            return false;
        }

        boolean vanillaCanContinue = super.canStillUse(world, entity, gameTime);
        WalkTarget walkTarget = entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        if (vanillaCanContinue
                || walkTarget == null
                || entity.getNavigation().getPath() == null
                || !entity.getNavigation().isDone()
                || walkTargetReached(entity, walkTarget)) {
            return vanillaCanContinue;
        }

        long unreachableSince = entity.getBrain()
                .getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                .orElseGet(() -> {
                    entity.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, gameTime);
                    return gameTime;
                });
        if (gameTime - unreachableSince > UNREACHABLE_PATH_RETRY_TICKS) {
            return false;
        }

        // Navigation can be "done" while the entity is still outside the logical WalkTarget. Keep this owner alive
        // only for the same short retry window so the CANT_REACH timestamp survives long enough for the destination
        // producer to make the terminal decision.
        return true;
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
