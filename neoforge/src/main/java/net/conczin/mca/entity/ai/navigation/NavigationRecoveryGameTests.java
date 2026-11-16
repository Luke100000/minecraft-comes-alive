package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedWalkTowardsTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
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

    @GameTest(batch = "mca_probe_keeps_route", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void reachabilityProbeKeepsActiveReplanTarget(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 10));
        BlockPos destination = start.east(5);
        BlockPos candidate = start.south(5);
        prepareFlatArea(helper, start, 8, 4);
        VillagerEntityMCA villager = spawnStationary(helper, start);
        try {
            NavigationProbe navigation = new NavigationProbe(villager);
            Path active = navigation.createPath(destination, 0);
            helper.assertTrue(active != null && active.canReach() && navigation.moveTo(active, 0.5D),
                    "The original route must be active");
            for (int i = 0; i < 2; i++) {
                Path probe = navigation.createPathForPersistentIntent(Set.of(candidate), 0);
                helper.assertTrue(probe != null && probe.canReach() && probe.getTarget().equals(candidate),
                        "A reachability check returned the route for a different destination");
            }
            Path airborneProbe = MCAGroundPathNavigation.probePath(navigation, candidate.above(2), 0);
            helper.assertTrue(airborneProbe != null && airborneProbe.canReach() && airborneProbe.getTarget().equals(candidate),
                    "A block-target probe must retain vanilla destination normalization");
            helper.assertTrue(navigation.getPath() == active, "Probing must preserve the active route");
            navigation.recomputeNow();
            Path replanned = navigation.getPath();
            helper.assertTrue(replanned != null && replanned.getTarget().equals(destination),
                    "Recomputation must keep heading to the original destination");

            Path nextJourney = navigation.createPath(candidate, 0);
            helper.assertTrue(nextJourney != null && nextJourney.canReach() && navigation.moveTo(nextJourney, 0.5D),
                    "The ordinary createPath and moveTo sequence must still start a new journey");
            navigation.recomputeNow();
            helper.assertTrue(navigation.getPath() != null && navigation.getPath().getTarget().equals(candidate),
                    "Recomputation must follow the destination accepted through the ordinary API");

            Path acceptedProbe = navigation.createPathForPersistentIntent(Set.of(destination), 0);
            navigation.createPathForPersistentIntent(Set.of(start.west(4)), 0);
            acceptedProbe = acceptedProbe == null ? null : acceptedProbe.copy();
            helper.assertTrue(acceptedProbe != null && navigation.moveTo(acceptedProbe, 0.5D),
                    "A verified probe result must be usable as the next journey");
            navigation.recomputeNow();
            helper.assertTrue(navigation.getPath() != null && navigation.getPath().getTarget().equals(destination),
                    "Accepting a probe must commit its destination for subsequent replans");

            BlockPos nearbyTarget = start.north(5);
            Path nearbyProbe = navigation.createPathForPersistentIntent(Set.of(nearbyTarget), 2);
            helper.assertTrue(nearbyProbe != null && nearbyProbe.getEndNode().distanceManhattan(nearbyTarget) > 0.0F
                            && navigation.moveTo(nearbyProbe.copy(), 0.5D),
                    "The fixture must accept a probe with a nonzero arrival range");
            navigation.recomputeNow();
            Path nearbyReplan = navigation.getPath();
            helper.assertTrue(nearbyReplan != null && nearbyReplan.canReach()
                            && nearbyReplan.getTarget().equals(nearbyTarget)
                            && nearbyReplan.getEndNode().distanceManhattan(nearbyTarget) > 0.0F
                            && nearbyReplan.getEndNode().distanceManhattan(nearbyTarget) <= 2.0F,
                    "Accepting a copied probe must preserve its arrival range on recomputation");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_addon_probe_dispatch", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void addonProbeDoesNotAcceptPendingCreatePath(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 10));
        BlockPos candidate = start.east(4);
        prepareFlatArea(helper, start, 6, 4);
        VillagerEntityMCA villager = spawnStationary(helper, start);
        try {
            MCAGroundPathNavigation navigation = new MCAGroundPathNavigation(villager, helper.getLevel()) {
                @Override
                public Path probePath(Set<BlockPos> targets, int reachRange) {
                    // An async addon has not verified a route yet.
                    return null;
                }

                @Override
                public Path probePath(BlockPos target, int reachRange) {
                    return probePath(Set.of(target), reachRange);
                }

                @Override
                public Path createPath(BlockPos target, int reachRange) {
                    return new Path(List.of(node(start)), target, true);
                }

                @Override
                protected Path createPath(Set<BlockPos> targets, int radiusOffset, boolean above, int reachRange) {
                    return createPath(targets.iterator().next(), reachRange);
                }
            };
            helper.assertTrue(MCAGroundPathNavigation.probePath(navigation, Set.of(candidate), 0) == null,
                    "A set probe must use the addon's verified reachability entry point");
            helper.assertTrue(MCAGroundPathNavigation.probePath(navigation, candidate, 0) == null,
                    "A block probe must use the addon's verified reachability entry point");
        } finally {
            villager.discard();
        }
        helper.succeed();
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
        var bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
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
                for (int y = helper.getLevel().getMinBuildHeight(); y < start.getY(); y++) {
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

    @GameTest(batch = "mca_fall_blocks_rejoin", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 60)
    public static void fallReplansInsteadOfRejoiningThroughWall(GameTestHelper helper) {
        BlockPos landing = helper.absolutePos(new BlockPos(10, 1, 10));
        BlockPos target = landing.east(2);
        prepareFlatArea(helper, landing, 8, 4);
        for (int y = 0; y < 3; y++) {
            helper.getLevel().setBlockAndUpdate(landing.east().above(y), Blocks.STONE.defaultBlockState());
        }
        helper.runAfterDelay(22, () -> {
            VillagerEntityMCA villager = spawnStationary(helper, landing);
            try {
                var navigation = villager.getNavigation();
                Path aroundWall = navigation.createPath(target, 0);
                helper.assertTrue(aroundWall != null && aroundWall.canReach(), "The destination must be reachable around the wall");
                Path stale = new Path(List.of(node(landing.above(3)), node(target)), target, true);
                helper.assertTrue(navigation.moveTo(stale, 0.5D), "The route planned before displacement must start");
                navigation.tick();
                Path recovered = navigation.getPath();
                helper.assertTrue(recovered != null && recovered != stale && recovered.canReach()
                                && recovered.getTarget().equals(target),
                        "Fall recovery must replan around the wall rather than rejoin through it");
            } finally {
                villager.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_fallen_path_start", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 60)
    public static void landingBelowFirstWaypointReplansFromTheGround(GameTestHelper helper) {
        BlockPos landing = helper.absolutePos(new BlockPos(10, 1, 10));
        BlockPos target = landing.east(5);
        prepareFlatArea(helper, landing, 7, 4);
        for (int x = -1; x <= 5; x++) {
            helper.getLevel().setBlockAndUpdate(landing.east(x).north(), Blocks.COBBLESTONE.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(landing.east(x).south(), Blocks.GRASS_BLOCK.defaultBlockState());
        }

        helper.runAfterDelay(22, () -> {
            VillagerEntityMCA villager = spawnStationary(helper, landing);
            try {
                var navigation = villager.getNavigation();
                Path groundedRoute = navigation.createPath(target, 0);
                helper.assertTrue(groundedRoute != null && groundedRoute.canReach(), "The trench must have a route to the target");

                // Match the live route: its first waypoint is over the trench and the
                // next is on the north bank. Movement has already fallen into the trench.
                BlockPos northBank = landing.north().above();
                Path stale = new Path(List.of(node(landing.above()), node(northBank),
                        node(northBank.east()), node(landing.east()), node(target)), target, true);
                villager.setPos(landing.getX() + 0.64D, landing.getY() + 0.9D, landing.getZ() + 0.33D);
                villager.setOnGround(false);
                helper.assertTrue(navigation.moveTo(stale, 0.65D), "The falling villager must receive the old route");
                villager.setPos(Vec3.atBottomCenterOf(landing));
                villager.setOnGround(true);
                navigation.tick();

                Path recovered = navigation.getPath();
                helper.assertTrue(recovered != null && recovered != stale && recovered.canReach()
                                && recovered.getTarget().equals(target),
                        "Landing under the first waypoint must replan to the same destination");
                helper.assertTrue(villager.getMoveControl().getWantedX() > villager.getX()
                                && villager.getMoveControl().getWantedY() == villager.getY(),
                        "The next movement command must lead out of the trench at the landed height");

                helper.getLevel().setBlockAndUpdate(landing, Blocks.SHORT_GRASS.defaultBlockState());
                helper.assertTrue(navigation.moveTo(stale, 0.65D), "A queued stale route must be accepted for this test");
                navigation.tick();
                helper.assertTrue(navigation.isDone(), "Grass must not keep a stale route active during recompute cooldown");

                Path uphill = navigation.createPath(northBank, 0);
                helper.assertTrue(uphill != null && uphill.canReach() && navigation.moveTo(uphill, 0.65D),
                        "The one-block step onto the north bank must remain reachable");
                navigation.tick();
                helper.assertTrue(villager.getMoveControl().getWantedZ() < villager.getZ()
                                && villager.getMoveControl().getWantedY() > villager.getY(),
                        "A route onto the supported bank must still steer uphill");
            } finally {
                villager.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_replaced_downhill_path", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void replacingDownhillPathKeepsSteeringAcrossTheLedge(GameTestHelper helper) {
        BlockPos ledge = helper.absolutePos(new BlockPos(10, 6, 10));
        BlockPos landing = ledge.north().west().below(3);
        prepareFlatArea(helper, ledge.below(3), 6, 7);
        for (int x = -2; x <= 2; x++) {
            helper.getLevel().setBlockAndUpdate(ledge.east(x).below(), Blocks.GRASS_BLOCK.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(ledge.east(x).below(2), Blocks.DIRT.defaultBlockState());
        }
        VillagerEntityMCA villager = spawnStationary(helper, ledge);
        try {
            // Same pose as the live trace: feet still supported by the ledge while
            // the centre has already crossed the edge, heading for the lower node.
            villager.setPos(ledge.getX() + 0.03D, ledge.getY(), ledge.getZ() - 0.12D);
            Vec3 position = villager.position();
            helper.assertTrue(helper.getLevel().noBlockCollision(villager, villager.getBoundingBox())
                            && !helper.getLevel().noBlockCollision(villager,
                            villager.getBoundingBox().move(0.0D, -0.01D, 0.0D)),
                    "The recorded ledge pose must be clear and supported");
            var navigation = villager.getNavigation();
            BlockPos oldTarget = landing.west(2);
            Path active = new Path(List.of(node(ledge.east()), node(ledge), node(landing),
                    node(landing.west()), node(oldTarget)), oldTarget, true);
            active.setNextNodeIndex(2);
            helper.assertTrue(navigation.moveTo(active, 0.65D), "The downhill leg must be active");

            // The new route starts from the previously visited high node and
            // joins the same descent before continuing to the updated target.
            BlockPos newTarget = oldTarget.north();
            Path replacement = new Path(List.of(node(ledge), node(landing), node(landing.west()),
                    node(oldTarget), node(newTarget)), newTarget, true);
            helper.assertTrue(navigation.moveTo(replacement, 0.65D), "The replacement must be admitted");
            navigation.tick();
            var move = villager.getMoveControl();
            helper.assertTrue(move.getWantedZ() < position.z && move.getWantedY() < position.y,
                    "Replacing the descent reversed steering toward the ledge: wanted="
                            + new Vec3(move.getWantedX(), move.getWantedY(), move.getWantedZ()));

            BlockPos uphillTarget = ledge.east(2);
            Path turnBack = new Path(List.of(node(ledge), node(ledge.east()), node(uphillTarget)), uphillTarget, true);
            helper.assertTrue(navigation.moveTo(turnBack, 0.65D), "The route back up must be admitted");
            navigation.tick();
            helper.assertTrue(move.getWantedZ() > position.z && move.getWantedY() == position.y,
                    "A destination back on the ledge must still reverse steering");

            // Both paths were built before the block appeared below the rim.
            // Their shared step is clear at the current height but blocked on descent.
            navigation.stop();
            helper.getLevel().setBlockAndUpdate(landing.above(), Blocks.COBBLESTONE.defaultBlockState());
            helper.assertTrue(navigation.moveTo(active, 0.65D), "The old route must still have the descent queued");
            replacement.setNextNodeIndex(0);
            helper.assertTrue(navigation.moveTo(replacement, 0.65D), "The precomputed route must be admitted");
            navigation.tick();
            helper.assertTrue(move.getWantedZ() > position.z && move.getWantedY() == position.y,
                    "An obstruction below the ledge must prevent skipping the first waypoint");
        } finally {
            villager.discard();
        }
        helper.succeed();
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
