package net.conczin.mca.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class GameTestTerrain {
    private GameTestTerrain() {
    }

    public static void prepareFlatArea(GameTestHelper helper, BlockPos center, int radius) {
        prepareFlatArea(helper, center, radius, 2);
    }

    public static void prepareFlatArea(GameTestHelper helper, BlockPos center, int radius, int clearanceHeight) {
        ChunkPos minChunk = new ChunkPos(center.offset(-radius, 0, -radius));
        ChunkPos maxChunk = new ChunkPos(center.offset(radius, 0, radius));
        ensureEntityTickingChunks(helper, minChunk, maxChunk);

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
        ChunkPos minChunk = new ChunkPos(new BlockPos(minX, start.getY(), start.getZ() - 1));
        ChunkPos maxChunk = new ChunkPos(new BlockPos(maxX, start.getY(), start.getZ() + 1));
        ensureEntityTickingChunks(helper, minChunk, maxChunk);

        for (int x = minX; x <= maxX; x++) {
            for (int z = start.getZ() - 1; z <= start.getZ() + 1; z++) {
                BlockPos feet = new BlockPos(x, start.getY(), z);
                helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void ensureEntityTickingChunks(GameTestHelper helper, ChunkPos minChunk, ChunkPos maxChunk) {
        ServerLevel level = helper.getLevel();
        List<ChunkPos> chunks = new ArrayList<>();
        for (int chunkX = minChunk.x; chunkX <= maxChunk.x; chunkX++) {
            for (int chunkZ = minChunk.z; chunkZ <= maxChunk.z; chunkZ++) {
                ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                chunks.add(chunk);
                level.setChunkForced(chunkX, chunkZ, true);
            }
        }

        // GameTest only waits for the tiny template's own chunks before starting.
        // Our fixtures intentionally extend outside that template, so load those
        // chunks and apply the newly-added forced tickets before spawning AI mobs.
        for (ChunkPos chunk : chunks) {
            level.getChunk(chunk.x, chunk.z);
        }
        level.getChunkSource().tick(() -> true, false);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        level.getServer().managedBlock(() -> allEntityTicking(level, chunks) || System.nanoTime() >= deadline);
        helper.assertTrue(
                allEntityTicking(level, chunks),
                "GameTest arena chunks did not become entity-ticking after being forced: " + chunks
        );
    }

    private static boolean allEntityTicking(ServerLevel level, List<ChunkPos> chunks) {
        return chunks.stream().allMatch(chunk -> level.isPositionEntityTicking(
                new BlockPos(chunk.getMinBlockX(), level.getMinBuildHeight(), chunk.getMinBlockZ())
        ));
    }
}
