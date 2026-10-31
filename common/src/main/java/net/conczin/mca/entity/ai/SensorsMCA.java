package net.conczin.mca.entity.ai;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.brain.sensor.ExplodingCreeperSensor;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.conczin.mca.entity.ai.brain.sensor.VillagerMCABabiesSensor;
import net.conczin.mca.entity.ai.brain.sensor.SleepingAwareVanillaSensors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public interface SensorsMCA {
    Map<ResourceLocation, SensorType<?>> SENSORS = new HashMap<>();

    SensorType<ExplodingCreeperSensor> EXPLODING_CREEPER = sensor("exploding_creeper", ExplodingCreeperSensor::new);
    SensorType<GuardEnemiesSensor> GUARD_ENEMIES = sensor("guard_enemies", GuardEnemiesSensor::new);
    SensorType<VillagerMCABabiesSensor> VILLAGER_BABIES = sensor("villager_babies_mca", VillagerMCABabiesSensor::new);
    SensorType<SleepingAwareVanillaSensors.NearestItems> SLEEP_AWARE_ITEMS = sensor("sleep_aware_items", SleepingAwareVanillaSensors.NearestItems::new);
    SensorType<SleepingAwareVanillaSensors.NearestBed> SLEEP_AWARE_BED = sensor("sleep_aware_bed", SleepingAwareVanillaSensors.NearestBed::new);
    SensorType<SleepingAwareVanillaSensors.SecondaryPois> SLEEP_AWARE_SECONDARY_POIS = sensor("sleep_aware_secondary_pois", SleepingAwareVanillaSensors.SecondaryPois::new);
    SensorType<SleepingAwareVanillaSensors.GolemDetected> SLEEP_AWARE_GOLEM = sensor("sleep_aware_golem_detected", SleepingAwareVanillaSensors.GolemDetected::new);

    static <T extends Sensor<?>> SensorType<T> sensor(String name, Supplier<T> factory) {
        SensorType<T> sensor = new SensorType<>(factory);
        SENSORS.put(MCA.locate(name), sensor);
        return sensor;
    }

    static void registerSensors(MCA.RegisterHelper<SensorType<?>> helper) {
        SENSORS.forEach(helper::register);
    }
}
