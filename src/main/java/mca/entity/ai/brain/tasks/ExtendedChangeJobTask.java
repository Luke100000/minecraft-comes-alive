package mca.entity.ai.brain.tasks;

import mca.entity.VillagerEntityMCA;
import net.minecraft.entity.ai.brain.task.ChangeJobTask;
import net.minecraft.entity.merchant.villager.VillagerEntity;
import net.minecraft.world.server.ServerWorld;

public class ExtendedChangeJobTask extends ChangeJobTask {
    protected boolean checkExtraStartConditions(ServerWorld world, VillagerEntity entity) {
        return super.checkExtraStartConditions(world, entity) && !((VillagerEntityMCA) entity).importantProfession.get();
    }
}