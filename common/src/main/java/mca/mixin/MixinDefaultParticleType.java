package mca.mixin;

import net.minecraft.particle.DefaultParticleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(DefaultParticleType.class)
public interface MixinDefaultParticleType {
    @Invoker("<init>")
    static DefaultParticleType init(boolean alwaysShow) {
        return null;
    }
}
