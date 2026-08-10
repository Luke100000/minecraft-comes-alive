package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class FollowTask extends Behavior<VillagerEntityMCA> {
    public FollowTask() {
        super(ImmutableMap.of(
                MemoryModuleTypeMCA.PLAYER_FOLLOWING.get(), MemoryStatus.VALUE_PRESENT
        ));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).isPresent();
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return this.checkExtraStartConditions(world, villager);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).ifPresent(playerToFollow -> {
            if (villager.getVillagerBrain().isPanicking() && villager.getBrain().getMemoryInternal(MemoryModuleType.HURT_BY_ENTITY).filter(livingEntity -> livingEntity == playerToFollow).isPresent()) {
                villager.getBrain().eraseMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get());
            } else if (shouldYieldToGuardCombat(villager)) {
                return;
            } else {
                float dist = villager.distanceTo(playerToFollow) - 2;
                float speed = Math.min(1.0f, Math.max(0.6f, dist * 0.4f * 0.25f));
                float speedModifier = (villager.isPassenger() ? 1.7f : 0.8f) * speed;
                BlockPos followPosition = getFollowPosition(playerToFollow);

                int verticalDistance = Math.abs(villager.getBlockY() - followPosition.getY());
                int closeEnoughDistance = verticalDistance > 1 ? 0 : 2;
                boolean climbing = villager.onClimbable()
                        || villager.getNavigation() instanceof MCAGroundPathNavigation navigation
                        && navigation.isControllingClimbable();
                if (climbing) {
                    closeEnoughDistance = 0;
                }

                villager.getBrain().setMemory(
                        MemoryModuleType.LOOK_TARGET,
                        new EntityTracker(playerToFollow, true)
                );
                villager.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        new WalkTarget(
                                new BlockPosTracker(followPosition),
                                speedModifier,
                                closeEnoughDistance
                        )
                );
            }
        });
    }

    private static BlockPos getFollowPosition(Entity target) {
        return target.onGround()
                ? target.getBlockPosBelowThatAffectsMyMovement().above()
                : target.blockPosition();
    }

    private boolean shouldYieldToGuardCombat(VillagerEntityMCA villager) {
        return villager.isGuard()
                && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isPresent();
    }
}
