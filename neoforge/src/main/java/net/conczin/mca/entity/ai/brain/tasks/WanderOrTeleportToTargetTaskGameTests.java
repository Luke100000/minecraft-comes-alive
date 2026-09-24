package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.navigation.LongDistancePathTarget;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;
import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class WanderOrTeleportToTargetTaskGameTests {
    private WanderOrTeleportToTargetTaskGameTests() {
    }

    @GameTest(batch = "mca_walk_target_timeout_scope", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void farStaticTargetsSuppressVanillaMovementTimeout(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos target = start.east(6);
        BlockPos farStaticTarget = start.east(60);
        prepareFlatArea(helper, start, 10, 2);
        prepareFlatPath(helper, start, farStaticTarget);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Walk Timeout Scope Probe")
                .spawn(MobSpawnType.STRUCTURE);
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
                "far static movement sink did not start");
        helper.assertTrue(!farStaticSink.timedOut(startedAt + 251L),
                "far static WALK_TARGET was still limited by vanilla's movement timeout");
        farStaticSink.doStop(helper.getLevel(), villager, startedAt + 251L);

        brain.eraseMemory(MemoryModuleType.PATH);
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new LongDistancePathTarget(farStaticTarget), 0.5F, 0));

        WanderOrTeleportToTargetTask longDistanceSink = new WanderOrTeleportToTargetTask();
        helper.assertTrue(longDistanceSink.tryStart(helper.getLevel(), villager, startedAt),
                "long-distance movement sink did not start");
        helper.assertTrue(!longDistanceSink.timedOut(startedAt + 251L),
                "long-distance WALK_TARGET lost its extended movement lifetime");
        longDistanceSink.doStop(helper.getLevel(), villager, startedAt + 251L);

        BlockPos movingTargetPos = start.east(60).north(4);
        VillagerEntityMCA movingTarget = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(movingTargetPos))
                .withName("Moving Timeout Target")
                .spawn(MobSpawnType.STRUCTURE);
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

    @GameTest(batch = "mca_walk_target_timeout_scope", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 400)
    public static void nearbyExtendedDetourKeepsVanillaMovementTimeout(GameTestHelper helper) {
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
                .spawn(MobSpawnType.STRUCTURE);
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
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.35F, 0));

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

        // This test owns the sink lifecycle, not physical travel. Freeze entity AI so
        // a completed route cannot turn the timeout assertion into a path-end test.
        villager.setNoAi(true);

        helper.onEachTick(() -> {
            long gameTime = helper.getLevel().getGameTime();
            sink.tickOrStop(helper.getLevel(), villager, gameTime);
            if (gameTime - startedAt < 260L) {
                return;
            }

            helper.assertTrue(!villager.blockPosition().equals(target),
                    "fixture reached the target before exercising the vanilla timeout window");
            helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                    "nearby extended-horizon fallback incorrectly suppressed the vanilla movement timeout");
            villager.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_stuck_retry_throttle", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 120)
    public static void failedRetryAfterVanillaStuckCooldownUsesMcaThrottle(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(10, 2, 10));
        BlockPos destination = start.east(6);
        prepareFlatArea(helper, start, 10, 2);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Stuck Retry Throttle Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.setSpeed(0.5F);

        var brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, 0.5F, 0));

        WanderOrTeleportToTargetTask sink = new WanderOrTeleportToTargetTask();
        long startedAt = helper.getLevel().getGameTime();
        helper.assertTrue(sink.tryStart(helper.getLevel(), villager, startedAt),
                "movement sink did not start the stuck-path fixture");

        for (int i = 0; i < 205 && !villager.getNavigation().isStuck(); i++) {
            villager.getNavigation().tick();
        }
        helper.assertTrue(villager.getNavigation().isStuck(),
                "fixture navigation did not enter vanilla's sticky stuck state");
        sink.tickOrStop(helper.getLevel(), villager, startedAt + 1L);

        villager.setPos(villager.getX(), villager.getY() + 2.0D, villager.getZ());
        villager.setNoGravity(true);
        villager.setOnGround(false);

        long[] firstFailureAt = {-1L};
        long[] secondFailureAt = {-1L};
        helper.onEachTick(() -> {
            if (brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty()) {
                brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, 0.5F, 0));
            }

            long gameTime = helper.getLevel().getGameTime();
            boolean started = sink.tryStart(helper.getLevel(), villager, gameTime);
            helper.assertTrue(!started, "airborne retry unexpectedly created a path");
            boolean targetWasErased = brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty();

            if (firstFailureAt[0] < 0L) {
                if (targetWasErased) {
                    firstFailureAt[0] = gameTime;
                    helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                                    .filter(since -> since == gameTime)
                                    .isPresent(),
                            "failed retry did not establish the canonical unreachable timestamp");
                }
                return;
            }

            long elapsed = gameTime - (secondFailureAt[0] >= 0L ? secondFailureAt[0] : firstFailureAt[0]);
            if (elapsed < 20L) {
                helper.assertTrue(!targetWasErased,
                        "failed path retried before the canonical 20-tick unreachable window elapsed");
                helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                                .filter(since -> since == firstFailureAt[0])
                                .isPresent(),
                        "retry throttle rewrote the canonical failure-since timestamp");
                return;
            }

            helper.assertTrue(targetWasErased,
                    "failed path did not retry when the canonical 20-tick unreachable window elapsed");
            helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                            .filter(since -> since == firstFailureAt[0])
                            .isPresent(),
                    "failed retry rewrote the canonical failure-since timestamp");
            if (secondFailureAt[0] < 0L) {
                secondFailureAt[0] = gameTime;
                return;
            }

            villager.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_brain_driven_long_distance_walk", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 2_400)
    public static void brainDrivenLongDistanceWalkArrivesAfterMultipleSegmentsAndTimeoutWindow(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        int pathHorizon = Math.max(Config.getInstance().getVillagerPathfindingDistance(), 48);
        BlockPos destination = start.east(pathHorizon * 2 + 64);
        ChunkPos startChunk = new ChunkPos(start);
        ChunkPos targetChunk = new ChunkPos(destination);
        for (int chunkX = startChunk.x; chunkX <= targetChunk.x; chunkX++) {
            helper.getLevel().setChunkForced(chunkX, startChunk.z, true);
        }
        prepareFlatPath(helper, start, destination);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Brain Driven Long Distance Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        var brain = villager.getBrain();
        brain.stopAll(helper.getLevel(), villager);
        brain.removeAllBehaviors();
        brain.addActivity(Activity.CORE, 1, ImmutableList.of(new WanderOrTeleportToTargetTask()));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.setActiveActivityIfPossible(Activity.IDLE);
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new LongDistancePathTarget(destination), 1.0F, 0));

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
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(true);

        var brain = villager.getBrain();
        brain.setSchedule(new ScheduleBuilder(new Schedule())
                .changeActivityAt(0, Activity.REST)
                .build());
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
                .spawn(MobSpawnType.STRUCTURE);
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
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        var brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.PATH);
        brain.eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(destination, 0.5F, 0));

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
                .spawn(MobSpawnType.STRUCTURE);
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
