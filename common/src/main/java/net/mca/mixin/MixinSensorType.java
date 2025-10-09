package net.mca.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.function.Supplier;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

@Mixin(SensorType.class)
public interface MixinSensorType {
    @Invoker("<init>")
    static <U extends Sensor<?>> SensorType<U> init(Supplier<U> supplier) {
        return null;
    }
}
