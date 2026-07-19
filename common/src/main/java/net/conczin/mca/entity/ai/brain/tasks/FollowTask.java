package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

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

            float distance = villager.distanceTo(playerToFollow) - 2.0F;
            float speed = Math.min(1.0F, Math.max(0.6F, distance * 0.1F));
            float speedModifier = (villager.isPassenger() ? 1.7F : 0.8F) * speed;

            int verticalDistance = Math.abs(
                    villager.blockPosition().getY() - playerToFollow.blockPosition().getY()
            );
            int closeEnoughDistance = verticalDistance > 1 ? 0 : 2;
            if (villager.onClimbable()) {
                closeEnoughDistance = 1;
            }

            BehaviorUtils.setWalkAndLookTargetMemories(
                    villager, playerToFollow, speedModifier, closeEnoughDistance
            );
        });
    }

    private boolean shouldYieldToGuardCombat(VillagerEntityMCA villager) {
        return villager.isGuard()
               && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isPresent();
    }
}
