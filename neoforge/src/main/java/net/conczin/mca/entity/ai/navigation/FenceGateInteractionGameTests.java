package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class FenceGateInteractionGameTests {
    private static final double FLOOR_EPSILON = 1.0E-3D;
    private static final double RAISED_EPSILON = 1.0E-3D;

    private FenceGateInteractionGameTests() {
    }

    @GameTest(batch = "mca_fence_gate_config", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void fenceGateInteractionControlsPathingAndOpening(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 4));
        BlockPos gatePos = helper.absolutePos(new BlockPos(5, 2, 4));
        BlockPos openGatePos = helper.absolutePos(new BlockPos(5, 2, 5));
        helper.getLevel().setBlock(gatePos, Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);
        helper.getLevel().setBlock(openGatePos,
                Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(BlockStateProperties.OPEN, true), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(villagerPos))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);

        Config config = Config.getInstance();
        boolean original = config.villagersInteractWithFenceGates;
        try {
            MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
            PathfindingContext context = new PathfindingContext(helper.getLevel(), villager);

            config.villagersInteractWithFenceGates = true;
            helper.assertTrue(PathingBlockInteraction.isOpenable(helper.getLevel().getBlockState(gatePos)),
                    "enabled fence-gate interaction did not make the gate openable");
            helper.assertTrue(evaluator.getPathType(context, gatePos.getX(), gatePos.getY(), gatePos.getZ()) == PathType.WALKABLE_DOOR,
                    "enabled fence-gate interaction did not make the closed gate pathable");

            PathType openGateType = evaluator.getPathType(context, openGatePos.getX(), openGatePos.getY(), openGatePos.getZ());
            PathType vanillaOpenGateType = WalkNodeEvaluator.getPathTypeStatic(context, openGatePos.mutable());
            helper.assertTrue(openGateType == vanillaOpenGateType && openGateType.getMalus() >= 0.0F,
                    "open fence gate did not use vanilla passable pathing: MCA=" + openGateType
                            + ", vanilla=" + vanillaOpenGateType);

            config.villagersInteractWithFenceGates = false;
            helper.assertTrue(!PathingBlockInteraction.isOpenable(helper.getLevel().getBlockState(gatePos)),
                    "disabled fence-gate interaction still allowed the gate to be opened");
            helper.assertTrue(evaluator.getPathType(context, gatePos.getX(), gatePos.getY(), gatePos.getZ()) == PathType.FENCE,
                    "disabled fence-gate interaction did not restore vanilla closed-gate pathing");

            helper.succeed();
        } finally {
            config.villagersInteractWithFenceGates = original;
            villager.discard();
        }
    }

    @GameTest(batch = "mca_fence_gate_raised_support", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void closedFenceGateRaisedSupportRejectsBlockedNeighbor(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos stair = prepareRaisedGateFixture(helper, feet);
        BlockPos gate = stair.north();
        VillagerEntityMCA villager = spawnSizedVillager(helper, feet);
        PathNavigationRegion region = new PathNavigationRegion(
                helper.getLevel(),
                feet.offset(-4, -2, -4),
                feet.offset(5, 6, 4)
        );
        BlockPos origin = stair.above();
        BlockPos candidate = gate.above();

        try {
            AABB originBox = mobBoxAt(villager, region, origin);
            AABB candidateBox = mobBoxAt(villager, region, candidate);
            helper.assertTrue(candidateBox.minY > originBox.minY + RAISED_EPSILON,
                    "gate fixture did not produce a raised transition");
            helper.assertTrue(!canSweepTo(villager, region, originBox, candidateBox),
                    "gate fixture raised transition was physically clear");

            WalkNodeEvaluator vanilla = new WalkNodeEvaluator();
            vanilla.setCanPassDoors(true);
            vanilla.setCanOpenDoors(true);
            vanilla.prepare(region, villager);
            try {
                helper.assertTrue(hasNeighbor(vanilla, origin, candidate),
                        "gate fixture did not produce the vanilla raised transition");
            } finally {
                vanilla.done();
            }

            MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
            evaluator.setCanPassDoors(true);
            evaluator.setCanOpenDoors(true);
            evaluator.prepare(region, villager);
            try {
                helper.assertTrue(!hasNeighbor(evaluator, origin, candidate),
                        "MCA kept blocked raised transition onto closed fence gate");
            } finally {
                evaluator.done();
            }

            helper.succeed();
        } finally {
            villager.discard();
        }
    }

    @GameTest(batch = "mca_planter_corner", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void raisedPlanterCornerRejectsDiagonalBlockedBeforeJumpRange(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos candidate = preparePlanterFixture(helper, feet);
        VillagerEntityMCA villager = spawnSizedVillager(helper, feet);
        PathNavigationRegion region = new PathNavigationRegion(helper.getLevel(), feet.offset(-4, -2, -4), feet.offset(4, 5, 4));
        try {
            MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
            evaluator.prepare(region, villager);
            try {
                helper.assertTrue(hasNeighbor(evaluator, feet, candidate),
                        "unobstructed planter rise lost its usable diagonal");
            } finally {
                evaluator.done();
            }

            helper.getLevel().setBlock(feet.west(), Blocks.OAK_TRAPDOOR.defaultBlockState()
                    .setValue(BlockStateProperties.OPEN, true)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 3);
            helper.getLevel().setBlock(feet.south(), Blocks.OAK_TRAPDOOR.defaultBlockState()
                    .setValue(BlockStateProperties.OPEN, true)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 3);
            // At this literal point the target is already within vanilla's one-block jump range,
            // but the upright planter edging prevents the grounded body from getting here.
            AABB jumpApproachBox = villager.getBoundingBox().move(-0.30D, 0.0D, 0.30D);
            helper.assertTrue(!helper.getLevel().noCollision(villager, jumpApproachBox),
                    "planter fixture did not obstruct the grounded jump approach");

            evaluator.prepare(region, villager);
            try {
                helper.assertTrue(!hasNeighbor(evaluator, feet, candidate),
                        "MCA kept diagonal planter rise blocked outside the jump trigger range");
            } finally {
                evaluator.done();
            }
            helper.succeed();
        } finally {
            villager.discard();
        }
    }

    @GameTest(batch = "mca_planter_corner", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void planterEdgesPreserveUsableDiagonals(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos candidate = preparePlanterFixture(helper, feet);
        VillagerEntityMCA villager = spawnSizedVillager(helper, feet);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState open = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.OPEN, true);
        BlockState[][] usableEdges = {
                {open.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), air},
                {air, open.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)},
                {open.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH),
                        open.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.WEST)},
                {Blocks.OAK_TRAPDOOR.defaultBlockState(), Blocks.OAK_TRAPDOOR.defaultBlockState()}
        };
        PathNavigationRegion region = new PathNavigationRegion(helper.getLevel(), feet.offset(-4, -2, -4), feet.offset(4, 5, 4));
        try {
            MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
            for (BlockState[] edges : usableEdges) {
                helper.getLevel().setBlock(feet.west(), edges[0], 3);
                helper.getLevel().setBlock(feet.south(), edges[1], 3);
                evaluator.prepare(region, villager);
                try {
                    helper.assertTrue(hasNeighbor(evaluator, feet, candidate),
                            "usable planter diagonal was rejected: " + edges[0] + ", " + edges[1]);
                } finally {
                    evaluator.done();
                }
            }
            helper.succeed();
        } finally {
            villager.discard();
        }
    }

    @GameTest(batch = "mca_planter_corner", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void villagerWalksAroundBlockedPlanterCorner(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(5, 2, 5));
        BlockPos candidate = preparePlanterFixture(helper, feet);
        helper.getLevel().setBlock(feet.west(), Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH), 3);
        helper.getLevel().setBlock(feet.south(), Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 3);
        VillagerEntityMCA villager = spawnSizedVillager(helper, feet);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(false);
        var path = villager.getNavigation().createPath(candidate, 0);
        helper.assertTrue(path != null && path.canReach(), "planter corner has no usable alternate route");
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.5D), "planter alternate route did not start");
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.blockPosition().equals(candidate),
                    "villager stalled on planter approach at " + villager.position());
            villager.discard();
        });
    }

    @GameTest(batch = "mca_partial_stair_fence", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void partialStairBesideFenceDoesNotInventRaisedStart(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(5, 2, 5));
        VillagerEntityMCA villager = partialStairFenceVillager(helper, feet);
        PathNavigationRegion region = new PathNavigationRegion(helper.getLevel(), feet.offset(-4, -2, -4), feet.offset(4, 5, 4));
        MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
        evaluator.prepare(region, villager);
        try {
            Node start = evaluator.getStart();
            helper.assertTrue(start.asBlockPos().equals(feet),
                    "Partial stair beside fence invented a raised start before the villager jumped: start="
                            + start.asBlockPos() + ", actual=" + villager.position());
            helper.succeed();
        } finally {
            evaluator.done();
            villager.discard();
        }
    }

    @GameTest(batch = "mca_partial_stair_fence", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void villagerLeavesPartialStairBesideFenceWithoutStalling(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(5, 2, 5));
        VillagerEntityMCA villager = partialStairFenceVillager(helper, feet);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(false);
        BlockPos target = feet.north().above();
        var path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(), "Partial stair has no usable route around its fence");
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.5D), "Partial stair route did not start");
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.blockPosition().equals(target),
                    "Villager stalled beside the fence at " + villager.position());
            villager.discard();
        });
    }

    private static VillagerEntityMCA partialStairFenceVillager(GameTestHelper helper, BlockPos feet) {
        preparePlanterFixture(helper, feet);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                helper.getLevel().setBlock(feet.offset(dx, -1, dz), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
        }
        helper.getLevel().setBlock(feet.north(), Blocks.SPRUCE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.NORTH).setValue(StairBlock.HALF, Half.BOTTOM), 3);
        helper.getLevel().setBlock(feet.south(), Blocks.SPRUCE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM), 3);
        for (int dz = -1; dz <= 1; dz++) {
            helper.getLevel().setBlock(feet.east().offset(0, 0, dz), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
        }
        helper.getLevel().setBlock(feet, Blocks.SPRUCE_FENCE.defaultBlockState()
                .setValue(BlockStateProperties.EAST, true), 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withGender(net.conczin.mca.entity.ai.relationship.Gender.FEMALE)
                .withAge(0).withPosition(Vec3.atBottomCenterOf(feet)).spawn(MobSpawnType.STRUCTURE);
        villager.getTraits().removeTrait(Traits.DWARFISM);
        villager.getTraits().removeTrait(Traits.TOUGH);
        villager.getTraits().removeTrait(Traits.WEAK);
        villager.setNoAi(true);
        villager.getGenetics().setGene(Genetics.SIZE, 0.5532416F);
        villager.getGenetics().setGene(Genetics.WIDTH, 0.53190565F);
        villager.refreshDimensions();
        villager.setPos(feet.getX() + 0.505684383D, feet.getY() + 0.5D, feet.getZ() + 0.91454658D);
        villager.setOnGround(true);
        helper.assertTrue(helper.getLevel().noCollision(villager, villager.getBoundingBox()),
                "Captured partial-stair start is not collision-free");
        return villager;
    }

    private static BlockPos preparePlanterFixture(GameTestHelper helper, BlockPos feet) {
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-3, -1, -3), feet.offset(3, 3, 3))) {
            helper.getLevel().setBlock(pos, pos.getY() == feet.getY() - 1
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        BlockPos candidate = feet.west().south().above();
        helper.getLevel().setBlock(candidate.below(), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        return candidate;
    }

    private static boolean hasNeighbor(WalkNodeEvaluator evaluator, BlockPos origin, BlockPos candidate) {
        Node[] neighbors = new Node[32];
        int count = evaluator.getNeighbors(
                neighbors,
                new Node(origin.getX(), origin.getY(), origin.getZ())
        );
        for (int index = 0; index < count; index++) {
            if (neighbors[index].asBlockPos().equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private static AABB mobBoxAt(VillagerEntityMCA villager, PathNavigationRegion region, BlockPos pos) {
        AABB box = villager.getBoundingBox();
        double floorY = WalkNodeEvaluator.getFloorLevel(region, pos);
        double centerX = (box.minX + box.maxX) * 0.5D;
        double centerZ = (box.minZ + box.maxZ) * 0.5D;
        return box.move(
                pos.getX() + 0.5D - centerX,
                floorY + FLOOR_EPSILON - box.minY,
                pos.getZ() + 0.5D - centerZ
        );
    }

    private static boolean canSweepTo(
            VillagerEntityMCA villager,
            PathNavigationRegion region,
            AABB originBox,
            AABB destinationBox
    ) {
        AABB sweepBox = originBox;
        double rise = destinationBox.minY - originBox.minY;
        if (rise > RAISED_EPSILON) {
            AABB verticalSweep = originBox.expandTowards(0.0D, rise, 0.0D);
            if (!region.noBlockCollision(villager, verticalSweep)) {
                return false;
            }
            sweepBox = originBox.move(0.0D, rise, 0.0D);
        }

        Vec3 travel = new Vec3(
                destinationBox.minX - originBox.minX,
                0.0D,
                destinationBox.minZ - originBox.minZ
        );
        int steps = Mth.ceil(travel.length() / originBox.getSize());
        if (steps <= 0) {
            return true;
        }

        Vec3 step = travel.scale(1.0D / steps);
        for (int index = 0; index < steps; index++) {
            sweepBox = sweepBox.move(step);
            if (!region.noBlockCollision(villager, sweepBox)) {
                return false;
            }
        }
        return true;
    }

    private static BlockPos prepareRaisedGateFixture(GameTestHelper helper, BlockPos feet) {
        for (int x = feet.getX() - 3; x <= feet.getX() + 4; x++) {
            for (int z = feet.getZ() - 3; z <= feet.getZ() + 3; z++) {
                helper.getLevel().setBlock(new BlockPos(x, feet.getY() - 1, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = feet.getY(); y <= feet.getY() + 5; y++) {
                    helper.getLevel().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        BlockPos stair = feet.east();
        helper.getLevel().setBlock(stair, Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST)
                .setValue(StairBlock.HALF, Half.BOTTOM), 3);
        BlockPos gate = stair.north();
        helper.getLevel().setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);
        helper.getLevel().setBlock(gate.above(), Blocks.TORCH.defaultBlockState(), 3);
        helper.getLevel().setBlock(stair.above(3), Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST)
                .setValue(StairBlock.HALF, Half.TOP), 3);
        return stair;
    }

    private static VillagerEntityMCA spawnSizedVillager(GameTestHelper helper, BlockPos feet) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .spawn(MobSpawnType.STRUCTURE);
        villager.getTraits().removeTrait(Traits.DWARFISM);
        villager.getTraits().removeTrait(Traits.TOUGH);
        villager.getTraits().removeTrait(Traits.WEAK);
        villager.getGenetics().setGene(Genetics.SIZE, 0.5F);
        villager.getGenetics().setGene(Genetics.WIDTH, 0.5F);
        villager.refreshDimensions();
        villager.setNoAi(true);
        villager.setOnGround(true);
        return villager;
    }

}
