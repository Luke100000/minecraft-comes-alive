package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.ActivityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

public class GrieveTask extends MultiTickTask<VillagerEntityMCA> {
    public GrieveTask() {
        super(ImmutableMap.of());
    }

    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA entity) {
        boolean hasRememberedSite = entity.getBrain().getOptionalMemory(MemoryModuleTypeMCA.MOURNING_SITE.get()).isPresent();
        boolean hasCompleteGraveyard = entity.getResidency().getHomeVillage().filter(v -> v.hasBuilding("graveyard")).isPresent();
        return entity.getVillagerBrain().shouldGrieve()
                && (hasRememberedSite || hasCompleteGraveyard);
    }

    @Override
    protected void run(ServerWorld serverWorld, VillagerEntityMCA villager, long l) {
        Brain<VillagerEntity> brain = villager.getBrain();
        if (!brain.hasActivity(ActivityMCA.GRIEVE.get())) {
            brain.forget(MemoryModuleType.PATH);
            brain.forget(MemoryModuleType.WALK_TARGET);
            brain.forget(MemoryModuleType.LOOK_TARGET);
            brain.forget(MemoryModuleType.BREED_TARGET);
            brain.forget(MemoryModuleType.INTERACTION_TARGET);
        }
        villager.getMCABrain().doExclusively(ActivityMCA.GRIEVE.get());
    }
}
