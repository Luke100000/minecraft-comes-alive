package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.minecraft.core.BlockPos;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class NavigationRecoveryGameTests {
    private NavigationRecoveryGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void recomputationSelectsRemainingEquivalentEndpoint(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 10));
        prepareFlatArea(helper, start, 9, 4);
        VillagerEntityMCA villager = spawnStationary(helper, start);
        try {
            Set<BlockPos> endpoints = new HashSet<>(Set.of(start.east(6).north(), start.east(6).south()));
            MultiTargetPositionTracker tracker = new MultiTargetPositionTracker() {
                public Set<BlockPos> getPathTargets(Mob mob) { return Set.copyOf(endpoints); }
                public boolean isReached(Mob mob, int distance) {
                    return endpoints.stream().anyMatch(pos -> pos.distManhattan(mob.blockPosition()) <= distance);
                }
                public Vec3 currentPosition() { return Vec3.atCenterOf(currentBlockPosition()); }
                public BlockPos currentBlockPosition() { return start.east(6); }
                public boolean isVisibleBy(LivingEntity entity) { return true; }
            };
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(tracker, 0.5F, 0));
            NavigationProbe navigation = new NavigationProbe(villager);
            Path initial = navigation.createPath(endpoints, 0);
            helper.assertTrue(initial != null && initial.canReach(), "initial equivalent endpoint search failed");
            navigation.moveTo(initial, 0.5D);
            BlockPos blocked = initial.getTarget();
            endpoints.remove(blocked);
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(blocked.above(y), Blocks.STONE.defaultBlockState(), 3);
            }
            navigation.recomputeNow();
            Path replacement = navigation.getPath();
            helper.assertTrue(replacement != null && replacement.canReach()
                            && endpoints.contains(replacement.getTarget()),
                    "recomputation lost the remaining equivalent endpoint: " + replacement);
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void fallStopsPathWithDistantLowNode(GameTestHelper helper) {
        checkFallRecovery(helper, false);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void fallKeepsNearbySafeLowWaypoint(GameTestHelper helper) {
        checkFallRecovery(helper, true);
    }

    private static void checkFallRecovery(GameTestHelper helper, boolean nearbyLanding) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 10));
        prepareFlatArea(helper, start, 8, 5);
        VillagerEntityMCA villager = spawnStationary(helper, start);
        try {
            BlockPos landing = start.east(nearbyLanding ? 2 : 6);
            List<Node> nodes = nearbyLanding
                    ? List.of(node(start.above(3)), node(landing))
                    : List.of(node(start.above(3)), node(start.east().above(3)),
                            node(start.east(2).above(3)), node(landing));
            Path path = new Path(nodes, landing, true);
            NavigationProbe navigation = new NavigationProbe(villager);
            navigation.moveTo(path, 0.5D);
            navigation.tick();
            helper.assertTrue(nearbyLanding
                            ? navigation.getPath() == path && path.getNextNodeIndex() == 1
                            : navigation.isDone(),
                    "fall recovery retained an unreachable high waypoint or discarded a nearby landing");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_review_reopen", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void openingRouteClearsExtendedSearchSuppression(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(35, 1, 35));
        BlockPos target = start.east(2);
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
                    helper.getLevel().setBlock(target.offset(dx, y, dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        VillagerEntityMCA villager = spawnStationary(helper, start);
        Config config = Config.getInstance();
        int previousHorizon = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            MCAGroundPathNavigation navigation = (MCAGroundPathNavigation) villager.getNavigation();
            Path blocked = navigation.createPath(target, 0);
            helper.assertTrue(blocked != null && !blocked.canReach(), "sealed detour fixture was reachable");
            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
            long searchesBefore = PathRequestDiagnostics.snapshot(villager).extendedSearches();
            BlockPos gap = target.east();
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(gap.above(y), Blocks.AIR.defaultBlockState(), 3);
            }
            navigation.shouldRecomputePath(gap);
            helper.assertTrue(navigation.consumeRetryInvalidation(target), "route opening did not wake the producer");
            Path reopened = navigation.createPath(target, 0);
            helper.assertTrue(PathRequestDiagnostics.snapshot(villager).extendedSearches() > searchesBefore
                            && reopened != null && reopened.canReach(),
                    "route opening retained the failed extended-search suppression");
        } finally {
            config.villagerPathfindingDistance = previousHorizon;
            villager.discard();
        }
        helper.succeed();
    }

    private static VillagerEntityMCA spawnStationary(GameTestHelper helper, BlockPos position) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(position)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        return villager;
    }

    @GameTest(batch = "mca_navigation_review_backoff", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 740)
    public static void unchangedExtendedFailuresBackOffWithoutRepeatingOrdinarySearch(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(12, 1, 12));
        BlockPos target = start.east(6);
        prepareFlatArea(helper, start, 10, 3);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(start.offset(dx, y, dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        VillagerEntityMCA villager = spawnStationary(helper, start);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
        long began = helper.getLevel().getGameTime();
        villager.getNavigation().createPath(target, 0);
        WalkTargetFailureMemory.record(villager, target, began);
        PathRequestDiagnostics.SearchSnapshot first = PathRequestDiagnostics.snapshot(villager);
        helper.assertTrue(first.extendedSearches() == 1, "fixture did not exhaust an extended search");
        // Every request is at the producer's normal stalled retry cadence. This
        // measures real finder calls, including their ordinary/extended horizon.
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - began;
            if (elapsed > 0 && elapsed % 100 == 0) {
                PathRequestDiagnostics.SearchSnapshot before = PathRequestDiagnostics.snapshot(villager);
                villager.getNavigation().createPath(target, 0);
                PathRequestDiagnostics.SearchSnapshot after = PathRequestDiagnostics.snapshot(villager);
                long expectedExtended = elapsed < 100 ? 1 : elapsed < 300 ? 2 : elapsed < 700 ? 3 : 4;
                helper.assertTrue(after.extendedSearches() == expectedExtended,
                        "unchanged failure repeated a full search too soon: " + after);
                if (after.extendedSearches() > before.extendedSearches()) {
                    helper.assertTrue(after.ordinarySearches() == before.ordinarySearches(),
                            "permitted extended retry expanded the ordinary frontier again");
                }
            }
            if (elapsed >= 701) {
                villager.discard();
                helper.succeed();
            }
        });
    }

    private static Node node(BlockPos position) {
        return new Node(position.getX(), position.getY(), position.getZ());
    }

    private static final class NavigationProbe extends MCAGroundPathNavigation {
        private NavigationProbe(VillagerEntityMCA mob) {
            super(mob, mob.level());
        }

        private void recomputeNow() {
            this.timeLastRecompute = this.level.getGameTime() - 21L;
            recomputePath();
        }
    }
}
