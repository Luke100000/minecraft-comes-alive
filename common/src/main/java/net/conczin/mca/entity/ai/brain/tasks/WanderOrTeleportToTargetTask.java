package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.conczin.mca.entity.ai.navigation.PersistentPathTarget;
import net.conczin.mca.entity.ai.navigation.TeleportBlockBlacklist;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

public class WanderOrTeleportToTargetTask extends MoveToTargetSink {
    private Mob movingEntity;

    @Override
    protected boolean timedOut(long gameTime) {
        if (!super.timedOut(gameTime)) {
            return false;
        }
        if (this.movingEntity == null) {
            return true;
        }
        // Behavior.tickOrStop checks timedOut before canStillUse and supplies no
        // entity here. Read the running mob's current journey intent: nearby targets
        // can grow into long detours, and producers can replace ordinary/persistent
        // intent without moving far enough for MoveToTargetSink to restart.
        // Initial distance/path length must never determine persistent lifetime.
        // Arrival, target removal, emergency combat and navigation's stuck checks
        // still end the journey. Preserve the timeout-scope and intent-change tests.
        WalkTarget walkTarget = this.movingEntity.getBrain()
                .getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        return walkTarget == null || !(walkTarget.getTarget() instanceof PersistentPathTarget);
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
        this.movingEntity = entity;
        super.start(world, entity, gameTime);
        Path path = entity.getNavigation().getPath();
        if (walkTarget != null
                && walkTarget.getTarget() instanceof PersistentPathTarget
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
        this.movingEntity = null;
        super.stop(world, entity, gameTime);
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
