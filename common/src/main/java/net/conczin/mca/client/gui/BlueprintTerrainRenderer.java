package net.conczin.mca.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Owns world-derived Blueprint terrain sampling, texture creation and cache lifecycle. */
final class BlueprintTerrainRenderer implements AutoCloseable {
    private static final int TARGET_CELL_PIXELS = 2;
    private static final int TILE_BLOCK_SIZE = 128;
    private static final int MAX_CACHED_TILES = 96;
    private static final long INCOMPLETE_TILE_RETRY_TICKS = 20L;
    private static final int TERRAIN_ALPHA = 0xff;
    private static final int CONTOUR_COLOR = 0x66000000;
    private static final int CONTOUR_INTERVAL = 4;
    private static final float BASE_BRIGHTNESS = MapColor.Brightness.NORMAL.modifier / 255.0f;
    private static final float SLOPE_BRIGHTNESS_PER_BLOCK = 0.055f;
    private static final float MIN_BRIGHTNESS = 0.58f;
    private static final float MAX_BRIGHTNESS = 1.15f;

    private final LinkedHashMap<TileKey, TerrainTile> tiles = new LinkedHashMap<>(16, 0.75f, true);

    void render(GuiGraphics context, BlueprintMapViewport viewport) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        int centerBlockX = (int) Math.floor(viewport.mapCenterX());
        int centerBlockZ = (int) Math.floor(viewport.mapCenterZ());
        int radius = Math.max(1, (int) Math.ceil((viewport.halfSize() - 1) / viewport.scale()) + 1);
        int sampleStep = sampleStep(viewport.scale());
        int visibleMinX = centerBlockX - radius;
        int visibleMaxX = centerBlockX + radius;
        int visibleMinZ = centerBlockZ - radius;
        int visibleMaxZ = centerBlockZ + radius;

        long gameTime = minecraft.level.getGameTime();
        for (int minX = tileMin(visibleMinX); minX <= visibleMaxX; minX += TILE_BLOCK_SIZE) {
            for (int minZ = tileMin(visibleMinZ); minZ <= visibleMaxZ; minZ += TILE_BLOCK_SIZE) {
                TileKey key = new TileKey(minX, minZ, sampleStep);
                TerrainTile tile = tiles.get(key);
                if (tile == null || tile.shouldRefresh(gameTime)) {
                    if (tile != null) releaseTexture(tile);
                    tile = TerrainTile.sample(minecraft.level, minX, minZ, sampleStep, gameTime);
                    tiles.put(key, tile);
                }
                renderTile(context, tile);
            }
        }
        trimCache();
    }

    static int tileMin(int blockCoordinate) {
        return Math.floorDiv(blockCoordinate, TILE_BLOCK_SIZE) * TILE_BLOCK_SIZE;
    }

    static int sampleStep(float scale) {
        return scale >= 1.0F
                ? 1
                : Math.max(1, (int) Math.ceil((double) TARGET_CELL_PIXELS / scale));
    }

    private void renderTile(GuiGraphics context, TerrainTile tile) {
        if (tile.textureLocation == null) tile.textureLocation = createTexture(tile);
        if (tile.textureLocation == null) return;

        context.blit(tile.textureLocation, tile.minX, tile.minZ,
                TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, 0.0F, 0.0F,
                TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, TILE_BLOCK_SIZE);
    }

    private ResourceLocation createTexture(TerrainTile tile) {
        Minecraft minecraft = Minecraft.getInstance();
        TerrainTile.Cell[][] cells = tile.cells;
        if (cells.length == 0 || cells[0].length == 0) return null;

        NativeImage image = new NativeImage(TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, true);
        int contourInterval = contourInterval(0);

        for (int cellX = 1; cellX < cells.length - 1; cellX++) {
            for (int cellZ = 1; cellZ < cells[cellX].length - 1; cellZ++) {
                TerrainTile.Cell cell = cells[cellX][cellZ];
                if (cell == null) continue;

                int northHeight = tile.heightAt(cellX, cellZ - 1, cell.height);
                int southHeight = tile.heightAt(cellX, cellZ + 1, cell.height);
                int westHeight = tile.heightAt(cellX - 1, cellZ, cell.height);
                int eastHeight = tile.heightAt(cellX + 1, cellZ, cell.height);
                float slopeDelta = ((westHeight - eastHeight) + (northHeight - southHeight)) * 0.25f;

                int color = shadeColor(cell.baseColor, slopeDelta);
                int nativeColor = FastColor.ABGR32.fromArgb32(color);
                int minPixelX = Math.max(cell.minX, tile.minX) - tile.minX;
                int minPixelZ = Math.max(cell.minZ, tile.minZ) - tile.minZ;
                int maxPixelX = Math.min(cell.minX + tile.sampleStep, tile.maxX()) - tile.minX;
                int maxPixelZ = Math.min(cell.minZ + tile.sampleStep, tile.maxZ()) - tile.minZ;
                if (minPixelX >= maxPixelX || minPixelZ >= maxPixelZ) continue;

                for (int pixelX = minPixelX; pixelX < maxPixelX; pixelX++) {
                    for (int pixelZ = minPixelZ; pixelZ < maxPixelZ; pixelZ++) {
                        image.setPixelRGBA(pixelX, pixelZ, nativeColor);
                    }
                }

                boolean northContour = cellZ > 0
                        && Math.floorDiv(cell.height, contourInterval)
                        != Math.floorDiv(northHeight, contourInterval);
                boolean westContour = cellX > 0
                        && Math.floorDiv(cell.height, contourInterval)
                        != Math.floorDiv(westHeight, contourInterval);
                int contourColor = FastColor.ABGR32.fromArgb32(blendContour(color));

                if (northContour && minPixelZ < maxPixelZ) {
                    for (int pixelX = minPixelX; pixelX < maxPixelX; pixelX++) {
                        image.setPixelRGBA(pixelX, minPixelZ, contourColor);
                    }
                }
                if (westContour && minPixelX < maxPixelX) {
                    for (int pixelZ = minPixelZ; pixelZ < maxPixelZ; pixelZ++) {
                        image.setPixelRGBA(minPixelX, pixelZ, contourColor);
                    }
                }
            }
        }

        DynamicTexture texture = new DynamicTexture(image);
        texture.setFilter(false, false);
        return minecraft.getTextureManager().register("mca_blueprint_terrain", texture);
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

    private static int contourInterval(int reliefRange) {
        return CONTOUR_INTERVAL;
    }

    private static int shadeColor(int baseColor, float slopeDelta) {
        float brightness = BASE_BRIGHTNESS + slopeDelta * SLOPE_BRIGHTNESS_PER_BLOCK;
        brightness = Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, brightness));

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
        if (tile.textureLocation == null) return;
        Minecraft.getInstance().getTextureManager().release(tile.textureLocation);
        tile.textureLocation = null;
    }

    @Override
    public void close() {
        tiles.values().forEach(BlueprintTerrainRenderer::releaseTexture);
        tiles.clear();
    }

    private record TileKey(int minX, int minZ, int sampleStep) {
    }

    private static final class TerrainTile {
        private static final int FALLBACK_COLOR = 0x6f766f;

        private final int minX;
        private final int minZ;
        private final int sampleStep;
        private final long sampledAtGameTime;
        private final boolean complete;
        private final Cell[][] cells;
        private ResourceLocation textureLocation;

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

        private static TerrainTile sample(ClientLevel level,
                                          int minX,
                                          int minZ,
                                          int sampleStep,
                                          long gameTime) {
            int maxX = minX + TILE_BLOCK_SIZE;
            int maxZ = minZ + TILE_BLOCK_SIZE;
            int minBuildHeight = level.getMinBuildHeight();
            int firstCellX = Math.floorDiv(minX, sampleStep) * sampleStep - sampleStep;
            int firstCellZ = Math.floorDiv(minZ, sampleStep) * sampleStep - sampleStep;
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
                        continue;
                    }

                    int surfaceHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE, sampleX, sampleZ);
                    if (surfaceHeight <= minBuildHeight) {
                        continue;
                    }

                    surfacePos.set(sampleX, surfaceHeight - 1, sampleZ);
                    BlockState surfaceState = level.getBlockState(surfacePos);
                    MapColor mapColor = surfaceState.getMapColor(level, surfacePos);
                    while (mapColor == MapColor.NONE && surfacePos.getY() > minBuildHeight) {
                        surfacePos.move(0, -1, 0);
                        surfaceState = level.getBlockState(surfacePos);
                        mapColor = surfaceState.getMapColor(level, surfacePos);
                    }

                    int terrainHeight = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);
                    if (terrainHeight <= minBuildHeight) {
                        terrainHeight = surfacePos.getY() + 1;
                    }

                    int baseColor = mapColor == MapColor.NONE ? FALLBACK_COLOR : mapColor.col;
                    cells[cellX][cellZ] = new Cell(x, z, terrainHeight, baseColor);
                }
            }

            return new TerrainTile(minX, minZ, sampleStep, gameTime, complete, cells);
        }

        private static final class Cell {
            private final int minX;
            private final int minZ;
            private final int height;
            private final int baseColor;

            private Cell(int minX, int minZ, int height, int baseColor) {
                this.minX = minX;
                this.minZ = minZ;
                this.height = height;
                this.baseColor = baseColor;
            }
        }
    }
}
