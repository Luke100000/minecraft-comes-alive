package net.conczin.mca.entity.ai.navigation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.MCA;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.brain.tasks.ConditionalTask;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.conczin.mca.entity.ai.brain.tasks.InteractTask;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
@PrefixGameTestTemplate(false)
public final class PathRetryLifecycleGameTests {
    // Each fixture extends beyond the tiny GameTest template. The two lanes
    // must run in separate batches so their stone walls cannot overlap.
    private static final String CORE_BATCH = "mca_path_retry_core_lifecycle";
    private static final String PLAY_BATCH = "mca_path_retry_play_lifecycle";
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static final Set<VillagerEntityMCA> ACTIVE_VILLAGERS = new HashSet<>();
    private static final Set<Entity> AUX_ENTITIES = new HashSet<>();
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
                .spawn(MobSpawnType.STRUCTURE);
        int originalPathfindingDistance = Config.getInstance().villagerPathfindingDistance;
        try {
            Config.getInstance().villagerPathfindingDistance = 48;
            villager.refreshBrain(helper.getLevel());
            villager.getBrain().removeAllBehaviors();
            villager.setNoAi(true);
            villager.setOnGround(true);
            villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(destination), 0.5F, 0));

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
                .spawn(MobSpawnType.STRUCTURE);
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
                            .map(target -> target.getTarget() instanceof PersistentPathTarget).orElse(false),
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
                .spawn(MobSpawnType.STRUCTURE);
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
                .spawn(MobSpawnType.STRUCTURE);
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
                .spawn(MobSpawnType.STRUCTURE);
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
        brain.addActivity(Activity.CORE, 1, ImmutableList.of(coreSink));
        brain.addActivity(Activity.CORE, 2, ImmutableList.of(
                ExtendedWalkTowardsTask.createWithoutPoiRelease(
                        MemoryModuleType.HOME, 1.0F, 0, 600, ignored -> false, ignored -> { })));
        if (play) {
            brain.addActivity(Activity.PLAY, 0, ImmutableList.of(playSink));
        } else {
            brain.addActivity(Activity.IDLE, 20, ImmutableList.of(new DoNothing(100, 100)));
        }
        Activity activity = play ? Activity.PLAY : Activity.IDLE;
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(activity);
        brain.setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, activity).build());
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
                        .spawn(MobSpawnType.STRUCTURE);
                try {
                    fresh.refreshBrain(helper.getLevel());
                    fresh.setNoAi(true);
                    fresh.setOnGround(true);
                    fresh.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
                    fresh.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                            new WalkTarget(new PersistentPathTarget(destination), 1.0F, 0));
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
        ChunkPos min = new ChunkPos(center.offset(-radius, 0, -radius));
        ChunkPos max = new ChunkPos(center.offset(radius, 0, radius));
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
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
        AUX_ENTITIES.forEach(Entity::discard);
        AUX_ENTITIES.clear();
        ORIGINAL_TERRAIN.forEach((pos, state) -> level.setBlock(pos, state, 3));
        ORIGINAL_TERRAIN.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
        FORCED_CHUNKS.clear();
        if (originalPathfindingDistance != null) {
            Config.getInstance().villagerPathfindingDistance = originalPathfindingDistance;
            originalPathfindingDistance = null;
        }
    }

    @AfterBatch(batch = "mca_path_retry_preemption_expiry")
    public static void releasePreemptionExpiry(ServerLevel level) {
        releaseLifecycleChunks(level);
    }

    @GameTest(batch = "mca_path_retry_preemption_expiry", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 260)
    public static void longForeignPreemptionDoesNotEvictHomeOnResumeAndStillExpiresLater(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 10));
        BlockPos destination = start.east(12);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .spawn(MobSpawnType.STRUCTURE);
        ACTIVE_VILLAGERS.add(villager);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), destination));

        int[] gaveUp = {0};
        OneShot<VillagerEntityMCA> producer = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                MemoryModuleType.HOME, 0.5F, 0, 80, ignored -> true, ignored -> gaveUp[0]++);
        long started = helper.getLevel().getGameTime();
        producer.trigger(helper.getLevel(), villager, started);
        WalkTargetFailureMemory.record(villager, destination, started);

        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            long elapsed = now - started;
            if (elapsed >= 10L && elapsed <= 120L) {
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(start.south(8), 0.5F, 0));
                producer.trigger(helper.getLevel(), villager, now);
                return;
            }
            if (elapsed == 121L) {
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                producer.trigger(helper.getLevel(), villager, now);
                helper.assertTrue(gaveUp[0] == 0
                                && villager.getBrain().getMemoryInternal(MemoryModuleType.HOME).isPresent(),
                        "foreign WALK_TARGET preemption aged HOME past maxRunTime and evicted it immediately on resume");
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                return;
            }
            if (elapsed > 121L) {
                producer.trigger(helper.getLevel(), villager, now);
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                if (gaveUp[0] > 0) {
                    helper.assertTrue(elapsed >= 190L && elapsed <= 220L,
                            "resumed HOME expired at unexpected tick after 10 pre-preemption unreachable ticks: " + elapsed);
                    ACTIVE_VILLAGERS.remove(villager);
                    villager.discard();
                    helper.succeed();
                } else if (elapsed > 230L) {
                    helper.fail("unreachable HOME never expired after resuming from foreign preemption");
                }
            }
        });
    }

    @GameTest(batch = "mca_path_retry_shared_oneshot", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void sharedOneShotProducerIsolatesRetryGatesAcrossVillagers(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(6, 1, 6));
        BlockPos destination = start.east(12);
        VillagerEntityMCA first = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(MobSpawnType.STRUCTURE);
        VillagerEntityMCA second = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(MobSpawnType.STRUCTURE);
        try {
            for (VillagerEntityMCA actor : new VillagerEntityMCA[]{first, second}) {
                actor.refreshBrain(helper.getLevel());
                actor.getBrain().removeAllBehaviors();
                actor.setNoAi(true);
                actor.getBrain().setMemory(MemoryModuleType.HOME,
                        GlobalPos.of(helper.getLevel().dimension(), destination));
            }
            OneShot<VillagerEntityMCA> sharedProducer = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                    MemoryModuleType.HOME, 0.5F, 0, 200, ignored -> true, ignored -> { });
            long started = helper.getLevel().getGameTime();
            sharedProducer.trigger(helper.getLevel(), first, started);
            first.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            sharedProducer.trigger(helper.getLevel(), first, started + 1L);
            first.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            sharedProducer.trigger(helper.getLevel(), first, started + 2L);
            helper.assertTrue(first.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                    "first villager did not enter retry backoff");

            sharedProducer.trigger(helper.getLevel(), second, started + 2L);
            helper.assertTrue(second.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                    "sharing a OneShot producer leaked the first villager's retry backoff onto a second villager");
        } finally {
            first.discard();
            second.discard();
        }
        helper.succeed();
    }

    @AfterBatch(batch = "mca_path_retry_real_brain_preemption")
    public static void releaseRealBrainPreemption(ServerLevel level) {
        releaseLifecycleChunks(level);
    }

    @GameTest(batch = "mca_path_retry_real_brain_preemption", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 260)
    public static void realBrainCombatAndInteractionPreemptionDoNotEvictHomeAndMultiFlashStillExpires(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(16, 1, 16));
        BlockPos destination = start.east(10);
        BlockPos jobPos = destination.south(3);
        rememberForcedChunks(start, 16);
        rememberOriginalTerrain(helper.getLevel(), start, 16);
        prepareFlatArea(helper, start, 16, 3);
        for (BlockPos sealed : new BlockPos[]{destination, jobPos}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        for (int y = 0; y < 3; y++) {
                            helper.getLevel().setBlock(sealed.offset(dx, y, dz), Blocks.STONE.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }

        // Actor 1: exercises MixinSetWalkTargetFromBlockMemory early return under foreign WALK_TARGET.
        VillagerEntityMCA worker = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start.west(2))).spawn(MobSpawnType.STRUCTURE);
        ACTIVE_VILLAGERS.add(worker);
        worker.refreshBrain(helper.getLevel());
        worker.getBrain().removeAllBehaviors();
        worker.setNoAi(true);
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(helper.getLevel().dimension(), jobPos));
        var jobProducer = SetWalkTargetFromBlockMemory.create(MemoryModuleType.JOB_SITE, 0.5F, 1, 100, 60);

        // Actor 2: live-AI villager right outside the sealed HOME wall, exercising Activity.REST suspension (ATTACK_TARGET) and ConditionalTask (!InteractTask.shouldRun).
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(destination.west(2))).spawn(MobSpawnType.STRUCTURE);
        ACTIVE_VILLAGERS.add(villager);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(true);
        int[] gaveUp = {0};
        @SuppressWarnings("unchecked")
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>) (Brain<?>) villager.getBrain();
        brain.stopAll(helper.getLevel(), villager);
        brain.removeAllBehaviors();
        brain.addActivity(Activity.CORE, 1, ImmutableList.of(new WanderOrTeleportToTargetTask()));
        brain.addActivityWithConditions(Activity.REST, ImmutableList.of(
                Pair.of(2, new ConditionalTask<>(ExtendedWalkTowardsTask.createWithoutPoiRelease(
                        MemoryModuleType.HOME, 0.5F, 0, 60, ignored -> true, ignored -> gaveUp[0]++),
                        v -> !InteractTask.shouldRun(v))),
                Pair.of(20, new DoNothing(20, 40))
        ), ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.REST).build());
        brain.setActiveActivityIfPossible(Activity.REST);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), destination));

        Zombie dummyThreat = new Zombie(EntityType.ZOMBIE, helper.getLevel());
        dummyThreat.setNoAi(true);
        dummyThreat.setSilent(true);
        dummyThreat.setPersistenceRequired();
        dummyThreat.setPos(start.getX() + 0.5D, start.getY() + 10.0D, start.getZ() + 0.5D);
        helper.getLevel().addFreshEntity(dummyThreat);
        AUX_ENTITIES.add(dummyThreat);

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(Vec3.atBottomCenterOf(destination.west(3)));
        AUX_ENTITIES.add(player);

        long started = helper.getLevel().getGameTime();
        jobProducer.trigger(helper.getLevel(), worker, started);
        WalkTargetFailureMemory.record(worker, jobPos, started);
        boolean[] sawInitialFailure = {false};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            long elapsed = now - started;
            sawInitialFailure[0] |= WalkTargetFailureMemory.hasFailureFor(villager, destination);

            if (elapsed >= 5L && elapsed <= 75L) {
                worker.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(start.south(4), 0.5F, 0));
                jobProducer.trigger(helper.getLevel(), worker, now);
            } else if (elapsed == 76L) {
                worker.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                jobProducer.trigger(helper.getLevel(), worker, now);
                helper.assertTrue(worker.getBrain().getMemoryInternal(MemoryModuleType.JOB_SITE).isPresent(),
                        "SetWalkTargetFromBlockMemory evicted JOB_SITE immediately after foreign WALK_TARGET preemption");
            }

            // Flash 1 (ticks 15..55): ATTACK_TARGET suspends Activity.REST entirely for 41 ticks.
            if (elapsed == 15L) {
                helper.assertTrue(sawInitialFailure[0], "live villager did not record initial sealed HOME failure");
                brain.setMemory(MemoryModuleType.ATTACK_TARGET, dummyThreat);
                brain.setActiveActivityIfPossible(Activity.IDLE);
            }
            if (elapsed == 56L) {
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                brain.setActiveActivityIfPossible(Activity.REST);
            }
            if (elapsed == 68L) {
                helper.assertTrue(gaveUp[0] == 0 && brain.getMemoryInternal(MemoryModuleType.HOME).isPresent(),
                        "combat preemption of Activity.REST aged HOME in wall-clock time and evicted it on resume");
            }

            // Flash 2 (ticks 75..120): live player interaction makes InteractTask.shouldRun(v) true, blocking ConditionalTask.
            if (elapsed == 75L) {
                villager.getInteractions().interactAt(player, Vec3.ZERO, InteractionHand.MAIN_HAND);
                helper.assertTrue(InteractTask.shouldRun(villager), "fixture did not enter InteractTask.shouldRun state");
            }
            if (elapsed == 121L) {
                villager.getInteractions().stopInteracting();
            }
            if (elapsed == 128L) {
                helper.assertTrue(gaveUp[0] == 0 && brain.getMemoryInternal(MemoryModuleType.HOME).isPresent(),
                        "player-interaction ConditionalTask preemption aged HOME in wall-clock time and evicted it");
            }
            if (elapsed > 128L && gaveUp[0] > 0) {
                helper.assertTrue(elapsed >= 135L && elapsed <= 195L,
                        "multi-flash owned unreachable ticks expired at unexpected tick: " + elapsed);
                releaseLifecycleChunks(helper.getLevel());
                helper.succeed();
            } else if (elapsed > 215L) {
                helper.fail("sealed HOME never expired after owned unreachable ticks exceeded maxRunTime across two flashes");
            }
        });
    }

    @AfterBatch(batch = "mca_path_retry_foreign_sink_preemption")
    public static void releaseForeignSinkPreemption(ServerLevel level) {
        releaseLifecycleChunks(level);
    }

    @GameTest(batch = "mca_path_retry_foreign_sink_preemption", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 220)
    public static void foreignMoveToTargetSinkPathAttemptPreservesPriorHomeFailureAcrossPreemption(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(16, 1, 16));
        BlockPos destination = start.east(10);
        BlockPos jobPos = destination.south(3);
        rememberForcedChunks(start, 16);
        rememberOriginalTerrain(helper.getLevel(), start, 16);
        prepareFlatArea(helper, start, 16, 3);
        for (BlockPos sealed : new BlockPos[]{destination, jobPos}) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx != 0 || dz != 0) {
                        for (int y = 0; y < 3; y++) {
                            helper.getLevel().setBlock(sealed.offset(dx, y, dz), Blocks.STONE.defaultBlockState(), 3);
                        }
                    }
                }
            }
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(destination.west(2))).spawn(MobSpawnType.STRUCTURE);
        ACTIVE_VILLAGERS.add(villager);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(true);
        int[] gaveUp = {0};
        var jobProducer = SetWalkTargetFromBlockMemory.create(MemoryModuleType.JOB_SITE, 0.5F, 1, 100, 60);
        @SuppressWarnings("unchecked")
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>) (Brain<?>) villager.getBrain();
        brain.stopAll(helper.getLevel(), villager);
        brain.removeAllBehaviors();
        brain.addActivity(Activity.CORE, 1, ImmutableList.of(new WanderOrTeleportToTargetTask()));
        brain.addActivityWithConditions(Activity.REST, ImmutableList.of(
                Pair.of(2, new ConditionalTask<>(ExtendedWalkTowardsTask.createWithoutPoiRelease(
                        MemoryModuleType.HOME, 0.5F, 0, 60, ignored -> true, ignored -> gaveUp[0]++),
                        v -> !InteractTask.shouldRun(v))),
                Pair.of(20, new DoNothing(20, 40))
        ), ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.REST).build());
        brain.setActiveActivityIfPossible(Activity.REST);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), destination));
        brain.setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(helper.getLevel().dimension(), jobPos));

        Zombie dummyThreat = new Zombie(EntityType.ZOMBIE, helper.getLevel());
        dummyThreat.setNoAi(true);
        dummyThreat.setSilent(true);
        dummyThreat.setPersistenceRequired();
        dummyThreat.setPos(start.getX() + 0.5D, start.getY() + 10.0D, start.getZ() + 0.5D);
        helper.getLevel().addFreshEntity(dummyThreat);
        AUX_ENTITIES.add(dummyThreat);

        long started = helper.getLevel().getGameTime();
        // Prime JOB_SITE's per-producer gate on the same villager before REST takes over.
        jobProducer.trigger(helper.getLevel(), villager, started);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);

        boolean[] clearedByForeignSink = {false};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            long elapsed = now - started;

            if (elapsed == 25L) {
                helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, destination),
                        "villager did not record initial sealed HOME failure before foreign MoveToTargetSink preemption");
                brain.setMemory(MemoryModuleType.ATTACK_TARGET, dummyThreat);
                brain.setActiveActivityIfPossible(Activity.IDLE);
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination.west(3), 0.5F, 0));
            }
            if (elapsed > 25L && elapsed <= 95L) {
                clearedByForeignSink[0] |= !WalkTargetFailureMemory.hasFailureFor(villager, destination);
                if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                    brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination.west(2), 0.5F, 0));
                }
                // Concurrent second POI producer on the same villager during preemption.
                jobProducer.trigger(helper.getLevel(), villager, now);
            }
            if (elapsed == 96L) {
                helper.assertTrue(clearedByForeignSink[0],
                        "foreign MoveToTargetSink path attempt did not clear CANT_REACH_WALK_TARGET_SINCE as expected");
                brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                villager.getNavigation().stop();
                brain.setActiveActivityIfPossible(Activity.REST);
            }
            if (elapsed == 102L) {
                helper.assertTrue(gaveUp[0] == 0 && brain.getMemoryInternal(MemoryModuleType.HOME).isPresent(),
                        "foreign MoveToTargetSink path attempt wiped preemption state and evicted HOME immediately on resume");
            }
            if (elapsed > 102L && gaveUp[0] > 0) {
                helper.assertTrue(elapsed >= 125L && elapsed <= 154L,
                        "prior HOME failure age did not survive foreign MoveToTargetSink preemption cleanly (expired at " + elapsed + ")");
                releaseLifecycleChunks(helper.getLevel());
                helper.succeed();
            } else if (elapsed > 180L) {
                helper.fail("sealed HOME never expired after resuming from foreign MoveToTargetSink preemption");
            }
        });
    }
}
