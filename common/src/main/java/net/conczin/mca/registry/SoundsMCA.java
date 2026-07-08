package net.conczin.mca.registry;

import net.conczin.mca.MCA;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import java.util.HashMap;
import java.util.Map;

public interface SoundsMCA {
    Map<ResourceLocation, SoundEvent> SOUNDS = new HashMap<>();

    SoundEvent REAPER_SCYTHE_OUT = register("reaper.scythe_out");
    SoundEvent REAPER_SCYTHE_SWING = register("reaper.scythe_swing");
    SoundEvent REAPER_IDLE = register("reaper.idle");
    SoundEvent REAPER_DEATH = register("reaper.death");
    SoundEvent REAPER_BLOCK = register("reaper.block");
    SoundEvent REAPER_SUMMON = register("reaper.summon");

    SoundEvent VILLAGER_BABY_LAUGH = register("villager.baby.laugh");

    SoundEvent VILLAGER_MALE_SCREAM = register("villager.male.scream");
    SoundEvent VILLAGER_FEMALE_SCREAM = register("villager.female.scream");

    SoundEvent VILLAGER_MALE_HURT = register("villager.male.hurt");
    SoundEvent VILLAGER_FEMALE_HURT = register("villager.female.hurt");

    SoundEvent VILLAGER_MALE_LAUGH = register("villager.male.laugh");
    SoundEvent VILLAGER_FEMALE_LAUGH = register("villager.female.laugh");

    SoundEvent VILLAGER_MALE_CRY = register("villager.male.cry");
    SoundEvent VILLAGER_FEMALE_CRY = register("villager.female.cry");

    SoundEvent VILLAGER_MALE_ANGRY = register("villager.male.angry");
    SoundEvent VILLAGER_FEMALE_ANGRY = register("villager.female.angry");

    SoundEvent VILLAGER_MALE_CELEBRATE = register("villager.male.celebrate");
    SoundEvent VILLAGER_FEMALE_CELEBRATE = register("villager.female.celebrate");

    SoundEvent VILLAGER_MALE_GREET = register("villager.male.greet");
    SoundEvent VILLAGER_FEMALE_GREET = register("villager.female.greet");

    SoundEvent VILLAGER_MALE_SURPRISE = register("villager.male.surprise");
    SoundEvent VILLAGER_FEMALE_SURPRISE = register("villager.female.surprise");

    SoundEvent VILLAGER_MALE_YES = register("villager.male.yes");
    SoundEvent VILLAGER_FEMALE_YES = register("villager.female.yes");

    SoundEvent VILLAGER_MALE_NO = register("villager.male.no");
    SoundEvent VILLAGER_FEMALE_NO = register("villager.female.no");

    SoundEvent VILLAGER_MALE_COUGH = register("villager.male.cough");
    SoundEvent VILLAGER_FEMALE_COUGH = register("villager.female.cough");

    SoundEvent VILLAGER_MALE_SNORE = register("villager.male.snore");
    SoundEvent VILLAGER_FEMALE_SNORE = register("villager.female.snore");

    SoundEvent VILLAGER_MALE_SIRBEN = register("villager.male.sirben");
    SoundEvent VILLAGER_FEMALE_SIRBEN = register("villager.female.sirben");

    SoundEvent SILENT = register("silent");

    static SoundEvent register(String sound) {
        ResourceLocation id = MCA.locate(sound);
        SoundEvent event = SoundEvent.createVariableRangeEvent(id);
        SOUNDS.put(id, event);
        return event;
    }

    static void registerSounds(MCA.RegisterHelper<SoundEvent> helper) {
        SOUNDS.forEach(helper::register);
    }
}
