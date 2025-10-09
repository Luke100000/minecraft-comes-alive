package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.ActivityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

public class GrieveTask extends Behavior<VillagerEntityMCA> {
    public GrieveTask() {
        super(ImmutableMap.of());
    }

    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        return entity.getVillagerBrain().shouldGrieve() && entity.getResidency().getHomeVillage().filter(v -> v.hasBuilding("graveyard")).isPresent();
    }

    @Override
    protected void start(ServerLevel serverWorld, VillagerEntityMCA villager, long l) {
        Brain<Villager> brain = villager.getBrain();
        if (!brain.isActive(ActivityMCA.GRIEVE.get())) {
            brain.eraseMemory(MemoryModuleType.PATH);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            brain.eraseMemory(MemoryModuleType.BREED_TARGET);
            brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        }
        villager.getMCABrain().setActiveActivityIfPossible(ActivityMCA.GRIEVE.get());
    }
}