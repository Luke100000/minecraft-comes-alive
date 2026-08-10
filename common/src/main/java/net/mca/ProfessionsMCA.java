package net.mca;

import com.google.common.collect.ImmutableSet;
import net.mca.entity.ai.PointOfInterestTypeMCA;
import net.mca.util.RegistryRef;
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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public interface ProfessionsMCA {
    Map<ResourceLocation, RegistryRef<VillagerProfession>> PROFESSIONS = new LinkedHashMap<>();

    RegistryRef<VillagerProfession> OUTLAW = register("outlaw", false, true, true, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FARMER);
    RegistryRef<VillagerProfession> GUARD = register("guard", false, true, false, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_ARMORER);
    RegistryRef<VillagerProfession> ARCHER = register("archer", false, true, false, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FLETCHER);
    RegistryRef<VillagerProfession> ADVENTURER = register("adventurer", true, true, true, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FLETCHER);
    RegistryRef<VillagerProfession> MERCENARY = register("mercenary", false, true, true, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FLETCHER);
    RegistryRef<VillagerProfession> CULTIST = register("cultist", true, true, true, PoiType.NONE, VillagerProfession.ALL_ACQUIRABLE_JOBS, SoundEvents.VILLAGER_WORK_FLETCHER);
    // VillagerProfession JEWELER = register("jeweler", PointOfInterestTypeMCA.JEWELER, SoundEvents.ENTITY_VILLAGER_WORK_ARMORER);

    Set<VillagerProfession> canNotTrade = new HashSet<>();
    Set<VillagerProfession> isImportant = new HashSet<>();
    Set<VillagerProfession> needsNoHome = new HashSet<>();

    static void bootstrap() {
        PointOfInterestTypeMCA.bootstrap();

        canNotTrade.add(VillagerProfession.NONE);
        canNotTrade.add(VillagerProfession.NITWIT);
    }

    private static RegistryRef<VillagerProfession> register(String name, boolean canTradeWith, boolean important, boolean needsNoHome, ResourceKey<PoiType> heldWorkstation, @Nullable SoundEvent workSound) {
        return register(name, canTradeWith, important, needsNoHome, (entry) -> {
            return entry.is(heldWorkstation);
        }, (entry) -> {
            return entry.is(heldWorkstation);
        }, workSound);
    }

    static RegistryRef<VillagerProfession> register(String name, boolean canTradeWith, boolean important, boolean needsNoHome, Predicate<Holder<PoiType>> heldWorkstation, Predicate<Holder<PoiType>> acquirableWorkstation, @Nullable SoundEvent workSound) {
        return register(name, canTradeWith, important, needsNoHome, heldWorkstation, acquirableWorkstation, ImmutableSet.of(), ImmutableSet.of(), workSound);
    }

    static RegistryRef<VillagerProfession> register(String name, boolean canTradeWith, boolean important, boolean needsNoHome, ResourceKey<PoiType> heldWorkstation, ImmutableSet<Item> gatherableItems, ImmutableSet<Block> secondaryJobSites, @Nullable SoundEvent workSound) {
        return register(name, canTradeWith, important, needsNoHome, (entry) -> {
            return entry.is(heldWorkstation);
        }, (entry) -> {
            return entry.is(heldWorkstation);
        }, gatherableItems, secondaryJobSites, workSound);
    }

    static RegistryRef<VillagerProfession> register(String name, boolean canTradeWith, boolean important, boolean needsNoHome, Predicate<Holder<PoiType>> heldWorkstation, Predicate<Holder<PoiType>> acquirableWorkstation, ImmutableSet<Item> gatherableItems, ImmutableSet<Block> secondaryJobSites, @Nullable SoundEvent workSound) {
        ResourceLocation id = MCA.locate(name);
        RegistryRef<VillagerProfession> ref = RegistryRef.of(id, () -> {
            VillagerProfession result = new VillagerProfession(
                    id.toString().replace(':', '.'), heldWorkstation, acquirableWorkstation, gatherableItems, secondaryJobSites, workSound
            );
            if (!canTradeWith) {
                canNotTrade.add(result);
            }
            if (important) {
                isImportant.add(result);
            }
            if (needsNoHome) {
                ProfessionsMCA.needsNoHome.add(result);
            }
            return result;
        });
        PROFESSIONS.put(id, ref);
        return ref;
    }

    static void registerProfessions(MCA.RegisterHelper<VillagerProfession> helper) {
        PROFESSIONS.forEach((id, ref) -> helper.register(id, ref.get()));
    }

    static String getFavoredBuilding(VillagerProfession profession) {
        if (VillagerProfession.CARTOGRAPHER == profession || VillagerProfession.LIBRARIAN == profession || VillagerProfession.CLERIC == profession) {
            return "library";
        } else if (GUARD.get() == profession || ARCHER.get() == profession) {
            return "inn";
        }
        return null;
    }
}
