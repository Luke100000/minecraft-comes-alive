package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.gametest.GameTestTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Navigation and real movement from inside a hollow workstation, with vanilla controls. */
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class HollowBlockNavigationGameTests {
    private static final String BATCH = "mca_hollow_block_escape";
    private static final List<Villager> ACTORS = new ArrayList<>();
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static Block hollowProxy;
    private static Block pathfindableHollowProxy;
    private static Block tallHollowProxy;

    private HollowBlockNavigationGameTests() { }

    @SubscribeEvent
    public static void registerHollowProxies(RegisterEvent event) {
        if (event.getRegistryKey() != Registries.BLOCK || !Boolean.getBoolean("mca.gametest.enabled")) return;
        event.register(Registries.BLOCK, MCA.locate("gametest_hollow_block"), () -> {
            hollowProxy = hollowBlock(0.9375D, false);
            return hollowProxy;
        });
        event.register(Registries.BLOCK, MCA.locate("gametest_pathfindable_hollow_block"), () -> {
            pathfindableHollowProxy = hollowBlock(0.9375D, true);
            return pathfindableHollowProxy;
        });
        event.register(Registries.BLOCK, MCA.locate("gametest_tall_hollow_block"), () -> {
            tallHollowProxy = hollowBlock(1.5D, false);
            return tallHollowProxy;
        });
    }

    private static Block hollowBlock(double rimHeight, boolean landPassable) {
        VoxelShape shape = Shapes.or(
                Shapes.box(0, 0, 0, 1, 0.1875D, 1),
                Shapes.box(0, 0, 0, 0.125D, rimHeight, 1),
                Shapes.box(0.875D, 0, 0, 1, rimHeight, 1),
                Shapes.box(0, 0, 0, 1, rimHeight, 0.125D),
                Shapes.box(0, 0, 0.875D, 1, rimHeight, 1));
        return new Block(BlockBehaviour.Properties.of().strength(1.0F).noOcclusion()) {
            @Override
            public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
                return shape;
            }

            @Override
            protected boolean isPathfindable(BlockState state, PathComputationType type) {
                return landPassable && type == PathComputationType.LAND;
            }
        };
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        ACTORS.forEach(Villager::discard);
        ACTORS.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
        FORCED_CHUNKS.clear();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void villagerEscapesCauldron(GameTestHelper helper) {
        assertEscape(helper, Blocks.CAULDRON.defaultBlockState(), 0.25D, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void villagerEscapesComposter(GameTestHelper helper) {
        assertEscape(helper, Blocks.COMPOSTER.defaultBlockState(), 0.125D, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void vanillaVillagerEscapesCauldron(GameTestHelper helper) {
        assertEscape(helper, Blocks.CAULDRON.defaultBlockState(), 0.25D, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void vanillaVillagerEscapesComposter(GameTestHelper helper) {
        assertEscape(helper, Blocks.COMPOSTER.defaultBlockState(), 0.125D, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void villagerEscapesUnrelatedHollowShape(GameTestHelper helper) {
        assertEscape(helper, hollowProxy.defaultBlockState(), 0.1875D, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void villagerEscapesHollowShapeMarkedLandPassable(GameTestHelper helper) {
        assertEscape(helper, pathfindableHollowProxy.defaultBlockState(), 0.1875D, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void lowRoofPreventsCauldronEscape(GameTestHelper helper) {
        assertEscape(helper, Blocks.CAULDRON.defaultBlockState(), 0.25D, false, true, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void lowRoofPreventsHollowShapeEscape(GameTestHelper helper) {
        assertEscape(helper, hollowProxy.defaultBlockState(), 0.1875D, false, true, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void hollowShapeAboveJumpReachHasNoEscapeRoute(GameTestHelper helper) {
        assertEscape(helper, tallHollowProxy.defaultBlockState(), 0.1875D, false, false, true);
    }

    private static void assertEscape(GameTestHelper helper, BlockState state, double bottom, boolean vanilla) {
        assertEscape(helper, state, bottom, vanilla, false, false);
    }

    private static void assertEscape(GameTestHelper helper, BlockState state, double bottom, boolean vanilla,
                                     boolean lowRoof, boolean tooHigh) {
        ServerLevel level = helper.getLevel();
        BlockPos container = helper.absolutePos(new BlockPos(20, 5, 20));
        ChunkPos min = new ChunkPos(container.offset(-6, 0, -6));
        ChunkPos max = new ChunkPos(container.offset(6, 0, 6));
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.getForcedChunks().contains(chunk.toLong())) FORCED_CHUNKS.add(chunk);
            }
        }
        GameTestTerrain.prepareFlatArea(helper, container, 6, 5);
        level.setBlock(container, state, 3);
        if (lowRoof) {
            level.setBlock(container.above(2), Blocks.STONE_SLAB.defaultBlockState()
                    .setValue(BlockStateProperties.SLAB_TYPE, SlabType.TOP), 3);
        }
        Vec3 start = Vec3.atBottomCenterOf(container).add(0.0D, bottom, 0.0D);
        Villager villager;
        if (vanilla) {
            villager = EntityType.VILLAGER.create(level);
            helper.assertTrue(villager != null, "Vanilla control must spawn");
            villager.addTag("doorGhost");
            villager.setPos(start);
            level.addFreshEntity(villager);
        } else {
            VillagerEntityMCA mca = VillagerFactory.newVillager(level).withAge(0).withGender(Gender.FEMALE).withPosition(start)
                    .spawn(MobSpawnType.STRUCTURE);
            mca.getTraits().removeTrait(Traits.DWARFISM);
            mca.getGenetics().setGene(Genetics.SIZE, 0.5F);
            mca.getGenetics().setGene(Genetics.WIDTH, 0.5F);
            mca.refreshDimensions();
            mca.refreshBrain(level);
            villager = mca;
        }
        ACTORS.add(villager);
        // Isolate navigation/movement from unrelated work, sleep, and POI decisions.
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);
        helper.assertTrue(level.noBlockCollision(villager, villager.getBoundingBox())
                        && !level.noBlockCollision(villager, villager.getBoundingBox().move(0, -0.01D, 0)),
                "Actor must fit inside and stand on the hollow block's bottom: " + villager.getBoundingBox());
        BlockPos destination = container.east(4);
        Path path = villager.getNavigation().createPath(destination, 0);
        if (lowRoof || tooHigh) {
            helper.assertTrue(path == null || !path.canReach(),
                    "An impossible rise must not admit an escape route: roof=" + lowRoof + ", tooHigh=" + tooHigh);
            villager.discard();
            helper.succeed();
            return;
        }
        helper.assertTrue(path != null && path.canReach(),
                "Must find an escape route from " + state + ": " + path);
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.6D), "Escape route must start");
        helper.onEachTick(() -> {
            helper.assertTrue(!villager.isRemoved(), "Actor must remain present during escape");
            helper.assertTrue(level.getBlockState(container).equals(state), "Escape must preserve the workstation");
            if (villager.distanceToSqr(Vec3.atBottomCenterOf(destination)) < 0.75D && villager.onGround()) {
                villager.discard();
                helper.succeed();
            }
        });
        helper.runAtTickTime(170, () -> helper.fail("Failed to escape " + state + ": pos=" + villager.position()
                + ", next=" + path.getNextNodeIndex() + "/" + path.getNodeCount()
                + ", wantedY=" + villager.getMoveControl().getWantedY()));
    }
}
