package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

/** Owns world-derived Blueprint terrain sampling, texture creation and cache lifecycle. */
final class BlueprintTerrainRenderer implements AutoCloseable {
    private static final int SAMPLE_STEP = 1;
    private static final int TILE_BLOCK_SIZE = 128;
    private static final int SAMPLE_SLICE_BLOCK_SIZE = 16;
    private static final int SAMPLE_SLICES_PER_AXIS = TILE_BLOCK_SIZE / SAMPLE_SLICE_BLOCK_SIZE;
    private static final int SAMPLE_SLICE_COUNT = SAMPLE_SLICES_PER_AXIS * SAMPLE_SLICES_PER_AXIS;
    private static final int MAX_CACHED_TILES = 96;
    private static final long TERRAIN_SAMPLE_BUDGET_NANOS = 1_500_000L;
    private static final int TEXTURE_REFRESH_SLICE_INTERVAL = 4;
    private static final long INCOMPLETE_TILE_RETRY_TICKS = 20L;
    private static final long TERRAIN_SLICE_STALE_TICKS = 100L;
    private static final int TERRAIN_ALPHA = 0xff;
    private static final int CONTOUR_COLOR = 0x66000000;
    private static final int CONTOUR_INTERVAL = 4;
    private static final float BASE_BRIGHTNESS = MapColor.Brightness.NORMAL.modifier / 255.0f;
    private static final float SLOPE_BRIGHTNESS_PER_BLOCK = 0.055f;
    private static final float MIN_BRIGHTNESS = 0.58f;
    private static final float MAX_BRIGHTNESS = 1.15f;
    private static final float WATER_BLEND = 0.80f;
    private static final int NO_WATER_TINT = -1;

    private final LinkedHashMap<TileKey, TerrainTile> tiles = new LinkedHashMap<>(16, 0.75f, true);

    void render(GuiGraphics context, BlueprintMapViewport viewport) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        int centerBlockX = (int) Math.floor(viewport.mapCenterX());
        int centerBlockZ = (int) Math.floor(viewport.mapCenterZ());
        int radius = Math.max(1, (int) Math.ceil((viewport.halfSize() - 1) / viewport.scale()) + 1);
        int sampleStep = sampleStep();
        int visibleMinX = centerBlockX - radius;
        int visibleMaxX = centerBlockX + radius;
        int visibleMinZ = centerBlockZ - radius;
        int visibleMaxZ = centerBlockZ + radius;
        int samplingMinX = visibleMinX - TILE_BLOCK_SIZE;
        int samplingMaxX = visibleMaxX + TILE_BLOCK_SIZE;
        int samplingMinZ = visibleMinZ - TILE_BLOCK_SIZE;
        int samplingMaxZ = visibleMaxZ + TILE_BLOCK_SIZE;

        long gameTime = minecraft.level.getGameTime();
        LoadedChunkLookup loadedChunks = (chunkX, chunkZ) -> minecraft.level.getChunkSource()
                .getChunk(chunkX, chunkZ, ChunkStatus.FULL, false) != null;
        List<TerrainTile> visibleTiles = new ArrayList<>();
        TileKey visibleSamplingKey = null;
        TerrainTile visibleSamplingTile = null;
        double visibleSamplingDistanceSq = Double.POSITIVE_INFINITY;
        TileKey prefetchSamplingKey = null;
        TerrainTile prefetchSamplingTile = null;
        double prefetchSamplingDistanceSq = Double.POSITIVE_INFINITY;

        for (int minX = tileMin(samplingMinX); minX <= samplingMaxX; minX += TILE_BLOCK_SIZE) {
            for (int minZ = tileMin(samplingMinZ); minZ <= samplingMaxZ; minZ += TILE_BLOCK_SIZE) {
                int samplingBand = terrainSamplingBand(
                        minX, minZ, visibleMinX, visibleMaxX, visibleMinZ, visibleMaxZ);
                if (samplingBand > 1) continue;

                TileKey key = new TileKey(minX, minZ);
                TerrainTile tile = tiles.get(key);
                if (samplingBand == 0 && tile != null) {
                    visibleTiles.add(tile);
                }

                boolean hasReadySlice = tile == null
                        ? tileTouchesLoadedChunk(minX, minZ, loadedChunks)
                        : tile.hasReadySlice(gameTime, viewport.mapCenterX(), viewport.mapCenterZ(), loadedChunks);
                if (hasReadySlice) {
                    double distanceSq = tileDistanceSq(minX, minZ, viewport.mapCenterX(), viewport.mapCenterZ());
                    if (samplingBand == 0 && distanceSq < visibleSamplingDistanceSq) {
                        visibleSamplingDistanceSq = distanceSq;
                        visibleSamplingKey = key;
                        visibleSamplingTile = tile;
                    } else if (samplingBand == 1 && distanceSq < prefetchSamplingDistanceSq) {
                        prefetchSamplingDistanceSq = distanceSq;
                        prefetchSamplingKey = key;
                        prefetchSamplingTile = tile;
                    }
                }
            }
        }

        if (visibleSamplingKey != null || prefetchSamplingKey != null) {
            TileSamplingBudget samplingBudget = new TileSamplingBudget();
            if (visibleSamplingKey != null) {
                if (visibleSamplingTile == null) {
                    visibleSamplingTile = new TerrainTile(
                            visibleSamplingKey.minX(), visibleSamplingKey.minZ(), sampleStep);
                    tiles.put(visibleSamplingKey, visibleSamplingTile);
                    visibleTiles.add(visibleSamplingTile);
                }
                while (samplingBudget.tryAcquire()) {
                    if (!visibleSamplingTile.sampleNextSlice(
                            minecraft.level, viewport.mapCenterX(), viewport.mapCenterZ(), gameTime, loadedChunks)) {
                        break;
                    }
                }
            }

            if (prefetchSamplingKey != null) {
                if (prefetchSamplingTile == null) {
                    prefetchSamplingTile = new TerrainTile(
                            prefetchSamplingKey.minX(), prefetchSamplingKey.minZ(), sampleStep);
                    tiles.put(prefetchSamplingKey, prefetchSamplingTile);
                }
                while (samplingBudget.tryAcquire()) {
                    if (!prefetchSamplingTile.sampleNextSlice(
                            minecraft.level, viewport.mapCenterX(), viewport.mapCenterZ(), gameTime, loadedChunks)) {
                        break;
                    }
                }
            }
        }

        for (TerrainTile tile : visibleTiles) {
            renderTile(context, tile);
        }
        trimCache();
    }

    static int tileMin(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, TILE_BLOCK_SIZE) * TILE_BLOCK_SIZE;
    }

    static int sampleStep() {
        return SAMPLE_STEP;
    }

    static boolean hasSampledTerrain(int sampledSliceCount) {
        return sampledSliceCount > 0;
    }

    static boolean shouldRefreshTexture(int sampledSliceCount) {
        return sampledSliceCount == 1
                || sampledSliceCount == SAMPLE_SLICE_COUNT
                || sampledSliceCount % TEXTURE_REFRESH_SLICE_INTERVAL == 0;
    }

    static boolean shouldRefreshTexture(int sampledSliceCount, boolean hasMoreReadySlices) {
        return shouldRefreshTexture(sampledSliceCount) || !hasMoreReadySlices;
    }

    static double tileDistanceSq(int minX, int minZ, double focusX, double focusZ) {
        double deltaX = minX + TILE_BLOCK_SIZE / 2.0D - focusX;
        double deltaZ = minZ + TILE_BLOCK_SIZE / 2.0D - focusZ;
        return deltaX * deltaX + deltaZ * deltaZ;
    }

    static int terrainSamplingBand(int minX,
                                   int minZ,
                                   int visibleMinX,
                                   int visibleMaxX,
                                   int visibleMinZ,
                                   int visibleMaxZ) {
        int maxX = minX + TILE_BLOCK_SIZE - 1;
        int maxZ = minZ + TILE_BLOCK_SIZE - 1;
        boolean visible = minX <= visibleMaxX && maxX >= visibleMinX
                && minZ <= visibleMaxZ && maxZ >= visibleMinZ;
        if (visible) return 0;

        boolean prefetched = minX <= visibleMaxX + TILE_BLOCK_SIZE
                && maxX >= visibleMinX - TILE_BLOCK_SIZE
                && minZ <= visibleMaxZ + TILE_BLOCK_SIZE
                && maxZ >= visibleMinZ - TILE_BLOCK_SIZE;
        return prefetched ? 1 : 2;
    }

    static boolean tileTouchesLoadedChunk(int minX, int minZ, LoadedChunkLookup loadedChunks) {
        int minChunkX = SectionPos.blockToSectionCoord(minX);
        int maxChunkX = SectionPos.blockToSectionCoord(minX + TILE_BLOCK_SIZE - 1);
        int minChunkZ = SectionPos.blockToSectionCoord(minZ);
        int maxChunkZ = SectionPos.blockToSectionCoord(minZ + TILE_BLOCK_SIZE - 1);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (loadedChunks.test(chunkX, chunkZ)) return true;
            }
        }
        return false;
    }

    static SliceBounds sampleSliceBounds(int sliceIndex) {
        if (sliceIndex < 0 || sliceIndex >= SAMPLE_SLICE_COUNT) {
            throw new IllegalArgumentException("sliceIndex out of range: " + sliceIndex);
        }
        int sliceX = sliceIndex % SAMPLE_SLICES_PER_AXIS;
        int sliceZ = sliceIndex / SAMPLE_SLICES_PER_AXIS;
        int minLocalX = sliceX * SAMPLE_SLICE_BLOCK_SIZE;
        int minLocalZ = sliceZ * SAMPLE_SLICE_BLOCK_SIZE;
        return new SliceBounds(
                minLocalX,
                minLocalX + SAMPLE_SLICE_BLOCK_SIZE,
                minLocalZ,
                minLocalZ + SAMPLE_SLICE_BLOCK_SIZE);
    }

    static boolean sliceNeedsSampling(long sampledAtGameTime, long retryAfterGameTime, long gameTime) {
        if (retryAfterGameTime != Long.MIN_VALUE && gameTime < retryAfterGameTime) {
            return false;
        }
        return sampledAtGameTime == Long.MIN_VALUE
                || gameTime - sampledAtGameTime >= TERRAIN_SLICE_STALE_TICKS;
    }

    static void recordSliceSampleResult(long[] sampledAtGameTimes,
                                        long[] retryAfterGameTimes,
                                        int sliceIndex,
                                        long gameTime,
                                        boolean success) {
        if (success) {
            sampledAtGameTimes[sliceIndex] = gameTime;
            retryAfterGameTimes[sliceIndex] = Long.MIN_VALUE;
        } else {
            retryAfterGameTimes[sliceIndex] = gameTime + INCOMPLETE_TILE_RETRY_TICKS;
        }
    }

    static int nearestReadySlice(int tileMinX,
                                 int tileMinZ,
                                 long[] sampledAtGameTimes,
                                 long[] retryAfterGameTimes,
                                 long gameTime,
                                 double focusX,
                                 double focusZ,
                                 LoadedChunkLookup loadedChunks) {
        if (sampledAtGameTimes.length != SAMPLE_SLICE_COUNT
                || retryAfterGameTimes.length != SAMPLE_SLICE_COUNT) {
            throw new IllegalArgumentException("expected " + SAMPLE_SLICE_COUNT + " slice states");
        }

        long candidates = 0L;
        for (int sliceIndex = 0; sliceIndex < SAMPLE_SLICE_COUNT; sliceIndex++) {
            if (sliceNeedsSampling(sampledAtGameTimes[sliceIndex], retryAfterGameTimes[sliceIndex], gameTime)) {
                candidates |= 1L << sliceIndex;
            }
        }

        while (candidates != 0L) {
            int nearest = -1;
            double nearestDistanceSq = Double.POSITIVE_INFINITY;
            for (int sliceIndex = 0; sliceIndex < SAMPLE_SLICE_COUNT; sliceIndex++) {
                if ((candidates & (1L << sliceIndex)) == 0L) continue;

                int sliceX = sliceIndex % SAMPLE_SLICES_PER_AXIS;
                int sliceZ = sliceIndex / SAMPLE_SLICES_PER_AXIS;
                double centerX = tileMinX + sliceX * SAMPLE_SLICE_BLOCK_SIZE
                        + SAMPLE_SLICE_BLOCK_SIZE / 2.0D;
                double centerZ = tileMinZ + sliceZ * SAMPLE_SLICE_BLOCK_SIZE
                        + SAMPLE_SLICE_BLOCK_SIZE / 2.0D;
                double deltaX = centerX - focusX;
                double deltaZ = centerZ - focusZ;
                double distanceSq = deltaX * deltaX + deltaZ * deltaZ;
                if (distanceSq < nearestDistanceSq) {
                    nearestDistanceSq = distanceSq;
                    nearest = sliceIndex;
                }
            }

            candidates &= ~(1L << nearest);
            int sliceX = nearest % SAMPLE_SLICES_PER_AXIS;
            int sliceZ = nearest / SAMPLE_SLICES_PER_AXIS;
            int chunkX = SectionPos.blockToSectionCoord(tileMinX + sliceX * SAMPLE_SLICE_BLOCK_SIZE);
            int chunkZ = SectionPos.blockToSectionCoord(tileMinZ + sliceZ * SAMPLE_SLICE_BLOCK_SIZE);
            if (loadedChunks.test(chunkX, chunkZ)) return nearest;
        }

        return -1;
    }

    static SliceBounds dirtyTextureBounds(int sliceIndex) {
        SliceBounds slice = sampleSliceBounds(sliceIndex);
        return new SliceBounds(
                Math.max(0, slice.minLocalX() - 1),
                Math.min(TILE_BLOCK_SIZE, slice.maxLocalX() + 1),
                Math.max(0, slice.minLocalZ() - 1),
                Math.min(TILE_BLOCK_SIZE, slice.maxLocalZ() + 1));
    }

    private void renderTile(GuiGraphics context, TerrainTile tile) {
        if ((tile.textureDirty || tile.terrainTextureLocation == null)
                && hasSampledTerrain(tile.sampledSliceCount)) {
            updateTexture(tile);
            tile.textureDirty = false;
        }
        if (tile.terrainTextureLocation == null) return;

        context.blit(tile.terrainTextureLocation, tile.minX, tile.minZ,
                TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, 0.0F, 0.0F,
                TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, TILE_BLOCK_SIZE);
    }

    private void updateTexture(TerrainTile tile) {
        if (tile.terrainTexture == null) {
            NativeImage terrainImage = new NativeImage(TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, true);
            for (int sliceIndex = 0; sliceIndex < SAMPLE_SLICE_COUNT; sliceIndex++) {
                if (!tile.dirtySlices[sliceIndex]) continue;
                updatePixels(tile, terrainImage, dirtyTextureBounds(sliceIndex));
            }

            DynamicTexture terrainTexture = new DynamicTexture(terrainImage);
            terrainTexture.setFilter(false, false);
            tile.terrainTexture = terrainTexture;
            tile.terrainTextureLocation = Minecraft.getInstance().getTextureManager()
                    .register("mca_blueprint_terrain", terrainTexture);
            Arrays.fill(tile.dirtySlices, false);
            return;
        }

        NativeImage terrainImage = tile.terrainTexture.getPixels();
        if (terrainImage == null) return;

        tile.terrainTexture.bind();
        for (int sliceIndex = 0; sliceIndex < SAMPLE_SLICE_COUNT; sliceIndex++) {
            if (!tile.dirtySlices[sliceIndex]) continue;

            SliceBounds dirty = dirtyTextureBounds(sliceIndex);
            updatePixels(tile, terrainImage, dirty);
            terrainImage.upload(
                    0,
                    dirty.minLocalX(), dirty.minLocalZ(),
                    dirty.minLocalX(), dirty.minLocalZ(),
                    dirty.maxLocalX() - dirty.minLocalX(),
                    dirty.maxLocalZ() - dirty.minLocalZ(),
                    false, false, false, false);
            tile.dirtySlices[sliceIndex] = false;
        }
    }

    private static void updatePixels(TerrainTile tile, NativeImage terrainImage, SliceBounds dirty) {
        int firstCellX = TerrainTile.firstCell(tile.minX, tile.sampleStep);
        int firstCellZ = TerrainTile.firstCell(tile.minZ, tile.sampleStep);

        for (int pixelX = dirty.minLocalX(); pixelX < dirty.maxLocalX(); pixelX++) {
            for (int pixelZ = dirty.minLocalZ(); pixelZ < dirty.maxLocalZ(); pixelZ++) {
                int blockX = tile.minX + pixelX;
                int blockZ = tile.minZ + pixelZ;
                int cellX = Math.floorDiv(blockX - firstCellX, tile.sampleStep);
                int cellZ = Math.floorDiv(blockZ - firstCellZ, tile.sampleStep);

                if (!tile.isCellValid(cellX, cellZ)) {
                    terrainImage.setPixelRGBA(pixelX, pixelZ, 0);
                    continue;
                }

                int height = tile.height(cellX, cellZ);
                int northHeight = tile.heightAt(cellX, cellZ - 1, height);
                int southHeight = tile.heightAt(cellX, cellZ + 1, height);
                int westHeight = tile.heightAt(cellX - 1, cellZ, height);
                int eastHeight = tile.heightAt(cellX + 1, cellZ, height);
                float brightness = hillshadeBrightness(westHeight, eastHeight, northHeight, southHeight);
                boolean northContour = Math.floorDiv(height, CONTOUR_INTERVAL)
                        != Math.floorDiv(northHeight, CONTOUR_INTERVAL);
                boolean westContour = Math.floorDiv(height, CONTOUR_INTERVAL)
                        != Math.floorDiv(westHeight, CONTOUR_INTERVAL);

                int color = composeTerrainAndWater(
                        tile.terrainColor(cellX, cellZ), tile.waterTint(cellX, cellZ), brightness,
                        northContour || westContour);
                terrainImage.setPixelRGBA(pixelX, pixelZ, FastColor.ABGR32.fromArgb32(color));
            }
        }
    }

    private static int blendContour(int baseColor) {
        int overlayAlpha = (CONTOUR_COLOR >>> 24) & 0xff;
        int inverseAlpha = 255 - overlayAlpha;
        int red = ((((CONTOUR_COLOR >> 16) & 0xff) * overlayAlpha)
                + (((baseColor >> 16) & 0xff) * inverseAlpha)) / 255;
        int green = ((((CONTOUR_COLOR >> 8) & 0xff) * overlayAlpha)
                + (((baseColor >> 8) & 0xff) * inverseAlpha)) / 255;
        int blue = (((CONTOUR_COLOR & 0xff) * overlayAlpha)
                + ((baseColor & 0xff) * inverseAlpha)) / 255;
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    static float hillshadeBrightness(int westHeight, int eastHeight, int northHeight, int southHeight) {
        float slopeDelta = ((westHeight - eastHeight) + (northHeight - southHeight)) * 0.25f;
        float brightness = BASE_BRIGHTNESS + slopeDelta * SLOPE_BRIGHTNESS_PER_BLOCK;
        return Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, brightness));
    }

    static int multiplyTint(int baseColor, int tintColor) {
        int red = (((baseColor >> 16) & 0xff) * ((tintColor >> 16) & 0xff)) / 255;
        int green = (((baseColor >> 8) & 0xff) * ((tintColor >> 8) & 0xff)) / 255;
        int blue = ((baseColor & 0xff) * (tintColor & 0xff)) / 255;
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    static int waterColor(int groundColor, int biomeWaterColor) {
        return blendOpaque(groundColor, biomeWaterColor, WATER_BLEND);
    }

    static int composeTerrainAndWater(int terrainColor,
                                      int biomeWaterColor,
                                      float brightness,
                                      boolean contour) {
        int color = shadeColor(terrainColor, brightness);
        if (contour) {
            color = blendContour(color);
        }
        return biomeWaterColor == NO_WATER_TINT
                ? color
                : waterColor(color, biomeWaterColor);
    }

    @SuppressWarnings("deprecation")
    static ColumnLayers sampleClientOceanFloorColumn(IntFunction<BlockState> stateAtY,
                                                      IntPredicate sectionMayContainFloor,
                                                      int waterY,
                                                      int minY) {
        int y = waterY - 1;
        while (y >= minY) {
            int sectionMinY = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(y));
            if (!sectionMayContainFloor.test(y)) {
                y = sectionMinY - 1;
                continue;
            }

            int scanMinY = Math.max(minY, sectionMinY);
            while (y >= scanMinY) {
                BlockState state = stateAtY.apply(y);
                if (state != null && state.blocksMotion()) {
                    return new ColumnLayers(y, waterY, state);
                }
                y--;
            }
        }
        return null;
    }

    @SuppressWarnings("deprecation")
    static int sampleClientDryTerrainHeight(IntFunction<BlockState> stateAtY,
                                            IntPredicate sectionMayContainBlocking,
                                            Predicate<BlockState> isLeaf,
                                            int motionBlockingHeight,
                                            int surfaceHeight,
                                            int minY) {
        int y = motionBlockingHeight - 1;
        while (y >= minY) {
            int sectionMinY = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(y));
            if (!sectionMayContainBlocking.test(y)) {
                y = sectionMinY - 1;
                continue;
            }

            int scanMinY = Math.max(minY, sectionMinY);
            while (y >= scanMinY) {
                BlockState state = stateAtY.apply(y);
                if (state != null
                        && !isLeaf.test(state)
                        && (state.blocksMotion() || !state.getFluidState().isEmpty())) {
                    return y + 1;
                }
                y--;
            }
        }
        return surfaceHeight;
    }

    private static boolean isWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER)
                || state.getFluidState().is(Fluids.WATER)
                || state.getFluidState().is(Fluids.FLOWING_WATER);
    }

    private static int blendOpaque(int baseColor, int overlayColor, float opacity) {
        float inverse = 1.0f - opacity;
        int red = Math.round(((baseColor >> 16) & 0xff) * inverse
                + ((overlayColor >> 16) & 0xff) * opacity);
        int green = Math.round(((baseColor >> 8) & 0xff) * inverse
                + ((overlayColor >> 8) & 0xff) * opacity);
        int blue = Math.round((baseColor & 0xff) * inverse
                + (overlayColor & 0xff) * opacity);
        return 0xff000000 | (red << 16) | (green << 8) | blue;
    }

    private static int shadeColor(int baseColor, float brightness) {
        int red = Math.min(255, Math.round(((baseColor >> 16) & 0xff) * brightness));
        int green = Math.min(255, Math.round(((baseColor >> 8) & 0xff) * brightness));
        int blue = Math.min(255, Math.round((baseColor & 0xff) * brightness));
        return (TERRAIN_ALPHA << 24) | (red << 16) | (green << 8) | blue;
    }

    private void trimCache() {
        Iterator<Map.Entry<TileKey, TerrainTile>> iterator = tiles.entrySet().iterator();
        while (tiles.size() > MAX_CACHED_TILES && iterator.hasNext()) {
            Map.Entry<TileKey, TerrainTile> eldest = iterator.next();
            releaseTexture(eldest.getValue());
            iterator.remove();
        }
    }

    private static void releaseTexture(TerrainTile tile) {
        if (tile.terrainTextureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(tile.terrainTextureLocation);
            tile.terrainTextureLocation = null;
            tile.terrainTexture = null;
        }
    }

    @Override
    public void close() {
        tiles.values().forEach(BlueprintTerrainRenderer::releaseTexture);
        tiles.clear();
    }

    private record TileKey(int minX, int minZ) {
    }

    @FunctionalInterface
    interface LoadedChunkLookup {
        boolean test(int chunkX, int chunkZ);
    }

    record SliceBounds(int minLocalX, int maxLocalX, int minLocalZ, int maxLocalZ) {
    }

    static final class TileSamplingBudget {
        private final LongSupplier nanoTime;
        private final long deadlineNanos;
        private boolean firstSample = true;

        TileSamplingBudget() {
            this(System::nanoTime, TERRAIN_SAMPLE_BUDGET_NANOS);
        }

        private TileSamplingBudget(LongSupplier nanoTime, long budgetNanos) {
            this.nanoTime = nanoTime;
            this.deadlineNanos = nanoTime.getAsLong() + budgetNanos;
        }

        boolean tryAcquire() {
            if (firstSample) {
                firstSample = false;
                return true;
            }
            return nanoTime.getAsLong() < deadlineNanos;
        }
    }

    record ColumnLayers(int terrainY, int waterY, BlockState terrainState) {
    }

    private static final class TerrainTile {
        private static final int FALLBACK_COLOR = 0x6f766f;

        private final int minX;
        private final int minZ;
        private final int sampleStep;
        private final int xCellCount;
        private final int zCellCount;
        private final int[] heights;
        private final int[] terrainColors;
        private final int[] waterTints;
        private final boolean[] validCells;
        private final long[] sampledAtGameTimes = new long[SAMPLE_SLICE_COUNT];
        private final long[] retryAfterGameTimes = new long[SAMPLE_SLICE_COUNT];
        private final boolean[] dirtySlices = new boolean[SAMPLE_SLICE_COUNT];
        private int sampledSliceCount;
        private boolean textureDirty;
        private DynamicTexture terrainTexture;
        private ResourceLocation terrainTextureLocation;

        private TerrainTile(int minX, int minZ, int sampleStep) {
            this.minX = minX;
            this.minZ = minZ;
            this.sampleStep = sampleStep;

            int firstCellX = firstCell(minX, sampleStep);
            int firstCellZ = firstCell(minZ, sampleStep);
            int lastCellX = Math.floorDiv(maxX() - 1, sampleStep) * sampleStep + sampleStep;
            int lastCellZ = Math.floorDiv(maxZ() - 1, sampleStep) * sampleStep + sampleStep;
            this.xCellCount = (lastCellX - firstCellX) / sampleStep + 1;
            this.zCellCount = (lastCellZ - firstCellZ) / sampleStep + 1;
            int cellCount = xCellCount * zCellCount;
            this.heights = new int[cellCount];
            this.terrainColors = new int[cellCount];
            this.waterTints = new int[cellCount];
            this.validCells = new boolean[cellCount];
            Arrays.fill(sampledAtGameTimes, Long.MIN_VALUE);
            Arrays.fill(retryAfterGameTimes, Long.MIN_VALUE);
        }

        private int maxX() {
            return minX + TILE_BLOCK_SIZE;
        }

        private int maxZ() {
            return minZ + TILE_BLOCK_SIZE;
        }

        private boolean hasReadySlice(long gameTime,
                                      double focusX,
                                      double focusZ,
                                      LoadedChunkLookup loadedChunks) {
            return nearestReadySlice(
                    minX, minZ, sampledAtGameTimes, retryAfterGameTimes,
                    gameTime, focusX, focusZ, loadedChunks) >= 0;
        }

        private int heightAt(int x, int z, int fallbackHeight) {
            return isCellValid(x, z) ? heights[cellIndex(x, z)] : fallbackHeight;
        }

        private boolean isCellValid(int x, int z) {
            return x >= 0 && z >= 0 && x < xCellCount && z < zCellCount && validCells[cellIndex(x, z)];
        }

        private int height(int x, int z) {
            return heights[cellIndex(x, z)];
        }

        private int terrainColor(int x, int z) {
            return terrainColors[cellIndex(x, z)];
        }

        private int waterTint(int x, int z) {
            return waterTints[cellIndex(x, z)];
        }

        private int cellIndex(int x, int z) {
            return x * zCellCount + z;
        }

        private void clearCell(int x, int z) {
            validCells[cellIndex(x, z)] = false;
        }

        private void setCell(int x, int z, int height, int terrainColor, int waterTint) {
            int index = cellIndex(x, z);
            heights[index] = height;
            terrainColors[index] = terrainColor;
            waterTints[index] = waterTint;
            validCells[index] = true;
        }

        private static int firstCell(int tileMin, int sampleStep) {
            return Math.floorDiv(tileMin, sampleStep) * sampleStep - sampleStep;
        }

        private boolean sampleNextSlice(ClientLevel level,
                                        double focusX,
                                        double focusZ,
                                        long gameTime,
                                        LoadedChunkLookup loadedChunks) {
            int sliceIndex = nearestReadySlice(
                    minX, minZ, sampledAtGameTimes, retryAfterGameTimes,
                    gameTime, focusX, focusZ, loadedChunks);
            if (sliceIndex < 0) return false;

            SliceBounds bounds = sampleSliceBounds(sliceIndex);
            int sliceChunkX = SectionPos.blockToSectionCoord(minX + bounds.minLocalX());
            int sliceChunkZ = SectionPos.blockToSectionCoord(minZ + bounds.minLocalZ());
            LevelChunk sliceChunk = level.getChunkSource().getChunk(
                    sliceChunkX, sliceChunkZ, ChunkStatus.FULL, false);
            if (sliceChunk == null) return false;
            int minBuildHeight = level.getMinBuildHeight();
            int firstCellX = firstCell(minX, sampleStep);
            int firstCellZ = firstCell(minZ, sampleStep);
            BlockPos.MutableBlockPos surfacePos = new BlockPos.MutableBlockPos();
            boolean samplingSucceeded = true;

            for (int localX = bounds.minLocalX(); localX < bounds.maxLocalX(); localX++) {
                for (int localZ = bounds.minLocalZ(); localZ < bounds.maxLocalZ(); localZ++) {
                    samplingSucceeded &= sampleCell(
                            level, firstCellX, firstCellZ, localX + 1, localZ + 1,
                            minBuildHeight, surfacePos, sliceChunkX, sliceChunkZ, sliceChunk);
                }
            }

            if (samplingSucceeded && bounds.minLocalX() == 0) {
                for (int localZ = bounds.minLocalZ(); localZ < bounds.maxLocalZ(); localZ++) {
                    sampleCell(level, firstCellX, firstCellZ, 0, localZ + 1, minBuildHeight, surfacePos,
                            sliceChunkX, sliceChunkZ, sliceChunk);
                }
            }
            if (samplingSucceeded && bounds.maxLocalX() == TILE_BLOCK_SIZE) {
                int eastCellX = xCellCount - 1;
                for (int localZ = bounds.minLocalZ(); localZ < bounds.maxLocalZ(); localZ++) {
                    sampleCell(level, firstCellX, firstCellZ, eastCellX, localZ + 1,
                            minBuildHeight, surfacePos, sliceChunkX, sliceChunkZ, sliceChunk);
                }
            }
            if (samplingSucceeded && bounds.minLocalZ() == 0) {
                for (int localX = bounds.minLocalX(); localX < bounds.maxLocalX(); localX++) {
                    sampleCell(level, firstCellX, firstCellZ, localX + 1, 0, minBuildHeight, surfacePos,
                            sliceChunkX, sliceChunkZ, sliceChunk);
                }
            }
            if (samplingSucceeded && bounds.maxLocalZ() == TILE_BLOCK_SIZE) {
                int southCellZ = zCellCount - 1;
                for (int localX = bounds.minLocalX(); localX < bounds.maxLocalX(); localX++) {
                    sampleCell(level, firstCellX, firstCellZ, localX + 1, southCellZ,
                            minBuildHeight, surfacePos, sliceChunkX, sliceChunkZ, sliceChunk);
                }
            }

            if (samplingSucceeded && bounds.minLocalX() == 0 && bounds.minLocalZ() == 0) {
                sampleCell(level, firstCellX, firstCellZ, 0, 0, minBuildHeight, surfacePos,
                        sliceChunkX, sliceChunkZ, sliceChunk);
            }
            if (samplingSucceeded && bounds.minLocalX() == 0 && bounds.maxLocalZ() == TILE_BLOCK_SIZE) {
                sampleCell(level, firstCellX, firstCellZ, 0, zCellCount - 1, minBuildHeight, surfacePos,
                        sliceChunkX, sliceChunkZ, sliceChunk);
            }
            if (samplingSucceeded && bounds.maxLocalX() == TILE_BLOCK_SIZE && bounds.minLocalZ() == 0) {
                sampleCell(level, firstCellX, firstCellZ, xCellCount - 1, 0, minBuildHeight, surfacePos,
                        sliceChunkX, sliceChunkZ, sliceChunk);
            }
            if (samplingSucceeded
                    && bounds.maxLocalX() == TILE_BLOCK_SIZE
                    && bounds.maxLocalZ() == TILE_BLOCK_SIZE) {
                sampleCell(level, firstCellX, firstCellZ, xCellCount - 1, zCellCount - 1,
                        minBuildHeight, surfacePos, sliceChunkX, sliceChunkZ, sliceChunk);
            }

            boolean wasPending = sampledAtGameTimes[sliceIndex] == Long.MIN_VALUE;
            recordSliceSampleResult(
                    sampledAtGameTimes, retryAfterGameTimes, sliceIndex, gameTime, samplingSucceeded);
            if (samplingSucceeded) {
                if (wasPending) sampledSliceCount++;
                dirtySlices[sliceIndex] = true;
                boolean regularRefresh = shouldRefreshTexture(sampledSliceCount);
                boolean hasMoreReadySlices = regularRefresh || nearestReadySlice(
                        minX, minZ, sampledAtGameTimes, retryAfterGameTimes,
                        gameTime, focusX, focusZ, loadedChunks) >= 0;
                textureDirty |= shouldRefreshTexture(sampledSliceCount, hasMoreReadySlices);
            }
            return true;
        }

        @SuppressWarnings("deprecation")
        private boolean sampleCell(ClientLevel level,
                                   int firstCellX,
                                   int firstCellZ,
                                   int cellX,
                                   int cellZ,
                                   int minBuildHeight,
                                   BlockPos.MutableBlockPos surfacePos,
                                   int sliceChunkX,
                                   int sliceChunkZ,
                                   LevelChunk sliceChunk) {
            int x = firstCellX + cellX * sampleStep;
            int z = firstCellZ + cellZ * sampleStep;
            int sampleX = x + sampleStep / 2;
            int sampleZ = z + sampleStep / 2;

            int sampleChunkX = SectionPos.blockToSectionCoord(sampleX);
            int sampleChunkZ = SectionPos.blockToSectionCoord(sampleZ);
            LevelChunk chunk = sampleChunkX == sliceChunkX && sampleChunkZ == sliceChunkZ
                    ? sliceChunk
                    : level.getChunkSource().getChunk(sampleChunkX, sampleChunkZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                return false;
            }

            int surfaceHeight = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ) + 1;
            if (surfaceHeight <= minBuildHeight) {
                clearCell(cellX, cellZ);
                return true;
            }

            surfacePos.set(sampleX, surfaceHeight - 1, sampleZ);
            BlockState surfaceState = chunk.getBlockState(surfacePos);
            boolean waterColumn = isWater(surfaceState);
            MapColor mapColor = surfaceState.getMapColor(level, surfacePos);
            while (mapColor == MapColor.NONE && surfacePos.getY() > minBuildHeight) {
                surfacePos.move(0, -1, 0);
                surfaceState = chunk.getBlockState(surfacePos);
                mapColor = surfaceState.getMapColor(level, surfacePos);
            }

            int terrainHeight = surfacePos.getY() + 1;
            if (!waterColumn) {
                int colorSampleY = surfacePos.getY();
                terrainHeight = sampleClientDryTerrainHeight(
                        y -> {
                            surfacePos.set(sampleX, y, sampleZ);
                            return chunk.getBlockState(surfacePos);
                        },
                        y -> {
                            int sectionIndex = chunk.getSectionIndex(y);
                            return sectionIndex >= 0
                                    && sectionIndex < chunk.getSectionsCount()
                                    && chunk.getSection(sectionIndex).maybeHas(candidate ->
                                    !candidate.is(BlockTags.LEAVES)
                                            && (candidate.blocksMotion() || !candidate.getFluidState().isEmpty()));
                        },
                        state -> state.is(BlockTags.LEAVES),
                        chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, sampleX, sampleZ) + 1,
                        terrainHeight,
                        minBuildHeight);
                surfacePos.set(sampleX, colorSampleY, sampleZ);
            }
            int baseColor = mapColor == MapColor.NONE ? FALLBACK_COLOR : mapColor.col;
            baseColor = biomeTintedColor(level, surfacePos, mapColor, baseColor);

            int terrainColor = baseColor;
            int waterTint = NO_WATER_TINT;
            if (waterColumn) {
                BlockPos waterPos = new BlockPos(sampleX, surfaceHeight - 1, sampleZ);
                waterTint = unblendedBiomeColor(level, waterPos, BiomeColors.WATER_COLOR_RESOLVER);
            }
            ColumnLayers layers = waterColumn
                    ? sampleClientOceanFloorColumn(
                            y -> {
                                surfacePos.set(sampleX, y, sampleZ);
                                return chunk.getBlockState(surfacePos);
                            },
                            y -> {
                                int sectionIndex = chunk.getSectionIndex(y);
                                return sectionIndex >= 0
                                        && sectionIndex < chunk.getSectionsCount()
                                        && chunk.getSection(sectionIndex)
                                        .maybeHas(candidate -> candidate.blocksMotion());
                            },
                            surfaceHeight - 1,
                            minBuildHeight)
                    : null;
            if (layers != null) {
                BlockPos.MutableBlockPos groundPos = new BlockPos.MutableBlockPos(
                        sampleX, layers.terrainY(), sampleZ);
                BlockState groundState = layers.terrainState();
                MapColor groundMapColor = groundState.getMapColor(level, groundPos);
                while (groundMapColor == MapColor.NONE && groundPos.getY() > minBuildHeight) {
                    groundPos.move(0, -1, 0);
                    groundState = chunk.getBlockState(groundPos);
                    groundMapColor = groundState.getMapColor(level, groundPos);
                }
                if (groundMapColor != MapColor.NONE) {
                    terrainColor = biomeTintedColor(level, groundPos, groundMapColor, groundMapColor.col);
                    terrainHeight = groundPos.getY() + 1;
                } else {
                    terrainColor = FALLBACK_COLOR;
                }
            }

            setCell(cellX, cellZ, terrainHeight, terrainColor, waterTint);
            return true;
        }

        private static int biomeTintedColor(ClientLevel level, BlockPos pos, MapColor mapColor, int baseColor) {
            if (mapColor == MapColor.GRASS) {
                return multiplyTint(baseColor,
                        unblendedBiomeColor(level, pos, BiomeColors.GRASS_COLOR_RESOLVER));
            }
            if (mapColor == MapColor.PLANT) {
                return multiplyTint(baseColor,
                        unblendedBiomeColor(level, pos, BiomeColors.FOLIAGE_COLOR_RESOLVER));
            }
            return 0xff000000 | (baseColor & 0x00ffffff);
        }

        private static int unblendedBiomeColor(ClientLevel level, BlockPos pos, ColorResolver resolver) {
            return resolver.getColor(level.getBiome(pos).value(), pos.getX(), pos.getZ());
        }
    }
}
