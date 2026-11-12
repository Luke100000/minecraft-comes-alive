package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
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

    @GameTest(batch = "mca_nearby_persistent_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void nearbyPersistentIntentFindsItsWalkableDetour(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(40, 1, 40));
        BlockPos target = start.east(2);
        prepareFlatArea(helper, start, 35, 3);
        for (int z = -31; z <= 31; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA villager = spawnStationary(helper, start);
        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            Path route = ((MCAGroundPathNavigation) villager.getNavigation())
                    .createPathForPersistentIntent(Set.of(target), 0);
            helper.assertTrue(route != null && route.canReach() && route.getEndNode().asBlockPos().equals(target),
                    "A nearby walkable destination was denied its native detour search");
        } finally {
            config.villagerPathfindingDistance = previousDistance;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_short_partial_continuation", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedOneBlockClimbContinuesPersistentJourney(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos endpoint = start.east().above();
        BlockPos target = start.east(100).above();
        net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath(helper, start, target);
        helper.getLevel().setBlock(endpoint.below(), Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA villager = spawnStationary(helper, start);
        try {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            Path completed = new Path(List.of(node(start), node(endpoint)), target, false);
            helper.assertTrue(MCAGroundPathNavigation.isUsefulPartialPath(completed, target),
                    "Fixture must exercise a useful short partial route");
            villager.getNavigation().moveTo(completed, 0.5D);
            villager.setPos(Vec3.atBottomCenterOf(endpoint));
            while (!completed.isDone()) completed.advance();
            villager.getNavigation().tick();
            Path continuation = villager.getNavigation().getPath();
            helper.assertTrue(continuation != null && continuation != completed && !continuation.isDone(),
                    "A physically completed one-block climb was rejected as stationary instead of continuing");
            helper.assertTrue(continuation.getTarget().equals(target)
                            && continuation.getEndNode().asBlockPos().distManhattan(target) < endpoint.distManhattan(target),
                    "The short climb's continuation lost forward progress toward its logical destination");
            helper.assertTrue(!WalkTargetFailureMemory.hasFailureFor(villager, target),
                    "A usable short-climb continuation was marked terminally unreachable");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_owned_bedside_partial_continuation", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedOwnedBedsidePartialContinuesToPhysicalApproach(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(16, 1, 16));
        BlockPos bedHead = start.east(8);
        prepareFlatArea(helper, start, 16, 3);
        var bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        helper.getLevel().setBlock(bedHead.west(), bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        helper.getLevel().setBlock(bedHead, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);

        VillagerEntityMCA villager = spawnStationary(helper, start);
        try {
            villager.getBrain().setMemory(MemoryModuleType.HOME,
                    GlobalPos.of(helper.getLevel().dimension(), bedHead));
            OneShot<VillagerEntityMCA> producer = ExtendedWalkTowardsTask.createWithFinalTarget(
                    MemoryModuleType.HOME, 0.5F, 0, 1_000,
                    ignored -> false, ignored -> { },
                    (world, entity, destination) -> BedApproachTarget.create(world, destination.pos()));
            producer.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());
            WalkTarget walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                    .orElseThrow(() -> new AssertionError("HOME producer did not publish its bedside target"));
            helper.assertTrue(walkTarget instanceof WalkTargetFailureMemory.TargetIdentityOwner
                            && walkTarget.getTarget() instanceof BedApproachTarget,
                    "fixture did not publish a producer-owned semantic bedside target");

            BlockPos approach = start.east(6);
            helper.assertTrue(((BedApproachTarget) walkTarget.getTarget()).getPathTargets(villager).contains(approach),
                    "fixture lacks a physical bedside endpoint");
            BlockPos endpoint = start.east();
            Path completed = new Path(List.of(node(start), node(endpoint)), approach, false);
            helper.assertTrue(MCAGroundPathNavigation.isUsefulPartialPath(completed, approach),
                    "fixture must represent useful progress toward the physical approach");
            villager.getNavigation().moveTo(completed, 0.5D);
            villager.setPos(Vec3.atBottomCenterOf(endpoint));
            while (!completed.isDone()) completed.advance();
            villager.getNavigation().tick();

            Path continuation = villager.getNavigation().getPath();
            helper.assertTrue(continuation != null && continuation != completed
                            && !continuation.isDone() && continuation.canReach()
                            && ((BedApproachTarget) walkTarget.getTarget()).getPathTargets(villager)
                            .contains(continuation.getTarget()),
                    "completed owned partial failed to continue toward a physical bedside endpoint: " + continuation);
            helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                            .filter(current -> current == walkTarget).isPresent(),
                    "partial continuation replaced the producer's logical HOME ownership");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_failed_short_partial_progress", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void priorFailureDoesNotBlockPhysicallyCompletedUsefulShortPartial(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos endpoint = start.east().above();
        BlockPos target = start.east(100).above();
        net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath(helper, start, target);
        helper.getLevel().setBlock(endpoint.below(), Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA villager = spawnStationary(helper, start);
        try {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            long failedAt = helper.getLevel().getGameTime();
            WalkTargetFailureMemory.record(villager, target, failedAt);
            Path completed = new Path(List.of(node(start), node(endpoint)), target, false);
            helper.assertTrue(MCAGroundPathNavigation.isUsefulPartialPath(completed, target),
                    "fixture is not a useful partial path");
            villager.getNavigation().moveTo(completed, 0.5D);
            villager.setPos(Vec3.atBottomCenterOf(endpoint));
            while (!completed.isDone()) completed.advance();
            villager.getNavigation().tick();
            Path continuation = villager.getNavigation().getPath();
            helper.assertTrue(continuation != null && continuation != completed && !continuation.isDone(),
                    "prior destination failure prevented real short-partial progress from chaining");
            helper.assertTrue(continuation.getTarget().equals(target)
                            && continuation.getEndNode().asBlockPos().distManhattan(target) < endpoint.distManhattan(target),
                    "continuation did not advance toward the original destination");
            helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, target),
                    "a merely useful partial path erased terminal-failure history prematurely");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_failed_short_partial_progress", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void priorFailureDoesNotTriggerStationaryShortPartialLoop(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos endpoint = start.east();
        BlockPos target = start.east(100);
        net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath(helper, start, target);
        VillagerEntityMCA villager = spawnStationary(helper, start);
        try {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
            Path completed = new Path(List.of(node(start), node(endpoint)), target, false);
            villager.getNavigation().moveTo(completed, 0.5D);
            while (!completed.isDone()) completed.advance();
            long before = PathRequestDiagnostics.snapshot(villager).directRequests();
            villager.getNavigation().tick();
            helper.assertTrue(PathRequestDiagnostics.snapshot(villager).directRequests() == before,
                    "stationary short partial retried without physical progress");
            helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, target),
                    "stationary partial lost its destination failure age");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_failed_short_partial_success", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedShortPartialClearsPriorFailureWhenFullRouteOpens(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockPos endpoint = start.east();
        BlockPos target = start.east(5);
        net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath(helper, start, target);
        VillagerEntityMCA villager = spawnStationary(helper, start);
        try {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
            Path completed = new Path(List.of(node(start), node(endpoint)), target, false);
            villager.getNavigation().moveTo(completed, 0.5D);
            villager.setPos(Vec3.atBottomCenterOf(endpoint));
            while (!completed.isDone()) completed.advance();
            villager.getNavigation().tick();
            Path continuation = villager.getNavigation().getPath();
            helper.assertTrue(continuation != null && continuation != completed
                            && continuation.canReach() && continuation.getTarget().equals(target),
                    "reachable continuation was not chained after the prior short-partial failure");
            helper.assertTrue(!WalkTargetFailureMemory.hasFailureFor(villager, target),
                    "fully reachable continuation retained an obsolete terminal-failure clock");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_short_partial_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedShortPartialUsesNativeDetourWhenContinuationIsBlocked(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(65, 1, 65));
        BlockPos endpoint = start.east();
        BlockPos target = start.east(3);
        prepareFlatArea(helper, start, 61, 3);
        for (int z = -55; z <= 55; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(2, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA villager = spawnStationary(helper, start);
        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            Path completed = new Path(List.of(node(start), node(endpoint)), target, false);
            helper.assertTrue(MCAGroundPathNavigation.isUsefulPartialPath(completed, target),
                    "Fixture must exercise a useful short partial route");
            villager.getNavigation().moveTo(completed, 0.5D);
            villager.setPos(Vec3.atBottomCenterOf(endpoint));
            while (!completed.isDone()) completed.advance();
            villager.getNavigation().tick();
            Path flank = villager.getNavigation().getPath();
            helper.assertTrue(flank != null && !flank.isDone() && flank.canReach()
                            && !flank.getTarget().equals(target)
                            && ((MCAGroundPathNavigation) villager.getNavigation()).isTakingDetourTo(target),
                    "A short completed partial route skipped navigation's existing detour recovery");
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow()
                            .getTarget().currentBlockPosition().equals(target),
                    "Recovery replaced the logical destination with its temporary flank waypoint");
        } finally {
            config.villagerPathfindingDistance = previousDistance;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_short_partial_gate_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedShortPartialDetoursWhenOnlyApproachIsOperableGate(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(65, 1, 65));
        BlockPos endpoint = start.east();
        BlockPos target = start.east(3);
        prepareFlatArea(helper, start, 61, 3);
        for (int z = -55; z <= 55; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(2, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        // The target has no vanilla WALKABLE neighbor. MCA can enter from the
        // south through this closed, hand-operable gate after clearing the wall.
        for (BlockPos blocked : List.of(target.east(), target.north())) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(blocked.above(y), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        helper.getLevel().setBlock(target.south(), Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);
        VillagerEntityMCA villager = spawnStationary(helper, start);
        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        boolean previousGates = config.villagersInteractWithFenceGates;
        try {
            config.villagersInteractWithFenceGates = true;
            config.villagerPathfindingDistance = 160;
            villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            Path completed = new Path(List.of(node(start), node(endpoint)), target, false);
            helper.assertTrue(MCAGroundPathNavigation.isUsefulPartialPath(completed, target),
                    "fixture must have a useful completed partial route");
            villager.getNavigation().moveTo(completed, 0.5D);
            villager.setPos(Vec3.atBottomCenterOf(endpoint));
            while (!completed.isDone()) completed.advance();
            villager.getNavigation().tick();

            Path flank = villager.getNavigation().getPath();
            helper.assertTrue(flank != null && !flank.isDone() && flank.canReach()
                            && !flank.getTarget().equals(target)
                            && ((MCAGroundPathNavigation) villager.getNavigation()).isTakingDetourTo(target),
                    "A target with an operable gate as its only approach was denied detour recovery");
        } finally {
            config.villagerPathfindingDistance = previousDistance;
            config.villagersInteractWithFenceGates = previousGates;
            villager.discard();
        }
        helper.succeed();
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

    @GameTest(batch = "mca_detour_recompute_target", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void invalidatedDetourRecomputesLogicalDestination(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(65, 1, 65));
        BlockPos endpoint = start.east();
        BlockPos destination = start.east(3);
        prepareFlatArea(helper, start, 61, 3);
        for (int z = -55; z <= 55; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(2, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA villager = spawnStationary(helper, start);
        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(destination), 0.5F, 0));
            NavigationProbe navigation = new NavigationProbe(villager);
            Path shortPartial = new Path(List.of(node(start), node(endpoint)), destination, false);
            helper.assertTrue(navigation.moveTo(shortPartial, 0.5D), "partial route could not start");
            villager.setPos(Vec3.atBottomCenterOf(endpoint));
            while (!shortPartial.isDone()) shortPartial.advance();
            navigation.tick();
            Path flank = navigation.getPath();
            helper.assertTrue(flank != null && flank.canReach()
                            && !flank.getTarget().equals(destination)
                            && navigation.isTakingDetourTo(destination),
                    "fixture did not create a physical flank route");

            BlockPos repaired = destination.above();
            helper.assertTrue(navigation.shouldRecomputePath(repaired),
                    "terrain change near the destination should invalidate the detour");
            navigation.recomputeNow();
            Path replacement = navigation.getPath();
            helper.assertTrue(replacement != null && replacement.getTarget().equals(destination),
                    "detour recomputation kept its obsolete flank target: " + replacement);
        } finally {
            config.villagerPathfindingDistance = previousDistance;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_remote_failure_repair", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void remotePassageRepairWakesFailedExtendedSearch(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(65, 1, 65));
        BlockPos farTurn = start.west(55);
        BlockPos destination = start.east(100).south(8);
        // The only route initially heads away from the destination to a distant
        // turn, then returns on a parallel lane. A blocked turn is not a useful partial path.
        net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath(helper, farTurn, start);
        net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath(helper, farTurn.south(8), destination);
        // The test world may offer unrelated ground routes. Clear an enclosed
        // rectangular arena and restore exactly the two lanes and their far turn.
        for (int x = farTurn.getX() - 1; x <= destination.getX() + 1; x++) {
            for (int z = start.getZ() - 1; z <= destination.getZ() + 1; z++) {
                BlockPos feet = new BlockPos(x, start.getY(), z);
                helper.getLevel().setBlock(feet.below(), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.below(2), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.below(3), Blocks.AIR.defaultBlockState(), 3);
                boolean perimeter = x == farTurn.getX() - 1 || x == destination.getX() + 1
                        || z == start.getZ() - 1 || z == destination.getZ() + 1;
                // Prevent a route along the GameTest world's natural bedrock floor
                // beneath our corridor or underneath the enclosing boundary.
                for (int y = helper.getLevel().getMinY(); y < start.getY(); y++) {
                    helper.getLevel().setBlock(new BlockPos(x, y, z),
                            perimeter ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(feet.above(y),
                            perimeter ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int x = farTurn.getX(); x <= start.getX(); x++) {
            helper.getLevel().setBlock(new BlockPos(x, start.getY() - 1, start.getZ()),
                    Blocks.STONE.defaultBlockState(), 3);
        }
        for (int x = farTurn.getX(); x <= destination.getX(); x++) {
            helper.getLevel().setBlock(new BlockPos(x, start.getY() - 1, destination.getZ()),
                    Blocks.STONE.defaultBlockState(), 3);
        }
        for (int z = start.getZ(); z <= destination.getZ(); z++) {
            helper.getLevel().setBlock(new BlockPos(farTurn.getX(), start.getY() - 1, z),
                    Blocks.STONE.defaultBlockState(), 3);
        }
        BlockPos gate = farTurn.south(4);
        for (int y = 0; y < 3; y++) {
            helper.getLevel().setBlock(gate.above(y), Blocks.STONE.defaultBlockState(), 3);
        }
        VillagerEntityMCA villager = spawnStationary(helper, start);
        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 256;
            villager.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(destination), 0.5F, 0));
            WalkTargetFailureMemory.record(villager, destination, helper.getLevel().getGameTime());
            MCAGroundPathNavigation navigation = (MCAGroundPathNavigation) villager.getNavigation();
            Path blocked = navigation.createPath(destination, 0);
            helper.assertTrue(blocked == null || !blocked.canReach()
                            && !MCAGroundPathNavigation.isUsefulPartialPath(blocked, destination),
                    "the long detour fixture unexpectedly offered a reachable/useful partial path: " + blocked
                            + "; end=" + (blocked == null ? null : blocked.getEndNode()));
            long initialSearches = PathRequestDiagnostics.snapshot(villager).extendedSearches();
            helper.assertTrue(initialSearches > 0, "fixture did not attempt an extended search");
            navigation.createPath(destination, 0);
            helper.assertTrue(PathRequestDiagnostics.snapshot(villager).extendedSearches() == initialSearches,
                    "fixture did not enter the failed extended-search backoff");

            // A distant, unrelated change inside the extended origin radius
            // cannot yield a route through it within the search budget.
            navigation.shouldRecomputePath(start.north(170));
            helper.assertTrue(!navigation.consumeRetryInvalidation(destination),
                    "unrelated remote terrain change woke the failed-search producer");

            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(gate.above(y), Blocks.AIR.defaultBlockState(), 3);
            }
            Path freshRoute = new NavigationProbe(villager).createPath(destination, 0);
            helper.assertTrue(freshRoute != null && freshRoute.canReach(),
                    "fixture repair did not actually create a reachable route: " + freshRoute);
            navigation.shouldRecomputePath(gate);
            helper.assertTrue(navigation.consumeRetryInvalidation(destination),
                    "opening the remote turn failed to notify the stalled destination producer");
            Path repaired = navigation.createPath(destination, 0);
            helper.assertTrue(repaired != null && repaired.canReach(),
                    "the repaired corridor still could not reach its destination: " + repaired);
        } finally {
            config.villagerPathfindingDistance = previousDistance;
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

    @GameTest(batch = "mca_navigation_review_reopen", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 50)
    public static void nativeRecomputationUsesInvalidationAndStillWakesProducer(GameTestHelper helper) {
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
        int previousDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            NavigationProbe navigation = new NavigationProbe(villager);
            Path blocked = navigation.createPath(target, 0);
            helper.assertTrue(blocked != null && !blocked.canReach(), "failed-search fixture was reachable");
            helper.assertTrue(navigation.moveTo(blocked, 0.5D), "failed-search fixture was not accepted");
            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
            long before = PathRequestDiagnostics.snapshot(villager).extendedSearches();
            BlockPos gap = target.east();
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(gap.above(y), Blocks.AIR.defaultBlockState(), 3);
            }
            navigation.shouldRecomputePath(gap);
            navigation.recomputeNow();
            Path reopened = navigation.getPath();
            helper.assertTrue(PathRequestDiagnostics.snapshot(villager).extendedSearches() > before
                            && reopened != null && reopened.canReach(),
                    "native recompute kept the stale failed extended-search backoff");
            helper.assertTrue(navigation.consumeRetryInvalidation(target),
                    "native recompute consumed the producer's one-shot wake-up");
            helper.assertTrue(!navigation.consumeRetryInvalidation(target),
                    "producer invalidation was delivered more than once");
        } finally {
            config.villagerPathfindingDistance = previousDistance;
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
