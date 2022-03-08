package mca.entity.ai;

import java.util.HashSet;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mca.MCA;
import mca.mixin.MixinVillagerProfession;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.village.VillagerProfession;
import net.minecraft.world.poi.PointOfInterestType;
import org.jetbrains.annotations.Nullable;

public interface ProfessionsMCA {
    DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(MCA.MOD_ID, Registry.VILLAGER_PROFESSION_KEY);

    RegistrySupplier<VillagerProfession> OUTLAW = register("outlaw", false, PointOfInterestType.UNEMPLOYED, SoundEvents.ENTITY_VILLAGER_WORK_FARMER);
    RegistrySupplier<VillagerProfession> GUARD = register("guard", false, PointOfInterestType.UNEMPLOYED, SoundEvents.ENTITY_VILLAGER_WORK_ARMORER);
    RegistrySupplier<VillagerProfession> ARCHER = register("archer", true, PointOfInterestType.UNEMPLOYED, SoundEvents.ENTITY_VILLAGER_WORK_FLETCHER);
    // VillagerProfession JEWELER = register("jeweler", PointOfInterestTypeMCA.JEWELER, SoundEvents.ENTITY_VILLAGER_WORK_ARMORER);

    Set<VillagerProfession> canNotTrade = new HashSet<>();

    static void bootstrap() {
        PROFESSIONS.register();
        PointOfInterestTypeMCA.bootstrap();

        canNotTrade.add(VillagerProfession.NONE);
        canNotTrade.add(VillagerProfession.NITWIT);
    }

    static RegistrySupplier<VillagerProfession> register(String name, boolean canTradeWith, PointOfInterestType workStation, @Nullable SoundEvent workSound) {
        return register(name, canTradeWith, workStation, ImmutableSet.of(), ImmutableSet.of(), workSound);
    }

    static RegistrySupplier<VillagerProfession> register(String name, boolean canTradeWith, PointOfInterestType workStation, ImmutableSet<Item> gatherableItems, ImmutableSet<Block> secondaryJobSites, @Nullable SoundEvent workSound) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return PROFESSIONS.register(id, () -> {
            VillagerProfession result = MixinVillagerProfession.init(
                    id.toString().replace(':', '.'), workStation, gatherableItems, secondaryJobSites, workSound
            );
            if (!canTradeWith) {
                canNotTrade.add(result);
            }
            return result;
        });
    }

    static String getFavoredBuilding(VillagerProfession profession) {
        if (VillagerProfession.CARTOGRAPHER == profession || VillagerProfession.LIBRARIAN == profession || VillagerProfession.CLERIC == profession) {
            return "library";
        } else if (GUARD == profession || ARCHER == profession) {
            return "inn";
        }
        return null;
    }
}
