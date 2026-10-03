package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.navigation.PersistentPathTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.function.Predicate;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ExtendedWalkTowardsTaskGameTests {
    private static final int TEST_TIMEOUT = 1200;
    private static final int TEST_AREA_RADIUS = 16;

    private ExtendedWalkTowardsTaskGameTests() {
    }

    @GameTest(batch = "mca_persistent_poi_target", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void vanillaPoiProducerKeepsRealDestinationForMcaVillager(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillagerOnFlatArea(helper);
        BlockPos target = villager.blockPosition().east(180);

        assertFactoryKeepsRealDestination(helper, villager, MemoryModuleType.HOME, target, 1, 150, "HOME");
        assertFactoryKeepsRealDestination(helper, villager, MemoryModuleType.JOB_SITE, target, 9, 100, "JOB_SITE");
        assertFactoryKeepsRealDestination(helper, villager, MemoryModuleType.MEETING_POINT, target, 6, 100, "MEETING_POINT");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_persistent_poi_target", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void vanillaVillagerStillUsesVanillaIntermediatePoiTarget(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(2, 1, 2));
        prepareFlatArea(helper, feet, TEST_AREA_RADIUS, 2);
        Villager villager = EntityTypes.VILLAGER.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
        if (villager == null) {
            throw new IllegalStateException("failed to create vanilla villager");
        }
        villager.snapTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
        villager.setNoAi(true);
        helper.getLevel().addFreshEntity(villager);

        BlockPos jobSite = feet.east(180);
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(helper.getLevel().dimension(), jobSite));
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);

        OneShot<Villager> task = SetWalkTargetFromBlockMemory.create(
                MemoryModuleType.JOB_SITE, 0.5F, 9, 100, TEST_TIMEOUT);
        task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());

        WalkTarget walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("vanilla JOB_SITE producer did not publish a walk target"));
        helper.assertTrue(!walkTarget.getTarget().currentBlockPosition().equals(jobSite),
                "vanilla villager unexpectedly received MCA's persistent POI target policy");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_persistent_poi_target", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void vanillaPoiProducerDoesNotReplaceExistingMcaWalkTarget(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillagerOnFlatArea(helper);
        BlockPos existingTarget = villager.blockPosition().north(4);
        BlockPos jobSite = villager.blockPosition().east(180);
        WalkTarget existingWalkTarget = new WalkTarget(existingTarget, 0.5F, 1);
        villager.getBrain().setMemory(MemoryModuleType.JOB_SITE,
                GlobalPos.of(helper.getLevel().dimension(), jobSite));
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, existingWalkTarget);

        OneShot<Villager> task = SetWalkTargetFromBlockMemory.create(
                MemoryModuleType.JOB_SITE, 0.5F, 9, 100, TEST_TIMEOUT);
        boolean started = task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());

        helper.assertTrue(!started,
                "MCA wrapper started even though vanilla requires WALK_TARGET to be absent");
        WalkTarget retained = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("MCA wrapper erased an existing walk target"));
        helper.assertTrue(retained == existingWalkTarget,
                "MCA wrapper replaced an existing walk target instead of preserving vanilla absence semantics");

        villager.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void targetBeyondFollowRangeKeepsRealDestination(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillagerOnFlatArea(helper);
        double followRange = villager.getAttributeValue(Attributes.FOLLOW_RANGE);
        int targetDistance = (int)Math.floor(followRange) + 32;
        BlockPos home = villager.blockPosition().east(targetDistance);
        setHome(villager, home);

        OneShot<VillagerEntityMCA> task = createTask(ignored -> false);
        task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());

        var walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("long-distance HOME did not publish a walk target"));
        helper.assertTrue(walkTarget.getTarget() instanceof PersistentPathTarget,
                "HOME beyond FOLLOW_RANGE did not use the long-distance path policy");
        PersistentPathTarget target = (PersistentPathTarget)walkTarget.getTarget();
        helper.assertTrue(target.currentBlockPosition().equals(home),
                "long-distance HOME replaced the real destination with an intermediate point");

        villager.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void diagonalTargetWithinFollowRangeRemainsDirect(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillagerOnFlatArea(helper);
        double followRange = villager.getAttributeValue(Attributes.FOLLOW_RANGE);
        BlockPos home = villager.blockPosition().offset(30, 0, 30);
        helper.assertTrue(villager.blockPosition().distSqr(home) < followRange * followRange,
                "fixture diagonal target must fit within FOLLOW_RANGE geometrically");
        helper.assertTrue(villager.blockPosition().distManhattan(home) > followRange,
                "fixture diagonal target must exceed FOLLOW_RANGE by Manhattan distance");
        setHome(villager, home);

        OneShot<VillagerEntityMCA> task = createTask(ignored -> false);
        task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());

        var walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("diagonal HOME did not publish a walk target"));
        helper.assertTrue(walkTarget.getTarget().currentBlockPosition().equals(home),
                "geometrically reachable diagonal HOME was unnecessarily segmented");

        villager.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void targetInsideOrdinaryNavigationRangeRemainsDirectWhenFollowRangeIsLowered(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillagerOnFlatArea(helper);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        double followRangeBefore = villager.getAttributeValue(Attributes.FOLLOW_RANGE);
        BlockPos home = villager.blockPosition().east(30);
        setHome(villager, home);

        OneShot<VillagerEntityMCA> task = createTask(ignored -> false);
        task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());

        var walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("ordinary HOME did not publish a walk target"));
        helper.assertTrue(walkTarget.getTarget() instanceof PersistentPathTarget,
                "persistent HOME inside ordinary navigation range lost its recovery intent");
        helper.assertTrue(walkTarget.getTarget().currentBlockPosition().equals(home),
                "ordinary HOME did not retain its real destination");
        helper.assertTrue(villager.getAttributeValue(Attributes.FOLLOW_RANGE) == followRangeBefore,
                "destination classification mutated FOLLOW_RANGE");

        villager.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void matchingDirectWalkTargetSurvivesRepeatedProducerTick(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillagerOnFlatArea(helper);
        BlockPos home = villager.blockPosition().east(6);
        setHome(villager, home);

        OneShot<VillagerEntityMCA> task = createTask(ignored -> false);
        long gameTime = helper.getLevel().getGameTime();
        task.tryStart(helper.getLevel(), villager, gameTime);
        WalkTarget first = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("initial nearby HOME did not publish a walk target"));
        helper.assertTrue(first.getTarget() instanceof PersistentPathTarget,
                "nearby HOME did not publish persistent navigation intent");
        helper.assertTrue(first.getTarget().currentBlockPosition().equals(home),
                "initial nearby HOME walk target did not match the logical destination");

        task.tryStart(helper.getLevel(), villager, gameTime + 1L);

        WalkTarget second = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("matching nearby walk target was erased on the next producer tick"));
        helper.assertTrue(second.getTarget().currentBlockPosition().equals(home),
                "repeated producer tick replaced the matching nearby destination");

        villager.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void unreachableTimestampStillAllowsRetryBeforeTimeout(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillagerOnFlatArea(helper);
        BlockPos home = villager.blockPosition().east((int)villager.getAttributeValue(Attributes.FOLLOW_RANGE) + 16);
        setHome(villager, home);
        long firstFailure = helper.getLevel().getGameTime() - 40L;
        WalkTargetFailureMemory.record(villager, home, firstFailure);

        OneShot<VillagerEntityMCA> task = createTask(ignored -> false);
        task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());

        var walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("retry before timeout did not republish the destination"));
        helper.assertTrue(walkTarget.getTarget() instanceof PersistentPathTarget,
                "retry before timeout did not preserve long-distance path intent");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        .filter(timestamp -> timestamp == firstFailure)
                        .isPresent(),
                "destination producer rewrote the original unreachable timestamp");

        villager.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void unreachableTimestampDefersRetryUntilCanonicalWindow(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillagerOnFlatArea(helper);
        BlockPos home = villager.blockPosition().east((int)villager.getAttributeValue(Attributes.FOLLOW_RANGE) + 16);
        setHome(villager, home);
        long firstFailure = helper.getLevel().getGameTime() - 19L;
        WalkTargetFailureMemory.record(villager, home, firstFailure);

        OneShot<VillagerEntityMCA> task = createTask(ignored -> false);
        task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());

        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                "destination producer retried before the canonical 20-tick failure window elapsed");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        .filter(timestamp -> timestamp == firstFailure)
                        .isPresent(),
                "retry wait rewrote the original failure-since timestamp");

        villager.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void changedDestinationClearsStaleLongDistanceTarget(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillagerOnFlatArea(helper);
        int distance = (int)Math.floor(villager.getAttributeValue(Attributes.FOLLOW_RANGE)) + 24;
        BlockPos oldHome = villager.blockPosition().east(distance);
        BlockPos newHome = villager.blockPosition().south(distance);
        setHome(villager, newHome);
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(oldHome), 0.5F, 0)
        );
        villager.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                helper.getLevel().getGameTime() - 10L);

        OneShot<VillagerEntityMCA> task = createTask(ignored -> false);
        task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());

        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                "changed destination left the stale long-distance walk target active");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).isEmpty(),
                "changed destination retained failure state from the stale long-distance route");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_persistent_final_handoff", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void persistentTransitHandsOffToSemanticFinalTargetInsideOrdinaryRange(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillagerOnFlatArea(helper);
        BlockPos start = villager.blockPosition();
        BlockPos home = start.east(80);
        BlockPos finalApproach = home.west(2);
        setHome(villager, home);

        OneShot<VillagerEntityMCA> task = ExtendedWalkTowardsTask.createWithFinalTarget(
                MemoryModuleType.HOME,
                0.5F,
                1,
                TEST_TIMEOUT,
                ignored -> false,
                ignored -> { },
                (world, entity, destination) -> Optional.of(new BlockPosTracker(finalApproach))
        );
        long gameTime = helper.getLevel().getGameTime();
        task.tryStart(helper.getLevel(), villager, gameTime);
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        .filter(target -> target.getTarget() instanceof PersistentPathTarget)
                        .isPresent(),
                "far persistent destination did not start with the transit target");

        villager.setPos(Vec3.atBottomCenterOf(home.west(10)));
        task.tryStart(helper.getLevel(), villager, gameTime + 1L);
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                "persistent transit target was retained after entering final-target range");

        task.tryStart(helper.getLevel(), villager, gameTime + 2L);
        WalkTarget finalTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("semantic final target was not published after transit handoff"));
        helper.assertTrue(!(finalTarget.getTarget() instanceof PersistentPathTarget),
                "semantic final target was replaced by persistent transit state");
        helper.assertTrue(finalTarget.getTarget().currentBlockPosition().equals(finalApproach),
                "semantic final target did not retain its resolved approach position");

        villager.discard();
        helper.succeed();
    }

    private static OneShot<VillagerEntityMCA> createTask(Predicate<VillagerEntityMCA> canGiveUp) {
        return ExtendedWalkTowardsTask.create(
                MemoryModuleType.HOME,
                0.5F,
                1,
                TEST_TIMEOUT,
                canGiveUp,
                ignored -> { }
        );
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void repeatedShortHomeTripsDoNotInheritFailureAge(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillagerOnFlatArea(helper);
        BlockPos start = villager.blockPosition();
        BlockPos home = start.east(2);
        setHome(villager, home);
        OneShot<VillagerEntityMCA> task = createTask(ignored -> true);
        long started = helper.getLevel().getGameTime();
        try {
            for (int trip = 0; trip < 4; trip++) {
                long time = started + trip * 4L;
                villager.setPos(Vec3.atBottomCenterOf(start));
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                task.tryStart(helper.getLevel(), villager, time);
                helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                        "completed short trip delayed the next departure to the same HOME");
                helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE),
                        "successful short trip inherited a stale failure episode");
                // Report arrival while WALK_TARGET still exists, as during a Brain tick.
                villager.setPos(Vec3.atBottomCenterOf(home));
                task.tryStart(helper.getLevel(), villager, time + 1L);
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                // Also exercise arrival after the sink has erased WALK_TARGET.
                task.tryStart(helper.getLevel(), villager, time + 2L);
                helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.HOME).isPresent(),
                        "successful short trip released HOME");
            }
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    private static void assertFactoryKeepsRealDestination(
            GameTestHelper helper,
            VillagerEntityMCA villager,
            MemoryModuleType<GlobalPos> memoryType,
            BlockPos target,
            int closeEnoughDistance,
            int tooFarDistance,
            String label
    ) {
        villager.getBrain().setMemory(memoryType, GlobalPos.of(helper.getLevel().dimension(), target));
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);

        OneShot<Villager> task = SetWalkTargetFromBlockMemory.create(
                memoryType, 0.5F, closeEnoughDistance, tooFarDistance, TEST_TIMEOUT);
        task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());

        WalkTarget walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError(label + " did not publish a walk target"));
        helper.assertTrue(walkTarget.getTarget() instanceof PersistentPathTarget,
                label + " did not use MCA's persistent long-distance target policy");
        helper.assertTrue(walkTarget.getTarget().currentBlockPosition().equals(target),
                label + " replaced the real POI with an intermediate destination");

        villager.getBrain().eraseMemory(memoryType);
    }

    private static VillagerEntityMCA spawnVillagerOnFlatArea(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(2, 1, 2));
        prepareFlatArea(helper, feet, TEST_AREA_RADIUS, 2);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .withName("Long Walk Range Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setNoAi(true);
        return villager;
    }

    private static void setHome(VillagerEntityMCA villager, BlockPos home) {
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(villager.level().dimension(), home));
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }
}
