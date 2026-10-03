package net.conczin.mca.benchmark;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.minecraft.core.BlockPos;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Identical open-query-only Spark fixture for baseline and current MCA. */
@PrefixGameTestTemplate(false)
public final class OpenPathABGameTests {
    private static final String BATCH = "zz_mca_ab_open";
    private static final int SEARCHES_PER_VILLAGER = 400;
    private static final List<VillagerEntityMCA> VILLAGERS = new ArrayList<>();
    private static final List<BlockPos> TARGETS = new ArrayList<>();
    private static int previousPathDistance;

    private OpenPathABGameTests() {
    }

    @GameTest(batch = BATCH, templateNamespace = "mca_ab", template = "ab_air", timeoutTicks = 450)
    public static void profileOnlyReachableOpenPaths(GameTestHelper helper) {
        ThreadMXBean cpu = ManagementFactory.getThreadMXBean();
        helper.assertTrue(cpu.isCurrentThreadCpuTimeSupported(), "thread CPU timer unavailable");
        if (!cpu.isThreadCpuTimeEnabled()) {
            cpu.setThreadCpuTimeEnabled(true);
        }
        previousPathDistance = Config.getInstance().villagerPathfindingDistance;
        MCA.LOGGER.info("[MCA AB OPEN] debugInstrumentationOptOut={}",
                Boolean.getBoolean("mca.pathDebug.benchmarkWithoutDiagnostics"));
        Config.getInstance().villagerPathfindingDistance = 160;
        BlockPos center = helper.absolutePos(new BlockPos(60, 1, 60));
        prepareFlatArea(helper, center, 24);
        for (int i = 0; i < 6; i++) {
            BlockPos origin = center.offset(-3 - i * 2, 0, -6 + (i / 2) * 4);
            BlockPos destination = origin.west(6);
            VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0).withPosition(Vec3.atBottomCenterOf(origin))
                    .withName("AB open path " + i).spawn(EntitySpawnReason.STRUCTURE);
            villager.setNoAi(true);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(destination, 0.75F, 0));
            TARGETS.add(destination);
            VILLAGERS.add(villager);
        }

        long initialProfiles = profileCount();
        long startTick = helper.getLevel().getGameTime();
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - startTick;
            if (elapsed >= 30 && elapsed <= 60 && elapsed % 5 == 0) {
                for (int i = 0; i < VILLAGERS.size(); i++) {
                    VillagerEntityMCA v = VILLAGERS.get(i);
                    v.setOnGround(true);
                    v.getNavigation().stop();
                    v.getNavigation().createPath(TARGETS.get(i), 0);
                }
            }
            if (elapsed == 100) {
                profile(helper, "start --thread * --interval 1");
            }
            if (elapsed == 105) {
                long wallStart = System.nanoTime();
                long cpuStart = cpu.getCurrentThreadCpuTime();
                int reachable = 0;
                int partial = 0;
                int nullPaths = 0;
                for (int j = 0; j < SEARCHES_PER_VILLAGER; j++) {
                    for (int i = 0; i < VILLAGERS.size(); i++) {
                        VillagerEntityMCA v = VILLAGERS.get(i);
                        v.setOnGround(true);
                        v.getNavigation().stop();
                        Path path = v.getNavigation().createPath(TARGETS.get(i), 0);
                        if (path == null) {
                            nullPaths++;
                        } else if (path.canReach()) {
                            reachable++;
                        } else {
                            partial++;
                        }
                    }
                }
                double cpuMs = (cpu.getCurrentThreadCpuTime() - cpuStart) / 1_000_000.0;
                double wallMs = (System.nanoTime() - wallStart) / 1_000_000.0;
                MCA.LOGGER.info("[MCA AB OPEN] calls={} wallMs={} cpuMs={} reachable={} partial={} null={}",
                        VILLAGERS.size() * SEARCHES_PER_VILLAGER, wallMs, cpuMs,
                        reachable, partial, nullPaths);
                helper.assertTrue(reachable == 6 * SEARCHES_PER_VILLAGER,
                        "open-path fixture returned partial or null paths");
                profile(helper, "stop --save-to-file");
            }
            if (elapsed > 120 && profileCount() > initialProfiles) {
                helper.succeed();
            } else if (elapsed > 105 && profileCount() <= initialProfiles) {
                // The GameTest clock runs faster than Spark's asynchronous file writer.
                try {
                    Thread.sleep(25);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        });
    }

    private static void profile(GameTestHelper helper, String args) {
        helper.getLevel().getServer().getCommands().performPrefixedCommand(
                helper.getLevel().getServer().createCommandSourceStack(), "spark profiler " + args);
    }

    private static long profileCount() {
        java.nio.file.Path directory = java.nio.file.Path.of("config", "spark");
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        try (var files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".sparkprofile")).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private static void prepareFlatArea(GameTestHelper helper, BlockPos center, int radius) {
        ServerLevel level = helper.getLevel();
        ChunkPos first = ChunkPos.containing(center.offset(-radius, 0, -radius));
        ChunkPos last = ChunkPos.containing(center.offset(radius, 0, radius));
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = first.x(); x <= last.x(); x++) {
            for (int z = first.z(); z <= last.z(); z++) {
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
                chunks.add(new ChunkPos(x, z));
            }
        }
        level.getChunkSource().tick(() -> true, false);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        level.getServer().managedBlock(() -> chunks.stream().allMatch(chunk ->
                level.isPositionEntityTicking(new BlockPos(
                        chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ())))
                || System.nanoTime() >= deadline);
        helper.assertTrue(chunks.stream().allMatch(chunk -> level.isPositionEntityTicking(
                        new BlockPos(chunk.getMinBlockX(), level.getMinY(), chunk.getMinBlockZ()))),
                "open-only benchmark chunks are not entity ticking");
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos feet = center.offset(dx, 0, dz);
                level.setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        VILLAGERS.forEach(VillagerEntityMCA::discard);
        VILLAGERS.clear();
        TARGETS.clear();
        Config.getInstance().villagerPathfindingDistance = previousPathDistance;
    }
}
