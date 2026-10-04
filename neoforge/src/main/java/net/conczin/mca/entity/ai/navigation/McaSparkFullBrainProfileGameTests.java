package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/** Temporary Spark fixture: actual MCA Brain, including sensors and activity scheduling. */
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class McaSparkFullBrainProfileGameTests {
    private static final String BATCH = "zz_mca_spark_full_brain";
    private static final List<VillagerEntityMCA> VILLAGERS = new ArrayList<>();
    private static final Path SPARK_DIRECTORY = Path.of("config", "spark");

    private McaSparkFullBrainProfileGameTests() {
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air",
            timeoutTicks = 12000)
    public static void captureFullMcaBrainAiStepWithSpark(GameTestHelper helper) {
        helper.assertTrue(PathRequestDiagnostics.enabled(), "development diagnostics must be enabled");
        helper.assertTrue(Files.isDirectory(SPARK_DIRECTORY), "install Spark in run-gametest/mods first");

        BlockPos center = helper.absolutePos(new BlockPos(180, 1, 180));
        prepareFlatArea(helper, center, 38, 3);

        // Two usable fence gates in an otherwise obstructing village boundary.
        for (int z = -24; z <= 24; z++) {
            BlockPos wall = center.offset(0, 0, z);
            if (z == -12 || z == 12) {
                helper.getLevel().setBlock(wall, Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);
            } else {
                helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        BlockPos meetingPoint = center.offset(-4, 0, 0);
        helper.getLevel().setBlock(meetingPoint, Blocks.BELL.defaultBlockState(), 3);

        BlockState bedFoot = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.SOUTH)
                .setValue(BedBlock.PART, BedPart.FOOT);
        for (int i = 0; i < 24; i++) {
            boolean west = i % 2 == 0;
            int z = -17 + (i / 2) * 3;
            BlockPos origin = center.offset(west ? -17 : 17, 0, z);
            BlockPos home = center.offset(west ? 19 : -19, 0, z);
            helper.getLevel().setBlock(home, bedFoot, 3);
            helper.getLevel().setBlock(home.south(), bedFoot.setValue(BedBlock.PART, BedPart.HEAD), 3);

            VillagerFactory factory = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0)
                    .withPosition(Vec3.atBottomCenterOf(origin))
                    .withName("Spark full Brain MCA " + i);
            if (i % 6 == 0) {
                factory.withProfession(ProfessionsMCA.GUARD);
            } else if (i % 3 == 0) {
                factory.withProfession(VillagerProfession.FARMER);
            }
            VillagerEntityMCA villager = factory.spawn(MobSpawnType.STRUCTURE);
            VILLAGERS.add(villager);
            villager.refreshBrain(helper.getLevel());
            // Do not replace or remove MCA Brain activities: this is the control
            // workload for the live aiStep -> Brain.tick behavior-start hotspot.
            villager.getBrain().setMemory(MemoryModuleType.HOME,
                    GlobalPos.of(helper.getLevel().dimension(), home));
            if (i % 3 == 0 && i % 6 != 0) {
                BlockPos jobSite = home.offset(west ? -3 : 3, 0, 0);
                helper.getLevel().setBlock(jobSite, Blocks.COMPOSTER.defaultBlockState(), 3);
                villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                        GlobalPos.of(helper.getLevel().dimension(), jobSite));
            }
            if (i % 3 == 1) {
                villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT,
                        GlobalPos.of(helper.getLevel().dimension(), meetingPoint));
            }
        }

        long initialProfileCount = savedProfileCount();
        long started = helper.getLevel().getGameTime();
        MCA.LOGGER.info("[MCA Spark Full Brain] ready villagers=24 normalBrain=true gates=2 warmup=100 sample=650 slowTickThresholdMs=20 previousProfiles={}",
                initialProfileCount);
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - started;
            if (elapsed == 100L) {
                helper.getLevel().getServer().getCommands().performPrefixedCommand(
                        helper.getLevel().getServer().createCommandSourceStack(),
                        "spark profiler start --thread * --interval 4 --only-ticks-over 20");
                MCA.LOGGER.info("[MCA Spark Full Brain] sample started tick={}", elapsed);
            }
            if (elapsed == 750L) {
                helper.getLevel().getServer().getCommands().performPrefixedCommand(
                        helper.getLevel().getServer().createCommandSourceStack(),
                        "spark profiler stop --save-to-file");
                long sink = 0, direct = 0, ordinary = 0, extended = 0;
                long searchNanos = 0;
                int walking = 0;
                for (VillagerEntityMCA villager : VILLAGERS) {
                    PathRequestDiagnostics.SearchSnapshot s = PathRequestDiagnostics.snapshot(villager);
                    sink += s.sinkRequests();
                    direct += s.directRequests();
                    ordinary += s.ordinarySearches();
                    extended += s.extendedSearches();
                    searchNanos += s.searchNanos();
                    if (villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                        walking++;
                    }
                }
                MCA.LOGGER.info("[MCA Spark Full Brain] totals villagers={} activeWalkTargets={} sink={} direct={} ordinary={} extended={} searchMs={}",
                        VILLAGERS.size(), walking, sink, direct, ordinary, extended, searchNanos / 1_000_000.0D);
                helper.assertTrue(ordinary + extended > 0, "full-Brain fixture did not exercise MCA pathfinding");
            }
            // Spark writes .sparkprofile asynchronously. Keep the server alive
            // until the capture exists, rather than racing shutdown of its worker.
            if (elapsed > 750L && elapsed % 20L == 0L && savedProfileCount() > initialProfileCount) {
                MCA.LOGGER.info("[MCA Spark Full Brain] local .sparkprofile saved after {} fixture ticks", elapsed);
                helper.succeed();
            }
        });
    }

    private static long savedProfileCount() {
        try (var files = Files.list(SPARK_DIRECTORY)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".sparkprofile")).count();
        } catch (IOException e) {
            return 0L;
        }
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(net.minecraft.server.level.ServerLevel level) {
        VILLAGERS.forEach(VillagerEntityMCA::discard);
        VILLAGERS.clear();
    }
}
