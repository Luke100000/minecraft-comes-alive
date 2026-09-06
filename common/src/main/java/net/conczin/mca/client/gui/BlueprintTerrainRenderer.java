package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.Fluids;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

/** Owns world-derived Blueprint terrain sampling, texture creation and cache lifecycle. */
final class BlueprintTerrainRenderer implements AutoCloseable {
    private static final int SAMPLE_STEP = 1;
    private static final int TILE_BLOCK_SIZE = 128;
    private static final int MAX_CACHED_TILES = 96;
    private static final int MAX_TILE_SAMPLES_PER_FRAME = 1;
    private static final long INCOMPLETE_TILE_RETRY_TICKS = 20L;
    private static final int TERRAIN_ALPHA = 0xff;
    private static final int CONTOUR_COLOR = 0x66000000;
    private static final int CONTOUR_INTERVAL = 4;
    private static final float BASE_BRIGHTNESS = MapColor.Brightness.NORMAL.modifier / 255.0f;
    private static final float SLOPE_BRIGHTNESS_PER_BLOCK = 0.055f;
    private static final float MIN_BRIGHTNESS = 0.58f;
    private static final float MAX_BRIGHTNESS = 1.15f;
    private static final float WATER_BLEND = 0.625f;
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

        long gameTime = minecraft.level.getGameTime();
        TileSamplingBudget samplingBudget = new TileSamplingBudget();
        for (int minX = tileMin(visibleMinX); minX <= visibleMaxX; minX += TILE_BLOCK_SIZE) {
            for (int minZ = tileMin(visibleMinZ); minZ <= visibleMaxZ; minZ += TILE_BLOCK_SIZE) {
                TileKey key = new TileKey(minX, minZ);
                TerrainTile tile = tiles.get(key);
                if ((tile == null || tile.shouldRefresh(gameTime)) && samplingBudget.tryAcquire()) {
                    TerrainTile previous = tile;
                    TerrainTile sampled = TerrainTile.sample(
                            minecraft.level, minX, minZ, sampleStep, gameTime, previous);
                    if (tile != null) releaseTexture(tile);
                    tile = sampled;
                    tiles.put(key, tile);
                }
                if (tile != null) {
                    renderTile(context, tile);
                }
            }
        }
        trimCache();
    }

    static int tileMin(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, TILE_BLOCK_SIZE) * TILE_BLOCK_SIZE;
    }

    static int sampleStep() {
        return SAMPLE_STEP;
    }

    private void renderTile(GuiGraphics context, TerrainTile tile) {
        if (tile.terrainTextureLocation == null) createTexture(tile);
        if (tile.terrainTextureLocation == null) return;

        context.blit(tile.terrainTextureLocation, tile.minX, tile.minZ,
                TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, 0.0F, 0.0F,
                TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, TILE_BLOCK_SIZE);
    }

    private void createTexture(TerrainTile tile) {
        Minecraft minecraft = Minecraft.getInstance();
        TerrainTile.Cell[][] cells = tile.cells;
        if (cells.length == 0 || cells[0].length == 0) return;

        NativeImage terrainImage = new NativeImage(TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, true);
        int firstCellX = TerrainTile.firstCell(tile.minX, tile.sampleStep);
        int firstCellZ = TerrainTile.firstCell(tile.minZ, tile.sampleStep);

        for (int cellX = 1; cellX < cells.length - 1; cellX++) {
            for (int cellZ = 1; cellZ < cells[cellX].length - 1; cellZ++) {
                TerrainTile.Cell cell = cells[cellX][cellZ];
                if (cell == null) continue;

                int northHeight = tile.heightAt(cellX, cellZ - 1, cell.height);
                int southHeight = tile.heightAt(cellX, cellZ + 1, cell.height);
                int westHeight = tile.heightAt(cellX - 1, cellZ, cell.height);
                int eastHeight = tile.heightAt(cellX + 1, cellZ, cell.height);

                float brightness = hillshadeBrightness(westHeight, eastHeight, northHeight, southHeight);
                int color = composeTerrainAndWater(
                        cell.terrainColor(), cell.waterTint(), brightness, false);
                int nativeColor = FastColor.ABGR32.fromArgb32(color);
                int cellMinX = firstCellX + cellX * tile.sampleStep;
                int cellMinZ = firstCellZ + cellZ * tile.sampleStep;
                int minPixelX = Math.max(cellMinX, tile.minX) - tile.minX;
                int minPixelZ = Math.max(cellMinZ, tile.minZ) - tile.minZ;
                int maxPixelX = Math.min(cellMinX + tile.sampleStep, tile.maxX()) - tile.minX;
                int maxPixelZ = Math.min(cellMinZ + tile.sampleStep, tile.maxZ()) - tile.minZ;
                if (minPixelX >= maxPixelX || minPixelZ >= maxPixelZ) continue;

                for (int pixelX = minPixelX; pixelX < maxPixelX; pixelX++) {
                    for (int pixelZ = minPixelZ; pixelZ < maxPixelZ; pixelZ++) {
                        terrainImage.setPixelRGBA(pixelX, pixelZ, nativeColor);
                    }
                }

                boolean northContour = cellZ > 0
                        && Math.floorDiv(cell.height, CONTOUR_INTERVAL)
                        != Math.floorDiv(northHeight, CONTOUR_INTERVAL);
                boolean westContour = cellX > 0
                        && Math.floorDiv(cell.height, CONTOUR_INTERVAL)
                        != Math.floorDiv(westHeight, CONTOUR_INTERVAL);
                int contourColor = FastColor.ABGR32.fromArgb32(composeTerrainAndWater(
                        cell.terrainColor(), cell.waterTint(), brightness, true));

                if (northContour && minPixelZ < maxPixelZ) {
                    for (int pixelX = minPixelX; pixelX < maxPixelX; pixelX++) {
                        terrainImage.setPixelRGBA(pixelX, minPixelZ, contourColor);
                    }
                }
                if (westContour && minPixelX < maxPixelX) {
                    for (int pixelZ = minPixelZ; pixelZ < maxPixelZ; pixelZ++) {
                        terrainImage.setPixelRGBA(minPixelX, pixelZ, contourColor);
                    }
                }
            }
        }

        DynamicTexture terrainTexture = new DynamicTexture(terrainImage);
        terrainTexture.setFilter(false, false);
        tile.terrainTextureLocation = minecraft.getTextureManager().register("mca_blueprint_terrain", terrainTexture);
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
        int color = biomeWaterColor == NO_WATER_TINT
                ? terrainColor
                : waterColor(terrainColor, biomeWaterColor);
        color = shadeColor(color, brightness);
        if (contour) {
            color = blendContour(color);
        }
        return color;
    }

    @SuppressWarnings("deprecation")
    static ColumnLayers sampleClientOceanFloorColumn(IntFunction<BlockState> stateAtY,
                                                      IntPredicate sectionMayContainFloor,
                                                      int waterY,
                                                      int minY) {
        int y = waterY - 1;
        while (y >= minY) {
            int sectionMinY = Math.floorDiv(y, 16) * 16;
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

    static int dryTerrainHeight(int motionBlockingHeight, int surfaceHeight, int minBuildHeight) {
        return motionBlockingHeight > minBuildHeight ? motionBlockingHeight : surfaceHeight;
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
        }
    }

    @Override
    public void close() {
        tiles.values().forEach(BlueprintTerrainRenderer::releaseTexture);
        tiles.clear();
    }

    private record TileKey(int minX, int minZ) {
    }

    static final class TileSamplingBudget {
        private int remaining = MAX_TILE_SAMPLES_PER_FRAME;

        boolean tryAcquire() {
            if (remaining <= 0) return false;
            remaining--;
            return true;
        }
    }

    record ColumnLayers(int terrainY, int waterY, BlockState terrainState) {
    }

    private static final class TerrainTile {
        private static final int FALLBACK_COLOR = 0x6f766f;

        private final int minX;
        private final int minZ;
        private final int sampleStep;
        private final long sampledAtGameTime;
        private final boolean complete;
        private final Cell[][] cells;
        private ResourceLocation terrainTextureLocation;

        private TerrainTile(int minX,
                            int minZ,
                            int sampleStep,
                            long sampledAtGameTime,
                            boolean complete,
                            Cell[][] cells) {
            this.minX = minX;
            this.minZ = minZ;
            this.sampleStep = sampleStep;
            this.sampledAtGameTime = sampledAtGameTime;
            this.complete = complete;
            this.cells = cells;
        }

        private int maxX() {
            return minX + TILE_BLOCK_SIZE;
        }

        private int maxZ() {
            return minZ + TILE_BLOCK_SIZE;
        }

        private boolean shouldRefresh(long gameTime) {
            return !complete && gameTime - sampledAtGameTime >= INCOMPLETE_TILE_RETRY_TICKS;
        }

        private int heightAt(int x, int z, int fallbackHeight) {
            if (x < 0 || z < 0 || x >= cells.length || z >= cells[x].length || cells[x][z] == null) {
                return fallbackHeight;
            }
            return cells[x][z].height;
        }

        private Cell cellAtBlock(int blockX, int blockZ) {
            int firstCellX = firstCell(minX, sampleStep);
            int firstCellZ = firstCell(minZ, sampleStep);
            int cellX = Math.floorDiv(blockX - firstCellX, sampleStep);
            int cellZ = Math.floorDiv(blockZ - firstCellZ, sampleStep);
            if (cellX < 0 || cellZ < 0 || cellX >= cells.length || cellZ >= cells[cellX].length) {
                return null;
            }
            return cells[cellX][cellZ];
        }

        private static int firstCell(int tileMin, int sampleStep) {
            return Math.floorDiv(tileMin, sampleStep) * sampleStep - sampleStep;
        }

        @SuppressWarnings("deprecation")
        private static TerrainTile sample(ClientLevel level,
                                          int minX,
                                          int minZ,
                                          int sampleStep,
                                          long gameTime,
                                          TerrainTile previous) {
            int maxX = minX + TILE_BLOCK_SIZE;
            int maxZ = minZ + TILE_BLOCK_SIZE;
            int minBuildHeight = level.getMinBuildHeight();
            int firstCellX = firstCell(minX, sampleStep);
            int firstCellZ = firstCell(minZ, sampleStep);
            int lastCellX = Math.floorDiv(maxX - 1, sampleStep) * sampleStep + sampleStep;
            int lastCellZ = Math.floorDiv(maxZ - 1, sampleStep) * sampleStep + sampleStep;
            int xCellCount = (lastCellX - firstCellX) / sampleStep + 1;
            int zCellCount = (lastCellZ - firstCellZ) / sampleStep + 1;
            Cell[][] cells = new Cell[xCellCount][zCellCount];
            boolean complete = true;

            BlockPos.MutableBlockPos surfacePos = new BlockPos.MutableBlockPos();
            for (int cellX = 0; cellX < xCellCount; cellX++) {
                int x = firstCellX + cellX * sampleStep;
                int sampleX = x + sampleStep / 2;
                for (int cellZ = 0; cellZ < zCellCount; cellZ++) {
                    int z = firstCellZ + cellZ * sampleStep;
                    int sampleZ = z + sampleStep / 2;
                    //noinspection deprecation
                    if (!level.hasChunkAt(sampleX, sampleZ)) {
                        complete = false;
                        Cell cached = previous == null ? null : previous.cellAtBlock(sampleX, sampleZ);
                        if (cached != null) {
                            cells[cellX][cellZ] = cached;
                        }
                        continue;
                    }

                    int surfaceHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ);
                    if (surfaceHeight <= minBuildHeight) {
                        continue;
                    }

                    surfacePos.set(sampleX, surfaceHeight - 1, sampleZ);
                    BlockState surfaceState = level.getBlockState(surfacePos);
                    boolean waterColumn = isWater(surfaceState);
                    MapColor mapColor = surfaceState.getMapColor(level, surfacePos);
                    while (mapColor == MapColor.NONE && surfacePos.getY() > minBuildHeight) {
                        surfacePos.move(0, -1, 0);
                        surfaceState = level.getBlockState(surfacePos);
                        mapColor = surfaceState.getMapColor(level, surfacePos);
                    }

                    int terrainHeight = surfacePos.getY() + 1;
                    if (!waterColumn) {
                        terrainHeight = dryTerrainHeight(
                                level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ),
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
                    var chunk = waterColumn
                            ? level.getChunk(sampleX >> 4, sampleZ >> 4)
                            : null;
                    ColumnLayers layers = waterColumn
                            ? sampleClientOceanFloorColumn(
                                    y -> {
                                        surfacePos.set(sampleX, y, sampleZ);
                                        return level.getBlockState(surfacePos);
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
                            groundState = level.getBlockState(groundPos);
                            groundMapColor = groundState.getMapColor(level, groundPos);
                        }
                        if (groundMapColor != MapColor.NONE) {
                            terrainColor = biomeTintedColor(level, groundPos, groundMapColor, groundMapColor.col);
                            terrainHeight = groundPos.getY() + 1;
                        } else {
                            terrainColor = FALLBACK_COLOR;
                        }
                    }

                    cells[cellX][cellZ] = new Cell(terrainHeight, terrainColor, waterTint);
                }
            }

            return new TerrainTile(minX, minZ, sampleStep, gameTime, complete, cells);
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

        private record Cell(int height, int terrainColor, int waterTint) {
        }
    }
}
