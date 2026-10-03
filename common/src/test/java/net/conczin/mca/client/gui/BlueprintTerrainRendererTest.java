package net.conczin.mca.client.gui;

import net.minecraft.SharedConstants;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.core.BlockPos;
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

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.BitSet;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintTerrainRendererTest {
    @Test
    void terrainSamplingPublishesWholeChunkSizedUnits() {
        BlueprintTerrainRenderer.SampleRegion first = BlueprintTerrainRenderer.sampleRegionBounds(0);
        BlueprintTerrainRenderer.SampleRegion second = BlueprintTerrainRenderer.sampleRegionBounds(1);

        assertEquals(0, first.minX());
        assertEquals(0, first.minZ());
        assertEquals(16, first.maxX());
        assertEquals(16, first.maxZ());

        assertEquals(16, second.minX());
        assertEquals(0, second.minZ());
        assertEquals(32, second.maxX());
        assertEquals(16, second.maxZ());
    }

    @Test
    void terrainSamplingIncludesOneCellHaloAroundCoreTile() {
        BlueprintTerrainRenderer.SampleRegion west = BlueprintTerrainRenderer.sampleRegionBounds(64);
        BlueprintTerrainRenderer.SampleRegion northWest = BlueprintTerrainRenderer.sampleRegionBounds(96);

        assertEquals(-1, west.minX());
        assertEquals(0, west.minZ());
        assertEquals(0, west.maxX());
        assertEquals(16, west.maxZ());

        assertEquals(-1, northWest.minX());
        assertEquals(-1, northWest.minZ());
        assertEquals(0, northWest.maxX());
        assertEquals(0, northWest.maxZ());
    }

    @Test
    void nearestTerrainChunkIsSelectedBeforeLinearScanOrder() {
        BitSet pending = new BitSet(64);
        pending.set(0, 64);

        assertEquals(63, BlueprintTerrainRenderer.nearestPendingRegion(
                pending, 0, 0, 124.0D, 124.0D));
        assertEquals(0, BlueprintTerrainRenderer.nearestPendingRegion(
                pending, 0, 0, 4.0D, 4.0D));
    }

    @Test
    void visibleCoreTerrainOutranksCloserHaloWork() {
        BitSet pending = new BitSet(97);
        pending.set(0);
        pending.set(64);

        assertEquals(0, BlueprintTerrainRenderer.nearestPendingRegion(
                pending, 0, 0, -1.0D, 8.0D));
    }

    @Test
    void dirtyTextureBoundsOnlyCoverCompletedChunkAndOnePixelBorder() {
        BlueprintTerrainRenderer.SampleRegion dirty = BlueprintTerrainRenderer.dirtyTextureBounds(9);

        assertEquals(15, dirty.minX());
        assertEquals(15, dirty.minZ());
        assertEquals(33, dirty.maxX());
        assertEquals(33, dirty.maxZ());
    }
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void terrainSamplingStepIsBounded() throws Exception {
        CountingClientChunkCache chunks = allocate(CountingClientChunkCache.class);
        CountingClientLevel level = allocate(CountingClientLevel.class);
        level.chunks = chunks;
        Object tile = newTerrainTile();
        Method sample = sampleMethod(tile);

        sample.invoke(tile, level, 0L, 64.0D, 64.0D);

        assertTrue(chunks.lookups > 0, "a sampling step should make progress");
        assertTrue(chunks.lookups <= 512,
                "one render-time sampling step must not traverse the full 130x130 terrain grid");
    }

    @Test
    void terrainSamplingContinuesOnTheFollowingStep() throws Exception {
        CountingClientChunkCache chunks = allocate(CountingClientChunkCache.class);
        CountingClientLevel level = allocate(CountingClientLevel.class);
        level.chunks = chunks;
        Object tile = newTerrainTile();
        Method sample = sampleMethod(tile);

        sample.invoke(tile, level, 0L, 64.0D, 64.0D);
        int firstStepLookups = chunks.lookups;
        sample.invoke(tile, level, 0L, 64.0D, 64.0D);
        int secondStepLookups = chunks.lookups - firstStepLookups;

        assertTrue(secondStepLookups > 0, "unfinished terrain should resume on the next sampling step");
        assertTrue(secondStepLookups <= 512, "the resumed step must obey the same sampling budget");
    }

    @Test
    void missingNearestChunkDoesNotConsumeWholeSamplingStep() throws Exception {
        SamplingFixture fixture = samplingFixture();
        fixture.chunks.missingTargetChunk = true;

        invokeSample(fixture, 0L);

        assertEquals(1, fixture.chunks.targetChunkLookups,
                "the nearest missing chunk should be checked once");
        assertTrue(fixture.chunks.lookups > fixture.chunks.targetChunkLookups,
                "sampling should continue to another ready chunk in the same step");
    }

    @Test
    void incompleteRetryOnlyResamplesCellsThatFailedPreviousPass() throws Exception {
        SamplingFixture fixture = samplingFixture();
        fixture.chunks.missingTargetChunk = true;
        finishInitialSamplingPass(fixture);
        int lookupsBeforeRetry = fixture.chunks.lookups;
        int targetLookupsBeforeRetry = fixture.chunks.targetChunkLookups;

        invokeSample(fixture, 20L);

        int retryLookups = fixture.chunks.lookups - lookupsBeforeRetry;
        int targetRetryLookups = fixture.chunks.targetChunkLookups - targetLookupsBeforeRetry;
        assertEquals(retryLookups, targetRetryLookups,
                "retry work must preserve successful cells instead of restarting the whole tile");
    }

    @Test
    void successfulRetryCompletesTileWithoutFullRescan() throws Exception {
        SamplingFixture fixture = samplingFixture();
        fixture.chunks.missingTargetChunk = true;
        finishInitialSamplingPass(fixture);
        fixture.chunks.missingTargetChunk = false;
        int lookupsBeforeRetry = fixture.chunks.lookups;

        invokeSample(fixture, 20L);

        assertTrue(readBooleanField(fixture.tile, "complete"),
                "a retry should complete once the previously missing cells become available");
        assertEquals(1, fixture.chunks.lookups - lookupsBeforeRetry,
                "recovery should fetch only the previously missing 16x16 chunk once");
    }

    @Test
    void haloRetryRecoversLateNeighborChunkWithoutRescanningCore() throws Exception {
        SamplingFixture fixture = samplingFixture();
        fixture.chunks.targetChunkX = -1;
        fixture.chunks.targetChunkZ = 0;
        fixture.chunks.missingTargetChunk = true;
        finishInitialSamplingPass(fixture);
        fixture.chunks.missingTargetChunk = false;
        int lookupsBeforeRetry = fixture.chunks.lookups;
        int targetLookupsBeforeRetry = fixture.chunks.targetChunkLookups;

        invokeSample(fixture, 20L);

        assertTrue(readBooleanField(fixture.tile, "complete"));
        assertEquals(1, fixture.chunks.lookups - lookupsBeforeRetry);
        assertEquals(1, fixture.chunks.targetChunkLookups - targetLookupsBeforeRetry);
    }

    private static SamplingFixture samplingFixture() throws Exception {
        CountingClientChunkCache chunks = allocate(CountingClientChunkCache.class);
        chunks.loadedChunk = allocate(FlatStoneChunk.class);
        CountingClientLevel level = allocate(CountingClientLevel.class);
        level.chunks = chunks;
        Object tile = newTerrainTile();
        Method sample = sampleMethod(tile);
        return new SamplingFixture(level, chunks, tile, sample);
    }

    private static void finishInitialSamplingPass(SamplingFixture fixture) throws Exception {
        int guard = 140;
        while (readLongField(fixture.tile, "sampledAtGameTime") == Long.MIN_VALUE
                && readLongField(fixture.tile, "retryAfterGameTime") == Long.MIN_VALUE
                && guard-- > 0) {
            invokeSample(fixture, 0L);
        }
        assertTrue(guard > 0, "initial terrain pass should finish within the expected number of batches");
    }

    private static Method sampleMethod(Object tile) throws Exception {
        Method sample = tile.getClass().getDeclaredMethod(
                "sampleNextRegion", ClientLevel.class, long.class, double.class, double.class);
        sample.setAccessible(true);
        return sample;
    }

    private static void invokeSample(SamplingFixture fixture, long gameTime) throws Exception {
        fixture.sample.invoke(fixture.tile, fixture.level, gameTime, 64.0D, 64.0D);
    }

    private static boolean readBooleanField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private static long readLongField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getLong(target);
    }

    private static Object newTerrainTile() throws Exception {
        Class<?> tileType = Class.forName(BlueprintTerrainRenderer.class.getName() + "$TerrainTile");
        Constructor<?> constructor = tileType.getDeclaredConstructor(int.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(0, 0);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return type.cast(((Unsafe) field.get(null)).allocateInstance(type));
    }

    private static final class CountingClientLevel extends ClientLevel {
        private CountingClientChunkCache chunks;

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
    }

    private static final class CountingClientChunkCache extends ClientChunkCache {
        private int lookups;
        private int targetChunkLookups;
        private int targetChunkX = 3;
        private int targetChunkZ = 3;
        private boolean missingTargetChunk;
        private LevelChunk loadedChunk;

        private CountingClientChunkCache() {
            super(null, 0);
        }

        @Override
        public LevelChunk getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean create) {
            lookups++;
            if (chunkX == targetChunkX && chunkZ == targetChunkZ) {
                targetChunkLookups++;
                if (missingTargetChunk) return null;
            }
            return loadedChunk;
        }
    }

    private static final class FlatStoneChunk extends LevelChunk {
        private FlatStoneChunk() {
            super((Level) null, new ChunkPos(0, 0));
        }

        @Override
        public int getHeight(Heightmap.Types type, int x, int z) {
            return 63;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return Blocks.STONE.defaultBlockState();
        }
    }

    private record SamplingFixture(
            CountingClientLevel level,
            CountingClientChunkCache chunks,
            Object tile,
            Method sample) {
    }
}
