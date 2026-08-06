package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;

import java.util.Optional;

public class GrieveTask extends Behavior<VillagerEntityMCA> {
    public GrieveTask() {
        super(ImmutableMap.of());
    }

    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        Optional<BlockPos> rememberedSite = entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE);
        if (rememberedSite.isPresent()) {
            if (!EnterGraveyardTask.hasMournableSite(entity)) {
                entity.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_SITE);
                entity.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
                entity.getVillagerBrain().justGrieved();
                return false;
            }
            return entity.getVillagerBrain().shouldGrieve();
        }

        return EnterGraveyardTask.hasPeriodicMourningCandidate(entity)
                && entity.getVillagerBrain().shouldGrieve();
    }

    @Override
    protected void start(ServerLevel serverWorld, VillagerEntityMCA villager, long l) {
        Brain<Villager> brain = villager.getBrain();
        if (!brain.isActive(ActivitiesMCA.GRIEVE)) {
            brain.eraseMemory(MemoryModuleType.PATH);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            brain.eraseMemory(MemoryModuleType.BREED_TARGET);
            brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        }
        villager.getMCABrain().setActiveActivityIfPossible(ActivitiesMCA.GRIEVE);
    }
}
