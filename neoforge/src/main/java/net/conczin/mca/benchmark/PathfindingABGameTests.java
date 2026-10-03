package net.conczin.mca.benchmark;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Identical, self-contained A/B fixture used on origin/1.21.1 and the
 * development worktree. Do not reference diagnostics or optimized-only APIs.
 */
@PrefixGameTestTemplate(false)
public final class PathfindingABGameTests {
    private static final String NORMAL_BATCH = "zz_mca_ab_normal";
    private static final String STRESS_BATCH = "zz_mca_ab_stress";
    private static final Identifier RANDOM_SPAWN_BONUS_ID =
            Identifier.withDefaultNamespace("random_spawn_bonus");
    private static final List<VillagerEntityMCA> VILLAGERS = new ArrayList<>();
    private static final int COUNT = 24;
    private static final int WARMUP = 100;
    private static final int SAMPLE = 650;
    private static int previousPathDistance;

    private PathfindingABGameTests() {
    }

    @GameTest(batch = NORMAL_BATCH, templateNamespace = "mca_ab",
            template = "ab_air", timeoutTicks = 1200)
    public static void normalBrainAcrossTwoGates(GameTestHelper helper) {
        previousPathDistance = Config.getInstance().villagerPathfindingDistance;
        Config.getInstance().villagerPathfindingDistance = 160;
        BlockPos center = helper.absolutePos(new BlockPos(80, 1, 80));
        prepareFlatArea(helper, center, 40, 3);

        for (int z = -24; z <= 24; z++) {
            BlockPos wall = center.offset(0, 0, z);
            if (z == -12 || z == 12) {
                helper.getLevel().setBlock(wall, Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);
            } else {
                helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        BlockPos bell = center.offset(-4, 0, 0);
        helper.getLevel().setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
        VillageManager villageManager = VillageManager.get(helper.getLevel());
        helper.assertTrue(villageManager.processBuilding(bell) == Building.validationResult.SUCCESS,
                "normal benchmark did not register its MCA town center");
        createRegisteredHouse(helper, center.offset(-22, 0, 28));
        createRegisteredHouse(helper, center.offset(16, 0, 28));
        Village benchmarkVillage = villageManager.findNearestVillage(bell, Village.MERGE_MARGIN).orElse(null);
        long registeredRooms = benchmarkVillage == null ? 0 : benchmarkVillage.getBuildings().values().stream()
                .filter(room -> !room.getBuildingType().grouped()).count();
        helper.assertTrue(registeredRooms == 2,
                "normal benchmark requires two actual registered MCA rooms");
        BlockState foot = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, BedPart.FOOT);
        double[] initialX = new double[COUNT];
        double[] initialZ = new double[COUNT];
        for (int i = 0; i < COUNT; i++) {
            boolean west = i % 2 == 0;
            int z = -17 + (i / 2) * 3;
            BlockPos origin = center.offset(west ? -17 : 17, 0, z);
            BlockPos home = center.offset(west ? 19 : -19, 0, z);
            helper.getLevel().setBlock(home, foot, 3);
            helper.getLevel().setBlock(home.south(),
                    foot.setValue(BedBlock.PART, BedPart.HEAD), 3);
            VillagerFactory factory = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0).withPosition(Vec3.atBottomCenterOf(origin))
                    .withName("AB normal villager " + i);
            if (i % 4 == 3) {
                factory.withProfession(ProfessionsMCA.GUARD);
            } else if (i % 3 == 0) {
                factory.withProfession(BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.FARMER).value());
            }
            VillagerEntityMCA villager = factory.spawn(EntitySpawnReason.STRUCTURE);
            setBenchmarkFollowRange(villager);
            if (i % 4 == 3) {
                villager.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                villager.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
            }
            VILLAGERS.add(villager);
            initialX[i] = villager.getX();
            initialZ[i] = villager.getZ();
            villager.refreshBrain(helper.getLevel());
            villager.getBrain().setMemory(MemoryModuleType.HOME,
                    GlobalPos.of(helper.getLevel().dimension(), home));
            if (villager.getProfession() == BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.FARMER).value()) {
                BlockPos job = home.offset(west ? -3 : 3, 0, 0);
                helper.getLevel().setBlock(job, Blocks.COMPOSTER.defaultBlockState(), 3);
                villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                        GlobalPos.of(helper.getLevel().dimension(), job));
            }
            if (i % 3 == 1) {
                villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT,
                        GlobalPos.of(helper.getLevel().dimension(), bell));
            }
        }

        long initialProfiles = profileCount();
        long startTick = helper.getLevel().getGameTime();
        MCA.LOGGER.info("[MCA AB] scenario=normal debugInstrumentationOptOut={}",
                Boolean.getBoolean("mca.pathDebug.benchmarkWithoutDiagnostics"));
        MCA.LOGGER.info("[MCA AB] scenario=normal ready villagers={} registeredRooms={} equippedGuards={} "
                        + "warmup={} sample={}", COUNT, registeredRooms,
                COUNT / 4, WARMUP, SAMPLE);
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - startTick;
            if (elapsed == WARMUP) {
                profile(helper, "start");
                MCA.LOGGER.info("[MCA AB] scenario=normal sample-start tick={}", elapsed);
            }
            if (elapsed == WARMUP + SAMPLE) {
                profile(helper, "stop --save-to-file");
                int moved = 0;
                int crossed = 0;
                int active = 0;
                double totalDistance = 0.0;
                for (int i = 0; i < VILLAGERS.size(); i++) {
                    VillagerEntityMCA v = VILLAGERS.get(i);
                    double distance = Math.hypot(v.getX() - initialX[i], v.getZ() - initialZ[i]);
                    totalDistance += distance;
                    if (distance >= 4.0) moved++;
                    if ((initialX[i] < center.getX() && v.getX() > center.getX() + 1)
                            || (initialX[i] > center.getX() && v.getX() < center.getX() - 1)) {
                        crossed++;
                    }
                    if (v.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) active++;
                }
                MCA.LOGGER.info("[MCA AB] scenario=normal result villagers={} moved4={} crossed={} activeTargets={} totalDistance={}",
                        VILLAGERS.size(), moved, crossed, active, totalDistance);
            }
            if (elapsed > WARMUP + SAMPLE + 20 && elapsed % 20 == 0
                    && profileCount() > initialProfiles) {
                helper.succeed();
            }
            if (elapsed > WARMUP + SAMPLE && profileCount() <= initialProfiles) {
                waitForProfileWorker();
            }
        });
    }

    @GameTest(batch = STRESS_BATCH, templateNamespace = "mca_ab",
            template = "ab_air", timeoutTicks = 1100)
    public static void identicalNavigationQueries(GameTestHelper helper) {
        ThreadMXBean threadCpu = ManagementFactory.getThreadMXBean();
        helper.assertTrue(threadCpu.isCurrentThreadCpuTimeSupported(),
                "JVM must support current-thread CPU time for the controlled A/B benchmark");
        if (!threadCpu.isThreadCpuTimeEnabled()) {
            threadCpu.setThreadCpuTimeEnabled(true);
        }
        previousPathDistance = Config.getInstance().villagerPathfindingDistance;
        Config.getInstance().villagerPathfindingDistance = 160;
        BlockPos center = helper.absolutePos(new BlockPos(80, 1, 80));
        prepareFlatArea(helper, center, 29, 3);
        // A separate, chunk-forced open corridor makes the fourth workload
        // a real long-distance request rather than an unloaded-chunk lookup.
        prepareFlatArea(helper, center.west(45), 20, 3);
        // Single two-block-high wall with a detour at its ends.
        for (int z = -9; z <= 9; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(center.offset(1, y, z),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
        // Isolate one destination beyond the wall so partial paths must be detected.
        BlockPos trapped = center.offset(10, 0, 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(trapped.offset(dx, y, dz),
                            Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }

        BlockPos[] targets = new BlockPos[COUNT];
        long[] calls = new long[4];
        long[] nanos = new long[4];
        long[] cpuNanos = new long[4];
        long[] reachable = new long[4];
        long[] partial = new long[4];
        long[] noPath = new long[4];
        double[] remainingDistance = new double[4];
        for (int i = 0; i < COUNT; i++) {
            BlockPos origin = center.offset(-3 - (i % 6) * 2, 0, -6 + (i / 6) * 4);
            int scenario = i % 4;
            targets[i] = switch (scenario) {
                case 0 -> origin.west(6);
                case 1 -> center.offset(6, 0, -2);
                case 2 -> trapped;
                default -> center.west(58);
            };
            VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0).withPosition(Vec3.atBottomCenterOf(origin))
                    .withName("AB path query " + i).spawn(EntitySpawnReason.STRUCTURE);
            setBenchmarkFollowRange(villager);
            helper.assertTrue(Math.abs(villager.getAttributeValue(Attributes.FOLLOW_RANGE) - 48.0D) < 1.0E-6D,
                    "stress benchmark must use deterministic 48-block follow range");
            villager.setNoAi(true);
            // Both implementations receive exactly the same static walk intent.
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(targets[i], 0.75F, 0));
            VILLAGERS.add(villager);
        }

        long initialProfiles = profileCount();
        long startTick = helper.getLevel().getGameTime();
        MCA.LOGGER.info("[MCA AB] scenario=stress ready villagers={} warmup=60 sample=420",
                COUNT);
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - startTick;
            if (elapsed == 60L) {
                profile(helper, "start");
                MCA.LOGGER.info("[MCA AB] scenario=stress sample-start tick={}", elapsed);
            }
            if (elapsed > 60L && elapsed <= 480L && elapsed % 20L == 0L) {
                for (int i = 0; i < COUNT; i++) {
                    VillagerEntityMCA v = VILLAGERS.get(i);
                    int scenario = i % 4;
                    // No-AI mobs do not necessarily update their on-ground flag;
                    // vanilla's path navigator refuses to search while airborne.
                    v.setOnGround(true);
                    v.getNavigation().stop();
                    long cpu0 = threadCpu.getCurrentThreadCpuTime();
                    long t0 = System.nanoTime();
                    Path path = v.getNavigation().createPath(targets[i], 0);
                    nanos[scenario] += System.nanoTime() - t0;
                    cpuNanos[scenario] += threadCpu.getCurrentThreadCpuTime() - cpu0;
                    calls[scenario]++;
                    if (path == null) {
                        noPath[scenario]++;
                    } else if (path.canReach()) {
                        reachable[scenario]++;
                    } else {
                        partial[scenario]++;
                    }
                    if (path != null) remainingDistance[scenario] += path.getDistToTarget();
                    if (scenario == 1 && elapsed == 80L) {
                        BlockPos endpoint = path == null || path.getNodeCount() == 0
                                ? null : path.getEndNode().asBlockPos();
                        MCA.LOGGER.info("[MCA AB DETOUR] index={} origin={} target={} followRange={} reachable={} "
                                        + "pathEnd={} distanceToTarget={}",
                                i, v.blockPosition(), targets[i], v.getAttributeValue(Attributes.FOLLOW_RANGE),
                                path != null && path.canReach(), endpoint,
                                path == null ? -1.0F : path.getDistToTarget());
                    }
                }
            }
            if (elapsed == 480L) {
                profile(helper, "stop --save-to-file");
                for (int i = 0; i < calls.length; i++) {
                    MCA.LOGGER.info("[MCA AB] scenario=stress type={} calls={} totalMs={} totalCpuMs={} reachable={} partial={} null={} remainingDistanceSum={}",
                            new String[]{"open", "detour", "blocked", "long"}[i],
                            calls[i], nanos[i] / 1_000_000.0, cpuNanos[i] / 1_000_000.0,
                            reachable[i], partial[i], noPath[i], remainingDistance[i]);
                }
            }
            if (elapsed == 500L) {
                if (Boolean.getBoolean("mca.pathFrontierAudit")) {
                    auditBroadWallSearches(helper);
                }
                // Diagnostic only: after the timed capture, test whether a
                // partial detour resolves with more A* visited nodes. Never
                // include these probes in the measured search totals.
                for (int i = 0; i < COUNT; i++) {
                    if (i % 4 != 1) {
                        continue;
                    }
                    VillagerEntityMCA villager = VILLAGERS.get(i);
                    villager.setOnGround(true);
                    villager.getNavigation().stop();
                    villager.getNavigation().setMaxVisitedNodesMultiplier(2.0F);
                    try {
                        Path expanded = villager.getNavigation().createPath(targets[i], 0);
                        BlockPos endpoint = expanded == null || expanded.getNodeCount() == 0
                                ? null : expanded.getEndNode().asBlockPos();
                        MCA.LOGGER.info("[MCA AB DETOUR BUDGET] index={} multiplier=2 reachable={} pathEnd={} "
                                        + "distanceToTarget={}",
                                i, expanded != null && expanded.canReach(), endpoint,
                                expanded == null ? -1.0F : expanded.getDistToTarget());
                        var range = villager.getAttribute(Attributes.FOLLOW_RANGE);
                        double originalRange = range.getBaseValue();
                        try {
                            // MCA scales its own visited-node allowance with
                            // search length. Resetting here gives the same
                            // effective 2x budget as the preceding 48-block probe.
                            villager.getNavigation().resetMaxVisitedNodesMultiplier();
                            range.setBaseValue(96.0D);
                            villager.getNavigation().stop();
                            Path longer = villager.getNavigation().createPath(targets[i], 0);
                            BlockPos longerEnd = longer == null || longer.getNodeCount() == 0
                                    ? null : longer.getEndNode().asBlockPos();
                            MCA.LOGGER.info("[MCA AB DETOUR RANGE] index={} followRange=96 multiplier=1 "
                                            + "reachable={} pathEnd={} distanceToTarget={}",
                                    i, longer != null && longer.canReach(), longerEnd,
                                    longer == null ? -1.0F : longer.getDistToTarget());
                        } finally {
                            range.setBaseValue(originalRange);
                        }
                    } finally {
                        villager.getNavigation().resetMaxVisitedNodesMultiplier();
                    }
                }
            }
            if (elapsed > 500L && elapsed % 20L == 0L && profileCount() > initialProfiles) {
                helper.succeed();
            }
            if (elapsed > 480L && profileCount() <= initialProfiles) {
                waitForProfileWorker();
            }
        });
    }

    /**
     * Replay the existing wall geometry with equal native search allowances.
     * Component diagnosis only: this does not install paths or assert a Brain journey.
     */
    private static void auditBroadWallSearches(GameTestHelper helper) {
        for (int halfWallLength : new int[]{35, 55}) {
            // Fixed coordinates also fix Node hash/tie ordering across the two worlds.
            BlockPos start = new BlockPos(256, -59, 256);
            BlockPos target = start.east(2);
            prepareFlatArea(helper, start, halfWallLength + 6, 3);
            for (int z = -halfWallLength; z <= halfWallLength; z++) {
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0).withPosition(Vec3.atBottomCenterOf(start))
                    .withName("AB wall frontier").spawn(EntitySpawnReason.STRUCTURE);
            VILLAGERS.add(villager);
            setBenchmarkFollowRange(villager);
            villager.getGenetics().setGene(net.conczin.mca.entity.ai.Genetics.SIZE, 0.5F);
            villager.getGenetics().setGene(net.conczin.mca.entity.ai.Genetics.WIDTH, 0.5F);
            villager.getTraits().getTraits().forEach(villager.getTraits()::removeTrait);
            villager.refreshDimensions();
            villager.setNoAi(true);
            villager.setOnGround(true);
            PathNavigationRegion region = new PathNavigationRegion(helper.getLevel(),
                    start.offset(-168, -8, -168), start.offset(168, 8, 168));
            var evaluator = villager.getNavigation().getNodeEvaluator();
            String label = "wall" + (halfWallLength * 2 + 1);
            for (int budget : new int[]{768, 2_560, 5_120}) {
                float maxLength = budget == 768 ? 48.0F : 160.0F;
                PathFinder finder = new PathFinder(evaluator, budget);
                Path path = finder.findPath(region, villager, Set.of(target), maxLength, 0, 1.0F);
                SearchFrontierGameTests.log(label + "-mca", finder, evaluator, start, budget, maxLength, path);
                helper.assertTrue(path != null, "wall component probe could not start: " + label);
            }
            // Same mob, terrain and budget with vanilla evaluation isolates MCA traversal changes.
            WalkNodeEvaluator vanilla = new WalkNodeEvaluator();
            vanilla.setCanPassDoors(true);
            vanilla.setCanOpenDoors(true);
            PathFinder finder = new PathFinder(vanilla, 2_560);
            Path path = finder.findPath(region, villager, Set.of(target), 160.0F, 0, 1.0F);
            SearchFrontierGameTests.log(label + "-vanilla", finder, vanilla, start, 2_560, 160.0F, path);
            helper.assertTrue(path != null, "vanilla wall component probe could not start: " + label);
            villager.discard();
        }
    }

    private static void createRegisteredHouse(GameTestHelper helper, BlockPos min) {
        // An actual roofed six-by-six MCA room, not merely a bed on open terrain.
        // Identical geometry and the public building-registration path are used
        // in both baseline and current worktrees; assert registration before
        // starting the timed Brain capture.
        for (int x = -1; x <= 6; x++) {
            for (int z = -1; z <= 6; z++) {
                BlockPos pos = min.offset(x, 0, z);
                boolean wall = x == -1 || x == 6 || z == -1 || z == 6;
                helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(pos, wall ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(pos.above(), wall ? Blocks.STONE.defaultBlockState()
                        : Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(pos.above(2), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        BlockState door = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        BlockPos entry = min.offset(2, 0, -1);
        helper.getLevel().setBlock(entry, door, 3);
        helper.getLevel().setBlock(entry.above(),
                door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        BlockState bed = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, BedPart.FOOT);
        BlockPos bedFoot = min.offset(1, 0, 1);
        helper.getLevel().setBlock(bedFoot, bed, 3);
        helper.getLevel().setBlock(bedFoot.south(), bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        BlockPos source = min.offset(3, 0, 3);
        var result = VillageManager.get(helper.getLevel()).processBuilding(source);
        helper.assertTrue(result == Building.validationResult.SUCCESS,
                "normal benchmark MCA room registration failed at " + source + ": " + result);
    }

    private static void setBenchmarkFollowRange(VillagerEntityMCA villager) {
        var followRange = villager.getAttribute(Attributes.FOLLOW_RANGE);
        if (followRange == null) {
            throw new IllegalStateException("benchmark villager has no follow-range attribute");
        }
        // Vanilla applies this modifier during finalizeSpawn, after the navigation
        // finder was constructed. Keep the measured search range comparable.
        followRange.removeModifier(RANDOM_SPAWN_BONUS_ID);
        followRange.setBaseValue(48.0D);
    }

    private static void profile(GameTestHelper helper, String action) {
        helper.getLevel().getServer().getCommands().performPrefixedCommand(
                helper.getLevel().getServer().createCommandSourceStack(),
                "spark profiler " + action + (action.equals("start") ? " --thread * --interval 4" : ""));
    }

    private static long profileCount() {
        java.nio.file.Path directory = java.nio.file.Path.of("config", "spark");
        if (!Files.isDirectory(directory)) return 0;
        try (var stream = Files.list(directory)) {
            return stream.filter(p -> p.getFileName().toString().endsWith(".sparkprofile")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private static void waitForProfileWorker() {
        // GameTest fast-forwards hundreds of ticks in under a second, but Spark
        // writes on a separate worker. Allow wall time after stopping the sampler.
        try {
            Thread.sleep(25L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void prepareFlatArea(GameTestHelper helper, BlockPos center, int radius, int clearance) {
        ServerLevel level = helper.getLevel();
        ChunkPos first = ChunkPos.containing(center.offset(-radius, 0, -radius));
        ChunkPos last = ChunkPos.containing(center.offset(radius, 0, radius));
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = first.x(); x <= last.x(); x++) {
            for (int z = first.z(); z <= last.z(); z++) {
                level.setChunkForced(x, z, true);
                chunks.add(new ChunkPos(x, z));
            }
        }
        for (ChunkPos chunk : chunks) level.getChunk(chunk.x(), chunk.z());
        level.getChunkSource().tick(() -> true, false);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        level.getServer().managedBlock(() -> chunks.stream().allMatch(chunk ->
                level.isPositionEntityTicking(new BlockPos(
                        chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ())))
                || System.nanoTime() >= deadline);
        helper.assertTrue(chunks.stream().allMatch(chunk -> level.isPositionEntityTicking(
                new BlockPos(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ()))),
                "benchmark chunks are not entity ticking");
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos feet = center.offset(x, 0, z);
                level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y < clearance; y++) {
                    level.setBlock(feet.above(y), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    @AfterBatch(batch = NORMAL_BATCH)
    public static void cleanupNormal(ServerLevel level) {
        cleanup();
    }

    @AfterBatch(batch = STRESS_BATCH)
    public static void cleanupStress(ServerLevel level) {
        cleanup();
    }

    private static void cleanup() {
        VILLAGERS.forEach(VillagerEntityMCA::discard);
        VILLAGERS.clear();
        Config.getInstance().villagerPathfindingDistance = previousPathDistance;
    }
}
