package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public class LoseUnimportantJobTask {
    protected static boolean shouldRun(ServerLevel world, Villager entity) {
        return !((VillagerEntityMCA) entity).isProfessionImportant();
    }

    public static BehaviorControl<Villager> create() {
        return BehaviorBuilder.create((context) -> {
            return context.group(context.absent(MemoryModuleType.JOB_SITE)).apply(context, (jobSite) -> {
                return (world, entity, time) -> {
                    VillagerData villagerData = entity.getVillagerData();
                    VillagerProfession profession = villagerData.profession().value();
                    if (shouldRun(world, entity)
                        && !ProfessionsMCA.is(profession, VillagerProfession.NONE)
                        && !ProfessionsMCA.is(profession, VillagerProfession.NITWIT)
                        && entity.getVillagerXp() == 0
                        && villagerData.level() <= 1) {
                        entity.setVillagerData(entity.getVillagerData().withProfession(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE)));
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
