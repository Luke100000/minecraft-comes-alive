package net.conczin.mca.server.world.data.villageComponents;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.PoolUtil;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;

public class VillageProcreationManager {
    private final Village village;

    public VillageProcreationManager(Village village) {
        this.village= village;
    }

    // if the population is low, find a couple and let them have a child
    public void procreate(ServerLevel world) {
        if (world.random.nextFloat() >= Config.getInstance().villagerProcreationChancePerMinute) {
            return;
        }

        int population = village.getPopulation();
        int maxPopulation = village.getMaxPopulation();
        if (population >= maxPopulation * village.getPopulationThreshold()) {
            return;
        }

        // look for married women without baby
        PoolUtil.pick(village.getResidents(world), world.random)
                .filter(villager -> villager.getGenetics().getGender() == Gender.FEMALE)
                .filter(villager -> world.random.nextFloat() < 1.0 / (FamilyTree.get(world).getOrCreate(villager).getChildren().count() + 0.1))
                .filter(villager -> villager.getRelationships().getPregnancy().tryStartGestation())
                .ifPresent(villager ->
                        villager.getRelationships().getPartner().ifPresent(spouse -> {
                                    // tell everyone about it
                                    if (Config.getInstance().villagerBirthNotification && spouse instanceof VillagerEntityMCA spouseVillager) {
                                        village.broadCastMessage(world, "events.baby", villager, spouseVillager);
                                    }
                                }
                        )
                );
    }
}
