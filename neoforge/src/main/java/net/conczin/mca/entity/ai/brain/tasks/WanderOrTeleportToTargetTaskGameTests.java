package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.gametest.GameTestBrain;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.navigation.PersistentPathTarget;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;
import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class WanderOrTeleportToTargetTaskGameTests {
    private WanderOrTeleportToTargetTaskGameTests() {
    }

    @GameTest(batch = "mca_walk_target_failure_owner", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void changedWalkTargetGetsFreshFailureOwnership(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(40, 1, 40));
        BlockPos oldTarget = start.east(80);
        BlockPos newTarget = start.west(80);
        prepareFlatPath(helper, start, oldTarget);
        prepareFlatPath(helper, start, newTarget);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Walk Failure Owner Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);

        var brain = villager.getBrain();
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(oldTarget), 0.5F, 0));
        Path oldPath = villager.getNavigation().createPath(oldTarget, 0);
        helper.assertTrue(oldPath != null && !oldPath.canReach(),
                "fixture old target did not produce a bounded partial path");
        WalkTargetFailureMemory.record(villager, oldTarget, helper.getLevel().getGameTime() - 20L);

        brain.eraseMemory(MemoryModuleType.PATH);
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(newTarget), 0.5F, 0));
        WanderOrTeleportToTargetTask sink = new WanderOrTeleportToTargetTask();
        long retryTime = helper.getLevel().getGameTime();
        helper.assertTrue(sink.tryStart(helper.getLevel(), villager, retryTime),
                "new far target did not start its ordinary partial-path attempt");

        Path freshPath = villager.getNavigation().getPath();
        helper.assertTrue(freshPath != null && !freshPath.canReach(),
                "new far target inherited the old route's extended search");
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).isEmpty(),
                "useful partial progress toward the new target kept stale failure timing");
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET).isEmpty(),
                "useful partial progress toward the new target kept stale failure ownership");

        while (!freshPath.isDone()) {
            freshPath.advance();
        }
        long finishedAt = retryTime + 1L;
        sink.tickOrStop(helper.getLevel(), villager, finishedAt);

        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        .filter(timestamp -> timestamp == finishedAt)
                        .isPresent(),
                "finished-short path did not establish fresh failure timing for the new target");
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET)
                        .filter(failureTarget -> failureTarget.pos().equals(newTarget))
                        .isPresent(),
                "finished-short path did not assign failure ownership to the new target");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_walk_target_timeout_scope", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void onlyPersistentStaticTargetsSuppressVanillaMovementTimeout(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos target = start.east(6);
        BlockPos farStaticTarget = start.east(60);
        prepareFlatArea(helper, start, 10, 2);
        prepareFlatPath(helper, start, farStaticTarget);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Walk Timeout Scope Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);

        long startedAt = helper.getLevel().getGameTime();
        var brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 0));

        WanderOrTeleportToTargetTask ordinarySink = new WanderOrTeleportToTargetTask();
        helper.assertTrue(ordinarySink.tryStart(helper.getLevel(), villager, startedAt),
                "ordinary movement sink did not start");
        helper.assertTrue(ordinarySink.timedOut(startedAt + 251L),
                "ordinary WALK_TARGET incorrectly disabled vanilla movement timeout");
        ordinarySink.doStop(helper.getLevel(), villager, startedAt + 251L);

        brain.eraseMemory(MemoryModuleType.PATH);
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(farStaticTarget, 0.5F, 0));

        WanderOrTeleportToTargetTask farStaticSink = new WanderOrTeleportToTargetTask();
        helper.assertTrue(farStaticSink.tryStart(helper.getLevel(), villager, startedAt),
                "far disposable movement sink did not start");
        helper.assertTrue(farStaticSink.timedOut(startedAt + 251L),
                "far disposable WALK_TARGET incorrectly disabled vanilla movement timeout");
        farStaticSink.doStop(helper.getLevel(), villager, startedAt + 251L);

        brain.eraseMemory(MemoryModuleType.PATH);
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
        WanderOrTeleportToTargetTask nearbyPersistentSink = new WanderOrTeleportToTargetTask();
        helper.assertTrue(nearbyPersistentSink.tryStart(helper.getLevel(), villager, startedAt),
                "nearby persistent movement sink did not start");
        Path nearbyPath = villager.getNavigation().getPath();
        helper.assertTrue(nearbyPath != null && nearbyPath.canReach() && !nearbyPath.isDone(),
                "nearby persistent fixture did not have unfinished reachable navigation");
        nearbyPersistentSink.tickOrStop(helper.getLevel(), villager, startedAt + 251L);
        helper.assertTrue(villager.getNavigation().getPath() == nearbyPath
                        && brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "nearby persistent travel discarded unfinished navigation at vanilla's behavior deadline");
        nearbyPersistentSink.doStop(helper.getLevel(), villager, startedAt + 251L);

        brain.eraseMemory(MemoryModuleType.PATH);
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(farStaticTarget), 0.5F, 0));

        WanderOrTeleportToTargetTask persistentSink = new WanderOrTeleportToTargetTask();
        helper.assertTrue(persistentSink.tryStart(helper.getLevel(), villager, startedAt),
                "persistent movement sink did not start");
        helper.assertTrue(!persistentSink.timedOut(startedAt + 251L),
                "persistent WALK_TARGET lost its extended movement lifetime");
        persistentSink.doStop(helper.getLevel(), villager, startedAt + 251L);

        BlockPos movingTargetPos = start.east(60).north(4);
        VillagerEntityMCA movingTarget = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(movingTargetPos))
                .withName("Moving Timeout Target")
                .spawn(EntitySpawnReason.STRUCTURE);
        movingTarget.setNoAi(true);

        brain.eraseMemory(MemoryModuleType.PATH);
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new EntityTracker(movingTarget, false), 0.5F, 0));

        WanderOrTeleportToTargetTask movingTargetSink = new WanderOrTeleportToTargetTask();
        helper.assertTrue(movingTargetSink.tryStart(helper.getLevel(), villager, startedAt),
                "far entity-target movement sink did not start");
        helper.assertTrue(movingTargetSink.timedOut(startedAt + 251L),
                "far entity WALK_TARGET incorrectly inherited static extended-path lifetime");

        movingTarget.discard();
        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_walk_target_intent_change", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void changedTargetIntentUsesCurrentMovementLifetime(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos target = start.east(6);
        prepareFlatArea(helper, start, 10, 2);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);
        var brain = villager.getBrain();
        long startedAt = helper.getLevel().getGameTime();
        try {
            for (int offset : new int[]{0, 2}) {
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 0));
                WanderOrTeleportToTargetTask sink = new WanderOrTeleportToTargetTask();
                helper.assertTrue(sink.tryStart(helper.getLevel(), villager, startedAt),
                        "ordinary movement did not start before intent change");
                Path activePath = villager.getNavigation().getPath();
                brain.setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new PersistentPathTarget(target.east(offset)), 0.5F, 0));
                sink.tickOrStop(helper.getLevel(), villager, startedAt + 251L);
                helper.assertTrue(brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)
                                && villager.getNavigation().getPath() == activePath
                                && !villager.getNavigation().isDone(),
                        "persistent intent inherited the ordinary timeout after a target shift of " + offset);
                sink.doStop(helper.getLevel(), villager, startedAt + 251L);

                brain.setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));
                sink = new WanderOrTeleportToTargetTask();
                helper.assertTrue(sink.tryStart(helper.getLevel(), villager, startedAt),
                        "persistent movement did not start before intent change");
                brain.setMemory(MemoryModuleType.WALK_TARGET,
                        new WalkTarget(target.east(offset), 0.5F, 0));
                sink.tickOrStop(helper.getLevel(), villager, startedAt + 251L);
                helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)
                                && villager.getNavigation().isDone(),
                        "ordinary intent retained persistent lifetime after a target shift of " + offset);
            }
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_walk_target_timeout_scope", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 1_000)
    public static void ordinaryStaticTargetDoesNotStartExtendedDetour(GameTestHelper helper) {
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
                .withName("Disposable Walk Target Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        var brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 0));

        WanderOrTeleportToTargetTask sink = new WanderOrTeleportToTargetTask();
        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            helper.assertTrue(sink.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                    "ordinary movement sink did not start its bounded path");
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
        }

        Path path = villager.getNavigation().getPath();
        helper.assertTrue(path != null && !path.canReach(),
                "ordinary disposable target escalated into an extended detour");
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).isPresent(),
                "ordinary disposable partial path did not retain vanilla unreachable evidence");
        while (!path.isDone()) {
            path.advance();
        }
        villager.getNavigation().tick();
        helper.assertTrue(!sink.canStillUse(helper.getLevel(), villager, helper.getLevel().getGameTime() + 1L),
                "ordinary disposable target started MCA detour recovery after its bounded path ended");
        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_walk_target_timeout_scope", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 1_000)
    public static void nearbyReachableLongDetourCanFinishWithoutMovementTimeout(GameTestHelper helper) {
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
                .withName("Nearby Extended Timeout Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        var brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        Path ordinary = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(ordinary != null && !ordinary.canReach(),
                "fixture ordinary path unexpectedly solved the long nearby detour");
        villager.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.5D);
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(target), 0.5F, 0));

        WanderOrTeleportToTargetTask sink = new WanderOrTeleportToTargetTask();
        long startedAt = helper.getLevel().getGameTime();
        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            helper.assertTrue(sink.tryStart(helper.getLevel(), villager, startedAt),
                    "movement sink did not start the nearby extended detour");
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
        }
        Path path = villager.getNavigation().getPath();
        helper.assertTrue(path != null && path.canReach(),
                "fixture did not select the reachable extended detour");
        helper.assertTrue(!MCAGroundPathNavigation.requiresExtendedPath(villager, target),
                "fixture target must remain inside the ordinary geometric range");
        helper.assertTrue(!sink.timedOut(startedAt + 251L),
                "long reachable route did not qualify for extended lifetime; nodes=" + path.getNodeCount()
                        + ", walkedDistance=" + path.getEndNode().walkedDistance
                        + ", pathTarget=" + path.getTarget() + ", walkTarget=" + target);

        boolean[] continuedAfterVanillaTimeout = {false};
        boolean[] madePhysicalProgress = {false};
        helper.onEachTick(() -> {
            long gameTime = helper.getLevel().getGameTime();
            sink.tickOrStop(helper.getLevel(), villager, gameTime);
            madePhysicalProgress[0] |= villager.blockPosition().distSqr(start) >= 16.0D;
            if (gameTime - startedAt < 260L) {
                return;
            }

            if (villager.blockPosition().equals(target)) {
                helper.assertTrue(continuedAfterVanillaTimeout[0],
                        "fixture did not exercise natural movement beyond the vanilla timeout");
                villager.discard();
                helper.succeed();
            } else {
                helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                        "reachable long detour lost its WALK_TARGET; position=" + villager.position()
                                + ", path=" + villager.getNavigation().getPath()
                                + ", stuck=" + villager.getNavigation().isStuck()
                                + ", timedOut=" + sink.timedOut(gameTime));
                helper.assertTrue(madePhysicalProgress[0],
                        "long detour kept a movement behavior without physical progress");
                continuedAfterVanillaTimeout[0] = true;
            }
            if (gameTime - startedAt >= 950L) {
                Path active = villager.getNavigation().getPath();
                helper.fail("long detour did not finish; position=" + villager.position()
                        + ", entityTicks=" + villager.tickCount
                        + ", entityTicking=" + helper.getLevel().isPositionEntityTicking(villager.blockPosition())
                        + ", noAi=" + villager.isNoAi() + ", width=" + villager.getBbWidth()
                        + ", speed=" + villager.getSpeed() + ", grounded=" + villager.onGround()
                        + ", movement=" + villager.getDeltaMovement()
                        + ", path=" + active
                        + ", next=" + (active == null ? null : active.getNextNodeIndex())
                        + ", node=" + (active == null || active.isDone() ? null : active.getNextNodePos())
                        + ", wanted=" + (active == null || active.isDone() ? null : active.getNextEntityPos(villager)));
            }
        });
    }

    @GameTest(batch = "mca_failed_path_retry_cadence", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void failedNearbyPathRetryIsNotThrottledInsideMovementSink(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 2, 10));
        BlockPos destination = start.east(6);
        prepareFlatArea(helper, start, 10, 2);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Failed Path Retry Cadence Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setPos(villager.getX(), villager.getY() + 2.0D, villager.getZ());
        villager.setNoGravity(true);
        villager.setOnGround(false);

        var brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        WanderOrTeleportToTargetTask sink = new WanderOrTeleportToTargetTask();
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, 0.5F, 0));
        long firstFailureAt = helper.getLevel().getGameTime();
        helper.assertTrue(!sink.tryStart(helper.getLevel(), villager, firstFailureAt),
                "airborne initial attempt unexpectedly created a path");
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                "initial failed path did not erase its WALK_TARGET");
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        .filter(since -> since == firstFailureAt)
                        .isPresent(),
                "initial failed path did not establish the canonical failure timestamp");

        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, 0.5F, 0));
        helper.assertTrue(!sink.tryStart(helper.getLevel(), villager, firstFailureAt + 1L),
                "airborne retry unexpectedly created a path");
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                "movement sink throttled the retry instead of letting its producer own retry cadence");
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        .filter(since -> since == firstFailureAt)
                        .isPresent(),
                "failed retry rewrote the canonical failure-since timestamp");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_brain_driven_long_distance_walk", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 2_400)
    public static void brainDrivenLongDistanceWalkArrivesAfterMultipleSegmentsAndTimeoutWindow(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        int pathHorizon = Math.max(Config.getInstance().getVillagerPathfindingDistance(), 48);
        BlockPos destination = start.east(pathHorizon * 2 + 64);
        ChunkPos startChunk = ChunkPos.containing(start);
        ChunkPos targetChunk = ChunkPos.containing(destination);
        for (int chunkX = startChunk.x(); chunkX <= targetChunk.x(); chunkX++) {
            helper.getLevel().setChunkForced(chunkX, startChunk.z(), true);
        }
        prepareFlatPath(helper, start, destination);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Brain Driven Long Distance Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        var brain = villager.getBrain();
        brain.stopAll(helper.getLevel(), villager);
        brain.removeAllBehaviors();
        GameTestBrain.addActivity(brain, Activity.CORE, 1, ImmutableList.of(new WanderOrTeleportToTargetTask()));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(destination), 1.0F, 0));

        long startedAt = helper.getLevel().getGameTime();
        Path[] lastPath = {null};
        int[] pathSegments = {0};
        BlockPos[] furthestPosition = {start};
        Vec3[] lastPosition = {villager.position()};
        int[] stationaryTicks = {0};
        int[] longestStationaryRun = {0};

        helper.onEachTick(() -> {
            Path activePath = villager.getNavigation().getPath();
            if (activePath != null && activePath != lastPath[0]) {
                lastPath[0] = activePath;
                pathSegments[0]++;
            }
            if (villager.blockPosition().distManhattan(destination)
                    < furthestPosition[0].distManhattan(destination)) {
                furthestPosition[0] = villager.blockPosition();
            }

            long elapsed = helper.getLevel().getGameTime() - startedAt;
            Vec3 position = villager.position();
            if (elapsed > 20L && position.distanceToSqr(lastPosition[0]) < 1.0E-6D) {
                stationaryTicks[0]++;
                longestStationaryRun[0] = Math.max(longestStationaryRun[0], stationaryTicks[0]);
            } else {
                stationaryTicks[0] = 0;
            }
            lastPosition[0] = position;

            if (stationaryTicks[0] >= 40
                    && villager.blockPosition().distManhattan(destination) > 4) {
                helper.fail("brain-driven journey paused for at least 40 ticks; elapsed=" + elapsed
                        + "; pos=" + villager.blockPosition()
                        + "; distance=" + villager.blockPosition().distManhattan(destination)
                        + "; segments=" + pathSegments[0]
                        + "; navDone=" + villager.getNavigation().isDone()
                        + "; navStuck=" + villager.getNavigation().isStuck()
                        + "; activities=" + brain.getActiveActivities()
                        + "; home=" + brain.getMemoryInternal(MemoryModuleType.HOME)
                        + "; walkTarget=" + brain.getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        + "; cantReach=" + brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        + "; path=" + activePath
                        + "; pathIndex=" + (activePath == null ? -1 : activePath.getNextNodeIndex())
                        + "/" + (activePath == null ? -1 : activePath.getNodeCount())
                        + "; next=" + (activePath == null || activePath.isDone() ? null : activePath.getNextNodePos())
                        + "; end=" + (activePath == null || activePath.getEndNode() == null
                                ? null
                                : activePath.getEndNode().asBlockPos())
                        + "; canReach=" + (activePath != null && activePath.canReach())
                        + "; onGround=" + villager.onGround()
                        + "; horizontalCollision=" + villager.horizontalCollision
                        + "; motion=" + villager.getDeltaMovement());
                return;
            }

            if (elapsed == 80L) {
                helper.assertTrue(pathSegments[0] > 0,
                        "brain-owned movement sink never started a path; walkTarget="
                                + brain.getMemoryInternal(MemoryModuleType.WALK_TARGET));
                helper.assertTrue(!furthestPosition[0].equals(start),
                        "brain-owned movement sink started but villager never moved; path=" + activePath);
            }

            if (elapsed == 2_200L) {
                helper.fail("brain-driven journey stalled before arrival; pos=" + villager.blockPosition()
                        + "; furthest=" + furthestPosition[0]
                        + "; distance=" + villager.blockPosition().distManhattan(destination)
                        + "; segments=" + pathSegments[0]
                        + "; navDone=" + villager.getNavigation().isDone()
                        + "; navStuck=" + villager.getNavigation().isStuck()
                        + "; walkTarget=" + brain.getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        + "; cantReach=" + brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        + "; path=" + activePath);
                return;
            }

            if (!villager.blockPosition().equals(destination)) {
                return;
            }

            helper.assertTrue(elapsed > 250L,
                    "brain-driven journey reached the destination before the vanilla timeout window");
            helper.assertTrue(pathSegments[0] >= 2,
                    "brain-driven journey did not traverse multiple path segments");
            helper.assertTrue(longestStationaryRun[0] < 40,
                    "brain-driven journey contained a multi-second pause before arrival");
            helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).isEmpty(),
                    "successful brain-driven journey retained unreachable evidence");
            villager.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_normal_brain_no_home_walk", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 900)
    public static void normalRestWithoutHomeDoesNotPauseAndResumeBetweenWalkLegs(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(40, 1, 40));
        prepareFlatArea(helper, start, 64, 2);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Normal Brain No Home Pause Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(true);

        var brain = villager.getBrain();
        brain.setSchedule(GameTestBrain.fixedSchedule(Activity.REST));
        brain.setActiveActivityIfPossible(Activity.REST);
        brain.eraseMemory(MemoryModuleType.HOME);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);

        long startedAt = helper.getLevel().getGameTime();
        Vec3[] lastPosition = {villager.position()};
        Vec3[] pausePosition = {null};
        WalkTarget[] lastWalkTarget = {null};
        int[] stationaryTicks = {0};
        int[] walkLegs = {0};
        boolean[] hasWalked = {false};
        String[] pauseSnapshot = {null};

        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - startedAt;
            Vec3 position = villager.position();
            WalkTarget walkTarget = brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
            if (walkTarget != null && walkTarget != lastWalkTarget[0]) {
                lastWalkTarget[0] = walkTarget;
                walkLegs[0]++;
            }
            double tickMovement = position.distanceToSqr(lastPosition[0]);
            if (position.distanceToSqr(Vec3.atBottomCenterOf(start)) > 4.0D) {
                hasWalked[0] = true;
            }

            if (hasWalked[0] && tickMovement < 1.0E-6D) {
                stationaryTicks[0]++;
            } else {
                stationaryTicks[0] = 0;
            }

            if (pausePosition[0] == null && stationaryTicks[0] >= 20) {
                pausePosition[0] = position;
                pauseSnapshot[0] = "elapsed=" + elapsed
                        + "; pos=" + villager.blockPosition()
                        + "; navDone=" + villager.getNavigation().isDone()
                        + "; navStuck=" + villager.getNavigation().isStuck()
                        + "; activity=" + brain.getActiveNonCoreActivity()
                        + "; running=" + brain.getRunningBehaviors()
                        + "; home=" + brain.getMemoryInternal(MemoryModuleType.HOME)
                        + "; walkTarget=" + brain.getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        + "; cantReach=" + brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        + "; path=" + villager.getNavigation().getPath();
            } else if (pausePosition[0] != null && position.distanceToSqr(pausePosition[0]) > 1.0E-4D) {
                helper.fail("reproduced normal-brain no-HOME pause followed by resumed walking; " + pauseSnapshot[0]
                        + "; resumedAt=" + elapsed
                        + "; resumedPos=" + villager.blockPosition()
                        + "; resumedWalkTarget=" + brain.getMemoryInternal(MemoryModuleType.WALK_TARGET));
                return;
            }

            lastPosition[0] = position;
            if (elapsed >= 800L) {
                helper.assertTrue(hasWalked[0],
                        "homeless REST fixture never began walking");
                helper.assertTrue(walkLegs[0] >= 2,
                        "homeless REST fixture did not exercise multiple village-seeking walk legs");
                helper.assertTrue(pausePosition[0] == null,
                        "homeless REST travel paused for at least 20 ticks and never resumed; " + pauseSnapshot[0]);
                villager.discard();
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_normal_night_sleep", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 700)
    public static void normalNightVillagersAcquireHomesAndSleep(GameTestHelper helper) {
        boolean mourningEnabled = Config.getInstance().enableMourning;
        Config.getInstance().enableMourning = false;
        BlockPos center = helper.absolutePos(new BlockPos(56, 1, 56));
        prepareFlatArea(helper, center, 24, 3);
        helper.setTime(13_000L);

        VillagerEntityMCA[] villagers = new VillagerEntityMCA[4];
        boolean[] sawHome = new boolean[villagers.length];
        boolean[] sawWalkTarget = new boolean[villagers.length];
        for (int index = 0; index < villagers.length; index++) {
            BlockPos foot = center.offset(8, 0, -9 + index * 6);
            placeBed(helper, foot, Direction.EAST);

            BlockPos start = center.offset(-8, 0, -9 + index * 6);
            VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0)
                    .withPosition(Vec3.atBottomCenterOf(start))
                    .withName("Normal Night Sleeper " + index)
                    .spawn(EntitySpawnReason.STRUCTURE);
            villager.refreshBrain(helper.getLevel());
            villager.setOnGround(true);
            villager.getBrain().setSchedule(SchedulesMCA.DEFAULT);
            villager.getBrain().updateActivityFromSchedule(
                    helper.getLevel().environmentAttributes(),
                    helper.getLevel().getGameTime(),
                    villager.position()
            );
            villager.getBrain().eraseMemory(MemoryModuleType.HOME);
            villager.getBrain().eraseMemory(MemoryModuleType.PATH);
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            villagers[index] = villager;
        }

        PoiManager initialPoiManager = helper.getLevel().getPoiManager();
        long initialHomePois = initialPoiManager.getCountInRange(
                poi -> poi.is(PoiTypes.HOME), center, 48, PoiManager.Occupancy.ANY);
        long initialAvailableHomePois = initialPoiManager.getCountInRange(
                poi -> poi.is(PoiTypes.HOME), center, 48, PoiManager.Occupancy.HAS_SPACE);
        helper.assertTrue(initialHomePois == villagers.length,
                "night fixture did not register exactly one HOME POI per bed; homePois=" + initialHomePois);
        helper.assertTrue(initialAvailableHomePois == villagers.length,
                "night fixture consumed a HOME POI before natural brain ticking began; availableHomePois="
                        + initialAvailableHomePois);

        long startedAt = helper.getLevel().getGameTime();
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - startedAt;
            int sleeping = 0;
            for (int index = 0; index < villagers.length; index++) {
                VillagerEntityMCA villager = villagers[index];
                sawHome[index] |= villager.getBrain().getMemoryInternal(MemoryModuleType.HOME).isPresent();
                sawWalkTarget[index] |= villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent();
                if (villager.isSleeping()) {
                    sleeping++;
                }
            }
            if (sleeping == villagers.length) {
                Config.getInstance().enableMourning = mourningEnabled;
                for (VillagerEntityMCA villager : villagers) {
                    villager.discard();
                }
                helper.succeed();
                return;
            }

            if (elapsed < 600L) {
                return;
            }

            Config.getInstance().enableMourning = mourningEnabled;
            StringBuilder state = new StringBuilder();
            PoiManager poiManager = helper.getLevel().getPoiManager();
            for (int index = 0; index < villagers.length; index++) {
                VillagerEntityMCA villager = villagers[index];
                if (villager.isSleeping()) {
                    continue;
                }
                var brain = villager.getBrain();
                long homePois = poiManager.getCountInRange(
                        poi -> poi.is(PoiTypes.HOME), villager.blockPosition(), 48, PoiManager.Occupancy.ANY);
                long availableHomePois = poiManager.getCountInRange(
                        poi -> poi.is(PoiTypes.HOME), villager.blockPosition(), 48, PoiManager.Occupancy.HAS_SPACE);
                state.append("; ")
                        .append(villager.getName().getString())
                        .append("@ ").append(villager.blockPosition())
                        .append(" act=").append(brain.getActiveNonCoreActivity())
                        .append(" home=").append(brain.getMemoryInternal(MemoryModuleType.HOME).isPresent())
                        .append(" sawHome=").append(sawHome[index])
                        .append(" walk=").append(brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent())
                        .append(" sawWalk=").append(sawWalkTarget[index])
                        .append(" cant=").append(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).isPresent())
                        .append(" homePois=").append(homePois)
                        .append(" availableHomePois=").append(availableHomePois)
                        .append(" inVillage=").append(helper.getLevel().isVillage(villager.blockPosition()))
                        .append(" running=").append(brain.getRunningBehaviors())
                        .append(" done=").append(villager.getNavigation().isDone())
                        .append(" stuck=").append(villager.getNavigation().isStuck());
            }
            helper.fail("normal nighttime villagers did not all acquire HOME and sleep; sleeping="
                    + sleeping + "/" + villagers.length + state);
        });
    }

    private static BlockPos placeBed(GameTestHelper helper, BlockPos foot, Direction facing) {
        BlockPos head = foot.relative(facing);
        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        helper.getLevel().setBlock(foot, footState, Block.UPDATE_CLIENTS);
        helper.getLevel().setBlock(head, footState.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
        return head;
    }

    /**
     * Catches an exact-target path finishing while the entity is still short of the requested block. Vanilla clears
     * the finished path and walk target, so MCA must preserve unreachable evidence for the destination producer instead
     * of immediately republishing the same destination forever.
     */
    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void exactTargetPathThatFinishesShortRecordsCantReach(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 2, 10));
        BlockPos destination = start.east(6);
        prepareFlatArea(helper, start, 10, 2);
        helper.getLevel().getChunk(start);
        helper.getLevel().getChunk(destination);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Stuck reachable path probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);

        var brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, 0.5F, 0));

        Path directPath = villager.getNavigation().createPath(destination, 0);
        helper.assertTrue(directPath != null && directPath.canReach(),
                "fixture destination did not produce a reachable vanilla path");
        helper.assertTrue(destination.equals(directPath.getTarget()),
                "fixture path did not end at the exact walk target: " + directPath.getTarget());

        WanderOrTeleportToTargetTask sink = new WanderOrTeleportToTargetTask();
        long startedAt = helper.getLevel().getGameTime();
        helper.assertTrue(sink.tryStart(helper.getLevel(), villager, startedAt),
                "MCA movement sink did not start the reachable fixture path");

        Path activePath = villager.getNavigation().getPath();
        helper.assertTrue(activePath != null && destination.equals(activePath.getTarget()),
                "MCA movement sink did not retain the exact-target reachable path");
        while (!activePath.isDone()) {
            activePath.advance();
        }
        helper.assertTrue(villager.getNavigation().isDone(),
                "fixture path did not reach its terminal navigation state");
        helper.assertTrue(!villager.blockPosition().equals(destination),
                "fixture villager unexpectedly reached the destination block");

        sink.tickOrStop(helper.getLevel(), villager, startedAt + 1L);

        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).isPresent(),
                "an exact-target path that finished short lost the terminal unreachable signal");
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                "finished short path kept the movement sink alive while the villager was already motionless");
        helper.succeed();
    }

    @GameTest(batch = "mca_progressive_long_distance_path", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void usefulPartialLongDistancePathChainsImmediately(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos destination = start.east(Config.getInstance().getVillagerPathfindingDistance() + 32);
        prepareFlatPath(helper, start, destination);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Progressive long path probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        var brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(destination), 0.5F, 0));

        Path partial = villager.getNavigation().createPath(destination, 0);
        helper.assertTrue(partial != null && !partial.canReach(),
                "fixture did not produce the expected bounded partial path");
        helper.assertTrue(destination.equals(partial.getTarget()),
                "partial path lost the real logical destination");
        helper.assertTrue(partial.getEndNode() != null
                        && partial.getEndNode().asBlockPos().distSqr(destination) < start.distSqr(destination),
                "partial path did not make forward progress toward the destination");

        WanderOrTeleportToTargetTask sink = new WanderOrTeleportToTargetTask();
        long startedAt = helper.getLevel().getGameTime();
        helper.assertTrue(sink.tryStart(helper.getLevel(), villager, startedAt),
                "movement sink did not start the bounded partial path");
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).isEmpty(),
                "useful partial path was left marked unreachable");

        Path activePath = villager.getNavigation().getPath();
        helper.assertTrue(activePath != null && !activePath.canReach(),
                "movement sink did not retain the bounded partial path");
        BlockPos firstSegmentEnd = activePath.getEndNode().asBlockPos();
        villager.setPos(Vec3.atBottomCenterOf(firstSegmentEnd));
        while (!activePath.isDone()) {
            activePath.advance();
        }

        villager.getNavigation().tick();
        sink.tickOrStop(helper.getLevel(), villager, startedAt + 1L);

        Path chainedPath = villager.getNavigation().getPath();
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                "completed partial segment dropped the logical destination between path chunks");
        helper.assertTrue(chainedPath != null && chainedPath != activePath && !chainedPath.isDone(),
                "completed partial segment did not start its next path immediately");
        helper.assertTrue(destination.equals(chainedPath.getTarget()),
                "chained path stopped targeting the real long-distance destination");
        helper.assertTrue(chainedPath.getEndNode() != null
                        && chainedPath.getEndNode().asBlockPos().distSqr(destination)
                        < firstSegmentEnd.distSqr(destination),
                "chained path did not make additional progress from the completed checkpoint");
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.PATH)
                        .filter(path -> path == chainedPath)
                        .isPresent(),
                "movement sink did not synchronize PATH memory to the chained segment");
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).isEmpty(),
                "completed useful segment was converted into an unreachable failure");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_static_target_unreachable", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void sealedStaticTargetStopsChainingAndRecordsUnreachable(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(6, 2, 6));
        BlockPos destination = start.east(10);
        prepareFlatArea(helper, start, 16, 3);

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                BlockPos wall = destination.offset(x, 0, z);
                helper.getLevel().setBlock(wall, Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(wall.above(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(wall.above(2), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Sealed Static Target Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        var brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, 0.5F, 0));

        WanderOrTeleportToTargetTask sink = new WanderOrTeleportToTargetTask();
        long startedAt = helper.getLevel().getGameTime();
        helper.assertTrue(sink.tryStart(helper.getLevel(), villager, startedAt),
                "movement sink did not start the sealed-target partial path");
        Path firstPath = villager.getNavigation().getPath();
        helper.assertTrue(firstPath != null && !firstPath.canReach() && firstPath.getEndNode() != null,
                "sealed target did not produce a partial path");

        villager.setPos(Vec3.atBottomCenterOf(firstPath.getEndNode().asBlockPos()));
        while (!firstPath.isDone()) {
            firstPath.advance();
        }

        villager.getNavigation().tick();
        helper.assertTrue(villager.getNavigation().isDone(),
                "sealed target kept chaining after reaching its closest useful point");

        sink.tickOrStop(helper.getLevel(), villager, startedAt + 1L);
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).isPresent(),
                "sealed target did not record terminal unreachable evidence");

        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                BlockPos wall = destination.offset(x, 0, z);
                for (int y = 0; y < 3; y++) {
                    helper.getLevel().setBlock(wall.above(y), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        for (int i = 0; i < 25; i++) {
            villager.getNavigation().tick();
        }
        helper.assertTrue(villager.getNavigation().isDone(),
                "navigation retried a failed continuation instead of leaving retry ownership to the movement sink");

        sink.tickOrStop(helper.getLevel(), villager, startedAt + 22L);
        helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                "sealed static target remained owned after the bounded unreachable window");

        villager.discard();
        helper.succeed();
    }

}
