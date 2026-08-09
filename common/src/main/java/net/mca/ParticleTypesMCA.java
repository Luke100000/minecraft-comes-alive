package net.mca;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.particle.ParticleType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

public interface ParticleTypesMCA {

    DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(MCA.MOD_ID, RegistryKeys.PARTICLE_TYPE);

    RegistrySupplier<DefaultParticleType> POS_INTERACTION = register("pos_interaction", () -> new DefaultParticleType(false));
    RegistrySupplier<DefaultParticleType> NEG_INTERACTION = register("neg_interaction", () -> new DefaultParticleType(false));

    static void bootstrap() {
        PARTICLE_TYPES.register();
    }

    static <T extends ParticleType<?>> RegistrySupplier<T> register(String name, Supplier<T> type) {
        return PARTICLE_TYPES.register(new Identifier(MCA.MOD_ID, name), type);
    }
}
