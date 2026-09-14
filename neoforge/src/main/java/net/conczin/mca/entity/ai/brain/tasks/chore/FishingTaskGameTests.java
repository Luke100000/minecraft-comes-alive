package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.TaskUtils;
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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class FishingTaskGameTests {
    private FishingTaskGameTests() {
    }

    @GameTest(batch = "mca_fishing_bobber", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void bobberCastAtSelectedWaterStartsBobbing(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos water = villagerPos.east(2);
        prepareWater(helper, water);

        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        MCAFishingBobberEntity bobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);

        helper.assertTrue(!bobber.isRemoved(), "new fishing bobber was removed immediately");
        for (int i = 0; i < 40 && !bobber.isBobbing() && !bobber.isRemoved(); i++) {
            bobber.tick();
        }
        helper.assertTrue(
                bobber.isBobbing(),
                "bobber never reached the selected water block; pos=" + bobber.position()
                        + ", motion=" + bobber.getDeltaMovement()
                        + ", removed=" + bobber.isRemoved()
                        + ", job=" + villager.getVillagerBrain().getCurrentJob()
                        + ", fluid=" + helper.getLevel().getFluidState(bobber.blockPosition())
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_timeout", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fishingDoesNotUseInheritedTimeout(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnFisher(helper, helper.absolutePos(new BlockPos(3, 2, 3)));
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL));

        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());

        helper.assertTrue(!task.isTimedOut(401L), "fishing still uses the inherited 400-tick chore timeout");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_single_bobber", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void repeatedFishingTicksKeepOneBobber(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
        prepareWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL));
        long time = helper.getLevel().getGameTime();

        task.start(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time);
        assertReadyToCast(helper, villager);
        task.tick(helper.getLevel(), villager, time + 1);
        task.tick(helper.getLevel(), villager, time + 2);

        helper.assertTrue(activeBobberCount(helper, villager) == 1L,
                "repeated fishing ticks did not keep exactly one active bobber; held="
                        + villager.getItemInHand(villager.getDominantHand())
                        + ", job=" + villager.getVillagerBrain().getCurrentJob());
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_stop", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void stoppingFishingClearsBobberAndRod(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
        prepareWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL));
        long time = helper.getLevel().getGameTime();

        task.start(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time + 1);
        task.tick(helper.getLevel(), villager, time + 2);
        helper.assertTrue(activeBobberCount(helper, villager) == 1L, "fishing did not create a bobber before stop");

        task.stop(helper.getLevel(), villager, time + 3);

        helper.assertTrue(activeBobberCount(helper, villager) == 0L, "stopped fishing left an active bobber");
        helper.assertTrue(villager.getItemInHand(villager.getDominantHand()).isEmpty(), "stopped fishing left the rod equipped");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_rod_loss", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void losingLastRodDiscardsBobber(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
        prepareWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL));
        long time = helper.getLevel().getGameTime();

        task.start(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time + 1);
        task.tick(helper.getLevel(), villager, time + 2);
        helper.assertTrue(activeBobberCount(helper, villager) == 1L, "fishing did not create a bobber before rod loss");

        villager.getInventory().clearContent();
        villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
        task.tick(helper.getLevel(), villager, time + 3);

        helper.assertTrue(activeBobberCount(helper, villager) == 0L, "losing the last rod left an active bobber");
        helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.NONE, "losing the last rod did not abandon fishing");
        helper.succeed();
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

    private static long activeBobberCount(GameTestHelper helper, VillagerEntityMCA villager) {
        return helper.getLevel()
                .getEntitiesOfClass(MCAFishingBobberEntity.class, villager.getBoundingBox().inflate(32.0D))
                .stream()
                .filter(entity -> !entity.isRemoved())
                .count();
    }

    private static void assertReadyToCast(GameTestHelper helper, VillagerEntityMCA villager) {
        List<BlockPos> water = TaskUtils.getNearbyBlocks(
                villager.blockPosition(),
                helper.getLevel(),
                state -> state.is(Blocks.WATER),
                12,
                3
        );
        BlockPos nearest = water.stream()
                .min(Comparator.comparingDouble(pos -> villager.distanceToSqr(pos.getX(), pos.getY(), pos.getZ())))
                .orElse(null);
        helper.assertTrue(nearest != null, "fishing fixture had no discoverable water");
        helper.assertTrue(
                villager.distanceToSqr(nearest.getX(), nearest.getY(), nearest.getZ()) < 5.0D,
                "nearest fishing water was too far away: " + nearest + " from " + villager.position()
        );
        helper.assertTrue(
                villager.getItemInHand(villager.getDominantHand()).getItem() instanceof net.minecraft.world.item.FishingRodItem,
                "fishing fixture lost its held rod before casting"
        );
        helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.FISH, "fishing fixture lost the FISH chore before casting");
    }

    private static final class TestFishingTask extends FishingTask {
        private final Player assigningPlayer;

        private TestFishingTask(Player assigningPlayer) {
            this.assigningPlayer = assigningPlayer;
        }

        @Override
        Optional<Player> getAssigningPlayer() {
            return Optional.of(assigningPlayer);
        }

        @Override
        void abandonJobWithMessage(String message) {
            villager.getVillagerBrain().abandonJob();
        }

        private boolean isTimedOut(long time) {
            return timedOut(time);
        }
    }
}
