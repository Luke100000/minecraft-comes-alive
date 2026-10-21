package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.gametest.GameTestTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.List;

/** Physical-navigation regressions for ordinary MCA villagers, without Builder-specific behavior. */
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class NavigationPhysicalSafetyGameTests {
    private static final ResourceKey<Block> HALF_DEPTH_SHELF_KEY =
            ResourceKey.create(Registries.BLOCK, MCA.locate("gametest_half_depth_shelf"));
    private static Block halfDepthShelf;

    private NavigationPhysicalSafetyGameTests() {
    }

    @SubscribeEvent
    public static void registerShelfProxy(RegisterEvent event) {
        if (event.getRegistryKey() != Registries.BLOCK || !Boolean.getBoolean("mca.gametest.enabled")) {
            return;
        }
        event.register(Registries.BLOCK, HALF_DEPTH_SHELF_KEY.identifier(), () -> {
            halfDepthShelf = new Block(BlockBehaviour.Properties.of().strength(1.0F)
                    .noOcclusion().setId(HALF_DEPTH_SHELF_KEY)) {
                @Override
                public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
                    // BiblioCraft Legacy Expanded ShelfBlock NORTH_SHAPE (Fabric-1.21.1).
                    return Shapes.box(0.0D, 0.0D, 0.5D, 1.0D, 1.0D, 1.0D);
                }
            };
            return halfDepthShelf;
        });
    }

    /** A full-height, half-depth shelf is not a full cube: the villager must still reach the far side. */
    @GameTest(batch = "mca_half_depth_shelf_crossing", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 200)
    public static void narrowShelvesDoNotTrapNavigationAtTheirFrontFace(GameTestHelper helper) {
        assertShelvesCanBeCrossed(helper, shelfState());
    }

    @GameTest(batch = "mca_full_depth_shelf_control", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 200)
    public static void fullBlocksAllowEquivalentNavigation(GameTestHelper helper) {
        assertShelvesCanBeCrossed(helper, Blocks.STONE.defaultBlockState());
    }

    private static void assertShelvesCanBeCrossed(GameTestHelper helper, BlockState obstacle) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos shelf = start.north(2);
        BlockPos target = shelf.north(3);
        GameTestTerrain.prepareFlatArea(helper, start, 6, 4);
        for (int dx = -1; dx <= 1; dx++) {
            level.setBlock(shelf.offset(dx, 0, 0), obstacle, 3);
        }

        VillagerEntityMCA villager = spawnForShelfNavigation(level, Vec3.atBottomCenterOf(start));
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(), "The obstacle must have a reachable route");
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.65D), "The obstacle route must start");
        int[] ticks = {0};
        float[] previousYaw = {villager.getYRot()};
        double[] totalRotation = {0.0D};
        helper.onEachTick(() -> {
            totalRotation[0] += Math.abs(net.minecraft.util.Mth.wrapDegrees(villager.getYRot() - previousYaw[0]));
            previousYaw[0] = villager.getYRot();
            if (villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 1.0D && villager.onGround()) {
                villager.discard();
                helper.succeed();
            } else if (++ticks[0] >= 170) {
                String error = "Navigation stalled at " + obstacle.getBlock() + ": pos=" + villager.position()
                        + ", shelf=" + shelf + ", target=" + target + ", onGround=" + villager.onGround()
                        + ", horizontalCollision=" + villager.horizontalCollision
                        + ", pathIndex=" + path.getNextNodeIndex() + "/" + path.getNodeCount()
                        + ", nextNode=" + (path.isDone() ? "done" : path.getNextNodePos())
                        + ", totalBodyYaw=" + totalRotation[0];
                villager.discard();
                helper.fail(error);
            }
        });
    }

    /** Control: a villager already standing on the narrow shelf top can physically depart. */
    @GameTest(batch = "mca_half_depth_shelf_top", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 160)
    public static void villagerLeavesHalfDepthShelfTopWithoutCircling(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos shelf = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos target = shelf.north(3);
        GameTestTerrain.prepareFlatArea(helper, shelf, 5, 4);
        level.setBlock(shelf, shelfState(), 3);
        VillagerEntityMCA villager = spawnForShelfNavigation(level,
                new Vec3(shelf.getX() + 0.5D, shelf.getY() + 1.0D, shelf.getZ() + 0.75D));
        helper.assertTrue(level.noCollision(villager)
                        && !level.noBlockCollision(villager, villager.getBoundingBox().move(0.0D, -0.02D, 0.0D)),
                "Shelf-top start must be physically clear and supported by the half-depth shape");
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(), "The shelf top must have a reachable ground exit");
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.65D), "The shelf-top exit must start");
        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 1.0D && villager.onGround()) {
                villager.discard();
                helper.succeed();
            } else if (++ticks[0] >= 130) {
                String error = "Villager did not leave shelf top: pos=" + villager.position()
                        + ", horizontalCollision=" + villager.horizontalCollision
                        + ", pathIndex=" + path.getNextNodeIndex() + "/" + path.getNodeCount();
                villager.discard();
                helper.fail(error);
            }
        });
    }

    private static BlockState shelfState() {
        if (halfDepthShelf == null) {
            throw new IllegalStateException("GameTest shelf proxy was not registered");
        }
        return halfDepthShelf.defaultBlockState();
    }

    private static VillagerEntityMCA spawnForShelfNavigation(ServerLevel level, Vec3 position) {
        VillagerEntityMCA villager = spawn(level, position);
        villager.refreshBrain(level);
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(false);
        return villager;
    }

    @GameTest(batch = "mca_shared_node_standing_height", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 60)
    public static void snowNodeHasSameStandingHeightInSearchAndMovement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos snow = helper.absolutePos(new BlockPos(8, 2, 8));
        GameTestTerrain.prepareFlatArea(helper, snow, 4, 4);
        level.setBlock(snow, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 3), 3);
        VillagerEntityMCA villager = spawn(level, Vec3.atBottomCenterOf(snow.south(2)));
        try {
            Vec3 standing = PathNodeGeometry.walkingPosition(
                    level, snow, villager, WalkNodeEvaluator.getFloorLevel(level, snow));
            double navigationY = ((MCAGroundPathNavigation) villager.getNavigation())
                    .getGroundY(Vec3.atBottomCenterOf(snow));
            double collisionSurface = snow.getY() + level.getBlockState(snow)
                    .getCollisionShape(level, snow).max(Direction.Axis.Y);
            helper.assertTrue(Math.abs(standing.x - (snow.getX() + 0.5D)) < 1.0E-6D
                            && Math.abs(standing.z - (snow.getZ() + 0.5D)) < 1.0E-6D,
                    "search node standing position lost the node center: " + standing);
            helper.assertTrue(collisionSurface > snow.getY()
                            && Math.abs(standing.y - collisionSurface) < 1.0E-6D,
                    "search node did not account for the three-layer snow surface: " + standing);
            helper.assertTrue(Math.abs(navigationY - standing.y) < 1.0E-6D,
                    "movement and node clearance disagree on snow height: " + navigationY + " vs " + standing);
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    /** An unavailable airborne search is not evidence that a fresh destination is unreachable. */
    @GameTest(batch = "mca_landing_target", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void airborneWalkTargetWaitsForNativeSearch(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        GameTestTerrain.prepareFlatArea(helper, start, 4, 4);
        VillagerEntityMCA villager = spawn(level, Vec3.atBottomCenterOf(start).add(0, 0.5D, 0));
        villager.refreshBrain(level);
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(false);
        villager.setOnGround(false);
        var target = new WalkTarget(new PersistentPathTarget(start.east(2)), 0.5F, 0);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, target);
        var sink = new WanderOrTeleportToTargetTask();
        sink.tryStart(level, villager, level.getGameTime());
        helper.assertTrue(villager.getBrain().getMemory(
                        MemoryModuleType.WALK_TARGET).orElse(null) == target
                        && !villager.getBrain().hasMemoryValue(
                        MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                "An unavailable native search during landing must preserve fresh intent without reporting failure");
        helper.onEachTick(() -> {
            if (sink.getStatus() == Behavior.Status.RUNNING) {
                sink.tickOrStop(level, villager, level.getGameTime());
            } else {
                sink.tryStart(level, villager, level.getGameTime());
            }
            if (villager.getX() > start.getX() + 1.5D && villager.onGround()) {
                villager.discard();
                helper.succeed();
            }
        });
    }

    /** Rounded fence starts must not test departure from a hypothetical body embedded in the support. */
    @GameTest(batch = "mca_fence_departure", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void roundedFenceStartKeepsSupportedDeparture(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos fence = helper.absolutePos(new BlockPos(8, 2, 8));
        // The neighboring ground is outside the start node's permitted fall; the first exit must follow the fence.
        GameTestTerrain.prepareFlatArea(helper, fence.below(2), 4, 6);
        for (int x = 0; x < 3; x++) {
            level.setBlock(fence.east(x), Blocks.OAK_FENCE.defaultBlockState(), 3);
        }
        VillagerEntityMCA villager = spawn(level, Vec3.atBottomCenterOf(fence).add(0, 1.5D, 0));
        villager.refreshBrain(level);
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(false);
        helper.assertTrue(level.isPositionEntityTicking(villager.blockPosition()) && level.noCollision(villager)
                        && !level.noBlockCollision(villager, villager.getBoundingBox().move(0, -0.01D, 0)),
                "The villager must start ticking in actual contact with the clear fence surface");
        BlockPos target = fence.east(3).below(2);
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(),
                "A grounded villager must retain the supported exit along the fence: "
                        + (path == null ? "none" : path.getNodePos(0) + "/" + path.getNode(0).type
                        + " nodes=" + path.getNodeCount()));
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.5D), "The fence departure must start");
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 0.75D
                            && villager.getY() < fence.getY() + 0.5D && villager.onGround(),
                    "The villager must physically leave the fence and reach the ground destination");
            villager.discard();
        });
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
        var bedState = BuiltInRegistries.BLOCK.getOptional(Identifier.withDefaultNamespace("blue_bed"))
                .orElseThrow().defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.SOUTH);
        level.setBlock(bed, bedState, 3);
        level.setBlock(bed.south(), bedState.setValue(BlockStateProperties.BED_PART,
                BedPart.HEAD), 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withGender(Gender.FEMALE).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(bed).add(0, 0.5625D, 0)).spawn(EntitySpawnReason.STRUCTURE);
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

    @GameTest(batch = "mca_partial_floor_lintel", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void plannedSnowDepartureRejectsLowLintelAndKeepsDetour(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos snow = helper.absolutePos(new BlockPos(8, 2, 8));
        GameTestTerrain.prepareFlatArea(helper, snow, 4, 4);
        level.setBlock(snow, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 3), 3);
        level.setBlock(snow.north().above(2), Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withGender(Gender.MALE)
                .withPosition(Vec3.atBottomCenterOf(snow.south(2))).spawn(EntitySpawnReason.STRUCTURE);
        villager.getTraits().removeTrait(Traits.DWARFISM);
        villager.getGenetics().setGene(Genetics.SIZE, 0.5F);
        villager.getGenetics().setGene(Genetics.WIDTH, 0.5F);
        villager.refreshDimensions();
        villager.refreshBrain(level);
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);
        helper.assertTrue(villager.getBbHeight() > 1.75F && villager.getBbHeight() < 2.0F,
                "The supported snow pose must exceed lintel clearance while fitting on the lower floor");
        helper.assertTrue(!neighborsAtPlannedNode(level, villager, snow).contains(snow.north()),
                "A future route edge must not lower the body before it has physically left the snow support");
        helper.assertTrue(neighborsAtPlannedNode(level, villager, snow).contains(snow.east()),
                "A clear departure from the same partial surface must remain available");
        BlockPos target = snow.north(2);
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach() && villager.getNavigation().moveTo(path, 0.5D),
                "Native navigation must retain a route around the obstructed lintel approach");
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 0.75D && villager.onGround(),
                    "The villager must physically complete the native detour: pos=" + villager.position()
                            + ", age=" + villager.tickCount + ", noAi=" + villager.isNoAi()
                            + ", grounded=" + villager.onGround() + ", collision=" + villager.horizontalCollision
                            + ", path=" + villager.getNavigation().getPath()
                            + ", wanted=" + villager.getMoveControl().getWantedX() + ","
                            + villager.getMoveControl().getWantedY() + "," + villager.getMoveControl().getWantedZ());
            villager.discard();
        });
    }

    /** Replays the admitted detour's waypoints; search is outside this route-following regression. */
    @GameTest(batch = "mca_partial_floor_lintel_following", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void lowerDetourWaypointRetainsItsRequiredSurface(GameTestHelper helper) {
        assertLowerDetourReachesOpening(helper, Blocks.AIR.defaultBlockState());
    }

    @GameTest(batch = "mca_partial_floor_raised_opening", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void slightlyRaisedOpeningStillNeedsTheLowerDetourSurface(GameTestHelper helper) {
        assertLowerDetourReachesOpening(helper,
                Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 2));
    }

    private static void assertLowerDetourReachesOpening(GameTestHelper helper,
                                                       net.minecraft.world.level.block.state.BlockState openingFloor) {
        ServerLevel level = helper.getLevel();
        BlockPos snow = helper.absolutePos(new BlockPos(8, 2, 8));
        GameTestTerrain.prepareFlatArea(helper, snow, 4, 4);
        level.setBlock(snow, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 3), 3);
        level.setBlock(snow.north(), openingFloor, 3);
        level.setBlock(snow.north().above(2), Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withGender(Gender.MALE)
                .withPosition(Vec3.atBottomCenterOf(snow.south(2))).spawn(EntitySpawnReason.STRUCTURE);
        villager.getTraits().removeTrait(Traits.DWARFISM);
        villager.getGenetics().setGene(Genetics.SIZE, 0.5F);
        villager.getGenetics().setGene(Genetics.WIDTH, 0.5F);
        villager.refreshDimensions();
        villager.refreshBrain(level);
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);
        helper.assertTrue(level.isPositionEntityTicking(villager.blockPosition()) && level.noCollision(villager)
                        && villager.getBbHeight() > 1.75F && villager.getBbHeight() < 2.0F,
                "The native detour must start with a ticking, clear adult body");
        BlockPos target = snow.north(2);
        Path path = new Path(List.of(walkable(snow.south(2)), walkable(snow.south()), walkable(snow.east()),
                walkable(snow.north()), walkable(target)), target, true);
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.5D), "The observed native detour must start");
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 0.75D && villager.onGround(),
                    "Native following skipped the lower surface needed to clear the lintel: pos=" + villager.position()
                            + ", node=" + path.getNextNodeIndex() + ", collision=" + villager.horizontalCollision);
            villager.discard();
        });
    }

    @GameTest(batch = "mca_partial_floor_upward_following", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void followingStepAboveActualSupportDoesNotRequireADescent(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos snow = helper.absolutePos(new BlockPos(8, 2, 8));
        GameTestTerrain.prepareFlatArea(helper, snow, 4, 4);
        level.setBlock(snow, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 3), 3);
        level.setBlock(snow.north(), Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 4), 3);
        VillagerEntityMCA villager = spawn(level, Vec3.atBottomCenterOf(snow).add(0.67D, 0.25D, 0.277D));
        helper.assertTrue(level.noCollision(villager)
                        && !level.noBlockCollision(villager, villager.getBoundingBox().move(0, -0.01D, 0)),
                "The positive must retain actual snow contact before a genuinely higher step");
        Path path = new Path(List.of(walkable(snow.east()), walkable(snow.north())), snow.north(), true);
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.5D), "The clear higher-step route must start");
        villager.getNavigation().tick();
        helper.assertTrue(path.getNextNodeIndex() == 1,
                "An upward step from the actual support must retain native waypoint tolerance");
        villager.discard();
        helper.succeed();
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
        assertFenceTopCorner(helper, false);
    }

    @GameTest(batch = "mca_grounded_fence_diagonal", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void groundedFenceTopCornerRejectsUnsupportedDiagonal(GameTestHelper helper) {
        assertFenceTopCorner(helper, true);
    }

    private static void assertFenceTopCorner(GameTestHelper helper, boolean groundedStart) {
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

        // Tiny collision rounding keeps getStart in this cell, rather than the one above the fence top.
        VillagerEntityMCA villager = spawn(level, Vec3.atBottomCenterOf(origin)
                .add(0.0D, groundedStart ? 0.5D - 1.0E-8D : 0.5D, 0.0D));
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
            List<BlockPos> offered = groundedStart ? neighbors(level, villager, origin)
                    : neighborsAtPlannedNode(level, villager, origin);
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
                .withAge(0).withPosition(position).spawn(EntitySpawnReason.STRUCTURE);
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
