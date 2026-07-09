package net.mca.entity.ai.brain.tasks;

import net.mca.Config;
import net.mca.entity.ai.navigation.PathfindingBlacklist;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.WanderAroundTask;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class WanderOrTeleportToTargetTask extends WanderAroundTask {
    // Pathfinding is one of the slowest components, let's slow it down a bit.
    private static final int SLOWDOWN = 5;
    private int cooldown = SLOWDOWN;

    public WanderOrTeleportToTargetTask() {
        // nop
    }

    @Override
    protected boolean shouldRun(ServerWorld serverWorld, MobEntity mobEntity) {
        if (cooldown < 0) {
            cooldown = SLOWDOWN;
            return super.shouldRun(serverWorld, mobEntity);
        } else {
            cooldown--;
            return false;
        }
    }

    @Override
    protected void keepRunning(ServerWorld world, MobEntity entity, long l) {
        if (Config.getInstance().allowVillagerTeleporting) {
            entity.getBrain().getOptionalMemory(MemoryModuleType.WALK_TARGET).ifPresent(walkTarget -> {
                BlockPos targetPos = walkTarget.getLookTarget().getBlockPos();

                // If the target is more than x blocks away, teleport to it immediately.
                if (!targetPos.isWithinDistance(entity.getPos(), Config.getInstance().villagerMinTeleportationDistance)) {
                    tryTeleport(world, entity, targetPos);
                }
            });
        }

        super.keepRunning(world, entity, l);
    }

    private void tryTeleport(ServerWorld world, MobEntity entity, BlockPos targetPos) {
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

    private boolean tryTeleportTo(ServerWorld world, MobEntity entity, BlockPos targetPos, int x, int y, int z) {
        if (Math.abs((double) x - targetPos.getX()) < 2.0D && Math.abs((double) z - targetPos.getZ()) < 2.0D) {
            return false;
        } else if (!this.canTeleportTo(world, entity, new BlockPos(x, y, z))) {
            return false;
        } else {
            entity.requestTeleport((double) x + 0.5D, y, (double) z + 0.5D);
            return true;
        }
    }

    private boolean canTeleportTo(ServerWorld world, MobEntity entity, BlockPos pos) {
        PathNodeType pathNodeType = LandPathNodeMaker.getLandNodeType(world, pos.mutableCopy());
        if (pathNodeType != PathNodeType.WALKABLE) {
            return false;
        } else {
            if (!isAreaSafe(world, pos.down())) {
                return false;
            } else {
                BlockPos blockPos = pos.subtract(entity.getBlockPos());
                return world.isSpaceEmpty(entity, entity.getBoundingBox().offset(blockPos));
            }
        }
    }

    private int getRandomInt(MobEntity entity, int min, int max) {
        return entity.getRandom().nextInt(max - min + 1) + min;
    }

    private boolean isAreaSafe(ServerWorld world, BlockPos pos) {
        // The following conditions define whether it is logically
        // safe for the entity to teleport to the specified pos within world
        return !PathfindingBlacklist.isBlocked(world.getBlockState(pos));
    }

}
