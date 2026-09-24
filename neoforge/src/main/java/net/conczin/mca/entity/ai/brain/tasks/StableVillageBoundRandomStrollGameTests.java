package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class StableVillageBoundRandomStrollGameTests {
    private StableVillageBoundRandomStrollGameTests() {
    }

    @GameTest(batch = "mca_stable_random_stroll_target", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void unstableMovedUpTargetIsDiscarded(GameTestHelper helper) {
        BlockPos leafSupport = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos solidSupport = leafSupport.east(3);
        BlockPos leafTarget = leafSupport.above();
        BlockPos solidTarget = solidSupport.above();
        helper.getLevel().setBlock(leafSupport, Blocks.OAK_LEAVES.defaultBlockState(), 3);
        helper.getLevel().setBlock(solidSupport, Blocks.STONE.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(solidTarget))
                .withName("Stable Stroll Target Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);

        helper.assertTrue(!villager.getNavigation().isStableDestination(leafTarget),
                "fixture target above leaves unexpectedly counted as stable");
        helper.assertTrue(villager.getNavigation().isStableDestination(solidTarget),
                "fixture target above stone must be stable");

        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(leafTarget), 0.5F, 0));
        StableVillageBoundRandomStroll.filterUnstableWalkTarget(villager);
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                "random stroll retained an unstable final target above leaves");

        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(solidTarget), 0.5F, 0));
        StableVillageBoundRandomStroll.filterUnstableWalkTarget(villager);
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                "random stroll discarded an ordinary stable ground target");

        villager.discard();
        helper.succeed();
    }
}
