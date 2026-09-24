package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.navigation.LongDistancePathTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.function.Predicate;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ExtendedWalkTowardsTaskGameTests {
    private static final int TEST_TIMEOUT = 1200;
    private static final int TEST_AREA_RADIUS = 16;

    private ExtendedWalkTowardsTaskGameTests() {
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
        helper.assertTrue(walkTarget.getTarget() instanceof LongDistancePathTarget,
                "HOME beyond FOLLOW_RANGE did not use the long-distance path policy");
        LongDistancePathTarget target = (LongDistancePathTarget)walkTarget.getTarget();
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
        helper.assertTrue(!(walkTarget.getTarget() instanceof LongDistancePathTarget),
                "target inside the 48-block ordinary navigation range was classified as long-distance from sensing range");
        helper.assertTrue(walkTarget.getTarget().currentBlockPosition().equals(home),
                "ordinary HOME did not retain its real destination");
        helper.assertTrue(villager.getAttributeValue(Attributes.FOLLOW_RANGE) == followRangeBefore,
                "destination classification mutated FOLLOW_RANGE");

        villager.discard();
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void unreachableTimestampStillAllowsRetryBeforeTimeout(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillagerOnFlatArea(helper);
        BlockPos home = villager.blockPosition().east((int)villager.getAttributeValue(Attributes.FOLLOW_RANGE) + 16);
        setHome(villager, home);
        long firstFailure = helper.getLevel().getGameTime() - 40L;
        villager.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, firstFailure);

        OneShot<VillagerEntityMCA> task = createTask(ignored -> false);
        task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());

        var walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("retry before timeout did not republish the destination"));
        helper.assertTrue(walkTarget.getTarget() instanceof LongDistancePathTarget,
                "retry before timeout did not preserve long-distance path intent");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                        .filter(timestamp -> timestamp == firstFailure)
                        .isPresent(),
                "destination producer rewrote the original unreachable timestamp");

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
                new WalkTarget(new LongDistancePathTarget(oldHome), 0.5F, 0)
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

    private static VillagerEntityMCA spawnVillagerOnFlatArea(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(2, 1, 2));
        prepareFlatArea(helper, feet, TEST_AREA_RADIUS, 2);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .withName("Long Walk Range Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        return villager;
    }

    private static void setHome(VillagerEntityMCA villager, BlockPos home) {
        villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(villager.level().dimension(), home));
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
    }
}
