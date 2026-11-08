package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.navigation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;

public class WanderOrTeleportToTargetTask extends MoveToTargetSink {
    private Mob movingEntity;
    private long playEndTime;

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, Mob entity) {
        WalkTarget target = entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        if (clearInactiveActivityTarget(entity, target)) {
            return false;
        }
        return super.checkExtraStartConditions(world, entity);
    }

    @Override
    protected boolean timedOut(long gameTime) {
        Mob entity = this.movingEntity;
        boolean playExpired = entity != null && gameTime > this.playEndTime
                && entity.getBrain().isActive(Activity.PLAY);
        if (!playExpired && !super.timedOut(gameTime)) {
            return false;
        }
        if (entity == null) {
            return true;
        }
        // The target's intent can change without restarting this behavior.
        WalkTarget walkTarget = entity.getBrain()
                .getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        return !ExtendedWalkTowardsTask.isPersistentJourney(walkTarget);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, Mob entity, long gameTime) {
        WalkTarget walkTarget = entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        if (clearInactiveActivityTarget(entity, walkTarget)) {
            return false;
        }
        if (shouldYieldToCombat(entity)) {
            return false;
        }

        boolean vanillaCanContinue = super.canStillUse(world, entity, gameTime);
        if (vanillaCanContinue) {
            return true;
        }

        Path path = entity.getNavigation().getPath();
        if (walkTarget != null
                && path != null
                && entity.getNavigation().isDone()
                && !MultiTargetPositionTracker.isReached(entity, walkTarget.getTarget(), walkTarget.getCloseEnoughDist())
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
                && ExtendedWalkTowardsTask.isPersistentJourney(walkTarget)
                && path != null
                && !entity.getNavigation().isStuck()
                && MCAGroundPathNavigation.isUsefulPartialPath(
                        path, entity, walkTarget
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
        // The CORE sink also consumes PLAY targets, which get vanilla's shorter timeout.
        this.playEndTime = entity.getBrain().isActive(Activity.PLAY)
                ? gameTime + 80 + entity.getRandom().nextInt(41) : Long.MAX_VALUE;
    }

    @Override
    protected void stop(ServerLevel world, Mob entity, long gameTime) {
        this.movingEntity = null;
        super.stop(world, entity, gameTime);
    }

    private static boolean shouldYieldToCombat(Mob entity) {
        RangedCombatState combat = RangedCombatState.current(entity).orElse(null);
        WalkTarget walkTarget = entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        if (walkTarget == null) {
            return false;
        }
        if (combat == RangedCombatState.EMERGENCY_FLEE) {
            return !(walkTarget.getTarget() instanceof CombatEscapePositionTracker);
        }
        return combat != null && entity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                && !(walkTarget instanceof ArcherMovementTask.CombatWalkTarget);
    }

    private static boolean clearInactiveActivityTarget(Mob entity, WalkTarget target) {
        if (target == null || !(target.getTarget() instanceof PersistentPathTarget persistent)
                || !persistent.isInactiveFor(entity)) {
            return false;
        }
        // Validate at the movement consumer, including when no HOME/other POI is remembered.
        entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        if (entity.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET).orElse(null) == persistent) {
            entity.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        return true;
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

    static boolean canTeleportTo(ServerLevel world, Mob entity, BlockPos pos) {
        // Probe exactly where teleportTo() will place the feet and body. Moving
        // by block-position deltas retains the mob's original fractional offset
        // and can miss a narrow collision at the destination center.
        AABB landingBox = entity.getBoundingBox().move(
                pos.getX() + 0.5D - entity.getX(),
                pos.getY() - entity.getY(),
                pos.getZ() + 0.5D - entity.getZ()
        );
        // Path-type classification examines neighboring blocks and collision
        // can cross the destination chunk boundary. Check the entire probe
        // region before a world read that could load a distant chunk.
        int minChunkX = Math.min(pos.getX() - 1, Mth.floor(landingBox.minX)) >> 4;
        int maxChunkX = Math.max(pos.getX() + 1, Mth.floor(landingBox.maxX)) >> 4;
        int minChunkZ = Math.min(pos.getZ() - 1, Mth.floor(landingBox.minZ)) >> 4;
        int maxChunkZ = Math.max(pos.getZ() + 1, Mth.floor(landingBox.maxZ)) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (world.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    return false;
                }
            }
        }
        if (WalkNodeEvaluator.getPathTypeStatic(entity, pos.mutable()) != PathType.WALKABLE
                || TeleportBlockBlacklist.isBlocked(world.getBlockState(pos.below()))) {
            return false;
        }
        return world.noCollision(entity, landingBox);
    }
}
