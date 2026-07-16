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
import net.minecraft.util.math.BlockPos;

import java.util.Optional;

public class GrieveTask extends MultiTickTask<VillagerEntityMCA> {
    public GrieveTask() {
        super(ImmutableMap.of());
    }

    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA entity) {
        Optional<BlockPos> rememberedSite = entity.getBrain().getOptionalMemory(MemoryModuleTypeMCA.MOURNING_SITE.get());
        if (rememberedSite.isPresent()) {
            if (!EnterGraveyardTask.hasMournableSite(entity)) {
                entity.getBrain().forget(MemoryModuleTypeMCA.MOURNING_SITE.get());
                entity.getBrain().forget(MemoryModuleTypeMCA.MOURNING_POSITION.get());
                entity.getVillagerBrain().justGrieved();
                return false;
            }
            return entity.getVillagerBrain().shouldGrieve();
        }

        // Periodic remembrance only makes sense when a complete graveyard contains
        // at least one occupied tombstone. Do this check before shouldGrieve() so an
        // empty decorative graveyard cannot initialize or advance the grief schedule.
        return EnterGraveyardTask.hasPeriodicMourningCandidate(entity)
                && entity.getVillagerBrain().shouldGrieve();
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
