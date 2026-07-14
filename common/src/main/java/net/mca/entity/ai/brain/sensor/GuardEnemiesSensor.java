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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sensor to detect nearby enemies for guards or combat-active villagers.
 *
 * <p>The shared nearby-entity scan also supplies other villager behaviors. Non-guard villagers
 * skip only the guard-target prioritization unless they are following a player or already fighting.</p>
 */
public class GuardEnemiesSensor extends NearestLivingEntitiesSensor<LivingEntity> {
    private static final double GUARD_ENEMY_RANGE = 48.0;
    private static final double GUARD_ENEMY_RANGE_SQUARED = GUARD_ENEMY_RANGE * GUARD_ENEMY_RANGE;

    @Override
    public Set<MemoryModuleType<?>> getOutputMemoryModules() {
        return ImmutableSet.of(
                MemoryModuleType.MOBS,
                MemoryModuleType.VISIBLE_MOBS,
                MemoryModuleType.ATTACK_TARGET,
                MemoryModuleTypeMCA.PLAYER_FOLLOWING.get(),
                MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY.get()
        );
    }

    @Override
    protected void sense(ServerWorld world, LivingEntity entity) {
        super.sense(world, entity);

        if (!(entity instanceof VillagerEntityMCA villager)) {
            return;
        }

        boolean shouldScan = villager.isGuard()
                || villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get()).isPresent()
                || villager.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET).isPresent();

        if (!shouldScan) {
            villager.getBrain().forget(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY.get());
            return;
        }

        villager.getBrain().remember(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY.get(), getNearestHostile(villager));
    }

    private Optional<LivingEntity> getNearestHostile(VillagerEntityMCA entity) {
        return getVisibleMobs(entity).flatMap(list -> list.stream(target -> isGuardEnemy(target, entity))
                .filter(target -> isWithinGuardEnemyRange(entity, target))
                .min((a, b) -> compareEntities(entity, a, b)));
    }

    private boolean isWithinGuardEnemyRange(LivingEntity guard, LivingEntity target) {
        if (target.squaredDistanceTo(guard) <= GUARD_ENEMY_RANGE_SQUARED) {
            return true;
        }
        return getFollowedPlayer(guard)
                .filter(player -> target.squaredDistanceTo(player) <= GUARD_ENEMY_RANGE_SQUARED)
                .isPresent();
    }

    private Optional<LivingTargetCache> getVisibleMobs(LivingEntity entity) {
        return entity.getBrain().getOptionalMemory(MemoryModuleType.VISIBLE_MOBS);
    }

    private int compareEntities(LivingEntity entity, LivingEntity hostile1, LivingEntity hostile2) {
        int priorityComparison = getPriority(hostile2, entity) - getPriority(hostile1, entity);
        return priorityComparison == 0 ? compareDistances(entity, hostile1, hostile2) : priorityComparison;
    }

    private int compareDistances(LivingEntity entity, LivingEntity hostile1, LivingEntity hostile2) {
        return Double.compare(hostile1.squaredDistanceTo(entity), hostile2.squaredDistanceTo(entity));
    }

    public static boolean isGuardEnemy(LivingEntity entity, LivingEntity guard) {
        return getPriority(entity, guard) >= 0;
    }

    private static int getPriority(LivingEntity entity, LivingEntity guard) {
        if (entity instanceof VillagerEntityMCA villager) {
            return villager.isHostile() ? 10 : -1;
        }
        if (guard != null && entity instanceof MobEntity mob && mob.getTarget() == guard) {
            return 9;
        }

        Optional<Integer> configuredPriority = getConfiguredPriority(entity.getType());
        if (configuredPriority.isPresent()) {
            return configuredPriority.get();
        }

        Optional<PlayerEntity> followedPlayer = getFollowedPlayer(guard);
        if (followedPlayer.isPresent()) {
            PlayerEntity player = followedPlayer.get();
            if (entity instanceof MobEntity mob && mob.getTarget() == player) {
                return 9;
            }
            if (isFollowingDefenseEnemy(entity, player)) {
                return 3;
            }
        }

        if (Config.getInstance().guardsTargetMonsters && entity instanceof Monster) {
            return 3;
        }
        return -1;
    }

    private static Optional<Integer> getConfiguredPriority(EntityType<?> type) {
        Identifier id = Registries.ENTITY_TYPE.getId(type);
        if (Config.getInstance().guardsTargetEntities.containsKey(id.toString())) {
            return Optional.of(Config.getInstance().guardsTargetEntities.get(id.toString()));
        }
        return getTagPriority(type);
    }

    private static Optional<PlayerEntity> getFollowedPlayer(LivingEntity guard) {
        if (guard instanceof VillagerEntityMCA villager) {
            return villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING.get());
        }
        return Optional.empty();
    }

    private static boolean isFollowingDefenseEnemy(LivingEntity entity, PlayerEntity player) {
        return entity instanceof Monster && entity.squaredDistanceTo(player) <= GUARD_ENEMY_RANGE_SQUARED;
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
}
