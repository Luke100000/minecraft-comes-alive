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
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.OwnableEntity;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class GuardEnemiesSensor extends NearestLivingEntitySensor<LivingEntity> {
    private static final double GUARD_ENEMY_RANGE = 48.0;
    private static final double GUARD_ENEMY_RANGE_SQR = GUARD_ENEMY_RANGE * GUARD_ENEMY_RANGE;

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(
                MemoryModuleType.NEAREST_LIVING_ENTITIES,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY,
                MemoryModuleTypeMCA.PLAYER_FOLLOWING
        );
    }

    @Override
    protected void doTick(ServerLevel world, LivingEntity entity) {
        super.doTick(world, entity);
        entity.getBrain().setMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY, this.getNearestHostile(entity));
    }

    private Optional<LivingEntity> getNearestHostile(LivingEntity entity) {
        LivingEntity followedPlayer = entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING)
                .map(player -> (LivingEntity) player)
                .orElse(null);

        return getVisibleMobs(entity).flatMap((list) -> list.find(e -> isGuardEnemy(e, entity, followedPlayer))
                .filter(e -> isWithinGuardRange(e, entity, followedPlayer))
                .min((a, b) -> this.compareEntities(entity, followedPlayer, a, b)));
    }

    private boolean isWithinGuardRange(LivingEntity hostile, LivingEntity guard, LivingEntity followedPlayer) {
        return hostile.distanceToSqr(guard) <= GUARD_ENEMY_RANGE_SQR
                || followedPlayer != null && hostile.distanceToSqr(followedPlayer) <= GUARD_ENEMY_RANGE_SQR;
    }

    private Optional<NearestVisibleLivingEntities> getVisibleMobs(LivingEntity entity) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
    }

    private int compareEntities(LivingEntity guard, LivingEntity followedPlayer, LivingEntity hostile1, LivingEntity hostile2) {
        boolean hostile1TargetsFollowed = isTargeting(hostile1, followedPlayer);
        boolean hostile2TargetsFollowed = isTargeting(hostile2, followedPlayer);
        if (hostile1TargetsFollowed != hostile2TargetsFollowed) {
            return hostile1TargetsFollowed ? -1 : 1;
        }

        int i = getPriority(hostile2, guard, followedPlayer) - getPriority(hostile1, guard, followedPlayer);
        return i == 0 ? compareDistances(guard, hostile1, hostile2) : i;
    }

    private boolean isTargeting(LivingEntity hostile, LivingEntity target) {
        return target != null && hostile instanceof Mob mob && mob.getTarget() == target;
    }

    private int compareDistances(LivingEntity entity, LivingEntity hostile1, LivingEntity hostile2) {
        return Mth.floor(hostile1.distanceToSqr(entity) - hostile2.distanceToSqr(entity));
    }

    public static boolean isGuardEnemy(LivingEntity entity, LivingEntity guard) {
        return getPriority(entity, guard, null) >= 0;
    }

    private static boolean isGuardEnemy(LivingEntity entity, LivingEntity guard, LivingEntity followedPlayer) {
        return getPriority(entity, guard, followedPlayer) >= 0;
    }

    private static int getPriority(LivingEntity entity, LivingEntity guard, LivingEntity followedPlayer) {
        if (entity instanceof VillagerEntityMCA villager) {
            return villager.isHostile() ? 10 : -1;
        } else if (guard != null && entity instanceof Mob mob && (mob.getTarget() == guard || followedPlayer != null && mob.getTarget() == followedPlayer)) {
            //priority is irrelevant if this entity is currently an active threat
            return 9;
        } else if (entity instanceof OwnableEntity ownable && ownable.getOwnerReference() != null) {
            return -1;
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

    private static Optional<Integer> getTagPriority(EntityType<?> type) {
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
}
