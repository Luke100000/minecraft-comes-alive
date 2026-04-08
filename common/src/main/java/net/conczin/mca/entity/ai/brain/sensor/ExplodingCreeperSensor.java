package net.conczin.mca.entity.ai.brain.sensor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.NearestVisibleLivingEntitySensor;
import net.minecraft.world.entity.monster.Creeper;

public class ExplodingCreeperSensor extends NearestVisibleLivingEntitySensor {
    @Override
    protected boolean isMatchingEntity(ServerLevel level, LivingEntity body, LivingEntity target) {
        return target instanceof Creeper
               && ((Creeper) target).isIgnited();
    }

    @Override
    protected MemoryModuleType<LivingEntity> getMemoryToSet() {
        return MemoryModuleType.NEAREST_HOSTILE;
    }
}
