package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class AbstractChoreTaskGameTests {
    private AbstractChoreTaskGameTests() {
    }

    @GameTest(batch = "mca_chore_random_walk_target", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void randomWalkTargetRequiresStableFinalDestination(GameTestHelper helper) {
        BlockPos leafSupport = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos solidSupport = leafSupport.east(3);
        BlockPos leafTarget = leafSupport.above();
        BlockPos solidTarget = solidSupport.above();
        helper.getLevel().setBlock(leafSupport, Blocks.OAK_LEAVES.defaultBlockState(), 3);
        helper.getLevel().setBlock(solidSupport, Blocks.STONE.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(solidTarget))
                .withName("Chore Random Walk Target Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);

        helper.assertTrue(AbstractChoreTask.createStableWalkTarget(villager, Vec3.atBottomCenterOf(leafTarget)).isEmpty(),
                "chore wandering accepted an unstable final target above leaves");
        helper.assertTrue(AbstractChoreTask.createStableWalkTarget(villager, Vec3.atBottomCenterOf(solidTarget)).isPresent(),
                "chore wandering rejected an ordinary stable ground target");

        villager.discard();
        helper.succeed();
    }
}
