package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public class StayTask extends Behavior<VillagerEntityMCA> {
    public StayTask() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return villager.getMCABrain().getMemoryInternal(MemoryModuleTypeMCA.STAYING.get()).isPresent() && !villager.getVillagerBrain().isPanicking();
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return this.checkExtraStartConditions(world, villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        villager.getNavigation().stop();
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        villager.getNavigation().stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
    }
}
