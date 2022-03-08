package mca;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mca.mixin.MixinDefaultParticleType;
import net.minecraft.particle.DefaultParticleType;
import net.minecraft.particle.ParticleType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.function.Supplier;

public interface ParticleTypesMCA {

    DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(MCA.MOD_ID, Registry.PARTICLE_TYPE_KEY);

    RegistrySupplier<DefaultParticleType> POS_INTERACTION = register("pos_interaction", () -> MixinDefaultParticleType.init(false));
    RegistrySupplier<DefaultParticleType> NEG_INTERACTION = register("neg_interaction", () -> MixinDefaultParticleType.init(false));

    static void bootstrap() {
        PARTICLE_TYPES.register();
    }

    static <T extends ParticleType<?>> RegistrySupplier<T> register(String name, Supplier<T> type) {
        return PARTICLE_TYPES.register(new Identifier(MCA.MOD_ID, name), type);
    }
}
