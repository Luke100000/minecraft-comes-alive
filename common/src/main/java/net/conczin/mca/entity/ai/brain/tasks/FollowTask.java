package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
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
                MemoryModuleTypeMCA.PLAYER_FOLLOWING, MemoryStatus.VALUE_PRESENT
        ));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING).isPresent();
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return this.checkExtraStartConditions(world, villager);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING).ifPresent(playerToFollow -> {
            if (villager.getVillagerBrain().isPanicking()
                    && villager.getBrain().getMemoryInternal(MemoryModuleType.HURT_BY_ENTITY)
                    .filter(livingEntity -> livingEntity == playerToFollow).isPresent()) {
                villager.getBrain().eraseMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
                return;
            }

            if (shouldYieldToGuardCombat(villager)) {
                return;
            }

            BlockPos followPosition = getFollowPosition(playerToFollow);

            villager.getBrain().setMemory(
                    MemoryModuleType.LOOK_TARGET,
                    new EntityTracker(playerToFollow, true)
            );

            if (villager.getNavigation() instanceof MCAGroundPathNavigation navigation
                    && navigation.isControllingClimbable()
                    && navigation.shouldKeepCurrentClimbPathForFollowTarget(followPosition.getY())) {
                return;
            }

            float distance = villager.distanceTo(playerToFollow) - 2.0F;
            float speed = Math.min(1.0F, Math.max(0.6F, distance * 0.1F));
            float speedModifier = (villager.isPassenger() ? 1.7F : 0.8F) * speed;
            int verticalDistance = Math.abs(villager.getBlockY() - followPosition.getY());
            int closeEnoughDistance = verticalDistance > 1 ? 0 : 2;
            boolean climbing = villager.onClimbable()
                    || villager.getNavigation() instanceof MCAGroundPathNavigation navigation
                    && navigation.isControllingClimbable();
            if (climbing) {
                closeEnoughDistance = 0;
            }

            villager.getBrain().setMemory(
                    MemoryModuleType.WALK_TARGET,
                    new WalkTarget(
                            new BlockPosTracker(followPosition),
                            speedModifier,
                            closeEnoughDistance
                    )
            );
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
