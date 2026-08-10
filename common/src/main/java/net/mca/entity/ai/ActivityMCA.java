package net.mca.entity.ai;

import net.mca.MCA;
import net.mca.util.RegistryRef;
import net.mca.entity.ai.brain.sensor.ExplodingCreeperSensor;
import net.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.mca.entity.ai.brain.sensor.VillagerMCABabiesSensor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public interface ActivityMCA {

    Map<ResourceLocation, RegistryRef<Activity>> ACTIVITIES = new LinkedHashMap<>();
    Map<ResourceLocation, RegistryRef<? extends SensorType<?>>> SENSORS = new LinkedHashMap<>();

    RegistryRef<Activity> CHORE = activity("chore");
    RegistryRef<Activity> GRIEVE = activity("grieve");

    RegistryRef<SensorType<ExplodingCreeperSensor>> EXPLODING_CREEPER = sensor("exploding_creeper", ExplodingCreeperSensor::new);
    RegistryRef<SensorType<GuardEnemiesSensor>> GUARD_ENEMIES = sensor("guard_enemies", GuardEnemiesSensor::new);
    RegistryRef<SensorType<VillagerMCABabiesSensor>> VILLAGER_BABIES = sensor("villager_babies_mca", VillagerMCABabiesSensor::new);

    static RegistryRef<Activity> activity(String name) {
        ResourceLocation id = MCA.locate(name);
        RegistryRef<Activity> ref = RegistryRef.of(id, () -> new Activity(id.toString()));
        ACTIVITIES.put(id, ref);
        return ref;
    }

    static <T extends Sensor<?>> RegistryRef<SensorType<T>> sensor(String name, Supplier<T> factory) {
        ResourceLocation id = MCA.locate(name);
        RegistryRef<SensorType<T>> ref = RegistryRef.of(id, () -> new SensorType<>(factory));
        SENSORS.put(id, ref);
        return ref;
    }

    static void registerActivities(MCA.RegisterHelper<Activity> helper) {
        ACTIVITIES.forEach((id, ref) -> helper.register(id, ref.get()));
    }

    static void registerSensors(MCA.RegisterHelper<SensorType<?>> helper) {
        SENSORS.forEach((id, ref) -> helper.register(id, ref.get()));
    }
}
