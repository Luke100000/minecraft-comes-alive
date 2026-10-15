package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.gametest.GameTestTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/** Physical-navigation regressions for ordinary MCA villagers, without Builder-specific behavior. */
@PrefixGameTestTemplate(false)
public final class NavigationPhysicalSafetyGameTests {
    private NavigationPhysicalSafetyGameTests() {
    }

    @GameTest(batch = "mca_partial_floor_start", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void bedUnderLowCeilingKeepsPhysicalDeparture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos bed = helper.absolutePos(new BlockPos(8, 2, 8));
        GameTestTerrain.prepareFlatArea(helper, bed, 4, 4);
        for (int x = -3; x <= 3; x++) {
            for (int z = -2; z <= 2; z++) {
                level.setBlock(bed.offset(x, 2, z), Blocks.SPRUCE_SLAB.defaultBlockState()
                        .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 3);
            }
        }
        var bedState = Blocks.BLUE_BED.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        level.setBlock(bed, bedState, 3);
        level.setBlock(bed.south(), bedState.setValue(BlockStateProperties.BED_PART,
                BedPart.HEAD), 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withGender(Gender.FEMALE).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(bed).add(0, 0.5625D, 0)).spawn(MobSpawnType.STRUCTURE);
        villager.getTraits().removeTrait(Traits.DWARFISM);
        villager.getTraits().removeTrait(Traits.TOUGH);
        villager.getTraits().removeTrait(Traits.WEAK);
        villager.getGenetics().setGene(Genetics.SIZE, 0.5F);
        villager.getGenetics().setGene(Genetics.WIDTH, 0.5F);
        villager.refreshDimensions();
        villager.refreshBrain(level);
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(false);
        villager.setOnGround(true);
        helper.assertTrue(level.isPositionEntityTicking(villager.blockPosition()),
                "The bed departure must run in an entity-ticking fixture chunk");
        helper.assertTrue(level.noCollision(villager), "The supported villager must fit below the real slab ceiling");
        helper.assertTrue(!level.noBlockCollision(villager, villager.getBoundingBox().move(0, -0.01D, 0)),
                "The departure must have actual bed contact");
        Path path = villager.getNavigation().createPath(bed.west(2), 0);
        helper.assertTrue(path != null && path.canReach(),
                "A supported bed pose under a clear ceiling must retain its native departure: "
                        + (path == null ? "none" : path.getNodePos(0) + "/" + path.getNode(0).type));
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.5D), "The native bed departure must start");
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getX() < bed.getX() - 0.5D && villager.getY() < bed.getY() + 0.5625D,
                    "The villager must physically step off the bed under the ceiling");
            villager.discard();
        });
    }

    @GameTest(batch = "mca_snow_step", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void layeredSnowStepUsesItsPhysicalSurface(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        GameTestTerrain.prepareFlatArea(helper, start, 4, 4);
        for (int z = -3; z <= 2; z++) {
            for (int y = 0; y <= 3; y++) {
                level.setBlock(start.offset(-1, y, z), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        level.setBlock(start, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 5), 3);
        level.setBlock(start.north(), Blocks.SNOW_BLOCK.defaultBlockState(), 3);
        level.setBlock(start.north().above(), Blocks.SNOW.defaultBlockState()
                .setValue(BlockStateProperties.LAYERS, 4), 3);
        level.setBlock(start.north(2), Blocks.SNOW_BLOCK.defaultBlockState(), 3);
        VillagerEntityMCA villager = spawn(level, Vec3.atBottomCenterOf(start).add(0, 0.5D, 0));
        villager.refreshBrain(level);
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(false);
        villager.setOnGround(true);
        helper.assertTrue(level.isPositionEntityTicking(start) && level.noCollision(villager),
                "The snow step must start supported and collision-free in a ticking chunk");
        Path path = villager.getNavigation().createPath(start.north(2).above(), 0);
        helper.assertTrue(path != null && path.canReach(), "Native search must admit the layered-snow step");
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.5D), "Native snow traversal must start");
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getZ() < start.getZ() - 0.5D && villager.getY() > start.getY() + 1.0D,
                    "The villager must physically climb the snow surface: pos=" + villager.position()
                            + ", wantedY=" + villager.getMoveControl().getWantedY());
            villager.discard();
        });
    }

    @GameTest(batch = "mca_stair_start", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void lowerStairSurfaceKeepsSupportedNativeStart(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos stair = helper.absolutePos(new BlockPos(8, 4, 8));
        GameTestTerrain.prepareFlatArea(helper, stair.below(2), 4, 4);
        for (int step = 0; step < 3; step++) {
            level.setBlock(stair.west(step).below(step), Blocks.SPRUCE_STAIRS.defaultBlockState()
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 3);
        }
        VillagerEntityMCA villager = spawn(level,
                new Vec3(stair.getX(), stair.getY() + 0.5D, stair.getZ() + 0.5D));
        villager.refreshBrain(level);
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(false);
        villager.setYRot(90.0F);
        helper.assertTrue(level.noCollision(villager), "The worker must stand on the real lower stair surface");
        var vanilla = new net.minecraft.world.level.pathfinder.WalkNodeEvaluator();
        try {
            vanilla.prepare(new PathNavigationRegion(level, stair.offset(-6, -4, -6), stair.offset(6, 6, 6)), villager);
            helper.assertTrue(vanilla.getStart().asBlockPos().equals(stair.above()),
                    "The fixture must offer vanilla's supported start above the stair");
        } finally {
            vanilla.done();
        }
        Path path = villager.getNavigation().createPath(stair.west(3).below(2), 0);
        helper.assertTrue(path != null && path.canReach(), "The stair flight must have a real native exit route");
        helper.assertTrue(path.getNodePos(0).equals(stair.above()) && path.getNode(0).type != PathType.BLOCKED,
                "MCA replaced a supported native start with a node inside the stair: "
                        + path.getNodePos(0) + '/' + path.getNode(0).type);
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.5D), "Native stair departure must start");
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.getX() < stair.getX() - 1.0D && villager.getY() < stair.getY(),
                    "The native worker must descend from the stair flight: " + villager.position());
            villager.discard();
        });
    }

    @GameTest(batch = "mca_trapdoor_diagonal", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void openTrapdoorsRejectBlockedDiagonalButKeepClearDiagonal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos northwest = origin.north().west();
        prepareGround(level, origin);
        VillagerEntityMCA villager = spawn(level, new Vec3(origin.getX() + 0.3D, origin.getY(), origin.getZ() + 0.3D));
        try {
            helper.assertTrue(neighbors(level, villager, origin).contains(northwest),
                    "An unobstructed diagonal must be offered");

            level.setBlock(origin.north(), Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                    .setValue(TrapDoorBlock.OPEN, true)
                    .setValue(TrapDoorBlock.FACING, Direction.SOUTH), 3);
            helper.assertTrue(neighbors(level, villager, origin).contains(northwest),
                    "A far-edge trapdoor panel should not block a clear diagonal");

            surroundWithOpenTrapdoors(level, origin);
            helper.assertTrue(level.noBlockCollision(villager, villager.getBoundingBox()),
                    "Villager must start outside the upright trapdoor panels");
            helper.assertTrue(sweepCollides(level, villager, Vec3.atBottomCenterOf(northwest)),
                    "Fixture must physically block the diagonal sweep");
            helper.assertTrue(!neighbors(level, villager, origin).contains(northwest),
                    "MCA offered a diagonal that the villager cannot physically traverse through trapdoors");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_trapdoor_cardinal", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void openTrapdoorsRejectBlockedCardinalButKeepClearCardinal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos west = origin.west();
        prepareGround(level, origin);
        VillagerEntityMCA villager = spawn(level, new Vec3(origin.getX() + 0.3D, origin.getY(), origin.getZ() + 0.3D));
        try {
            helper.assertTrue(neighbors(level, villager, origin).contains(west),
                    "An unobstructed cardinal step must be offered");
            level.setBlock(west, Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                    .setValue(TrapDoorBlock.OPEN, true)
                    .setValue(TrapDoorBlock.FACING, Direction.EAST), 3);
            helper.assertTrue(neighbors(level, villager, origin).contains(west),
                    "A far-edge upright trapdoor should not block a clear cardinal step");

            surroundWithOpenTrapdoors(level, origin);
            helper.assertTrue(level.noBlockCollision(villager, villager.getBoundingBox()),
                    "Villager must start outside the upright trapdoor panels");
            helper.assertTrue(sweepCollides(level, villager, Vec3.atBottomCenterOf(west)),
                    "Fixture must physically block the cardinal sweep");
            helper.assertTrue(!neighbors(level, villager, origin).contains(west),
                    "MCA offered a cardinal step through upright trapdoor collision geometry");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_fence_diagonal", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void fenceTopCornerRejectsUnsupportedDiagonal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 4, 8));
        BlockPos corner = origin.south();
        BlockPos diagonal = corner.east();
        prepareGround(level, origin.below());
        int exactShape = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        level.setBlock(origin.below(), Blocks.ACACIA_FENCE.defaultBlockState()
                .setValue(FenceBlock.SOUTH, true), exactShape);
        level.setBlock(corner.below(), Blocks.ACACIA_FENCE.defaultBlockState()
                .setValue(FenceBlock.NORTH, true).setValue(FenceBlock.EAST, true), exactShape);
        level.setBlock(diagonal.below(), Blocks.ACACIA_FENCE.defaultBlockState()
                .setValue(FenceBlock.WEST, true), exactShape);

        VillagerEntityMCA villager = spawn(level, Vec3.atBottomCenterOf(origin).add(0.0D, 0.5D, 0.0D));
        try {
            helper.assertTrue(level.noBlockCollision(villager, villager.getBoundingBox()),
                    "Fence-top start must be clear");
            helper.assertTrue(!level.noBlockCollision(villager, villager.getBoundingBox().move(0, -0.05D, 0)),
                    "Fence-top start must have support");
            boolean unsupported = false;
            for (int index = 1; index < 20; index++) {
                double fraction = index / 20.0D;
                unsupported |= level.noBlockCollision(villager,
                        villager.getBoundingBox().move(fraction, -0.05D, fraction));
            }
            helper.assertTrue(unsupported, "Diagonal fixture must cross an unsupported inside fence corner");
            List<BlockPos> offered = neighborsAtPlannedNode(level, villager, origin);
            helper.assertTrue(offered.contains(corner), "Supported cardinal fence route must remain available");
            helper.assertTrue(!offered.contains(diagonal),
                    "MCA offered an unsupported diagonal that drops the villager into the fence pen");

            for (BlockPos position : List.of(origin, origin.east(), corner, diagonal)) {
                level.setBlock(position.below(), Blocks.STONE.defaultBlockState(), exactShape);
            }
            villager.setPos(Vec3.atBottomCenterOf(origin));
            for (int index = 1; index < 20; index++) {
                double fraction = index / 20.0D;
                helper.assertTrue(!level.noBlockCollision(villager,
                                villager.getBoundingBox().move(fraction, -0.05D, fraction)),
                        "Broad platform must support the entire diagonal sweep");
            }
            helper.assertTrue(neighborsAtPlannedNode(level, villager, origin).contains(diagonal),
                    "Supported same-height diagonal must remain available");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_roofed_rise", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void fullOverhangRejectsImpossibleOneBlockRise(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos uphill = origin.east().above();
        prepareGround(level, origin);
        level.setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA villager = spawn(level, Vec3.atBottomCenterOf(origin));
        try {
            helper.assertTrue(villager.getBbHeight() > 1.0D && neighbors(level, villager, origin).contains(uphill),
                    "Fixture must allow the unobstructed one-block rise");
            level.setBlock(origin.above(2), Blocks.DIRT.defaultBlockState(), 3);
            helper.assertTrue(!neighbors(level, villager, origin).contains(uphill),
                    "MCA offered an uphill jump that collides with the full-block overhang");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_roofed_detour", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void overhungRiseDoesNotSkipClearDetourWaypoint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareGround(level, origin);
        level.setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA villager = spawn(level,
                new Vec3(origin.getX() + 0.65D, origin.getY(), origin.getZ() + 0.90D));
        try {
            helper.assertTrue(villager.blockPosition().equals(origin)
                            && level.noCollision(villager, villager.getBoundingBox()),
                    "Side-approach villager must start in the clear departure cell");
            Path unobstructed = detour(origin);
            helper.assertTrue(villager.getNavigation().moveTo(unobstructed, 1.0D),
                    "Unobstructed detour control must start");
            villager.getNavigation().tick();
            helper.assertTrue(unobstructed.getNextNodeIndex() == 1, "Start node did not advance");
            villager.getNavigation().tick();
            helper.assertTrue(unobstructed.getNextNodeIndex() == 2,
                    "Open control must permit a direct path shortcut");

            villager.getNavigation().stop();
            level.setBlock(origin.above(2), Blocks.DIRT.defaultBlockState(), 3);
            Path roofed = detour(origin);
            helper.assertTrue(villager.getNavigation().moveTo(roofed, 1.0D), "Roofed detour must start");
            villager.getNavigation().tick();
            helper.assertTrue(roofed.getNextNodeIndex() == 1, "Roofed route did not advance past departure");
            villager.getNavigation().tick();
            helper.assertTrue(roofed.getNextNodeIndex() == 1,
                    "MCA skipped the only clear waypoint and targeted an impossible overhung ascent");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_farmland_rise", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void farmlandApproachPreservesUnreachedRaisedWaypoint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos raised = origin.east().above();
        BlockPos lowerSuccessor = origin.east(2);
        prepareGround(level, origin);
        level.setBlock(origin.below(), Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(raised.below(), Blocks.ACACIA_WOOD.defaultBlockState(), 3);
        level.setBlock(lowerSuccessor.below(), Blocks.ACACIA_PLANKS.defaultBlockState(), 3);

        VillagerEntityMCA villager = spawn(level,
                new Vec3(origin.getX() + 0.70D, origin.getY() - 0.0625D, origin.getZ() + 0.50D));
        try {
            helper.assertTrue(Math.abs(villager.getBoundingBox().minY - (origin.getY() - 0.0625D)) < 1.0E-6D,
                    "Villager must stand one sixteenth below the integer node height");
            Path path = new Path(List.of(
                    walkable(origin.west(2)), walkable(origin.west()), walkable(origin),
                    walkable(raised), walkable(lowerSuccessor)
            ), lowerSuccessor, true);
            helper.assertTrue(villager.getNavigation().moveTo(path, 1.0D), "Farmland path must start");
            path.setNextNodeIndex(3);
            villager.getNavigation().tick();
            helper.assertTrue(path.getNextNodeIndex() == 3,
                    "MCA skipped the unreached raised waypoint before its lower successor");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_raised_vine_start", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void partialHeightGroundDoesNotStartAtHeadLevelVine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos vinePos = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareGround(level, vinePos);
        level.setBlock(vinePos.below(), Blocks.FARMLAND.defaultBlockState(), 3);
        level.setBlock(vinePos.east(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(vinePos, Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.EAST, true), 3);

        VillagerEntityMCA villager = spawn(level, new Vec3(
                vinePos.getX() + 0.5D, vinePos.getY() - 0.0625D, vinePos.getZ() + 0.5D));
        MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
        try {
            helper.assertTrue(!villager.onClimbable(),
                    "Grounded villager must not already be climbing the head-level vine");
            helper.assertTrue(level.noCollision(villager, villager.getBoundingBox()),
                    "Grounded villager must have a collision-free starting position");
            evaluator.prepare(new PathNavigationRegion(level, vinePos.offset(-4, -3, -4),
                    vinePos.offset(4, 5, 4)), villager);
            Node start = evaluator.getStart();
            helper.assertTrue(start.asBlockPos().equals(villager.blockPosition()),
                    "MCA started inside an unreached head-level vine instead of the villager's feet: "
                            + start.asBlockPos() + " vs " + villager.blockPosition());
        } finally {
            evaluator.done();
            villager.discard();
        }
        helper.succeed();
    }

    private static void surroundWithOpenTrapdoors(ServerLevel level, BlockPos origin) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            level.setBlock(origin.relative(direction), Blocks.SPRUCE_TRAPDOOR.defaultBlockState()
                    .setValue(TrapDoorBlock.OPEN, true)
                    .setValue(TrapDoorBlock.FACING, direction), 3);
        }
    }

    private static boolean sweepCollides(ServerLevel level, VillagerEntityMCA villager, Vec3 destination) {
        Vec3 start = villager.position();
        AABB box = villager.getBoundingBox();
        for (int index = 1; index <= 20; index++) {
            double fraction = index / 20.0D;
            if (!level.noBlockCollision(villager, box.move(
                    (destination.x - start.x) * fraction, 0.0D,
                    (destination.z - start.z) * fraction))) {
                return true;
            }
        }
        return false;
    }

    private static VillagerEntityMCA spawn(ServerLevel level, Vec3 position) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withPosition(position).spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);
        return villager;
    }

    private static void prepareGround(ServerLevel level, BlockPos origin) {
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

    private static List<BlockPos> neighbors(ServerLevel level, VillagerEntityMCA villager, BlockPos departure) {
        MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
        evaluator.setCanPassDoors(true);
        evaluator.setCanOpenDoors(true);
        try {
            evaluator.prepare(new PathNavigationRegion(level, departure.offset(-5, -3, -5),
                    departure.offset(5, 5, 5)), villager);
            Node start = evaluator.getStart();
            if (!start.asBlockPos().equals(departure)) {
                throw new IllegalStateException("Unexpected evaluator start " + start + " for " + departure);
            }
            return offered(evaluator, start);
        } finally {
            evaluator.done();
        }
    }

    private static List<BlockPos> neighborsAtPlannedNode(ServerLevel level, VillagerEntityMCA villager,
                                                         BlockPos departure) {
        MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
        try {
            evaluator.prepare(new PathNavigationRegion(level, departure.offset(-5, -3, -5),
                    departure.offset(5, 5, 5)), villager);
            return offered(evaluator, walkable(departure));
        } finally {
            evaluator.done();
        }
    }

    private static List<BlockPos> offered(MCAWalkNodeEvaluator evaluator, Node start) {
        Node[] nodes = new Node[32];
        int count = evaluator.getNeighbors(nodes, start);
        ArrayList<BlockPos> offered = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            offered.add(nodes[i].asBlockPos());
        }
        return offered;
    }

    private static Path detour(BlockPos origin) {
        return new Path(List.of(walkable(origin), walkable(origin.south()), walkable(origin.east().above())),
                origin.east().above(), true);
    }

    private static Node walkable(BlockPos position) {
        Node node = new Node(position.getX(), position.getY(), position.getZ());
        node.type = PathType.WALKABLE;
        return node;
    }
}
