package net.mca.server.world.data.villageComponents;

import net.mca.Config;
import net.mca.ProfessionsMCA;
import net.mca.entity.EquipmentSet;
import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.VillagerProfession;
import java.util.LinkedList;
import java.util.List;

public class VillageGuardsManager {
    private final Village village;

    public VillageGuardsManager(Village village) {
        this.village= village;
    }

    public void spawnGuards(ServerLevel world) {
        int guardCapacity = (int)Math.ceil(village.getPopulation() * Config.getInstance().guardSpawnFraction);

        // Count up the guards
        int guards = 0;
        int citizen = 0;
        List<VillagerEntityMCA> villagers = village.getResidents(world);
        List<VillagerEntityMCA> nonGuards = new LinkedList<>();
        for (VillagerEntityMCA villager : villagers) {
            if (villager.isGuard()) {
                guards++;
            } else {
                if (!villager.isBaby() && !villager.isProfessionImportant() && villager.getVillagerXp() == 0 && villager.getVillagerData().getLevel() <= 1) {
                    nonGuards.add(villager);
                }
                citizen++;
            }
        }

        // Count all unloaded villagers against the guard limit
        // This is statistical and may not be accurate, but it's better than nothing
        guards += Math.ceil((village.getPopulation() - guards - citizen) * Config.getInstance().guardSpawnFraction);

        // Spawn a new guard if we don't have enough
        if (nonGuards.size() > 0 && guards < guardCapacity) {
            VillagerEntityMCA villager = nonGuards.get(world.random.nextInt(nonGuards.size()));
            villager.setProfession(guards % 2 == 0 ? ProfessionsMCA.GUARD.get() : ProfessionsMCA.ARCHER.get());
        }
    }


    public EquipmentSet getGuardEquipment(VillagerProfession profession, InteractionHand dominantHand) {
        if (profession == ProfessionsMCA.ARCHER.get()) {
            if (village.hasBuilding("armory")) {
                if (village.hasBuilding("blacksmith")) {
                    return getEquipmentFor(dominantHand, EquipmentSet.ARCHER_2, EquipmentSet.ARCHER_2_LEFT);
                } else {
                    return getEquipmentFor(dominantHand, EquipmentSet.ARCHER_1, EquipmentSet.ARCHER_1_LEFT);
                }
            } else {
                return getEquipmentFor(dominantHand, EquipmentSet.ARCHER_0, EquipmentSet.ARCHER_0_LEFT);
            }
        } else {
            if (village.hasBuilding("armory")) {
                if (village.hasBuilding("blacksmith")) {
                    return EquipmentSet.GUARD_2;
                } else {
                    return EquipmentSet.GUARD_1;
                }
            } else {
                return getEquipmentFor(dominantHand, EquipmentSet.GUARD_0, EquipmentSet.GUARD_0_LEFT);
            }
        }
    }

    public static EquipmentSet getEquipmentFor(InteractionHand dominantHand, EquipmentSet rightSet, EquipmentSet leftSet) {
        return dominantHand == InteractionHand.OFF_HAND && leftSet != null ? leftSet : rightSet;
    }
}
