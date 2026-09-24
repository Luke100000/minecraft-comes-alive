package net.conczin.mca.entity.ai;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ResidencyGoHomeGameTests {
    private ResidencyGoHomeGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void distantMoveTowardsUsesCentralExtendedNavigation(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
        prepareFlatArea(helper, start, 32, 2);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Go Home Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        int targetDistance = 24;
        BlockPos home = start.east(targetDistance);
        villager.getBrain().setMemory(
                MemoryModuleType.HOME,
                GlobalPos.of(villager.level().dimension(), home)
        );
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);

        villager.moveTowards(home);

        var walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("Go Home did not publish a WALK_TARGET"));
        helper.assertTrue(walkTarget.getTarget().currentBlockPosition().equals(home),
                "Go Home changed the real HOME destination");
        helper.assertTrue(walkTarget.getCloseEnoughDist() == 1,
                "Go Home changed its existing close-enough distance");

        Path path = villager.getNavigation().createPath(home, 0);
        helper.assertTrue(path != null && path.canReach(),
                "central MCA navigation did not extend Go Home beyond FOLLOW_RANGE");
        helper.assertTrue(path.getTarget().equals(home),
                "Go Home path stopped targeting the real HOME position");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_go_home_long_running", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void longDistanceGoHomeSurvivesMovementSinkDuration(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        prepareFlatArea(helper, start, 96, 2);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Long Go Home Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        BlockPos home = start.east(80);
        villager.getBrain().setMemory(
                MemoryModuleType.HOME,
                GlobalPos.of(villager.level().dimension(), home)
        );
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        villager.moveTowards(home);

        WanderOrTeleportToTargetTask sink = new WanderOrTeleportToTargetTask();
        long startedAt = helper.getLevel().getGameTime();
        helper.assertTrue(sink.tryStart(helper.getLevel(), villager, startedAt),
                "Go Home movement sink did not start");
        helper.assertTrue(villager.getNavigation().getPath() != null,
                "Go Home movement sink did not install a path");

        sink.tickOrStop(helper.getLevel(), villager, startedAt + 251L);

        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                "Go Home lost its one-shot WALK_TARGET only because MoveToTargetSink timed out");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        .orElseThrow()
                        .getTarget()
                        .currentBlockPosition()
                        .equals(home),
                "Go Home target changed after the vanilla movement-sink duration");

        villager.discard();
        helper.succeed();
    }
}
