package net.mca.entity.ai.brain.tasks;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerProfession;

public class LoseUnimportantJobTask {
    protected static boolean shouldRun(ServerLevel world, Villager entity) {
        return !((VillagerEntityMCA) entity).isProfessionImportant();
    }

    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.create((context) -> {
            return context.group(context.absent(MemoryModuleType.JOB_SITE)).apply(context, (jobSite) -> {
                return (world, entity, time) -> {
                    VillagerData villagerData = entity.getVillagerData();
                    if (shouldRun(world, entity) && villagerData.getProfession() != VillagerProfession.NONE && villagerData.getProfession() != VillagerProfession.NITWIT && entity.getVillagerXp() == 0 && villagerData.getLevel() <= 1) {
                        entity.setVillagerData(entity.getVillagerData().setProfession(VillagerProfession.NONE));
                        entity.refreshBrain(world);
                        return true;
                    } else {
                        return false;
                    }
                };
            });
        });
    }
}