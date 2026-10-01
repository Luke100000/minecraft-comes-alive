package net.conczin.mca.entity.ai.navigation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/** Temporary, controlled MCA-only path stress fixture for an offline Spark capture. */
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class McaSparkPathProfileGameTests {
    private static final String BATCH = "zz_mca_spark_path_profile";
    private static final List<VillagerEntityMCA> VILLAGERS = new ArrayList<>();
    private static int previousPathDistance;

    private McaSparkPathProfileGameTests() { }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air",
            timeoutTicks = 650)
    public static void captureRepeatedBlockedMcaPathsWithSpark(GameTestHelper helper) {
        helper.assertTrue(PathRequestDiagnostics.enabled(), "MCA path diagnostics must be active");
        previousPathDistance = Config.getInstance().villagerPathfindingDistance;
        Config.getInstance().villagerPathfindingDistance = 160;
        BlockPos start = helper.absolutePos(new BlockPos(180, 1, 180));
        BlockPos destination = start.east(3);
        prepareFlatArea(helper, start, 30, 3);
        for (int z = -23; z <= 23; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(destination.offset(dx, y, dz),
                            Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }

        for (int i = 0; i < 24; i++) {
            BlockPos origin = start.offset(-(i % 6) * 2, 0, (i / 6) * 3 - 5);
            VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0).withPosition(Vec3.atBottomCenterOf(origin))
                    .withName("Spark MCA path probe " + i).spawn(MobSpawnType.STRUCTURE);
            VILLAGERS.add(villager);
            villager.refreshBrain(helper.getLevel());
            @SuppressWarnings("unchecked")
            Brain<VillagerEntityMCA> brain = (Brain<VillagerEntityMCA>)(Brain<?>)villager.getBrain();
            brain.stopAll(helper.getLevel(), villager);
            brain.removeAllBehaviors();
            brain.addActivity(Activity.CORE, 1, ImmutableList.of(new WanderOrTeleportToTargetTask()));
            brain.addActivity(Activity.CORE, 2, ImmutableList.of(
                    ExtendedWalkTowardsTask.createWithoutPoiRelease(
                            MemoryModuleType.HOME, 0.75F, 0, 1200, ignored -> false, ignored -> { })));
            brain.addActivity(Activity.IDLE, 20, ImmutableList.of(new DoNothing(100, 100)));
            brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
            brain.setDefaultActivity(Activity.IDLE);
            brain.setSchedule(new ScheduleBuilder(new Schedule())
                    .changeActivityAt(0, Activity.IDLE).build());
            brain.setActiveActivityIfPossible(Activity.IDLE);
            brain.setMemory(MemoryModuleType.HOME,
                    GlobalPos.of(helper.getLevel().dimension(), destination));
        }

        long startedAt = helper.getLevel().getGameTime();
        MCA.LOGGER.info("[MCA Spark Stress] fixture ready villagers=24 target={} warmup=60 ticks sample=420 ticks",
                destination);
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - startedAt;
            if (elapsed == 60L) {
                helper.getLevel().getServer().getCommands().performPrefixedCommand(
                        helper.getLevel().getServer().createCommandSourceStack(),
                        "spark profiler start --thread * --interval 4");
                MCA.LOGGER.info("[MCA Spark Stress] spark start requested tick={}", elapsed);
            }
            if (elapsed == 480L) {
                helper.getLevel().getServer().getCommands().performPrefixedCommand(
                        helper.getLevel().getServer().createCommandSourceStack(),
                        "spark profiler stop --save-to-file");
                MCA.LOGGER.info("[MCA Spark Stress] spark local save requested tick={}", elapsed);
                long sinkRequests = 0, directRequests = 0, searches = 0, extended = 0, suppressed = 0;
                long searchNanos = 0;
                for (VillagerEntityMCA v : VILLAGERS) {
                    PathRequestDiagnostics.SearchSnapshot snapshot = PathRequestDiagnostics.snapshot(v);
                    sinkRequests += snapshot.sinkRequests();
                    directRequests += snapshot.directRequests();
                    searches += snapshot.ordinarySearches();
                    extended += snapshot.extendedSearches();
                    suppressed += snapshot.suppressedExtended();
                    searchNanos += snapshot.searchNanos();
                }
                MCA.LOGGER.info("[MCA Spark Stress] totals villagers={} sinkRequests={} directRequests={} ordinarySearches={} extendedSearches={} suppressedExtended={} searchMs={}",
                        VILLAGERS.size(), sinkRequests, directRequests, searches, extended,
                        suppressed, searchNanos / 1_000_000.0D);
            }
            // Leave ample time for Spark's asynchronous local .sparkprofile write.
            if (elapsed == 570L) {
                helper.succeed();
            }
        });
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(net.minecraft.server.level.ServerLevel level) {
        VILLAGERS.forEach(VillagerEntityMCA::discard);
        VILLAGERS.clear();
        Config.getInstance().villagerPathfindingDistance = previousPathDistance;
    }
}
