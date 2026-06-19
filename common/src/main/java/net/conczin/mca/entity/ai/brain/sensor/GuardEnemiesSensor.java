package net.conczin.mca.entity.ai.brain.sensor;

import com.google.common.collect.ImmutableSet;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.monster.Enemy;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GuardEnemiesSensor extends Sensor<LivingEntity> {
    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
    }

    @Override
    protected void doTick(ServerLevel world, LivingEntity entity) {
        entity.getBrain().setMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY, this.getNearestHostile(entity));
    }

    private Optional<LivingEntity> getNearestHostile(LivingEntity entity) {
        return getVisibleMobs(entity).flatMap((list) -> list.find(this::isHostile)
                .filter(e -> e.distanceToSqr(entity) <= 48.0 * 48.0)
                .min((a, b) -> this.compareEntities(entity, a, b)));
    }

    private Optional<NearestVisibleLivingEntities> getVisibleMobs(LivingEntity entity) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
    }

    private int compareEntities(LivingEntity entity, LivingEntity hostile1, LivingEntity hostile2) {
        int i = getPriority(hostile2, entity) - getPriority(hostile1, entity);
        return i == 0 ? compareDistances(entity, hostile1, hostile2) : i;
    }

    private int compareDistances(LivingEntity entity, LivingEntity hostile1, LivingEntity hostile2) {
        return Mth.floor(hostile1.distanceToSqr(entity) - hostile2.distanceToSqr(entity));
    }

    private int getPriority(LivingEntity entity, LivingEntity guard) {
        if (entity instanceof VillagerEntityMCA villager) {
            return villager.isHostile() ? 10 : -1;
        } else if (guard != null && entity instanceof Mob mob && mob.getTarget() == guard) {
            //priority is irrelevant if this entity is currently an active threat
            return 9;
        } else {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
            if (Config.getInstance().guardsTargetEntities.containsKey(id.toString())) {
                return Config.getInstance().guardsTargetEntities.get(id.toString());
            } else {
                Optional<Integer> tagPriority = getTagPriority(entity.getType());
                if (tagPriority.isPresent()) {
                    return tagPriority.get();
                }
            }

            if (Config.getInstance().guardsTargetMonsters && entity instanceof Enemy) {
                return 3;
            } else {
                return -1;
            }
        }
    }

    private Optional<Integer> getTagPriority(EntityType<?> type) {
        for (Map.Entry<String, Integer> entry : Config.getInstance().guardsTargetEntities.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("#")) {
                Identifier id = Identifier.tryParse(key.substring(1));
                if (id != null && BuiltInRegistries.ENTITY_TYPE.wrapAsHolder(type).is(TagKey.create(Registries.ENTITY_TYPE, id))) {
                    return Optional.of(entry.getValue());
                }
            }
        }
        return Optional.empty();
    }

    private boolean isHostile(LivingEntity entity) {
        return getPriority(entity, null) >= 0;
    }
}
