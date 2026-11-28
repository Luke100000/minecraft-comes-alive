package net.conczin.mca.entity.ai.brain.sensor;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestVisibleLivingEntitySensor;
import net.minecraft.world.entity.monster.Creeper;

public class ExplodingCreeperSensor extends NearestVisibleLivingEntitySensor {
    @Override
    protected void doTick(ServerLevel world, LivingEntity entity) {
        if (entity instanceof VillagerEntityMCA villager && getVisibleEntities(entity).isEmpty()) {
            // ExplodingCreeperSensor relies on GuardEnemiesSensor to populate NEAREST_VISIBLE_LIVING_ENTITIES
            // because vanilla NEAREST_LIVING_ENTITIES is omitted from VillagerTasksMCA.SENSOR_TYPES.
            GuardEnemiesSensor.refreshNearbyEntities(world, villager);
        }
        super.doTick(world, entity);
    }

    @Override
    protected boolean isMatchingEntity(LivingEntity entity, LivingEntity target) {
        return target instanceof Creeper
               && ((Creeper) target).isIgnited();
    }

    @Override
    protected MemoryModuleType<LivingEntity> getMemory() {
        return MemoryModuleTypeMCA.NEAREST_IGNITED_CREEPER;
    }
}
