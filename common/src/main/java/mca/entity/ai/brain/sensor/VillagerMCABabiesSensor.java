package mca.entity.ai.brain.sensor;

import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;
import mca.entity.EntitiesMCA;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.LivingTargetCache;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.Sensor;
import net.minecraft.server.world.ServerWorld;

public class VillagerMCABabiesSensor extends Sensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> getOutputMemoryModules() {
        return ImmutableSet.of(MemoryModuleType.VISIBLE_VILLAGER_BABIES);
    }

    @Override
    protected void sense(ServerWorld world, LivingEntity entity) {
        entity.getBrain().remember(MemoryModuleType.VISIBLE_VILLAGER_BABIES, getVisibleVillagerBabies(entity));
    }

    private List<LivingEntity> getVisibleVillagerBabies(LivingEntity entities) {
        return getVisibleMobs(entities).stream(this::isVillagerBaby).toList();
    }

    private boolean isVillagerBaby(LivingEntity entity) {
        return (entity.getType() == EntitiesMCA.FEMALE_VILLAGER.get() || entity.getType() == EntitiesMCA.MALE_VILLAGER.get()) && entity.isBaby();
    }

    private LivingTargetCache getVisibleMobs(LivingEntity entity) {
        return entity.getBrain().getOptionalMemory(MemoryModuleType.VISIBLE_MOBS).orElseGet(LivingTargetCache::empty);
    }
}
