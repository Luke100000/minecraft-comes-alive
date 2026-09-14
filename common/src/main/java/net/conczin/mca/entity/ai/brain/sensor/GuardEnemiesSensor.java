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
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Villager nearby-entity sensor with dedicated long-range guard enemy acquisition.
 * <p>
 * Non-combat villagers keep vanilla's 16-block query. Guards, followed-player defenders, and villagers already
 * in combat query once at guard range, then derive the normal 16-block nearby memories from that same result.
 */
public class GuardEnemiesSensor extends Sensor<LivingEntity> {
    private static final double VANILLA_NEARBY_RANGE = 16.0;
    private static final double GUARD_ENEMY_RANGE = 48.0;
    private static final double GUARD_ENEMY_RANGE_SQR = GUARD_ENEMY_RANGE * GUARD_ENEMY_RANGE;
    private static final TargetingConditions TARGET_CONDITIONS = TargetingConditions.forNonCombat()
            .range(GUARD_ENEMY_RANGE)
            .ignoreLineOfSight();
    private static final TargetingConditions TARGET_CONDITIONS_IGNORE_INVISIBILITY = TargetingConditions.forNonCombat()
            .range(GUARD_ENEMY_RANGE)
            .ignoreLineOfSight()
            .ignoreInvisibilityTesting();

    @Override
    public Set<MemoryModuleType<?>> requires() {
        return ImmutableSet.of(
                MemoryModuleType.NEAREST_LIVING_ENTITIES,
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                MemoryModuleType.ATTACK_TARGET,
                MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY,
                MemoryModuleTypeMCA.PLAYER_FOLLOWING
        );
    }

    @Override
    protected void doTick(ServerLevel world, LivingEntity entity) {
        if (!(entity instanceof VillagerEntityMCA villager)) {
            return;
        }

        boolean shouldScan = villager.isGuard()
                || villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING).isPresent()
                || villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isPresent();

        AABB vanillaBounds = villager.getBoundingBox().inflate(VANILLA_NEARBY_RANGE);
        AABB scanBounds = shouldScan ? getGuardScanBounds(villager) : vanillaBounds;
        List<LivingEntity> candidates = world.getEntitiesOfClass(
                LivingEntity.class,
                scanBounds,
                target -> target != villager && target.isAlive()
        );
        candidates.sort(Comparator.comparingDouble(villager::distanceToSqr));

        List<LivingEntity> nearbyEntities = new ArrayList<>();
        for (LivingEntity candidate : candidates) {
            if (vanillaBounds.intersects(candidate.getBoundingBox())) {
                nearbyEntities.add(candidate);
            }
        }
        villager.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, nearbyEntities);
        villager.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new NearestVisibleLivingEntities(world, villager, nearbyEntities)
        );

        if (!shouldScan) {
            villager.getBrain().eraseMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
            return;
        }

        villager.getBrain().setMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY, this.getNearestHostile(villager, candidates));
    }

    private Optional<LivingEntity> getNearestHostile(VillagerEntityMCA entity, List<LivingEntity> candidates) {
        LivingEntity followedPlayer = getFollowedPlayer(entity)
                .map(player -> (LivingEntity) player)
                .orElse(null);
        return candidates.stream()
                .filter(target -> isGuardEnemy(target, entity, followedPlayer))
                .filter(target -> isWithinGuardEnemyRange(entity, target))
                .filter(target -> isVisibleToGuard(entity, target))
                .min((a, b) -> this.compareEntities(entity, followedPlayer, a, b));
    }

    private AABB getGuardScanBounds(VillagerEntityMCA guard) {
        AABB bounds = guard.getBoundingBox().inflate(GUARD_ENEMY_RANGE);
        Optional<Player> followedPlayer = getFollowedPlayer(guard);
        if (followedPlayer.isPresent()) {
            bounds = bounds.minmax(followedPlayer.get().getBoundingBox().inflate(GUARD_ENEMY_RANGE));
        }
        return bounds;
    }

    private boolean isWithinGuardEnemyRange(VillagerEntityMCA guard, LivingEntity target) {
        if (target.distanceToSqr(guard) <= GUARD_ENEMY_RANGE_SQR) {
            return true;
        }
        return getFollowedPlayer(guard)
                .filter(player -> target.distanceToSqr(player) <= GUARD_ENEMY_RANGE_SQR)
                .isPresent();
    }

    private boolean isVisibleToGuard(VillagerEntityMCA guard, LivingEntity target) {
        LivingEntity rangeAnchor = target.distanceToSqr(guard) <= GUARD_ENEMY_RANGE_SQR
                ? guard
                : getFollowedPlayer(guard).map(player -> (LivingEntity) player).orElse(guard);
        TargetingConditions conditions = guard.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, target)
                ? TARGET_CONDITIONS_IGNORE_INVISIBILITY
                : TARGET_CONDITIONS;
        return conditions.test((ServerLevel) guard.level(), rangeAnchor, target) && guard.getSensing().hasLineOfSight(target);
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
        return Double.compare(hostile1.distanceToSqr(entity), hostile2.distanceToSqr(entity));
    }

    public static boolean isGuardEnemy(LivingEntity entity, LivingEntity guard) {
        LivingEntity followedPlayer = getFollowedPlayer(guard)
                .map(player -> (LivingEntity) player)
                .orElse(null);
        return entity.isAlive()
               && !entity.isRemoved()
               && (guard == null || entity.level() == guard.level())
               && getPriority(entity, guard, followedPlayer) >= 0;
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

            if (followedPlayer != null && entity instanceof Enemy
                    && entity.distanceToSqr(followedPlayer) <= GUARD_ENEMY_RANGE_SQR) {
                return 3;
            } else if (Config.getInstance().guardsTargetMonsters && entity instanceof Enemy) {
                return 3;
            } else {
                return -1;
            }
        }
    }

    private static Optional<Player> getFollowedPlayer(LivingEntity guard) {
        if (guard instanceof VillagerEntityMCA villager) {
            return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.PLAYER_FOLLOWING);
        }
        return Optional.empty();
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
