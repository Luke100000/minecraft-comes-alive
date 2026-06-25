package net.conczin.mca.server.world.data.villageComponents;

import net.conczin.mca.Config;
import net.conczin.mca.entity.EquipmentSet;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class VillageGuardsManager {
    private final Village village;

    public VillageGuardsManager(Village village) {
        this.village = village;
    }

    public static EquipmentSet getGuardEquipmentForLevel(int level) {
        EquipmentSet fallback = switch (Math.clamp(level, 0, 2)) {
            case 2 -> EquipmentSet.GUARD_2;
            case 1 -> EquipmentSet.GUARD_1;
            default -> EquipmentSet.GUARD_0;
        };
        return getConfiguredEquipment(Config.getInstance().guardEquipment, level, fallback);
    }

    public static EquipmentSet getArcherEquipmentForLevel(int level) {
        EquipmentSet fallback = switch (Math.clamp(level, 0, 2)) {
            case 2 -> EquipmentSet.ARCHER_2;
            case 1 -> EquipmentSet.ARCHER_1;
            default -> EquipmentSet.ARCHER_0;
        };
        return getConfiguredEquipment(Config.getInstance().archerEquipment, level, fallback);
    }

    public void spawnGuards(ServerLevel world) {
        int guardCapacity = (int) Math.ceil(village.getPopulation() * Config.getInstance().guardSpawnFraction);

        // Count up the guards
        int guards = 0;
        int citizen = 0;
        List<VillagerEntityMCA> villagers = village.getResidents(world);
        List<VillagerEntityMCA> nonGuards = new LinkedList<>();
        for (VillagerEntityMCA villager : villagers) {
            if (villager.isGuard()) {
                guards++;
            } else {
                if (!villager.isBaby() && !villager.isProfessionImportant() && villager.getVillagerXp() == 0 && villager.getVillagerData().level() <= 1) {
                    nonGuards.add(villager);
                }
                citizen++;
            }
        }

        // Count all unloaded villagers against the guard limit
        // This is statistical and may not be accurate, but it's better than nothing
        guards += (int) Math.ceil((village.getPopulation() - guards - citizen) * Config.getInstance().guardSpawnFraction);

        // Spawn a new guard if we don't have enough
        if (!nonGuards.isEmpty() && guards < guardCapacity) {
            VillagerEntityMCA villager = nonGuards.get(world.getRandom().nextInt(nonGuards.size()));
            villager.setProfession(guards % 2 == 0 ? ProfessionsMCA.GUARD : ProfessionsMCA.ARCHER);
        }
    }

    public EquipmentSet getGuardEquipment(VillagerProfession profession) {
        int villageLevel = getVillageEquipmentLevel();
        if (profession == ProfessionsMCA.ARCHER) {
            return getArcherEquipmentForLevel(villageLevel);
        } else {
            return getGuardEquipmentForLevel(villageLevel);
        }
    }

    private int getVillageEquipmentLevel() {
        int level = 0;
        if (village.hasBuilding("armory")) level++;
        if (village.hasBuilding("blacksmith")) level++;
        return level;
    }

    private static EquipmentSet getConfiguredEquipment(Map<String, EquipmentSet> config, int level, EquipmentSet fallback) {
        if (config == null) {
            return fallback;
        }

        return config.getOrDefault(Integer.toString(Math.clamp(level, 0, 2)), fallback);
    }
}
