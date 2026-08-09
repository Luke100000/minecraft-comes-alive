package net.mca.entity.ai;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.mca.MCA;
import net.mca.entity.ai.brain.sensor.ExplodingCreeperSensor;
import net.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.mca.entity.ai.brain.sensor.VillagerMCABabiesSensor;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.entity.ai.brain.sensor.SensorType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.function.Supplier;

public interface ActivityMCA {

    DeferredRegister<Activity> ACTIVITIES = DeferredRegister.create(MCA.MOD_ID, RegistryKeys.ACTIVITY);
    DeferredRegister<SensorType<?>> SENSORS = DeferredRegister.create(MCA.MOD_ID, RegistryKeys.SENSOR_TYPE);

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
        return ACTIVITIES.register(id, () -> new Activity(id.toString()));
    }

    static <T extends Sensor<?>> RegistrySupplier<SensorType<T>> sensor(String name, Supplier<T> factory) {
        Identifier id = new Identifier(MCA.MOD_ID, name);
        return SENSORS.register(id, () -> new SensorType<>(factory));
    }
}
