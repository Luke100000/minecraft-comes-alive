package net.conczin.mca.registry;

import com.google.common.collect.ImmutableSet;
import net.conczin.mca.MCA;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public interface ProfessionsMCA {
    Map<ResourceLocation, VillagerProfession> PROFESSIONS = new HashMap<>();

    Set<VillagerProfession> CAN_NOT_TRADE = new HashSet<>();
    Set<VillagerProfession> IS_IMPORTANT = new HashSet<>();
    Set<VillagerProfession> NEEDS_NO_HOME = new HashSet<>();

    VillagerProfession OUTLAW = register("outlaw", false, true, true, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FARMER);
    VillagerProfession GUARD = register("guard", false, true, false, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_ARMORER);
    VillagerProfession ARCHER = register("archer", false, true, false, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FLETCHER);
    VillagerProfession ADVENTURER = register("adventurer", true, true, true, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FLETCHER);
    VillagerProfession MERCENARY = register("mercenary", false, true, true, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FLETCHER);
    VillagerProfession CULTIST = register("cultist", true, true, true, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FLETCHER);

    static VillagerProfession register(String name, boolean canTradeWith, boolean important, boolean needsNoHome, Predicate<Holder<PoiType>> heldWorkstation, Predicate<Holder<PoiType>> acquirableWorkstation, @Nullable SoundEvent workSound) {
        return register(name, canTradeWith, important, needsNoHome, heldWorkstation, acquirableWorkstation, ImmutableSet.of(), ImmutableSet.of(), workSound);
    }

    static VillagerProfession register(String name, boolean canTradeWith, boolean important, boolean needsNoHome, ResourceKey<PoiType> heldWorkstation, ImmutableSet<Item> gatherableItems, ImmutableSet<Block> secondaryJobSites, @Nullable SoundEvent workSound) {
        return register(name, canTradeWith, important, needsNoHome, (entry) -> {
            return entry.is(heldWorkstation);
        }, (entry) -> {
            return entry.is(heldWorkstation);
        }, gatherableItems, secondaryJobSites, workSound);
    }

    static VillagerProfession register(String name, boolean canTradeWith, boolean important, boolean needsNoHome, Predicate<Holder<PoiType>> heldWorkstation, Predicate<Holder<PoiType>> acquirableWorkstation, ImmutableSet<Item> gatherableItems, ImmutableSet<Block> secondaryJobSites, @Nullable SoundEvent workSound) {
        ResourceLocation id = MCA.locate(name);
        VillagerProfession result = new VillagerProfession(
                id.toString().replace(':', '.'), heldWorkstation, acquirableWorkstation, gatherableItems, secondaryJobSites, workSound
        );
        if (!canTradeWith) {
            CAN_NOT_TRADE.add(result);
        }
        if (important) {
            IS_IMPORTANT.add(result);
        }
        if (needsNoHome) {
            ProfessionsMCA.NEEDS_NO_HOME.add(result);
        }
        PROFESSIONS.put(id, result);
        return result;
    }

    static String getFavoredBuilding(VillagerProfession profession) {
        if (VillagerProfession.CARTOGRAPHER == profession || VillagerProfession.LIBRARIAN == profession || VillagerProfession.CLERIC == profession) {
            return "library";
        } else if (GUARD == profession || ARCHER == profession) {
            return "inn";
        }
        return null;
    }

    static void registerProfessions(MCA.RegisterHelper<VillagerProfession> helper) {
        PROFESSIONS.forEach(helper::register);

        CAN_NOT_TRADE.add(VillagerProfession.NONE);
        CAN_NOT_TRADE.add(VillagerProfession.NITWIT);
    }
}
