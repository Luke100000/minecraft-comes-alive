package net.conczin.mca.entity.ai.navigation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.conczin.mca.entity.ai.brain.tasks.SmarterOpenDoorsTask;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;
import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath;

/**
 * End-to-end pathfinding contracts: real destination producer, real CORE movement
 * sink and natural entity ticks. No test creates a path, advances a node, moves
 * a mob or invokes a behavior's start method directly.
 */
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class AutonomousPathfindingGameTests {
    private static final String DETOUR_BATCH = "mca_autonomous_live_detour";
    private static final String GATE_BATCH = "mca_autonomous_closed_gate";
    private static final String SEGMENT_BATCH = "mca_autonomous_progressive_segments";
    private static final String BOUNDED_DETOUR_BATCH = "mca_autonomous_bounded_detour";
    private static final String LONG_CORRIDOR_BATCH = "mca_autonomous_long_corridor";
    private static final Set<VillagerEntityMCA> VILLAGERS = new HashSet<>();
    private static final Map<BlockPos, BlockState> ORIGINAL_BLOCKS = new LinkedHashMap<>();
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static Boolean originalGateConfig;
    private static Integer originalPathDistance;
    private static Boolean originalTeleportConfig;

    @GameTest(batch = LONG_CORRIDOR_BATCH, templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 1_100)
    public static void brainFinishesLongNearbyDetourInsteadOfTimingOutAndWalkingBack(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(2);
        BlockPos center = start.offset(1, 0, 28);
        rememberArea(helper.getLevel(), center, 32);
        prepareFlatArea(helper, center, 32, 3);
        for (int x = -2; x <= 4; x++) {
            for (int z = -1; z <= 58; z++) {
                boolean passage = z >= 0 && z <= 56 && x >= 0 && x <= 2
                        && (z == 56 || x == 0 || x == 2);
                if (!passage) {
                    for (int y = 0; y < 3; y++) {
                        helper.getLevel().setBlock(start.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
        originalPathDistance = Config.getInstance().villagerPathfindingDistance;
        originalTeleportConfig = Config.getInstance().allowVillagerTeleporting;
        Config.getInstance().villagerPathfindingDistance = 160;
        Config.getInstance().allowVillagerTeleporting = false;
        VillagerEntityMCA villager = spawnAutonomousVillager(helper, start, target, 0.5F, false);
        villager.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.5D);
        // Match the work-count fixture's settled start, without directly starting a path.
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        helper.runAfterDelay(10L, () -> villager.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), target)));
        long started = helper.getLevel().getGameTime();
        boolean[] passedTurn = {false};
        Vec3[] previous = {villager.position()};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - started;
            helper.assertTrue(villager.position().distanceTo(previous[0]) < 3.0D,
                    "long nearby detour teleported instead of walking");
            previous[0] = villager.position();
            passedTurn[0] |= villager.getZ() > start.getZ() + 55.5D;
            if (villager.blockPosition().equals(target)) {
                var searches = PathRequestDiagnostics.snapshot(villager);
                helper.assertTrue(passedTurn[0] && elapsed > 250L,
                        "fixture did not walk the long route beyond vanilla's movement timeout");
                helper.assertTrue(searches.sinkRequests() > 0 && searches.extendedSearches() == 1,
                        "reachable nearby detour was repeatedly replanned: " + searches);
                helper.succeed();
            } else if (elapsed >= 1_000L) {
                helper.fail("Brain abandoned its reachable long detour; position=" + villager.blockPosition()
                        + ", path=" + villager.getNavigation().getPath()
                        + ", searches=" + PathRequestDiagnostics.snapshot(villager));
            }
        });
    }

    private AutonomousPathfindingGameTests() {
    }

    @GameTest(batch = DETOUR_BATCH, templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 550)
    public static void brainReplansAroundNewWallAndWalksToHome(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(12);
        BlockPos wall = start.east(7);
        rememberArea(helper.getLevel(), start.east(6), 14);
        prepareFlatArea(helper, start.east(6), 14, 3);
        VillagerEntityMCA villager = spawnAutonomousVillager(helper, start, target, 0.8F, false);

        long started = helper.getLevel().getGameTime();
        boolean[] initialPath = {false};
        boolean[] wallPlaced = {false};
        boolean[] detoured = {false};
        int[] updatesBefore = {0};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - started;
            Path path = villager.getNavigation().getPath();
            if (path != null && path.getEndNode() != null && !path.isDone()) {
                initialPath[0] = true;
                helper.assertTrue(path.getTarget().equals(target),
                        "Brain navigation discarded HOME's actual destination");
            }
            if (!wallPlaced[0] && initialPath[0] && villager.getX() >= start.getX() + 1.5D) {
                helper.assertTrue(villager.getX() < wall.getX() - 2.0D,
                        "villager reached the obstruction before the dynamic update");
                updatesBefore[0] = PathRequestDiagnostics.recomputeSnapshot(villager).blockUpdates();
                // This completely blocks the original straight path. The
                // unblocked floor extends three cells around either end.
                for (int z = -2; z <= 2; z++) {
                    for (int y = 0; y < 3; y++) {
                        helper.getLevel().setBlock(wall.offset(0, y, z),
                                Blocks.STONE.defaultBlockState(), 3);
                    }
                }
                wallPlaced[0] = true;
            }
            if (wallPlaced[0] && Math.abs(villager.getZ() - start.getZ() - 0.5D) >= 1.7D) {
                detoured[0] = true;
            }
            if (wallPlaced[0] && villager.blockPosition().closerThan(target, 2.0D)) {
                var searches = PathRequestDiagnostics.snapshot(villager);
                var recomputes = PathRequestDiagnostics.recomputeSnapshot(villager);
                helper.assertTrue(detoured[0], "villager arrived without walking around the new wall");
                helper.assertTrue(recomputes.blockUpdates() > updatesBefore[0],
                        "world block updates never invalidated the old route: " + recomputes);
                helper.assertTrue(searches.sinkRequests() >= 1 && searches.ordinarySearches() >= 2,
                        "Brain did not autonomously plan and replan after obstruction: " + searches);
                helper.succeed();
            }
            if (elapsed >= 500L) {
                helper.fail("Brain did not recover from live obstacle; pos=" + villager.blockPosition()
                        + ", wallPlaced=" + wallPlaced[0] + ", detoured=" + detoured[0]
                        + ", path=" + path + ", searches=" + PathRequestDiagnostics.snapshot(villager));
            }
        });
    }

    @GameTest(batch = GATE_BATCH, templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 430)
    public static void brainOpensClosedFenceGatesAndWalksThrough(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(10);
        BlockPos gate = start.east(5);
        rememberArea(helper.getLevel(), start.east(5), 12);
        prepareFlatArea(helper, start.east(5), 12, 3);
        originalGateConfig = Config.getInstance().villagersInteractWithFenceGates;
        Config.getInstance().villagersInteractWithFenceGates = true;

        for (int z = -3; z <= 3; z++) {
            for (int y = 0; y < 3; y++) {
                if (z == 0 && y < 2) {
                    helper.getLevel().setBlock(gate.above(y), Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);
                } else {
                    helper.getLevel().setBlock(gate.offset(0, y, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }

        VillagerEntityMCA villager = spawnAutonomousVillager(helper, start, target, 0.7F, true);
        long started = helper.getLevel().getGameTime();
        boolean[] openedLower = {false};
        boolean[] openedUpper = {false};
        boolean[] crossedGate = {false};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - started;
            openedLower[0] |= helper.getLevel().getBlockState(gate).getValue(BlockStateProperties.OPEN);
            openedUpper[0] |= helper.getLevel().getBlockState(gate.above()).getValue(BlockStateProperties.OPEN);
            crossedGate[0] |= villager.getX() > gate.getX() + 0.8D
                    && Math.abs(villager.getZ() - gate.getZ() - 0.5D) < 1.0D;
            if (villager.blockPosition().closerThan(target, 2.0D)) {
                helper.assertTrue(openedLower[0] && openedUpper[0] && crossedGate[0],
                        "villager bypassed closed gate or failed to open its body clearance");
                var searches = PathRequestDiagnostics.snapshot(villager);
                helper.assertTrue(searches.sinkRequests() >= 1,
                        "villager reached gate destination without a Brain sink search: " + searches);
                helper.succeed();
            }
            if (elapsed >= 380L) {
                helper.fail("Brain stalled at closed gates; pos=" + villager.blockPosition()
                        + ", opened=" + openedLower[0] + "/" + openedUpper[0]
                        + ", crossed=" + crossedGate[0]
                        + ", path=" + villager.getNavigation().getPath());
            }
        });
    }

    @GameTest(batch = SEGMENT_BATCH, templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 1_600)
    public static void brainWalksMultiplePartialSegmentsWithoutManualPathStart(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(100);
        rememberPath(helper.getLevel(), start, target);
        prepareFlatPath(helper, start, target);
        originalPathDistance = Config.getInstance().villagerPathfindingDistance;
        Config.getInstance().villagerPathfindingDistance = 48;
        VillagerEntityMCA villager = spawnAutonomousVillager(helper, start, target, 1.0F, false);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        long started = helper.getLevel().getGameTime();
        boolean[] sawPartial = {false};
        boolean[] madePhysicalProgress = {false};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - started;
            Path path = villager.getNavigation().getPath();
            if (path != null && path.getEndNode() != null) {
                helper.assertTrue(path.getTarget().equals(target),
                        "partial segment changed the real HOME destination");
                sawPartial[0] |= !path.canReach();
            }
            madePhysicalProgress[0] |= villager.getX() > start.getX() + 30.0D;
            if (villager.blockPosition().closerThan(target, 2.0D)) {
                var searches = PathRequestDiagnostics.snapshot(villager);
                helper.assertTrue(sawPartial[0] && madePhysicalProgress[0],
                        "the test did not exercise real physical partial-segment continuation");
                helper.assertTrue(searches.sinkRequests() > 0 && searches.ordinarySearches() >= 2,
                        "Brain did not start the first path and chain later segments: " + searches);
                helper.succeed();
            }
            if (elapsed >= 1_500L) {
                helper.fail("Brain stopped between partial segments; pos=" + villager.blockPosition()
                        + ", path=" + path + ", searches=" + PathRequestDiagnostics.snapshot(villager));
            }
        });
    }

    @GameTest(batch = BOUNDED_DETOUR_BATCH, templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 1_100)
    public static void brainCompletesBoundedDetourAfterNearTargetPartial(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(40, 1, 40));
        BlockPos start = center.offset(-9, 0, 6);
        BlockPos target = center.offset(6, 0, -2);
        rememberArea(helper.getLevel(), center, 29);
        prepareFlatArea(helper, center, 29, 3);
        for (int z = -9; z <= 9; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(center.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        BlockPos trapped = center.offset(10, 0, 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(trapped.offset(dx, y, dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }

        originalPathDistance = Config.getInstance().villagerPathfindingDistance;
        Config.getInstance().villagerPathfindingDistance = 48;
        VillagerEntityMCA villager = spawnAutonomousVillager(helper, start, target, 0.9F, false);
        var followRange = villager.getAttribute(Attributes.FOLLOW_RANGE);
        followRange.removeModifier(ResourceLocation.withDefaultNamespace("random_spawn_bonus"));
        followRange.setBaseValue(48.0D);
        helper.assertTrue(Math.abs(villager.getAttributeValue(Attributes.FOLLOW_RANGE) - 48.0D) < 1.0E-6D,
                "bounded-detour villager must have deterministic follow range");

        long started = helper.getLevel().getGameTime();
        boolean[] sawPartial = {false};
        boolean[] passedWallEnd = {false};
        Vec3[] previous = {villager.position()};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - started;
            Vec3 position = villager.position();
            helper.assertTrue(position.distanceTo(previous[0]) < 3.0D,
                    "bounded detour teleported instead of walking");
            previous[0] = position;
            Path path = villager.getNavigation().getPath();
            if (path != null && path.getEndNode() != null) {
                helper.assertTrue(path.getTarget().equals(target),
                        "bounded detour replaced the original HOME destination");
                sawPartial[0] |= !path.canReach();
            }
            passedWallEnd[0] |= Math.abs(position.z - center.getZ() - 0.5D) > 9.5D;
            if (villager.blockPosition().equals(target)) {
                var searches = PathRequestDiagnostics.snapshot(villager);
                helper.assertTrue(sawPartial[0] && passedWallEnd[0],
                        "test did not exercise physical progress through a bounded partial detour");
                helper.assertTrue(searches.sinkRequests() >= 1 && searches.ordinarySearches() >= 2,
                        "Brain did not autonomously continue the partial detour: " + searches);
                helper.succeed();
            }
            if (elapsed >= 1_000L) {
                helper.fail("bounded detour never reached HOME; position=" + villager.blockPosition()
                        + ", partial=" + sawPartial[0] + ", passedWallEnd=" + passedWallEnd[0]
                        + ", path=" + path + ", searches=" + PathRequestDiagnostics.snapshot(villager));
            }
        });
    }

    private static VillagerEntityMCA spawnAutonomousVillager(GameTestHelper helper, BlockPos start,
                                                            BlockPos destination, float speed, boolean doors) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start))
                .withName("Autonomous pathfinding probe")
                .spawn(MobSpawnType.STRUCTURE);
        VILLAGERS.add(villager);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(24.0D);

        @SuppressWarnings("unchecked")
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>) (Brain<?>) villager.getBrain();
        brain.stopAll(helper.getLevel(), villager);
        brain.removeAllBehaviors();
        if (doors) {
            brain.addActivity(Activity.CORE, 0, ImmutableList.of(new SmarterOpenDoorsTask()));
        }
        brain.addActivity(Activity.CORE, 1, ImmutableList.of(new WanderOrTeleportToTargetTask()));
        brain.addActivity(Activity.CORE, 2, ImmutableList.of(
                ExtendedWalkTowardsTask.createWithoutPoiRelease(
                        MemoryModuleType.HOME, speed, 0, 1_400, ignored -> false, ignored -> { })));
        brain.addActivity(Activity.IDLE, 0, ImmutableList.of(new DoNothing(100, 100)));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.IDLE).build());
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), destination));
        return villager;
    }

    private static void rememberArea(ServerLevel level, BlockPos center, int radius) {
        BlockPos start = center.offset(-radius, 0, -radius);
        BlockPos end = center.offset(radius, 0, radius);
        rememberBlocks(level, start, end);
    }

    private static void rememberPath(ServerLevel level, BlockPos start, BlockPos destination) {
        rememberBlocks(level,
                new BlockPos(Math.min(start.getX(), destination.getX()) - 1, start.getY(), start.getZ() - 1),
                new BlockPos(Math.max(start.getX(), destination.getX()) + 1, start.getY(), start.getZ() + 1));
    }

    private static void rememberBlocks(ServerLevel level, BlockPos start, BlockPos end) {
        for (int x = start.getX(); x <= end.getX(); x++) {
            for (int z = start.getZ(); z <= end.getZ(); z++) {
                BlockPos foot = new BlockPos(x, start.getY(), z);
                for (int y = -1; y < 3; y++) {
                    BlockPos pos = foot.above(y);
                    ORIGINAL_BLOCKS.putIfAbsent(pos, level.getBlockState(pos));
                }
            }
        }
        ChunkPos min = new ChunkPos(start);
        ChunkPos max = new ChunkPos(end);
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
                FORCED_CHUNKS.add(new ChunkPos(x, z));
            }
        }
    }

    @AfterBatch(batch = DETOUR_BATCH)
    public static void cleanupDetour(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = GATE_BATCH)
    public static void cleanupGate(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = SEGMENT_BATCH)
    public static void cleanupSegments(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = BOUNDED_DETOUR_BATCH)
    public static void cleanupBoundedDetour(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = LONG_CORRIDOR_BATCH)
    public static void cleanupLongCorridor(ServerLevel level) {
        cleanup(level);
    }

    private static void cleanup(ServerLevel level) {
        VILLAGERS.forEach(VillagerEntityMCA::discard);
        VILLAGERS.clear();
        ORIGINAL_BLOCKS.forEach((pos, state) -> level.setBlock(pos, state, 3));
        ORIGINAL_BLOCKS.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
        FORCED_CHUNKS.clear();
        if (originalGateConfig != null) {
            Config.getInstance().villagersInteractWithFenceGates = originalGateConfig;
            originalGateConfig = null;
        }
        if (originalPathDistance != null) {
            Config.getInstance().villagerPathfindingDistance = originalPathDistance;
            originalPathDistance = null;
        }
        if (originalTeleportConfig != null) {
            Config.getInstance().allowVillagerTeleporting = originalTeleportConfig;
            originalTeleportConfig = null;
        }
    }
}
