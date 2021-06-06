package cobalt.minecraft.entity.merchant.villager;

import com.google.common.collect.ImmutableSet;
import lombok.Getter;
import mca.core.MCA;
import net.minecraft.entity.merchant.villager.VillagerProfession;
import net.minecraft.util.SoundEvent;
import net.minecraft.village.PointOfInterestType;

import java.lang.reflect.Constructor;

public class CVillagerProfession {
    @Getter
    private final VillagerProfession mcProfession;

    private CVillagerProfession(VillagerProfession profession) {
        this.mcProfession = profession;
    }

    public static CVillagerProfession fromMC(VillagerProfession profession) {
        return new CVillagerProfession(profession);
    }

    public static CVillagerProfession createNew(String name, PointOfInterestType poiType, SoundEvent sound) {
        return fromMC(new VillagerProfession(name, poiType, ImmutableSet.of(), ImmutableSet.of(), sound));
    }
}
