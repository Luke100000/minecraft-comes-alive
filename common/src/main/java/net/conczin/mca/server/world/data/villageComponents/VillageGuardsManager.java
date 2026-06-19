package net.conczin.mca.server.world.data.villageComponents;

import net.conczin.mca.Config;
import net.conczin.mca.entity.EquipmentSet;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.LinkedList;
import java.util.List;

public class VillageGuardsManager {
    private static final int MAX_LEVEL = 3;

    private static final EquipmentSet[] GUARD_SETS = {
            EquipmentSet.GUARD_0, EquipmentSet.GUARD_1, EquipmentSet.GUARD_2, EquipmentSet.GUARD_3
    };
    private static final EquipmentSet[] GUARD_SETS_LEFT = {
            EquipmentSet.GUARD_0_LEFT, EquipmentSet.GUARD_1, EquipmentSet.GUARD_2, EquipmentSet.GUARD_3
    };
    private static final EquipmentSet[] ARCHER_SETS = {
            EquipmentSet.ARCHER_0, EquipmentSet.ARCHER_1, EquipmentSet.ARCHER_2, EquipmentSet.ARCHER_3
    };
    private static final EquipmentSet[] ARCHER_SETS_LEFT = {
            EquipmentSet.ARCHER_0_LEFT, EquipmentSet.ARCHER_1_LEFT, EquipmentSet.ARCHER_2_LEFT, EquipmentSet.ARCHER_3_LEFT
    };

    private final Village village;

    public VillageGuardsManager(Village village) {
        this.village = village;
    }

    public static EquipmentSet getEquipmentFor(InteractionHand dominantHand, EquipmentSet rightSet, EquipmentSet leftSet) {
        return dominantHand == InteractionHand.OFF_HAND && leftSet != null ? leftSet : rightSet;
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

    private int getEquipmentLevel() {
        int level = Config.getInstance().guardBaseEquipmentLevel;
        if (village.hasBuilding("armory")) {
            level++;
            if (village.hasBuilding("blacksmith")) {
                level++;
            }
        }
        return Math.max(0, Math.min(MAX_LEVEL, level));
    }

    public EquipmentSet getGuardEquipment(VillagerProfession profession, InteractionHand dominantHand) {
        int level = getEquipmentLevel();
        boolean left = dominantHand == InteractionHand.OFF_HAND;
        if (profession == ProfessionsMCA.ARCHER) {
            return (left ? ARCHER_SETS_LEFT : ARCHER_SETS)[level];
        }
        return (left ? GUARD_SETS_LEFT : GUARD_SETS)[level];
    }
}
