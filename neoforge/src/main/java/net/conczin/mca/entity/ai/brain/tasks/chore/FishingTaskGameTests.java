package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.gametest.GameTestBrain;
import com.mojang.authlib.GameProfile;
import net.minecraft.advancements.criterion.FishingHookPredicate;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.TaskUtils;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class FishingTaskGameTests {
    private static final List<Entity> NATURAL_FISHING_ENTITIES = new ArrayList<>();

    @GameTest(batch = "mca_fishing_cast_distance", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void fartherCastLandsAtChosenWaterAcrossBankHeights(GameTestHelper helper) {
        BlockPos stance = helper.absolutePos(new BlockPos(40, 6, 40));
        for (BlockPos p : BlockPos.betweenClosed(stance.offset(-3, -5, -8), stance.offset(12, 5, 8))) {
            helper.getLevel().setBlock(p, p.getY() < stance.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        VillagerEntityMCA villager = spawnFisher(helper, stance);
        try {
            for (int bankHeight = 0; bankHeight <= 3; bankHeight++) {
                for (BlockPos p : BlockPos.betweenClosed(stance.offset(2, -4, -5), stance.offset(11, 1, 5))) {
                    helper.getLevel().setBlock(p, p.getY() <= stance.getY() - bankHeight
                            ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
                }
                for (int distance = 4; distance <= 7; distance++) {
                    BlockPos target = stance.east(distance).below(bankHeight);
                    MCAFishingBobberEntity bobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, target);
                    try {
                        for (int tick = 0; tick < 40 && !bobber.isBobbing() && !bobber.isRemoved(); tick++) bobber.tick();
                        helper.assertTrue(bobber.isBobbing(), "cast missed water at distance " + distance + ", bankHeight=" + bankHeight);
                        helper.assertTrue(bobber.position().subtract(Vec3.atCenterOf(target)).horizontalDistance() <= 0.8,
                                "cast landed short: target=" + target + ", landing=" + bobber.position() + ", bankHeight=" + bankHeight);
                    } finally { bobber.discard(); }
                }
            }
        } finally { villager.discard(); }
        helper.succeed();
    }

    private FishingTaskGameTests() {
    }

    @GameTest(batch = "mca_fishing_bobber", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void bobberCastAtSelectedWaterStartsBobbing(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(40, 3, 40));
        BlockPos water = villagerPos.east(2);
        // The 26.x blank arena fills unprepared volume with barrier blocks.
        // Clear the casting lane before preparing the pond and its shoreline.
        for (BlockPos pos : BlockPos.betweenClosed(villagerPos.offset(-2, 0, -2), water.offset(2, 5, 2))) {
            helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
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

    @GameTest(batch = "mca_fishing_bobber", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void bobberOwnerReferenceFollowsCurrentBobberLifecycle(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(40, 3, 40));
        BlockPos water = villagerPos.east(2);
        prepareWater(helper, water);

        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        MCAFishingBobberEntity first = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);
        helper.assertTrue(villager.getFishingBobber() == first, "cast bobber was not tracked by its villager owner");

        MCAFishingBobberEntity second = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);
        helper.assertTrue(villager.getFishingBobber() == second, "new cast did not replace the tracked bobber");

        first.discard();
        helper.assertTrue(villager.getFishingBobber() == second, "removing an old bobber cleared the current one");

        second.discard();
        helper.assertTrue(villager.getFishingBobber() == null, "removing the current bobber did not clear the owner reference");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_bobber", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void cancelledBobberSpawnDoesNotLeaveGhostOwnerReference(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(40, 3, 40));
        BlockPos water = villagerPos.east(2);
        prepareWater(helper, water);
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        int[] cancelled = {0};
        Consumer<EntityJoinLevelEvent> cancellation = event -> {
            if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof MCAFishingBobberEntity) {
                cancelled[0]++;
                event.setCanceled(true);
            }
        };

        try {
            NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, cancellation);
            try {
                MCAFishingBobberEntity rejected = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);
                helper.assertTrue(cancelled[0] == 1, "fixture did not cancel the bobber spawn");
                helper.assertTrue(rejected == null, "cancelled cast reported an unspawned bobber as usable");
                helper.assertTrue(villager.getFishingBobber() == null,
                        "cancelled cast left a ghost bobber in the villager's owner reference");
            } finally {
                NeoForge.EVENT_BUS.unregister(cancellation);
            }

            MCAFishingBobberEntity accepted = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);
            try {
                helper.assertTrue(accepted != null && villager.getFishingBobber() == accepted,
                        "villager could not cast normally after a cancelled spawn");
            } finally {
                if (accepted != null) accepted.discard();
            }
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_bobber", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void fishingChoreHandlesCancelledBobberWithoutStalling(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(40, 3, 40));
        prepareWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL));
        int[] cancelled = {0};
        Consumer<EntityJoinLevelEvent> cancellation = event -> {
            if (event.getLevel() == helper.getLevel() && event.getEntity() instanceof MCAFishingBobberEntity) {
                cancelled[0]++;
                event.setCanceled(true);
            }
        };

        NeoForge.EVENT_BUS.addListener(EntityJoinLevelEvent.class, cancellation);
        try {
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);
            helper.assertTrue(cancelled[0] > 0, "fishing chore never attempted a cast");
            helper.assertTrue(villager.getFishingBobber() == null,
                    "fishing chore retained a rejected bobber and cannot retry later");
            task.tick(helper.getLevel(), villager, time + 1);
            helper.assertTrue(villager.getFishingBobber() == null,
                    "fishing chore recached a rejected bobber on the next tick");
        } finally {
            NeoForge.EVENT_BUS.unregister(cancellation);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_water_recovery", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void bobberStuckOutOfWaterExpiresSoVillagerCanRecast(GameTestHelper helper) {
        BlockPos feet = biteCycleVillagerPos(helper);
        BlockPos water = feet.east(2);
        prepareBiteWater(helper, water);
        VillagerEntityMCA villager = spawnFisher(helper, feet);
        MCAFishingBobberEntity bobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);
        try {
            tickUntilBobbing(helper, bobber);
            BlockPos dry = feet.west(3);
            helper.assertTrue(helper.getLevel().getFluidState(dry).isEmpty(),
                    "water-recovery fixture selected wet ground");
            bobber.setPos(Vec3.atBottomCenterOf(dry));
            bobber.setDeltaMovement(Vec3.ZERO);
            for (int i = 0; i < 15 && !bobber.isRemoved(); i++) {
                bobber.tick();
            }
            helper.assertTrue(bobber.isRemoved(),
                    "bobber remained alive after 15 dry ticks with its fishing countdown stalled");
            helper.assertTrue(villager.getFishingBobber() == null,
                    "stranded bobber kept the owner reference and prevented recasting");
        } finally {
            bobber.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(
            batch = "mca_fishing_bite",
            templateNamespace = "mca",
            template = "gametest/isolated_ai_arena",
            timeoutTicks = 800
    )
    public static void bobberEventuallyEntersRealBiteWindow(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        BlockPos water = villagerPos.east(2);
        prepareBiteWater(helper, water);

        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        MCAFishingBobberEntity bobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);
        AtomicBoolean biteSeen = new AtomicBoolean();
        AtomicInteger biteTicks = new AtomicInteger();

        helper.succeedWhen(() -> {
            if (!biteSeen.get()) {
                helper.assertTrue(
                        bobber.isBobbing() && bobber.isBiting(),
                        "bobber never reached the vanilla-shaped bite window"
                );
                biteSeen.set(true);
            }

            helper.assertTrue(!bobber.isRemoved(), "bobber was discarded during the vanilla bite dip");
            helper.assertTrue(
                    biteTicks.incrementAndGet() >= 12,
                    "waiting for the same bobber to survive the bite dip"
            );
        });
    }

    @GameTest(
            batch = "mca_fishing_reel",
            templateNamespace = "mca",
            template = "gametest/isolated_ai_arena",
            timeoutTicks = 900
    )
    public static void biteSpawnsVisibleReelBeforeDeliveringCatch(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true)
                .withFishingLoot(List.of(new ItemStack(Items.COD)));
        AtomicBoolean delivered = new AtomicBoolean();
        AtomicReference<ItemEntity> reelItem = new AtomicReference<>();
        int startingFish = villager.getInventory().countItem(Items.COD);
        int startingRodDamage = villager.getItemInHand(villager.getDominantHand()).getDamageValue();

        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.onEachTick(() -> {
            if (delivered.get()) {
                return;
            }

            int rollsBeforeTick = task.getBiteRollCount();
            task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());

            if (task.getBiteRollCount() > rollsBeforeTick) {
                ItemEntity spawned = singleCodReelItem(helper, villager);
                helper.assertTrue(spawned != null, "successful bite did not spawn a visible caught item");
                helper.assertTrue(villager.getInventory().countItem(Items.COD) == startingFish,
                        "fisher was credited before the world catch arrived");
                helper.assertTrue(spawned.getDeltaMovement().lengthSqr() > 0.0,
                        "caught item was not launched toward the fisher");
                reelItem.set(spawned);
            }
            if (reelItem.get() != null && villager.getInventory().countItem(Items.COD) > startingFish) {
                delivered.set(true);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(delivered.get(), "visible reel never delivered its catch");
            helper.assertTrue(reelItem.get() != null && reelItem.get().isRemoved(),
                    "delivered reel item remained collectible and could duplicate");
            helper.assertTrue(villager.getInventory().countItem(Items.COD) == startingFish + 1,
                    "real bite did not deliver exactly one COD");
            helper.assertTrue(task.getBiteRollCount() == 1, "successful bite rolled catch chance more than once");
            helper.assertTrue(
                    villager.getItemInHand(villager.getDominantHand()).getDamageValue() == startingRodDamage + 1,
                    "one delivered bite did not damage the fishing rod exactly once"
            );
        });
    }

    @GameTest(batch = "mca_fishing_reel_homing", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void reelItemUsesXpOrbAttractionUntilHalfBlockDelivery(GameTestHelper helper) {
        BlockPos feet = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, feet.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, feet);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true)
                .withFishingLoot(List.of(new ItemStack(Items.COD)));
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.assertTrue(villager.getFishingBobber() != null, "reel fixture failed to cast");
        invokeBeginReel(task, helper, villager);

        ItemEntity item = singleCodReelItem(helper, villager);
        helper.assertTrue(item != null, "reel did not spawn an ordinary world ItemEntity");
        Vec3 target = new Vec3(villager.getX(), villager.getY() + villager.getEyeHeight() / 2.0, villager.getZ());
        item.setPos(target.add(1.0, 0.0, 0.0));
        item.setDeltaMovement(Vec3.ZERO);
        item.needsSync = false;
        task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());

        helper.assertTrue(!item.isRemoved(), "reel item arrived outside the half-block radius");
        helper.assertTrue(item.needsSync, "homing velocity was not marked for synchronization");
        helper.assertTrue(Math.abs(item.getDeltaMovement().x + 0.0765625) < 1.0E-7
                        && Math.abs(item.getDeltaMovement().y) < 1.0E-7
                        && Math.abs(item.getDeltaMovement().z) < 1.0E-7,
                "reel did not use XP-orb acceleration: " + item.getDeltaMovement());

        item.setPos(target.add(0.25, 0.0, 0.0));
        task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime() + 1);
        helper.assertTrue(item.isRemoved(), "reel was not delivered inside half a block");
        helper.assertTrue(villager.getInventory().countItem(Items.COD) == 1, "arrival did not credit exactly one COD");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_reel_pickup", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void playerCollectsReelWithoutDuplicatingFisherCatch(GameTestHelper helper) {
        BlockPos feet = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, feet.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, feet);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true)
                .withFishingLoot(List.of(new ItemStack(Items.COD, 3)));
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        invokeBeginReel(task, helper, villager);
        ItemEntity item = singleCodReelItem(helper, villager);
        helper.assertTrue(item != null, "player pickup fixture lacks world reel");

        Player collector = helper.makeMockPlayer(GameType.SURVIVAL);
        item.playerTouch(collector);
        helper.assertTrue(item.isRemoved() && collector.getInventory().countItem(Items.COD) == 3,
                "ordinary reel was not collectible by another player");
        for (int tick = 0; tick < 41; tick++) {
            task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime() + tick);
        }
        task.stop(helper.getLevel(), villager, helper.getLevel().getGameTime() + 41);
        helper.assertTrue(villager.getInventory().countItem(Items.COD) == 0,
                "fisher duplicated a catch already picked up by another player");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_reel_hopper", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void hopperCollectsReelWithoutCreditingFisher(GameTestHelper helper) {
        BlockPos feet = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, feet.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, feet);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true)
                .withFishingLoot(List.of(new ItemStack(Items.COD, 3)));
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        invokeBeginReel(task, helper, villager);
        ItemEntity item = singleCodReelItem(helper, villager);
        helper.assertTrue(item != null, "hopper fixture lacks world reel");

        BlockPos hopperPos = feet.west(3).below();
        helper.getLevel().setBlock(hopperPos, Blocks.HOPPER.defaultBlockState(), 3);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(hopperPos);
        helper.assertTrue(hopper != null, "hopper fixture could not place hopper");
        item.setPos(hopperPos.getX() + 0.5, hopperPos.getY() + 1.1, hopperPos.getZ() + 0.5);
        helper.assertTrue(HopperBlockEntity.suckInItems(helper.getLevel(), hopper),
                "vanilla hopper could not absorb ordinary reel item");
        helper.assertTrue(item.isRemoved() && hopper.getItem(0).getCount() == 3,
                "hopper did not absorb exactly the original catch");

        task.stop(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.assertTrue(villager.getInventory().countItem(Items.COD) == 0,
                "fisher duplicated a catch already collected by a hopper");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_reel_partial_pickup", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void partiallyCollectedReelCreditsOnlyRemainingStack(GameTestHelper helper) {
        BlockPos feet = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, feet.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, feet);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true)
                .withFishingLoot(List.of(new ItemStack(Items.COD, 3)));
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        invokeBeginReel(task, helper, villager);
        ItemEntity item = singleCodReelItem(helper, villager);
        helper.assertTrue(item != null, "partial-pickup fixture lacks world reel");

        BlockPos hopperPos = feet.west(3).below();
        helper.getLevel().setBlock(hopperPos, Blocks.HOPPER.defaultBlockState(), 3);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(hopperPos);
        helper.assertTrue(hopper != null, "partial-pickup fixture lacks hopper");
        for (int slot = 0; slot < hopper.getContainerSize() - 1; slot++) {
            hopper.setItem(slot, new ItemStack(Blocks.STONE, 64));
        }
        hopper.setItem(hopper.getContainerSize() - 1, new ItemStack(Items.COD, 63));
        item.setPos(hopperPos.getX() + 0.5, hopperPos.getY() + 1.1, hopperPos.getZ() + 0.5);
        HopperBlockEntity.suckInItems(helper.getLevel(), hopper);
        helper.assertTrue(hopper.countItem(Items.COD) == 64
                        && !item.isRemoved() && item.getItem().getCount() == 2,
                "hopper did not take only its one fitting COD from the reel");

        item.setPos(villager.getX(), villager.getY() + villager.getEyeHeight() / 2.0, villager.getZ());
        task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.assertTrue(item.isRemoved() && villager.getInventory().countItem(Items.COD) == 2,
                "fisher did not receive exactly the two COD remaining after third-party pickup");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_reel_bound", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void distantReelDeliversOnFortiethTaskTick(GameTestHelper helper) {
        BlockPos feet = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, feet.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, feet);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true)
                .withFishingLoot(List.of(new ItemStack(Items.COD)));
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        invokeBeginReel(task, helper, villager);
        ItemEntity item = singleCodReelItem(helper, villager);
        helper.assertTrue(item != null, "timeout fixture lacks world reel");
        item.setPos(villager.position().add(10.0, 3.0, 0.0));
        item.setDeltaMovement(Vec3.ZERO);

        for (int tick = 1; tick < 40; tick++) {
            task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime() + tick);
        }
        helper.assertTrue(!item.isRemoved() && villager.getInventory().countItem(Items.COD) == 0,
                "distant reel was credited before the 40-tick bound");
        task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime() + 40);
        helper.assertTrue(item.isRemoved() && villager.getInventory().countItem(Items.COD) == 1,
                "reel was not delivered by the 40-tick bound");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_reel_stop", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void interruptedReelSettlesLiveFisherButLeavesDeadFisherItem(GameTestHelper helper) {
        BlockPos feet = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, feet.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, feet);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true)
                .withFishingLoot(List.of(new ItemStack(Items.COD)));
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        invokeBeginReel(task, helper, villager);
        ItemEntity first = singleCodReelItem(helper, villager);
        helper.assertTrue(first != null, "live-stop fixture lacks world reel");
        task.stop(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.assertTrue(first.isRemoved() && villager.getInventory().countItem(Items.COD) == 1,
                "interrupted live fisher lost or duplicated its pending catch");

        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        invokeBeginReel(task, helper, villager);
        ItemEntity second = singleCodReelItem(helper, villager);
        helper.assertTrue(second != null, "dead-stop fixture lacks world reel");
        villager.discard();
        task.stop(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.assertTrue(!second.isRemoved() && second.getItem().getCount() == 1,
                "dead fisher's world reel was deleted instead of being left collectible");
        helper.succeed();
    }

    @GameTest(
            batch = "mca_fishing_repeated_catches",
            templateNamespace = "mca",
            template = "gametest/isolated_ai_arena",
            timeoutTicks = 650
    )
    public static void enchantedFishingRodKeepsCatchingAfterFirstReel(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        ItemStack enchantedRod = new ItemStack(Items.FISHING_ROD);
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        enchantedRod.enchant(enchantments.getOrThrow(Enchantments.LURE), 3);
        enchantedRod.enchant(enchantments.getOrThrow(Enchantments.LUCK_OF_THE_SEA), 3);
        villager.setItemInHand(villager.getDominantHand(), enchantedRod);
        villager.addEffect(new MobEffectInstance(MobEffects.LUCK, 1200, 1));

        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true)
                .withFishingLoot(List.of(new ItemStack(Items.COD)));
        int startingFish = villager.getInventory().countItem(Items.COD);
        long now = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), villager, now);
        AtomicReference<MCAFishingBobberEntity> accelerated = new AtomicReference<>();

        helper.onEachTick(() -> {
            MCAFishingBobberEntity bobber = villager.getFishingBobber();
            if (bobber != null && bobber.isBobbing() && accelerated.get() != bobber) {
                setFishingCountdown(bobber, "timeUntilLured", 0);
                setFishingCountdown(bobber, "timeUntilHooked", 2);
                accelerated.set(bobber);
            }
            task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());
        });
        helper.succeedWhen(() -> helper.assertTrue(
                villager.getInventory().countItem(Items.COD) >= startingFish + 3,
                "enchanted villager stopped fishing after its first reel; catches="
                        + (villager.getInventory().countItem(Items.COD) - startingFish)
                        + ", bites=" + task.getBiteRollCount()
                        + ", bobber=" + villager.getFishingBobber()
                        + ", job=" + villager.getVillagerBrain().getCurrentJob()
        ));
    }

    @GameTest(batch = "mca_fishing_fixture_cleanup", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 40)
    public static void naturalFishingFixtureReleasesJobAssignerOnCleanup(GameTestHelper helper) {
        FakePlayer assigner = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "FishingCleanup"));
        assigner.snapTo(helper.absolutePos(new BlockPos(40, 3, 40)).getCenter().x,
                helper.absolutePos(new BlockPos(40, 3, 40)).getY(),
                helper.absolutePos(new BlockPos(40, 3, 40)).getCenter().z);
        helper.getLevel().addNewPlayer(assigner);
        NATURAL_FISHING_ENTITIES.add(assigner);
        try {
            helper.assertTrue(helper.getLevel().players().contains(assigner),
                    "test fixture never registered its fishing assigner");
            cleanupNaturalFishingBatch(helper.getLevel());
            helper.assertTrue(!helper.getLevel().players().contains(assigner),
                    "fishing fixture left a registered player after cleanup");
        } finally {
            assigner.discard();
            NATURAL_FISHING_ENTITIES.remove(assigner);
        }
        helper.succeed();
    }

    @AfterBatch(batch = "mca_fishing_natural_brain")
    public static void cleanupNaturalFishingBatch(ServerLevel level) {
        NATURAL_FISHING_ENTITIES.forEach(Entity::discard);
        NATURAL_FISHING_ENTITIES.clear();
    }

    @GameTest(
            batch = "mca_fishing_natural_brain",
            templateNamespace = "mca",
            template = "gametest/isolated_ai_arena",
            timeoutTicks = 6000
    )
    public static void assignedFisherKeepsCatchingWithNaturalBrain(GameTestHelper helper) {
        BlockPos feet = biteCycleVillagerPos(helper);
        prepareTaskWater(helper.getLevel(), feet);
        VillagerEntityMCA villager = spawnFisher(helper, feet);
        NATURAL_FISHING_ENTITIES.add(villager);
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        rod.enchant(enchantments.getOrThrow(Enchantments.LURE), 3);
        rod.enchant(enchantments.getOrThrow(Enchantments.LUCK_OF_THE_SEA), 3);
        villager.setItemInHand(villager.getDominantHand(), rod);
        villager.addEffect(new MobEffectInstance(MobEffects.LUCK, 7000, 1));

        FakePlayer assigner = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "FishingAssigner"));
        NATURAL_FISHING_ENTITIES.add(assigner);
        assigner.setGameMode(GameType.SURVIVAL);
        assigner.snapTo(feet.getX() + 1.5, feet.getY(), feet.getZ() + 6.5);
        helper.getLevel().addNewPlayer(assigner);
        villager.getVillagerBrain().assignJob(Chore.FISH, assigner);
        // The natural-fishing scenario is about fishing across multiple bites,
        // so control the villager's own schedule independently of suite world time.
        villager.getBrain().setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
        villager.getVillagerBrain().think();
        helper.assertTrue(villager.getVillagerBrain().getJobAssigner().isPresent(),
                "natural AI fishing fixture has no online job assigner");
        helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.CHORE),
                "assigning a fishing chore left an idle activity able to publish a walk target");
        villager.setNoAi(false);

        AtomicReference<MCAFishingBobberEntity> seenBobber = new AtomicReference<>();
        AtomicInteger runningFishingTicks = new AtomicInteger();
        AtomicInteger observedBobbers = new AtomicInteger();
        AtomicInteger blockedByWalkTicks = new AtomicInteger();
        helper.onEachTick(() -> {
            if (villager.getBrain().getRunningBehaviors().stream()
                    .anyMatch(b -> FishingTask.class.isAssignableFrom(b.getClass()))) {
                runningFishingTicks.incrementAndGet();
            }
            if (villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                blockedByWalkTicks.incrementAndGet();
            }
            MCAFishingBobberEntity bobber = villager.getFishingBobber();
            if (bobber != null && bobber.isBobbing() && seenBobber.get() != bobber) {
                seenBobber.set(bobber);
                observedBobbers.incrementAndGet();
            }
        });

        helper.succeedWhen(() -> {
            int catches = countCaughtItems(villager);
            int fish = villager.getInventory().countItem(Items.COD)
                    + villager.getInventory().countItem(Items.SALMON)
                    + villager.getInventory().countItem(Items.TROPICAL_FISH)
                    + villager.getInventory().countItem(Items.PUFFERFISH);
            if (fish >= 3) {
                villager.discard();
                assigner.discard();
                return;
            }
            helper.assertTrue(false, "natural brain fishing did not deliver 3 fish; fish="
                    + fish + ", loot=" + catches + ", job=" + villager.getVillagerBrain().getCurrentJob()
                    + ", activity=" + villager.getBrain().getActiveNonCoreActivity()
                    + ", bobber=" + villager.getFishingBobber()
                    + ", held=" + villager.getItemInHand(villager.getDominantHand())
                    + ", runningFishingTicks=" + runningFishingTicks.get()
                    + ", observedBobbers=" + observedBobbers.get()
                    + ", walkTargetTicks=" + blockedByWalkTicks.get()
                    + ", walkTarget=" + villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                    + ", lookTarget=" + villager.getBrain().getMemory(MemoryModuleType.LOOK_TARGET)
                    + ", pos=" + villager.position()
                    + ", running=" + villager.getBrain().getRunningBehaviors().stream()
                        .map(b -> b.getClass().getSimpleName()).toList());
        });
    }

    @GameTest(
            batch = "mca_fishing_lure",
            templateNamespace = "mca",
            template = "gametest/isolated_ai_arena",
            timeoutTicks = 800
    )
    public static void lureOneReducesInitialWaitByVanillaAmount(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        BlockPos plainWater = villagerPos.east(2).north();
        BlockPos luredWater = villagerPos.east(2).south();
        prepareBiteWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);

        ItemStack plainRod = new ItemStack(Items.FISHING_ROD);
        villager.setItemInHand(villager.getDominantHand(), plainRod);
        MCAFishingBobberEntity plainBobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, plainWater);
        tickUntilBobbing(helper, plainBobber);

        ItemStack lureRod = new ItemStack(Items.FISHING_ROD);
        lureRod.enchant(
                helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LURE),
                1
        );
        villager.setItemInHand(villager.getDominantHand(), lureRod);
        MCAFishingBobberEntity luredBobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, luredWater);
        tickUntilBobbing(helper, luredBobber);

        setFishingCountdown(plainBobber, "timeUntilLured", 0);
        setFishingCountdown(luredBobber, "timeUntilLured", 0);
        setFishingCountdown(plainBobber, "timeUntilHooked", 0);
        setFishingCountdown(luredBobber, "timeUntilHooked", 0);
        plainBobber.getRandom().setSeed(42L);
        luredBobber.getRandom().setSeed(42L);
        invokeCatchingFish(plainBobber);
        invokeCatchingFish(luredBobber);

        int plainWait = getFishingCountdown(plainBobber, "timeUntilLured");
        int luredWait = getFishingCountdown(luredBobber, "timeUntilLured");
        helper.assertTrue(
                plainWait - luredWait == 100,
                "Lure I did not apply vanilla's 100-tick wait reduction; plain=" + plainWait + ", lured=" + luredWait
        );
        helper.succeed();
    }

    @GameTest(
            batch = "mca_fishing_rain",
            templateNamespace = "mca",
            template = "gametest/isolated_ai_arena",
            timeoutTicks = 800
    )
    public static void rainAdvancesFishingCountdownLikeVanilla(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        BlockPos water = villagerPos.east(2);
        prepareBiteWater(helper, water);
        for (BlockPos pos : BlockPos.betweenClosed(water.offset(-2, 1, -2), water.offset(10, 6, 2))) {
            helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        MCAFishingBobberEntity bobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);
        tickUntilBobbing(helper, bobber);
        long rainSeed = findSeedForRainRoll(bobber);
        BlockPos canopy = bobber.blockPosition().above(8);
        var originalCanopy = helper.getLevel().getBlockState(canopy);
        BlockPos rainProbe = bobber.blockPosition().above();
        int originalSkyLight = helper.getLevel().getBrightness(LightLayer.SKY, rainProbe);

        try {
            helper.assertTrue(!originalCanopy.hasBlockEntity(), "rain canopy fixture would overwrite a block entity at " + canopy);
            helper.getLevel().setBlock(canopy, Blocks.CHERRY_LEAVES.defaultBlockState(), 3);
            try (RainFixture fixture = new RainFixture(helper.getLevel(), bobber.blockPosition())) {
                fixture.prepare();
                helper.assertTrue(helper.getLevel().canSeeSky(bobber.blockPosition().above())
                                && helper.getLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, bobber.blockPosition().above()).getY()
                                <= bobber.blockPosition().above().getY(),
                        "rain fixture did not clear overhead terrain above the bobber");
                helper.getLevel().getWeatherData().setClearWeatherTime(6000);
                helper.getLevel().getWeatherData().setRainTime(0);
                helper.getLevel().getWeatherData().setRaining(false);
                helper.getLevel().getWeatherData().setThundering(false);
                helper.getLevel().setRainLevel(0.0F);
                setFishingCountdown(bobber, "timeUntilLured", 2);
                bobber.getRandom().setSeed(rainSeed);
                invokeCatchingFish(bobber);
                helper.assertTrue(
                        getFishingCountdown(bobber, "timeUntilLured") == 1,
                        "dry fishing countdown did not advance by exactly one tick"
                );

                helper.getLevel().getWeatherData().setClearWeatherTime(0);
                helper.getLevel().getWeatherData().setRainTime(6000);
                helper.getLevel().getWeatherData().setRaining(true);
                helper.getLevel().getWeatherData().setThundering(false);
                helper.getLevel().setRainLevel(1.0F);
                helper.assertTrue(
                        helper.getLevel().isRainingAt(bobber.blockPosition().above()),
                        "rain timing fixture was not actually raining at the bobber; raining=" + helper.getLevel().isRaining()
                                + "; sky=" + helper.getLevel().canSeeSky(bobber.blockPosition().above())
                                + "; height=" + helper.getLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, bobber.blockPosition().above())
                                + "; topBlock=" + helper.getLevel().getBlockState(helper.getLevel().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, bobber.blockPosition().above()).below())
                                + "; pos=" + bobber.blockPosition().above()
                                + "; biome=" + helper.getLevel().getBiome(bobber.blockPosition().above()).unwrapKey()
                                + "; precipitation=" + helper.getLevel().getBiome(bobber.blockPosition().above()).value()
                                        .getPrecipitationAt(bobber.blockPosition().above(), helper.getLevel().getSeaLevel())
                );
                setFishingCountdown(bobber, "timeUntilLured", 2);
                bobber.getRandom().setSeed(rainSeed);
                invokeCatchingFish(bobber);
                helper.assertTrue(
                        getFishingCountdown(bobber, "timeUntilLured") <= 0,
                        "rain did not apply vanilla's extra fishing-countdown tick"
                );
            }
        } finally {
            try {
                if (!originalCanopy.hasBlockEntity()) {
                    helper.getLevel().setBlock(canopy, originalCanopy, 3);
                    RainFixture.waitForSkyLight(helper.getLevel(), rainProbe, originalSkyLight);
                }
            } finally {
                bobber.discard();
                villager.discard();
            }
        }
        helper.succeed();
    }

    @GameTest(
            batch = "mca_fishing_luck",
            templateNamespace = "mca",
            template = "gametest/isolated_ai_arena"
    )
    public static void luckOfTheSeaContributesToFishingLootLuck(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL));
        long gameTime = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), villager, gameTime);
        task.tick(helper.getLevel(), villager, gameTime);
        task.tick(helper.getLevel(), villager, gameTime + 1);
        helper.assertTrue(villager.getFishingBobber() != null, "fishing loot test did not create a bobber context");

        ItemStack plainRod = new ItemStack(Items.FISHING_ROD);
        villager.setItemInHand(villager.getDominantHand(), plainRod);
        int plainJunk = sampleFishingJunk(task, helper, villager, 3000);

        ItemStack luckyRod = new ItemStack(Items.FISHING_ROD);
        luckyRod.enchant(
                helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.LUCK_OF_THE_SEA),
                3
        );
        villager.setItemInHand(villager.getDominantHand(), luckyRod);
        int luckyJunk = sampleFishingJunk(task, helper, villager, 3000);

        helper.assertTrue(
                luckyJunk <= plainJunk - 90,
                "Luck of the Sea III did not reduce junk through the fishing loot context; plain="
                        + plainJunk + ", lucky=" + luckyJunk
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_potion_luck", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void luckPotionImprovesFishingLootForVillagers(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL));
        long time = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time + 1);
        helper.assertTrue(villager.getFishingBobber() != null, "potion loot test has no bobber context");

        int noPotionJunk = sampleFishingJunk(task, helper, villager, 6000);
        villager.addEffect(new MobEffectInstance(MobEffects.LUCK, 1200, 1));
        int potionJunk = sampleFishingJunk(task, helper, villager, 6000);

        helper.assertTrue(potionJunk <= noPotionJunk - 100,
                "Luck II did not improve villager fishing loot; baselineJunk="
                        + noPotionJunk + ", potionJunk=" + potionJunk);
        helper.succeed();
    }

    @GameTest(
            batch = "mca_fishing_open_water",
            templateNamespace = "mca",
            template = "gametest/isolated_ai_arena",
            timeoutTicks = 800
    )
    public static void mcaBobberParticipatesInVanillaOpenWaterPredicate(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        BlockPos openWater = villagerPos.east(3);
        BlockPos shallowWater = villagerPos.west(10);
        prepareOpenWater(helper, openWater);
        prepareWater(helper, shallowWater);
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);

        MCAFishingBobberEntity openBobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, openWater);
        // Casting and landing have their own tests; keep this predicate fixture in the intended pool.
        openBobber.setPos(openWater.getX() + 0.5D, openWater.getY() + 0.5D, openWater.getZ() + 0.5D);
        openBobber.setDeltaMovement(Vec3.ZERO);
        tickUntilBobbing(helper, openBobber);
        setFishingCountdown(openBobber, "timeUntilHooked", 2);
        openBobber.tick();

        MCAFishingBobberEntity shallowBobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, shallowWater);
        shallowBobber.setPos(shallowWater.getX() + 0.5D, shallowWater.getY() + 0.5D, shallowWater.getZ() + 0.5D);
        shallowBobber.setDeltaMovement(Vec3.ZERO);
        tickUntilBobbing(helper, shallowBobber);
        setFishingCountdown(shallowBobber, "timeUntilHooked", 2);
        shallowBobber.tick();

        FishingHookPredicate predicate = FishingHookPredicate.inOpenWater(true);
        helper.assertTrue(openBobber.isOpenWaterFishing(), "valid open-water fixture was rejected");
        helper.assertTrue(
                predicate.matches(openBobber, helper.getLevel(), openBobber.position()),
                "vanilla fishing open-water predicate did not recognize the MCA bobber"
        );
        helper.assertTrue(!shallowBobber.isOpenWaterFishing(), "shallow fishing water was incorrectly treated as open water");
        helper.assertTrue(
                !predicate.matches(shallowBobber, helper.getLevel(), shallowBobber.position()),
                "vanilla fishing open-water predicate accepted an invalid MCA fishing area"
        );
        helper.assertTrue(!openBobber.canUsePortal(false), "MCA fishing bobber retained generic portal behavior");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_empty_loot", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void emptyFishingLootDoesNotCreateFallbackCatchOrCrash(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(40, 3, 40));
        prepareWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL))
                .withFishingLoot(List.of());
        long time = helper.getLevel().getGameTime();

        task.start(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time + 1);
        task.tick(helper.getLevel(), villager, time + 2);

        helper.assertTrue(villager.getFishingBobber() != null, "empty-loot fixture did not create a bobber");
        int itemEntitiesBefore = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(32.0D))
                .size();
        int caughtItemsBefore = countCaughtItems(villager);

        invokeBeginReel(task, helper, villager);

        int itemEntitiesAfter = helper.getLevel()
                .getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(32.0D))
                .size();
        helper.assertTrue(activeBobberCount(helper, villager) == 0L, "empty loot did not finish the reel lifecycle");
        helper.assertTrue(
                countCaughtItems(villager) == caughtItemsBefore,
                "empty fishing loot created a fallback catch"
        );
        helper.assertTrue(
                itemEntitiesAfter == itemEntitiesBefore,
                "empty fishing loot spawned a reel ItemEntity"
        );
        helper.succeed();
    }

    @GameTest(
            batch = "mca_fishing_reaction",
            templateNamespace = "mca",
            template = "gametest/isolated_ai_arena",
            timeoutTicks = 900
    )
    public static void successfulBiteRemainsVisibleBeforeNpcReels(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true);
        AtomicBoolean biteSeen = new AtomicBoolean();
        AtomicInteger visibleBiteTicks = new AtomicInteger();

        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.onEachTick(() -> {
            MCAFishingBobberEntity bobberBeforeTick = villager.getFishingBobber();
            boolean biteWasActive = bobberBeforeTick != null && bobberBeforeTick.isBiting();

            task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());

            if (!biteWasActive) {
                return;
            }

            biteSeen.set(true);
            int biteTick = visibleBiteTicks.incrementAndGet();
            if (biteTick <= 5) {
                helper.assertTrue(!bobberBeforeTick.isRemoved(), "successful bite reeled before the visible reaction window elapsed");
                helper.assertTrue(task.getBiteRollCount() == 0, "catch chance rolled before the visible reaction window elapsed");
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(biteSeen.get(), "successful fishing never reached a real bite");
            helper.assertTrue(visibleBiteTicks.get() >= 5, "bite was not left visible for five task ticks");
            helper.assertTrue(task.getBiteRollCount() == 1, "successful bite was not reeled exactly once after the reaction window");
        });
    }

    @GameTest(
            batch = "mca_fishing_miss",
            templateNamespace = "mca",
            template = "gametest/isolated_ai_arena",
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

            MCAFishingBobberEntity bobberBeforeTick = villager.getFishingBobber();
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

            MCAFishingBobberEntity active = villager.getFishingBobber();
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
            templateNamespace = "mca",
            template = "gametest/isolated_ai_arena",
            timeoutTicks = 900
    )
    public static void fullInventoryReleasesOnlyTheRealReelRemainder(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
            villager.getInventory().setItem(slot, new ItemStack(Blocks.STONE, 64));
        }
        villager.getInventory().setItem(villager.getInventory().getContainerSize() - 1, new ItemStack(Items.COD, 63));

        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true)
                .withFishingLoot(List.of(new ItemStack(Items.COD, 3)));
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        invokeBeginReel(task, helper, villager);
        ItemEntity remainder = singleCodReelItem(helper, villager);
        helper.assertTrue(remainder != null && remainder.getItem().getCount() == 3,
                "near-full catch did not first exist as three physical COD");
        helper.assertTrue(villager.getInventory().countItem(Items.COD) == 63,
                "near-full fisher was credited before item arrived");

        Vec3 reelTarget = new Vec3(villager.getX(), villager.getY() + villager.getEyeHeight() / 2.0, villager.getZ());
        remainder.setPos(reelTarget);
        task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.assertTrue(villager.getInventory().countItem(Items.COD) == 64,
                "fisher did not take the single fitting COD");
        helper.assertTrue(!remainder.isRemoved() && remainder.getItem().getCount() == 2,
                "overflow must remain in the original collectible ItemEntity, with count two");

        Player collector = helper.makeMockPlayer(GameType.SURVIVAL);
        remainder.playerTouch(collector);
        helper.assertTrue(remainder.isRemoved() && collector.getInventory().countItem(Items.COD) == 2,
                "ordinary overflow item was not collectible by a player");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_timeout", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void fishingDoesNotUseInheritedTimeout(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnFisher(helper, helper.absolutePos(new BlockPos(40, 3, 40)));
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL));

        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());

        helper.assertTrue(!task.isTimedOut(401L), "fishing still uses the inherited 400-tick chore timeout");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_single_bobber", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void repeatedFishingTicksKeepOneBobber(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(40, 3, 40));
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

    @GameTest(batch = "mca_fishing_stop", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void stoppingFishingClearsBobberAndRod(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(40, 3, 40));
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

    @GameTest(batch = "mca_fishing_rod_loss", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void losingLastRodDiscardsBobber(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(40, 3, 40));
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
        // Clearing a real equipment slot now stows its item; model actual rod removal.
        villager.getItemInHand(villager.getDominantHand()).copyAndClear();
        villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
        helper.assertTrue(villager.getInventory().countItem(Items.FISHING_ROD) == 0
                        && villager.getItemInHand(villager.getDominantHand()).isEmpty(),
                "fixture did not remove the last fishing rod");
        task.tick(helper.getLevel(), villager, time + 3);

        helper.assertTrue(activeBobberCount(helper, villager) == 0L, "losing the last rod left an active bobber");
        helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.NONE, "losing the last rod did not abandon fishing");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_recovery", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void stoppingFishingDuringRecoveryReturnsFoodAndClearsRod(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(40, 3, 40));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL));

        ItemStack inventoryRod = villager.getInventory().getItems().stream()
                .filter(stack -> stack.getItem() instanceof FishingRodItem)
                .findFirst()
                .orElseThrow();
        inventoryRod.setDamageValue(34);
        villager.getItemInHand(villager.getDominantHand()).setDamageValue(46);
        villager.getInventory().addItem(new ItemStack(Items.BREAD, 2));
        villager.setHealth(villager.getMaxHealth() - 1.0F);

        int startingRods = villager.getInventory().countItem(Items.FISHING_ROD)
                + villager.getItemInHand(villager.getDominantHand()).getCount();
        int startingBread = villager.getInventory().countItem(Items.BREAD);
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());

        villager.tickCount = 200;
        villager.aiStep();
        helper.assertTrue(villager.isUsingRecoveryFood(), "villager did not begin recovery food use");
        helper.assertTrue(villager.getItemInHand(villager.getDominantHand()).is(Items.BREAD), "recovery food did not own the hand before fishing ticked");

        task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());
        task.stop(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.assertTrue(
                !villager.isUsingRecoveryFood(),
                "stopping fishing left recovery food use active"
        );
        helper.assertTrue(
                villager.getInventory().countItem(Items.BREAD) == startingBread,
                "stopping fishing during recovery deleted recovery food"
        );
        helper.assertTrue(
                villager.getInventory().countItem(Items.FISHING_ROD) == startingRods,
                "fishing hand takeover lost or duplicated a real fishing rod during recovery"
        );
        helper.assertTrue(
                villager.getItemInHand(villager.getDominantHand()).isEmpty(),
                "stopping fishing restored a rod after the chore had ended"
        );
        helper.assertTrue(
                inventoryRod.getDamageValue() == 34,
                "stopping fishing changed the inventory fishing rod during recovery cleanup"
        );
        helper.assertTrue(villager.getInventory().getItems().stream()
                        .filter(stack -> stack.is(Items.FISHING_ROD) && stack.getDamageValue() == 46).count() == 1L,
                "stopping fishing lost the distinct rod that had occupied the hand");
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_head_tracking", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void fishingKeepsLookingAtWaterAcrossLookSinkLifecycle(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(40, 3, 40));
        BlockPos water = villagerPos.east(2);
        prepareWater(helper, water);
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL));
        LookAtTargetSink lookSink = new LookAtTargetSink(1, 1);
        long time = helper.getLevel().getGameTime();

        int fishingPriority = VillagerTasksMCA.getChorePackage().stream()
                .filter(pair -> pair.getSecond() instanceof FishingTask)
                .mapToInt(pair -> pair.getFirst())
                .findFirst()
                .orElseThrow();
        helper.assertTrue(
                fishingPriority > 0,
                "fishing must run after vanilla's priority-0 look sink so an expired target is republished in the same tick"
        );

        task.start(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time);
        task.tick(helper.getLevel(), villager, time + 1);
        helper.assertTrue(
                villager.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET),
                "fishing did not publish its water look target"
        );
        helper.assertTrue(
                lookSink.tryStart(helper.getLevel(), villager, time + 1),
                "look sink did not start from fishing's look target"
        );

        lookSink.tickOrStop(helper.getLevel(), villager, time + 1);
        villager.getLookControl().tick();
        helper.assertTrue(villager.getXRot() > 10.0F, "look sink did not initially drive the fishing look target");

        // Vanilla ticks lower behavior priorities first. The look sink expires at priority 0,
        // then the persistent fishing producer republishes the same memory later that tick.
        lookSink.tickOrStop(helper.getLevel(), villager, time + 3);
        task.tick(helper.getLevel(), villager, time + 3);
        villager.getLookControl().tick();
        helper.assertTrue(
                villager.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET),
                "fishing did not republish the look target after the sink expired"
        );

        helper.assertTrue(
                lookSink.tryStart(helper.getLevel(), villager, time + 4),
                "look sink could not restart on the republished fishing target"
        );
        lookSink.tickOrStop(helper.getLevel(), villager, time + 4);
        task.tick(helper.getLevel(), villager, time + 4);
        villager.getLookControl().tick();
        helper.assertTrue(
                villager.getXRot() > 10.0F,
                "fishing head tracking dropped across the vanilla look-sink lifecycle; pitch=" + villager.getXRot()
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_look_lifecycle", templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void allChoresRunAfterCoreLookSink(GameTestHelper helper) {
        var chorePackage = VillagerTasksMCA.getChorePackage();

        helper.assertTrue(
                chorePackage.stream().allMatch(pair -> pair.getFirst() > 0),
                "every persistent chore must tick after vanilla's priority-0 LookAtTargetSink"
        );
        helper.assertTrue(
                chorePackage.stream().map(pair -> pair.getFirst()).distinct().count() == 1,
                "persistent chores must share one lifecycle priority instead of special-casing fishing"
        );
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

    /** Catch/reel fixtures begin at a valid bank; movement itself is covered by FishingSiteGameTests. */
    private static void prepareTaskWater(ServerLevel world, BlockPos feet) {
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-13, -2, -13), feet.offset(13, 4, 13))) {
            world.setBlock(pos, pos.getY() < feet.getY() ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 2);
        }
        for (BlockPos water : BlockPos.betweenClosed(feet.offset(2, -1, -2), feet.offset(10, -1, 2))) {
            world.setBlock(water, Blocks.WATER.defaultBlockState(), 2);
    }
    }

    private static void prepareWater(GameTestHelper helper, BlockPos center) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos water = center.offset(x, 0, z);
                helper.getLevel().setBlock(water.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
                // The bobber rises above a straight sightline on a normal cast.
                for (int y = 1; y <= 4; y++) {
                    helper.getLevel().setBlock(water.above(y), Blocks.AIR.defaultBlockState(), 3);
                }
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

    private static void prepareOpenWater(GameTestHelper helper, BlockPos center) {
        // Leave enough margin for the cast's vanilla-style inaccuracy while keeping
        // every possible landing point surrounded by the 5x5 area vanilla checks.
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                BlockPos water = center.offset(x, 0, z);
                helper.getLevel().setBlock(water.below(2), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(water.below(), Blocks.WATER.defaultBlockState(), 3);
                helper.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
                helper.getLevel().setBlock(water.above(), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(water.above(2), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static long activeBobberCount(GameTestHelper helper, VillagerEntityMCA villager) {
        return helper.getLevel()
                .getEntitiesOfClass(MCAFishingBobberEntity.class, villager.getBoundingBox().inflate(32.0D))
                .stream()
                .filter(entity -> !entity.isRemoved() && entity.getOwner() == villager)
                .count();
    }

    private static ItemEntity singleCodReelItem(GameTestHelper helper, VillagerEntityMCA villager) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, villager.getBoundingBox().inflate(16.0D))
                .stream()
                .filter(item -> !item.isRemoved() && item.getItem().is(Items.COD))
                .findFirst()
                .orElse(null);
    }

    private static int countCaughtItems(VillagerEntityMCA villager) {
        return villager.getInventory().getItems().stream()
                .filter(stack -> !stack.isEmpty())
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

    private static void tickUntilBobbing(GameTestHelper helper, MCAFishingBobberEntity bobber) {
        for (int tick = 0; tick < 40 && !bobber.isBobbing() && !bobber.isRemoved(); tick++) {
            bobber.tick();
        }
        helper.assertTrue(bobber.isBobbing(), "bobber did not reach water before lure timing check");
    }

    private static int firstBiteTick(MCAFishingBobberEntity bobber, int maxTicks) {
        for (int tick = 0; tick < maxTicks; tick++) {
            bobber.tick();
            if (bobber.isBiting()) {
                return tick;
            }
        }
        return -1;
    }

    private static long findSeedForRainRoll(MCAFishingBobberEntity bobber) {
        for (long seed = 0; seed < 10_000; seed++) {
            bobber.getRandom().setSeed(seed);
            if (bobber.getRandom().nextFloat() < 0.25F) {
                return seed;
            }
        }
        throw new AssertionError("could not find deterministic fishing rain seed");
    }

    private static void invokeCatchingFish(MCAFishingBobberEntity bobber) {
        try {
            Method method = MCAFishingBobberEntity.class.getDeclaredMethod("catchingFish");
            method.setAccessible(true);
            method.invoke(bobber);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not invoke fishing countdown step", exception);
        }
    }

    private static void invokeBeginReel(
            FishingTask task,
            GameTestHelper helper,
            VillagerEntityMCA villager
    ) {
        try {
            Method method = FishingTask.class.getDeclaredMethod(
                    "beginReel",
                    net.minecraft.server.level.ServerLevel.class,
                    VillagerEntityMCA.class
            );
            method.setAccessible(true);
            method.invoke(task, helper.getLevel(), villager);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not invoke fishing reel", exception);
        }
    }

    private static void setFishingCountdown(MCAFishingBobberEntity bobber, String name, int value) {
        try {
            Field field = MCAFishingBobberEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(bobber, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not set fishing countdown " + name, exception);
        }
    }

    private static int getFishingCountdown(MCAFishingBobberEntity bobber, String name) {
        try {
            Field field = MCAFishingBobberEntity.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(bobber);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not read fishing countdown " + name, exception);
        }
    }

    private static int sampleFishingJunk(
            FishingTask task,
            GameTestHelper helper,
            VillagerEntityMCA villager,
            int samples
    ) {
        int junk = 0;
        for (int sample = 0; sample < samples; sample++) {
            if (invokeFishingLoot(task, helper, villager).stream().anyMatch(FishingTaskGameTests::isFishingJunk)) {
                junk++;
            }
        }
        return junk;
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> invokeFishingLoot(
            FishingTask task,
            GameTestHelper helper,
            VillagerEntityMCA villager
    ) {
        try {
            Method method = FishingTask.class.getDeclaredMethod(
                    "getFishingLoot",
                    net.minecraft.server.level.ServerLevel.class,
                    VillagerEntityMCA.class
            );
            method.setAccessible(true);
            return (List<ItemStack>) method.invoke(task, helper.getLevel(), villager);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not invoke fishing loot roll", exception);
        }
    }

    private static boolean isFishingJunk(ItemStack stack) {
        return stack.is(Items.LILY_PAD)
                || stack.is(Items.LEATHER_BOOTS)
                || stack.is(Items.LEATHER)
                || stack.is(Items.BONE)
                || stack.is(Items.POTION)
                || stack.is(Items.STRING)
                || stack.is(Items.FISHING_ROD)
                || stack.is(Items.BOWL)
                || stack.is(Items.STICK)
                || stack.is(Items.INK_SAC)
                || stack.is(Items.TRIPWIRE_HOOK)
                || stack.is(Items.ROTTEN_FLESH)
                || stack.is(Items.BAMBOO);
    }

    /** Local synchronous rain check: restore both chunk palettes and weather on any assertion failure. */
    private static final class RainFixture implements AutoCloseable {
        private final ServerLevel world;
        private final Map<LevelChunk, BiomeResolver> originals = new HashMap<>();
        private final Map<BlockPos, BlockState> skyBlocks = new HashMap<>();
        private final BlockPos rainProbe;
        private final int clearTime;
        private final int rainTime;
        private final int thunderTime;
        private final boolean raining;
        private final boolean thundering;
        private final float rainLevel;
        private final float thunderLevel;

        RainFixture(ServerLevel world, BlockPos bobber) {
            this.world = world;
            rainProbe = bobber.above();
            var data = world.getWeatherData();
            clearTime = data.getClearWeatherTime();
            rainTime = data.getRainTime();
            thunderTime = data.getThunderTime();
            raining = data.isRaining();
            thundering = data.isThundering();
            rainLevel = world.getRainLevel(1);
            thunderLevel = world.getThunderLevel(1);
            // BiomeManager can sample across a chunk border around the bobber.
            for (int cx = (bobber.getX() - 4) >> 4; cx <= (bobber.getX() + 4) >> 4; cx++) {
                for (int cz = (bobber.getZ() - 4) >> 4; cz <= (bobber.getZ() + 4) >> 4; cz++) {
                    LevelChunk chunk = world.getChunk(cx, cz);
                    Map<BlockPos, Holder<Biome>> palette = new HashMap<>();
                    for (int x = cx * 4; x < cx * 4 + 4; x++) {
                        for (int z = cz * 4; z < cz * 4 + 4; z++) {
                            for (int y = world.getMinY() >> 2; y <= world.getMaxY() >> 2; y++) {
                                palette.put(new BlockPos(x, y, z), chunk.getNoiseBiome(x, y, z));
                            }
                        }
                    }
                    originals.put(chunk, (x, y, z, sampler) -> palette.get(new BlockPos(x, y, z)));
                }
            }
        }

        void prepare() {
            int topY = world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, rainProbe).getY();
            for (int y = rainProbe.getY(); y < topY; y++) {
                BlockPos pos = new BlockPos(rainProbe.getX(), y, rainProbe.getZ());
                BlockState original = world.getBlockState(pos);
                if (original.hasBlockEntity()) {
                    throw new IllegalStateException("Rain fixture would overwrite block entity at " + pos);
                }
                if (!original.isAir()) {
                    skyBlocks.put(pos, original);
                }
            }
            var plains = world.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
            for (LevelChunk chunk : originals.keySet()) {
                chunk.fillBiomesFromNoise((x, y, z, sampler) -> plains, world.getChunkSource().randomState().sampler());
                chunk.markUnsaved();
            }
            for (BlockPos pos : skyBlocks.keySet()) {
                world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }

            long deadline = System.nanoTime() + 5_000_000_000L;
            world.getServer().managedBlock(() -> {
                world.getChunkSource().getLightEngine().tryScheduleUpdate();
                return (world.canSeeSky(rainProbe)
                        && world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, rainProbe).getY() <= rainProbe.getY()
                        && world.getBrightness(LightLayer.SKY, rainProbe) == 15)
                        || System.nanoTime() >= deadline;
            });
        }

        static void waitForSkyLight(ServerLevel world, BlockPos rainProbe, int expected) {
            long deadline = System.nanoTime() + 5_000_000_000L;
            world.getServer().managedBlock(() -> {
                world.getChunkSource().getLightEngine().tryScheduleUpdate();
                return world.getBrightness(LightLayer.SKY, rainProbe) == expected || System.nanoTime() >= deadline;
            });
            if (world.getBrightness(LightLayer.SKY, rainProbe) != expected) {
                throw new IllegalStateException("Rain fixture did not restore sky light at " + rainProbe);
            }
        }

        @Override
        public void close() {
            try {
                RuntimeException restoreFailure = null;
                for (var entry : skyBlocks.entrySet()) {
                    try {
                        world.setBlock(entry.getKey(), entry.getValue(), 3);
                    } catch (RuntimeException e) {
                        if (restoreFailure == null) restoreFailure = e;
                        else restoreFailure.addSuppressed(e);
                    }
                }
                if (restoreFailure != null) throw restoreFailure;
            } finally {
                try {
                    for (var entry : originals.entrySet()) {
                        entry.getKey().fillBiomesFromNoise(entry.getValue(), world.getChunkSource().randomState().sampler());
                        entry.getKey().markUnsaved();
                    }
                } finally {
                    var data = world.getWeatherData();
                    data.setClearWeatherTime(clearTime);
                    data.setRainTime(rainTime);
                    data.setThunderTime(thunderTime);
                    data.setRaining(raining);
                    data.setThundering(thundering);
                    world.setRainLevel(rainLevel);
                    world.setThunderLevel(thunderLevel);
                }
            }
        }
    }

    private static final class TestFishingTask extends FishingTask {
        private final Player assigningPlayer;
        private final Boolean forcedBiteResult;
        private List<ItemStack> forcedFishingLoot;
        private int biteRollCount;

        private TestFishingTask(Player assigningPlayer) {
            this(assigningPlayer, null);
        }

        private TestFishingTask(Player assigningPlayer, Boolean forcedBiteResult) {
            this.assigningPlayer = assigningPlayer;
            this.forcedBiteResult = forcedBiteResult;
        }

        @Override
        protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
            // These tests exercise catches, not discovery latency or movement. Keep
            // their initial cast ready, using real search over a safe isolated bank.
            boolean waterPresent = !TaskUtils.getNearbyBlocks(villager.blockPosition(), world,
                    state -> state.is(Blocks.WATER), 12, 3).isEmpty();
            if (waterPresent && !villager.isUsingRecoveryFood()) prepareTaskWater(world, villager.blockPosition());
            super.start(world, villager, time);
            if (waterPresent && !villager.isUsingRecoveryFood()) {
                for (int tick = 0; tick < 40 && villager.getFishingBobber() == null; tick++) {
                    super.tick(world, villager, time);
                }
            }
        }

        @Override
        boolean shouldReelBite(VillagerEntityMCA villager) {
            biteRollCount++;
            return forcedBiteResult != null ? forcedBiteResult : super.shouldReelBite(villager);
        }

        @Override
        List<ItemStack> getFishingLoot(ServerLevel world, VillagerEntityMCA villager) {
            return forcedFishingLoot != null ? forcedFishingLoot : super.getFishingLoot(world, villager);
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

        private TestFishingTask withFishingLoot(List<ItemStack> loot) {
            forcedFishingLoot = loot;
            return this;
        }
    }
}
