package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;
@PrefixGameTestTemplate(false)
public final class NavigationRecoveryGameTests {
    private NavigationRecoveryGameTests() {
    }

    @GameTest(batch = "mca_detour_failure_age", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void sidewaysDetourDoesNotEraseDestinationFailure(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(65, 1, 65));
        BlockPos target = start.east(2);
        prepareFlatArea(helper, start, 61, 3);
        for (int z = -60; z <= 60; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA villager = spawnStationary(helper, start);
        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            villager.getBrain().setMemory(MemoryModuleType.HOME,
                    GlobalPos.of(helper.getLevel().dimension(), target));
            var producer = ExtendedWalkTowardsTask.createWithoutPoiRelease(
                    MemoryModuleType.HOME, 0.5F, 0, 19, ignored -> false, ignored -> {});
            long began = helper.getLevel().getGameTime();
            producer.tryStart(helper.getLevel(), villager, began);
            var navigation = villager.getNavigation();
            Path blocked = navigation.createPath(target, 0);
            helper.assertTrue(blocked != null && !blocked.canReach()
                            && !MCAGroundPathNavigation.isUsefulPartialPath(blocked, target),
                    "fixture did not exhaust the route at the wall");
            WalkTargetFailureMemory.record(villager, target, began);
            navigation.moveTo(blocked, 0.5D);
            while (!blocked.isDone()) {
                blocked.advance();
            }
            navigation.tick();
            Path flank = navigation.getPath();
            helper.assertTrue(flank != null && flank.canReach() && !flank.getTarget().equals(target),
                    "fixture did not start a sideways recovery leg");
            // Advance the lifecycle to a real point on the selected flank. This
            // checks failure ownership, rather than villager travel physics.
            BlockPos sideways = flank.getNodePos(flank.getNodeCount() / 2);
            helper.assertTrue(sideways.distSqr(start) >= 16.0D,
                    "flank did not move beyond the producer's progress threshold");
            villager.setPos(Vec3.atBottomCenterOf(sideways));
            producer.tryStart(helper.getLevel(), villager, began + 1L);
            helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, target)
                            && villager.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                            .filter(since -> since == began).isPresent(),
                    "sideways movement erased the unresolved destination's failure age");
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow()
                            .getTarget().currentBlockPosition().equals(target),
                    "preserving failure evidence interrupted the logical journey");
        } finally {
            config.villagerPathfindingDistance = previousDistance;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_owned_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedPersistentPathSkipsDestinationProbeDuringExtendedBackoff(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(65, 1, 65));
        BlockPos target = start.east(2);
        prepareFlatArea(helper, start, 65, 3);
        for (int z = -60; z <= 60; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA villager = spawnStationary(helper, start);
        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            var navigation = villager.getNavigation();
            Path blocked = navigation.createPath(target, 0);
            helper.assertTrue(blocked != null && !blocked.canReach()
                            && !MCAGroundPathNavigation.isUsefulPartialPath(blocked, target),
                    "fixture did not exhaust the destination search: " + blocked);
            PathRequestDiagnostics.SearchSnapshot before = PathRequestDiagnostics.snapshot(villager);
            helper.assertTrue(before.extendedSearches() == 1,
                    "fixture did not perform exactly one extended search: " + before);
            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
            navigation.moveTo(blocked, 0.5D);
            while (!blocked.isDone()) {
                blocked.advance();
            }
            navigation.tick();
            Path flank = navigation.getPath();
            helper.assertTrue(flank != null && !flank.isDone() && flank.canReach()
                            && !flank.getTarget().equals(target),
                    "navigation did not start a reachable flank: " + flank);
            PathRequestDiagnostics.SearchSnapshot after = PathRequestDiagnostics.snapshot(villager);
            helper.assertTrue(after.extendedSearches() == before.extendedSearches(),
                    "detour startup repeated the exhausted extended search: " + after);
            helper.assertTrue(after.ordinarySearches() == before.ordinarySearches() + 1,
                    "detour startup searched the destination again instead of only finding the flank: before="
                            + before + ", after=" + after);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow()
                            .getTarget().currentBlockPosition().equals(target)
                            && WalkTargetFailureMemory.hasFailureFor(villager, target),
                    "flank selection changed the logical destination or erased its failure");
        } finally {
            config.villagerPathfindingDistance = previousDistance;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_owned_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedPersistentPathRetriesDestinationWhenExtendedHorizonIncreases(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(65, 1, 65));
        BlockPos target = start.east(2);
        prepareFlatArea(helper, start, 61, 3);
        for (int z = -23; z <= 23; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA villager = spawnStationary(helper, start);
        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 49;
            villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            var navigation = villager.getNavigation();
            Path blocked = navigation.createPath(target, 0);
            helper.assertTrue(blocked != null && !blocked.canReach()
                            && !MCAGroundPathNavigation.isUsefulPartialPath(blocked, target)
                            && PathRequestDiagnostics.snapshot(villager).extendedSearches() == 1,
                    "fixture did not exhaust the smaller extended search: " + blocked);
            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
            config.villagerPathfindingDistance = 160;
            navigation.moveTo(blocked, 0.5D);
            while (!blocked.isDone()) {
                blocked.advance();
            }
            navigation.tick();
            Path recovered = navigation.getPath();
            helper.assertTrue(recovered != null && !recovered.isDone() && recovered.canReach()
                            && recovered.getTarget().equals(target),
                    "larger search horizon retained stale backoff and started a sideways detour: " + recovered);
        } finally {
            config.villagerPathfindingDistance = previousDistance;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_owned_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedPersistentPathRetriesDestinationWhenRouteOpens(GameTestHelper helper) {
        assertCompletedPathRetriesOpenedRoute(helper, 2, 0);
    }

    @GameTest(batch = "mca_navigation_owned_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedPersistentPathRetriesDestinationWhenRouteOpensNearOrigin(GameTestHelper helper) {
        assertCompletedPathRetriesOpenedRoute(helper, 20, 0);
    }

    @GameTest(batch = "mca_navigation_owned_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedPersistentPathRetriesDestinationWhenSideRouteOpens(GameTestHelper helper) {
        assertCompletedPathRetriesOpenedRoute(helper, 2, 10);
    }

    private static void assertCompletedPathRetriesOpenedRoute(GameTestHelper helper, int destinationDistance,
                                                              int gapOffset) {
        BlockPos start = helper.absolutePos(new BlockPos(65, 1, 65));
        BlockPos target = start.east(destinationDistance);
        prepareFlatArea(helper, start, 65, 3);
        for (int z = -60; z <= 60; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA villager = spawnStationary(helper, start);
        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            MCAGroundPathNavigation navigation = (MCAGroundPathNavigation) villager.getNavigation();
            Path blocked = navigation.createPath(target, 0);
            helper.assertTrue(blocked != null && !blocked.canReach()
                            && !MCAGroundPathNavigation.isUsefulPartialPath(blocked, target)
                            && PathRequestDiagnostics.snapshot(villager).extendedSearches() == 1,
                    "fixture did not exhaust the destination search: " + blocked);
            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
            navigation.moveTo(blocked, 0.5D);
            while (!blocked.isDone()) {
                blocked.advance();
            }
            BlockPos gap = start.east().south(gapOffset);
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(gap.above(y), Blocks.AIR.defaultBlockState(), 3);
            }
            VillagerEntityMCA probe = spawnStationary(helper, start);
            try {
                probe.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 0));
                Path opened = probe.getNavigation().createPath(target, 0);
                helper.assertTrue(opened != null && opened.canReach(),
                        "opened route was not reachable by an ordinary destination probe: " + opened);
            } finally {
                probe.discard();
            }
            navigation.shouldRecomputePath(gap);
            navigation.tick();
            Path recovered = navigation.getPath();
            helper.assertTrue(recovered != null && !recovered.isDone() && recovered.canReach()
                            && recovered.getTarget().equals(target)
                            && !WalkTargetFailureMemory.hasFailureFor(villager, target),
                    "opened route retained stale backoff or failed to clear destination failure: " + recovered);
        } finally {
            config.villagerPathfindingDistance = previousDistance;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_owned_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedPersistentPathStartsDetourWhenConfiguredSearchCannotReach(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(65, 1, 65));
        BlockPos target = start.east(2);
        prepareFlatArea(helper, start, 61, 3);
        for (int z = -55; z <= 55; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA villager = spawnStationary(helper, start);
        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 32;
            villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            var navigation = villager.getNavigation();
            Path blocked = navigation.createPath(target, 0);
            helper.assertTrue(blocked != null && !blocked.canReach()
                            && !MCAGroundPathNavigation.isUsefulPartialPath(blocked, target),
                    "fixture did not exhaust the search at the long wall: " + blocked);
            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
            navigation.moveTo(blocked, 0.5D);
            while (!blocked.isDone()) {
                blocked.advance();
            }
            navigation.tick();
            Path flank = navigation.getPath();
            helper.assertTrue(flank != null && !flank.isDone() && flank.canReach()
                            && !flank.getTarget().equals(target),
                    "navigation did not own long-wall recovery without the MCA sink: " + flank);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow()
                            .getTarget().currentBlockPosition().equals(target),
                    "navigation replaced the logical destination with a flank waypoint");
        } finally {
            config.villagerPathfindingDistance = previousDistance;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_owned_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedPersistentPathPrefersReachableExtendedRouteBeforeSidewaysDetour(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(65, 1, 65));
        BlockPos target = start.east(2);
        prepareFlatArea(helper, start, 61, 3);
        for (int z = -23; z <= 23; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA villager = spawnStationary(helper, start);
        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 32;
            villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            var navigation = villager.getNavigation();
            Path blocked = navigation.createPath(target, 0);
            helper.assertTrue(blocked != null && !blocked.canReach()
                            && !MCAGroundPathNavigation.isUsefulPartialPath(blocked, target),
                    "fixture did not exhaust the ordinary search at the long wall: " + blocked);
            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
            config.villagerPathfindingDistance = 160;
            navigation.moveTo(blocked, 0.5D);
            while (!blocked.isDone()) {
                blocked.advance();
            }
            navigation.tick();
            Path recovered = navigation.getPath();
            helper.assertTrue(recovered != null && !recovered.isDone() && recovered.canReach()
                            && recovered.getTarget().equals(target),
                    "navigation started a sideways detour even though its extended search could reach the destination: "
                            + recovered);
        } finally {
            config.villagerPathfindingDistance = previousDistance;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_owned_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedPersistentPathPrefersUsefulExtendedRouteBeforeSidewaysDetour(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(65, 1, 65));
        BlockPos target = start.east(8);
        prepareFlatArea(helper, start, 61, 3);
        for (int z = -23; z <= 23; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(target.offset(dx, y, dz), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }

        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        VillagerEntityMCA villager = spawnStationary(helper, start);
        try {
            config.villagerPathfindingDistance = 32;
            villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            var navigation = villager.getNavigation();
            Path blocked = navigation.createPath(target, 0);
            helper.assertTrue(blocked != null && !blocked.canReach()
                            && !MCAGroundPathNavigation.isUsefulPartialPath(blocked, target),
                    "fixture ordinary search unexpectedly made useful progress: " + blocked);
            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());

            config.villagerPathfindingDistance = 160;
            VillagerEntityMCA probe = spawnStationary(helper, start);
            try {
                probe.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
                probe.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
                WalkTargetFailureMemory.record(probe, target, helper.getLevel().getGameTime());
                Path extended = probe.getNavigation().createPath(target, 0);
                helper.assertTrue(MCAGroundPathNavigation.isUsefulPartialPath(extended, target),
                        "fixture extended search did not produce a useful partial route: " + extended);
            } finally {
                probe.discard();
            }

            navigation.moveTo(blocked, 0.5D);
            while (!blocked.isDone()) {
                blocked.advance();
            }
            navigation.tick();
            Path recovered = navigation.getPath();
            helper.assertTrue(MCAGroundPathNavigation.isUsefulPartialPath(recovered, target),
                    "navigation discarded a useful extended route in favor of a sideways detour: " + recovered);
            helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, target),
                    "useful partial route cleared destination failure before the villager made progress");
        } finally {
            config.villagerPathfindingDistance = previousDistance;
            villager.discard();
        }
        helper.succeed();
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
                .withAge(0).withPosition(Vec3.atBottomCenterOf(position)).spawn(MobSpawnType.STRUCTURE);
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
