package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.minecraft.core.BlockPos;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/** Guards the path-type classifier from repeating expensive terrain reads per candidate. */
@GameTestHolder("minecraft")
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
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setNoAi(true);
        boolean oldGateConfig = Config.getInstance().villagersInteractWithFenceGates;
        Config.getInstance().villagersInteractWithFenceGates = true;
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
            Config.getInstance().villagersInteractWithFenceGates = oldGateConfig;
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
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setNoAi(true);
        boolean oldAnyDoorConfig = Config.getInstance().villagersInteractWithAnyDoor;
        try {
            MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
            PathfindingContext context = new PathfindingContext(level, villager);

            Config.getInstance().villagersInteractWithAnyDoor = false;
            helper.assertTrue(PathingBlockInteraction.isOpenable(level.getBlockState(woodenDoor)),
                    "vanilla mob-interactable door should remain operable by default");
            helper.assertTrue(!PathingBlockInteraction.isOpenable(level.getBlockState(ironDoor)),
                    "untagged iron door should remain protected by default");
            helper.assertTrue(evaluator.getPathType(context, woodenDoor.getX(), woodenDoor.getY(), woodenDoor.getZ()) == PathType.WALKABLE_DOOR,
                    "operable wooden door should be pathable");
            helper.assertTrue(evaluator.getPathType(context, ironDoor.getX(), ironDoor.getY(), ironDoor.getZ()) == PathType.DOOR_IRON_CLOSED,
                    "door the villager refuses to operate must not be pathable");

            Config.getInstance().villagersInteractWithAnyDoor = true;
            helper.assertTrue(PathingBlockInteraction.isOpenable(level.getBlockState(ironDoor)),
                    "any-door config should make an iron DoorBlock operable");
            helper.assertTrue(evaluator.getPathType(context, ironDoor.getX(), ironDoor.getY(), ironDoor.getZ()) == PathType.WALKABLE_DOOR,
                    "any-door config should make the same iron DoorBlock pathable");
            helper.succeed();
        } finally {
            Config.getInstance().villagersInteractWithAnyDoor = oldAnyDoorConfig;
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
                .spawn(EntitySpawnReason.STRUCTURE);
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
                .spawn(EntitySpawnReason.STRUCTURE);
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
                .spawn(EntitySpawnReason.STRUCTURE);
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
                .spawn(EntitySpawnReason.STRUCTURE);
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
            helper.succeed();
        } finally {
            evaluator.done();
            villager.discard();
        }
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
