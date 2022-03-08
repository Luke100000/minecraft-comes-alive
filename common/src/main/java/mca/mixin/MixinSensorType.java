package mca.mixin;

import java.util.function.Supplier;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;

@Mixin(SensorType.class)
public interface MixinSensorType {
    @Invoker("<init>")
    static <U extends Sensor<?>> SensorType<U> init(Supplier<U> supplier) {
        return null;
    }
}
