package net.mca;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

public interface ParticleTypesMCA {

    DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(MCA.MOD_ID, Registries.PARTICLE_TYPE);

    RegistrySupplier<SimpleParticleType> POS_INTERACTION = register("pos_interaction", () -> new SimpleParticleType(false));
    RegistrySupplier<SimpleParticleType> NEG_INTERACTION = register("neg_interaction", () -> new SimpleParticleType(false));

    static void bootstrap() {
        PARTICLE_TYPES.register();
    }

    static <T extends ParticleType<?>> RegistrySupplier<T> register(String name, Supplier<T> type) {
        return PARTICLE_TYPES.register(new ResourceLocation(MCA.MOD_ID, name), type);
    }
}
