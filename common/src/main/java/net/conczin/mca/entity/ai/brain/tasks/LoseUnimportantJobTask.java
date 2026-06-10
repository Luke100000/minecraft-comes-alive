package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.Trigger;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public class LoseUnimportantJobTask {
    protected static boolean shouldRun(ServerLevel world, Villager entity) {
        return !((VillagerEntityMCA) entity).isProfessionImportant();
    }

    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.create(context -> context.map(jobSite -> (Trigger<Villager>) (world, entity, time) -> {
            VillagerData villagerData = entity.getVillagerData();
            if (shouldRun(world, entity)
                && !villagerData.profession().is(VillagerProfession.NONE)
                && !villagerData.profession().is(VillagerProfession.NITWIT)
                && entity.getVillagerXp() == 0
                && villagerData.level() <= 1) {
                entity.setVillagerData(villagerData.withProfession(world.registryAccess(), VillagerProfession.NONE));
                entity.refreshBrain(world);
                return true;
            } else {
                return false;
            }
        }, context.absent(MemoryModuleType.JOB_SITE)));
    }
}
