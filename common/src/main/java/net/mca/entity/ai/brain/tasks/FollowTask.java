package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

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
            } else {
                float dist = villager.distanceTo(playerToFollow) - 2;
                float speed = Math.min(1.0f, Math.max(0.6f, dist * 0.4f * 0.25f));
                BehaviorUtils.setWalkAndLookTargetMemories(villager, playerToFollow, (villager.isPassenger() ? 1.7f : 0.8f) * speed, 2);
            }
        });
    }
}
