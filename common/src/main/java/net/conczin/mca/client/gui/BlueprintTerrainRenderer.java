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

/** Owns world-derived Blueprint terrain sampling, texture creation and cache lifecycle. */
final class BlueprintTerrainRenderer implements AutoCloseable {
    private static final int TARGET_CELL_PIXELS = 2;
    private static final int TERRAIN_ALPHA = 0xff;
    private static final int CONTOUR_COLOR = 0x66000000;
    private static final float BASE_BRIGHTNESS = MapColor.Brightness.NORMAL.modifier / 255.0f;
    private static final float ELEVATION_BRIGHTNESS_RANGE = 0.12f;
    private static final float SLOPE_BRIGHTNESS_PER_BLOCK = 0.055f;
    private static final float MIN_BRIGHTNESS = 0.58f;
    private static final float MAX_BRIGHTNESS = 1.15f;

    private TerrainSnapshot snapshot;
    private ResourceLocation textureLocation;

    void render(GuiGraphics context, BlueprintMapViewport viewport) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;

        int centerBlockX = (int) Math.floor(viewport.mapCenterX());
        int centerBlockZ = (int) Math.floor(viewport.mapCenterZ());
        int radius = Math.max(1, (int) Math.ceil((viewport.halfSize() - 1) / viewport.scale()) + 1);
        int sampleStep = Math.max(1, (int) Math.ceil((double) TARGET_CELL_PIXELS / viewport.scale()));
        int visibleMinX = centerBlockX - radius;
        int visibleMaxX = centerBlockX + radius;
        int visibleMinZ = centerBlockZ - radius;
        int visibleMaxZ = centerBlockZ + radius;

        if (snapshot == null || !snapshot.covers(
                visibleMinX, visibleMinZ, visibleMaxX, visibleMaxZ, sampleStep)) {
            snapshot = TerrainSnapshot.sample(minecraft.level, centerBlockX, centerBlockZ, radius, sampleStep);
            releaseTexture();
        }
        renderTexture(context, snapshot);
    }

    private void renderTexture(GuiGraphics context, TerrainSnapshot snapshot) {
        if (textureLocation == null) textureLocation = createTexture(snapshot);
        if (textureLocation == null) return;

        int textureWidth = snapshot.maxX() - snapshot.minX() + 1;
        int textureHeight = snapshot.maxZ() - snapshot.minZ() + 1;
        context.blit(textureLocation, snapshot.minX(), snapshot.minZ(),
                textureWidth, textureHeight, 0.0F, 0.0F,
                textureWidth, textureHeight, textureWidth, textureHeight);
    }

    private ResourceLocation createTexture(TerrainSnapshot snapshot) {
        Minecraft minecraft = Minecraft.getInstance();
        TerrainSnapshot.Cell[][] cells = snapshot.cells();
        if (cells.length == 0 || cells[0].length == 0) return null;

        int textureWidth = snapshot.maxX() - snapshot.minX() + 1;
        int textureHeight = snapshot.maxZ() - snapshot.minZ() + 1;
        NativeImage image = new NativeImage(textureWidth, textureHeight, true);
        int reliefRange = snapshot.maxTerrainHeight() - snapshot.minTerrainHeight();
        int contourInterval = contourInterval(reliefRange);

        for (int cellX = 0; cellX < cells.length; cellX++) {
            for (int cellZ = 0; cellZ < cells[cellX].length; cellZ++) {
                TerrainSnapshot.Cell cell = cells[cellX][cellZ];
                if (cell == null) continue;

                int northHeight = snapshot.heightAt(cellX, cellZ - 1, cell.height());
                int southHeight = snapshot.heightAt(cellX, cellZ + 1, cell.height());
                int westHeight = snapshot.heightAt(cellX - 1, cellZ, cell.height());
                int eastHeight = snapshot.heightAt(cellX + 1, cellZ, cell.height());
                float slopeDelta = ((westHeight - eastHeight) + (northHeight - southHeight)) * 0.25f;
                float elevation = reliefRange == 0
                        ? 0.5f
                        : (cell.height() - snapshot.minTerrainHeight()) / (float) reliefRange;

                int color = shadeColor(cell.baseColor(), slopeDelta, elevation);
                int nativeColor = FastColor.ABGR32.fromArgb32(color);
                int minPixelX = cell.minX() - snapshot.minX();
                int minPixelZ = cell.minZ() - snapshot.minZ();
                int maxPixelX = cell.maxX() - snapshot.minX();
                int maxPixelZ = cell.maxZ() - snapshot.minZ();

                for (int pixelX = minPixelX; pixelX < maxPixelX; pixelX++) {
                    for (int pixelZ = minPixelZ; pixelZ < maxPixelZ; pixelZ++) {
                        image.setPixelRGBA(pixelX, pixelZ, nativeColor);
                    }
                }

                boolean northContour = cellZ > 0
                        && Math.floorDiv(cell.height(), contourInterval)
                        != Math.floorDiv(northHeight, contourInterval);
                boolean westContour = cellX > 0
                        && Math.floorDiv(cell.height(), contourInterval)
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
        if (reliefRange <= 2) return 1;
        if (reliefRange <= 6) return 2;
        return 4;
    }

    private static int shadeColor(int baseColor, float slopeDelta, float elevation) {
        float elevationBrightness = (elevation - 0.5f) * 2.0f * ELEVATION_BRIGHTNESS_RANGE;
        float brightness = BASE_BRIGHTNESS + slopeDelta * SLOPE_BRIGHTNESS_PER_BLOCK + elevationBrightness;
        brightness = Math.max(MIN_BRIGHTNESS, Math.min(MAX_BRIGHTNESS, brightness));

        int red = Math.min(255, Math.round(((baseColor >> 16) & 0xff) * brightness));
        int green = Math.min(255, Math.round(((baseColor >> 8) & 0xff) * brightness));
        int blue = Math.min(255, Math.round((baseColor & 0xff) * brightness));
        return (TERRAIN_ALPHA << 24) | (red << 16) | (green << 8) | blue;
    }

    private void releaseTexture() {
        if (textureLocation != null) {
            Minecraft.getInstance().getTextureManager().release(textureLocation);
            textureLocation = null;
        }
    }

    @Override
    public void close() {
        releaseTexture();
    }

    private record TerrainSnapshot(int minX, int minZ, int maxX, int maxZ, int sampleStep,
                                    int minTerrainHeight, int maxTerrainHeight, Cell[][] cells) {
        private static final int CACHE_MARGIN_BLOCKS = 64;
        private static final int FALLBACK_COLOR = 0x6f766f;

        boolean covers(int visibleMinX, int visibleMinZ, int visibleMaxX, int visibleMaxZ, int requiredSampleStep) {
            return sampleStep == requiredSampleStep
                    && visibleMinX >= minX
                    && visibleMinZ >= minZ
                    && visibleMaxX <= maxX
                    && visibleMaxZ <= maxZ;
        }

        int heightAt(int x, int z, int fallbackHeight) {
            if (x < 0 || z < 0 || x >= cells.length || z >= cells[x].length || cells[x][z] == null) {
                return fallbackHeight;
            }
            return cells[x][z].height();
        }

        static TerrainSnapshot sample(ClientLevel level,
                                      int centerBlockX,
                                      int centerBlockZ,
                                      int visibleRadius,
                                      int sampleStep) {
            int cacheRadius = visibleRadius + CACHE_MARGIN_BLOCKS;
            int minX = centerBlockX - cacheRadius;
            int maxX = centerBlockX + cacheRadius;
            int minZ = centerBlockZ - cacheRadius;
            int maxZ = centerBlockZ + cacheRadius;
            int minBuildHeight = level.getMinBuildHeight();
            int xCellCount = (maxX - minX) / sampleStep + 1;
            int zCellCount = (maxZ - minZ) / sampleStep + 1;
            Cell[][] cells = new Cell[xCellCount][zCellCount];
            int minTerrainHeight = Integer.MAX_VALUE;
            int maxTerrainHeight = Integer.MIN_VALUE;

            BlockPos.MutableBlockPos surfacePos = new BlockPos.MutableBlockPos();
            for (int cellX = 0; cellX < xCellCount; cellX++) {
                int x = minX + cellX * sampleStep;
                int cellMaxX = Math.min(x + sampleStep, maxX + 1);
                int sampleX = Math.min(x + sampleStep / 2, maxX);
                for (int cellZ = 0; cellZ < zCellCount; cellZ++) {
                    int z = minZ + cellZ * sampleStep;
                    int cellMaxZ = Math.min(z + sampleStep, maxZ + 1);
                    int sampleZ = Math.min(z + sampleStep / 2, maxZ);
                    //noinspection deprecation
                    if (!level.hasChunkAt(sampleX, sampleZ)) {
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
                    cells[cellX][cellZ] = new Cell(x, z, cellMaxX, cellMaxZ, terrainHeight, baseColor);
                    minTerrainHeight = Math.min(minTerrainHeight, terrainHeight);
                    maxTerrainHeight = Math.max(maxTerrainHeight, terrainHeight);
                }
            }

            if (minTerrainHeight == Integer.MAX_VALUE) {
                minTerrainHeight = 0;
                maxTerrainHeight = 0;
            }
            return new TerrainSnapshot(
                    minX, minZ, maxX, maxZ, sampleStep, minTerrainHeight, maxTerrainHeight, cells);
        }

        private record Cell(int minX, int minZ, int maxX, int maxZ, int height, int baseColor) {
        }
    }
}
