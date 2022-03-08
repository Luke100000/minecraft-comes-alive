package mca;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public interface SoundsMCA {
    
    DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(MCA.MOD_ID, Registry.SOUND_EVENT_KEY);
    
    RegistrySupplier<SoundEvent> reaper_scythe_out = register("reaper.scythe_out");
    RegistrySupplier<SoundEvent> reaper_scythe_swing = register("reaper.scythe_swing");
    RegistrySupplier<SoundEvent> reaper_idle = register("reaper.idle");
    RegistrySupplier<SoundEvent> reaper_death = register("reaper.death");
    RegistrySupplier<SoundEvent> reaper_block = register("reaper.block");
    RegistrySupplier<SoundEvent> reaper_summon = register("reaper.summon");

    RegistrySupplier<SoundEvent> VILLAGER_BABY_LAUGH = register("villager_baby_laugh");//TODO:

    RegistrySupplier<SoundEvent> VILLAGER_MALE_SCREAM = register("villager_male_scream");
    RegistrySupplier<SoundEvent> VILLAGER_FEMALE_SCREAM = register("villager_female_scream");

    RegistrySupplier<SoundEvent> VILLAGER_MALE_LAUGH = register("villager_male_laugh");
    RegistrySupplier<SoundEvent> VILLAGER_FEMALE_LAUGH = register("villager_female_laugh");//TODO:

    RegistrySupplier<SoundEvent> VILLAGER_MALE_CRY = register("villager_male_cry");
    RegistrySupplier<SoundEvent> VILLAGER_FEMALE_CRY = register("villager_female_cry");//TODO:

    RegistrySupplier<SoundEvent> VILLAGER_MALE_ANGRY = register("villager_male_angry");//TODO:
    RegistrySupplier<SoundEvent> VILLAGER_FEMALE_ANGRY = register("villager_female_angry");//TODO:

    RegistrySupplier<SoundEvent> VILLAGER_MALE_GREET = register("villager_male_greet");
    RegistrySupplier<SoundEvent> VILLAGER_FEMALE_GREET = register("villager_female_greet");//TODO:

    RegistrySupplier<SoundEvent> VILLAGER_MALE_SURPRISE = register("villager_male_surprise");
    RegistrySupplier<SoundEvent> VILLAGER_FEMALE_SURPRISE = register("villager_female_surprise");//TODO:

    static void bootstrap() {
        SOUNDS.register();
    }

    static RegistrySupplier<SoundEvent> register(String sound) {
        Identifier id = new Identifier(MCA.MOD_ID, sound);
        return SOUNDS.register(id, () -> new SoundEvent(id));
    }
}
