package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
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
            if (villager.getVillagerBrain().isPanicking() && villager.getBrain().getMemoryInternal(MemoryModuleType.HURT_BY_ENTITY).filter(livingEntity -> livingEntity == playerToFollow).isPresent()) {
                cancelLadderRoute(villager);
                villager.getBrain().eraseMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
            } else if (shouldYieldToGuardCombat(villager)) {
                cancelLadderRoute(villager);
                return;
            } else {
                float dist = villager.distanceTo(playerToFollow) - 2;
                float speed = Math.min(1.0f, Math.max(0.6f, dist * 0.4f * 0.25f));
                float speedModifier = (villager.isPassenger() ? 1.7f : 0.8f) * speed;
                if (villager.getNavigation() instanceof MCAGroundPathNavigation navigation
                        && navigation.moveToLadderTarget(playerToFollow.blockPosition(), 0, speedModifier)) {
                    villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(playerToFollow, true));
                    villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                } else {
                    BehaviorUtils.setWalkAndLookTargetMemories(villager, playerToFollow, speedModifier, 2);
                }
            }
        });
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING).isEmpty()) {
            cancelLadderRoute(villager);
        }
    }

    private void cancelLadderRoute(VillagerEntityMCA villager) {
        if (villager.getNavigation() instanceof MCAGroundPathNavigation navigation) {
            navigation.cancelLadderRoute();
        }
    }

    private boolean shouldYieldToGuardCombat(VillagerEntityMCA villager) {
        return villager.isGuard()
               && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isPresent();
    }
}
