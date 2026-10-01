package net.conczin.mca.benchmark;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;
import java.util.function.LongConsumer;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;
import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath;

/**
 * Opt-in work-count experiment in a disposable world. Real HOME producer, sink,
 * navigation and movement; the fixture never starts a path or moves an active mob.
 * The only experimental override caps extended searches, leaving ordinary searches alone.
 */
@PrefixGameTestTemplate(false)
public final class PathSearchBudgetGameTests {
    private static final int HORIZON = Integer.getInteger("mca.pathSearchHorizon", 160);
    private static final int EXTENDED_NODE_CAP = Integer.getInteger("mca.pathSearchNodeCap", 0);
    private static final String OPEN_BATCH = "mca_search_open";
    private static final String DETOUR_BATCH = "mca_search_detour";
    private static final String MEDIUM_DETOUR_BATCH = "mca_search_medium_detour";
    private static final String LONG_DETOUR_BATCH = "mca_search_long_detour";
    private static final String CORRIDOR_BATCH = "mca_search_long_corridor";
    private static final String BLOCKED_BATCH = "mca_search_blocked";
    private static final Set<ChunkPos> NEW_FORCED_CHUNKS = new HashSet<>();
    private static VillagerEntityMCA activeVillager;
    private static Integer previousHorizon;
    private static Boolean previousTeleport;

    private PathSearchBudgetGameTests() {
    }

    @GameTest(batch = OPEN_BATCH, templateNamespace = "mca_ab", template = "ab_air", timeoutTicks = 2_100)
    public static void brainWalks130BlocksThroughPartialSegments(GameTestHelper helper) {
        configure(helper);
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(130);
        rememberChunkTickets(helper.getLevel(), start.offset(-1, 0, -1), target.offset(1, 0, 1));
        prepareFlatPath(helper, start, target);
        observeJourney(helper, "open130", spawn(helper, start, target), start, target, 2_000, null);
    }

    @GameTest(batch = DETOUR_BATCH, templateNamespace = "mca_ab", template = "ab_air", timeoutTicks = 2_100)
    public static void brainWalksAround47BlockWall(GameTestHelper helper) {
        wallJourney(helper, "wall47", 23);
    }

    @GameTest(batch = MEDIUM_DETOUR_BATCH, templateNamespace = "mca_ab", template = "ab_air", timeoutTicks = 2_100)
    public static void brainWalksAround71BlockWall(GameTestHelper helper) {
        wallJourney(helper, "wall71", 35);
    }

    // Deliberate limit probe, not a promise that bounded A* solves every broad detour.
    // Its failed arrival remains visible in the output and is never counted as a passing journey.
    @GameTest(batch = LONG_DETOUR_BATCH, templateNamespace = "mca_ab", template = "ab_air", timeoutTicks = 2_100,
            required = false)
    public static void brainWalksAround111BlockWall(GameTestHelper helper) {
        wallJourney(helper, "wall111", 55);
    }

    @GameTest(batch = CORRIDOR_BATCH, templateNamespace = "mca_ab", template = "ab_air", timeoutTicks = 2_100)
    public static void brainWalks114BlockCorridorToNearbyHome(GameTestHelper helper) {
        configure(helper);
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(2);
        BlockPos center = start.offset(1, 0, 28);
        rememberChunkTickets(helper.getLevel(), center.offset(-32, 0, -32), center.offset(32, 0, 32));
        prepareFlatArea(helper, center, 32, 3);
        // A U-shaped single-cell passage separates route-length limits from
        // the broad wall's branching/node-budget exhaustion. No other exit exists.
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
        VillagerEntityMCA villager = spawn(helper, start, target);
        boolean[] passedTurn = {false};
        observeJourney(helper, "corridor114", villager, start, target, 2_000, elapsed -> {
            passedTurn[0] |= villager.getZ() > start.getZ() + 55.5D;
            if (villager.blockPosition().equals(target)) {
                helper.assertTrue(passedTurn[0], "villager bypassed the long corridor");
            }
        });
    }

    private static void wallJourney(GameTestHelper helper, String scenario, int halfWallLength) {
        configure(helper);
        int radius = halfWallLength + 6;
        BlockPos start = helper.absolutePos(new BlockPos(radius + 4, 1, radius + 4));
        BlockPos target = start.east(2);
        rememberChunkTickets(helper.getLevel(), start.offset(-radius, 0, -radius), start.offset(radius, 0, radius));
        prepareFlatArea(helper, start, radius, 3);
        for (int z = -halfWallLength; z <= halfWallLength; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA villager = spawn(helper, start, target);
        boolean[] passedWallEnd = {false};
        boolean[] leftStart = {false};
        observeJourney(helper, scenario, villager, start, target, 2_000, elapsed -> {
            leftStart[0] |= villager.blockPosition().distSqr(start) >= 64.0D;
            if (leftStart[0] && villager.blockPosition().equals(start)) {
                report(scenario + "_backtracked", villager, start, false, elapsed);
                helper.fail("villager returned to its original start instead of completing the detour");
            }
            passedWallEnd[0] |= Math.abs(villager.getZ() - start.getZ() - 0.5D) > halfWallLength + 0.5D;
            if (villager.blockPosition().equals(target)) {
                helper.assertTrue(passedWallEnd[0], "villager reached HOME without going around the wall");
            }
        });
    }

    @GameTest(batch = BLOCKED_BATCH, templateNamespace = "mca_ab", template = "ab_air", timeoutTicks = 1_100)
    public static void brainRetriesBlockedHomeAndRecoversWhenEntranceOpens(GameTestHelper helper) {
        configure(helper);
        BlockPos start = helper.absolutePos(new BlockPos(24, 1, 24));
        BlockPos target = start.east(10);
        rememberChunkTickets(helper.getLevel(), start.offset(-20, 0, -20), start.offset(20, 0, 20));
        prepareFlatArea(helper, start, 20, 3);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != 0 || z != 0) {
                    for (int y = 0; y < 3; y++) {
                        helper.getLevel().setBlock(target.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
        VillagerEntityMCA villager = spawn(helper, start, target);
        boolean[] opened = {false};
        observeJourney(helper, "blocked_then_open", villager, start, target, 1_000, elapsed -> {
            if (elapsed == 300) {
                helper.assertTrue(!villager.blockPosition().equals(target), "sealed HOME was somehow reached");
                report("blocked300", villager, start, false, elapsed);
                // This nearby HOME is sealed on every side, not behind a broad obstacle.
                // Retries must stay in the local goal neighborhood, not invent a distant excursion.
                double localGoalRadius = start.distManhattan(target) + 2.0D;
                helper.assertTrue(villager.position().distanceTo(Vec3.atBottomCenterOf(start)) <= localGoalRadius,
                        "sealed nearby HOME triggered speculative wandering beyond the local goal neighborhood");
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(target.west().above(y), Blocks.AIR.defaultBlockState(), 3);
                }
                opened[0] = true;
            }
            if (elapsed == 340) {
                helper.assertTrue(villager.blockPosition().equals(target),
                        "opened HOME did not wake the stalled route within 40 ticks");
            }
            if (villager.blockPosition().equals(target)) {
                helper.assertTrue(opened[0], "blocked HOME was reached before opening the entrance");
            }
        });
    }

    private static void configure(GameTestHelper helper) {
        helper.assertTrue(HORIZON >= 48 && EXTENDED_NODE_CAP >= 0, "invalid search experiment settings");
        previousHorizon = Config.getInstance().villagerPathfindingDistance;
        previousTeleport = Config.getInstance().allowVillagerTeleporting;
        Config.getInstance().villagerPathfindingDistance = HORIZON;
        Config.getInstance().allowVillagerTeleporting = false;
    }

    private static VillagerEntityMCA spawn(GameTestHelper helper, BlockPos start, BlockPos target) {
        VillagerEntityMCA villager = new VillagerEntityMCA(Gender.MALE.getVillagerType(), helper.getLevel(), Gender.MALE) {
            @Override
            protected PathNavigation createNavigation(Level level) {
                return new BudgetProbeNavigation(this, level);
            }
        };
        activeVillager = villager;
        villager.setAge(0);
        villager.absMoveTo(start.getX() + 0.5D, start.getY(), start.getZ() + 0.5D);
        villager.getRandom().setSeed(42L);
        WorldUtils.spawnEntity(helper.getLevel(), villager, MobSpawnType.STRUCTURE);
        // Genetics and traits use their own RNGs; normalize body and movement explicitly.
        villager.getGenetics().setGene(Genetics.SIZE, 0.5F);
        villager.getGenetics().setGene(Genetics.WIDTH, 0.5F);
        villager.getTraits().getTraits().forEach(villager.getTraits()::removeTrait);
        villager.refreshDimensions();
        var followRange = villager.getAttribute(Attributes.FOLLOW_RANGE);
        followRange.removeModifier(ResourceLocation.withDefaultNamespace("random_spawn_bonus"));
        followRange.setBaseValue(48.0D);
        villager.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.5D);
        villager.setOnGround(true);
        villager.refreshBrain(helper.getLevel());
        @SuppressWarnings("unchecked")
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>) (Brain<?>) villager.getBrain();
        brain.stopAll(helper.getLevel(), villager);
        brain.removeAllBehaviors();
        brain.addActivity(Activity.CORE, 0, ImmutableList.of(new WanderOrTeleportToTargetTask()));
        brain.addActivity(Activity.CORE, 1, ImmutableList.of(ExtendedWalkTowardsTask.createWithoutPoiRelease(
                MemoryModuleType.HOME, 0.7F, 0, 2_500, ignored -> false, ignored -> { })));
        brain.addActivity(Activity.IDLE, 0, ImmutableList.of(new DoNothing(100, 100)));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.IDLE).build());
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        // Allow initial gravity/dimension changes to settle before publishing HOME.
        // Otherwise first-tick canUpdatePath() failures inject an unrelated random fallback.
        helper.runAfterDelay(10L, () -> brain.setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), target)));
        return villager;
    }

    private static void observeJourney(GameTestHelper helper, String scenario, VillagerEntityMCA villager,
                                       BlockPos start, BlockPos target, int timeout,
                                       LongConsumer beforeArrival) {
        long started = helper.getLevel().getGameTime();
        Vec3[] previous = {villager.position()};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - started;
            helper.assertTrue(villager.position().distanceTo(previous[0]) < 3.0D, "journey teleported instead of walking");
            previous[0] = villager.position();
            if (elapsed < 11L) {
                return;
            }
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME)
                    .filter(home -> home.pos().equals(target)).isPresent(), "journey changed its logical HOME");
            if (beforeArrival != null) {
                beforeArrival.accept(elapsed);
            }
            if (villager.blockPosition().equals(target)) {
                var searches = PathRequestDiagnostics.snapshot(villager);
                helper.assertTrue(searches.sinkRequests() > 0 && searches.expandedNodes() > 0,
                        "journey did not measure the real Brain search lifecycle: " + searches);
                report(scenario, villager, start, true, elapsed);
                helper.succeed();
            } else if (elapsed >= timeout) {
                report(scenario, villager, start, false, elapsed);
                helper.fail("search alternative failed to reach HOME: scenario=" + scenario
                        + ", horizon=" + HORIZON + ", nodeCap=" + EXTENDED_NODE_CAP
                        + ", position=" + villager.blockPosition() + ", path=" + villager.getNavigation().getPath());
            }
        });
    }

    private static void report(String scenario, VillagerEntityMCA villager, BlockPos start, boolean arrived, long ticks) {
        var searches = PathRequestDiagnostics.snapshot(villager);
        MCA.LOGGER.info("[MCA Search Probe] scenario={} horizon={} extendedNodeCap={} arrived={} ticks={} "
                        + "distance={} ordinary={} extended={} suppressed={} deferred={} expanded={} maxExpanded={} searchMs={}",
                scenario, HORIZON, EXTENDED_NODE_CAP, arrived, ticks,
                villager.position().distanceTo(Vec3.atBottomCenterOf(start)),
                searches.ordinarySearches(), searches.extendedSearches(), searches.suppressedExtended(),
                searches.deferredProducerRetries(), searches.expandedNodes(), searches.maxExpandedNodes(),
                searches.searchNanos() / 1_000_000.0D);
    }

    /** Delegate the actual MCA finder, scaling only the budget argument for extended searches. */
    private static final class BudgetProbeNavigation extends MCAGroundPathNavigation {
        private BudgetProbeNavigation(Mob mob, Level level) {
            super(mob, level);
        }

        @Override
        protected void doStuckDetection(Vec3 position) {
            Path active = this.path;
            long timer = this.timeoutTimer;
            double limit = this.timeoutLimit;
            super.doStuckDetection(position);
            if (active != null && this.path == null) {
                MCA.LOGGER.info("[MCA Navigation Stop Probe] position={} nextIndex={} nodes={} timer={} limit={} stuck={}",
                        position, active.getNextNodeIndex(), active.getNodeCount(), timer, limit, this.isStuck());
            }
        }

        @Override
        protected PathFinder createPathFinder(int maxVisitedNodes) {
            PathFinder actualFinder = super.createPathFinder(maxVisitedNodes);
            int ordinaryBudget = Math.max(maxVisitedNodes, 768);
            return new PathFinder(this.nodeEvaluator, ordinaryBudget) {
                @Override
                public Path findPath(PathNavigationRegion region, Mob mob, Set<BlockPos> targets,
                                     float maxPathLength, int reachRange, float visitedNodesMultiplier) {
                    float adjustment = 1.0F;
                    if (EXTENDED_NODE_CAP > 0 && maxPathLength > Math.max(48.0D, mob.getAttributeValue(Attributes.FOLLOW_RANGE))) {
                        adjustment = Math.min(1.0F, EXTENDED_NODE_CAP / Math.max(ordinaryBudget, maxPathLength * 16.0F));
                    }
                    long before = PathRequestDiagnostics.snapshot(mob).expandedNodes();
                    Path result = actualFinder.findPath(region, mob, targets, maxPathLength, reachRange,
                            visitedNodesMultiplier * adjustment);
                    long expanded = PathRequestDiagnostics.snapshot(mob).expandedNodes() - before;
                    int budget = (int) (ordinaryBudget * visitedNodesMultiplier * adjustment
                            * Math.max(1.0F, maxPathLength * 16.0F / ordinaryBudget));
                    if (Boolean.getBoolean("mca.pathFrontierAudit")) {
                        SearchFrontierGameTests.log("brain", actualFinder, BudgetProbeNavigation.this.nodeEvaluator,
                                mob.blockPosition(), budget, maxPathLength, result);
                    }
                    MCA.LOGGER.info("[MCA Search Query] maxLength={} budget={} expanded={} position={} targets={} "
                                    + "reachable={} nodes={} end={}", maxPathLength, budget, expanded,
                            mob.blockPosition(), targets, result != null && result.canReach(),
                            result == null ? 0 : result.getNodeCount(), result == null ? null : result.getEndNode());
                    return result;
                }
            };
        }
    }

    private static void rememberChunkTickets(ServerLevel level, BlockPos min, BlockPos max) {
        ChunkPos first = new ChunkPos(min);
        ChunkPos last = new ChunkPos(max);
        for (int x = first.x; x <= last.x; x++) {
            for (int z = first.z; z <= last.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.getForcedChunks().contains(chunk.toLong())) {
                    NEW_FORCED_CHUNKS.add(chunk);
                }
            }
        }
    }

    @AfterBatch(batch = OPEN_BATCH)
    public static void cleanupOpen(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = DETOUR_BATCH)
    public static void cleanupDetour(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = MEDIUM_DETOUR_BATCH)
    public static void cleanupMediumDetour(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = LONG_DETOUR_BATCH)
    public static void cleanupLongDetour(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = CORRIDOR_BATCH)
    public static void cleanupCorridor(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = BLOCKED_BATCH)
    public static void cleanupBlocked(ServerLevel level) {
        cleanup(level);
    }

    private static void cleanup(ServerLevel level) {
        if (activeVillager != null) {
            activeVillager.discard();
            activeVillager = null;
        }
        NEW_FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
        NEW_FORCED_CHUNKS.clear();
        if (previousHorizon != null) {
            Config.getInstance().villagerPathfindingDistance = previousHorizon;
            previousHorizon = null;
        }
        if (previousTeleport != null) {
            Config.getInstance().allowVillagerTeleporting = previousTeleport;
            previousTeleport = null;
        }
    }
}
