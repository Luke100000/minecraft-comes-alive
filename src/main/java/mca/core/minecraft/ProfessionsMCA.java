package mca.core.minecraft;

import cobalt.minecraft.entity.merchant.villager.CVillagerProfession;
import com.google.common.collect.ImmutableSet;
import mca.core.MCA;
import mca.core.forge.Registration;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.SoundEvents;
import net.minecraft.village.PointOfInterestType;
import net.minecraftforge.fml.RegistryObject;

import java.util.*;

public class ProfessionsMCA {
    // as set of invalid professions
    private static final List<VillagerProfession> PROFESSIONS = new ArrayList<>(12);
    public static VillagerProfession CHILD = new VillagerProfession("child", PointOfInterestType.HOME, ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_FARMER);
    public static VillagerProfession GUARD = new VillagerProfession("guard", PointOfInterestType.ARMORER, ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_ARMORER);

    public static void register() {
        Registration.PROFESSIONS.register("child", () -> CHILD);
        Registration.PROFESSIONS.register("guard", () -> GUARD);

        // add vanilla professions
        PROFESSIONS.addAll(Arrays.asList(
                VillagerProfession.ARMORER,
                VillagerProfession.BUTCHER,
                VillagerProfession.CARTOGRAPHER,
                VillagerProfession.CLERIC,
                VillagerProfession.FARMER,
                VillagerProfession.FISHERMAN,
                VillagerProfession.FLETCHER,
                VillagerProfession.LEATHERWORKER,
                VillagerProfession.LIBRARIAN,
                VillagerProfession.MASON,
                VillagerProfession.NITWIT,
                GUARD
        ));
    }

    public static VillagerProfession randomProfession() {
        Random r = new Random();
        return PROFESSIONS.get(r.nextInt(PROFESSIONS.size()));
    }

    public static RegistryObject<VillagerProfession> registerProfession(String name, PointOfInterestType poiType, SoundEvent soundEvent) {
        return Registration.PROFESSIONS.register(name, () -> new VillagerProfession(name, poiType, ImmutableSet.of(), ImmutableSet.of(), soundEvent));
    }


}