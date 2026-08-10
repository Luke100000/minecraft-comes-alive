package net.mca.entity.ai;

import dev.architectury.registry.registries.DeferredRegister;
import dev.architectury.registry.registries.RegistrySupplier;
import net.mca.MCA;
import net.mca.entity.ai.brain.sensor.ExplodingCreeperSensor;
import net.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.mca.entity.ai.brain.sensor.VillagerMCABabiesSensor;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import java.util.function.Supplier;

public interface ActivityMCA {

    DeferredRegister<Activity> ACTIVITIES = DeferredRegister.create(MCA.MOD_ID, Registries.ACTIVITY);
    DeferredRegister<SensorType<?>> SENSORS = DeferredRegister.create(MCA.MOD_ID, Registries.SENSOR_TYPE);

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
        ResourceLocation id = new ResourceLocation(MCA.MOD_ID, name);
        return ACTIVITIES.register(id, () -> new Activity(id.toString()));
    }

    static <T extends Sensor<?>> RegistrySupplier<SensorType<T>> sensor(String name, Supplier<T> factory) {
        ResourceLocation id = new ResourceLocation(MCA.MOD_ID, name);
        return SENSORS.register(id, () -> new SensorType<>(factory));
    }
}
