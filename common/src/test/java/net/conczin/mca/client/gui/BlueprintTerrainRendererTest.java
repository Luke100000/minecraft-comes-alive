package net.conczin.mca.client.gui;

import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintTerrainRendererTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void terrainSamplingPublishesWholeChunkSizedUnits() {
        BlueprintTerrainRenderer.SampleRegion first = BlueprintTerrainRenderer.sampleRegionBounds(0);
        BlueprintTerrainRenderer.SampleRegion second = BlueprintTerrainRenderer.sampleRegionBounds(1);
        assertEquals(new BlueprintTerrainRenderer.SampleRegion(0, 0, 16, 16), first);
        assertEquals(new BlueprintTerrainRenderer.SampleRegion(16, 0, 32, 16), second);
    }

    @Test
    void terrainSamplingIncludesOneCellHaloAroundCoreTile() {
        assertEquals(new BlueprintTerrainRenderer.SampleRegion(-1, 0, 0, 16),
                BlueprintTerrainRenderer.sampleRegionBounds(64));
        assertEquals(new BlueprintTerrainRenderer.SampleRegion(-1, -1, 0, 0),
                BlueprintTerrainRenderer.sampleRegionBounds(96));
    }

    @Test
    void dirtyTextureBoundsOnlyCoverCompletedChunkAndOnePixelBorder() {
        assertEquals(new BlueprintTerrainRenderer.SampleRegion(15, 15, 33, 33),
                BlueprintTerrainRenderer.dirtyTextureBounds(9));
    }

    @Test
    void terrainSchedulingCostCountsAgainstFrameBudget() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        fixture.level.nanosOnGameTimeRead = 2_000_000L;

        sampleFrame(fixture, 0L);

        assertEquals(0, fixture.chunk.sampledColumns,
                "work done before terrain sampling must consume the same frame budget");
    }

    @Test
    void cheapChunksBatchWithinOneFrameAndResumeWithoutResampling() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        sampleFrame(fixture, 0L);
        assertEquals(6 * 256, fixture.chunk.sampledColumns,
                "cheap terrain should fill multiple chunks within the frame budget");
        assertTrue(fixture.chunk.nanos >= 1_500_000L);
        assertTrue(fixture.chunk.nanos < 1_500_000L + 256_000L,
                "sampling must yield between chunks after crossing the budget");

        sampleFrame(fixture, 0L);
        assertEquals(12 * 256, fixture.chunk.sampledColumns);
        assertEquals(12, fixture.chunk.samplingOrder.stream().distinct().count(),
                "the next frame must continue with unsampled chunks");
    }

    @Test
    void expensiveChunkYieldsBeforeStartingAnotherChunk() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        fixture.chunk.nanosPerColumn = 10_000L;
        sampleFrame(fixture, 0L);
        assertEquals(256, fixture.chunk.sampledColumns);
        sampleFrame(fixture, 0L);
        assertEquals(512, fixture.chunk.sampledColumns);
        assertEquals(2, fixture.chunk.samplingOrder.size());
    }

    @Test
    void visibleChunksFillOutwardAcrossTileBoundaries() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        fixture.chunks.maxChunkX = 15;
        fixture.chunks.maxChunkZ = 15;
        fixture.viewport = viewport(124.0D, 124.0D, 35);
        fixture.chunk.nanosPerColumn = 6_000L;
        for (int frame = 0; frame < 4; frame++) sampleFrame(fixture, 0L);
        assertEquals(List.of(new ChunkPos(7, 7), new ChunkPos(7, 8),
                new ChunkPos(8, 7), new ChunkPos(8, 8)), fixture.chunk.samplingOrder,
                "distance must be compared across tiles rather than finishing one tile first");
    }

    @Test
    void visibleTerrainOutranksCloserOffscreenTerrainAndBorders() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        fixture.viewport = viewport(120.0D, 120.0D, 18);
        fixture.chunk.nanosPerColumn = 6_000L;
        for (int frame = 0; frame < 4; frame++) sampleFrame(fixture, 0L);
        assertEquals(List.of(new ChunkPos(7, 7), new ChunkPos(7, 6),
                new ChunkPos(6, 7), new ChunkPos(6, 6)), fixture.chunk.samplingOrder,
                "all available visible chunks must precede border and offscreen samples");
    }

    @Test
    void missingNearestChunkDoesNotConsumeWholeSamplingStep() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        fixture.chunks.missingTargetChunk = true;
        sampleFrame(fixture, 0L);
        assertEquals(1, fixture.chunks.targetChunkLookups);
        assertTrue(fixture.chunk.sampledColumns > 0,
                "other loaded chunks must still be sampled during the same frame");
    }

    @Test
    void incompleteRetryOnlyResamplesCellsThatFailedPreviousPass() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        fixture.chunks.missingTargetChunk = true;
        finishInitialSamplingPass(fixture);
        int columnsBeforeRetry = fixture.chunk.sampledColumns;
        int targetLookupsBeforeRetry = fixture.chunks.targetChunkLookups;
        sampleFrame(fixture, 19L);
        assertEquals(targetLookupsBeforeRetry, fixture.chunks.targetChunkLookups,
                "missing chunks must wait for their retry deadline");
        sampleFrame(fixture, 20L);
        assertTrue(fixture.chunks.targetChunkLookups > targetLookupsBeforeRetry);
        assertEquals(columnsBeforeRetry, fixture.chunk.sampledColumns,
                "retrying a missing chunk must preserve already sampled terrain");
    }

    @Test
    void successfulRetryCompletesTerrainWithoutFullRescan() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        fixture.chunks.missingTargetChunk = true;
        finishInitialSamplingPass(fixture);
        int columnsBeforeRetry = fixture.chunk.sampledColumns;
        fixture.chunks.missingTargetChunk = false;
        sampleFrame(fixture, 20L);
        assertEquals(256, fixture.chunk.sampledColumns - columnsBeforeRetry,
                "recovery should only sample the previously missing chunk");
        assertEquals(64, terrainHeight(cachedTile(0, 0), 49, 49));
    }

    @Test
    void haloRetryRecoversLateNeighborChunkWithoutRescanningCore() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        fixture.chunks.targetChunkX = -1;
        fixture.chunks.targetChunkZ = 0;
        fixture.chunks.missingTargetChunk = true;
        finishInitialSamplingPass(fixture);
        fixture.chunk.samplingOrder.clear();
        fixture.chunk.lastSampledChunk = null;
        fixture.chunks.missingTargetChunk = false;
        sampleFrame(fixture, 20L);
        assertEquals(64, terrainHeight(cachedTile(0, 0), 0, 1));
        assertTrue(fixture.chunk.samplingOrder.stream().allMatch(pos -> pos.x == -1 && pos.z == 0),
                "border recovery must not restart the successful core samples");
    }

    @Test
    void sampledTerrainRefreshesEvenWhenBorderChunkRemainsMissing() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        fixture.chunks.targetChunkX = -1;
        fixture.chunks.targetChunkZ = 0;
        fixture.chunks.missingTargetChunk = true;
        finishInitialSamplingPass(fixture);
        assertEquals(64, terrainHeight(cachedTile(0, 0), 1, 1));
        fixture.chunk.surfaceHeight = 68;
        for (int step = 0; step < 140; step++) sampleFrame(fixture, 600L);
        assertEquals(69, terrainHeight(cachedTile(0, 0), 1, 1),
                "an unavailable shading border must not prevent loaded terrain from refreshing");
    }

    @Test
    void unavailableCorePreservesOldPixelsWhileOtherChunksRefresh() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        finishInitialSamplingPass(fixture);
        fixture.chunks.missingTargetChunk = true;
        fixture.chunk.surfaceHeight = 68;
        for (int frame = 0; frame < 30; frame++) sampleFrame(fixture, 600L);
        assertEquals(64, terrainHeight(cachedTile(0, 0), 49, 49),
                "unavailable terrain must retain its previous pixels");
        assertEquals(69, terrainHeight(cachedTile(0, 0), 1, 1),
                "an unavailable core chunk must not prevent neighboring terrain from refreshing");
        fixture.chunks.missingTargetChunk = false;
        sampleFrame(fixture, 620L);
        assertEquals(69, terrainHeight(cachedTile(0, 0), 49, 49));
    }

    @Test
    void refreshingTileEdgeUpdatesNeighborShadingBorderInSameFrame() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        fixture.chunks.maxChunkX = 15;
        fixture.viewport = viewport(124.0D, 64.0D, 100);
        for (int frame = 0; frame < 50; frame++) sampleFrame(fixture, 0L);
        Object neighbor = cachedTile(128, 0);
        BitSet dirty = (BitSet) readField(neighbor, "dirtyRegions");
        dirty.clear();
        fixture.chunk.surfaceHeight = 68;
        fixture.chunk.nanosPerColumn = 6_000L;
        fixture.viewport = viewport(120.0D, 72.0D, 4);
        sampleFrame(fixture, 600L);
        assertEquals(69, terrainHeight(neighbor, 0, 65),
                "the adjacent tile must shade against the newly sampled boundary height");
        assertFalse(dirty.isEmpty(), "the neighboring texture must publish the changed border");
    }

    @Test
    void unloadedAreasDoNotAllocateTerrainTiles() throws ReflectiveOperationException {
        SamplingFixture fixture = samplingFixture();
        fixture.chunks.loadedChunk = null;
        sampleFrame(fixture, 0L);
        assertTrue(((Map<?, ?>) readStaticField("tiles")).isEmpty());
    }

    @Test
    void levelChangeDiscardsPreviousTerrainSamples() throws ReflectiveOperationException {
        SamplingFixture first = samplingFixture();
        sampleFrame(first, 0L);
        SamplingFixture second = samplingFixture();
        second.chunk.surfaceHeight = 68;
        sampleFrame(second, 0L);
        assertEquals(69, terrainHeight(cachedTile(0, 0), 49, 49));
    }

    private static SamplingFixture samplingFixture() throws ReflectiveOperationException {
        FlatStoneChunk chunk = allocate(FlatStoneChunk.class);
        chunk.surfaceHeight = 63;
        chunk.nanosPerColumn = 1_000L;
        chunk.samplingOrder = new ArrayList<>();
        CountingClientChunkCache chunks = allocate(CountingClientChunkCache.class);
        chunks.loadedChunk = chunk;
        chunks.targetChunkX = 3;
        chunks.targetChunkZ = 3;
        chunks.maxChunkX = 7;
        chunks.maxChunkZ = 7;
        CountingClientLevel level = allocate(CountingClientLevel.class);
        level.chunks = chunks;
        return new SamplingFixture(level, chunks, chunk, viewport(64.0D, 64.0D, 64));
    }

    private static BlueprintMapViewport viewport(double centerX, double centerZ, int halfSize) {
        return BlueprintMapViewport.create(0, 0, halfSize, centerX, centerZ, 1.0F);
    }

    private static void sampleFrame(SamplingFixture fixture, long gameTime) {
        fixture.level.gameTime = gameTime;
        BlueprintTerrainRenderer.sampleTerrain(
                fixture.level,
                fixture.viewport,
                () -> fixture.level.nanos + fixture.chunk.nanos
        );
    }

    private static void finishInitialSamplingPass(SamplingFixture fixture) {
        for (int frame = 0; frame < 140; frame++) {
            int sampledColumns = fixture.chunk.sampledColumns;
            sampleFrame(fixture, 0L);
            if (fixture.chunk.sampledColumns == sampledColumns) return;
        }
        throw new AssertionError("initial terrain pass did not settle within the expected number of frames");
    }

    private static Object cachedTile(int minX, int minZ) throws ReflectiveOperationException {
        Map<?, ?> tiles = (Map<?, ?>) readStaticField("tiles");
        for (Object tile : tiles.values()) {
            if ((int) readField(tile, "minX") == minX && (int) readField(tile, "minZ") == minZ) return tile;
        }
        throw new AssertionError("terrain tile was not sampled: " + minX + ", " + minZ);
    }

    private static Object readStaticField(String name) throws ReflectiveOperationException {
        Field field = BlueprintTerrainRenderer.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static Object readField(Object target, String name) throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int terrainHeight(Object tile, int cellX, int cellZ) throws ReflectiveOperationException {
        Method height = tile.getClass().getDeclaredMethod("height", int.class, int.class);
        height.setAccessible(true);
        return (int) height.invoke(tile, cellX, cellZ);
    }

    private static <T> T allocate(Class<T> type) throws ReflectiveOperationException {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class CountingClientLevel extends ClientLevel {
        private CountingClientChunkCache chunks;
        private long gameTime;
        private long nanos;
        private long nanosOnGameTimeRead;

        private CountingClientLevel() {
            super(null, null, null, null, 0, 0, emptyProfiler(), null, false, 0L);
        }

        private static Supplier<ProfilerFiller> emptyProfiler() {
            return () -> null;
        }

        @Override
        public ClientChunkCache getChunkSource() {
            return chunks;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }

        @Override
        public long getGameTime() {
            nanos += nanosOnGameTimeRead;
            return gameTime;
        }
    }

    private static final class CountingClientChunkCache extends ClientChunkCache {
        private int targetChunkLookups;
        private int targetChunkX;
        private int targetChunkZ;
        private int maxChunkX;
        private int maxChunkZ;
        private boolean missingTargetChunk;
        private LevelChunk loadedChunk;

        private CountingClientChunkCache() {
            super(null, 0);
        }

        @Override
        public LevelChunk getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean create) {
            assertFalse(create, "terrain sampling must never request loading a client chunk");
            if (chunkX == targetChunkX && chunkZ == targetChunkZ) {
                targetChunkLookups++;
                return missingTargetChunk ? null : loadedChunk;
            }
            return chunkX >= 0 && chunkX <= maxChunkX && chunkZ >= 0 && chunkZ <= maxChunkZ
                    ? loadedChunk : null;
        }
    }

    private static final class FlatStoneChunk extends LevelChunk {
        private int surfaceHeight;
        private int sampledColumns;
        private long nanos;
        private long nanosPerColumn;
        private ChunkPos lastSampledChunk;
        private List<ChunkPos> samplingOrder;

        private FlatStoneChunk() {
            super((Level) null, new ChunkPos(0, 0));
        }

        @Override
        public int getHeight(Heightmap.Types type, int x, int z) {
            if (type == Heightmap.Types.WORLD_SURFACE) {
                sampledColumns++;
                ChunkPos pos = new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
                if (!pos.equals(lastSampledChunk)) {
                    samplingOrder.add(pos);
                    lastSampledChunk = pos;
                }
            }
            return surfaceHeight;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            nanos += nanosPerColumn;
            return Blocks.STONE.defaultBlockState();
        }
    }

    private static final class SamplingFixture {
        private final CountingClientLevel level;
        private final CountingClientChunkCache chunks;
        private final FlatStoneChunk chunk;
        private BlueprintMapViewport viewport;

        private SamplingFixture(CountingClientLevel level, CountingClientChunkCache chunks,
                                FlatStoneChunk chunk, BlueprintMapViewport viewport) {
            this.level = level;
            this.chunks = chunks;
            this.chunk = chunk;
            this.viewport = viewport;
        }
    }
}
