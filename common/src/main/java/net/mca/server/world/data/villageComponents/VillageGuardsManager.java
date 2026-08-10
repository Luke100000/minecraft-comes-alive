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
import java.util.Map;

public class VillageGuardsManager {
    private final Village village;

    public VillageGuardsManager(Village village) {
        this.village = village;
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
        guards += (int)Math.ceil((village.getPopulation() - guards - citizen) * Config.getInstance().guardSpawnFraction);

        // Spawn a new guard if we don't have enough
        if (!nonGuards.isEmpty() && guards < guardCapacity) {
            VillagerEntityMCA villager = nonGuards.get(world.random.nextInt(nonGuards.size()));
            villager.setProfession(guards % 2 == 0 ? ProfessionsMCA.GUARD.get() : ProfessionsMCA.ARCHER.get());
        }
    }

    public EquipmentSet getGuardEquipment(VillagerProfession profession, InteractionHand dominantHand) {
        int villageLevel = getVillageEquipmentLevel();
        if (profession == ProfessionsMCA.ARCHER.get()) {
            return getArcherEquipmentForLevel(villageLevel, dominantHand);
        } else {
            return getGuardEquipmentForLevel(villageLevel, dominantHand);
        }
    }

    private int getVillageEquipmentLevel() {
        int level = 0;
        if (village.hasBuilding("armory")) {
            level++;
        }
        if (village.hasBuilding("blacksmith")) {
            level++;
        }
        return level;
    }

    public static EquipmentSet getEquipmentFor(InteractionHand dominantHand, EquipmentSet rightSet, EquipmentSet leftSet) {
        return dominantHand == InteractionHand.OFF_HAND && leftSet != null ? leftSet : rightSet;
    }

    public static EquipmentSet getGuardEquipmentForLevel(int level, InteractionHand dominantHand) {
        EquipmentSet fallback = switch (clampEquipmentLevel(level)) {
            case 2 -> EquipmentSet.GUARD_2;
            case 1 -> EquipmentSet.GUARD_1;
            default -> getEquipmentFor(dominantHand, EquipmentSet.GUARD_0, EquipmentSet.GUARD_0_LEFT);
        };
        return getConfiguredEquipment(Config.getInstance().guardEquipment, level, fallback);
    }

    public static EquipmentSet getArcherEquipmentForLevel(int level, InteractionHand dominantHand) {
        EquipmentSet fallback = switch (clampEquipmentLevel(level)) {
            case 2 -> getEquipmentFor(dominantHand, EquipmentSet.ARCHER_2, EquipmentSet.ARCHER_2_LEFT);
            case 1 -> getEquipmentFor(dominantHand, EquipmentSet.ARCHER_1, EquipmentSet.ARCHER_1_LEFT);
            default -> getEquipmentFor(dominantHand, EquipmentSet.ARCHER_0, EquipmentSet.ARCHER_0_LEFT);
        };
        return getConfiguredEquipment(Config.getInstance().archerEquipment, level, fallback);
    }

    private static EquipmentSet getConfiguredEquipment(Map<String, EquipmentSet> config, int level, EquipmentSet fallback) {
        if (config == null) {
            return fallback;
        }
        return config.getOrDefault(Integer.toString(clampEquipmentLevel(level)), fallback);
    }

    private static int clampEquipmentLevel(int level) {
        return Math.max(0, Math.min(2, level));
    }
}
