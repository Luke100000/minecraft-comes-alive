package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MCAMoveControl;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.brain.tasks.LocalInsideBrownianWalk;
import net.conczin.mca.registry.BlocksMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.InsideBrownianWalk;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;
import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class MCAGroundPathNavigationGameTests {
    private static final Set<ChunkPos> PROGRESSIVE_FORCED_CHUNKS = new HashSet<>();

    private MCAGroundPathNavigationGameTests() {
    }

    @GameTest(batch = "mca_vine_column_descent", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void descendingVinePathLeavesCurrentColumn(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(5, 5, 5));
        BlockPos target = start.west().below(3).south();
        prepareFlatArea(helper, start.below(3), 3, 6);
        BlockState leaves = Blocks.JUNGLE_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.PERSISTENT, true);
        BlockState vine = Blocks.VINE.defaultBlockState().setValue(BlockStateProperties.EAST, true);
        for (int drop = 1; drop <= 3; drop++) {
            helper.getLevel().setBlock(start.below(drop), leaves, 3);
            helper.getLevel().setBlock(start.west().below(drop), vine, 3);
        }
        helper.getLevel().setBlock(start.east(), leaves, 3);
        helper.getLevel().setBlock(start, vine, 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);
        helper.assertTrue(villager.onClimbable(), "fixture villager is not in its original vine column");
        Path path = new Path(List.of(
                new Node(start.getX(), start.getY(), start.getZ()),
                new Node(start.getX() - 1, start.getY() - 2, start.getZ()),
                new Node(start.getX() - 1, start.getY() - 3, start.getZ()),
                new Node(target.getX(), target.getY(), target.getZ())
        ), target, true);
        path.setNextNodeIndex(1);
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.5D), "vine descent route did not start");
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.blockPosition().equals(target),
                    "villager stayed above leaves instead of entering adjacent descending vines: "
                            + villager.position() + "; " + summarizePath(villager.getNavigation().getPath()));
            villager.discard();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void sameHeightScaffoldingUsesGroundNavigationWithoutCentering(GameTestHelper helper) {
        BlockPos current = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos next = current.east();
        helper.getLevel().setBlock(current.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(next.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(current, Blocks.SCAFFOLDING.defaultBlockState(), 3);
        helper.getLevel().setBlock(next, Blocks.SCAFFOLDING.defaultBlockState(), 3);

        Vec3 offCenter = Vec3.atBottomCenterOf(current).add(-0.25D, 0.0D, -0.25D);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(offCenter).spawn(EntitySpawnReason.STRUCTURE);
        try {
            villager.setNoAi(true);
            helper.assertTrue(villager.onClimbable(), "fixture villager is not inside scaffolding");

            Path path = new Path(List.of(
                    new Node(current.getX(), current.getY(), current.getZ()),
                    new Node(next.getX(), next.getY(), next.getZ())
            ), next, true);
            path.setNextNodeIndex(1);

            ClimbTraversal traversal = new ClimbTraversal(villager, helper.getLevel());
            villager.setDeltaMovement(Vec3.ZERO);
            traversal.tick(path, 0.5D, 1);
            helper.assertTrue(villager.getDeltaMovement().x == 0.0D
                            && villager.getDeltaMovement().z == 0.0D,
                    "flat scaffolding climb handling pulled the villager toward block center: "
                            + villager.getDeltaMovement());
            helper.assertTrue(!traversal.followPath(path),
                    "same-height scaffolding should hand horizontal following back to navigation");
            helper.assertTrue(path.getNextNodeIndex() == 1,
                    "same-height scaffolding waypoint advanced before horizontal arrival");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void flatScaffoldingKeepsGroundJumpMoveControlState(GameTestHelper helper) {
        BlockPos current = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.getLevel().setBlock(current.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(current, Blocks.SCAFFOLDING.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(current)).spawn(EntitySpawnReason.STRUCTURE);
        try {
            villager.setNoAi(true);
            helper.assertTrue(villager.onClimbable(), "fixture villager is not inside scaffolding");

            ProbeMoveControl moveControl = new ProbeMoveControl(villager);
            moveControl.markJumping();
            moveControl.setWantedPosition(current.getX() + 1.5D, current.getY(), current.getZ() + 0.5D, 0.5D);
            helper.assertTrue(moveControl.isJumping(),
                    "flat scaffolding replaced a legitimate ground jump with MOVE_TO");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void flatScaffoldingPreservesRaisedTargetJump(GameTestHelper helper) {
        BlockPos current = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.getLevel().setBlock(current.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(current, Blocks.SCAFFOLDING.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(current)).spawn(EntitySpawnReason.STRUCTURE);
        try {
            villager.setNoAi(true);
            helper.assertTrue(villager.onClimbable(), "fixture villager is not inside scaffolding");

            ProbeMoveControl moveControl = new ProbeMoveControl(villager);
            moveControl.setWantedPosition(villager.getX(), villager.getY() + 1.0D, villager.getZ(), 0.5D);
            moveControl.tick();
            helper.assertTrue(moveControl.isJumping(),
                    "flat scaffolding suppressed vanilla's raised-target jump");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void flatScaffoldingDoesNotSuppressGroundJump(GameTestHelper helper) {
        BlockPos current = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.getLevel().setBlock(current.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(current, Blocks.SCAFFOLDING.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(current)).spawn(EntitySpawnReason.STRUCTURE);
        try {
            villager.setNoAi(true);
            villager.setOnGround(true);
            villager.setDeltaMovement(Vec3.ZERO);
            helper.assertTrue(villager.onClimbable(), "fixture villager is not inside scaffolding");

            villager.setJumping(true);
            villager.aiStep();
            helper.assertTrue(villager.getDeltaMovement().y > 0.0D,
                    "flat scaffolding suppressed an otherwise valid ground jump: "
                            + villager.getDeltaMovement());
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void horizontalPathWalksThroughScaffolding(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(6);
        prepareFlatPath(helper, start, target);
        for (int offset = 2; offset <= 4; offset++) {
            helper.getLevel().setBlock(start.east(offset), Blocks.SCAFFOLDING.defaultBlockState(), 3);
            helper.getLevel().setBlock(start.east(offset).above(), Blocks.SCAFFOLDING.defaultBlockState(), 3);
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);

        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(),
                "scaffolding blocked a reachable horizontal path");
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.5D),
                "scaffolding path could not start");

        helper.succeedWhen(() -> {
            helper.assertTrue(villager.blockPosition().equals(target),
                    "villager did not walk through scaffolding; pos=" + villager.blockPosition()
                            + "; path=" + summarizePath(villager.getNavigation().getPath()));
            villager.discard();
        });
    }

    @GameTest(batch = "mca_scaffolding_vertical", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void verticalPathClimbsScaffolding(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos target = start.above(2);
        prepareFlatArea(helper, start, 1, 4);
        helper.runAfterDelay(1, () -> placeStableScaffoldingColumn(helper, start));
        helper.runAfterDelay(2, () -> {
            BlockState support = helper.getLevel().getBlockState(start.below());
            helper.assertTrue(support.is(Blocks.STONE),
                    "vertical scaffolding support changed before navigation: " + support);
            helper.assertTrue(support.isFaceSturdy(helper.getLevel(), start.below(), Direction.UP),
                    "vertical scaffolding support has no sturdy top face: " + support);
            helper.assertTrue(helper.getLevel().getBlockState(start).is(Blocks.SCAFFOLDING),
                    "vertical scaffolding disappeared before navigation; distance="
                            + ScaffoldingBlock.getDistance(helper.getLevel(), start));
            helper.assertTrue(ScaffoldingBlock.getDistance(helper.getLevel(), start) == 0,
                    "supported vertical scaffolding resolved nonzero distance: "
                            + ScaffoldingBlock.getDistance(helper.getLevel(), start));

            VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
            villager.refreshBrain(helper.getLevel());
            villager.getBrain().removeAllBehaviors();

            Path path = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(path != null && path.canReach(),
                    "vertical scaffolding ascent did not produce a reachable path");
            helper.assertTrue(path.getNodeCount() >= 3,
                    "vertical scaffolding ascent path omitted climb nodes: " + summarizePath(path));

            ClimbTraversal probe = new ClimbTraversal(villager, helper.getLevel());
            villager.setDeltaMovement(Vec3.ZERO);
            helper.assertTrue(probe.followPath(path),
                    "vertical scaffolding path was not handled by climb traversal: " + summarizePath(path));
            helper.assertTrue(path.getNextNodeIndex() == 1,
                    "vertical scaffolding path did not advance past the already-reached start node: "
                            + summarizePath(path));
            probe.tick(path, 0.5D, 1);
            helper.assertTrue(probe.ownsMovement(path, 1),
                    "vertical scaffolding path was not climb-owned: " + summarizePath(path));
            helper.assertTrue(villager.getDeltaMovement().y > 0.0D,
                    "vertical scaffolding climb ownership produced no upward motion: "
                            + villager.getDeltaMovement() + "; " + summarizePath(path));
            villager.setDeltaMovement(Vec3.ZERO);

            helper.assertTrue(villager.getNavigation().moveTo(path, 0.5D),
                    "vertical scaffolding ascent could not start");

            helper.succeedWhen(() -> {
                helper.assertTrue(villager.blockPosition().getY() >= target.getY(),
                        "villager did not climb scaffolding; pos=" + villager.blockPosition()
                                + "; path=" + summarizePath(villager.getNavigation().getPath()));
                villager.discard();
            });
        });
    }

    @GameTest(batch = "mca_scaffolding_vertical", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void verticalPathDescendsScaffolding(GameTestHelper helper) {
        BlockPos target = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos start = target.above(2);
        prepareFlatArea(helper, target, 1, 4);
        helper.runAfterDelay(1, () -> placeStableScaffoldingColumn(helper, target));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(helper.getLevel().getBlockState(target).is(Blocks.SCAFFOLDING),
                    "vertical descent scaffolding disappeared before navigation");

            VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
            villager.refreshBrain(helper.getLevel());
            villager.getBrain().removeAllBehaviors();

            Path path = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(path != null && path.canReach(),
                    "vertical scaffolding descent did not produce a reachable path");
            helper.assertTrue(villager.getNavigation().moveTo(path, 0.5D),
                    "vertical scaffolding descent could not start");

            helper.succeedWhen(() -> {
                helper.assertTrue(villager.blockPosition().getY() <= target.getY(),
                        "villager did not descend scaffolding; pos=" + villager.blockPosition()
                                + "; path=" + summarizePath(villager.getNavigation().getPath()));
                villager.discard();
            });
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void advancingWaypointResetsOnlyNodeTimeout(GameTestHelper helper) {
        checkNodeTimeout(helper, true, false);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void unchangedWaypointStillTimesOut(GameTestHelper helper) {
        checkNodeTimeout(helper, false, false);
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 20)
    public static void waypointChangeDoesNotDisablePhysicalStuckCheck(GameTestHelper helper) {
        checkNodeTimeout(helper, true, true);
    }

    private static void checkNodeTimeout(GameTestHelper helper, boolean changedWaypoint, boolean physicallyStuck) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        try {
            villager.setNoAi(true);
            villager.setSpeed(0.1F);
            Node first = new Node(start.getX(), start.getY(), start.getZ());
            Node next = new Node(start.getX() + 1, start.getY(), start.getZ());
            Path path = new Path(List.of(first, next), start.east(), true);
            path.setNextNodeIndex(1);
            NodeTimeoutProbe navigation = new NodeTimeoutProbe(villager);
            navigation.check(path, changedWaypoint ? first : next, physicallyStuck);
            if (changedWaypoint && !physicallyStuck) {
                helper.assertTrue(navigation.getPath() == path && navigation.nodeTimer() == 0L,
                        "new waypoint inherited elapsed time from previous waypoints: " + navigation.nodeTimer());
            } else {
                helper.assertTrue(navigation.isDone(), "stationary path escaped navigation timeout");
                helper.assertTrue(!physicallyStuck || navigation.isStuck(), "physical stuck detection was bypassed");
            }
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    /** Seed the observed timeout state without waiting hundreds of ticks in a focused contract test. */
    private static final class NodeTimeoutProbe extends MCAGroundPathNavigation {
        private NodeTimeoutProbe(VillagerEntityMCA mob) {
            super(mob, mob.level());
        }

        private void check(Path path, Node previousWaypoint, boolean physicallyStuck) {
            this.path = path;
            this.timeoutCachedNode = previousWaypoint.asBlockPos();
            this.timeoutTimer = 601L;
            this.timeoutLimit = 200.0D;
            this.lastTimeoutCheck = this.level.getGameTime() - 1L;
            this.tick = physicallyStuck ? 101 : 1;
            this.lastStuckCheckPos = this.mob.position();
            this.doStuckDetection(this.mob.position());
        }

        private long nodeTimer() {
            return this.timeoutTimer;
        }
    }

    @GameTest(batch = "mca_navigation_stalled_partial", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void completedPartialWithoutPhysicalProgressReturnsToBrainFailure(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos destination = start.east(100);
        prepareFlatPath(helper, start, destination);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        try {
            villager.refreshBrain(helper.getLevel());
            villager.getBrain().removeAllBehaviors();
            villager.setNoAi(true);
            villager.setOnGround(true);
            villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(destination), 0.5F, 0));
            Path path = villager.getNavigation().createPath(destination, 0);
            helper.assertTrue(path != null && MCAGroundPathNavigation.isUsefulPartialPath(path, destination),
                    "fixture did not create a useful bounded partial path");
            helper.assertTrue(villager.getNavigation().moveTo(path, 0.0D),
                    "fixture could not activate the partial path");
            WalkTargetFailureMemory.clear(villager);
            PathRequestDiagnostics.SearchSnapshot before = PathRequestDiagnostics.snapshot(villager);
            while (!path.isDone()) {
                path.advance();
            }

            // Simulate a consumed/inactivated partial route without real movement.
            // An optimistic endpoint alone must not authorize another search.
            villager.getNavigation().tick();
            PathRequestDiagnostics.SearchSnapshot after = PathRequestDiagnostics.snapshot(villager);
            helper.assertTrue(after.ordinarySearches() == before.ordinarySearches()
                            && after.extendedSearches() == before.extendedSearches(),
                    "stationary completed partial triggered another synchronous search");
            helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, destination),
                    "stationary failed continuation did not return control to Brain failure memory");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_failed_partial_continuation", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void failedPartialContinuationDoesNotSearchEveryTick(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos destination = start.east(100);
        prepareFlatPath(helper, start, destination);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        try {
            villager.refreshBrain(helper.getLevel());
            villager.getBrain().removeAllBehaviors();
            villager.setNoAi(true);
            villager.setOnGround(true);
            villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(destination), 0.5F, 0));
            Path completed = villager.getNavigation().createPath(destination, 0);
            helper.assertTrue(completed != null && MCAGroundPathNavigation.isUsefulPartialPath(completed, destination),
                    "fixture could not create an initial useful partial path");
            helper.assertTrue(villager.getNavigation().moveTo(completed, 0.0D),
                    "fixture could not activate the first partial path");

            // Consume the first segment and put the villager beyond the movement
            // threshold. Closing this already-completed path prevents block updates
            // from triggering vanilla's unrelated active-path recomputation.
            while (!completed.isDone()) {
                completed.advance();
            }
            BlockPos stranded = start.east(5);
            villager.setPos(Vec3.atBottomCenterOf(stranded));
            villager.setOnGround(true);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) {
                        continue;
                    }
                    for (int dy = 0; dy < 3; dy++) {
                        helper.getLevel().setBlock(stranded.offset(dx, dy, dz),
                                Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }

            PathRequestDiagnostics.SearchSnapshot before = PathRequestDiagnostics.snapshot(villager);
            villager.getNavigation().tick();
            PathRequestDiagnostics.SearchSnapshot afterFirst = PathRequestDiagnostics.snapshot(villager);
            helper.assertTrue(afterFirst.ordinarySearches() > before.ordinarySearches(),
                    "fixture did not attempt its first blocked continuation");
            helper.assertTrue(WalkTargetFailureMemory.hasFailureFor(villager, destination),
                    "unusable continuation failed to hand retry ownership back to Brain");

            villager.getNavigation().tick();
            PathRequestDiagnostics.SearchSnapshot afterSecond = PathRequestDiagnostics.snapshot(villager);
            helper.assertTrue(afterSecond.ordinarySearches() == afterFirst.ordinarySearches(),
                    "blocked completed partial searched again on the very next navigation tick");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_block_recompute_trace", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 60)
    public static void blockShapeUpdateIdentifiesNavigationRecomputeTrigger(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(8);
        prepareFlatPath(helper, start, target);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 0));
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach()
                        && villager.getNavigation().moveTo(path, 0.0D),
                "fixture could not activate a path through the changed block");

        helper.runAfterDelay(1, () -> {
            try {
                BlockPos changed = start.east(4);
                helper.getLevel().setBlock(changed, Blocks.STONE.defaultBlockState(), 3);
                PathRequestDiagnostics.RecomputeSnapshot snapshot =
                        PathRequestDiagnostics.recomputeSnapshot(villager);
                helper.assertTrue(snapshot.blockUpdates() > 0
                                && changed.equals(snapshot.lastChangedBlock()),
                        "world-driven navigation recomputation did not record its changed block: " + snapshot);
                helper.assertTrue(snapshot.lastActiveTarget() != null
                                && snapshot.lastActiveTarget().equals(target),
                        "recompute instrumentation lost the pre-invalidation path target: " + snapshot);
            } finally {
                villager.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_navigation_off_route_block_update", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void offRouteBlockUpdatePreservesReachablePath(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(24);
        prepareFlatArea(helper, start.east(12), 16, 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach() && path.getNodeCount() > 16
                        && villager.getNavigation().moveTo(path, 0.0D),
                "fixture did not activate a reachable route long enough for vanilla's broad invalidation");

        helper.runAfterDelay(21, () -> {
            try {
                BlockPos unrelated = start.east(12).south(8);
                PathRequestDiagnostics.RecomputeSnapshot before =
                        PathRequestDiagnostics.recomputeSnapshot(villager);
                helper.getLevel().setBlock(unrelated, Blocks.STONE.defaultBlockState(), 3);
                PathRequestDiagnostics.RecomputeSnapshot after =
                        PathRequestDiagnostics.recomputeSnapshot(villager);
                helper.assertTrue(after.blockUpdates() == before.blockUpdates(),
                        "off-route obstruction invalidated a reachable route: " + after);
                helper.assertTrue(villager.getNavigation().getPath() == path,
                        "off-route obstruction replaced a still-valid reachable path");
            } finally {
                villager.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_navigation_near_route_block_update", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void adjacentBlockUpdateStillInvalidatesReachablePath(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(24);
        prepareFlatArea(helper, start.east(12), 16, 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach() && path.getNodeCount() > 16
                        && villager.getNavigation().moveTo(path, 0.0D),
                "fixture did not activate a reachable path for nearby block invalidation");

        helper.runAfterDelay(21, () -> {
            try {
                BlockPos adjacent = start.east(12).south();
                PathRequestDiagnostics.RecomputeSnapshot before =
                        PathRequestDiagnostics.recomputeSnapshot(villager);
                helper.getLevel().setBlock(adjacent, Blocks.STONE.defaultBlockState(), 3);
                PathRequestDiagnostics.RecomputeSnapshot after =
                        PathRequestDiagnostics.recomputeSnapshot(villager);
                helper.assertTrue(after.blockUpdates() > before.blockUpdates()
                                && adjacent.equals(after.lastChangedBlock()),
                        "near-route obstruction failed to invalidate the active route: " + after);
            } finally {
                villager.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_navigation_partial_block_update", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void offRouteBlockUpdateStillInvalidatesPartialPath(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos destination = start.east(100);
        prepareFlatPath(helper, start, destination);
        BlockPos offRoute = start.east(4).south(4);
        helper.getLevel().setBlock(offRoute, Blocks.AIR.defaultBlockState(), 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(destination), 0.5F, 0));
        Path partial = villager.getNavigation().createPath(destination, 0);
        helper.assertTrue(partial != null && !partial.canReach() && !partial.isDone()
                        && villager.getNavigation().moveTo(partial, 0.0D),
                "fixture did not activate a bounded partial path");

        helper.runAfterDelay(21, () -> {
            try {
                helper.assertTrue(villager.getNavigation().shouldRecomputePath(offRoute),
                        "partial route lost vanilla's broader block-update invalidation");
                PathRequestDiagnostics.RecomputeSnapshot before =
                        PathRequestDiagnostics.recomputeSnapshot(villager);
                helper.getLevel().setBlock(offRoute, Blocks.STONE.defaultBlockState(), 3);
                PathRequestDiagnostics.RecomputeSnapshot after =
                        PathRequestDiagnostics.recomputeSnapshot(villager);
                helper.assertTrue(after.blockUpdates() > before.blockUpdates(),
                        "off-route change failed to invalidate a partial path: " + after);
            } finally {
                villager.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_navigation_passable_gate_update", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void passableGateToggleKeepsReachablePath(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(24);
        prepareFlatPath(helper, start, target);
        BlockPos gate = start.east(12);
        BlockState closed = Blocks.OAK_FENCE_GATE.defaultBlockState();
        helper.assertTrue(PathingBlockInteraction.canInteractWithFenceGate(closed),
                "fixture requires MCA fence-gate interaction to be enabled");
        helper.getLevel().setBlock(gate, closed, 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach() && path.getNodeCount() > 16
                        && villager.getNavigation().moveTo(path, 0.0D),
                "fixture did not activate an MCA path through the closed gate");

        helper.runAfterDelay(21, () -> {
            try {
                PathRequestDiagnostics.RecomputeSnapshot before =
                        PathRequestDiagnostics.recomputeSnapshot(villager);
                helper.getLevel().setBlock(gate,
                        closed.setValue(BlockStateProperties.OPEN, true), 3);
                helper.getLevel().setBlock(gate, closed, 3);
                PathRequestDiagnostics.RecomputeSnapshot after =
                        PathRequestDiagnostics.recomputeSnapshot(villager);
                helper.assertTrue(after.blockUpdates() == before.blockUpdates()
                                && villager.getNavigation().getPath() == path,
                        "operating an MCA-passable gate caused a redundant reachable-path search: " + after);
            } finally {
                villager.discard();
            }
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_navigation_partial_surface", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void oneSnowLayerMatchesVanilla(GameTestHelper helper) {
        assertPartialSurfaceReachability(helper,
                Blocks.SNOW_BLOCK.defaultBlockState(),
                Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1),
                true,
                "one snow layer");
    }

    @GameTest(batch = "mca_navigation_partial_surface", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void twoSnowLayersMatchVanilla(GameTestHelper helper) {
        assertPartialSurfaceReachability(helper,
                Blocks.SNOW_BLOCK.defaultBlockState(),
                Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 2),
                true,
                "two snow layers");
    }

    @GameTest(batch = "mca_navigation_partial_surface", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void threeSnowLayersMatchVanilla(GameTestHelper helper) {
        assertPartialSurfaceReachability(helper,
                Blocks.SNOW_BLOCK.defaultBlockState(),
                Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 3),
                true,
                "three snow layers");
    }

    @GameTest(batch = "mca_navigation_partial_surface", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void fourSnowLayersMatchVanilla(GameTestHelper helper) {
        assertPartialSurfaceReachability(helper,
                Blocks.SNOW_BLOCK.defaultBlockState(),
                Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 4),
                true,
                "four snow layers");
    }

    @GameTest(batch = "mca_navigation_partial_surface", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void fiveSnowLayersRemainBlockedLikeVanilla(GameTestHelper helper) {
        assertPartialSurfaceReachability(helper,
                Blocks.SNOW_BLOCK.defaultBlockState(),
                Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 5),
                false,
                "five snow layers");
    }

    @GameTest(batch = "mca_navigation_partial_surface", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void bottomSlabMatchesVanilla(GameTestHelper helper) {
        assertPartialSurfaceReachability(helper,
                Blocks.STONE.defaultBlockState(),
                Blocks.STONE_SLAB.defaultBlockState(),
                true,
                "bottom slab");
    }

    @GameTest(batch = "mca_navigation_partial_surface", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void carpetMatchesVanilla(GameTestHelper helper) {
        assertPartialSurfaceReachability(helper,
                Blocks.STONE.defaultBlockState(),
                Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState(),
                true,
                "carpet");
    }

    @GameTest(batch = "mca_navigation_partial_surface", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void pressurePlateMatchesVanilla(GameTestHelper helper) {
        assertPartialSurfaceTraversal(helper,
                Blocks.STONE.defaultBlockState(),
                Blocks.STONE_PRESSURE_PLATE.defaultBlockState(),
                "pressure plate");
    }

    private static void assertPartialSurfaceTraversal(
            GameTestHelper helper,
            BlockState supportState,
            BlockState surfaceState,
            String description
    ) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos target = start.east(6);
        for (int x = -1; x <= 7; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos feet = start.offset(x, 0, z);
                helper.getLevel().setBlock(feet.below(), supportState, 3);
                helper.getLevel().setBlock(feet, x < 6 ? surfaceState : Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.above(2), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        assertReachabilityParity(helper, Vec3.atBottomCenterOf(start), target, true, description);
    }

    private static void assertPartialSurfaceReachability(
            GameTestHelper helper,
            BlockState supportState,
            BlockState surfaceState,
            boolean expectedReachable,
            String description
    ) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos target = start.east(6);
        for (int x = -1; x <= 7; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos feet = start.offset(x, 0, z);
                helper.getLevel().setBlock(feet.below(), supportState, 3);
                helper.getLevel().setBlock(feet, surfaceState, 3);
                helper.getLevel().setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.above(2), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        VoxelShape surfaceShape = surfaceState.getCollisionShape(helper.getLevel(), start);
        double surfaceHeight = surfaceShape.isEmpty() ? 0.0D : surfaceShape.max(Direction.Axis.Y);
        Vec3 spawnPos = Vec3.atBottomCenterOf(start).add(0.0D, surfaceHeight, 0.0D);

        assertReachabilityParity(helper, spawnPos, target, expectedReachable, description);
    }

    private static void assertReachabilityParity(
            GameTestHelper helper,
            Vec3 spawnPos,
            BlockPos target,
            boolean expectedReachable,
            String description
    ) {
        Villager vanilla = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
        if (vanilla == null) {
            throw new IllegalStateException("failed to create vanilla villager");
        }
        vanilla.snapTo(spawnPos.x, spawnPos.y, spawnPos.z);
        vanilla.setNoAi(true);
        vanilla.setOnGround(true);
        helper.getLevel().addFreshEntity(vanilla);
        Path vanillaPath = vanilla.getNavigation().createPath(target, 0);
        boolean vanillaReachable = vanillaPath != null && vanillaPath.canReach();
        vanilla.discard();

        VillagerEntityMCA mca = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(spawnPos)
                .withName("Partial Surface Parity Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        mca.setNoAi(true);
        mca.setOnGround(true);
        Path mcaPath = mca.getNavigation().createPath(target, 0);
        boolean mcaReachable = mcaPath != null && mcaPath.canReach();
        mca.discard();

        helper.assertTrue(vanillaReachable == expectedReachable,
                "vanilla fixture expectation changed for " + description
                        + "; expectedReachable=" + expectedReachable
                        + "; actualReachable=" + vanillaReachable
                        + "; path=" + vanillaPath);
        helper.assertTrue(mcaReachable == expectedReachable,
                "MCA pathfinding diverged for " + description
                        + "; expectedReachable=" + expectedReachable
                        + "; actualReachable=" + mcaReachable
                        + "; path=" + mcaPath);
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_partial_tiebreak", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 20)
    public static void equalDistancePartialPrefersFewerNodes(GameTestHelper helper) {
        BlockPos target = new BlockPos(10, 0, 0);
        Path longer = new Path(List.of(
                new Node(0, 0, 0),
                new Node(2, 0, 0),
                new Node(5, 0, 0)
        ), target, false);
        Path shorter = new Path(List.of(
                new Node(0, 0, 0),
                new Node(5, 0, 0)
        ), target, false);

        helper.assertTrue(longer.getDistToTarget() == shorter.getDistToTarget(),
                "fixture partial paths must end equally close to the target");
        helper.assertTrue(MCAGroundPathNavigation.isBetterPartialPath(shorter, longer),
                "equal-distance partial path did not prefer vanilla's shorter node count");
        helper.assertTrue(!MCAGroundPathNavigation.isBetterPartialPath(longer, shorter),
                "equal-distance partial path incorrectly preferred a longer node count");
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_recompute", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void airborneRecomputePreservesActivePath(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos target = start.east(8);
        prepareFlatPath(helper, start, target);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Recompute Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);

        var navigation = villager.getNavigation();
        helper.assertTrue(navigation instanceof MCAGroundPathNavigation,
                "fixture villager did not use MCA ground navigation");
        Path path = navigation.createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(),
                "fixture could not create a reachable active path");
        helper.assertTrue(navigation.moveTo(path, 0.0D),
                "fixture could not install the active path");

        helper.runAfterDelay(21, () -> {
            Path originalPath = navigation.getPath();
            helper.assertTrue(originalPath != null,
                    "fixture lost its active path before recomputation");

            villager.setPos(villager.getX(), villager.getY() + 2.0D, villager.getZ());
            villager.setOnGround(false);
            navigation.recomputePath();

            helper.assertTrue(navigation.getPath() == originalPath,
                    "airborne recomputation discarded the active path");

            villager.setPos(Vec3.atBottomCenterOf(start));
            villager.setOnGround(true);
            navigation.recomputePath();
            helper.assertTrue(navigation.getPath() != null,
                    "delayed recomputation did not resume after landing");

            villager.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_navigation_required_path_length", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void ordinaryNavigationRemains48WhenFollowRangeIsLowered(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(30);
        prepareFlatPath(helper, start, target);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Required Path Length Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        double followRangeBefore = villager.getAttributeValue(Attributes.FOLLOW_RANGE);

        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(),
                "ordinary navigation fell below the 48-block navigation floor when FOLLOW_RANGE was lowered");
        helper.assertTrue(villager.getAttributeValue(Attributes.FOLLOW_RANGE) == followRangeBefore,
                "ordinary path creation mutated FOLLOW_RANGE");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_tombstone_collision", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void tombstoneCollisionIsRejectedByNodeEvaluator(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(6);
        prepareFlatPath(helper, start, target);

        for (int x = 0; x <= 6; x++) {
            BlockPos lane = start.east(x);
            for (int y = 0; y < 2; y++) {
                helper.getLevel().setBlock(lane.north().above(y), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(lane.south().above(y), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        BlockPos grave = start.east(3);
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Tombstone Collision Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);

        Path path = villager.getNavigation().createPath(target, 0);

        helper.assertTrue(path != null && path.canReach(),
                "fixture did not retain a reachable detour around the narrow corridor");
        for (int index = 0; index < path.getNodeCount(); index++) {
            helper.assertTrue(!grave.equals(path.getNode(index).asBlockPos()),
                    "pathfinder treated the cross headstone as a traversable node at " + grave);
        }

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_exact_air_target", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void exactAirWalkTargetDoesNotCollapseToSurfaceBelow(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(12, 1, 12));
        BlockPos target = start.east(6).above(8);
        prepareFlatArea(helper, start, 10, 2);
        for (int y = start.getY(); y <= target.getY(); y++) {
            helper.getLevel().setBlock(new BlockPos(target.getX(), y, target.getZ()),
                    Blocks.AIR.defaultBlockState(), 3);
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Exact Air Target Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 0));

        Path path = villager.getNavigation().createPath(target, 0);

        helper.assertTrue(path != null,
                "exact unsupported air target did not produce even a partial path result");
        helper.assertTrue(target.equals(path.getTarget()),
                "exact air WALK_TARGET was vertically collapsed to " + path.getTarget()
                        + " instead of retaining " + target);
        helper.assertTrue(!path.canReach(),
                "unsupported exact air target was falsely reported reachable via the surface below");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_long_distance_horizon", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void farStaticWalkTargetEscalatesOnlyAfterFailureEvidence(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(80);
        prepareFlatPath(helper, start, target);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Long Path Horizon Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);
        double followRangeBefore = villager.getAttributeValue(Attributes.FOLLOW_RANGE);

        Path ordinary = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(ordinary != null && !ordinary.canReach() && ordinary.getEndNode() != null,
                "fixture ordinary path did not stop at its normal bounded horizon");

        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
        Path progressive = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(progressive != null && progressive.getEndNode() != null,
                "long-distance WALK_TARGET did not receive a progressive path result");
        helper.assertTrue(target.equals(progressive.getTarget()),
                "progressive path did not retain the real destination");
        helper.assertTrue(progressive.getEndNode().asBlockPos().equals(ordinary.getEndNode().asBlockPos()),
                "first far-static segment expanded beyond the ordinary geometric horizon; ordinaryEnd="
                        + ordinary.getEndNode().asBlockPos()
                        + ", progressiveEnd=" + progressive.getEndNode().asBlockPos()
                        + ", ordinaryDist=" + ordinary.getDistToTarget()
                        + ", progressiveDist=" + progressive.getDistToTarget()
                        + ", ordinaryNodes=" + ordinary.getNodeCount()
                        + ", progressiveNodes=" + progressive.getNodeCount());

        WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
        Path escalated = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(escalated != null && escalated.getEndNode() != null,
                "canonical retry did not receive an extended path result");
        helper.assertTrue(target.equals(escalated.getTarget()),
                "extended retry did not retain the real destination");
        helper.assertTrue(start.distSqr(escalated.getEndNode().asBlockPos())
                        > start.distSqr(progressive.getEndNode().asBlockPos()),
                "canonical retry did not extend the geometric path horizon");
        helper.assertTrue(escalated.getEndNode().asBlockPos().distSqr(target) < start.distSqr(target),
                "extended retry did not make useful progress toward the real destination");
        helper.assertTrue(villager.getAttributeValue(Attributes.FOLLOW_RANGE) == followRangeBefore,
                "long-distance path request mutated FOLLOW_RANGE");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_surface_adjusted_retry", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void surfaceAdjustedFarTargetKeepsFailureEvidenceForExtendedRetry(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos surfaceTarget = start.east(80);
        BlockPos logicalTarget = surfaceTarget.above(3);
        prepareFlatPath(helper, start, surfaceTarget);
        helper.getLevel().setBlock(surfaceTarget.above(2), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(logicalTarget, Blocks.AIR.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Surface Adjusted Retry Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(logicalTarget), 0.5F, 1));

        Path ordinary = villager.getNavigation().createPath(logicalTarget, 1);
        helper.assertTrue(ordinary != null && ordinary.getEndNode() != null && !ordinary.canReach(),
                "surface-adjusted fixture did not produce the expected bounded partial path");
        helper.assertTrue(ordinary.getTarget().getX() == logicalTarget.getX()
                        && ordinary.getTarget().getZ() == logicalTarget.getZ()
                        && ordinary.getTarget().getY() != logicalTarget.getY(),
                "fixture target was not surface-adjusted away from the logical Y; pathTarget="
                        + ordinary.getTarget() + ", logicalTarget=" + logicalTarget);

        WalkTargetFailureMemory.record(villager, logicalTarget, helper.getLevel().getGameTime());
        Path retry = villager.getNavigation().createPath(logicalTarget, 1);

        helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                "surface-adjusted retry discarded failure evidence for the same logical target");
        helper.assertTrue(retry != null && retry.getEndNode() != null,
                "surface-adjusted retry did not produce a path");
        helper.assertTrue(start.distSqr(retry.getEndNode().asBlockPos())
                        > start.distSqr(ordinary.getEndNode().asBlockPos()),
                "surface-adjusted retry did not extend the path horizon; ordinaryEnd="
                        + ordinary.getEndNode().asBlockPos() + ", retryEnd=" + retry.getEndNode().asBlockPos());

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_long_distance_horizon", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void freshFarTargetDoesNotReusePreviousTargetFailureEvidence(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(40, 1, 40));
        BlockPos oldTarget = start.east(80);
        BlockPos newTarget = start.west(80);
        prepareFlatPath(helper, start, oldTarget);
        prepareFlatPath(helper, start, newTarget);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Fresh Long Path Horizon Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);

        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(oldTarget), 0.5F, 0));
        Path oldPath = villager.getNavigation().createPath(oldTarget, 0);
        helper.assertTrue(oldPath != null && !oldPath.canReach(),
                "fixture old destination did not establish an ordinary progressive path");
        long oldFailureSince = helper.getLevel().getGameTime() - 20L;
        WalkTargetFailureMemory.record(villager, oldTarget, oldFailureSince);

        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(newTarget), 0.5F, 0));
        Path freshPath = villager.getNavigation().createPath(newTarget, 0);

        helper.assertTrue(freshPath != null && !freshPath.canReach(),
                "fresh far destination reused stale failure evidence for an immediate extended search");
        helper.assertTrue(newTarget.equals(freshPath.getTarget()),
                "fresh far destination did not retain its real logical target");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        .filter(timestamp -> timestamp == oldFailureSince)
                        .isPresent(),
                "path query mutated failure timing owned by the previous route");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET)
                        .filter(failureTarget -> failureTarget.pos().equals(oldTarget))
                        .isPresent(),
                "path query mutated failure ownership instead of treating the new destination as fresh");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_unloaded_chunk", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void extendedSearchDoesNotLoadFarChunk(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(150);
        prepareFlatArea(helper, start, 2, 3);
        helper.assertTrue(!helper.getLevel().isLoaded(target),
                "fixture requires the distant target chunk to start unloaded");

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Unloaded Path Horizon Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));

        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            villager.getNavigation().createPath(target, 0);
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
        }

        helper.assertTrue(!helper.getLevel().isLoaded(target),
                "extended path request synchronously loaded the distant target chunk");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_progressive_walk", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 1_200)
    public static void longDistanceTargetWalksAcrossMultiplePathSegments(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        int pathHorizon = Math.max(Config.getInstance().getVillagerPathfindingDistance(), 48);
        BlockPos target = start.east(pathHorizon + 32);
        ChunkPos startChunk = ChunkPos.containing(start);
        ChunkPos targetChunk = ChunkPos.containing(target);
        for (int chunkX = startChunk.x(); chunkX <= targetChunk.x(); chunkX++) {
            PROGRESSIVE_FORCED_CHUNKS.add(new ChunkPos(chunkX, startChunk.z()));
            helper.getLevel().setChunkForced(chunkX, startChunk.z(), true);
        }
        prepareFlatPath(helper, start, target);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Progressive Navigation Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(target), 1.0F, 0));

        Path firstSegment = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(firstSegment != null && !firstSegment.canReach(),
                "fixture did not produce a bounded first path segment");
        helper.assertTrue(target.equals(firstSegment.getTarget()),
                "first path segment did not retain the real destination");
        helper.assertTrue(villager.getNavigation().moveTo(firstSegment, 1.0D),
                "first long-distance path segment could not start");

        helper.succeedWhen(() -> {
            Path activePath = villager.getNavigation().getPath();
            helper.assertTrue(villager.blockPosition().equals(target),
                    "villager did not walk across multiple path segments to the real destination"
                            + "; pos=" + villager.blockPosition()
                            + "; posLoaded=" + helper.getLevel().isLoaded(villager.blockPosition())
                            + "; targetLoaded=" + helper.getLevel().isLoaded(target)
                            + "; navDone=" + villager.getNavigation().isDone()
                            + "; path=" + summarizePath(activePath));
            villager.discard();
        });
    }

    @GameTest(batch = "mca_navigation_nearby_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 600)
    public static void nearbyPersistentTargetCanTakeRouteLongerThanFollowRange(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(40, 1, 40));
        BlockPos target = start.east(2);
        prepareFlatArea(helper, start, 30, 3);

        for (int z = -23; z <= 23; z++) {
            BlockPos wall = start.offset(1, 0, z);
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Nearby Long Detour Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        double followRangeBefore = villager.getAttributeValue(Attributes.FOLLOW_RANGE);
        var navigation = villager.getNavigation();
        navigation.stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        Path ordinary = navigation.createPath(target, 0);
        helper.assertTrue(ordinary != null && !ordinary.canReach(),
                "fixture ordinary path unexpectedly solved a detour longer than 48 blocks");

        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        Path extended;
        try {
            config.villagerPathfindingDistance = 160;
            extended = navigation.createPath(target, 0);
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
        }

        helper.assertTrue(extended != null && extended.canReach(),
                "extended search did not find the route around the 47-block wall");
        helper.assertTrue(target.equals(extended.getTarget()),
                "extended detour changed the logical destination");
        helper.assertTrue(villager.getAttributeValue(Attributes.FOLLOW_RANGE) == followRangeBefore,
                "extended detour changed sensing range");
        helper.assertTrue(navigation.moveTo(extended, 0.5D),
                "extended detour could not start navigation");

        Path cached = navigation.createPath(target, 0);
        helper.assertTrue(cached == extended,
                "fixture did not exercise vanilla's active-path cache reuse");

        // Exercise normal navigation, movement controls and collision, without moving
        // the villager or advancing path nodes from the test.
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.blockPosition().equals(target),
                    "villager has not walked around the wall to the destination");
            villager.discard();
        });
    }

    @GameTest(batch = "mca_navigation_progressive_budget", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void longDistancePersistentTargetKeepsBoundedBudget(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(40, 1, 40));
        BlockPos target = start.east(80);
        prepareFlatArea(helper, start.east(40), 45, 3);

        for (int z = -23; z <= 23; z++) {
            BlockPos wall = start.offset(24, 0, z);
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Progressive Budget Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;

            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            Path progressive = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(progressive != null && !progressive.canReach(),
                    "far static search consumed the enlarged detour budget in one pass");
            helper.assertTrue(MCAGroundPathNavigation.isUsefulPartialPath(progressive, target),
                    "bounded far-static search did not retain useful progress toward the real destination");

            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
            Path escalated = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(escalated != null && escalated.canReach(),
                    "canonical far-target retry did not receive the enlarged detour budget");
            helper.assertTrue(target.equals(escalated.getTarget()),
                    "canonical far-target retry changed the logical destination");
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
        }

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_progressive_budget", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void usefulNearbyPartialDefersExpensiveDetourEscalation(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(40, 1, 40));
        BlockPos target = start.east(30);
        prepareFlatArea(helper, start.east(15), 40, 3);

        for (int z = -23; z <= 23; z++) {
            BlockPos wall = start.offset(20, 0, z);
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Progressive Nearby Detour Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));

        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            Path first = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(first != null && !first.canReach(),
                    "useful ordinary partial was replaced by an immediate enlarged detour search");
            helper.assertTrue(MCAGroundPathNavigation.isUsefulPartialPath(first, target),
                    "ordinary search did not produce useful progress toward the nearby target");

            BlockPos checkpoint = first.getEndNode().asBlockPos();
            villager.setPos(Vec3.atBottomCenterOf(checkpoint));
            Path escalated = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(escalated != null && escalated.canReach(),
                    "detour escalation was not available after bounded progress became exhausted");
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
        }

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_failed_extended_backoff", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void failedNearbyDetourBacksOffUntilVillagerMoves(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(40, 1, 40));
        BlockPos target = start.east(2);
        prepareFlatArea(helper, start, 30, 3);
        for (int z = -23; z <= 23; z++) {
            BlockPos wall = start.offset(1, 0, z);
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        // The 47-block wall has a known navigable extended detour. Initially
        // enclose the destination so even that full detour cannot reach it.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos enclosure = target.offset(dx, 0, dz);
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(enclosure.above(y), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));

        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            Path blocked = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(blocked != null && !blocked.canReach(),
                    "fixture found a route into the sealed target enclosure");
            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());

            BlockPos gap = target.east();
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(gap.above(y), Blocks.AIR.defaultBlockState(), 3);
            }
            // A fresh navigation instance must find the opened detour; otherwise a
            // partial result could pass the backoff check for the wrong reason.
            VillagerEntityMCA fresh = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0)
                    .withPosition(Vec3.atBottomCenterOf(start))
                    .spawn(EntitySpawnReason.STRUCTURE);
            try {
                fresh.refreshBrain(helper.getLevel());
                fresh.getBrain().removeAllBehaviors();
                fresh.setNoAi(true);
                fresh.setOnGround(true);
                fresh.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
                fresh.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
                Path freshPath = fresh.getNavigation().createPath(target, 0);
                helper.assertTrue(freshPath != null && freshPath.canReach(),
                        "opened detour is not navigable without backoff; path=" + summarizePath(freshPath));
            } finally {
                fresh.discard();
            }
            Path backedOff = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(backedOff != null && !backedOff.canReach(),
                    "same-position retry repeated the extended search despite a recent failed attempt");

            villager.setPos(Vec3.atBottomCenterOf(start.south(5)));
            villager.setOnGround(true);
            Path afterMoving = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(afterMoving != null && (afterMoving.canReach()
                            || MCAGroundPathNavigation.isUsefulPartialPath(afterMoving, target)),
                    "moving five blocks did not permit ordinary progress; path=" + summarizePath(afterMoving));
            // Moving must invalidate the stale backoff even when the ordinary path
            // makes progress and defers the detour search on this particular tick.
            villager.setPos(Vec3.atBottomCenterOf(start));
            villager.setOnGround(true);
            Path afterReturning = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(afterReturning != null && afterReturning.canReach(),
                    "meaningful movement did not invalidate the old failed detour; path=" + summarizePath(afterReturning));
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_failed_extended_backoff", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void failedFarTargetUsesBoundedRetryUntilMovement(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(80);
        prepareFlatPath(helper, start, target);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos enclosure = start.offset(dx, 0, dz);
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(enclosure.above(y), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));

        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            WalkTargetFailureMemory.record(villager, target, helper.getLevel().getGameTime());
            Path blocked = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(blocked == null || !blocked.canReach(),
                    "sealed start unexpectedly reached the far destination");

            BlockPos exit = start.east();
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(exit.above(y), Blocks.AIR.defaultBlockState(), 3);
            }
            Path backedOff = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(backedOff != null && !backedOff.canReach()
                            && MCAGroundPathNavigation.isUsefulPartialPath(backedOff, target),
                    "repeated far failure did not use a bounded progressive path; path=" + summarizePath(backedOff));

            villager.setPos(Vec3.atBottomCenterOf(start.east(5)));
            villager.setOnGround(true);
            Path afterMoving = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(afterMoving != null && afterMoving.canReach(),
                    "meaningful movement did not unlock a full far-target search; path=" + summarizePath(afterMoving));
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_local_brownian", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void insideBrownianWalkPublishesLocalTarget(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 10));
        prepareFlatArea(helper, start, 3, 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);

        BlockPos feet = villager.blockPosition();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.getLevel().setBlock(feet.offset(x, 2, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        helper.succeedWhen(() -> {
            helper.assertTrue(!helper.getLevel().canSeeSky(villager.blockPosition()),
                    "indoor Brownian fixture has open sky at " + feet
                            + "; roof=" + helper.getLevel().getBlockState(feet.above(2)));
            helper.assertTrue(LocalInsideBrownianWalk.create(0.5F)
                            .tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                    "indoor Brownian walk did not start under a roof");
            WalkTarget published = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                    .orElseThrow();
            helper.assertTrue(!(published.getTarget() instanceof PersistentPathTarget),
                    "indoor Brownian walk incorrectly published persistent navigation intent");
            helper.assertTrue(published.getTarget().currentBlockPosition().distManhattan(start) <= 3,
                    "indoor Brownian walk selected a nonlocal destination");
            villager.discard();
        });
    }

    @GameTest(batch = "mca_navigation_local_brownian_retry", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void stalledIndoorStrollWaitsButAPhysicalStepAllowsNewDestination(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 10));
        prepareFlatArea(helper, start, 3, 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        BlockPos feet = villager.blockPosition();
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.getLevel().setBlock(feet.offset(x, 2, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        var stroll = LocalInsideBrownianWalk.create(0.5F);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!helper.getLevel().canSeeSky(villager.blockPosition()),
                    "indoor stroll fixture has open sky");
            helper.assertTrue(stroll.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                    "indoor stroll did not start");
            helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                    "indoor stroll did not publish a destination");
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            helper.assertTrue(!stroll.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                    "stalled villager immediately issued another random stroll");

            villager.setPos(Vec3.atBottomCenterOf(start.east()));
            helper.assertTrue(stroll.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                    "moving to a different block did not permit a fresh indoor stroll");
            helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                    "moving villager did not receive a new indoor destination");
            villager.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_navigation_local_brownian_rejected", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void existingWalkTargetRejectionDoesNotDelayIndoorStroll(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 10));
        prepareFlatArea(helper, start, 3, 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                helper.getLevel().setBlock(start.offset(x, 2, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        var stroll = LocalInsideBrownianWalk.create(0.5F);
        helper.runAfterDelay(5, () -> {
            try {
                long gameTime = helper.getLevel().getGameTime();
                helper.assertTrue(!helper.getLevel().canSeeSky(villager.blockPosition()),
                        "rejected-target fixture is not indoors");
                WalkTarget otherTarget = new WalkTarget(start.east(2), 0.5F, 0);
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, otherTarget);
                helper.assertTrue(!stroll.tryStart(helper.getLevel(), villager, gameTime),
                        "indoor stroll replaced another behavior's walk target");
                helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                                .orElseThrow() == otherTarget,
                        "rejected indoor stroll changed the existing walk target");

                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                helper.assertTrue(InsideBrownianWalk.create(0.5F)
                                .tryStart(helper.getLevel(), villager, gameTime)
                                && villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                        "vanilla cannot publish an indoor destination in the cleared fixture");
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                helper.assertTrue(villager.blockPosition().equals(start),
                        "villager moved and would hide the rejected-start cooldown");
                helper.assertTrue(stroll.tryStart(helper.getLevel(), villager, gameTime),
                        "existing walk-target rejection consumed the indoor-stroll cooldown");
                helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                                .filter(target -> !(target.getTarget() instanceof PersistentPathTarget))
                                .isPresent(),
                        "newly eligible indoor stroll published persistent navigation intent");
                helper.succeed();
            } finally {
                villager.discard();
            }
        });
    }

    @GameTest(batch = "mca_navigation_local_brownian_rejected", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void outdoorRejectionDoesNotDelayNewlyIndoorStroll(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 1, 10));
        prepareFlatArea(helper, start, 3, 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        var stroll = LocalInsideBrownianWalk.create(0.5F);
        helper.runAfterDelay(5, () -> {
            try {
                long gameTime = helper.getLevel().getGameTime();
                helper.assertTrue(helper.getLevel().canSeeSky(villager.blockPosition()),
                        "outdoor rejection fixture already has a roof");
                helper.assertTrue(!stroll.tryStart(helper.getLevel(), villager, gameTime),
                        "indoor stroll started under open sky");
                helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                        "outdoor rejection published a walk target");

                for (int x = -2; x <= 2; x++) {
                    for (int z = -2; z <= 2; z++) {
                        helper.getLevel().setBlock(start.offset(x, 2, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
                // canSeeSky reads propagated sky light, not just the newly placed roof blocks.
                helper.startSequence().thenWaitUntil(() -> {
                    helper.assertTrue(helper.getLevel().getGameTime() - gameTime < 40L,
                            "sky lighting did not settle before the rejected-start cooldown expired");
                    helper.assertTrue(!helper.getLevel().canSeeSky(villager.blockPosition()),
                            "new roof has not updated sky lighting yet");
                }).thenExecute(() -> {
                    try {
                        long indoorTime = helper.getLevel().getGameTime();
                        helper.assertTrue(InsideBrownianWalk.create(0.5F)
                                        .tryStart(helper.getLevel(), villager, indoorTime)
                                        && villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                                "vanilla cannot publish an indoor destination under the new roof");
                        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                        helper.assertTrue(villager.blockPosition().equals(start),
                                "villager moved and would hide the rejected-start cooldown");
                        helper.assertTrue(stroll.tryStart(helper.getLevel(), villager, indoorTime),
                                "outdoor rejection consumed the indoor-stroll cooldown");
                        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                                        .filter(target -> !(target.getTarget() instanceof PersistentPathTarget))
                                        .isPresent(),
                                "newly indoor stroll published persistent navigation intent");
                    } finally {
                        villager.discard();
                    }
                }).thenSucceed();
            } catch (RuntimeException | Error failure) {
                villager.discard();
                throw failure;
            }
        });
    }

    @GameTest(batch = "mca_navigation_target_scope", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void onlyExplicitPersistentTargetEscalatesFarPathSearch(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(80);
        prepareFlatPath(helper, start, target);

        VillagerEntityMCA ordinaryVillager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .spawn(EntitySpawnReason.STRUCTURE);
        ordinaryVillager.refreshBrain(helper.getLevel());
        ordinaryVillager.getBrain().removeAllBehaviors();
        ordinaryVillager.setNoAi(true);
        ordinaryVillager.setOnGround(true);
        ordinaryVillager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        VillagerEntityMCA persistentVillager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .spawn(EntitySpawnReason.STRUCTURE);
        persistentVillager.refreshBrain(helper.getLevel());
        persistentVillager.getBrain().removeAllBehaviors();
        persistentVillager.setNoAi(true);
        persistentVillager.setOnGround(true);
        persistentVillager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            ordinaryVillager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 0));
            Path ordinaryProbe = ordinaryVillager.getNavigation().createPath(target, 0);
            helper.assertTrue(ordinaryProbe != null && !ordinaryProbe.canReach(),
                    "fixture ordinary disposable path unexpectedly reached the far target");
            WalkTargetFailureMemory.record(ordinaryVillager, target, helper.getLevel().getGameTime());
            PathRequestDiagnostics.SearchSnapshot ordinaryBefore = PathRequestDiagnostics.snapshot(ordinaryVillager);
            ordinaryVillager.getNavigation().createPath(target, 0);
            PathRequestDiagnostics.SearchSnapshot ordinaryAfter = PathRequestDiagnostics.snapshot(ordinaryVillager);
            helper.assertTrue(ordinaryAfter.extendedSearches() == ordinaryBefore.extendedSearches(),
                    "ordinary disposable destination escalated into an extended path search");

            persistentVillager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
            Path persistentProbe = persistentVillager.getNavigation().createPath(target, 0);
            helper.assertTrue(persistentProbe != null && !persistentProbe.canReach(),
                    "fixture persistent path unexpectedly reached the far target before retry");
            WalkTargetFailureMemory.record(persistentVillager, target, helper.getLevel().getGameTime());
            PathRequestDiagnostics.SearchSnapshot persistentBefore = PathRequestDiagnostics.snapshot(persistentVillager);
            persistentVillager.getNavigation().createPath(target, 0);
            PathRequestDiagnostics.SearchSnapshot persistentAfter = PathRequestDiagnostics.snapshot(persistentVillager);
            helper.assertTrue(persistentAfter.extendedSearches() > persistentBefore.extendedSearches(),
                    "explicit persistent destination did not receive an extended path search");
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
        }
        ordinaryVillager.discard();
        persistentVillager.discard();
        helper.succeed();
    }

    @AfterBatch(batch = "mca_navigation_progressive_walk")
    public static void releaseProgressiveNavigationChunks(ServerLevel level) {
        PROGRESSIVE_FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        PROGRESSIVE_FORCED_CHUNKS.clear();
    }

    private static String summarizePath(Path path) {
        if (path == null) {
            return "null";
        }
        return "target=" + path.getTarget()
                + ", end=" + (path.getEndNode() == null ? null : path.getEndNode().asBlockPos())
                + ", canReach=" + path.canReach()
                + ", next=" + path.getNextNodeIndex() + '/' + path.getNodeCount();
    }

    private static BlockState stableScaffolding() {
        return Blocks.SCAFFOLDING.defaultBlockState()
                .setValue(ScaffoldingBlock.DISTANCE, 0)
                .setValue(ScaffoldingBlock.BOTTOM, false);
    }

    private static void placeStableScaffoldingColumn(GameTestHelper helper, BlockPos bottom) {
        for (int rise = 0; rise <= 2; rise++) {
            helper.getLevel().setBlock(bottom.above(rise), stableScaffolding(), 3);
        }
    }

    private static final class ProbeMoveControl extends MCAMoveControl {
        private ProbeMoveControl(VillagerEntityMCA villager) {
            super(villager);
        }

        private void markJumping() {
            this.operation = Operation.JUMPING;
        }

        private boolean isJumping() {
            return this.operation == Operation.JUMPING;
        }
    }

}
