package net.mca;

import net.mca.util.RegistryRef;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

public interface ParticleTypesMCA {

    Map<ResourceLocation, RegistryRef<? extends ParticleType<?>>> PARTICLE_TYPES = new LinkedHashMap<>();

    RegistryRef<SimpleParticleType> POS_INTERACTION = register("pos_interaction", () -> new SimpleParticleType(false));
    RegistryRef<SimpleParticleType> NEG_INTERACTION = register("neg_interaction", () -> new SimpleParticleType(false));

    static <T extends ParticleType<?>> RegistryRef<T> register(String name, Supplier<T> type) {
        ResourceLocation id = MCA.locate(name);
        RegistryRef<T> ref = RegistryRef.of(id, type);
        PARTICLE_TYPES.put(id, ref);
        return ref;
    }

    static void registerParticles(MCA.RegisterHelper<ParticleType<?>> helper) {
        PARTICLE_TYPES.forEach((id, ref) -> helper.register(id, ref.get()));
    }
}
