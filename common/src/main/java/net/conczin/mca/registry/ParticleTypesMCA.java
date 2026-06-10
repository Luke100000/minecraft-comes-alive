package net.conczin.mca.registry;

import net.conczin.mca.MCA;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public interface ParticleTypesMCA {
    Map<Identifier, SimpleParticleType> PARTICLES = new HashMap<>();

    SimpleParticleType POS_INTERACTION = register("pos_interaction", new SimpleParticleType(false));
    SimpleParticleType NEG_INTERACTION = register("neg_interaction", new SimpleParticleType(false));

    static SimpleParticleType register(String name, SimpleParticleType type) {
        PARTICLES.put(MCA.locate(name), type);
        return type;
    }

    static void registerParticles(MCA.RegisterHelper<ParticleType<?>> helper) {
        PARTICLES.forEach(helper::register);
    }
}
