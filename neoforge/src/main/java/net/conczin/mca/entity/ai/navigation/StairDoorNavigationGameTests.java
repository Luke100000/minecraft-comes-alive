package net.conczin.mca.entity.ai.navigation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.tasks.SmarterOpenDoorsTask;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.gametest.GameTestTerrain;
import net.conczin.mca.gametest.GameTestBrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class StairDoorNavigationGameTests {
    private static final String BATCH = "mca_stair_door";
    private static final ResourceKey<Block> TALL_DOOR_KEY = ResourceKey.create(
            Registries.BLOCK, Identifier.fromNamespaceAndPath("dramaticdoors", "mca_gametest_tall_door"));
    private static final List<Villager> ACTORS = new ArrayList<>();
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static TallDoorProxy tallDoor;

    private StairDoorNavigationGameTests() { }

    @SubscribeEvent
    public static void registerTallDoorProxy(RegisterEvent event) {
        if (event.getRegistryKey() == Registries.BLOCK && Boolean.getBoolean("mca.gametest.enabled")) {
            event.register(Registries.BLOCK, TALL_DOOR_KEY.identifier(), () -> {
                        tallDoor = new TallDoorProxy();
                        return tallDoor;
                    });
        }
    }

    /** Optional integration proxy: Dramatic Doors 1.21.1's third property and linked setOpen contract. */
    public static final class TallDoorProxy extends Block {
        static final EnumProperty<DoorThird> THIRD = EnumProperty.create("third", DoorThird.class);

        TallDoorProxy() {
            super(BlockBehaviour.Properties.of().strength(1.0F).noOcclusion().setId(TALL_DOOR_KEY));
            registerDefaultState(stateDefinition.any().setValue(THIRD, DoorThird.LOWER)
                    .setValue(BlockStateProperties.OPEN, false)
                    .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST));
        }

        public BlockSetType type() { return BlockSetType.OAK; }

        public void setOpen(Entity entity, Level level, BlockState state, BlockPos pos, boolean open) {
            BlockPos lower = pos.below(state.getValue(THIRD).ordinal());
            for (DoorThird third : DoorThird.values()) {
                BlockPos part = lower.above(third.ordinal());
                level.setBlock(part, level.getBlockState(part).setValue(BlockStateProperties.OPEN, open), 2);
            }
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(THIRD, BlockStateProperties.OPEN, BlockStateProperties.HORIZONTAL_FACING);
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Blocks.OAK_DOOR.defaultBlockState()
                    .setValue(DoorBlock.FACING, state.getValue(BlockStateProperties.HORIZONTAL_FACING))
                    .setValue(DoorBlock.OPEN, state.getValue(BlockStateProperties.OPEN))
                    .getShape(level, pos, context);
        }
    }

    public enum DoorThird implements StringRepresentable {
        LOWER, MIDDLE, UPPER;

        @Override
        public String getSerializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void raisedSnowStartProjectsEveryLinkedTallDoorPanel(GameTestHelper helper) {
        BlockPos snow = prepareSnowDoorCorridor(helper);
        BlockPos lower = snow.east();
        for (DoorThird third : DoorThird.values()) {
            helper.getLevel().setBlock(lower.above(third.ordinal()),
                    tallDoor.defaultBlockState().setValue(TallDoorProxy.THIRD, third), 2);
        }
        VillagerEntityMCA actor = spawnProbe(helper, Vec3.atBottomCenterOf(snow).add(0, 0.25D, 0));
        helper.assertTrue(actor.getBoundingBox().maxY > lower.getY() + 2,
                "raised probe must touch the door's third panel");
        helper.assertTrue(PathingBlockInteraction.isDramaticDoorOpenable(helper.getLevel().getBlockState(lower)),
                "proxy must use the optional Dramatic Doors interaction contract");
        Path path = actor.getNavigation().createPath(snow.east(4), 0);
        for (DoorThird third : DoorThird.values()) {
            helper.assertTrue(!helper.getLevel().getBlockState(lower.above(third.ordinal()))
                            .getValue(BlockStateProperties.OPEN), "search changed a linked door panel");
        }
        helper.assertTrue(path != null && path.canReach(),
                "search left the linked upper door panel closed: " + describe(path));
        helper.assertTrue(PathingBlockInteraction.setOpen(actor, helper.getLevel(),
                helper.getLevel().getBlockState(lower), lower, true), "door task could not operate the proxy");
        helper.assertTrue(helper.getLevel().getBlockState(lower.above(2)).getValue(BlockStateProperties.OPEN),
                "operation must open the linked third panel");
        PathingBlockInteraction.setOpen(actor, helper.getLevel(), helper.getLevel().getBlockState(lower), lower, false);
        helper.getLevel().setBlock(lower.above(2), tallDoor.defaultBlockState(), 2);
        Path blocked = actor.getNavigation().createPath(snow.east(4), 0);
        helper.assertTrue(blocked == null || !blocked.canReach(),
                "search projected a separate door above the operated door as a linked upper panel");
        helper.getLevel().setBlock(lower.above(2),
                tallDoor.defaultBlockState().setValue(TallDoorProxy.THIRD, DoorThird.UPPER), 2);
        BlockPos target = snow.east(4);
        startWalking(helper, actor, target, true);
        helper.succeedWhen(() -> helper.assertTrue(actor.distanceToSqr(Vec3.atBottomCenterOf(target)) < 0.64D
                        && actor.onGround(), "villager failed natural passage through the tall door: " + actor.position()));
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        ACTORS.forEach(Villager::discard);
        ACTORS.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        FORCED_CHUNKS.clear();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void mcaEntersDoubleDoorFromStair(GameTestHelper helper) {
        enter(helper, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void vanillaEntersDoubleDoorFromStair(GameTestHelper helper) {
        enter(helper, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void raisedSnowStartPlansThroughClosedDoor(GameTestHelper helper) {
        BlockPos snow = prepareSnowDoorCorridor(helper);
        VillagerEntityMCA actor = spawnProbe(helper, Vec3.atBottomCenterOf(snow).add(0, 0.25D, 0));
        BlockPos target = snow.east(4);
        Path path = actor.getNavigation().createPath(target, 0);
        helper.assertTrue(!helper.getLevel().getBlockState(snow.east()).getValue(DoorBlock.OPEN),
                "planning changed the closed door");
        helper.assertTrue(path != null && path.canReach(),
                "supported snow start could not plan through an operable door: " + describe(path));
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void raisedSnowStartDoesNotPlanThroughIronDoor(GameTestHelper helper) {
        BlockPos snow = prepareSnowDoorCorridor(helper);
        helper.assertTrue(!Config.getServerConfig().villagersInteractWithAnyDoor,
                "iron-door negative requires the normal interaction policy");
        var door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        helper.getLevel().setBlock(snow.east(), door, 3);
        helper.getLevel().setBlock(snow.east().above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        VillagerEntityMCA actor = spawnProbe(helper, Vec3.atBottomCenterOf(snow).add(0, 0.25D, 0));
        Path path = actor.getNavigation().createPath(snow.east(4), 0);
        helper.assertTrue(path == null || !path.canReach(), "search treated a non-operable iron door as passable");
        helper.assertTrue(!helper.getLevel().getBlockState(snow.east()).getValue(DoorBlock.OPEN),
                "search operated the iron door");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void openedDoorPanelStillBlocksAnOversizedBody(GameTestHelper helper) {
        BlockPos snow = prepareSnowDoorCorridor(helper);
        VillagerEntityMCA actor = spawnProbe(helper, Vec3.atBottomCenterOf(snow).add(0, 0.25D, 0));
        actor.getAttribute(Attributes.SCALE).setBaseValue(1.5D);
        actor.refreshDimensions();
        helper.assertTrue(actor.getBbWidth() > 0.8125D && actor.getBbWidth() < 1.0D
                        && helper.getLevel().noCollision(actor),
                "oversized probe must fit the corridor but exceed the opened door's clearance");
        Path path = actor.getNavigation().createPath(snow.east(4), 0);
        helper.assertTrue(path == null || !path.canReach(), "search ignored the door panel after opening");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void raisedSnowStartPlansPastParallelDoorWithoutTogglingIt(GameTestHelper helper) {
        BlockPos snow = prepareSnowDoorCorridor(helper);
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.OPEN, true);
        helper.getLevel().setBlock(snow.east(), door, 3);
        helper.getLevel().setBlock(snow.east().above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        VillagerEntityMCA actor = spawnProbe(helper, Vec3.atBottomCenterOf(snow).add(0, 0.25D, 0));
        Path path = actor.getNavigation().createPath(snow.east(4), 0);
        helper.assertTrue(path != null && path.canReach(),
                "search did not allow the door task to clear the parallel door: " + describe(path));
        helper.assertTrue(helper.getLevel().getBlockState(snow.east()).getValue(DoorBlock.OPEN),
                "planning closed the parallel door in the world");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void raisedSnowStartWalksThroughClosedDoor(GameTestHelper helper) {
        BlockPos snow = prepareSnowDoorCorridor(helper);
        VillagerEntityMCA actor = spawnProbe(helper, Vec3.atBottomCenterOf(snow).add(0, 0.25D, 0));
        BlockPos target = snow.east(4);
        startWalking(helper, actor, target, true);
        helper.succeedWhen(() -> helper.assertTrue(actor.distanceToSqr(Vec3.atBottomCenterOf(target)) < 0.64D
                        && actor.onGround(), "villager failed to leave supported snow through the door: "
                        + actor.position() + ", path=" + describe(actor.getNavigation().getPath())));
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void raisedSnowStartJumpsUpThroughClosedDoor(GameTestHelper helper) {
        BlockPos snow = prepareSnowDoorCorridor(helper);
        for (int x = 1; x <= 6; x++) {
            helper.getLevel().setBlock(snow.east(x), Blocks.STONE.defaultBlockState(), 3);
        }
        BlockPos doorPos = snow.east().above();
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        helper.getLevel().setBlock(doorPos, door, 3);
        helper.getLevel().setBlock(doorPos.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        VillagerEntityMCA actor = spawnProbe(helper, Vec3.atBottomCenterOf(snow).add(0, 0.25D, 0));
        helper.assertTrue(doorPos.getY() - actor.getY() > actor.maxUpStep(), "door fixture must require a jump");
        BlockPos target = snow.east(4).above();
        Path path = actor.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(),
                "jump clearance used the closed door's collider: " + describe(path));
        helper.assertTrue(!helper.getLevel().getBlockState(doorPos).getValue(DoorBlock.OPEN),
                "jump planning opened the door in the world");
        startWalking(helper, actor, target, true);
        helper.succeedWhen(() -> helper.assertTrue(actor.distanceToSqr(Vec3.atBottomCenterOf(target)) < 0.64D
                        && actor.onGround(), "villager failed the raised doorway jump: " + actor.position()));
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 100)
    public static void partialSurfaceWaypointWaitsForActualStandingHeight(GameTestHelper helper) {
        BlockPos snow = helper.absolutePos(new BlockPos(40, 3, 40));
        prepareArea(helper, snow, 5, 5);
        helper.getLevel().setBlock(snow.west(), Blocks.STONE_SLAB.defaultBlockState(), 3);
        helper.getLevel().setBlock(snow, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 3), 3);
        VillagerEntityMCA actor = spawnProbe(helper,
                new Vec3(snow.getX() + 0.1D, snow.getY() + 0.5D, snow.getZ() + 0.5D));
        BlockPos target = snow.east(2);
        Path path = new Path(List.of(walkable(snow), walkable(snow.east()), walkable(target)), target, true);
        helper.assertTrue(actor.getNavigation().moveTo(path, 0.5D), "partial-surface route did not start");
        ((MCAGroundPathNavigation) actor.getNavigation()).followThePath();
        helper.assertTrue(path.getNextNodeIndex() == 0,
                "partial waypoint advanced while the body was still supported above its physical floor");
        actor.setNoAi(false);
        helper.succeedWhen(() -> helper.assertTrue(actor.distanceToSqr(Vec3.atBottomCenterOf(target)) < 0.64D
                        && actor.onGround(), "partial waypoint never completed its physical descent: " + actor.position()));
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void semanticFinalTargetFinishesItsPartialFloorDescent(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(40, 3, 40));
        prepareArea(helper, target, 5, 5);
        helper.getLevel().setBlock(target.east(), Blocks.STONE_SLAB.defaultBlockState(), 3);
        VillagerEntityMCA actor = spawnProbe(helper,
                new Vec3(target.getX() + 0.92D, target.getY() + 0.5D, target.getZ() + 0.5D));
        class FloorTarget extends net.minecraft.world.entity.ai.behavior.BlockPosTracker
                implements MultiTargetPositionTracker {
            FloorTarget() { super(target); }

            @Override
            public Set<BlockPos> getPathTargets(net.minecraft.world.entity.Mob mob) { return Set.of(target); }

            @Override
            public boolean isReached(net.minecraft.world.entity.Mob mob, int distance) {
                return mob.onGround() && mob.blockPosition().equals(target)
                        && Math.abs(mob.getY() - target.getY()) < 0.125D;
            }
        }
        FloorTarget destination = new FloorTarget();
        actor.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, 0.55F, 0));
        Path route = actor.getNavigation().createPath(target, 0);
        helper.assertTrue(route != null && route.canReach()
                        && actor.getNavigation().moveTo(route, 0.55D),
                "The semantic floor target must retain a native admitted descent");
        actor.setNoAi(false);
        helper.onEachTick(() -> {
            if (destination.isReached(actor, 0)) {
                helper.succeed();
            } else {
                helper.assertTrue(!actor.getNavigation().isDone(),
                        "Native navigation completed the logical final node before its semantic descent: "
                                + actor.position() + " target=" + target + " grounded=" + actor.onGround()
                                + " path=" + describe(actor.getNavigation().getPath())
                                + " pathTarget=" + route.getTarget() + " reachable=" + route.canReach()
                                + " walk=" + actor.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                                .map(walk -> walk.getTarget().getClass().getName()));
            }
        });
    }

    private static BlockPos prepareSnowDoorCorridor(GameTestHelper helper) {
        BlockPos snow = helper.absolutePos(new BlockPos(40, 3, 40));
        prepareArea(helper, snow, 8, 6);
        for (int x = -1; x <= 6; x++) {
            for (int y = 0; y < 4; y++) {
                helper.getLevel().setBlock(snow.offset(x, y, -1), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(snow.offset(x, y, 1), Blocks.STONE.defaultBlockState(), 3);
                if (x == -1) helper.getLevel().setBlock(snow.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        helper.getLevel().setBlock(snow, Blocks.SNOW.defaultBlockState().setValue(BlockStateProperties.LAYERS, 3), 3);
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        helper.getLevel().setBlock(snow.east(), door, 3);
        helper.getLevel().setBlock(snow.east().above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        return snow;
    }

    private static VillagerEntityMCA spawnProbe(GameTestHelper helper, Vec3 position) {
        VillagerEntityMCA actor = VillagerFactory.newVillager(helper.getLevel()).withAge(0).withGender(Gender.MALE)
                .withPosition(position).withName("Node geometry probe").spawn(EntitySpawnReason.STRUCTURE);
        ACTORS.add(actor);
        actor.getTraits().removeTrait(Traits.DWARFISM);
        actor.getGenetics().setGene(Genetics.SIZE, 0.5F);
        actor.getGenetics().setGene(Genetics.WIDTH, 0.5F);
        actor.refreshDimensions();
        actor.setOnGround(true);
        actor.setNoAi(true);
        actor.getBrain().stopAll(helper.getLevel(), actor);
        actor.getBrain().removeAllBehaviors();
        helper.assertTrue(helper.getLevel().noCollision(actor)
                        && !helper.getLevel().noBlockCollision(actor, actor.getBoundingBox().move(0, -0.02D, 0)),
                "node geometry probe must start collision-free on physical support: " + actor.getBoundingBox());
        return actor;
    }

    private static Node walkable(BlockPos pos) {
        Node node = new Node(pos.getX(), pos.getY(), pos.getZ());
        node.type = PathType.WALKABLE;
        return node;
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void openDoorDoesNotRaiseStandingFloor(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos door = helper.absolutePos(new BlockPos(40, 3, 40));
        prepareArea(helper, door, 3, 4);
        var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.OPEN, true);
        level.setBlock(door, state, 3);
        level.setBlock(door.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        var actor = VillagerFactory.newVillager(level).withAge(0).withPosition(Vec3.atBottomCenterOf(door.west()))
                .withName("Door floor probe").spawn(EntitySpawnReason.STRUCTURE);
        ACTORS.add(actor);
        double floor = PathNodeGeometry.walkingPosition(level, door, actor,
                WalkNodeEvaluator.getFloorLevel(level, door)).y;
        helper.assertTrue(Math.abs(floor - door.getY()) < 1.0E-6D,
                "open door was treated as a supporting floor: expected=" + door.getY() + ", actual=" + floor);
        actor.discard();
        helper.succeed();
    }

    private static void enter(GameTestHelper helper, boolean mca) {
        ServerLevel level = helper.getLevel();
        BlockPos lowerStair = helper.absolutePos(new BlockPos(40, 3, 40));
        prepareArea(helper, lowerStair, 9, 7);
        BlockPos door = lowerStair.east(2).above(2);
        for (int z = 0; z <= 1; z++) {
            level.setBlock(lowerStair.offset(0, 0, z), Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST), 3);
            level.setBlock(lowerStair.offset(1, 1, z), Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                    .setValue(StairBlock.FACING, Direction.EAST), 3);
        }
        for (int x = 2; x <= 6; x++) {
            for (int z = -1; z <= 2; z++) {
                level.setBlock(lowerStair.offset(x, 1, z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                if (z == -1 || z == 2 || x == 2) {
                    level.setBlock(lowerStair.offset(x, 4, z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
                if (z == -1 || z == 2) {
                    level.setBlock(lowerStair.offset(x, 2, z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                    level.setBlock(lowerStair.offset(x, 3, z), Blocks.COBBLESTONE.defaultBlockState(), 3);
                }
            }
        }
        for (int z = 0; z <= 1; z++) {
            BlockPos pos = door.south(z);
            var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST)
                    .setValue(DoorBlock.HINGE, z == 0 ? DoorHingeSide.LEFT : DoorHingeSide.RIGHT);
            level.setBlock(pos, state, 3);
            level.setBlock(pos.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        }
        Vec3 start = Vec3.atBottomCenterOf(lowerStair).add(0.6D, 1.5D, 0.0D);
        Villager actor;
        if (mca) {
            var villager = VillagerFactory.newVillager(level).withAge(0).withGender(Gender.MALE).withPosition(start)
                    .withName("Stair door probe").spawn(EntitySpawnReason.STRUCTURE);
            villager.getTraits().removeTrait(Traits.DWARFISM);
            villager.getGenetics().setGene(Genetics.SIZE, 0.5F);
            villager.getGenetics().setGene(Genetics.WIDTH, 0.5F);
            villager.refreshDimensions();
            actor = villager;
        } else {
            actor = EntityType.VILLAGER.create(level, EntitySpawnReason.STRUCTURE);
            actor.addTag("doorGhost"); // Existing conversion exclusion keeps the vanilla control intact.
            actor.setPos(start);
            level.addFreshEntity(actor);
        }
        ACTORS.add(actor);
        actor.setOnGround(true);
        helper.assertTrue(level.noCollision(actor), "stair start intersects blocks: " + actor.getBoundingBox());
        helper.assertTrue(!level.noBlockCollision(actor, actor.getBoundingBox().move(0, -0.02D, 0)),
                "stair start has no physical support");
        BlockPos target = door.east(3);
        startWalking(helper, actor, target, mca);
        int[] openings = {0};
        int[] jumps = {0};
        boolean[] wasOpen = {false};
        boolean[] wasGrounded = {true};
        boolean[] crossedDoor = {false};
        helper.onEachTick(() -> {
            helper.assertTrue(!actor.isRemoved(), "entry probe was removed or converted");
            if (wasGrounded[0] && !actor.onGround() && actor.getDeltaMovement().y > 0.1D) jumps[0]++;
            wasGrounded[0] = actor.onGround();
            crossedDoor[0] |= actor.getBlockX() == door.getX()
                    && actor.getBlockZ() >= door.getZ() && actor.getBlockZ() <= door.getZ() + 1
                    && Math.abs(actor.getY() - door.getY()) < 0.125D;
            boolean open = level.getBlockState(door).getValue(DoorBlock.OPEN);
            if (open && !wasOpen[0]) openings[0]++;
            wasOpen[0] = open;
            Path path = actor.getNavigation().getPath();
            if (actor.distanceToSqr(Vec3.atBottomCenterOf(target)) < 0.64D && actor.onGround()) {
                helper.assertTrue(openings[0] == 1, "door cycled before entry: " + openings[0]);
                helper.assertTrue(jumps[0] <= 1, "repeated jumping before entry: " + jumps[0]);
                helper.assertTrue(crossedDoor[0], "actor reached the target without crossing the doorway");
                actor.discard();
                helper.succeed();
            } else if (helper.getTick() >= 160) {
                helper.fail("stair-door entry stalled: pos=" + actor.position() + ", path=" + describe(path)
                        + ", openings=" + openings[0]);
            }
        });
    }

    private static void startWalking(GameTestHelper helper, Villager actor, BlockPos target, boolean mca) {
        ServerLevel level = helper.getLevel();
        actor.setNoAi(false);
        var brain = actor.getBrain();
        brain.stopAll(level, actor);
        brain.removeAllBehaviors();
        if (mca) {
            GameTestBrain.addActivity(brain, Activity.CORE, 0, ImmutableList.of(new SmarterOpenDoorsTask()));
        } else {
            GameTestBrain.addActivity(brain, Activity.CORE, 0, ImmutableList.of(InteractWithDoor.create()));
        }
        GameTestBrain.addActivity(brain, Activity.CORE, 1, ImmutableList.of(new MoveToTargetSink()));
        GameTestBrain.addActivity(brain, Activity.IDLE, 0, ImmutableList.of(new DoNothing(200, 200)));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.65F, 0));
    }

    private static String describe(Path path) {
        if (path == null) return "null";
        List<BlockPos> nodes = new ArrayList<>();
        for (int i = 0; i < path.getNodeCount(); i++) nodes.add(path.getNodePos(i));
        return path.getNextNodeIndex() + "/" + path.getNodeCount() + " " + nodes;
    }

    private static void prepareArea(GameTestHelper helper, BlockPos center, int radius, int height) {
        ChunkPos min = ChunkPos.containing(center.offset(-radius, 0, -radius));
        ChunkPos max = ChunkPos.containing(center.offset(radius, 0, radius));
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (FORCED_CHUNKS.add(chunk)) helper.getLevel().setChunkForced(x, z, true);
            }
        }
        GameTestTerrain.prepareFlatArea(helper, center, radius, height);
    }
}
