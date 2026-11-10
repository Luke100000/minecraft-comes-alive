package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.minecraft.core.BlockPos;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class EnterBuildingRetryGameTests {
    private EnterBuildingRetryGameTests() {
    }

    @GameTest(batch = "mca_enter_building_failed_search_retry_base", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 155)
    public static void inaccessibleBuildingSearchesBackOffAndEventuallyRetry(GameTestHelper helper) {
        assertFailedBuildingRetry(helper, false);
    }

    @GameTest(batch = "mca_enter_building_failed_search_retry_favored", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 155)
    public static void inaccessibleFavoredBuildingSearchesBackOffAndEventuallyRetry(GameTestHelper helper) {
        assertFailedBuildingRetry(helper, true);
    }

    private static void assertFailedBuildingRetry(GameTestHelper helper, boolean favored) {
        BlockPos start = helper.absolutePos(new BlockPos(12, 1, 12));
        BlockPos target = start.east(6);
        prepareFlatArea(helper, start, 10, 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(target.offset(dx, y, dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        EnterBuildingTask task = favored
                ? new FixedFavoredBuildingTask(target)
                : new FixedBuildingTask(target);
        long began = helper.getLevel().getGameTime();
        int[] attempts = {0};
        long[] searchesAfterFirst = {-1L};

        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - began;
            // A foreign short walk may clear global failure memory without the
            // actor having advanced enough to justify another building search.
            if (elapsed == 30) {
                WalkTargetFailureMemory.clear(villager);
            }
            if (elapsed >= 1 && elapsed <= 121
                    && task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime())) {
                attempts[0]++;
                var walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
                helper.assertTrue(walkTarget != null
                                && walkTarget.getTarget().currentBlockPosition().equals(target),
                        "building task failed to publish its selected persistent destination");
                var path = villager.getNavigation().createPath(target, 0);
                helper.assertTrue(path == null || !path.canReach(),
                        "fixture unexpectedly permitted access to the enclosed building target");
                WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                task.doStop(helper.getLevel(), villager, helper.getLevel().getGameTime());
                if (searchesAfterFirst[0] < 0) {
                    var stats = PathRequestDiagnostics.snapshot(villager);
                    searchesAfterFirst[0] = stats.ordinarySearches() + stats.extendedSearches();
                }
            }
            if (elapsed == 25 || elapsed == 90) {
                var stats = PathRequestDiagnostics.snapshot(villager);
                long searches = stats.ordinarySearches() + stats.extendedSearches();
                helper.assertTrue(attempts[0] == 1 && searchesAfterFirst[0] > 0
                                && searches == searchesAfterFirst[0],
                        "inaccessible building repeated pathfinder searches during cooldown: "
                                + attempts[0] + " visits, " + searches + " searches");
            }
            if (elapsed == 121) {
                var stats = PathRequestDiagnostics.snapshot(villager);
                long searches = stats.ordinarySearches() + stats.extendedSearches();
                helper.assertTrue(attempts[0] >= 2 && attempts[0] <= 3
                                && searches > searchesAfterFirst[0],
                        "unchanged inaccessible building never retried after bounded cooldown: "
                                + attempts[0] + " visits, " + searches + " searches");
                villager.discard();
                helper.succeed();
            }
        });
    }

    private static final class FixedBuildingTask extends EnterBuildingTask {
        private final BlockPos target;

        private FixedBuildingTask(BlockPos target) {
            super("house", 0.5F);
            this.target = target;
        }

        @Override
        protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
            return Optional.of(target);
        }
    }

    private static final class FixedFavoredBuildingTask extends EnterFavoredBuildingTask {
        private final BlockPos target;

        private FixedFavoredBuildingTask(BlockPos target) {
            super(0.5F);
            this.target = target;
        }

        @Override
        protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
            return Optional.of(target);
        }
    }
}
