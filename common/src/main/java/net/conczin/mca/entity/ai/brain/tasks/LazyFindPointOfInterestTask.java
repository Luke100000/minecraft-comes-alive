package net.conczin.mca.entity.ai.brain.tasks;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.Trigger;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.pathfinder.Path;
import org.apache.commons.lang3.mutable.MutableLong;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A lazy version of {@link AcquirePoi} with longer cooldowns
 */
public class LazyFindPointOfInterestTask extends AcquirePoi {
    private static final int MIN_DELAY = 200;

    public static BehaviorControl<PathfinderMob> create(Predicate<Holder<PoiType>> poiPredicate, MemoryModuleType<GlobalPos> poiPosModule, MemoryModuleType<GlobalPos> potentialPoiPosModule, boolean onlyRunIfChild, Optional<Byte> entityStatus) {
        MutableLong cooldown = new MutableLong(0L);
        Long2ObjectMap<RetryMarker> long2ObjectMap = new Long2ObjectOpenHashMap<>();
        OneShot<PathfinderMob> singleTickTask = BehaviorBuilder.create(taskContext -> taskContext.map(queryResult -> (Trigger<PathfinderMob>) (world, entity, time) -> {
            if (onlyRunIfChild && entity.isBaby()) {
                return false;
            } else if (cooldown.longValue() == 0L) {
                cooldown.setValue(world.getGameTime() + (long) world.getRandom().nextInt(MIN_DELAY));
                return false;
            } else if (world.getGameTime() < cooldown.longValue()) {
                return false;
            } else {
                cooldown.setValue(time + MIN_DELAY + (long) world.getRandom().nextInt(MIN_DELAY));

                PoiManager pointOfInterestStorage = world.getPoiManager();
                long2ObjectMap.long2ObjectEntrySet().removeIf(entry -> !entry.getValue().isAttempting(time));

                Predicate<BlockPos> predicate2 = pos -> {
                    RetryMarker retryMarker = long2ObjectMap.get(pos.asLong());
                    if (retryMarker == null) {
                        return true;
                    } else if (!retryMarker.shouldRetry(time)) {
                        return false;
                    } else {
                        retryMarker.setAttemptTime(time);
                        return true;
                    }
                };

                Set<Pair<Holder<PoiType>, BlockPos>> set = pointOfInterestStorage.findAllClosestFirstWithType(poiPredicate, predicate2, entity.blockPosition(), 48, PoiManager.Occupancy.HAS_SPACE).limit(5L).collect(Collectors.toSet());
                Path path = findPathToPois(entity, set);
                if (path != null && path.canReach()) {
                    BlockPos blockPos = path.getTarget();
                    pointOfInterestStorage.getType(blockPos).ifPresent(poiType -> {
                        pointOfInterestStorage.take(poiPredicate, (registryEntry, blockPos2) -> blockPos2.equals(blockPos), blockPos, 1);
                        queryResult.set(GlobalPos.of(world.dimension(), blockPos));
                        entityStatus.ifPresent(status -> world.broadcastEntityEvent(entity, status));
                        long2ObjectMap.clear();
                    });
                } else {
                    for (Pair<Holder<PoiType>, BlockPos> registryEntryBlockPosPair : set) {
                        long2ObjectMap.computeIfAbsent(registryEntryBlockPosPair.getSecond().asLong(), m -> new RetryMarker(world.getRandom(), time));
                    }
                }

                return true;
            }
        }, taskContext.absent(potentialPoiPosModule)));

        return potentialPoiPosModule == poiPosModule ? singleTickTask : BehaviorBuilder.create(context -> context.map(poiPos -> (Trigger<PathfinderMob>) singleTickTask, context.absent(poiPosModule)));
    }

    private static class RetryMarker {
        private static final int MIN_DELAY = 40;
        private static final int ATTEMPT_DURATION = 400;
        private final RandomSource random;
        private long previousAttemptAt;
        private long nextScheduledAttemptAt;
        private int currentDelay;

        RetryMarker(RandomSource random, long time) {
            this.random = random;
            this.setAttemptTime(time);
        }

        public void setAttemptTime(long time) {
            this.previousAttemptAt = time;
            int i = this.currentDelay + this.random.nextInt(MIN_DELAY) + MIN_DELAY;
            this.currentDelay = Math.min(i, ATTEMPT_DURATION);
            this.nextScheduledAttemptAt = time + (long) this.currentDelay;
        }

        public boolean isAttempting(long time) {
            return time - this.previousAttemptAt < ATTEMPT_DURATION;
        }

        public boolean shouldRetry(long time) {
            return time >= this.nextScheduledAttemptAt;
        }

        public String toString() {
            return "RetryMarker{, previousAttemptAt=" + this.previousAttemptAt + ", nextScheduledAttemptAt=" + this.nextScheduledAttemptAt + ", currentDelay=" + this.currentDelay + "}";
        }
    }
}
