package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/** Guards the path-type classifier from repeating expensive terrain reads per candidate. */
@PrefixGameTestTemplate(false)
public final class MCAWalkNodeEvaluatorLookupGameTests {
    private MCAWalkNodeEvaluatorLookupGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void readsEachCandidateStateOnceAndKeepsSpecialTraversal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos air = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos gate = air.east(2);
        BlockPos ladder = air.east(4);
        level.setBlock(air, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(air.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);
        level.setBlock(ladder, Blocks.LADDER.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withPosition(Vec3.atBottomCenterOf(air.west(2)))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        boolean oldGateConfig = Config.SERVER.villagersInteractWithFenceGates.get();
        Config.SERVER.villagersInteractWithFenceGates.set(true);
        try {
            CountingContext context = new CountingContext(level, villager);
            MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();

            PathType airType = evaluator.getPathType(context, air.getX(), air.getY(), air.getZ());
            helper.assertTrue(airType == PathType.WALKABLE, "air above stone should be walkable");
            helper.assertTrue(context.lookups == 1,
                    "ordinary candidate required " + context.lookups + " direct state reads instead of one");

            context.lookups = 0;
            PathType gateType = evaluator.getPathType(context, gate.getX(), gate.getY(), gate.getZ());
            helper.assertTrue(gateType == PathType.WALKABLE_DOOR, "hand-openable gate lost door traversal");
            helper.assertTrue(context.lookups == 1,
                    "gate candidate required " + context.lookups + " state reads instead of one");

            context.lookups = 0;
            PathType ladderType = evaluator.getPathType(context, ladder.getX(), ladder.getY(), ladder.getZ());
            helper.assertTrue(ladderType == PathType.WALKABLE, "climbable lost ladder traversal");
            helper.assertTrue(context.lookups == 1, "ladder should require one state read");
            helper.succeed();
        } finally {
            Config.SERVER.villagersInteractWithFenceGates.set(oldGateConfig);
            villager.discard();
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void doorPathingUsesSamePolicyAsDoorInteraction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos woodenDoor = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos ironDoor = woodenDoor.east(2);
        level.setBlock(woodenDoor, Blocks.OAK_DOOR.defaultBlockState(), 3);
        level.setBlock(ironDoor, Blocks.IRON_DOOR.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withPosition(Vec3.atBottomCenterOf(woodenDoor.west(2)))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        boolean oldAnyDoorConfig = Config.SERVER.villagersInteractWithAnyDoor.get();
        try {
            MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
            PathfindingContext context = new PathfindingContext(level, villager);

            Config.SERVER.villagersInteractWithAnyDoor.set(false);
            helper.assertTrue(PathingBlockInteraction.isOpenable(level.getBlockState(woodenDoor)),
                    "vanilla mob-interactable door should remain operable by default");
            helper.assertTrue(!PathingBlockInteraction.isOpenable(level.getBlockState(ironDoor)),
                    "untagged iron door should remain protected by default");
            helper.assertTrue(evaluator.getPathType(context, woodenDoor.getX(), woodenDoor.getY(), woodenDoor.getZ()) == PathType.WALKABLE_DOOR,
                    "operable wooden door should be pathable");
            helper.assertTrue(evaluator.getPathType(context, ironDoor.getX(), ironDoor.getY(), ironDoor.getZ()) == PathType.DOOR_IRON_CLOSED,
                    "door the villager refuses to operate must not be pathable");

            Config.SERVER.villagersInteractWithAnyDoor.set(true);
            helper.assertTrue(PathingBlockInteraction.isOpenable(level.getBlockState(ironDoor)),
                    "any-door config should make an iron DoorBlock operable");
            helper.assertTrue(evaluator.getPathType(context, ironDoor.getX(), ironDoor.getY(), ironDoor.getZ()) == PathType.WALKABLE_DOOR,
                    "any-door config should make the same iron DoorBlock pathable");
            helper.succeed();
        } finally {
            Config.SERVER.villagersInteractWithAnyDoor.set(oldAnyDoorConfig);
            villager.discard();
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void stackedScaffoldingKeepsVanillaPassThroughPathType(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos upper = lower.above();
        level.setBlock(lower.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(lower, Blocks.SCAFFOLDING.defaultBlockState(), 3);
        level.setBlock(upper, Blocks.SCAFFOLDING.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withPosition(Vec3.atBottomCenterOf(lower.west()))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        try {
            MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
            PathfindingContext context = new PathfindingContext(level, villager);
            PathType vanillaType = WalkNodeEvaluator.getPathTypeStatic(villager, upper);
            PathType mcaType = evaluator.getPathType(context, upper.getX(), upper.getY(), upper.getZ());

            helper.assertTrue(vanillaType == PathType.OPEN,
                    "fixture no longer represents pass-through stacked scaffolding: " + vanillaType);
            helper.assertTrue(mcaType == vanillaType,
                    "MCA turned pass-through scaffolding into a ground/climb surface: " + mcaType);
            helper.succeed();
        } finally {
            villager.discard();
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void stackedScaffoldingStillAddsVerticalClimbEdge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos lower = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos upper = lower.above();
        level.setBlock(lower.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(lower, Blocks.SCAFFOLDING.defaultBlockState(), 3);
        level.setBlock(upper, Blocks.SCAFFOLDING.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withPosition(Vec3.atBottomCenterOf(lower))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
        try {
            evaluator.prepare(new PathNavigationRegion(level, lower.offset(-4, -3, -4),
                    upper.offset(4, 4, 4)), villager);
            Node start = evaluator.getStart();
            Node[] neighbors = new Node[32];
            int neighborCount = evaluator.getNeighbors(neighbors, start);

            Node climb = null;
            for (int i = 0; i < neighborCount; i++) {
                if (neighbors[i].asBlockPos().equals(upper)) {
                    climb = neighbors[i];
                    break;
                }
            }
            helper.assertTrue(climb != null,
                    "vanilla-pass-through scaffolding lost its explicit vertical climb edge");
            helper.assertTrue(climb.type == PathType.WALKABLE && climb.costMalus >= 0.0F,
                    "vertical scaffolding edge was not promoted to a usable climb node: " + climb);
            helper.succeed();
        } finally {
            evaluator.done();
            villager.discard();
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void skipsBarrierFloorChecksOnOrdinaryGround(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        prepareFlatArea(helper, origin, 4, 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withPosition(Vec3.atBottomCenterOf(origin))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);
        FloorCountingEvaluator evaluator = new FloorCountingEvaluator();
        try {
            evaluator.prepare(new PathNavigationRegion(level, origin.offset(-4, -3, -4),
                    origin.offset(4, 3, 4)), villager);
            Node start = evaluator.getStart();
            evaluator.floorReads = 0;
            int neighbors = evaluator.getNeighbors(new Node[32], start);
            helper.assertTrue(neighbors >= 4, "flat-ground fixture did not expand ordinary neighbors");
            helper.assertTrue(evaluator.floorReads <= 20,
                    "flat-ground expansion unnecessarily calculated barrier floor levels "
                            + evaluator.floorReads + " times");
            helper.succeed();
        } finally {
            evaluator.done();
            villager.discard();
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void descendingClimbableProbeReadsEachGroundCandidateOnce(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 1, 8));
        prepareFlatArea(helper, origin, 4, 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withPosition(Vec3.atBottomCenterOf(origin))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);
        DescendingCountingEvaluator evaluator = new DescendingCountingEvaluator();
        try {
            evaluator.prepare(new PathNavigationRegion(level, origin.offset(-4, -3, -4),
                    origin.offset(4, 3, 4)), villager);
            Node start = evaluator.getStart();
            BlockPos startPos = start.asBlockPos();
            helper.assertTrue(level.getBlockState(startPos.below().east()).is(Blocks.STONE),
                    "flat-ground fixture must have solid ground beneath neighboring steps");

            CountingContext context = new CountingContext(level, villager);
            evaluator.installContext(context);
            int extra = evaluator.addDescendingClimbableEntries(new Node[32], 0, startPos);
            helper.assertTrue(extra == 0, "ordinary flat terrain should not offer a descending climbable");
            helper.assertTrue(context.lookups == 4,
                    "four ordinary grounded neighbors should require exactly four state reads, not "
                            + context.lookups);
            int repeated = evaluator.addDescendingClimbableEntries(new Node[32], 0, startPos);
            helper.assertTrue(repeated == 0, "repeated flat-terrain probe added a descending climbable");
            helper.assertTrue(context.lookups == 4,
                    "repeated probes should reuse non-climbable terrain classifications, not reread "
                            + context.lookups + " block states");
            helper.succeed();
        } finally {
            evaluator.done();
            villager.discard();
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void descendingClimbableProbePreservesTwoBlockDrop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 3, 8));
        prepareFlatArea(helper, origin, 4, 3);
        BlockPos lower = origin.east().below(2);
        level.setBlock(lower.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(lower, Blocks.SCAFFOLDING.defaultBlockState(), 3);
        level.setBlock(lower.above(), Blocks.AIR.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withPosition(Vec3.atBottomCenterOf(origin))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        DescendingCountingEvaluator evaluator = new DescendingCountingEvaluator();
        try {
            evaluator.prepare(new PathNavigationRegion(level, origin.offset(-4, -4, -4),
                    origin.offset(4, 3, 4)), villager);
            helper.assertTrue(level.getBlockState(lower).is(Blocks.SCAFFOLDING),
                    "two-block drop fixture lost its climbable support");
            Node[] nodes = new Node[32];
            int count = evaluator.addDescendingClimbableEntries(nodes, 0, origin);
            helper.assertTrue(count == 1 && nodes[0].asBlockPos().equals(lower),
                    "descending probe did not preserve the reachable lower climbable");
            Node[] repeated = new Node[32];
            int repeatedCount = evaluator.addDescendingClimbableEntries(repeated, 0, origin);
            helper.assertTrue(repeatedCount == 1 && repeated[0] == nodes[0],
                    "cached descending probe returned a different climb node");
            evaluator.done();

            level.setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 3);
            evaluator.prepare(new PathNavigationRegion(level, origin.offset(-4, -4, -4),
                    origin.offset(4, 3, 4)), villager);
            helper.assertTrue(!transitionNeighbors(evaluator, evaluator.getStart()).contains(lower),
                    "a lower climbable was reachable through a solid departure-height obstruction");
            helper.succeed();
        } finally {
            evaluator.done();
            villager.discard();
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void openTrapdoorEdgeChecksReuseStateOnlyWithinTheSameSearch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 2, 8));
        level.setBlock(pos, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true), 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withPosition(Vec3.atBottomCenterOf(pos.west()))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);

        DescendingCountingEvaluator evaluator = new DescendingCountingEvaluator();
        try {
            evaluator.prepare(new PathNavigationRegion(level, pos.offset(-3, -3, -3),
                    pos.offset(3, 3, 3)), villager);
            CountingContext context = new CountingContext(level, villager);
            evaluator.installContext(context);
            helper.assertTrue(evaluator.isOpenTrapdoor(pos.getX(), pos.getY(), pos.getZ()),
                    "fixture must have an open trapdoor");
            helper.assertTrue(evaluator.isOpenTrapdoor(pos.getX(), pos.getY(), pos.getZ()),
                    "the repeated probe changed its result");
            helper.assertTrue(context.lookups == 1,
                    "repeated trapdoor edge probes fetched the same state " + context.lookups + " times");

            evaluator.done();
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            evaluator.prepare(new PathNavigationRegion(level, pos.offset(-3, -3, -3),
                    pos.offset(3, 3, 3)), villager);
            evaluator.installContext(context);
            helper.assertTrue(!evaluator.isOpenTrapdoor(pos.getX(), pos.getY(), pos.getZ()),
                    "trapdoor state was stale after the previous search finished");
            helper.assertTrue(context.lookups == 2,
                    "the next search did not recheck changed terrain");
            helper.succeed();
        } finally {
            evaluator.done();
            villager.discard();
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void wetBodyContactCannotMoveGroundStartIntoSolidSupport(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos feet = helper.absolutePos(new BlockPos(8, 1, 8));
        prepareTransitionFloor(level, feet);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level).withAge(0)
                .withPosition(new Vec3(feet.getX() + 0.251D, feet.getY(), feet.getZ() + 0.250D))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);
        MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
        try {
            evaluator.setCanFloat(true);
            villager.baseTick();
            double inset = villager.getBbWidth() / 2.0D - 0.05D;
            villager.setPos(feet.getX() + inset, feet.getY(), feet.getZ() + inset);
            evaluator.prepare(new PathNavigationRegion(level, feet.offset(-5, -3, -5), feet.offset(5, 5, 5)), villager);
            helper.assertTrue(evaluator.getStart().y == feet.getY(), "dry-ground start began inside its support");
            evaluator.done();

            level.setBlock(feet.north().west(), Blocks.WATER.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 7), 3);
            villager.baseTick();
            helper.assertTrue(villager.isInWater() && level.getBlockState(villager.blockPosition()).isAir()
                            && villager.onGround()
                            && !level.noBlockCollision(villager, villager.getBoundingBox().move(0, -0.05D, 0)),
                    "thin-water and dry supported-feet fixture is invalid");
            evaluator.prepare(new PathNavigationRegion(level, feet.offset(-5, -3, -5), feet.offset(5, 5, 5)), villager);
            helper.assertTrue(evaluator.getStart().y == feet.getY(),
                    "water contacting the body lowered the start into solid support: " + evaluator.getStart());
            helper.succeed();
        } finally {
            evaluator.done();
            villager.discard();
        }
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void flowingWaterCannotPublishUnreachableRaisedGroundJump(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos water = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos bank = water.east().above();
        prepareTransitionFloor(level, water);
        level.setBlock(water, Blocks.WATER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LiquidBlock.LEVEL, 3), 3);
        level.setBlock(water.east(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level).withAge(0)
                .withPosition(new Vec3(water.getX() + 0.715D, water.getY() + 0.18D, water.getZ() + 0.285D))
                .spawn(MobSpawnType.STRUCTURE);
        MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
        Node origin = new Node(water.getX(), water.getY(), water.getZ());
        origin.type = PathType.WATER;
        try {
            evaluator.setCanFloat(true);
            evaluator.prepare(new PathNavigationRegion(level, water.offset(-5, -3, -5), water.offset(5, 5, 5)), villager);
            helper.assertTrue(!transitionNeighbors(evaluator, origin).contains(bank),
                    "flowing water offered a jump higher than the mob's grounded step");
            evaluator.done();

            level.setBlock(water.east(), Blocks.OAK_SLAB.defaultBlockState(), 3);
            evaluator.prepare(new PathNavigationRegion(level, water.offset(-5, -3, -5), water.offset(5, 5, 5)), villager);
            helper.assertTrue(transitionNeighbors(evaluator, origin).contains(bank),
                    "a traversable slab exit from water was lost");
            evaluator.done();

            level.setBlock(water, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(water.east(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            origin.type = PathType.WALKABLE;
            evaluator.prepare(new PathNavigationRegion(level, water.offset(-5, -3, -5), water.offset(5, 5, 5)), villager);
            helper.assertTrue(transitionNeighbors(evaluator, origin).contains(bank),
                    "ordinary dry-ground jump was lost");
            helper.succeed();
        } finally {
            evaluator.done();
            villager.discard();
        }
    }

    @GameTest(batch = "mca_shallow_water_ground_jump", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 160)
    public static void supportedShallowFlowingWaterAllowsPhysicalBankExit(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos water = helper.absolutePos(new BlockPos(12, 1, 12));
        BlockPos bank = water.east().above();
        prepareFlatArea(helper, water, 10, 4);
        for (int x = -8; x <= 3; x++) {
            for (int y = 0; y < 3; y++) {
                level.setBlock(water.offset(x, y, -1), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(water.offset(x, y, 1), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int x = 1; x <= 3; x++) {
            level.setBlock(water.east(x), Blocks.STONE.defaultBlockState(), 3);
        }
        // A source seven cells upstream keeps the departure shallow throughout the real traversal.
        for (int x = -7; x <= 0; x++) {
            level.setBlock(water.east(x), Blocks.WATER.defaultBlockState()
                    .setValue(LiquidBlock.LEVEL, x + 7), 3);
        }
        VillagerEntityMCA villager = VillagerFactory.newVillager(level).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(water)).spawn(MobSpawnType.STRUCTURE);
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);
        villager.baseTick();
        MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
        try {
            helper.assertTrue(villager.isInWater() && villager.onGround()
                            && level.getFluidState(water).getHeight(level, water) <= villager.getFluidJumpThreshold(),
                    "shallow-water fixture must allow a grounded jump");
            evaluator.setCanFloat(true);
            evaluator.prepare(new PathNavigationRegion(level, water.offset(-10, -2, -3),
                    water.offset(5, 5, 3)), villager);
            helper.assertTrue(transitionNeighbors(evaluator, evaluator.getStart()).contains(bank),
                    "supported shallow water lost a physically usable ground-jump exit");
        } catch (RuntimeException | Error failure) {
            villager.discard();
            throw failure;
        } finally {
            evaluator.done();
        }
        villager.getNavigation().setCanFloat(true);
        Path path = villager.getNavigation().createPath(bank, 0);
        helper.assertTrue(path != null && path.canReach() && villager.getNavigation().moveTo(path, 0.8D),
                "the shallow-water bank must have a usable movement route");
        helper.onEachTick(() -> {
            if (villager.onGround() && villager.getY() >= bank.getY() - 0.05D
                    && villager.distanceToSqr(Vec3.atBottomCenterOf(bank)) < 0.4D) {
                villager.discard();
                helper.succeed();
            }
        });
    }

    private static void prepareTransitionFloor(ServerLevel level, BlockPos origin) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                BlockPos feet = origin.offset(x, 0, z);
                level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y <= 3; y++) {
                    level.setBlock(feet.above(y), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    private static List<BlockPos> transitionNeighbors(MCAWalkNodeEvaluator evaluator, Node origin) {
        Node[] nodes = new Node[32];
        int count = evaluator.getNeighbors(nodes, origin);
        List<BlockPos> positions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            positions.add(nodes[index].asBlockPos());
        }
        return positions;
    }

    private static final class DescendingCountingEvaluator extends MCAWalkNodeEvaluator {
        void installContext(PathfindingContext context) {
            this.currentContext = context;
        }
    }

    private static final class FloorCountingEvaluator extends MCAWalkNodeEvaluator {
        private int floorReads;

        @Override
        protected double getFloorLevel(BlockPos pos) {
            floorReads++;
            return super.getFloorLevel(pos);
        }
    }

    private static final class CountingContext extends PathfindingContext {
        private int lookups;

        CountingContext(ServerLevel level, Mob mob) {
            super(level, mob);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            lookups++;
            return super.getBlockState(pos);
        }
    }
}
