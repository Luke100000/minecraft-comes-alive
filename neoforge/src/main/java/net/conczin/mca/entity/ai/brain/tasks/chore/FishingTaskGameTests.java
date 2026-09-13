package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Chore;
import net.minecraft.core.BlockPos;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class FishingTaskGameTests {
    private FishingTaskGameTests() {
    }

    @GameTest(batch = "mca_fishing_bobber", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void bobberCastAtSelectedWaterStartsBobbing(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos water = villagerPos.east(2);
        prepareWater(helper, water);

        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        MCAFishingBobberEntity bobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);

        helper.assertTrue(!bobber.isRemoved(), "new fishing bobber was removed immediately");
        helper.succeedWhen(() -> helper.assertTrue(
                bobber.isBobbing(),
                "bobber never reached the selected water block; pos=" + bobber.position()
                        + ", motion=" + bobber.getDeltaMovement()
                        + ", removed=" + bobber.isRemoved()
                        + ", job=" + villager.getVillagerBrain().getCurrentJob()
                        + ", fluid=" + helper.getLevel().getFluidState(bobber.blockPosition())
        ));
    }

    private static VillagerEntityMCA spawnFisher(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().getChunk(pos);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Fishing Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.tickCount = 20;
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        villager.getInventory().addItem(rod.copy());
        villager.setItemInHand(villager.getDominantHand(), rod);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        villager.getVillagerBrain().assignJob(Chore.FISH, player);
        villager.setNoAi(true);
        return villager;
    }

    private static void prepareWater(GameTestHelper helper, BlockPos center) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos water = center.offset(x, 0, z);
                helper.getLevel().setBlock(water.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
                helper.getLevel().setBlock(water.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
