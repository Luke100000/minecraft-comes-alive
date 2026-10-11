package net.conczin.mca.entity.ai.brain.tasks;

import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A lazy version of {@link AcquirePoi} with longer cooldowns
 */
public class LazyFindPointOfInterestTask extends AcquirePoi {
    private static final int MIN_DELAY = 200;

    public static BehaviorControl<PathfinderMob> create(Predicate<Holder<PoiType>> poiPredicate,
                                                        MemoryModuleType<GlobalPos> poiPosModule,
                                                        MemoryModuleType<GlobalPos> potentialPoiPosModule,
                                                        boolean onlyRunIfAdult, Optional<Byte> entityStatus) {
        return new OneShot<>() {
            private long nextAttemptTime;
            private final Long2ObjectMap<RetryMarker> retryMarkers = new Long2ObjectOpenHashMap<>();

            @Override
            public String debugString() {
                return LazyFindPointOfInterestTask.class.getSimpleName();
            }

            @Override
            public boolean trigger(ServerLevel world, PathfinderMob entity, long time) {
                if (!entity.getBrain().checkMemory(potentialPoiPosModule, MemoryStatus.VALUE_ABSENT)
                        || !entity.getBrain().checkMemory(poiPosModule, MemoryStatus.VALUE_ABSENT)
                        || (onlyRunIfAdult && entity.isBaby())) {
                    return false;
                }
                if (nextAttemptTime == 0L) {
                    nextAttemptTime = world.getGameTime() + world.random.nextInt(MIN_DELAY);
                    return false;
                }
                if (world.getGameTime() < nextAttemptTime) {
                    return false;
                }
                nextAttemptTime = time + MIN_DELAY + world.getRandom().nextInt(MIN_DELAY);

                PoiManager poiManager = world.getPoiManager();
                retryMarkers.long2ObjectEntrySet().removeIf(entry -> !entry.getValue().isAttempting(time));
                Predicate<BlockPos> eligible = pos -> {
                    RetryMarker retry = retryMarkers.get(pos.asLong());
                    if (retry == null) {
                        return true;
                    }
                    if (!retry.shouldRetry(time)) {
                        return false;
                    }
                    retry.setAttemptTime(time);
                    return true;
                };

                Set<Pair<Holder<PoiType>, BlockPos>> candidates = poiManager
                        .findAllClosestFirstWithType(poiPredicate, eligible, entity.blockPosition(), 48, PoiManager.Occupancy.HAS_SPACE)
                        .limit(5)
                        .collect(Collectors.toSet());
                Path path = findPathToPois(entity, candidates);
                if (path != null && path.canReach()) {
                    BlockPos target = path.getTarget();
                    poiManager.getType(target).ifPresent(type -> {
                        poiManager.take(poiPredicate, (entry, pos) -> pos.equals(target), target, 1);
                        entity.getBrain().setMemory(potentialPoiPosModule, GlobalPos.of(world.dimension(), target));
                        entityStatus.ifPresent(status -> world.broadcastEntityEvent(entity, status));
                        retryMarkers.clear();
                        DebugPackets.sendPoiTicketCountPacket(world, target);
                    });
                } else {
                    for (Pair<Holder<PoiType>, BlockPos> candidate : candidates) {
                        retryMarkers.computeIfAbsent(candidate.getSecond().asLong(), pos -> new RetryMarker(world.random, time));
                    }
                }
                return true;
            }
        };
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
