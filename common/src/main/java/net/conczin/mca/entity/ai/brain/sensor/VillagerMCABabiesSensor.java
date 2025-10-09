package net.conczin.mca.entity.ai.brain.sensor;

import com.google.common.collect.ImmutableSet;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.Sensor;

import java.util.List;
import java.util.Set;

public class VillagerMCABabiesSensor extends Sensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleType.VISIBLE_VILLAGER_BABIES);
    }

    @Override
    protected void doTick(ServerLevel world, LivingEntity entity) {
        entity.getBrain().setMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES, getVisibleVillagerBabies(entity));
    }

    private List<LivingEntity> getVisibleVillagerBabies(LivingEntity entities) {
        return getVisibleMobs(entities).find(this::isVillagerBaby).toList();
    }

    private boolean isVillagerBaby(LivingEntity entity) {
        return (entity.getType() == EntitiesMCA.FEMALE_VILLAGER || entity.getType() == EntitiesMCA.MALE_VILLAGER) && entity.isBaby();
    }

    private NearestVisibleLivingEntities getVisibleMobs(LivingEntity entity) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES).orElseGet(NearestVisibleLivingEntities::empty);
    }
}
