package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Mourning;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;

public class GrieveTask extends Behavior<VillagerEntityMCA> {
    public GrieveTask() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        return Mourning.canResumeScheduled(entity);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        Mourning.resumeScheduled(villager);
    }
}
