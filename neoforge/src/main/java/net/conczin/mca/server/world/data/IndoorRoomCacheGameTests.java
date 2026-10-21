package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@PrefixGameTestTemplate(false)
public final class IndoorRoomCacheGameTests {
    private static final String BATCH = "mca_indoor_exterior_dependencies";
    private static final Map<BlockPos, BlockState> CHANGED = new LinkedHashMap<>();

    @GameTest(batch = BATCH, templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 120)
    public static void openingAFrameLintelInvalidatesCachedShelter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = room(helper);
        for (int z : new int[]{0, 2}) {
            put(level, min.offset(4, 0, z), Blocks.STONE.defaultBlockState());
            put(level, min.offset(4, 1, z), Blocks.STONE.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(min.offset(5, -1, -4), min.offset(14, -1, 4))) {
            put(level, pos, Blocks.STONE.defaultBlockState());
        }
        BlockPos lintel = min.offset(4, 2, 1), bed = min.offset(2, 0, 1);
        var closed = Blocks.OAK_TRAPDOOR.defaultBlockState();
        put(level, lintel, closed);
        IndoorRoomCache cache = new IndoorRoomCache(level);
        helper.assertTrue(cache.resolve(bed).isPresent(), "closed full-bottom-face lintel did not provide a frame");
        put(level, lintel, closed.setValue(TrapDoorBlock.OPEN, true));
        helper.assertTrue(new IndoorRoomCache(level).resolve(bed).isEmpty(),
                "open partial-face lintel did not invalidate fresh enclosure");
        helper.startSequence().thenIdle(41).thenExecute(() -> helper.assertTrue(cache.resolve(bed).isEmpty(),
                "opening a frame lintel retained stale shelter beyond the validation boundary")).thenSucceed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 120)
    public static void ordinaryTrapdoorTogglesStillReuseCachedGeometry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = room(helper);
        for (int z = 0; z < 3; z++) {
            put(level, min.offset(4, 0, z), Blocks.STONE.defaultBlockState());
            put(level, min.offset(4, 1, z), Blocks.STONE.defaultBlockState());
        }
        BlockPos bed = min.offset(2, 0, 1), hatch = bed.above(2);
        var closed = Blocks.OAK_TRAPDOOR.defaultBlockState();
        put(level, hatch, closed);
        IndoorRoomCache cache = new IndoorRoomCache(level);
        var initial = cache.resolve(bed).orElseThrow();
        put(level, hatch, closed.setValue(TrapDoorBlock.OPEN, true));
        helper.assertTrue(new IndoorRoomCache(level).resolve(bed).orElseThrow().floorCells().equals(initial.floorCells()),
                "ordinary hatch toggle changed fresh structural membership");
        helper.startSequence().thenIdle(41).thenExecute(() -> helper.assertTrue(cache.resolve(bed).orElseThrow() == initial,
                "frame dependency tracking disabled ordinary trapdoor-state normalization")).thenSucceed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 120)
    public static void ordinaryGateTogglesStillReuseCachedGeometry(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(new BlockPos(40, 8, 40));
        for (BlockPos pos : BlockPos.betweenClosed(min.offset(-1, -1, -1), min.offset(2, 2, 2))) {
            int x = pos.getX() - min.getX(), y = pos.getY() - min.getY(), z = pos.getZ() - min.getZ();
            put(level, pos, y == -1 || y == 2 || x == -1 || x == 2 || z == -1 || z == 2
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        var bedState = Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        BlockPos bed = min.offset(1, 0, 0), gate = min.offset(-1, 0, 1);
        put(level, min, bedState.setValue(BedBlock.PART, BedPart.FOOT));
        put(level, bed, bedState.setValue(BedBlock.PART, BedPart.HEAD));
        var closed = Blocks.OAK_FENCE_GATE.defaultBlockState();
        put(level, gate, closed);
        IndoorRoomCache cache = new IndoorRoomCache(level);
        var initial = cache.resolve(bed).orElseThrow();
        put(level, gate, closed.setValue(FenceGateBlock.OPEN, true));
        helper.assertTrue(new IndoorRoomCache(level).resolve(bed).orElseThrow().floorCells().equals(initial.floorCells()),
                "ordinary gate toggle changed fresh structural membership");
        helper.startSequence().thenIdle(41).thenExecute(() -> helper.assertTrue(cache.resolve(bed).orElseThrow() == initial,
                "frame dependency tracking disabled ordinary gate-state normalization")).thenSucceed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void exteriorSupportChangesInvalidateCachedShelter(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = room(helper);
        BlockPos bed = min.offset(2, 0, 1);
        put(level, min.offset(5, -1, 1), Blocks.STONE.defaultBlockState());
        IndoorRoomCache cache = new IndoorRoomCache(level);
        IndoorRoomCache.Room initial = cache.resolve(bed).orElseThrow();
        helper.assertTrue(!initial.floorCells().contains(min.offset(5, 0, 1)),
                "uncovered balcony became canonical room geometry");

        helper.startSequence().thenIdle(41).thenExecute(() -> {
            helper.assertTrue(cache.resolve(bed).orElseThrow() == initial,
                    "unchanged small exterior proof did not reuse the cached room");
            for (BlockPos pos : BlockPos.betweenClosed(min.offset(6, -1, -4), min.offset(14, -1, 4))) {
                put(level, pos, Blocks.STONE.defaultBlockState());
            }
            helper.assertTrue(new IndoorRoomCache(level).resolve(bed).isEmpty(),
                    "extended exterior did not invalidate fresh discovery");
        }).thenIdle(41).thenExecute(() -> helper.assertTrue(cache.resolve(bed).isEmpty(),
                "cached shelter ignored exterior supports outside its floor footprint")).thenSucceed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 120)
    public static void exteriorCeilingChangesRefreshCachedMembership(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = room(helper);
        BlockPos bed = min.offset(2, 0, 1);
        BlockPos balcony = min.offset(5, 0, 1);
        put(level, balcony.below(), Blocks.STONE.defaultBlockState());
        IndoorRoomCache cache = new IndoorRoomCache(level);
        helper.assertTrue(!cache.resolve(bed).orElseThrow().floorCells().contains(balcony),
                "uncovered balcony was already inside the room");
        put(level, balcony.above(4), Blocks.STONE.defaultBlockState());
        helper.assertTrue(new IndoorRoomCache(level).resolve(bed).orElseThrow().floorCells().contains(balcony),
                "covering the balcony did not change fresh room membership");
        helper.startSequence().thenIdle(41).thenExecute(() -> helper.assertTrue(
                cache.resolve(bed).orElseThrow().floorCells().contains(balcony),
                "cached room ignored a roof outside its former physical ceiling band")).thenSucceed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 120)
    public static void largeExteriorProofCannotKeepIncompleteDependenciesForever(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = room(helper);
        BlockPos bed = min.offset(2, 0, 1);
        for (BlockPos pos : BlockPos.betweenClosed(min.offset(5, -1, -3), min.offset(10, -1, 4))) {
            put(level, pos, Blocks.STONE.defaultBlockState());
        }
        IndoorRoomCache cache = new IndoorRoomCache(level);
        helper.assertTrue(cache.resolve(bed).isPresent(), "bounded 48-cell exterior was rejected");
        for (BlockPos pos : BlockPos.betweenClosed(min.offset(11, -1, -3), min.offset(13, -1, 4))) {
            put(level, pos, Blocks.STONE.defaultBlockState());
        }
        helper.assertTrue(new IndoorRoomCache(level).resolve(bed).isEmpty(),
                "72-cell exterior did not fail fresh discovery");
        helper.startSequence().thenIdle(41).thenExecute(() -> helper.assertTrue(cache.resolve(bed).isEmpty(),
                "oversized dependency proof silently retained stale cached shelter")).thenSucceed();
    }

    private static BlockPos room(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos min = helper.absolutePos(new BlockPos(40, 8, 40));
        for (BlockPos pos : BlockPos.betweenClosed(min.offset(-1, -1, -1), min.offset(4, 2, 4))) {
            int x = pos.getX() - min.getX(), y = pos.getY() - min.getY(), z = pos.getZ() - min.getZ();
            put(level, pos, y == -1 || y == 2 || x == -1 || x == 4 || z == -1 || z == 4
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        // This fixture exercises bounded exterior evidence, not framed-doorway admission.
        for (int z = 0; z < 3; z++) {
            put(level, min.offset(4, 0, z), Blocks.AIR.defaultBlockState());
            put(level, min.offset(4, 1, z), Blocks.AIR.defaultBlockState());
        }
        var state = Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        put(level, min.offset(1, 0, 1), state.setValue(BedBlock.PART, BedPart.FOOT));
        put(level, min.offset(2, 0, 1), state.setValue(BedBlock.PART, BedPart.HEAD));
        return min;
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) {
        CHANGED.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        CHANGED.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
        CHANGED.clear();
    }
}
