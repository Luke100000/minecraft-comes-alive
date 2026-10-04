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
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Owns world-derived Blueprint terrain sampling, texture creation and cache lifecycle. */
final class BlueprintTerrainRenderer {
    private static final int TILE_BLOCK_SIZE = 128;
    private static final int TERRAIN_GRID_SIZE = TILE_BLOCK_SIZE + 2;
    private static final int SAMPLE_REGION_BLOCK_SIZE = 16;
    private static final int SAMPLE_REGIONS_PER_AXIS = TILE_BLOCK_SIZE / SAMPLE_REGION_BLOCK_SIZE;
    private static final int CORE_SAMPLE_REGION_COUNT = SAMPLE_REGIONS_PER_AXIS * SAMPLE_REGIONS_PER_AXIS;
    private static final int HALO_EDGE_REGION_COUNT = SAMPLE_REGIONS_PER_AXIS * 4;
    private static final int SAMPLE_REGION_COUNT = CORE_SAMPLE_REGION_COUNT + HALO_EDGE_REGION_COUNT + 4;
    private static final int MAX_CACHED_TILES = 96;
    private static final long INCOMPLETE_TILE_RETRY_TICKS = 20L;
    private static final long TERRAIN_REGION_STALE_TICKS = 600L;
    private static final long TERRAIN_SAMPLE_BUDGET_NANOS = 1_500_000L;
    private static final int TERRAIN_ALPHA = 0xff;
    private static final int CONTOUR_COLOR = 0x66000000;
    private static final int CONTOUR_INTERVAL = 4;
    private static final float BASE_BRIGHTNESS = MapColor.Brightness.NORMAL.modifier / 255.0f;
    private static final float SLOPE_BRIGHTNESS_PER_BLOCK = 0.055f;
    private static final float MIN_BRIGHTNESS = 0.58f;
    private static final float MAX_BRIGHTNESS = 1.15f;
    private static final float WATER_BLEND = 0.80f;
    private static final int NO_WATER_TINT = -1;

    private static final LinkedHashMap<TileKey, TerrainTile> tiles = new LinkedHashMap<>(16, 0.75f, true);
    private static Object cacheLevelIdentity;

    void render(GuiGraphics context, BlueprintMapViewport viewport) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        sampleTerrain(minecraft.level, viewport, System::nanoTime);

        int radius = samplingRadius(viewport);
        int centerBlockX = (int) Math.floor(viewport.mapCenterX());
        int centerBlockZ = (int) Math.floor(viewport.mapCenterZ());
        for (int minX = tileMin(centerBlockX - radius); minX <= centerBlockX + radius; minX += TILE_BLOCK_SIZE) {
            for (int minZ = tileMin(centerBlockZ - radius); minZ <= centerBlockZ + radius; minZ += TILE_BLOCK_SIZE) {
                TerrainTile tile = tiles.get(new TileKey(minX, minZ));
                if (tile != null) {
                    updateTexture(tile);
                    renderTile(context, tile);
                }
            }
        }
        trimCache();
    }

    private static int samplingRadius(BlueprintMapViewport viewport) {
        return Math.max(1, (int) Math.ceil((viewport.halfSize() - 1) / viewport.scale()) + 1);
    }

    /** Uses one frame's sampling budget; texture publication stays in the render path. */
    static void sampleTerrain(ClientLevel level, BlueprintMapViewport viewport, LongSupplier nanoTime) {
        onClientLevelChanged(level);
        long startedAt = nanoTime.getAsLong();

        int centerBlockX = (int) Math.floor(viewport.mapCenterX());
        int centerBlockZ = (int) Math.floor(viewport.mapCenterZ());
        int radius = samplingRadius(viewport);
        int visibleMinX = centerBlockX - radius;
        int visibleMaxX = centerBlockX + radius;
        int visibleMinZ = centerBlockZ - radius;
        int visibleMaxZ = centerBlockZ + radius;
        int samplingMinX = visibleMinX - TILE_BLOCK_SIZE;
        int samplingMaxX = visibleMaxX + TILE_BLOCK_SIZE;
        int samplingMinZ = visibleMinZ - TILE_BLOCK_SIZE;
        int samplingMaxZ = visibleMaxZ + TILE_BLOCK_SIZE;

        long gameTime = level.getGameTime();
        List<TerrainWork> work = new ArrayList<>();

        for (int minX = tileMin(samplingMinX); minX <= samplingMaxX; minX += TILE_BLOCK_SIZE) {
            for (int minZ = tileMin(samplingMinZ); minZ <= samplingMaxZ; minZ += TILE_BLOCK_SIZE) {
                TileKey key = new TileKey(minX, minZ);
                TerrainTile tile = tiles.get(key);
                for (int regionIndex = 0; regionIndex < SAMPLE_REGION_COUNT; regionIndex++) {
                    if (tile != null && !tile.isRegionReady(regionIndex, gameTime)) continue;

                    SampleRegion region = sampleRegionBounds(regionIndex);
                    boolean core = regionIndex < CORE_SAMPLE_REGION_COUNT;
                    if (tile == null && !core) continue;
                    boolean visible = region.intersects(minX, minZ,
                            visibleMinX, visibleMaxX, visibleMinZ, visibleMaxZ);
                    if (!core && !region.intersects(minX, minZ,
                            visibleMinX - 1, visibleMaxX + 1, visibleMinZ - 1, visibleMaxZ + 1)) continue;

                    boolean sampled = tile != null && tile.regionSampledAtGameTimes[regionIndex] != Long.MIN_VALUE;
                    int priority = core ? (visible ? 0 : 4) : 2;
                    if (sampled) priority++;
                    double deltaX = minX + (region.minX() + region.maxX()) * 0.5D - viewport.mapCenterX();
                    double deltaZ = minZ + (region.minZ() + region.maxZ()) * 0.5D - viewport.mapCenterZ();
                    work.add(new TerrainWork(key, regionIndex, priority, deltaX * deltaX + deltaZ * deltaZ));
                }
            }
        }

        work.sort(Comparator.comparingInt(TerrainWork::priority).thenComparingDouble(TerrainWork::distanceSq));
        for (TerrainWork next : work) {
            if (nanoTime.getAsLong() - startedAt >= TERRAIN_SAMPLE_BUDGET_NANOS) break;
            TerrainTile tile = tiles.get(next.key());
            // A neighboring core sample may already have supplied this border during the batch.
            if (tile != null && !tile.isRegionReady(next.regionIndex(), gameTime)) continue;

            SampleRegion region = sampleRegionBounds(next.regionIndex());
            int chunkX = SectionPos.blockToSectionCoord(next.key().minX() + region.minX());
            int chunkZ = SectionPos.blockToSectionCoord(next.key().minZ() + region.minZ());
            LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                if (tile != null) {
                    tile.regionRetryAfterGameTimes[next.regionIndex()] = gameTime + INCOMPLETE_TILE_RETRY_TICKS;
                }
                continue;
            }
            if (tile == null) {
                tile = new TerrainTile(next.key().minX(), next.key().minZ());
                tiles.put(next.key(), tile);
            }
            tile.sampleRegion(level, chunk, next.regionIndex(), gameTime);
        }
    }

    static int tileMin(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, TILE_BLOCK_SIZE) * TILE_BLOCK_SIZE;
    }

    static SampleRegion sampleRegionBounds(int regionIndex) {
        if (regionIndex < 0 || regionIndex >= SAMPLE_REGION_COUNT) {
            throw new IllegalArgumentException("regionIndex out of range: " + regionIndex);
        }
        if (regionIndex < CORE_SAMPLE_REGION_COUNT) {
            int regionX = regionIndex % SAMPLE_REGIONS_PER_AXIS;
            int regionZ = regionIndex / SAMPLE_REGIONS_PER_AXIS;
            int minX = regionX * SAMPLE_REGION_BLOCK_SIZE;
            int minZ = regionZ * SAMPLE_REGION_BLOCK_SIZE;
            return new SampleRegion(
                    minX, minZ,
                    minX + SAMPLE_REGION_BLOCK_SIZE,
                    minZ + SAMPLE_REGION_BLOCK_SIZE);
        }

        int haloIndex = regionIndex - CORE_SAMPLE_REGION_COUNT;
        if (haloIndex < SAMPLE_REGIONS_PER_AXIS) {
            int minZ = haloIndex * SAMPLE_REGION_BLOCK_SIZE;
            return new SampleRegion(-1, minZ, 0, minZ + SAMPLE_REGION_BLOCK_SIZE);
        }
        haloIndex -= SAMPLE_REGIONS_PER_AXIS;
        if (haloIndex < SAMPLE_REGIONS_PER_AXIS) {
            int minZ = haloIndex * SAMPLE_REGION_BLOCK_SIZE;
            return new SampleRegion(TILE_BLOCK_SIZE, minZ, TILE_BLOCK_SIZE + 1, minZ + SAMPLE_REGION_BLOCK_SIZE);
        }
        haloIndex -= SAMPLE_REGIONS_PER_AXIS;
        if (haloIndex < SAMPLE_REGIONS_PER_AXIS) {
            int minX = haloIndex * SAMPLE_REGION_BLOCK_SIZE;
            return new SampleRegion(minX, -1, minX + SAMPLE_REGION_BLOCK_SIZE, 0);
        }
        haloIndex -= SAMPLE_REGIONS_PER_AXIS;
        if (haloIndex < SAMPLE_REGIONS_PER_AXIS) {
            int minX = haloIndex * SAMPLE_REGION_BLOCK_SIZE;
            return new SampleRegion(minX, TILE_BLOCK_SIZE, minX + SAMPLE_REGION_BLOCK_SIZE, TILE_BLOCK_SIZE + 1);
        }

        return switch (haloIndex - SAMPLE_REGIONS_PER_AXIS) {
            case 0 -> new SampleRegion(-1, -1, 0, 0);
            case 1 -> new SampleRegion(TILE_BLOCK_SIZE, -1, TILE_BLOCK_SIZE + 1, 0);
            case 2 -> new SampleRegion(-1, TILE_BLOCK_SIZE, 0, TILE_BLOCK_SIZE + 1);
            case 3 -> new SampleRegion(TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, TILE_BLOCK_SIZE + 1, TILE_BLOCK_SIZE + 1);
            default -> throw new IllegalArgumentException("regionIndex out of range: " + regionIndex);
        };
    }

    static SampleRegion dirtyTextureBounds(int regionIndex) {
        SampleRegion region = sampleRegionBounds(regionIndex);
        return new SampleRegion(
                Math.max(0, region.minX() - 1),
                Math.max(0, region.minZ() - 1),
                Math.min(TILE_BLOCK_SIZE, region.maxX() + 1),
                Math.min(TILE_BLOCK_SIZE, region.maxZ() + 1));
    }

    private void renderTile(GuiGraphics context, TerrainTile tile) {
        if (tile.terrainTextureLocation == null) return;

        context.blit(tile.terrainTextureLocation, tile.minX, tile.minZ,
                TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, 0.0F, 0.0F,
                TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, TILE_BLOCK_SIZE);
    }

    private void updateTexture(TerrainTile tile) {
        if (tile.dirtyRegions.isEmpty()) return;

        if (tile.terrainTexture == null) {
            NativeImage terrainImage = new NativeImage(TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, true);
            for (int regionIndex = tile.dirtyRegions.nextSetBit(0);
                 regionIndex >= 0;
                 regionIndex = tile.dirtyRegions.nextSetBit(regionIndex + 1)) {
                updatePixels(tile, terrainImage, dirtyTextureBounds(regionIndex));
            }

            DynamicTexture terrainTexture = new DynamicTexture(terrainImage);
            terrainTexture.setFilter(false, false);
            tile.terrainTexture = terrainTexture;
            tile.terrainTextureLocation = Minecraft.getInstance().getTextureManager()
                    .register("mca_blueprint_terrain", terrainTexture);
            tile.dirtyRegions.clear();
            return;
        }

        NativeImage terrainImage = tile.terrainTexture.getPixels();
        if (terrainImage == null) return;

        tile.terrainTexture.bind();
        for (int regionIndex = tile.dirtyRegions.nextSetBit(0);
             regionIndex >= 0;
             regionIndex = tile.dirtyRegions.nextSetBit(regionIndex + 1)) {
            SampleRegion dirty = dirtyTextureBounds(regionIndex);
            updatePixels(tile, terrainImage, dirty);
            terrainImage.upload(
                    0,
                    dirty.minX(), dirty.minZ(),
                    dirty.minX(), dirty.minZ(),
                    dirty.maxX() - dirty.minX(),
                    dirty.maxZ() - dirty.minZ(),
                    false, false, false, false);
        }
        tile.dirtyRegions.clear();
    }

    private static void updatePixels(TerrainTile tile, NativeImage terrainImage, SampleRegion bounds) {
        for (int pixelX = bounds.minX(); pixelX < bounds.maxX(); pixelX++) {
            for (int pixelZ = bounds.minZ(); pixelZ < bounds.maxZ(); pixelZ++) {
                int cellX = pixelX + 1;
                int cellZ = pixelZ + 1;

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

    static int dryTerrainHeight(int noLeavesHeight, int surfaceHeight, int minBuildHeight) {
        return noLeavesHeight > minBuildHeight ? noLeavesHeight : surfaceHeight;
    }

    private static boolean isWater(BlockState state) {
        return state.getFluidState().is(FluidTags.WATER);
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

    static void onClientLevelChanged(Object levelIdentity) {
        if (cacheLevelIdentity == levelIdentity) return;
        tiles.values().forEach(BlueprintTerrainRenderer::releaseTexture);
        tiles.clear();
        cacheLevelIdentity = levelIdentity;
    }

    private record TileKey(int minX, int minZ) {
    }

    private record TerrainWork(TileKey key, int regionIndex, int priority, double distanceSq) {
    }

    record SampleRegion(int minX, int minZ, int maxX, int maxZ) {
        private boolean intersects(int tileMinX, int tileMinZ, int minX, int maxX, int minZ, int maxZ) {
            return tileMinX + this.minX <= maxX && tileMinX + this.maxX > minX
                    && tileMinZ + this.minZ <= maxZ && tileMinZ + this.maxZ > minZ;
        }
    }

    private static final class TerrainTile {
        private static final int FALLBACK_COLOR = 0x6f766f;

        private final int minX;
        private final int minZ;
        private final int[] heights = new int[TERRAIN_GRID_SIZE * TERRAIN_GRID_SIZE];
        private final int[] terrainColors = new int[TERRAIN_GRID_SIZE * TERRAIN_GRID_SIZE];
        private final int[] waterTints = new int[TERRAIN_GRID_SIZE * TERRAIN_GRID_SIZE];
        private final boolean[] validCells = new boolean[TERRAIN_GRID_SIZE * TERRAIN_GRID_SIZE];
        private final BitSet dirtyRegions = new BitSet(SAMPLE_REGION_COUNT);
        private final long[] regionSampledAtGameTimes = new long[SAMPLE_REGION_COUNT];
        private final long[] regionRetryAfterGameTimes = new long[SAMPLE_REGION_COUNT];
        private DynamicTexture terrainTexture;
        private ResourceLocation terrainTextureLocation;

        private TerrainTile(int minX, int minZ) {
            this.minX = minX;
            this.minZ = minZ;
            java.util.Arrays.fill(regionSampledAtGameTimes, Long.MIN_VALUE);
            java.util.Arrays.fill(regionRetryAfterGameTimes, Long.MIN_VALUE);
        }

        private int heightAt(int x, int z, int fallbackHeight) {
            return isCellValid(x, z) ? heights[cellIndex(x, z)] : fallbackHeight;
        }

        private boolean isCellValid(int x, int z) {
            return x >= 0 && z >= 0 && x < TERRAIN_GRID_SIZE && z < TERRAIN_GRID_SIZE
                    && validCells[cellIndex(x, z)];
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
            return x * TERRAIN_GRID_SIZE + z;
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

        private boolean isRegionReady(int regionIndex, long gameTime) {
            long retryAfter = regionRetryAfterGameTimes[regionIndex];
            if (retryAfter != Long.MIN_VALUE && gameTime < retryAfter) return false;
            long sampledAt = regionSampledAtGameTimes[regionIndex];
            return sampledAt == Long.MIN_VALUE || gameTime - sampledAt >= TERRAIN_REGION_STALE_TICKS;
        }

        private void sampleRegion(ClientLevel level, LevelChunk chunk, int regionIndex, long gameTime) {
            SampleRegion region = sampleRegionBounds(regionIndex);
            int minBuildHeight = level.getMinBuildHeight();
            BlockPos.MutableBlockPos surfacePos = new BlockPos.MutableBlockPos();
            for (int pixelX = region.minX(); pixelX < region.maxX(); pixelX++) {
                for (int pixelZ = region.minZ(); pixelZ < region.maxZ(); pixelZ++) {
                    sampleCell(level, chunk, pixelX + 1, pixelZ + 1, minBuildHeight, surfacePos);
                }
            }
            regionSampledAtGameTimes[regionIndex] = gameTime;
            regionRetryAfterGameTimes[regionIndex] = Long.MIN_VALUE;
            dirtyRegions.set(regionIndex);

            if (regionIndex < CORE_SAMPLE_REGION_COUNT) {
                int regionX = regionIndex % SAMPLE_REGIONS_PER_AXIS;
                int regionZ = regionIndex / SAMPLE_REGIONS_PER_AXIS;
                if (regionX == 0) {
                    copyBorderToNeighbor(minX - TILE_BLOCK_SIZE, minZ,
                            CORE_SAMPLE_REGION_COUNT + SAMPLE_REGIONS_PER_AXIS + regionZ, gameTime);
                }
                if (regionX == SAMPLE_REGIONS_PER_AXIS - 1) {
                    copyBorderToNeighbor(minX + TILE_BLOCK_SIZE, minZ,
                            CORE_SAMPLE_REGION_COUNT + regionZ, gameTime);
                }
                if (regionZ == 0) {
                    copyBorderToNeighbor(minX, minZ - TILE_BLOCK_SIZE,
                            CORE_SAMPLE_REGION_COUNT + SAMPLE_REGIONS_PER_AXIS * 3 + regionX, gameTime);
                }
                if (regionZ == SAMPLE_REGIONS_PER_AXIS - 1) {
                    copyBorderToNeighbor(minX, minZ + TILE_BLOCK_SIZE,
                            CORE_SAMPLE_REGION_COUNT + SAMPLE_REGIONS_PER_AXIS * 2 + regionX, gameTime);
                }
            }
        }

        /** Shares freshly sampled edge columns so adjacent textures shade the same boundary. */
        private void copyBorderToNeighbor(int neighborMinX, int neighborMinZ, int regionIndex, long gameTime) {
            TerrainTile neighbor = tiles.get(new TileKey(neighborMinX, neighborMinZ));
            if (neighbor == null) return;
            SampleRegion border = sampleRegionBounds(regionIndex);
            for (int pixelX = border.minX(); pixelX < border.maxX(); pixelX++) {
                for (int pixelZ = border.minZ(); pixelZ < border.maxZ(); pixelZ++) {
                    int cellX = neighborMinX + pixelX - minX + 1;
                    int cellZ = neighborMinZ + pixelZ - minZ + 1;
                    if (isCellValid(cellX, cellZ)) {
                        neighbor.setCell(pixelX + 1, pixelZ + 1,
                                height(cellX, cellZ), terrainColor(cellX, cellZ), waterTint(cellX, cellZ));
                    } else {
                        neighbor.clearCell(pixelX + 1, pixelZ + 1);
                    }
                }
            }
            neighbor.regionSampledAtGameTimes[regionIndex] = gameTime;
            neighbor.regionRetryAfterGameTimes[regionIndex] = Long.MIN_VALUE;
            neighbor.dirtyRegions.set(regionIndex);
        }

        @SuppressWarnings("deprecation")
        private void sampleCell(ClientLevel level,
                                LevelChunk chunk,
                                int cellX,
                                int cellZ,
                                int minBuildHeight,
                                BlockPos.MutableBlockPos surfacePos) {
            int sampleX = minX + cellX - 1;
            int sampleZ = minZ + cellZ - 1;

            int surfaceHeight = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ) + 1;
            if (surfaceHeight <= minBuildHeight) {
                clearCell(cellX, cellZ);
                return;
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
                terrainHeight = dryTerrainHeight(
                        chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ) + 1,
                        terrainHeight,
                        minBuildHeight);
            }
            int baseColor = mapColor == MapColor.NONE ? FALLBACK_COLOR : mapColor.col;
            baseColor = biomeTintedColor(level, surfacePos, mapColor, baseColor);

            int terrainColor = baseColor;
            int waterTint = NO_WATER_TINT;
            if (waterColumn) {
                BlockPos waterPos = new BlockPos(sampleX, surfaceHeight - 1, sampleZ);
                waterTint = unblendedBiomeColor(level, waterPos, BiomeColors.WATER_COLOR_RESOLVER);
            }
            if (waterColumn) {
                int y = surfaceHeight - 2;
                BlockState groundState = null;
                while (y >= minBuildHeight && groundState == null) {
                    int sectionMinY = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(y));
                    int sectionIndex = chunk.getSectionIndex(y);
                    if (sectionIndex < 0
                            || sectionIndex >= chunk.getSectionsCount()
                            || !chunk.getSection(sectionIndex).maybeHas(BlockState::blocksMotion)) {
                        y = sectionMinY - 1;
                        continue;
                    }

                    int scanMinY = Math.max(minBuildHeight, sectionMinY);
                    while (y >= scanMinY) {
                        surfacePos.set(sampleX, y, sampleZ);
                        BlockState candidate = chunk.getBlockState(surfacePos);
                        if (candidate.blocksMotion()) {
                            groundState = candidate;
                            break;
                        }
                        y--;
                    }
                }

                if (groundState != null) {
                    MapColor groundMapColor = groundState.getMapColor(level, surfacePos);
                    while (groundMapColor == MapColor.NONE && surfacePos.getY() > minBuildHeight) {
                        surfacePos.move(0, -1, 0);
                        groundState = chunk.getBlockState(surfacePos);
                        groundMapColor = groundState.getMapColor(level, surfacePos);
                    }
                    if (groundMapColor != MapColor.NONE) {
                        terrainColor = biomeTintedColor(level, surfacePos, groundMapColor, groundMapColor.col);
                        terrainHeight = surfacePos.getY() + 1;
                    } else {
                        terrainColor = FALLBACK_COLOR;
                    }
                }
            }

            setCell(cellX, cellZ, terrainHeight, terrainColor, waterTint);
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
