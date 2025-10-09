package net.mca.mixin;

import com.mojang.serialization.Codec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Optional;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

@Mixin(MemoryModuleType.class)
public interface MixinMemoryModuleType {
    @Invoker("<init>")
    static <U> MemoryModuleType<U> init(Optional<Codec<U>> codec) {
        return null;
    }
}
