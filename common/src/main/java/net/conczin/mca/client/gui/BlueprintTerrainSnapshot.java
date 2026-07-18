package net.conczin.mca.client.gui;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;

record BlueprintTerrainSnapshot(int minX, int minZ, int maxX, int maxZ, int sampleStep,
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

    static BlueprintTerrainSnapshot sample(ClientLevel level,
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
        return new BlueprintTerrainSnapshot(
                minX, minZ, maxX, maxZ, sampleStep, minTerrainHeight, maxTerrainHeight, cells);
    }

    record Cell(int minX, int minZ, int maxX, int maxZ, int height, int baseColor) {
    }
}
