package net.conczin.mca.entity.ai;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.brain.sensor.ExplodingCreeperSensor;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.conczin.mca.entity.ai.brain.sensor.VillagerMCABabiesSensor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public interface SensorsMCA {
    Map<Identifier, SensorType<?>> SENSORS = new HashMap<>();

    SensorType<ExplodingCreeperSensor> EXPLODING_CREEPER = sensor("exploding_creeper", ExplodingCreeperSensor::new);
    SensorType<GuardEnemiesSensor> GUARD_ENEMIES = sensor("guard_enemies", GuardEnemiesSensor::new);
    SensorType<VillagerMCABabiesSensor> VILLAGER_BABIES = sensor("villager_babies_mca", VillagerMCABabiesSensor::new);

    static <T extends Sensor<?>> SensorType<T> sensor(String name, Supplier<T> factory) {
        SensorType<T> sensor = new SensorType<>(factory);
        SENSORS.put(MCA.locate(name), sensor);
        return sensor;
    }

    static void registerSensors(MCA.RegisterHelper<SensorType<?>> helper) {
        SENSORS.forEach(helper::register);
    }
}
