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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    @GameTest(
            batch = "mca_fishing_bite",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 800
    )
    public static void bobberEventuallyEntersRealBiteWindow(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        BlockPos water = villagerPos.east(2);
        prepareBiteWater(helper, water);

        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        MCAFishingBobberEntity bobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);

        helper.succeedWhen(() -> helper.assertTrue(
                bobber.isBobbing() && bobber.isBiting(),
                "bobber never reached the vanilla-shaped bite window"
        ));
    }

    @GameTest(
            batch = "mca_fishing_reel",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 900
    )
    public static void biteReelsOneProtectedItemIntoInventory(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true);
        Player thief = helper.makeMockPlayer(GameType.SURVIVAL);
        AtomicBoolean sawProtectedReel = new AtomicBoolean();
        AtomicBoolean delivered = new AtomicBoolean();
        AtomicReference<ItemEntity> reelItem = new AtomicReference<>();
        int startingLoot = countCaughtItems(villager);
        int startingRodDamage = villager.getItemInHand(villager.getDominantHand()).getDamageValue();

        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.onEachTick(() -> {
            if (delivered.get()) {
                return;
            }

            MCAFishingBobberEntity bobberBeforeTick = activeBobber(helper, villager);
            int rollsBeforeTick = task.getBiteRollCount();
            List<Integer> nearbyItemIds = bobberBeforeTick == null
                    ? List.of()
                    : helper.getLevel().getEntitiesOfClass(ItemEntity.class, bobberBeforeTick.getBoundingBox().inflate(1.5D))
                    .stream()
                    .map(ItemEntity::getId)
                    .toList();

            task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());

            if (task.getBiteRollCount() > rollsBeforeTick && bobberBeforeTick != null) {
                helper.getLevel().getEntitiesOfClass(ItemEntity.class, bobberBeforeTick.getBoundingBox().inflate(1.5D))
                        .stream()
                        .filter(item -> !nearbyItemIds.contains(item.getId()))
                        .findFirst()
                        .ifPresent(item -> reelItem.compareAndSet(null, item));
            }

            ItemEntity item = reelItem.get();
            if (item != null && !item.isRemoved()) {
                sawProtectedReel.set(true);
                helper.assertTrue(item.hasPickUpDelay(), "reel item became naturally pickup-eligible in flight");
                item.playerTouch(thief);
                helper.assertTrue(!item.isRemoved(), "another player stole the protected reel item");
            }

            if (item != null && item.isRemoved() && countCaughtItems(villager) > startingLoot) {
                delivered.set(true);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(sawProtectedReel.get(), "no real reel ItemEntity was observed");
            helper.assertTrue(delivered.get(), "protected reel item was not delivered into MCA inventory");
            helper.assertTrue(thief.getInventory().isEmpty(), "protected reel item entered another player's inventory");
            helper.assertTrue(countCaughtItems(villager) > startingLoot, "a real bite did not deliver a catch");
            helper.assertTrue(task.getBiteRollCount() == 1, "successful bite rolled catch chance more than once");
            helper.assertTrue(
                    villager.getItemInHand(villager.getDominantHand()).getDamageValue() == startingRodDamage + 1,
                    "one delivered bite did not damage the fishing rod exactly once"
            );
        });
    }

    @GameTest(
            batch = "mca_fishing_miss",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 900
    )
    public static void failedOriginCatchRollLetsBiteExpireWithoutLoot(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), false);
        AtomicBoolean sawBite = new AtomicBoolean();
        AtomicBoolean biteExpired = new AtomicBoolean();
        AtomicBoolean spawnedReel = new AtomicBoolean();
        int startingLoot = countCaughtItems(villager);
        int startingRodDamage = villager.getItemInHand(villager.getDominantHand()).getDamageValue();

        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.onEachTick(() -> {
            if (biteExpired.get()) {
                return;
            }

            MCAFishingBobberEntity bobberBeforeTick = activeBobber(helper, villager);
            int nearbyItemsBefore = bobberBeforeTick == null
                    ? 0
                    : helper.getLevel().getEntitiesOfClass(ItemEntity.class, bobberBeforeTick.getBoundingBox().inflate(1.5D)).size();
            int rollsBeforeTick = task.getBiteRollCount();
            task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());

            if (task.getBiteRollCount() > rollsBeforeTick && bobberBeforeTick != null) {
                int nearbyItemsAfter = helper.getLevel()
                        .getEntitiesOfClass(ItemEntity.class, bobberBeforeTick.getBoundingBox().inflate(1.5D))
                        .size();
                if (nearbyItemsAfter > nearbyItemsBefore) {
                    spawnedReel.set(true);
                }
            }

            MCAFishingBobberEntity active = activeBobber(helper, villager);
            if (active != null && active.isBiting()) {
                sawBite.set(true);
            } else if (sawBite.get() && task.getBiteRollCount() > 0) {
                biteExpired.set(true);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(sawBite.get(), "forced miss never reached a real bite");
            helper.assertTrue(biteExpired.get(), "failed bite did not expire back to waiting");
            helper.assertTrue(task.getBiteRollCount() == 1, "failed bite rerolled catch chance before the bite expired");
            helper.assertTrue(countCaughtItems(villager) == startingLoot, "failed origin catch roll produced loot");
            helper.assertTrue(!spawnedReel.get(), "failed origin catch roll spawned a reel item");
            helper.assertTrue(
                    villager.getItemInHand(villager.getDominantHand()).getDamageValue() == startingRodDamage,
                    "failed origin catch roll damaged the fishing rod"
            );
        });
    }

    @GameTest(
            batch = "mca_fishing_reel_remainder",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 900
    )
    public static void fullInventoryReleasesOnlyTheRealReelRemainder(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            villager.getInventory().setItem(slot, new ItemStack(Blocks.STONE, 64));
        }

        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true);
        AtomicBoolean reelFinished = new AtomicBoolean();
        AtomicReference<ItemEntity> reelItem = new AtomicReference<>();
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.onEachTick(() -> {
            if (reelFinished.get()) {
                return;
            }

            MCAFishingBobberEntity bobberBeforeTick = activeBobber(helper, villager);
            int rollsBeforeTick = task.getBiteRollCount();
            List<Integer> nearbyItemIds = bobberBeforeTick == null
                    ? List.of()
                    : helper.getLevel().getEntitiesOfClass(ItemEntity.class, bobberBeforeTick.getBoundingBox().inflate(1.5D))
                    .stream()
                    .map(ItemEntity::getId)
                    .toList();

            task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());

            if (task.getBiteRollCount() > rollsBeforeTick && bobberBeforeTick != null) {
                helper.getLevel().getEntitiesOfClass(ItemEntity.class, bobberBeforeTick.getBoundingBox().inflate(1.5D))
                        .stream()
                        .filter(item -> !nearbyItemIds.contains(item.getId()))
                        .findFirst()
                        .ifPresent(item -> reelItem.compareAndSet(null, item));
            }

            ItemEntity item = reelItem.get();
            if (item != null && !item.isRemoved() && !item.hasPickUpDelay()) {
                reelFinished.set(true);
            }
        });

        helper.succeedWhen(() -> {
            ItemEntity remainder = reelItem.get();
            helper.assertTrue(task.getBiteRollCount() == 1, "full-inventory bite rolled catch chance more than once");
            helper.assertTrue(reelFinished.get(), "full inventory did not finish the reel as a world remainder");
            helper.assertTrue(remainder != null && !remainder.isRemoved(), "full inventory did not preserve the real reel entity");
            helper.assertTrue(!remainder.hasPickUpDelay(), "finished reel remainder stayed permanently protected");
        });
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

    private static BlockPos biteCycleVillagerPos(GameTestHelper helper) {
        BlockPos anchor = helper.absolutePos(BlockPos.ZERO);
        ChunkPos tickingChunk = ChunkPos.containing(anchor);
        return new BlockPos(
                tickingChunk.getMinBlockX() + 3,
                anchor.getY() + 2,
                tickingChunk.getMinBlockZ() + 8
        );
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

    private static void prepareBiteWater(GameTestHelper helper, BlockPos center) {
        for (int x = -1; x <= 8; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos water = center.offset(x, 0, z);
                helper.getLevel().setBlock(water.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
                helper.getLevel().setBlock(water.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        BlockPos villagerPos = center.west(2);
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos floor = villagerPos.offset(x, -1, z);
                helper.getLevel().setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(floor.above(3), Blocks.AIR.defaultBlockState(), 3);
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

    private static MCAFishingBobberEntity activeBobber(GameTestHelper helper, VillagerEntityMCA villager) {
        return helper.getLevel()
                .getEntitiesOfClass(MCAFishingBobberEntity.class, villager.getBoundingBox().inflate(32.0D))
                .stream()
                .filter(entity -> !entity.isRemoved() && entity.getVillagerOwner() == villager)
                .findFirst()
                .orElse(null);
    }

    private static int countCaughtItems(VillagerEntityMCA villager) {
        return villager.getInventory().getItems().stream()
                .filter(stack -> !stack.isEmpty())
                .filter(stack -> !(stack.getItem() instanceof FishingRodItem))
                .mapToInt(ItemStack::getCount)
                .sum();
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
        private final Boolean forcedBiteResult;
        private int biteRollCount;

        private TestFishingTask(Player assigningPlayer) {
            this(assigningPlayer, null);
        }

        private TestFishingTask(Player assigningPlayer, Boolean forcedBiteResult) {
            this.assigningPlayer = assigningPlayer;
            this.forcedBiteResult = forcedBiteResult;
        }

        @Override
        boolean shouldReelBite(VillagerEntityMCA villager) {
            biteRollCount++;
            return forcedBiteResult != null ? forcedBiteResult : super.shouldReelBite(villager);
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

        private int getBiteRollCount() {
            return biteRollCount;
        }
    }
}
