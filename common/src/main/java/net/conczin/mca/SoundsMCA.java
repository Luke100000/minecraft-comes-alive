package net.conczin.mca;

import net.conczin.mca.util.RegistryRef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.LinkedHashMap;
import java.util.Map;

public interface SoundsMCA {
    Map<ResourceLocation, RegistryRef<SoundEvent>> SOUNDS = new LinkedHashMap<>();
    
    RegistryRef<SoundEvent> REAPER_SCYTHE_OUT = register("reaper.scythe_out");
    RegistryRef<SoundEvent> REAPER_SCYTHE_SWING = register("reaper.scythe_swing");
    RegistryRef<SoundEvent> REAPER_IDLE = register("reaper.idle");
    RegistryRef<SoundEvent> REAPER_DEATH = register("reaper.death");
    RegistryRef<SoundEvent> REAPER_BLOCK = register("reaper.block");
    RegistryRef<SoundEvent> REAPER_SUMMON = register("reaper.summon");

    RegistryRef<SoundEvent> VILLAGER_BABY_LAUGH = register("villager.baby.laugh");

    RegistryRef<SoundEvent> VILLAGER_MALE_SCREAM = register("villager.male.scream");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_SCREAM = register("villager.female.scream");

    RegistryRef<SoundEvent> VILLAGER_MALE_HURT = register("villager.male.hurt");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_HURT = register("villager.female.hurt");

    RegistryRef<SoundEvent> VILLAGER_MALE_LAUGH = register("villager.male.laugh");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_LAUGH = register("villager.female.laugh");

    RegistryRef<SoundEvent> VILLAGER_MALE_CRY = register("villager.male.cry");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_CRY = register("villager.female.cry");

    RegistryRef<SoundEvent> VILLAGER_MALE_ANGRY = register("villager.male.angry");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_ANGRY = register("villager.female.angry");

    RegistryRef<SoundEvent> VILLAGER_MALE_CELEBRATE = register("villager.male.celebrate");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_CELEBRATE = register("villager.female.celebrate");

    RegistryRef<SoundEvent> VILLAGER_MALE_GREET = register("villager.male.greet");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_GREET = register("villager.female.greet");

    RegistryRef<SoundEvent> VILLAGER_MALE_SURPRISE = register("villager.male.surprise");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_SURPRISE = register("villager.female.surprise");

    RegistryRef<SoundEvent> VILLAGER_MALE_YES = register("villager.male.yes");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_YES = register("villager.female.yes");

    RegistryRef<SoundEvent> VILLAGER_MALE_NO = register("villager.male.no");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_NO = register("villager.female.no");

    RegistryRef<SoundEvent> VILLAGER_MALE_COUGH = register("villager.male.cough");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_COUGH = register("villager.female.cough");

    RegistryRef<SoundEvent> VILLAGER_MALE_SNORE = register("villager.male.snore");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_SNORE = register("villager.female.snore");

    RegistryRef<SoundEvent> VILLAGER_MALE_SIRBEN = register("villager.male.sirben");
    RegistryRef<SoundEvent> VILLAGER_FEMALE_SIRBEN = register("villager.female.sirben");

    RegistryRef<SoundEvent> SILENT = register("silent");

    static RegistryRef<SoundEvent> register(String sound) {
        ResourceLocation id = MCA.locate(sound);
        RegistryRef<SoundEvent> ref = RegistryRef.of(id, () -> SoundEvent.createVariableRangeEvent(id));
        SOUNDS.put(id, ref);
        return ref;
    }

    static void registerSounds(MCA.RegisterHelper<SoundEvent> helper) {
        SOUNDS.forEach((id, ref) -> helper.register(id, ref.get()));
    }
}
