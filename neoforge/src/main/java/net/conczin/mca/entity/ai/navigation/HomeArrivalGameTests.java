package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/** Real REST/HOME producer, movement sink, door interaction and sleep, with natural entity ticks. */
@PrefixGameTestTemplate(false)
public final class HomeArrivalGameTests {
    private static final Set<VillagerEntityMCA> VILLAGERS = new HashSet<>();
    private static final Map<BlockPos, BlockState> ORIGINAL_BLOCKS = new LinkedHashMap<>();
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static Long originalDayTime;
    private static Boolean originalDaylightCycle;
    private static Boolean originalTeleport;

    private HomeArrivalGameTests() {
    }

    @GameTest(batch = "mca_home_bedside_corner", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 200)
    public static void restSleepsFromRecordedBedsideCorner(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(3, 4, 5));
        BlockPos door = start.east(6);
        BlockPos head = door.east(5);
        prepareHouse(helper, start, door, head.west());
        configureNight(helper.getLevel());
        VillagerEntityMCA villager = spawnResident(helper, head.west().south(), head);
        villager.getGenetics().setGene(Genetics.SIZE, 0.5F);
        villager.snapTo(head.getX() - 0.86901814644561D, head.getY(),
                head.getZ() + 1.869823516555417D);
        helper.assertTrue(!head.closerToCenterThan(villager.position(), 2.0D)
                        && head.closerToCenterThan(villager.position(), 2.1D),
                "recorded corner did not straddle vanilla sleep reach");
        var target = BedApproachTarget.create(helper.getLevel(), head).orElseThrow();
        helper.assertTrue(target.getPathTargets(villager).contains(head.west().south()),
                "recorded corner is not inside a safe bedside destination cell");
        helper.assertTrue(!target.isReached(villager, 0),
                "arrival accepted the recorded corner before reaching vanilla sleep range");
        helper.onEachTick(() -> {
            helper.assertTrue(villager.getBrain().isActive(Activity.REST), "fixture left REST");
            if (villager.isSleeping()) {
                helper.assertTrue(villager.getSleepingPos().filter(head::equals).isPresent(),
                        "villager slept at a different bed");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_home_small_bedside_corner", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 200)
    public static void smallResidentConvergesFromOuterBedsideCorner(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(3, 4, 5));
        BlockPos door = start.east(6);
        BlockPos head = door.east(5);
        prepareHouse(helper, start, door, head.west());
        configureNight(helper.getLevel());
        VillagerEntityMCA villager = spawnResident(helper, head.west().south(), head);
        villager.getGenetics().setGene(Genetics.WIDTH, 0.0F);
        // Random TOUGH genetics scale physical width by 1.2x; this must be
        // a narrow-collider test regardless of randomly generated traits.
        villager.getTraits().removeTrait(Traits.TOUGH);
        // Updating a tracked gene doesn't synchronously resize the physical collider.
        villager.refreshDimensions();
        villager.snapTo(head.getX() - 0.99D, head.getY(), head.getZ() + 1.99D);
        helper.assertTrue(villager.getBbWidth() < 0.5F && helper.getLevel().noCollision(villager),
                "small resident fixture was not collision-free and narrow: width=" + villager.getBbWidth()
                        + ", collisionFree=" + helper.getLevel().noCollision(villager)
                        + ", pos=" + villager.position());
        helper.assertTrue(!head.closerToCenterThan(villager.position(), 2.1D),
                "outer corner was already within bedside sleep reach");
        long started = helper.getLevel().getGameTime();
        helper.onEachTick(() -> {
            helper.assertTrue(villager.getBrain().isActive(Activity.REST), "small fixture left REST");
            if (villager.isSleeping()) {
                helper.assertTrue(villager.getSleepingPos().filter(head::equals).isPresent(),
                        "small resident slept at a different bed");
                helper.succeed();
            } else if (helper.getLevel().getGameTime() - started >= 180) {
                helper.fail("small resident failed to converge: width=" + villager.getBbWidth() + ", " + describe(villager));
            }
        });
    }

    @GameTest(batch = "mca_home_bedside_guards", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void bedsideArrivalPreservesRangeFloorCollisionAndBehindFoot(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(3, 4, 5));
        BlockPos door = start.east(6);
        BlockPos head = door.east(5);
        prepareHouse(helper, start, door, head.west());
        configureNight(helper.getLevel());
        VillagerEntityMCA villager = spawnResident(helper, head.west().south(), head);
        villager.setNoAi(true); // Isolate sleep eligibility; movement is tested by the natural REST test.
        var target = BedApproachTarget.create(helper.getLevel(), head).orElseThrow();
        villager.snapTo(head.getX() - 0.86901814644561D, head.getY(),
                head.getZ() + 1.869823516555417D);
        helper.assertTrue(!head.closerToCenterThan(villager.position(), 2.0D),
                "near-boundary corner fixture was inside vanilla sleep range");
        assertCannotStartSleep(helper, villager, "near-boundary side corner outside vanilla sleep range");
        villager.snapTo(head.getX() - 0.99D, head.getY(), head.getZ() + 1.99D);
        helper.assertTrue(!target.isReached(villager, 0),
                "arrival accepted a side corner beyond the bounded sleep tolerance");
        assertCannotStartSleep(helper, villager, "side corner beyond tolerance");

        villager.snapTo(head.getX() - 0.86901814644561D, head.getY() + 1.0D,
                head.getZ() + 1.869823516555417D);
        helper.assertTrue(head.closerToCenterThan(villager.position(), 2.1D),
                "wrong-floor fixture was not within extended spherical reach");
        helper.assertTrue(!target.isReached(villager, 0), "arrival accepted the wrong standing floor");
        assertCannotStartSleep(helper, villager, "wrong standing floor");

        BlockPos approach = head.west().south();
        helper.getLevel().setBlock(approach, Blocks.BEDROCK.defaultBlockState(), 3);
        helper.getLevel().setBlock(approach.above(), Blocks.BEDROCK.defaultBlockState(), 3);
        villager.snapTo(head.getX() - 0.86901814644561D, head.getY(),
                head.getZ() + 1.869823516555417D);
        helper.assertTrue(!target.isReached(villager, 0), "arrival accepted a blocked bedside cell");
        assertCannotStartSleep(helper, villager, "blocked bedside cell");

        helper.getLevel().setBlock(approach, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(approach.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(approach.west(), Blocks.BEDROCK.defaultBlockState(), 3);
        helper.getLevel().setBlock(approach.west().above(), Blocks.BEDROCK.defaultBlockState(), 3);
        helper.assertTrue(target.getPathTargets(villager).contains(approach),
                "wall-edge fixture did not retain a collision-safe approach centre");
        helper.assertTrue(!target.isReached(villager, 0),
                "arrival accepted an actual body intersecting the neighbouring wall");
        assertCannotStartSleep(helper, villager, "actual body intersecting the neighbouring wall");

        villager.snapTo(head.getX() - 1.9D, head.getY(), head.getZ() + 0.5D);
        helper.assertTrue(!head.closerToCenterThan(villager.position(), 2.1D),
                "behind-foot fixture did not exercise the existing exception");
        helper.assertTrue(target.isReached(villager, 0), "existing behind-foot arrival was lost");
        helper.assertTrue(new SleepInBed().tryStart(helper.getLevel(), villager,
                        helper.getLevel().getGameTime()) && villager.isSleeping(),
                "existing behind-foot sleep exception was lost");
        helper.succeed();
    }

    private static void assertCannotStartSleep(GameTestHelper helper, VillagerEntityMCA villager, String scenario) {
        helper.assertTrue(!new SleepInBed().tryStart(helper.getLevel(), villager,
                        helper.getLevel().getGameTime()) && !villager.isSleeping(),
                "sleep accepted " + scenario);
    }

    @GameTest(batch = "mca_home_intermediate_span", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void bedIntentDoesNotHoldIntermediatePhysicalEndpoint(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(3, 4, 5));
        BlockPos door = start.east(6);
        BlockPos head = door.east(5);
        prepareHouse(helper, start, door, head.west());
        configureNight(helper.getLevel());
        BlockPos intermediate = door.east(2);
        VillagerEntityMCA villager = spawnResident(helper, intermediate, head);
        villager.setNoAi(true); // Exercise native cursor advancement, not Brain route production.
        var target = BedApproachTarget.create(helper.getLevel(), head).orElseThrow();
        helper.assertTrue(!target.getPathTargets(villager).contains(intermediate),
                "intermediate span fixture was actually a bedside endpoint");
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 0));
        ArrivalNavigationProbe navigation = new ArrivalNavigationProbe(villager, helper.getLevel());
        Path span = new Path(List.of(new Node(intermediate.getX(), intermediate.getY(), intermediate.getZ())),
                intermediate, true);
        helper.assertTrue(navigation.moveTo(span, 0.5D), "intermediate physical span was rejected");
        navigation.advancePhysicalPath();
        helper.assertTrue(span.isDone(), "bed intent retained an already-reached intermediate physical endpoint");
        helper.succeed();
    }

    private static final class ArrivalNavigationProbe extends MCAGroundPathNavigation {
        private ArrivalNavigationProbe(VillagerEntityMCA villager, ServerLevel level) {
            super(villager, level);
        }

        private void advancePhysicalPath() {
            followThePath();
        }
    }

    @GameTest(batch = "mca_home_flat_door", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 600)
    public static void restWalksThroughClosedDoorAndSleeps(GameTestHelper helper) {
        verifyArrival(helper, 0, false);
    }

    @GameTest(batch = "mca_home_raised_steps", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 600)
    public static void restClimbsTwoStepsThroughClosedDoorAndSleeps(GameTestHelper helper) {
        verifyArrival(helper, 2, false);
    }

    @GameTest(batch = "mca_home_repaired_ledge", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 700)
    public static void restRecoversWhenStepsAreAddedToTwoBlockLedge(GameTestHelper helper) {
        verifyArrival(helper, 2, true);
    }

    @GameTest(batch = "mca_home_drop", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 600)
    public static void restDescendsTwoBlockDropThroughClosedDoorAndSleeps(GameTestHelper helper) {
        verifyArrival(helper, -2, false);
    }

    @GameTest(batch = "mca_home_retry_phase", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void restRetriesWhenFailureAndProducerTicksHaveOppositeParity(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(3, 4, 5));
        BlockPos door = start.east(6);
        BlockPos head = door.east(5);
        prepareHouse(helper, start, door, head.west());
        configureNight(helper.getLevel());
        VillagerEntityMCA villager = spawnResident(helper, start, head);
        villager.setNoAi(true);
        var homeProducer = VillagerTasksMCA.getRestPackage(0.5F).stream()
                .filter(entry -> entry.getFirst() == 2).findFirst().orElseThrow().getSecond();
        long now = helper.getLevel().getGameTime();
        WalkTargetFailureMemory.record(villager, GlobalPos.of(helper.getLevel().dimension(), head), now - 1L);
        helper.onEachTick(() -> {
            long tick = helper.getLevel().getGameTime();
            if (homeProducer.getStatus() == Behavior.Status.STOPPED) {
                homeProducer.tryStart(helper.getLevel(), villager, tick);
            }
            if (homeProducer.getStatus() == Behavior.Status.RUNNING) {
                homeProducer.tickOrStop(helper.getLevel(), villager, tick);
            }
            if (villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                helper.succeed();
            } else if (tick - now >= 40L) {
                helper.fail("HOME producer never retried an odd-age failure through ConditionalTask");
            }
        });
    }

    private static void verifyArrival(GameTestHelper helper, int heightDifference, boolean repairSteps) {
        BlockPos start = helper.absolutePos(new BlockPos(3, 4, 5));
        BlockPos door = start.offset(6, heightDifference, 0);
        BlockPos foot = door.east(4);
        BlockPos head = foot.east();
        prepareHouse(helper, start, door, foot);
        if (heightDifference == 2 && !repairSteps) {
            addSteps(helper.getLevel(), door);
        }
        configureNight(helper.getLevel());

        VillagerEntityMCA[] resident = {null};
        long[] started = {0L};
        Vec3[] previous = {null};
        boolean[] openedDoor = {false};
        boolean[] sawFailure = {false};
        boolean[] repaired = {false};
        // Register tick callbacks before execution starts: registering onEachTick
        // inside a delayed callback rehashes GameTest's live callback iterator.
        helper.runAfterDelay(10, () -> {
            resident[0] = spawnResident(helper, start, head);
            started[0] = helper.getLevel().getGameTime();
            previous[0] = resident[0].position();
        });
        helper.onEachTick(() -> {
            VillagerEntityMCA villager = resident[0];
            if (villager == null) {
                return;
            }
            long elapsed = helper.getLevel().getGameTime() - started[0];
            // Vanilla sleep snaps onto the bed. Transit must use physical movement.
            if (!villager.isSleeping()) {
                helper.assertTrue(villager.position().distanceTo(previous[0]) < 2.0D,
                        "HOME transit teleported: " + describe(villager));
            }
            previous[0] = villager.position();
            openedDoor[0] |= helper.getLevel().getBlockState(door).getValue(DoorBlock.OPEN);
            sawFailure[0] |= villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            if (repairSteps && !repaired[0] && elapsed >= 160) {
                helper.assertTrue(!villager.isSleeping() && !openedDoor[0]
                                && villager.getY() < door.getY() - 0.5D,
                        "two-block ledge was traversed without steps: " + describe(villager));
                helper.assertTrue(sawFailure[0], "unreachable raised HOME never recorded failure");
                MCA.LOGGER.info("[MCA Home Arrival] blocked ledge elapsed={} {}", elapsed, describe(villager));
                addSteps(helper.getLevel(), door);
                repaired[0] = true;
            }
            if (villager.isSleeping()) {
                helper.assertTrue(villager.getSleepingPos().filter(head::equals).isPresent(),
                        "villager slept at a different bed");
                helper.assertTrue(openedDoor[0], "villager reached bed without opening the only entrance");
                helper.assertTrue(!repairSteps || repaired[0], "villager reached raised HOME before repair");
                MCA.LOGGER.info("[MCA Home Arrival] arrived rise={} repaired={} elapsed={} {}",
                        heightDifference, repaired[0], elapsed, describe(villager));
                helper.succeed();
            } else if (elapsed >= 550) {
                helper.fail("REST did not reach HOME through entrance; rise=" + heightDifference
                        + ", repaired=" + repaired[0] + ", doorOpened=" + openedDoor[0]
                        + ", " + describe(villager));
            }
        });
    }

    private static void prepareHouse(GameTestHelper helper, BlockPos start, BlockPos door, BlockPos foot) {
        ServerLevel level = helper.getLevel();
        BlockPos min = start.offset(-3, -6, -9);
        BlockPos max = start.offset(15, 7, 9);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            ORIGINAL_BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        }
        ChunkPos minChunk = ChunkPos.containing(min);
        ChunkPos maxChunk = ChunkPos.containing(max);
        for (int x = minChunk.x(); x <= maxChunk.x(); x++) {
            for (int z = minChunk.z(); z <= maxChunk.z(); z++) {
                if (!level.getForceLoadedChunks().contains(new ChunkPos(x, z).pack())) {
                    FORCED_CHUNKS.add(new ChunkPos(x, z));
                }
            }
        }
        prepareFlatArea(helper, start.east(6), 9, 7);
        // The house has a single two-block-high doorway, a roof, and a real HOME bed.
        for (int x = 0; x <= 6; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = -4; y <= 3; y++) {
                    BlockPos pos = door.offset(x, y, z);
                    boolean wall = x == 0 || x == 6 || Math.abs(z) == 3;
                    level.setBlock(pos, y < 0 || y == 3 || wall
                            ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        BlockState doorState = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        level.setBlock(door, doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(door.above(), doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        BlockState bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(foot.east(), bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        if (door.getY() < start.getY()) {
            // Clear the high outdoor floor immediately in front of the low doorway.
            for (BlockPos pos : BlockPos.betweenClosed(door.west(2), door.west().above(2))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(door.west().below(), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(door.west(2).below(), Blocks.STONE.defaultBlockState(), 3);
        }
    }

    private static void addSteps(ServerLevel level, BlockPos door) {
        level.setBlock(door.west(2).below(2), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(door.west().below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(door.west().below(2), Blocks.STONE.defaultBlockState(), 3);
    }

    private static VillagerEntityMCA spawnResident(GameTestHelper helper, BlockPos start, BlockPos head) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.getBlockState(head).is(Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE)),
                "fixture lost bed head: " + level.getBlockState(head));
        helper.assertTrue(level.getPoiManager().exists(head, type -> type.is(PoiTypes.HOME)),
                "fixture bed has no HOME POI: " + level.getBlockState(head));
        BlockPos claimed = level.getPoiManager().take(type -> type.is(PoiTypes.HOME),
                (type, pos) -> pos.equals(head), head, 1)
                .orElse(null);
        helper.assertTrue(claimed != null, "fixture HOME POI was not available");
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).withName("HOME arrival probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        VILLAGERS.add(villager);
        villager.refreshBrain(level);
        villager.getBrain().setSchedule(SchedulesMCA.DEFAULT);
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), claimed));
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        villager.getBrain().setActiveActivityIfPossible(Activity.REST);
        return villager;
    }

    private static void configureNight(ServerLevel level) {
        originalDayTime = level.getOverworldClockTime();
        originalDaylightCycle = level.getGameRules().get(GameRules.ADVANCE_TIME);
        originalTeleport = Config.getInstance().allowVillagerTeleporting;
        level.getGameRules().set(GameRules.ADVANCE_TIME, false, level.getServer());
        setDayTime(level, 16000L);
        Config.getInstance().allowVillagerTeleporting = false;
    }

    private static String describe(VillagerEntityMCA villager) {
        var brain = villager.getBrain();
        var path = villager.getNavigation().getPath();
        return "pos=" + villager.position() + ", activity=" + brain.getActiveNonCoreActivity()
                + ", home=" + brain.getMemory(MemoryModuleType.HOME)
                + ", walk=" + brain.getMemory(MemoryModuleType.WALK_TARGET)
                .map(target -> target.getTarget().getClass().getSimpleName() + "@" + target.getTarget().currentBlockPosition())
                + ", path=" + (path == null ? "none" : path.getNextNodeIndex() + "/" + path.getNodeCount()
                + " reachable=" + path.canReach() + " target=" + path.getTarget())
                + ", stuck=" + villager.getNavigation().isStuck()
                + ", failure=" + brain.getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                + ", searches=" + PathRequestDiagnostics.snapshot(villager);
    }

    @AfterBatch(batch = "mca_home_flat_door")
    public static void cleanupFlat(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_home_raised_steps")
    public static void cleanupSteps(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_home_repaired_ledge")
    public static void cleanupLedge(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_home_drop")
    public static void cleanupDrop(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_home_retry_phase")
    public static void cleanupRetryPhase(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_home_bedside_corner")
    public static void cleanupBedsideCorner(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_home_bedside_guards")
    public static void cleanupBedsideGuards(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_home_small_bedside_corner")
    public static void cleanupSmallBedsideCorner(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = "mca_home_intermediate_span")
    public static void cleanupIntermediateSpan(ServerLevel level) {
        cleanup(level);
    }

    private static void cleanup(ServerLevel level) {
        VILLAGERS.forEach(villager -> {
            if (villager.isSleeping()) {
                villager.stopSleeping();
            }
            villager.releasePoi(MemoryModuleType.HOME);
            villager.discard();
        });
        VILLAGERS.clear();
        ORIGINAL_BLOCKS.forEach((pos, state) -> level.setBlock(pos, state, 3));
        ORIGINAL_BLOCKS.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        FORCED_CHUNKS.clear();
        if (originalDayTime != null) {
            setDayTime(level, originalDayTime);
            level.getGameRules().set(GameRules.ADVANCE_TIME, originalDaylightCycle, level.getServer());
            Config.getInstance().allowVillagerTeleporting = originalTeleport;
            originalDayTime = null;
        }
    }

    private static void setDayTime(ServerLevel level, long ticks) {
        var clock = level.dimensionType().defaultClock().orElseThrow();
        level.clockManager().setTotalTicks(clock, ticks);
    }
}
