package net.conczin.mca.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

public final class GameTestTerrain {
    private GameTestTerrain() {
    }

    public static void prepareFlatArea(GameTestHelper helper, BlockPos center, int radius) {
        prepareFlatArea(helper, center, radius, 2);
    }

    public static void prepareFlatArea(GameTestHelper helper, BlockPos center, int radius, int clearanceHeight) {
        ChunkPos minChunk = new ChunkPos(center.offset(-radius, 0, -radius));
        ChunkPos maxChunk = new ChunkPos(center.offset(radius, 0, radius));
        for (int chunkX = minChunk.x; chunkX <= maxChunk.x; chunkX++) {
            for (int chunkZ = minChunk.z; chunkZ <= maxChunk.z; chunkZ++) {
                helper.getLevel().setChunkForced(chunkX, chunkZ, true);
            }
        }

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos feet = center.offset(x, 0, z);
                helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
                for (int y = 0; y < clearanceHeight; y++) {
                    helper.getLevel().setBlock(feet.above(y), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    public static void prepareFlatPath(GameTestHelper helper, BlockPos start, BlockPos target) {
        int minX = Math.min(start.getX(), target.getX()) - 1;
        int maxX = Math.max(start.getX(), target.getX()) + 1;
        for (int x = minX; x <= maxX; x++) {
            for (int z = start.getZ() - 1; z <= start.getZ() + 1; z++) {
                BlockPos feet = new BlockPos(x, start.getY(), z);
                helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
