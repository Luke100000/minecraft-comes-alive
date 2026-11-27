package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.gametest.GameTestBrain;

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
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.ArrayList;
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
    private static final Set<Entity> AUX_ENTITIES = new HashSet<>();
    private static final Map<BlockPos, BlockState> ORIGINAL_TERRAIN = new LinkedHashMap<>();
    private static Integer originalPathfindingDistance;

    private PathRetryLifecycleGameTests() {
    }

    @GameTest(batch = "mca_path_retry_same_position_owners", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void twoProducerCompletionPreservesOtherFailure(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
        prepareFlatArea(helper, start, 16, 2);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        try {
            villager.setNoAi(true);
            var brain = villager.getBrain();
            BlockPos target = start.east(2);
            GlobalPos destination = GlobalPos.of(helper.getLevel().dimension(), target);
            brain.setMemory(MemoryModuleType.HOME, destination);
            brain.setMemory(MemoryModuleType.JOB_SITE, destination);
            int[] gaveUp = {0};
            var home = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                    MemoryModuleType.HOME, 0.5F, 0, 60, ignored -> true, ignored -> gaveUp[0]++);
            var job = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                    MemoryModuleType.JOB_SITE, 0.5F, 1, 100, ignored -> false, ignored -> { });
            long time = helper.getLevel().getGameTime();
            home.trigger(helper.getLevel(), villager, time);
            helper.assertTrue(brain.hasMemoryValue(MemoryModuleType.WALK_TARGET), "HOME did not publish");
            WalkTargetFailureMemory.record(villager, target, time);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            job.trigger(helper.getLevel(), villager, time + 25);
            WalkTarget jobWalk = brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
            WalkTargetFailureMemory.record(villager, target, time + 25);
            home.trigger(helper.getLevel(), villager, time + 26);
            helper.assertTrue(brain.getMemory(MemoryModuleType.WALK_TARGET).filter(walk -> walk == jobWalk).isPresent(),
                    "HOME overwrote same-position JOB_SITE movement");
            villager.setPos(Vec3.atBottomCenterOf(start.east()));
            job.trigger(helper.getLevel(), villager, time + 39);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.setPos(Vec3.atBottomCenterOf(start));
            home.trigger(helper.getLevel(), villager, time + 40);
            helper.assertTrue(brain.getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                            .filter(since -> since == time + 14).isPresent(),
                    "JOB_SITE completion lost or replaced HOME's paused failure age");
            home.trigger(helper.getLevel(), villager, time + 74);
            helper.assertTrue(gaveUp[0] == 0 && brain.hasMemoryValue(MemoryModuleType.HOME),
                    "HOME expired before its remaining deadline");
            home.trigger(helper.getLevel(), villager, time + 75);
            helper.assertTrue(gaveUp[0] == 1 && !brain.hasMemoryValue(MemoryModuleType.HOME),
                    "HOME did not expire at its remaining deadline");
            job.trigger(helper.getLevel(), villager, time + 76);
            helper.assertTrue(ExtendedWalkTowardsTask.ownsWalkTarget(villager, MemoryModuleType.JOB_SITE)
                            && !brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                    "completed JOB_SITE inherited its old failure on a fresh trip");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_path_retry_admitted_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void retractOwnedMovementStopsItsAdmittedDetour(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(300, 1, 300));
        BlockPos target = start.east(2);
        rememberOriginalTerrain(helper.getLevel(), start, 30);
        var ownedChunks = new ArrayList<ChunkPos>();
        prepareFlatArea(helper, start, 30, 3, ownedChunks);
        FORCED_CHUNKS.addAll(ownedChunks);
        for (int z = -23; z <= 23; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    for (int y = 0; y < 3; y++) {
                        helper.getLevel().setBlock(target.offset(dx, y, dz), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
        // Native flanking requires a walkable final approach; the long wall,
        // rather than a fully sealed destination, must exhaust the search.
        for (int y = 0; y < 3; y++) {
            helper.getLevel().setBlock(target.east().above(y), Blocks.AIR.defaultBlockState(), 3);
        }
        originalPathfindingDistance = Config.getInstance().villagerPathfindingDistance;
        Config.getInstance().villagerPathfindingDistance = 32;
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        ACTIVE_VILLAGERS.add(villager);
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        var brain = villager.getBrain();
        var destination = GlobalPos.of(helper.getLevel().dimension(), target);
        brain.setMemory(MemoryModuleType.HOME, destination);
        long time = helper.getLevel().getGameTime();
        var producer = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                MemoryModuleType.HOME, 0.5F, 0, 100, ignored -> false, ignored -> { });
        producer.trigger(helper.getLevel(), villager, time);
        var sink = new WanderOrTeleportToTargetTask();
        helper.assertTrue(sink.tryStart(helper.getLevel(), villager, time), "fixture did not admit HOME route");
        var navigation = (MCAGroundPathNavigation) villager.getNavigation();
        Path initial = navigation.getPath();
        helper.assertTrue(initial != null && !initial.canReach()
                        && brain.getMemory(MemoryModuleType.PATH).filter(path -> path == initial).isPresent(),
                "fixture did not admit a blocked HOME route");
        while (!initial.isDone()) initial.advance();
        navigation.tick();
        Path flank = navigation.getPath();
        helper.assertTrue(flank != null && flank != initial && !flank.isDone()
                        && navigation.isTakingDetourTo(target),
                "fixture did not replace the admitted route with a native detour: initial="
                        + initial + ", initial end=" + initial.getEndNode() + ", current=" + flank);
        sink.tickOrStop(helper.getLevel(), villager, time + 1);
        helper.assertTrue(brain.getMemory(MemoryModuleType.PATH).filter(path -> path == flank).isPresent(),
                "fixture did not synchronize the admitted detour into PATH");
        helper.assertTrue(ExtendedWalkTowardsTask.retractOwnedMovement(villager, MemoryModuleType.HOME),
                "typed HOME command was not retracted");
        helper.assertTrue(navigation.getPath() == null && !brain.hasMemoryValue(MemoryModuleType.PATH)
                        && !brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)
                        && !brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        && brain.getMemory(MemoryModuleType.HOME).filter(destination::equals).isPresent(),
                "retraction did not remove the admitted detour without erasing HOME");
        helper.succeed();
    }

    @AfterBatch(batch = "mca_path_retry_admitted_detour")
    public static void releaseAdmittedDetour(ServerLevel level) {
        releaseLifecycleChunks(level);
    }

    @GameTest(batch = "mca_path_retry_rejected_sink_handoff", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void rejectedSinkPathDoesNotBlockReplacementWalkTarget(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(300, 1, 300));
        BlockPos firstDestination = start.east(7);
        BlockPos replacementDestination = start.south(7);
        rememberOriginalTerrain(helper.getLevel(), start, 12);
        var ownedChunks = new ArrayList<ChunkPos>();
        try {
            prepareFlatArea(helper, start, 12, 3, ownedChunks);
        } finally {
            FORCED_CHUNKS.addAll(ownedChunks);
        }
        try {
            VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
            ACTIVE_VILLAGERS.add(villager);
            villager.refreshBrain(helper.getLevel());
            villager.getBrain().removeAllBehaviors();
            villager.setNoAi(true);
            villager.setOnGround(true);
            var brain = villager.getBrain();
            var navigation = (MCAGroundPathNavigation) villager.getNavigation();
            WalkTarget firstCommand = new WalkTarget(firstDestination, 0.5F, 0);
            brain.setMemory(MemoryModuleType.WALK_TARGET, firstCommand);

            Path completed = navigation.createPath(firstDestination, 0);
            helper.assertTrue(completed != null && completed.canReach()
                            && navigation.moveTo(completed, 0.5D),
                    "fixture did not start an ordinary reachable path");
            while (!completed.isDone()) completed.advance();
            helper.assertTrue(navigation.getPath() == completed && navigation.isDone()
                            && !brain.hasMemoryValue(MemoryModuleType.PATH),
                    "fixture did not retain a completed path with free Brain.PATH");

            MoveToTargetSink rejectedSink = new MoveToTargetSink();
            long started = helper.getLevel().getGameTime();
            helper.assertTrue(rejectedSink.tryStart(helper.getLevel(), villager, started),
                    "fixture could not start the sink for the original command");
            Path attempted = brain.getMemory(MemoryModuleType.PATH).orElse(null);
            helper.assertTrue(attempted != null && attempted != completed
                            && attempted.sameAs(completed)
                            && navigation.getPath() == completed && navigation.isDone(),
                    "fixture did not reproduce a rejected equivalent path while retaining the old path");

            WalkTarget replacement = new WalkTarget(replacementDestination, 0.5F, 0);
            brain.setMemory(MemoryModuleType.WALK_TARGET, replacement);
            rejectedSink.tickOrStop(helper.getLevel(), villager, started + 1);
            helper.assertTrue(brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == replacement,
                    "retiring the rejected sink erased the replacement WALK_TARGET");
            helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.PATH),
                    "retiring the rejected sink left its stale PATH and blocked the replacement");

            MoveToTargetSink replacementSink = new MoveToTargetSink();
            helper.assertTrue(replacementSink.tryStart(helper.getLevel(), villager, started + 2),
                    "replacement WALK_TARGET could not start its movement sink");
            replacementSink.doStop(helper.getLevel(), villager, started + 2);
        } finally {
            releaseLifecycleChunks(helper.getLevel());
        }
        helper.succeed();
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
        long[] arrivedAt = {-1L};
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

            if (routeOpened[0] && arrivedAt[0] == -1L && villager.blockPosition().equals(destination)) {
                arrivedAt[0] = elapsed;
            }
            // Physical movement can enter the target block after the Brain has ticked.
            // Observe the next Brain tick before checking the producer's completion cleanup.
            if (arrivedAt[0] != -1L && elapsed > arrivedAt[0]) {
                helper.assertTrue(villager.blockPosition().equals(destination),
                        lane + " left HOME before completing its reached movement");
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
        AUX_ENTITIES.forEach(Entity::discard);
        AUX_ENTITIES.clear();
        ORIGINAL_TERRAIN.forEach((pos, state) -> level.setBlock(pos, state, 3));
        ORIGINAL_TERRAIN.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
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
                .spawn(EntitySpawnReason.STRUCTURE);
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
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        VillagerEntityMCA second = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
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
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start.west(2))).spawn(EntitySpawnReason.STRUCTURE);
        ACTIVE_VILLAGERS.add(worker);
        worker.refreshBrain(helper.getLevel());
        worker.getBrain().removeAllBehaviors();
        worker.setNoAi(true);
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(helper.getLevel().dimension(), jobPos));
        var jobProducer = SetWalkTargetFromBlockMemory.create(MemoryModuleType.JOB_SITE, 0.5F, 1, 100, 60);

        // Actor 2: live-AI villager right outside the sealed HOME wall, exercising Activity.REST suspension (ATTACK_TARGET) and ConditionalTask (!InteractTask.shouldRun).
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(destination.west(2))).spawn(EntitySpawnReason.STRUCTURE);
        ACTIVE_VILLAGERS.add(villager);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(true);
        int[] gaveUp = {0};
        @SuppressWarnings("unchecked")
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>) (Brain<?>) villager.getBrain();
        brain.stopAll(helper.getLevel(), villager);
        brain.removeAllBehaviors();
        GameTestBrain.addActivity(brain, Activity.CORE, 1, ImmutableList.of(new WanderOrTeleportToTargetTask()));
        brain.addActivity(Activity.REST, ImmutableList.of(
                Pair.of(2, new ConditionalTask<>(ExtendedWalkTowardsTask.createWithoutPoiRelease(
                        MemoryModuleType.HOME, 0.5F, 0, 60, ignored -> true, ignored -> gaveUp[0]++),
                        v -> !InteractTask.shouldRun(v))),
                Pair.of(20, new DoNothing(20, 40))
        ), ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)), Set.of());
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setSchedule(GameTestBrain.fixedSchedule(Activity.REST));
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
                .withAge(0).withPosition(Vec3.atBottomCenterOf(destination.west(2))).spawn(EntitySpawnReason.STRUCTURE);
        ACTIVE_VILLAGERS.add(villager);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(true);
        int[] gaveUp = {0};
        var jobProducer = SetWalkTargetFromBlockMemory.create(MemoryModuleType.JOB_SITE, 0.5F, 1, 100, 60);
        @SuppressWarnings("unchecked")
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>) (Brain<?>) villager.getBrain();
        brain.stopAll(helper.getLevel(), villager);
        brain.removeAllBehaviors();
        GameTestBrain.addActivity(brain, Activity.CORE, 1, ImmutableList.of(new WanderOrTeleportToTargetTask()));
        brain.addActivity(Activity.REST, ImmutableList.of(
                Pair.of(2, new ConditionalTask<>(ExtendedWalkTowardsTask.createWithoutPoiRelease(
                        MemoryModuleType.HOME, 0.5F, 0, 60, ignored -> true, ignored -> gaveUp[0]++),
                        v -> !InteractTask.shouldRun(v))),
                Pair.of(20, new DoNothing(20, 40))
        ), ImmutableSet.of(Pair.of(MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT)), Set.of());
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setSchedule(GameTestBrain.fixedSchedule(Activity.REST));
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

    @GameTest(batch = "mca_path_retry_first_resume_clock", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void firstResumeAdoptsMatchingExistingFailure(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(300, 1, 300));
        VillagerEntityMCA villager = clockFixture(helper, start);
        try {
            BlockPos destination = start.east(8);
            GlobalPos home = GlobalPos.of(helper.getLevel().dimension(), destination);
            var brain = villager.getBrain();
            brain.setMemory(MemoryModuleType.HOME, home);
            long t = helper.getLevel().getGameTime();
            WalkTargetFailureMemory.record(villager, home, t);
            var producer = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                    MemoryModuleType.HOME, 0.5F, 0, 120, ignored -> true, ignored -> { });

            producer.trigger(helper.getLevel(), villager, t + 19);
            helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "existing failure must delay first HOME publication for 20 ticks");
            producer.trigger(helper.getLevel(), villager, t + 20);
            helper.assertTrue(ExtendedWalkTowardsTask.ownsWalkTarget(villager, MemoryModuleType.HOME),
                    "HOME did not publish at the initial 20-tick boundary");
            assertHomeClock(helper, villager, destination, t);

            replaceClockWithForeignSink(helper, villager, producer, start.north(3), t + 25);
            producer.trigger(helper.getLevel(), villager, t + 55);
            assertHomeClock(helper, villager, destination, t + 30);
            helper.assertTrue(brain.hasMemoryValue(MemoryModuleType.HOME),
                    "HOME was abandoned after its first interruption");
        } finally {
            releaseLifecycleChunks(helper.getLevel());
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_path_retry_mismatched_resume_clock", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void firstResumeRejectsMismatchedExistingFailure(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(300, 1, 300));
        VillagerEntityMCA villager = clockFixture(helper, start);
        try {
            BlockPos destination = start.east(8);
            GlobalPos home = GlobalPos.of(helper.getLevel().dimension(), destination);
            var brain = villager.getBrain();
            brain.setMemory(MemoryModuleType.HOME, home);
            long t = helper.getLevel().getGameTime();
            // Same coordinates, wrong dimension: positional equality is insufficient.
            var otherDimension = helper.getLevel().dimension() == net.minecraft.world.level.Level.NETHER
                    ? net.minecraft.world.level.Level.OVERWORLD : net.minecraft.world.level.Level.NETHER;
            WalkTargetFailureMemory.record(villager, GlobalPos.of(otherDimension, destination), t);
            var producer = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                    MemoryModuleType.HOME, 0.5F, 0, 120, ignored -> true, ignored -> { });

            producer.trigger(helper.getLevel(), villager, t + 1);
            helper.assertTrue(ExtendedWalkTowardsTask.ownsWalkTarget(villager, MemoryModuleType.HOME),
                    "foreign dimension's failure delayed initial HOME publication");
            helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                    "foreign dimension's timestamp was adopted by HOME");
        } finally {
            releaseLifecycleChunks(helper.getLevel());
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_path_retry_repeated_resume_clock", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void repeatedResumeDoesNotDoubleShiftFailure(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(300, 1, 300));
        VillagerEntityMCA villager = clockFixture(helper, start);
        try {
            BlockPos destination = start.east(8);
            GlobalPos home = GlobalPos.of(helper.getLevel().dimension(), destination);
            var brain = villager.getBrain();
            brain.setMemory(MemoryModuleType.HOME, home);
            int[] gaveUp = {0};
            var producer = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                    MemoryModuleType.HOME, 0.5F, 0, 60, ignored -> true, ignored -> gaveUp[0]++);
            long t = helper.getLevel().getGameTime();
            producer.trigger(helper.getLevel(), villager, t);
            helper.assertTrue(ExtendedWalkTowardsTask.ownsWalkTarget(villager, MemoryModuleType.HOME),
                    "fixture did not publish HOME before recording failure");
            WalkTargetFailureMemory.record(villager, destination, t);

            replaceClockWithForeignSink(helper, villager, producer, start.north(3), t + 15);
            producer.trigger(helper.getLevel(), villager, t + 65);
            assertHomeClock(helper, villager, destination, t + 50);
            producer.trigger(helper.getLevel(), villager, t + 70);
            assertHomeClock(helper, villager, destination, t + 50);

            replaceClockWithForeignSink(helper, villager, producer, start.south(3), t + 80);
            producer.trigger(helper.getLevel(), villager, t + 110);
            assertHomeClock(helper, villager, destination, t + 80);
            producer.trigger(helper.getLevel(), villager, t + 111);
            assertHomeClock(helper, villager, destination, t + 80);
            producer.trigger(helper.getLevel(), villager, t + 140);
            helper.assertTrue(gaveUp[0] == 0 && brain.hasMemoryValue(MemoryModuleType.HOME),
                    "HOME expired at or before its inclusive 60-tick failure limit");
            producer.trigger(helper.getLevel(), villager, t + 141);
            helper.assertTrue(gaveUp[0] == 1 && !brain.hasMemoryValue(MemoryModuleType.HOME),
                    "HOME did not expire after exactly 80 paused ticks");
        } finally {
            releaseLifecycleChunks(helper.getLevel());
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_path_retry_same_memory_handoff", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void secondMeetingProducerAdoptsEstablishedShiftedFailure(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(300, 1, 300));
        VillagerEntityMCA villager = clockFixture(helper, start);
        try {
            BlockPos destination = start.east(8);
            GlobalPos meeting = GlobalPos.of(helper.getLevel().dimension(), destination);
            var brain = villager.getBrain();
            brain.setMemory(MemoryModuleType.MEETING_POINT, meeting);
            // The two distinct OneShots used by MEET and PRE_RAID.
            var first = SetWalkTargetFromBlockMemory.create(MemoryModuleType.MEETING_POINT, 0.5F, 6, 100, 200);
            var second = SetWalkTargetFromBlockMemory.create(MemoryModuleType.MEETING_POINT, 0.75F, 2, 150, 200);
            long now = helper.getLevel().getGameTime();
            long t = now - 215;
            first.trigger(helper.getLevel(), villager, t);
            helper.assertTrue(ExtendedWalkTowardsTask.ownsWalkTarget(villager, MemoryModuleType.MEETING_POINT),
                    "first MEETING_POINT producer did not publish");
            WalkTargetFailureMemory.record(villager, destination, t);

            // Open the pause through the real producer, but never run a foreign sink:
            // the Brain projection should remain T even when think() closes A's pause.
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(start.north(3), 0.5F, 0));
            first.trigger(helper.getLevel(), villager, t + 135);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getVillagerBrain().think();
            helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, destination)
                            && brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                            .filter(since -> since == t).isPresent(),
                    "fixture did not retain the stale projection until the handoff");

            // B has never run. It must adopt A's established T+80, not stale T
            // (whose 215-tick age would expire the 200-tick MEETING_POINT episode).
            second.trigger(helper.getLevel(), villager, now);
            helper.assertTrue(brain.hasMemoryValue(MemoryModuleType.MEETING_POINT)
                            && ExtendedWalkTowardsTask.ownsWalkTarget(villager, MemoryModuleType.MEETING_POINT)
                            && brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                            .filter(since -> since == t + 80).isPresent(),
                    "second producer adopted stale failure projection instead of T+80: "
                            + brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE));

            WalkTargetFailureMemory.record(villager, destination, now + 1);
            helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                            .filter(since -> since == t + 80).isPresent(),
                    "subsequent failure report changed the established failure clock");
            second.trigger(helper.getLevel(), villager, t + 280);
            helper.assertTrue(brain.hasMemoryValue(MemoryModuleType.MEETING_POINT),
                    "MEETING_POINT expired at or before its inclusive 200-tick failure limit");
            second.trigger(helper.getLevel(), villager, t + 281);
            helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.MEETING_POINT),
                    "MEETING_POINT did not expire after exactly 80 paused ticks");
        } finally {
            releaseLifecycleChunks(helper.getLevel());
        }
        helper.succeed();
    }

    @AfterBatch(batch = "mca_path_retry_same_memory_handoff")
    public static void releaseSameMemoryHandoff(ServerLevel level) {
        releaseLifecycleChunks(level);
    }


    private static VillagerEntityMCA clockFixture(GameTestHelper helper, BlockPos start) {
        rememberOriginalTerrain(helper.getLevel(), start, 12);
        var ownedChunks = new java.util.ArrayList<ChunkPos>();
        try {
            prepareFlatArea(helper, start, 12, 3, ownedChunks);
        } finally {
            FORCED_CHUNKS.addAll(ownedChunks);
        }
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        ACTIVE_VILLAGERS.add(villager);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        return villager;
    }

    private static void replaceClockWithForeignSink(GameTestHelper helper, VillagerEntityMCA villager,
                                                    OneShot<VillagerEntityMCA> producer, BlockPos foreign, long time) {
        var brain = villager.getBrain();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(foreign, 0.5F, 0));
        producer.trigger(helper.getLevel(), villager, time);
        int before = PathRequestDiagnostics.snapshot(villager).sinkRequests();
        MoveToTargetSink sink = new MoveToTargetSink();
        helper.assertTrue(sink.tryStart(helper.getLevel(), villager, time),
                "reachable foreign sink fixture did not start");
        Path path = villager.getNavigation().getPath();
        helper.assertTrue(path != null && path.canReach()
                        && PathRequestDiagnostics.snapshot(villager).sinkRequests() > before,
                "foreign sink did not make a reachable path attempt");
        helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                "foreign sink did not replace the old failure projection");
        sink.doStop(helper.getLevel(), villager, time);
        helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)
                        && !brain.hasMemoryValue(MemoryModuleType.PATH),
                "foreign sink failed to release movement memories");
    }

    private static void assertHomeClock(GameTestHelper helper, VillagerEntityMCA villager,
                                        BlockPos home, long expected) {
        helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, home)
                        && villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        .filter(since -> since == expected).isPresent(),
                "HOME failure clock expected " + expected + ", observed "
                        + villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE));
    }

    @AfterBatch(batch = "mca_path_retry_first_resume_clock")
    public static void releaseFirstResumeClock(ServerLevel level) {
        releaseLifecycleChunks(level);
    }

    @AfterBatch(batch = "mca_path_retry_mismatched_resume_clock")
    public static void releaseMismatchedResumeClock(ServerLevel level) {
        releaseLifecycleChunks(level);
    }

    @AfterBatch(batch = "mca_path_retry_repeated_resume_clock")
    public static void releaseRepeatedResumeClock(ServerLevel level) {
        releaseLifecycleChunks(level);
    }

    @GameTest(batch = "mca_path_retry_invalidated_age", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void routeInvalidationDoesNotRenewUnreachableEpisode(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(300, 1, 300));
        BlockPos destination = start.east(2);
        VillagerEntityMCA villager = invalidationFixture(helper, start, destination);
        villager.setNoAi(true);
        var brain = villager.getBrain();
        int[] gaveUp = {0};
        var producer = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                MemoryModuleType.HOME, 0.5F, 0, 70, ignored -> true, ignored -> gaveUp[0]++);
        long t = helper.getLevel().getGameTime();
        producer.trigger(helper.getLevel(), villager, t);
        helper.assertTrue(ExtendedWalkTowardsTask.ownsWalkTarget(villager, MemoryModuleType.HOME),
                "fixture did not publish the original sealed HOME");
        attemptStoppedHomeSearch(helper, villager, t);
        assertInvalidationClock(helper, villager, destination, t);
        helper.assertTrue(PathRequestDiagnostics.snapshot(villager).extendedSearches() == 1,
                "fixture did not exhaust its initial extended search");

        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            long elapsed = now - t;
            if (elapsed == 20L) {
                producer.trigger(helper.getLevel(), villager, now);
                helper.assertTrue(ExtendedWalkTowardsTask.ownsWalkTarget(villager, MemoryModuleType.HOME),
                        "fixture did not consume the one early retry");
                attemptStoppedHomeSearch(helper, villager, now);
            }
            if (elapsed == 21L) {
                producer.trigger(helper.getLevel(), villager, now);
                helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                        "fixture did not establish ordinary publication suppression");
                assertInvalidationClock(helper, villager, destination, t);
            }
            if (elapsed == 25L) {
                // Change collision inside the failed search frontier, without opening any
                // of the eight enclosing columns around HOME. Navigation is stopped.
                helper.assertTrue(villager.getNavigation().getPath() == null,
                        "fixture must have no active navigation route before the block update");
                helper.getLevel().setBlockAndUpdate(start.north(2), Blocks.STONE.defaultBlockState());
                int before = PathRequestDiagnostics.snapshot(villager).extendedSearches();
                producer.trigger(helper.getLevel(), villager, now);
                helper.assertTrue(ExtendedWalkTowardsTask.ownsWalkTarget(villager, MemoryModuleType.HOME),
                        "collision change did not wake the suppressed HOME producer before its normal retry");
                attemptStoppedHomeSearch(helper, villager, now);
                helper.assertTrue(PathRequestDiagnostics.snapshot(villager).extendedSearches() > before,
                        "woken producer did not run a fresh extended search");
                helper.assertTrue(villager.blockPosition().equals(start),
                        "stationary fixture unexpectedly made progress");
                assertInvalidationClock(helper, villager, destination, t);
            }
            if (elapsed == 26L) {
                // The early escalation was spent at tick 20. A failed repair may
                // grant one wake-up, but must not grant another free publication.
                producer.trigger(helper.getLevel(), villager, now);
                helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                        "unsuccessful repair rearmed the spent early retry");
                assertInvalidationClock(helper, villager, destination, t);
            }
            if (elapsed == 70L || elapsed == 71L) {
                producer.trigger(helper.getLevel(), villager, now);
                if (elapsed == 70L) {
                    helper.assertTrue(gaveUp[0] == 0 && brain.hasMemoryValue(MemoryModuleType.HOME),
                            "HOME expired at its inclusive failure limit");
                    assertInvalidationClock(helper, villager, destination, t);
                } else {
                    helper.assertTrue(gaveUp[0] == 1 && !brain.hasMemoryValue(MemoryModuleType.HOME),
                            "unsuccessful repair renewed the original HOME failure deadline");
                    releaseLifecycleChunks(helper.getLevel());
                    helper.succeed();
                }
            }
        });
    }

    @GameTest(batch = "mca_path_retry_invalidated_repair", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 700)
    public static void routeInvalidationWithSuccessfulRepairRetainsHome(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(300, 1, 300));
        BlockPos destination = start.east(2);
        VillagerEntityMCA villager = invalidationFixture(helper, start, destination);
        int[] gaveUp = {0};

        @SuppressWarnings("unchecked")
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>) (Brain<?>) villager.getBrain();
        GameTestBrain.addActivity(brain, Activity.CORE, 1, ImmutableList.of(new WanderOrTeleportToTargetTask()));
        GameTestBrain.addActivity(brain, Activity.CORE, 2, ImmutableList.of(
                ExtendedWalkTowardsTask.createWithoutPoiRelease(
                        MemoryModuleType.HOME, 1.0F, 0, 600, ignored -> true, ignored -> gaveUp[0]++)));
        GameTestBrain.addActivity(brain, Activity.IDLE, 20, ImmutableList.of(new DoNothing(100, 100)));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
        brain.setActiveActivityIfPossible(Activity.IDLE);

        long began = helper.getLevel().getGameTime();
        int[] beforeRepair = {-1};
        long[] firstArrival = {-1L};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - began;
            if (elapsed == 80L) {
                helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, destination),
                        "repair fixture never recorded the sealed HOME failure");
                beforeRepair[0] = PathRequestDiagnostics.snapshot(villager).extendedSearches();
                helper.assertTrue(beforeRepair[0] == 1, "repair fixture lacked one original extended search");
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlockAndUpdate(destination.east().above(y), Blocks.AIR.defaultBlockState());
                }
            }
            if (beforeRepair[0] >= 0 && firstArrival[0] == -1L && villager.blockPosition().equals(destination)) {
                firstArrival[0] = elapsed;
            }
            if (firstArrival[0] != -1L && elapsed > firstArrival[0]) {
                helper.assertTrue(villager.blockPosition().equals(destination),
                        "villager left HOME before completing the repaired movement");
                helper.assertTrue(PathRequestDiagnostics.snapshot(villager).extendedSearches() > beforeRepair[0],
                        "repair did not trigger a fresh extended search");
                helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                        "arrival retained the unreachable timestamp");
                helper.assertTrue(gaveUp[0] == 0 && brain.getMemoryInternal(MemoryModuleType.HOME)
                                .filter(GlobalPos.of(helper.getLevel().dimension(), destination)::equals).isPresent(),
                        "successful repair abandoned the original HOME");
                releaseLifecycleChunks(helper.getLevel());
                helper.succeed();
            } else if (elapsed >= 620L) {
                helper.fail("villager did not physically arrive after the route repair: " + villager.blockPosition());
            }
        });
    }

    private static VillagerEntityMCA invalidationFixture(GameTestHelper helper, BlockPos start, BlockPos destination) {
        originalPathfindingDistance = Config.getInstance().villagerPathfindingDistance;
        Config.getInstance().villagerPathfindingDistance = 160;
        rememberOriginalTerrain(helper.getLevel(), start, 30);
        var ownedChunks = new java.util.ArrayList<ChunkPos>();
        try {
            prepareFlatArea(helper, start, 30, 3, ownedChunks);
        } finally {
            FORCED_CHUNKS.addAll(ownedChunks);
        }
        for (int z = -23; z <= 23; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(destination.offset(dx, y, dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        ACTIVE_VILLAGERS.add(villager);
        villager.refreshBrain(helper.getLevel());
        @SuppressWarnings("unchecked")
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>) (Brain<?>) villager.getBrain();
        brain.stopAll(helper.getLevel(), villager);
        brain.removeAllBehaviors();
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.PATH);
        WalkTargetFailureMemory.clear(villager);
        brain.setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), destination));
        return villager;
    }

    private static void attemptStoppedHomeSearch(GameTestHelper helper, VillagerEntityMCA villager, long time) {
        var brain = villager.getBrain();
        int before = PathRequestDiagnostics.snapshot(villager).sinkRequests();
        MoveToTargetSink sink = new WanderOrTeleportToTargetTask();
        if (sink.tryStart(helper.getLevel(), villager, time)) {
            Path path = villager.getNavigation().getPath();
            helper.assertTrue(path == null || !path.getTarget().equals(brain.getMemory(MemoryModuleType.HOME).orElseThrow().pos()) || !path.canReach(), "sealed HOME became reachable");
            sink.doStop(helper.getLevel(), villager, time);
        }
        helper.assertTrue(PathRequestDiagnostics.snapshot(villager).sinkRequests() > before,
                "HOME sink never attempted a search");
        helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)
                        && villager.getNavigation().getPath() == null,
                "fixture must release the failed movement before the next publication");
    }

    private static void assertInvalidationClock(GameTestHelper helper, VillagerEntityMCA villager,
                                                BlockPos destination, long expected) {
        helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, destination)
                        && villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        .filter(since -> since == expected).isPresent(),
                "unchanged unreachable HOME failure expected " + expected + ", observed "
                        + villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE));
    }

    @AfterBatch(batch = "mca_path_retry_invalidated_age")
    public static void releaseInvalidatedAge(ServerLevel level) {
        releaseLifecycleChunks(level);
    }

    @AfterBatch(batch = "mca_path_retry_invalidated_repair")
    public static void releaseInvalidatedRepair(ServerLevel level) {
        releaseLifecycleChunks(level);
    }
}
