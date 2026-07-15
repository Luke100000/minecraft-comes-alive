package net.conczin.mca.entity.ai.brain.sensor;

import com.google.common.collect.ImmutableSet;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.util.RegistryHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.NearestLivingEntitySensor;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sensor to detect nearby enemies for guards or combat-active villagers.
 * <p>
 * The shared nearby-entity scan also supplies other villager behaviors. Non-guard villagers skip only
 * guard-target prioritization unless they are following a player or already fighting.
 */
public class GuardEnemiesSensor extends NearestLivingEntitySensor<LivingEntity> {
    private static final double GUARD_ENEMY_RANGE = 48.0;
    private static final double GUARD_ENEMY_RANGE_SQR = GUARD_ENEMY_RANGE * GUARD_ENEMY_RANGE;

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(
                MemoryModuleType.NEAREST_LIVING_ENTITIES,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryModuleType.ATTACK_TARGET,
                MemoryModuleTypeMCA.PLAYER_FOLLOWING,
                MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY
        );
    }

    @Override
    protected void doTick(ServerLevel world, LivingEntity entity) {
        super.doTick(world, entity);

        if (!(entity instanceof VillagerEntityMCA villager)) {
            return;
        }

        // Performance optimization: only scan if the villager is a guard,
        // is following a player, or has an active attack target.
        // Otherwise, skip the expensive scan and clear any remaining guard enemy memory.
        boolean shouldScan = villager.isGuard()
                || villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING).isPresent()
                || villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isPresent();

        if (!shouldScan) {
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
            return;
        }

        villager.getBrain().setMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY, this.getNearestHostile(villager));
    }

    private Optional<LivingEntity> getNearestHostile(VillagerEntityMCA entity) {
        return getVisibleMobs(entity).flatMap((list) -> list.find(target -> isGuardEnemy(target, entity))
                .filter(target -> isWithinGuardEnemyRange(entity, target))
                .min((a, b) -> this.compareEntities(entity, a, b)));
    }

    private boolean isWithinGuardEnemyRange(LivingEntity guard, LivingEntity target) {
        if (target.distanceToSqr(guard) <= GUARD_ENEMY_RANGE_SQR) {
            return true;
        }
        return getFollowedPlayer(guard)
                .filter(player -> target.distanceToSqr(player) <= GUARD_ENEMY_RANGE_SQR)
                .isPresent();
    }

    private Optional<NearestVisibleLivingEntities> getVisibleMobs(LivingEntity entity) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
    }

    private int compareEntities(LivingEntity entity, LivingEntity hostile1, LivingEntity hostile2) {
        int i = getPriority(hostile2, entity) - getPriority(hostile1, entity);
        return i == 0 ? compareDistances(entity, hostile1, hostile2) : i;
    }

    private int compareDistances(LivingEntity entity, LivingEntity hostile1, LivingEntity hostile2) {
        return Double.compare(hostile1.distanceToSqr(entity), hostile2.distanceToSqr(entity));
    }

    public static boolean isGuardEnemy(LivingEntity entity, LivingEntity guard) {
        return getPriority(entity, guard) >= 0;
    }

    private static int getPriority(LivingEntity entity, LivingEntity guard) {
        if (entity instanceof VillagerEntityMCA villager) {
            return villager.isHostile() ? 10 : -1;
        }
        if (guard != null && entity instanceof Mob mob && mob.getTarget() == guard) {
            return 9;
        }

        Optional<Integer> configuredPriority = getConfiguredPriority(entity.getType());
        if (configuredPriority.isPresent()) {
            return configuredPriority.get();
        }

        Optional<Player> followedPlayer = getFollowedPlayer(guard);
        if (followedPlayer.isPresent()) {
            Player player = followedPlayer.get();
            if (entity instanceof Mob mob && mob.getTarget() == player) {
                return 9;
            }
            if (isFollowingDefenseEnemy(entity, player)) {
                return 3;
            }
        }

        if (Config.getInstance().guardsTargetMonsters && entity instanceof Enemy) {
            return 3;
        }
        return -1;
    }

    private static Optional<Integer> getConfiguredPriority(EntityType<?> type) {
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
        if (Config.getInstance().guardsTargetEntities.containsKey(id.toString())) {
            return Optional.of(Config.getInstance().guardsTargetEntities.get(id.toString()));
        }
        return getTagPriority(type);
    }

    private static Optional<Player> getFollowedPlayer(LivingEntity guard) {
        if (guard instanceof VillagerEntityMCA villager) {
            return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
        }
        return Optional.empty();
    }

    private static boolean isFollowingDefenseEnemy(LivingEntity entity, Player player) {
        return entity instanceof Enemy
               && entity.distanceToSqr(player) <= GUARD_ENEMY_RANGE_SQR;
    }

    private static Optional<Integer> getTagPriority(EntityType<?> type) {
        for (Map.Entry<String, Integer> entry : Config.getInstance().guardsTargetEntities.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(key.substring(1));
                if (id != null && RegistryHelper.isObjectInTag(BuiltInRegistries.ENTITY_TYPE, id, type)) {
                    return Optional.of(entry.getValue());
                }
            }
        }
        return Optional.empty();
    }
}
