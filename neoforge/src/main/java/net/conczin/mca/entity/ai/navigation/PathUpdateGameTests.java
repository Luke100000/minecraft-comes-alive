package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.gametest.GameTestTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@PrefixGameTestTemplate(false)
public final class PathUpdateGameTests {
    private static final String BATCH = "mca_toggle_path_updates";
    private static final List<VillagerEntityMCA> ACTORS = new ArrayList<>();
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static final Map<BlockPos, BlockState> ORIGINAL_FLOORS = new HashMap<>();

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        ACTORS.forEach(VillagerEntityMCA::discard);
        ACTORS.clear();
        ORIGINAL_FLOORS.forEach((pos, state) -> level.setBlock(pos, state, 3));
        ORIGINAL_FLOORS.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
        FORCED_CHUNKS.clear();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void operableDoorToggleKeepsReachablePath(GameTestHelper helper) {
        checkUpdate(helper, door(false), door(true), 0, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void operableGateToggleKeepsReachablePath(GameTestHelper helper) {
        BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState();
        checkUpdate(helper, gate, gate.setValue(BlockStateProperties.OPEN, true), 0, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void headHeightGateToggleKeepsReachablePath(GameTestHelper helper) {
        BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState();
        checkUpdate(helper, gate, gate.setValue(BlockStateProperties.OPEN, true), 1, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void newGatePlacementStillInvalidatesPath(GameTestHelper helper) {
        checkUpdate(helper, Blocks.AIR.defaultBlockState(), Blocks.OAK_FENCE_GATE.defaultBlockState(), 0, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void gateOrientationChangeStillInvalidatesPath(GameTestHelper helper) {
        BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState();
        checkUpdate(helper, gate, gate.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST), 0, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void clearOverheadTrapdoorToggleKeepsPath(GameTestHelper helper) {
        BlockState trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState();
        checkUpdate(helper, trapdoor, trapdoor.setValue(BlockStateProperties.OPEN, true), 3, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void supportingTrapdoorToggleStillInvalidatesPath(GameTestHelper helper) {
        BlockState trapdoor = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(BlockStateProperties.HALF, Half.TOP);
        checkUpdate(helper, trapdoor, trapdoor.setValue(BlockStateProperties.OPEN, true), -1, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void unopenableDoorClosingStillInvalidatesPath(GameTestHelper helper) {
        BlockState open = Blocks.IRON_DOOR.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.OPEN, true);
        checkUpdate(helper, open, open.setValue(BlockStateProperties.OPEN, false), 0, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void gateToggleStillInvalidatesPartialPath(GameTestHelper helper) {
        BlockState gate = Blocks.OAK_FENCE_GATE.defaultBlockState();
        checkUpdate(helper, gate, gate.setValue(BlockStateProperties.OPEN, true), 0, true, true);
    }

    private static BlockState door(boolean open) {
        return Blocks.OAK_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.EAST)
                .setValue(BlockStateProperties.OPEN, open);
    }

    /** Exercise world notifications and the live navigator, with locomotion outside this policy test's scope. */
    private static void checkUpdate(GameTestHelper helper, BlockState before, BlockState after,
                                    int height, boolean invalidates) {
        checkUpdate(helper, before, after, height, invalidates, false);
    }

    private static void checkUpdate(GameTestHelper helper, BlockState before, BlockState after,
                                    int height, boolean invalidates, boolean partial) {
        BlockPos start = helper.absolutePos(new BlockPos(4, height < 0 ? 2 : 1, 4));
        BlockPos target = start.east(24);
        BlockPos changed = start.east(12).above(height);
        helper.assertTrue(helper.getBounds().contains(target.getX() + 0.5, changed.getY() + 0.5, target.getZ() + 0.5),
                "path-update terrain must stay inside its allocated arena");
        ChunkPos min = new ChunkPos(start.offset(-1, 0, -1));
        ChunkPos max = new ChunkPos(target.offset(1, 0, 1));
        for (int x = min.x; x <= max.x; x++) for (int z = min.z; z <= max.z; z++) {
            ChunkPos chunk = new ChunkPos(x, z);
            if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) FORCED_CHUNKS.add(chunk);
        }
        // The low corridor's supporting floor is one layer below the template;
        // restore that owned footprint as well as releasing our chunk tickets.
        for (BlockPos pos : BlockPos.betweenClosed(start.offset(-1, -1, -1), target.offset(1, -1, 1))) {
            ORIGINAL_FLOORS.putIfAbsent(pos.immutable(), helper.getLevel().getBlockState(pos));
        }
        GameTestTerrain.prepareFlatPath(helper, start, target);
        helper.getLevel().setBlock(changed, before, 3);
        if (before.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            helper.getLevel().setBlock(changed.above(),
                    before.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER), 3);
        }
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start)).spawn(MobSpawnType.STRUCTURE);
        ACTORS.add(villager);
        villager.setNoAi(true);
        villager.setOnGround(true);
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach() && path.getNodeCount() > 16,
                "fixture did not establish a reachable route");
        // This test isolates invalidation policy, including its canReach boundary.
        if (partial) {
            List<net.minecraft.world.level.pathfinder.Node> nodes = new ArrayList<>();
            for (int i = 0; i < path.getNodeCount(); i++) nodes.add(path.getNode(i));
            path = new Path(nodes, target.east(20), false);
        }
        Path activePath = path;
        helper.assertTrue(villager.getNavigation().moveTo(activePath, 0), "fixture did not activate its route");
        helper.runAfterDelay(21, () -> {
            try {
                var initial = PathRequestDiagnostics.recomputeSnapshot(villager);
                helper.getLevel().setBlock(changed, after, 3);
                var updated = PathRequestDiagnostics.recomputeSnapshot(villager);
                helper.assertTrue((updated.blockUpdates() > initial.blockUpdates()) == invalidates,
                        "unexpected toggle invalidation: " + before + " -> " + after + "; " + updated);
                if (!invalidates) {
                    helper.getLevel().setBlock(changed, before, 3);
                    helper.assertTrue(PathRequestDiagnostics.recomputeSnapshot(villager).blockUpdates()
                                    == initial.blockUpdates() && villager.getNavigation().getPath() == activePath,
                            "reverse toggle invalidated or replaced the retained route");
                }
            } finally {
                villager.discard();
            }
            helper.succeed();
        });
    }
}
