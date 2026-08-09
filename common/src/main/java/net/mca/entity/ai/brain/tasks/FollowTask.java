package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.brain.BlockPosLookTarget;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class FollowTask extends MultiTickTask<VillagerEntityMCA> {
    public FollowTask() {
        super(ImmutableMap.of(
                MemoryModuleTypeMCA.PLAYER_FOLLOWING.get(), MemoryModuleState.VALUE_PRESENT
        ));
    }

    @Override
    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA villager) {
        return villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).isPresent();
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        return this.shouldRun(world, villager);
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).ifPresent(playerToFollow -> {
            if (villager.getVillagerBrain().isPanicking() && villager.getBrain().getOptionalMemory(MemoryModuleType.HURT_BY_ENTITY).filter(livingEntity -> livingEntity == playerToFollow).isPresent()) {
                villager.getBrain().forget(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get());
            } else if (shouldYieldToGuardCombat(villager)) {
                return;
            } else {
                float dist = villager.distanceTo(playerToFollow) - 2;
                float speed = Math.min(1.0f, Math.max(0.6f, dist * 0.4f * 0.25f));
                float speedModifier = (villager.hasVehicle() ? 1.7f : 0.8f) * speed;
                BlockPos followPosition = getFollowPosition(playerToFollow);

                int verticalDistance = Math.abs(villager.getBlockY() - followPosition.getY());
                int closeEnoughDistance = verticalDistance > 1 ? 0 : 2;
                boolean climbing = villager.isClimbing()
                        || villager.getNavigation() instanceof MCAGroundPathNavigation navigation
                        && navigation.isControllingClimbable();
                if (climbing) {
                    closeEnoughDistance = 0;
                }

                villager.getBrain().remember(
                        MemoryModuleType.LOOK_TARGET,
                        new EntityLookTarget(playerToFollow, true)
                );
                villager.getBrain().remember(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(
                                new BlockPosLookTarget(followPosition),
                                speedModifier,
                                closeEnoughDistance
                        )
                );
            }
        });
    }

    private static BlockPos getFollowPosition(Entity target) {
        return target.isOnGround()
                ? target.getVelocityAffectingPos().up()
                : target.getBlockPos();
    }

    private boolean shouldYieldToGuardCombat(VillagerEntityMCA villager) {
        return villager.isGuard()
                && villager.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET).isPresent();
    }
}
