package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;

import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.minecraft.world.entity.ai.behavior.AcquirePoi.findPathToPois;

public class ExtendedFindPointOfInterestTask extends Behavior<VillagerEntityMCA> {
    public static final int POI_SORTING_RADIUS = 48;
    private static final int MAX_POSITIONS_PER_RUN = 5;
    private static final int POSITION_EXPIRE_INTERVAL = 200;
    private final Consumer<VillagerEntityMCA> onFinish;
    private final BiPredicate<VillagerEntityMCA, BlockPos> predicate;

    private final Predicate<Holder<PoiType>> poiType;
    private final MemoryModuleType<GlobalPos> targetMemoryModuleType;
    private final boolean onlyRunIfAdult;
    private final Optional<Byte> entityStatus;
    private final Long2ObjectMap<RetryMarker> foundPositionsToExpiry = new Long2ObjectOpenHashMap<>();
    private long positionExpireTimeLimit;

    public ExtendedFindPointOfInterestTask(Predicate<Holder<PoiType>> poiType, MemoryModuleType<GlobalPos> moduleType, boolean onlyRunIfAdult, Optional<Byte> entityStatus, Consumer<VillagerEntityMCA> onFinish) {
        this(poiType, moduleType, onlyRunIfAdult, entityStatus, onFinish, (e, p) -> true);
    }

    public ExtendedFindPointOfInterestTask(Predicate<Holder<PoiType>> poiType, MemoryModuleType<GlobalPos> moduleType, boolean onlyRunIfAdult, Optional<Byte> entityStatus, Consumer<VillagerEntityMCA> onFinish, BiPredicate<VillagerEntityMCA, BlockPos> predicate) {
        super(create(moduleType));

        this.onFinish = onFinish;
        this.predicate = predicate;
        this.poiType = poiType;
        this.targetMemoryModuleType = moduleType;
        this.onlyRunIfAdult = onlyRunIfAdult;
        this.entityStatus = entityStatus;
    }

    private static ImmutableMap<MemoryModuleType<?>, MemoryStatus> create(MemoryModuleType<GlobalPos> firstModule) {
        ImmutableMap.Builder<MemoryModuleType<?>, MemoryStatus> builder = ImmutableMap.builder();
        builder.put(firstModule, MemoryStatus.VALUE_ABSENT);
        return builder.build();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel serverWorld, VillagerEntityMCA pathAwareEntity) {
        if (this.onlyRunIfAdult && pathAwareEntity.isBaby()) {
            return false;
        }
        if (this.positionExpireTimeLimit == 0L) {
            this.positionExpireTimeLimit = pathAwareEntity.level().getGameTime() + (long) serverWorld.getRandom().nextInt(POSITION_EXPIRE_INTERVAL);
            return false;
        }
        return serverWorld.getGameTime() >= this.positionExpireTimeLimit;
    }


    @Override
    protected void start(ServerLevel serverWorld, VillagerEntityMCA villager, long l) {
        this.positionExpireTimeLimit = l + POSITION_EXPIRE_INTERVAL + (long) serverWorld.getRandom().nextInt(POSITION_EXPIRE_INTERVAL);
        PoiManager pointOfInterestStorage = serverWorld.getPoiManager();
        this.foundPositionsToExpiry.long2ObjectEntrySet().removeIf(entry -> !entry.getValue().isAttempting(l));
        Predicate<BlockPos> predicate = blockPos -> {
            RetryMarker retryMarker = this.foundPositionsToExpiry.get(blockPos.asLong());
            if (retryMarker != null) {
                if (!retryMarker.shouldRetry(l)) {
                    return false;
                }
                retryMarker.setAttemptTime(l);
            }
            if (isBedOccupiedByOthers(serverWorld, blockPos, villager)) {
                return false;
            }
            return this.predicate.test(villager, blockPos);
        };
        Set<Pair<Holder<PoiType>, BlockPos>> set = pointOfInterestStorage.findAllClosestFirstWithType(this.poiType, predicate, villager.blockPosition(), POI_SORTING_RADIUS, PoiManager.Occupancy.HAS_SPACE).limit(MAX_POSITIONS_PER_RUN).collect(Collectors.toSet());
        Path path = findPathToPois(villager, set);
        if (path != null && path.canReach()) {
            BlockPos blockPos2 = path.getTarget();
            pointOfInterestStorage.getType(blockPos2).ifPresent(pointOfInterestType -> {
                pointOfInterestStorage.take(this.poiType, (typeRegistryEntry, otherPos) -> {
                    return otherPos.equals(blockPos2);
                }, blockPos2, 1);

                villager.getBrain().setMemory(this.targetMemoryModuleType, GlobalPos.of(serverWorld.dimension(), blockPos2));
                this.entityStatus.ifPresent(statusByte -> serverWorld.broadcastEntityEvent(villager, statusByte));
                this.foundPositionsToExpiry.clear();
                // on finish callback
                onFinish.accept(villager);
            });
        } else {
            for (Pair<Holder<PoiType>, BlockPos> blockPos2 : set) {
                this.foundPositionsToExpiry.computeIfAbsent(blockPos2.getSecond().asLong(), m -> new RetryMarker(villager.level().getRandom(), l));
            }
        }
    }

    //todo this check is not necessary in vanilla, but since the 1.19.2 port of 7.4.0 it is requires as occupied beds are used
    private boolean isBedOccupiedByOthers(ServerLevel world, BlockPos pos, LivingEntity entity) {
        BlockState blockState = world.getBlockState(pos);
        return blockState.is(BlockTags.BEDS) && blockState.getValue(BedBlock.OCCUPIED) && !entity.isSleeping();
    }

    static class RetryMarker {
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
    }
}
