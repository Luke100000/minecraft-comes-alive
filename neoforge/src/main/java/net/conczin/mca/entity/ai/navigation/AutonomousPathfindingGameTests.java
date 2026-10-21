package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.gametest.GameTestBrain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.conczin.mca.MCA;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.conczin.mca.entity.ai.brain.tasks.SmarterOpenDoorsTask;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
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
 * End-to-end pathfinding contracts: real destination producer, real CORE movement
 * sink and natural entity ticks. No test creates a path, advances a node, moves
 * a mob or invokes a behavior's start method directly.
 */
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class AutonomousPathfindingGameTests {
    private static final String DETOUR_BATCH = "mca_autonomous_live_detour";
    private static final String GATE_BATCH = "mca_autonomous_closed_gate";
    private static final String ENCLOSED_GATE_BATCH = "mca_autonomous_enclosed_gate_exit";
    private static final String ENCLOSED_RAISED_GATE_BATCH = "mca_autonomous_enclosed_raised_gate_exit";
    private static final String DECORATIVE_GATE_BATCH = "mca_autonomous_decorative_raised_gate";
    private static final String WALL_GATE_BATCH = "mca_autonomous_wall_gate_approaches";
    private static final String SLOW_HEAD_GATE_BATCH = "mca_autonomous_slow_head_gate";
    private static final String SEGMENT_BATCH = "mca_autonomous_progressive_segments";
    private static final String BOUNDED_DETOUR_BATCH = "mca_autonomous_bounded_detour";
    private static final String LONG_CORRIDOR_BATCH = "mca_autonomous_long_corridor";
    private static final String FLOWING_WATER_BATCH = "mca_autonomous_flowing_water";
    private static final Set<VillagerEntityMCA> VILLAGERS = new HashSet<>();
    private static final Map<BlockPos, BlockState> ORIGINAL_BLOCKS = new LinkedHashMap<>();
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static final Set<DynamicGameEventListener<GameEventListener>> GATE_LISTENERS = new HashSet<>();
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

    @GameTest(batch = FLOWING_WATER_BATCH, templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void brainTraversesFlowingWaterInsteadOfStayingAgainstBank(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos target = start.south();
        rememberArea(level, start, 8);
        prepareFlatArea(helper, start, 8, 3);
        for (int distance = -1; distance <= 5; distance++) {
            BlockPos cell = start.south(distance);
            level.setBlock(cell.west(), Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(cell.east(), Blocks.DIRT.defaultBlockState(), 3);
            level.setBlock(cell, distance < 0 || distance > 4
                    ? Blocks.DIRT.defaultBlockState()
                    : Blocks.WATER.defaultBlockState().setValue(BlockStateProperties.LEVEL, 4 - distance), 3);
        }
        VillagerEntityMCA villager = spawnAutonomousVillager(helper, start, target, 0.55F, false);
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        @SuppressWarnings("unchecked")
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>) (Brain<?>) villager.getBrain();
        GameTestBrain.addActivity(brain, Activity.CORE, 0,
                ImmutableList.of(new net.minecraft.world.entity.ai.behavior.Swim<VillagerEntityMCA>(0.8F)));
        helper.runAfterDelay(20L, () -> {
            helper.assertTrue(villager.isInWater() && !villager.onGround()
                            && level.getBlockState(start).getValue(BlockStateProperties.LEVEL) == 4
                            && level.getFluidState(start).getFlow(level, start).z < -0.9D,
                    "The shallow LEVEL4 current and airborne bank pose must precede the movement request");
            villager.getBrain().setMemory(MemoryModuleType.HOME,
                    GlobalPos.of(level.dimension(), target));
        });
        Vec3[] previous = {villager.position()};
        helper.onEachTick(() -> {
            helper.assertTrue(villager.position().distanceTo(previous[0]) < 1.0D,
                    "The flowing-water journey must use native locomotion instead of teleporting");
            previous[0] = villager.position();
            if (helper.getTick() > 20 && villager.blockPosition().equals(target)) {
                helper.succeed();
            } else if (helper.getTick() >= 100) {
                helper.fail("Native movement could not cross one shallow-water cell against its current: pos="
                        + villager.position() + ",collision=" + villager.horizontalCollision
                        + ",path=" + villager.getNavigation().getPath());
            }
        });
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

    @GameTest(batch = ENCLOSED_GATE_BATCH, templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 650)
    public static void villagerEscapesEnclosedYardThroughOnlyFenceGate(GameTestHelper helper) {
        testEnclosedGateExit(helper, false);
    }

    @GameTest(batch = ENCLOSED_RAISED_GATE_BATCH, templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 650)
    public static void villagerEscapesEnclosedYardThroughOnlyGateOnSlab(GameTestHelper helper) {
        testEnclosedGateExit(helper, true);
    }

    private static void testEnclosedGateExit(GameTestHelper helper, boolean gateOnSlab) {
        BlockPos start = helper.absolutePos(new BlockPos(7, 1, 7));
        BlockPos target = start.east(7);
        BlockPos entrance = start.east(3);
        BlockPos gate = gateOnSlab ? entrance.above() : entrance;
        rememberArea(helper.getLevel(), start.east(2), 9);
        prepareFlatArea(helper, start.east(2), 9, 5);

        // A three-block-high perimeter with a single one-block-wide doorway.
        // No diagonal gap, alternative path or teleport can bypass the gate.
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                if (Math.abs(x) != 3 && Math.abs(z) != 3) {
                    continue;
                }
                BlockPos wall = start.offset(x, 0, z);
                for (int y = 0; y < 3; y++) {
                    if (x == 3 && z == 0 && (gateOnSlab || y > 0)) {
                        continue;
                    }
                    helper.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        if (gateOnSlab) {
            helper.getLevel().setBlock(entrance, Blocks.OAK_SLAB.defaultBlockState(), 3);
        }
        helper.getLevel().setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(FenceGateBlock.FACING, net.minecraft.core.Direction.EAST), 3);

        enableFenceGateMovementWithoutTeleporting();

        VillagerEntityMCA villager = spawnAutonomousVillager(helper, start, target, 0.7F, true);
        long started = helper.getLevel().getGameTime();
        boolean[] lastOpen = {false};
        boolean[] sawOpen = {false};
        boolean[] crossedGate = {false};
        int[] gateTransitions = {0};
        Vec3[] lastPosition = {villager.position()};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - started;
            Vec3 current = villager.position();
            helper.assertTrue(current.distanceTo(lastPosition[0]) < 2.0D,
                    "villager moved without walking through the enclosed yard");
            lastPosition[0] = current;

            boolean open = helper.getLevel().getBlockState(gate).getValue(BlockStateProperties.OPEN);
            if (open != lastOpen[0]) {
                gateTransitions[0]++;
                lastOpen[0] = open;
            }
            sawOpen[0] |= open;
            helper.assertTrue(gateTransitions[0] <= 4,
                    "gate repeatedly opened/closed without the villager exiting: "
                            + gateTransitions[0] + " transitions, pos=" + current);

            crossedGate[0] |= current.x > gate.getX() + 1.2D
                    && Math.abs(current.z - gate.getZ() - 0.5D) < 0.8D;
            if (villager.blockPosition().closerThan(target, 2.0D)) {
                helper.assertTrue(sawOpen[0] && crossedGate[0],
                        "villager bypassed the only gate instead of passing through it");
                helper.assertTrue(PathRequestDiagnostics.snapshot(villager).sinkRequests() > 0,
                        "the villager never used its Brain movement path");
                helper.succeed();
            }
            if (elapsed >= 600L) {
                helper.fail("villager never exited the enclosed yard through "
                        + (gateOnSlab ? "the raised gate on a slab" : "the normal gate")
                        + "; pos=" + current + ", gateOpened=" + sawOpen[0]
                        + ", toggles=" + gateTransitions[0]
                        + ", path=" + villager.getNavigation().getPath());
            }
        });
    }

    @GameTest(batch = DECORATIVE_GATE_BATCH, templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 850)
    public static void villagerDoesNotSpamDecorativeGateOnUpsideDownStairAndLeaves(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos wall = start.east(3);
        BlockPos target = start.east(7);
        BlockPos stair = wall.above();
        BlockPos gate = stair.above();
        rememberArea(helper.getLevel(), start.east(3), 12);
        prepareFlatArea(helper, start.east(3), 12, 5);

        // Exact relative layout: one leaf at (x,y), an upside-down stair at
        // (x+1,y+1), and a closed vanilla fence gate at (x+1,y+2).
        // The villager may cross the gate or go around the ends of the wall.
        for (int z = -4; z <= 4; z++) {
            BlockPos column = wall.offset(0, 0, z);
            helper.getLevel().setBlock(column, Blocks.ACACIA_PLANKS.defaultBlockState(), 3);
            helper.getLevel().setBlock(column.above(), Blocks.ACACIA_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.WEST)
                    .setValue(StairBlock.HALF, Half.TOP), 3);
            helper.getLevel().setBlock(column.above(2), z == 0
                    ? Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.WEST)
                    : Blocks.CHERRY_FENCE.defaultBlockState(), 3);
        }
        BlockPos leaf = stair.west().below();
        helper.getLevel().setBlock(leaf, Blocks.OAK_LEAVES.defaultBlockState(), 3);
        helper.assertTrue(stair.equals(leaf.east().above()) && gate.equals(leaf.east().above(2)),
                "fixture must have leaf (x,y), upside-down stair (x+1,y+1), gate (x+1,y+2)");

        enableFenceGateMovementWithoutTeleporting();

        VillagerEntityMCA villager = spawnAutonomousVillager(helper, start, target, 0.7F, true);
        long started = helper.getLevel().getGameTime();
        boolean[] lastOpen = {false};
        int[] gateTransitions = {0};
        boolean[] gateWasOpened = {false};
        boolean[] approachedGate = {false};
        Vec3[] lastPosition = {villager.position()};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - started;
            Vec3 position = villager.position();
            helper.assertTrue(position.distanceTo(lastPosition[0]) < 2.0D,
                    "villager teleported rather than walking to its destination");
            lastPosition[0] = position;
            boolean open = helper.getLevel().getBlockState(gate).getValue(BlockStateProperties.OPEN);
            if (open != lastOpen[0]) {
                gateTransitions[0]++;
                lastOpen[0] = open;
                Path activePath = villager.getBrain().getMemory(MemoryModuleType.PATH).orElse(null);
                MCA.LOGGER.info("[MCA Decorative Gate GameTest] gateState={} transition={} villager={} previousNode={} nextNode={}",
                        open, gateTransitions[0], position,
                        activePath == null ? null : activePath.getPreviousNode(),
                        activePath == null || activePath.isDone() ? null : activePath.getNextNode());
            }
            gateWasOpened[0] |= open;
            approachedGate[0] |= Math.abs(position.x - gate.getX() - 0.5D) < 3.5D
                    && Math.abs(position.z - gate.getZ() - 0.5D) < 2.0D;
            helper.assertTrue(gateTransitions[0] <= 4,
                    "villager repeatedly toggled decorative gate without making progress: "
                            + gateTransitions[0] + " transitions at " + position
                            + ", path=" + villager.getNavigation().getPath());
            if (villager.blockPosition().closerThan(target, 2.0D)) {
                MCA.LOGGER.info("[MCA Decorative Gate GameTest] gateOpened={} transitions={} approached={} finalPos={}",
                        gateWasOpened[0], gateTransitions[0], approachedGate[0], position);
                helper.assertTrue(gateWasOpened[0], "villager reached target without exercising raised vanilla fence gate");
                helper.assertTrue(PathRequestDiagnostics.snapshot(villager).sinkRequests() > 0,
                        "villager reached destination without Brain pathfinding");
                helper.succeed();
            } else if (elapsed >= 800L) {
                helper.fail("villager stalled by decorative gate; pos=" + position
                        + ", approachedGate=" + approachedGate[0]
                        + ", toggles=" + gateTransitions[0]
                        + ", path=" + villager.getNavigation().getPath());
            }
        });
    }

    @GameTest(batch = WALL_GATE_BATCH, templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 4_000)
    public static void raisedGateBesideSandstoneWallsDoesNotSpamAcrossApproaches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(10, 1, 10));
        BlockPos gate = base.above(2);
        rememberArea(level, base, 9);
        prepareFlatArea(helper, base, 9, 5);
        BlockState gateState = BuiltInRegistries.BLOCK.getOptional(
                Identifier.fromNamespaceAndPath("ecologics", "azalea_fence_gate"))
                .orElse(Blocks.OAK_FENCE_GATE).defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.WEST);
        MCA.LOGGER.info("[MCA Wall Gate] testing block={}", BuiltInRegistries.BLOCK.getKey(gateState.getBlock()));
        for (int z = -4; z <= 4; z++) {
            for (int x = 0; x <= 5; x++) {
                level.setBlock(base.offset(x, 0, z), Blocks.ACACIA_PLANKS.defaultBlockState(), 3);
                level.setBlock(base.offset(x, 1, z), x == 0
                        ? Blocks.ACACIA_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.WEST)
                                .setValue(StairBlock.HALF, Half.TOP)
                        : Blocks.ACACIA_PLANKS.defaultBlockState(), 3);
            }
            level.setBlock(base.west().offset(0, 0, z), Blocks.OAK_LEAVES.defaultBlockState()
                    .setValue(BlockStateProperties.PERSISTENT, true), 3);
            level.setBlock(gate.offset(0, 0, z), Math.abs(z) <= 1 ? gateState
                    : Blocks.RED_SANDSTONE_WALL.defaultBlockState(), 3);
        }
        enableFenceGateMovementWithoutTeleporting();

        int[] attempt = {0};
        int[] events = {0};
        int[] jumps = {0};
        long[] started = {0};
        boolean[] grounded = {true};
        VillagerEntityMCA[] current = {null};
        BlockPos[] destination = {base};
        double[] lateralOffsets = {-1.3D, -0.7D, 0.7D, 1.3D};
        DynamicGameEventListener<GameEventListener> listener = new DynamicGameEventListener<>(new GameEventListener() {
            public PositionSource getListenerSource() { return new BlockPositionSource(gate); }
            public int getListenerRadius() { return 16; }
            public boolean handleGameEvent(ServerLevel world, Holder<GameEvent> event, GameEvent.Context context, Vec3 source) {
                BlockPos pos = BlockPos.containing(source);
                if (current[0] != null && pos.getX() == gate.getX() && pos.getY() == gate.getY()
                        && Math.abs(pos.getZ() - gate.getZ()) <= 1
                        && (event == GameEvent.BLOCK_OPEN || event == GameEvent.BLOCK_CLOSE)) {
                    events[0]++;
                    MCA.LOGGER.info("[MCA Wall Gate] attempt={} event={} pos={} villager={} collision={} path={} states={} / {} / {}",
                            attempt[0], event.getRegisteredName(), pos, current[0].position(),
                            current[0].horizontalCollision, current[0].getNavigation().getPath(),
                            world.getBlockState(gate.north()), world.getBlockState(gate), world.getBlockState(gate.south()));
                }
                return false;
            }
        });
        listener.add(level);
        GATE_LISTENERS.add(listener);
        helper.onEachTick(() -> {
            if (current[0] == null) {
                if (attempt[0] == 8) {
                    helper.succeed();
                    return;
                }
                for (int z = -1; z <= 1; z++) {
                    level.setBlock(gate.offset(0, 0, z), gateState
                            .setValue(FenceGateBlock.IN_WALL, Math.abs(z) == 1), 3);
                }
                boolean fromPorch = attempt[0] >= 4;
                BlockPos start = fromPorch ? base.east(3).above(2) : base.west(3);
                destination[0] = fromPorch ? base.west(4) : base.east(4).above(2);
                events[0] = 0;
                jumps[0] = 0;
                grounded[0] = true;
                started[0] = level.getGameTime();
                current[0] = spawnAutonomousVillager(helper, start, destination[0], 0.7F, true);
                // Set the initial fixture position once, then leave all movement to the Brain.
                current[0].setPos(Vec3.atBottomCenterOf(start).add(0, 0, lateralOffsets[attempt[0] % 4]));
                return;
            }
            VillagerEntityMCA villager = current[0];
            if (grounded[0] && !villager.onGround() && villager.getDeltaMovement().y > 0) {
                jumps[0]++;
            }
            grounded[0] = villager.onGround();
            helper.assertTrue(events[0] <= 6, "repeated gate opening/closing; attempt=" + attempt[0]
                    + ", events=" + events[0] + ", jumps=" + jumps[0] + ", pos=" + villager.position());
            if (villager.position().distanceToSqr(Vec3.atBottomCenterOf(destination[0])) < 2.25D) {
                MCA.LOGGER.info("[MCA Wall Gate] reached attempt={} events={} jumps={} pos={}",
                        attempt[0], events[0], jumps[0], villager.position());
                villager.discard();
                VILLAGERS.remove(villager);
                current[0] = null;
                attempt[0]++;
            } else if (level.getGameTime() - started[0] >= 450) {
                helper.fail("wall/gate approach stalled; attempt=" + attempt[0] + ", events=" + events[0]
                        + ", jumps=" + jumps[0] + ", collision=" + villager.horizontalCollision
                        + ", pos=" + villager.position() + ", path=" + villager.getNavigation().getPath());
            }
        });
    }

    @GameTest(batch = SLOW_HEAD_GATE_BATCH, templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 1_200)
    public static void slowedVillagerKeepsHeadHeightGateOpenUntilItPasses(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(13, 1, 13));
        BlockPos feetAtGate = start.east(4);
        BlockPos gate = feetAtGate.above();
        BlockPos destination = start.east(9);
        rememberArea(level, start.east(4), 8);
        prepareFlatArea(helper, start.east(4), 8, 4);
        for (int z = -8; z <= 8; z++) {
            for (int y = 0; y < 3; y++) {
                BlockPos pos = feetAtGate.offset(0, y, z);
                if (z == 0 && y == 0) {
                    continue;
                }
                level.setBlock(pos, z == 0 && y == 1
                        ? Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.FACING, Direction.EAST)
                        : Blocks.STONE.defaultBlockState(), 3);
            }
        }
        level.setBlock(feetAtGate.west().below(), Blocks.HONEY_BLOCK.defaultBlockState(), 3);
        level.setBlock(feetAtGate.west(), Blocks.COBWEB.defaultBlockState(), 3);
        // Keep the web's path cost from sending the villager around the gate.
        // This is an ordinary walkable corridor, not an injected or held path.
        for (int x = -1; x <= 10; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x != -1 && x != 10 && z == 0) {
                    continue;
                }
                for (int y = 0; y < 3; y++) {
                    level.setBlock(start.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        enableFenceGateMovementWithoutTeleporting();
        VillagerEntityMCA villager = spawnAutonomousVillager(helper, start, destination, 0.7F, true);
        villager.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 1_200, 3));
        int[] opens = {0};
        int[] closesBeforeCrossing = {0};
        int[] headNodeTicks = {0};
        boolean[] passed = {false};
        boolean[] loggedPath = {false};
        DynamicGameEventListener<GameEventListener> listener = new DynamicGameEventListener<>(new GameEventListener() {
            public PositionSource getListenerSource() { return new BlockPositionSource(gate); }
            public int getListenerRadius() { return 16; }
            public boolean handleGameEvent(ServerLevel world, Holder<GameEvent> event, GameEvent.Context context, Vec3 source) {
                if (BlockPos.containing(source).equals(gate)) {
                    if (event == GameEvent.BLOCK_OPEN) {
                        opens[0]++;
                    } else if (event == GameEvent.BLOCK_CLOSE && villager.getX() < gate.getX()) {
                        closesBeforeCrossing[0]++;
                    }
                    if (event == GameEvent.BLOCK_OPEN || event == GameEvent.BLOCK_CLOSE) {
                        Path path = villager.getNavigation().getPath();
                        MCA.LOGGER.info("[MCA Slow Head Gate] event={} pos={} opens={} prematureCloses={} next={}",
                                event.getRegisteredName(), villager.position(), opens[0], closesBeforeCrossing[0],
                                path == null || path.isDone() ? null : path.getNextNode());
                    }
                }
                return false;
            }
        });
        listener.add(level);
        GATE_LISTENERS.add(listener);
        helper.onEachTick(() -> {
            Path path = villager.getNavigation().getPath();
            if (path != null && !loggedPath[0]) {
                loggedPath[0] = true;
                MCA.LOGGER.info("[MCA Slow Head Gate] natural path={}",
                        java.util.stream.IntStream.range(0, path.getNodeCount())
                                .mapToObj(i -> path.getNode(i).toString()).toList());
            }
            if (path != null && !path.isDone() && path.getNextNode().asBlockPos().equals(feetAtGate)) {
                headNodeTicks[0]++;
            }
            passed[0] |= villager.getX() > gate.getX() + 1.3D;
            helper.assertTrue(closesBeforeCrossing[0] == 0,
                    "head-height gate closed before the approaching villager reached it; opens=" + opens[0]
                            + ", closes=" + closesBeforeCrossing[0] + ", headNodeTicks=" + headNodeTicks[0]
                            + ", pos=" + villager.position());
            if (villager.position().distanceToSqr(Vec3.atBottomCenterOf(destination)) < 2.25D) {
                helper.assertTrue(passed[0] && opens[0] > 0 && headNodeTicks[0] > 0,
                        "fixture did not exercise an autonomous path beneath the gate; opens=" + opens[0]
                                + ", passed=" + passed[0] + ", headNodeTicks=" + headNodeTicks[0]);
                MCA.LOGGER.info("[MCA Slow Head Gate] arrived opens={} prematureCloses={} headNodeTicks={}",
                        opens[0], closesBeforeCrossing[0], headNodeTicks[0]);
                helper.succeed();
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
        followRange.removeModifier(Identifier.withDefaultNamespace("random_spawn_bonus"));
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
                .spawn(EntitySpawnReason.STRUCTURE);
        VILLAGERS.add(villager);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(24.0D);

        @SuppressWarnings("unchecked")
        Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>) (Brain<?>) villager.getBrain();
        brain.stopAll(helper.getLevel(), villager);
        brain.removeAllBehaviors();
        if (doors) {
            GameTestBrain.addActivity(brain, Activity.CORE, 0, ImmutableList.of(new SmarterOpenDoorsTask()));
        }
        GameTestBrain.addActivity(brain, Activity.CORE, 1, ImmutableList.of(new WanderOrTeleportToTargetTask()));
        GameTestBrain.addActivity(brain, Activity.CORE, 2, ImmutableList.of(
                ExtendedWalkTowardsTask.createWithoutPoiRelease(
                        MemoryModuleType.HOME, speed, 0, 1_400, ignored -> false, ignored -> { })));
        GameTestBrain.addActivity(brain, Activity.IDLE, 0, ImmutableList.of(new DoNothing(100, 100)));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
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
        ChunkPos min = ChunkPos.containing(start);
        ChunkPos max = ChunkPos.containing(end);
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
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

    @AfterBatch(batch = ENCLOSED_GATE_BATCH)
    public static void cleanupEnclosedGate(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = ENCLOSED_RAISED_GATE_BATCH)
    public static void cleanupEnclosedRaisedGate(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = DECORATIVE_GATE_BATCH)
    public static void cleanupDecorativeGate(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = WALL_GATE_BATCH)
    public static void cleanupWallGate(ServerLevel level) {
        cleanup(level);
    }

    @AfterBatch(batch = SLOW_HEAD_GATE_BATCH)
    public static void cleanupSlowHeadGate(ServerLevel level) {
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

    @AfterBatch(batch = FLOWING_WATER_BATCH)
    public static void cleanupFlowingWater(ServerLevel level) {
        cleanup(level);
    }

    private static void enableFenceGateMovementWithoutTeleporting() {
        Config config = Config.getInstance();
        if (originalGateConfig == null) {
            originalGateConfig = config.villagersInteractWithFenceGates;
        }
        if (originalTeleportConfig == null) {
            originalTeleportConfig = config.allowVillagerTeleporting;
        }
        config.villagersInteractWithFenceGates = true;
        config.allowVillagerTeleporting = false;
    }

    private static void cleanup(ServerLevel level) {
        GATE_LISTENERS.forEach(listener -> listener.remove(level));
        GATE_LISTENERS.clear();
        VILLAGERS.forEach(VillagerEntityMCA::discard);
        VILLAGERS.clear();
        ORIGINAL_BLOCKS.forEach((pos, state) -> level.setBlock(pos, state, 3));
        ORIGINAL_BLOCKS.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
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
