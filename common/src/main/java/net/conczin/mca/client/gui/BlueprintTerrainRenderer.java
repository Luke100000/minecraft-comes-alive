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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private static final long TERRAIN_TILE_STALE_TICKS = 600L;
    private static final int NO_TERRAIN_WORK = Integer.MAX_VALUE;
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
        onClientLevelChanged(minecraft.level);

        int centerBlockX = (int) Math.floor(viewport.mapCenterX());
        int centerBlockZ = (int) Math.floor(viewport.mapCenterZ());
        int radius = Math.max(1, (int) Math.ceil((viewport.halfSize() - 1) / viewport.scale()) + 1);
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
        List<TileKey> visibleKeys = new ArrayList<>();
        TileKey workKey = null;
        TerrainTile workTile = null;
        int bestPriority = NO_TERRAIN_WORK;
        double bestDistanceSq = Double.POSITIVE_INFINITY;

        for (int minX = tileMin(samplingMinX); minX <= samplingMaxX; minX += TILE_BLOCK_SIZE) {
            for (int minZ = tileMin(samplingMinZ); minZ <= samplingMaxZ; minZ += TILE_BLOCK_SIZE) {
                int samplingBand = terrainSamplingBand(
                        minX, minZ, visibleMinX, visibleMaxX, visibleMinZ, visibleMaxZ);
                if (samplingBand > 1) continue;

                TileKey key = new TileKey(minX, minZ);
                TerrainTile tile = tiles.get(key);
                if (samplingBand == 0) {
                    visibleKeys.add(key);
                }

                int priority = terrainWorkPriority(
                        samplingBand,
                        tile != null,
                        tile != null && tile.complete,
                        tile == null ? Long.MIN_VALUE : tile.sampledAtGameTime,
                        tile == null ? Long.MIN_VALUE : tile.retryAfterGameTime,
                        gameTime);
                if (priority == NO_TERRAIN_WORK) continue;
                if (!tileTouchesLoadedChunk(minX, minZ, loadedChunks)) continue;

                double distanceSq = tileDistanceSq(minX, minZ, viewport.mapCenterX(), viewport.mapCenterZ());
                if (priority < bestPriority || (priority == bestPriority && distanceSq < bestDistanceSq)) {
                    workKey = key;
                    workTile = tile;
                    bestPriority = priority;
                    bestDistanceSq = distanceSq;
                }
            }
        }

        if (workKey != null) {
            if (workTile == null) {
                workTile = new TerrainTile(workKey.minX(), workKey.minZ());
                tiles.put(workKey, workTile);
            }
            workTile.sampleNextRegion(
                    minecraft.level, gameTime, viewport.mapCenterX(), viewport.mapCenterZ());
            updateTexture(workTile);
        }

        for (TileKey key : visibleKeys) {
            TerrainTile tile = tiles.get(key);
            if (tile != null) renderTile(context, tile);
        }
        trimCache();
    }

    static int tileMin(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, TILE_BLOCK_SIZE) * TILE_BLOCK_SIZE;
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

    static int terrainWorkPriority(int samplingBand,
                                   boolean tilePresent,
                                   boolean complete,
                                   long sampledAtGameTime,
                                   long retryAfterGameTime,
                                   long gameTime) {
        if (samplingBand < 0 || samplingBand > 1) return NO_TERRAIN_WORK;
        if (!tilePresent) return samplingBand == 0 ? 0 : 3;
        if (!complete) {
            if (retryAfterGameTime != Long.MIN_VALUE && gameTime < retryAfterGameTime) {
                return NO_TERRAIN_WORK;
            }
            return samplingBand == 0 ? 1 : 4;
        }
        if (sampledAtGameTime != Long.MIN_VALUE
                && gameTime - sampledAtGameTime >= TERRAIN_TILE_STALE_TICKS) {
            return samplingBand == 0 ? 2 : 4;
        }
        return NO_TERRAIN_WORK;
    }

    static boolean isCoreSampleCell(int cellX, int cellZ) {
        return cellX > 0 && cellX < TERRAIN_GRID_SIZE - 1
                && cellZ > 0 && cellZ < TERRAIN_GRID_SIZE - 1;
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

    static int nearestPendingRegion(BitSet pendingRegions,
                                    int tileMinX,
                                    int tileMinZ,
                                    double focusX,
                                    double focusZ) {
        int nearest = -1;
        double nearestDistanceSq = Double.POSITIVE_INFINITY;
        for (int regionIndex = pendingRegions.nextSetBit(0);
             regionIndex >= 0;
             regionIndex = pendingRegions.nextSetBit(regionIndex + 1)) {
            SampleRegion region = sampleRegionBounds(regionIndex);
            double centerX = tileMinX + (region.minX() + region.maxX()) * 0.5D;
            double centerZ = tileMinZ + (region.minZ() + region.maxZ()) * 0.5D;
            double deltaX = centerX - focusX;
            double deltaZ = centerZ - focusZ;
            double distanceSq = deltaX * deltaX + deltaZ * deltaZ;
            if (distanceSq < nearestDistanceSq) {
                nearestDistanceSq = distanceSq;
                nearest = regionIndex;
            }
        }
        return nearest;
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

    @FunctionalInterface
    interface LoadedChunkLookup {
        boolean test(int chunkX, int chunkZ);
    }

    record SampleRegion(int minX, int minZ, int maxX, int maxZ) {
    }

    private static final class TerrainTile {
        private static final int FALLBACK_COLOR = 0x6f766f;

        private final int minX;
        private final int minZ;
        private final int[] heights = new int[TERRAIN_GRID_SIZE * TERRAIN_GRID_SIZE];
        private final int[] terrainColors = new int[TERRAIN_GRID_SIZE * TERRAIN_GRID_SIZE];
        private final int[] waterTints = new int[TERRAIN_GRID_SIZE * TERRAIN_GRID_SIZE];
        private final boolean[] validCells = new boolean[TERRAIN_GRID_SIZE * TERRAIN_GRID_SIZE];
        private final BitSet pendingRegions = new BitSet(SAMPLE_REGION_COUNT);
        private final BitSet dirtyRegions = new BitSet(SAMPLE_REGION_COUNT);
        private final long[] regionRetryAfterGameTimes = new long[SAMPLE_REGION_COUNT];
        private long sampledAtGameTime = Long.MIN_VALUE;
        private long retryAfterGameTime = Long.MIN_VALUE;
        private boolean complete;
        private DynamicTexture terrainTexture;
        private ResourceLocation terrainTextureLocation;

        private TerrainTile(int minX, int minZ) {
            this.minX = minX;
            this.minZ = minZ;
            pendingRegions.set(0, SAMPLE_REGION_COUNT);
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

        private void sampleWholeTile(ClientLevel level, long gameTime) {
            sampleNextRegion(level, gameTime,
                    minX + TILE_BLOCK_SIZE * 0.5D,
                    minZ + TILE_BLOCK_SIZE * 0.5D);
        }

        private void sampleNextRegion(ClientLevel level,
                                      long gameTime,
                                      double focusX,
                                      double focusZ) {
            if (complete) {
                pendingRegions.set(0, SAMPLE_REGION_COUNT);
                java.util.Arrays.fill(regionRetryAfterGameTimes, Long.MIN_VALUE);
                complete = false;
            }

            BitSet readyRegions = new BitSet(SAMPLE_REGION_COUNT);
            for (int regionIndex = pendingRegions.nextSetBit(0);
                 regionIndex >= 0;
                 regionIndex = pendingRegions.nextSetBit(regionIndex + 1)) {
                long retryAfter = regionRetryAfterGameTimes[regionIndex];
                if (retryAfter == Long.MIN_VALUE || gameTime >= retryAfter) {
                    readyRegions.set(regionIndex);
                }
            }

            int regionIndex = nearestPendingRegion(readyRegions, minX, minZ, focusX, focusZ);
            if (regionIndex < 0) {
                refreshTileState(gameTime);
                return;
            }

            SampleRegion region = sampleRegionBounds(regionIndex);
            int chunkX = SectionPos.blockToSectionCoord(minX + region.minX());
            int chunkZ = SectionPos.blockToSectionCoord(minZ + region.minZ());
            LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                regionRetryAfterGameTimes[regionIndex] = gameTime + INCOMPLETE_TILE_RETRY_TICKS;
                refreshTileState(gameTime);
                return;
            }

            int minBuildHeight = level.getMinBuildHeight();
            BlockPos.MutableBlockPos surfacePos = new BlockPos.MutableBlockPos();
            for (int pixelX = region.minX(); pixelX < region.maxX(); pixelX++) {
                for (int pixelZ = region.minZ(); pixelZ < region.maxZ(); pixelZ++) {
                    sampleCell(level, chunk, pixelX + 1, pixelZ + 1, minBuildHeight, surfacePos);
                }
            }
            pendingRegions.clear(regionIndex);
            regionRetryAfterGameTimes[regionIndex] = Long.MIN_VALUE;
            dirtyRegions.set(regionIndex);
            refreshTileState(gameTime);
        }

        private void refreshTileState(long gameTime) {
            if (pendingRegions.isEmpty()) {
                complete = true;
                sampledAtGameTime = gameTime;
                retryAfterGameTime = Long.MIN_VALUE;
                return;
            }

            complete = false;
            long earliestRetry = Long.MAX_VALUE;
            boolean hasReadyRegion = false;
            for (int regionIndex = pendingRegions.nextSetBit(0);
                 regionIndex >= 0;
                 regionIndex = pendingRegions.nextSetBit(regionIndex + 1)) {
                long retryAfter = regionRetryAfterGameTimes[regionIndex];
                if (retryAfter == Long.MIN_VALUE || gameTime >= retryAfter) {
                    hasReadyRegion = true;
                    break;
                }
                earliestRetry = Math.min(earliestRetry, retryAfter);
            }
            retryAfterGameTime = hasReadyRegion || earliestRetry == Long.MAX_VALUE
                    ? Long.MIN_VALUE
                    : earliestRetry;
        }

        @SuppressWarnings("deprecation")
        private boolean sampleCell(ClientLevel level,
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
