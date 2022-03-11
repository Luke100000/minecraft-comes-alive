package mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;

import mca.entity.VillagerEntityMCA;
import mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.task.LookTargetUtil;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.server.world.ServerWorld;

public class FollowTask extends Task<VillagerEntityMCA> {

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
        villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).ifPresent(playerToFollow -> LookTargetUtil.walkTowards(villager, playerToFollow, villager.hasVehicle() ? 1.7f : 0.8f, 2));
    }
}
