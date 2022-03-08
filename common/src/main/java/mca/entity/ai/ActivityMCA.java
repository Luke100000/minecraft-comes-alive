package mca.entity.ai;

import java.util.function.Supplier;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import mca.MCA;
import mca.entity.ai.brain.sensor.ExplodingCreeperSensor;
import mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import mca.entity.ai.brain.sensor.VillagerMCABabiesSensor;
import mca.mixin.MixinActivity;
import mca.mixin.MixinSensorType;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public interface ActivityMCA {

    DeferredRegister<Activity> ACTIVITIES = DeferredRegister.create(MCA.MOD_ID, Registry.ACTIVITY_KEY);
    DeferredRegister<SensorType<?>> SENSORS = DeferredRegister.create(MCA.MOD_ID, Registry.SENSOR_TYPE_KEY);

    RegistrySupplier<Activity> CHORE = activity("chore");
    RegistrySupplier<Activity> GRIEVE = activity("grieve");

    RegistrySupplier<SensorType<ExplodingCreeperSensor>> EXPLODING_CREEPER = sensor("exploding_creeper", ExplodingCreeperSensor::new);
    RegistrySupplier<SensorType<GuardEnemiesSensor>> GUARD_ENEMIES = sensor("guard_enemies", GuardEnemiesSensor::new);
    RegistrySupplier<SensorType<VillagerMCABabiesSensor>> VILLAGER_BABIES = sensor("villager_babies_mca", VillagerMCABabiesSensor::new);

    static void bootstrap() {
        ACTIVITIES.register();
        SENSORS.register();
    }

    static RegistrySupplier<Activity> activity(String name) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return ACTIVITIES.register(id, () -> MixinActivity.init(id.toString()));
    }

    static <T extends Sensor<?>> RegistrySupplier<SensorType<T>> sensor(String name, Supplier<T> factory) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return SENSORS.register(id, () -> MixinSensorType.init(factory));
    }
}
