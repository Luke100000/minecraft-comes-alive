package net.mca.entity.ai.brain.sensor;

import com.google.common.collect.ImmutableSet;
import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.util.RegistryHelper;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.LivingTargetCache;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.sensor.NearestLivingEntitiesSensor;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GuardEnemiesSensor extends NearestLivingEntitiesSensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> getOutputMemoryModules() {
        return ImmutableSet.of(
                MemoryModuleType.MOBS,
                MemoryModuleType.VISIBLE_MOBS,
                MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY.get()
        );
    }

    @Override
    protected void sense(ServerWorld world, LivingEntity entity) {
        super.sense(world, entity);
        entity.getBrain().remember(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY.get(), this.getNearestHostile(entity));
    }

    private Optional<LivingEntity> getNearestHostile(LivingEntity entity) {
        return getVisibleMobs(entity).flatMap((list) -> list.stream(e -> isGuardEnemy(e, entity))
                .filter(e -> e.squaredDistanceTo(entity) <= 48.0 * 48.0)
                .min((a, b) -> this.compareEntities(entity, a, b)));
    }

    private Optional<LivingTargetCache> getVisibleMobs(LivingEntity entity) {
        return entity.getBrain().getOptionalMemory(MemoryModuleType.VISIBLE_MOBS);
    }

    private int compareEntities(LivingEntity entity, LivingEntity hostile1, LivingEntity hostile2) {
        int i = getPriority(hostile2, entity) - getPriority(hostile1, entity);
        return i == 0 ? compareDistances(entity, hostile1, hostile2) : i;
    }

    private int compareDistances(LivingEntity entity, LivingEntity hostile1, LivingEntity hostile2) {
        return MathHelper.floor(hostile1.squaredDistanceTo(entity) - hostile2.squaredDistanceTo(entity));
    }

    public static boolean isGuardEnemy(LivingEntity entity, LivingEntity guard) {
        return getPriority(entity, guard) >= 0;
    }

    private static int getPriority(LivingEntity entity, LivingEntity guard) {
        if (entity instanceof VillagerEntityMCA villager) {
            return villager.isHostile() ? 10 : -1;
        } else if (guard != null && entity instanceof MobEntity && (((MobEntity)entity).getTarget() == guard)) {
            //priority is irrelevant if this entity is currently an active threat
            return 9;
        } else {
            Identifier id = Registries.ENTITY_TYPE.getId(entity.getType());
            if (Config.getInstance().guardsTargetEntities.containsKey(id.toString())) {
                return Config.getInstance().guardsTargetEntities.get(id.toString());
            } else {
                Optional<Integer> tagPriority = getTagPriority(entity.getType());
                if (tagPriority.isPresent()) {
                    return tagPriority.get();
                }
            }

            if (Config.getInstance().guardsTargetMonsters && entity instanceof Monster) {
                return 3;
            } else {
                return -1;
            }
        }
    }

    private static Optional<Integer> getTagPriority(EntityType<?> type) {
        for (Map.Entry<String, Integer> entry : Config.getInstance().guardsTargetEntities.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("#")) {
                Identifier id = Identifier.tryParse(key.substring(1));
                if (id != null && RegistryHelper.isObjectInTag(Registries.ENTITY_TYPE, id, type)) {
                    return Optional.of(entry.getValue());
                }
            }
        }
        return Optional.empty();
    }

    @Override
    protected int getHorizontalExpansion() {
        return 48;
    }

    @Override
    protected int getHeightExpansion() {
        return 48;
    }
}
