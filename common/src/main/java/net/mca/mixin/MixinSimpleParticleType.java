package net.mca.mixin;

import net.minecraft.core.particles.SimpleParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SimpleParticleType.class)
public interface MixinSimpleParticleType {
    @Invoker("<init>")
    static SimpleParticleType init(boolean alwaysShow) {
        return null;
    }
}
