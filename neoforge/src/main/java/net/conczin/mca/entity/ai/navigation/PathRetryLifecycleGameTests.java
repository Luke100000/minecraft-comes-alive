package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.gametest.GameTestBrain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.conczin.mca.MCA;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;
import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath;

/**
 * Runs the real destination producer and movement sink in Brain ticks.
 * Existing navigation tests invoke createPath directly, bypassing this handoff.
 */
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class PathRetryLifecycleGameTests {
    // Each fixture extends beyond the tiny GameTest template. The two lanes
    // must run in separate batches so their stone walls cannot overlap.
    private static final String CORE_BATCH = "mca_path_retry_core_lifecycle";
    private static final String PLAY_BATCH = "mca_path_retry_play_lifecycle";
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static final Set<VillagerEntityMCA> ACTIVE_VILLAGERS = new HashSet<>();
    private static final Map<BlockPos, BlockState> ORIGINAL_TERRAIN = new LinkedHashMap<>();
    private static Integer originalPathfindingDistance;

    private PathRetryLifecycleGameTests() {
    }

    @GameTest(batch = CORE_BATCH, templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 700)
    public static void coreSinkRetriesAndRecoversThroughRealBrain(GameTestHelper helper) {
        runLifecycle(helper, false);
    }

    @GameTest(batch = PLAY_BATCH, templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 700)
    public static void playSinkRetriesAndRecoversThroughRealBrain(GameTestHelper helper) {
        runLifecycle(helper, true);
    }

    @GameTest(batch = "mca_path_retry_partial_start_core", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void corePartialPathDoesNotEraseFailureBeforeMovement(GameTestHelper helper) {
        assertExistingPartialFailureRetainsAge(helper, false);
    }

    @GameTest(batch = "mca_path_retry_partial_start_play", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void playPartialPathDoesNotEraseFailureBeforeMovement(GameTestHelper helper) {
        assertExistingPartialFailureRetainsAge(helper, true);
    }

    private static void assertExistingPartialFailureRetainsAge(GameTestHelper helper, boolean vanillaSink) {
        BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos destination = start.east(80);
        prepareFlatPath(helper, start, destination);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .spawn(EntitySpawnReason.STRUCTURE);
        int originalPathfindingDistance = Config.getInstance().villagerPathfindingDistance;
        try {
            Config.getInstance().villagerPathfindingDistance = 48;
            villager.refreshBrain(helper.getLevel());
            villager.getBrain().removeAllBehaviors();
            villager.setNoAi(true);
            villager.setOnGround(true);
            villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new LongDistancePathTarget(destination), 0.5F, 0));

            MoveToTargetSink sink = vanillaSink ? new MoveToTargetSink() : new WanderOrTeleportToTargetTask();
            long started = helper.getLevel().getGameTime();
            long originalFailure = started - 25L;
            WalkTargetFailureMemory.record(villager, destination, originalFailure);
            helper.assertTrue(sink.tryStart(helper.getLevel(), villager, started),
                    "partial-route fixture did not start the movement sink");
            Path path = villager.getNavigation().getPath();
            helper.assertTrue(path != null && !path.canReach()
                            && MCAGroundPathNavigation.isUsefulPartialPath(path, destination),
                    "partial-route fixture did not start useful bounded navigation");
            helper.assertTrue(villager.blockPosition().equals(start),
                    "villager moved before the failure-memory assertion");
            helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, destination)
                            && villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                            .filter(since -> since == originalFailure).isPresent(),
                    (vanillaSink ? "PLAY" : "CORE")
                            + " sink reset an existing failure episode without physical progress");
        } finally {
            Config.getInstance().villagerPathfindingDistance = originalPathfindingDistance;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_path_retry_foreign_movement", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void combatMovementDoesNotClearHomeFailure(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos destination = start.east(80);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .spawn(EntitySpawnReason.STRUCTURE);
        try {
            villager.refreshBrain(helper.getLevel());
            villager.getBrain().removeAllBehaviors();
            villager.setNoAi(true);
            villager.getBrain().setMemory(MemoryModuleType.HOME,
                    GlobalPos.of(helper.getLevel().dimension(), destination));
            OneShot<VillagerEntityMCA> producer = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                    MemoryModuleType.HOME, 0.5F, 0, 150, ignored -> true, ignored -> { });
            long started = helper.getLevel().getGameTime();
            producer.trigger(helper.getLevel(), villager, started);
            helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                            .map(target -> target.getTarget() instanceof LongDistancePathTarget).orElse(false),
                    "fixture did not publish HOME long-distance transit");
            WalkTargetFailureMemory.record(villager, destination, started);

            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(start.south(8), 0.5F, 0));
            villager.setPos(Vec3.atBottomCenterOf(start.east(5)));
            producer.trigger(helper.getLevel(), villager, started + 1);

            helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                            .filter(since -> since == started).isPresent(),
                    "movement belonging to combat cleared the unrelated HOME failure episode");
            helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, destination),
                    "foreign WALK_TARGET changed the HOME failure identity");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_path_retry_failure_age", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 160)
    public static void suppressedRetriesKeepOriginalFailureAgeWhenVanillaClearsMemory(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 10));
        BlockPos destination = start.east(12);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), destination));

        int[] gaveUp = {0};
        OneShot<VillagerEntityMCA> producer = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                MemoryModuleType.HOME, 0.5F, 0, 80, ignored -> true, ignored -> gaveUp[0]++);
        long started = helper.getLevel().getGameTime();
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - started;
            if (elapsed == 60L) {
                // Vanilla clears this memory after a nominally reachable path. If the
                // villager has not physically moved, that must not restart the expiry.
                villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            }

            producer.trigger(helper.getLevel(), villager, helper.getLevel().getGameTime());
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            if (elapsed >= 60L && elapsed < 80L) {
                helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                                .filter(since -> since <= started + 1L).isPresent(),
                        "a suppressed unchanged target restarted the original failure age");
            }
            if (gaveUp[0] > 0) {
                helper.assertTrue(elapsed <= 105L,
                        "original failure expired late after a vanilla memory clear: " + elapsed);
                helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                        "giving up kept the unavailable HOME memory");
                villager.discard();
                helper.succeed();
            }
            if (elapsed > 125L) {
                villager.discard();
                helper.fail("unchanged destination never reached its original give-up deadline");
            }
        });
    }

    @GameTest(batch = "mca_path_retry_progress", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 75)
    public static void partialPathProgressPreventsPrematurePoiGiveUp(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 10));
        BlockPos destination = start.east(12);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), destination));
        int[] gaveUp = {0};
        OneShot<VillagerEntityMCA> producer = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                MemoryModuleType.HOME, 0.5F, 0, 30, ignored -> true, ignored -> gaveUp[0]++);
        long started = helper.getLevel().getGameTime();
        producer.trigger(helper.getLevel(), villager, started);
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                "destination producer never started the first route");
        WalkTargetFailureMemory.record(villager, destination, started);

        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - started;
            if (elapsed == 20L) {
                villager.setPos(Vec3.atBottomCenterOf(start.east(5)));
                producer.trigger(helper.getLevel(), villager, helper.getLevel().getGameTime());
                helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                                .isEmpty(), "moving five blocks on the same partial route retained stale failure age");
            }
            if (elapsed == 40L) {
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                producer.trigger(helper.getLevel(), villager, helper.getLevel().getGameTime());
                helper.assertTrue(gaveUp[0] == 0
                                && villager.getBrain().getMemoryInternal(MemoryModuleType.HOME).isPresent()
                                && villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                        "villager abandoned a still-progressing route when the old failure clock expired");
                villager.discard();
                helper.succeed();
            }
        });
    }

    private static void runLifecycle(GameTestHelper helper, boolean play) {
        String lane = play ? "PLAY vanilla sink" : "CORE MCA sink";
        helper.assertTrue(PathRequestDiagnostics.enabled(), "path diagnostics must be enabled in GameTests");
        // The test server may override the default 160-block distance in
        // config/mca.json. Give this known 47-block-wall detour its tested budget.
        originalPathfindingDistance = Config.getInstance().villagerPathfindingDistance;
        Config.getInstance().villagerPathfindingDistance = 160;

        // The fixture exceeds the GameTest template, so keep it well clear of
        // the adjacent templates and restore every changed block after the batch.
        BlockPos start = helper.absolutePos(new BlockPos(300, 1, 300));
        BlockPos destination = start.east(2);
        rememberForcedChunks(start, 30);
        rememberOriginalTerrain(helper.getLevel(), start, 30);
        prepareFlatArea(helper, start, 30, 3);
        // An ordinary 48-block search cannot go around this wall; an extended
        // search can, once the destination's separate stone enclosure is opened.
        for (int z = -23; z <= 23; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(destination.offset(dx, y, dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Path retry lifecycle " + lane)
                .spawn(EntitySpawnReason.STRUCTURE);
        ACTIVE_VILLAGERS.add(villager);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        @SuppressWarnings("unchecked")
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>) (Brain<?>) villager.getBrain();
        brain.stopAll(helper.getLevel(), villager);
        brain.removeAllBehaviors();
        WanderOrTeleportToTargetTask coreSink = new WanderOrTeleportToTargetTask();
        MoveToTargetSink playSink = new MoveToTargetSink(80, 120);
        GameTestBrain.addActivity(brain, Activity.CORE, 1, ImmutableList.of(coreSink));
        GameTestBrain.addActivity(brain, Activity.CORE, 2, ImmutableList.of(
                ExtendedWalkTowardsTask.createWithoutPoiRelease(
                        MemoryModuleType.HOME, 1.0F, 0, 600, ignored -> false, ignored -> { })));
        if (play) {
            GameTestBrain.addActivity(brain, Activity.PLAY, 0, ImmutableList.of(playSink));
        } else {
            GameTestBrain.addActivity(brain, Activity.IDLE, 20, ImmutableList.of(new DoNothing(100, 100)));
        }
        Activity activity = play ? Activity.PLAY : Activity.IDLE;
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(activity);
        brain.setSchedule(GameTestBrain.fixedSchedule(activity));
        brain.setActiveActivityIfPossible(activity);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), destination));

        long startedAt = helper.getLevel().getGameTime();
        boolean[] expectedSinkRan = {false};
        boolean[] routeOpened = {false};
        boolean[] sawFailure = {false};
        PathRequestDiagnostics.SearchSnapshot[] blockedSnapshot = {null};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - startedAt;
            expectedSinkRan[0] |= (play ? playSink : coreSink).getStatus() == Behavior.Status.RUNNING;
            sawFailure[0] |= WalkTargetFailureMemory.hasFailureFor(villager, destination);

            if (elapsed == 80L) {
                blockedSnapshot[0] = PathRequestDiagnostics.snapshot(villager);
                logPhase(lane, "blocked", villager, blockedSnapshot[0]);
                helper.assertTrue(expectedSinkRan[0], lane + " never started its real Brain movement sink");
                helper.assertTrue(sawFailure[0], lane + " never associated failure memory with HOME");
                helper.assertTrue(blockedSnapshot[0].sinkRequests() >= 1,
                        lane + " never made a sink-originated search: " + blockedSnapshot[0]);
                helper.assertTrue(blockedSnapshot[0].sinkRequests() <= 2,
                        lane + " republished an unchanged blocked HOME without making progress: " + blockedSnapshot[0]);
                helper.assertTrue(blockedSnapshot[0].extendedSearches() == 1,
                        lane + " repeated or omitted the initial expensive failed search: " + blockedSnapshot[0]);
                helper.assertTrue(blockedSnapshot[0].deferredProducerRetries() >= 1,
                        lane + " never deferred unchanged destination at its producer: " + blockedSnapshot[0]);

                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(destination.east().above(y),
                            Blocks.AIR.defaultBlockState(), 3);
                }
                VillagerEntityMCA fresh = VillagerFactory.newVillager(helper.getLevel())
                        .withAge(0)
                        .withPosition(Vec3.atBottomCenterOf(start))
                        .spawn(EntitySpawnReason.STRUCTURE);
                try {
                    fresh.refreshBrain(helper.getLevel());
                    fresh.setNoAi(true);
                    fresh.setOnGround(true);
                    fresh.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
                    fresh.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                            new WalkTarget(destination, 1.0F, 0));
                    Path freshPath = fresh.getNavigation().createPath(destination, 0);
                    MCA.LOGGER.info("[MCA Path Lifecycle] lane={} fresh opened-route path={} target={} end={} reachable={}",
                            lane, freshPath, freshPath == null ? null : freshPath.getTarget(),
                            freshPath == null || freshPath.getEndNode() == null ? null
                                    : freshPath.getEndNode().asBlockPos(),
                            freshPath != null && freshPath.canReach());
                    helper.assertTrue(freshPath != null && freshPath.canReach(),
                            lane + " opened-route fixture is not navigable by a fresh villager");
                } finally {
                    fresh.discard();
                }
                routeOpened[0] = true;
                logPhase(lane, "opened route", villager, PathRequestDiagnostics.snapshot(villager));
            }

            if (routeOpened[0] && villager.blockPosition().equals(destination)) {
                PathRequestDiagnostics.SearchSnapshot recovered = PathRequestDiagnostics.snapshot(villager);
                logPhase(lane, "arrived", villager, recovered);
                helper.assertTrue(recovered.extendedSearches() > blockedSnapshot[0].extendedSearches(),
                        lane + " reached HOME without retrying the extended search after the route reopened: " + recovered);
                helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).isEmpty(),
                        lane + " reached HOME but retained vanilla failure memory");
                ACTIVE_VILLAGERS.remove(villager);
                villager.discard();
                helper.succeed();
            }
            if (elapsed == 620L) {
                logPhase(lane, "timed out", villager, PathRequestDiagnostics.snapshot(villager));
                helper.fail(lane + " did not recover after the route opened; pos=" + villager.blockPosition()
                        + "; WALK_TARGET=" + brain.getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        + "; activeNonCore=" + brain.getActiveNonCoreActivity());
            }
        });
    }

    private static void logPhase(String lane, String phase, VillagerEntityMCA villager,
                                 PathRequestDiagnostics.SearchSnapshot snapshot) {
        MCA.LOGGER.info("[MCA Path Lifecycle] lane={} phase={} uuid={} pos={} walkTarget={} failureSince={} snapshot={}",
                lane, phase, villager.getStringUUID(), villager.blockPosition(),
                villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET),
                villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE), snapshot);
    }

    private static void rememberForcedChunks(BlockPos center, int radius) {
        ChunkPos min = ChunkPos.containing(center.offset(-radius, 0, -radius));
        ChunkPos max = ChunkPos.containing(center.offset(radius, 0, radius));
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                FORCED_CHUNKS.add(new ChunkPos(x, z));
            }
        }
    }

    private static void rememberOriginalTerrain(ServerLevel level, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                for (int y = -1; y <= 2; y++) {
                    BlockPos pos = center.offset(x, y, z);
                    ORIGINAL_TERRAIN.putIfAbsent(pos, level.getBlockState(pos));
                }
            }
        }
    }

    @AfterBatch(batch = CORE_BATCH)
    public static void releaseCoreLifecycleChunks(ServerLevel level) {
        releaseLifecycleChunks(level);
    }

    @AfterBatch(batch = PLAY_BATCH)
    public static void releasePlayLifecycleChunks(ServerLevel level) {
        releaseLifecycleChunks(level);
    }

    private static void releaseLifecycleChunks(ServerLevel level) {
        ACTIVE_VILLAGERS.forEach(VillagerEntityMCA::discard);
        ACTIVE_VILLAGERS.clear();
        ORIGINAL_TERRAIN.forEach((pos, state) -> level.setBlock(pos, state, 3));
        ORIGINAL_TERRAIN.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        FORCED_CHUNKS.clear();
        if (originalPathfindingDistance != null) {
            Config.getInstance().villagerPathfindingDistance = originalPathfindingDistance;
            originalPathfindingDistance = null;
        }
    }
}
