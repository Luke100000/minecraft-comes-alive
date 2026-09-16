package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.TaskUtils;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.minecraft.core.BlockPos;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

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
        Player reloadPicker = helper.makeMockPlayer(GameType.SURVIVAL);
        AtomicBoolean sawProtectedReel = new AtomicBoolean();
        AtomicBoolean sawReloadRelease = new AtomicBoolean();
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

                if (sawReloadRelease.compareAndSet(false, true)) {
                    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, helper.getLevel().registryAccess());
                    item.saveWithoutId(output);
                    CompoundTag saved = output.buildResult();
                    ValueInput savedInput = TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved);
                    helper.assertTrue(
                            savedInput.read("Thrower", UUIDUtil.CODEC).filter(villager.getUUID()::equals).isPresent(),
                            "protected reel item did not retain the villager as its vanilla thrower"
                    );
                    helper.assertTrue(
                            savedInput.read("Owner", UUIDUtil.CODEC).filter(villager.getUUID()::equals).isPresent(),
                            "protected reel item did not carry the villager reel target marker"
                    );

                    ItemEntity reloaded = new ItemEntity(EntityTypes.ITEM, helper.getLevel());
                    reloaded.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved));
                    helper.assertTrue(!reloaded.hasPickUpDelay(), "reloaded orphan reel stayed pickup-protected");
                    reloaded.playerTouch(reloadPicker);
                    helper.assertTrue(reloaded.isRemoved(), "reloaded orphan reel stayed target-locked to the villager");
                }
            }

            if (item != null && item.isRemoved() && countCaughtItems(villager) > startingLoot) {
                delivered.set(true);
            }
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(sawProtectedReel.get(), "no real reel ItemEntity was observed");
            helper.assertTrue(sawReloadRelease.get(), "protected reel reload behavior was not exercised");
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
            batch = "mca_fishing_reel_homing",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 900
    )
    public static void reelItemUsesXpStyleHomingInsideOldDeliveryRadius(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true);
        AtomicReference<ItemEntity> reelItem = new AtomicReference<>();
        AtomicBoolean checkedHoming = new AtomicBoolean();

        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        helper.onEachTick(() -> {
            if (checkedHoming.get()) {
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
            if (item == null || item.isRemoved()) {
                return;
            }

            Vec3 reelTarget = new Vec3(
                    villager.getX(),
                    villager.getY() + villager.getEyeHeight() / 2.0,
                    villager.getZ()
            );
            item.setPos(reelTarget.add(1.0, 0.0, 0.0));
            item.setDeltaMovement(Vec3.ZERO);
            item.needsSync = false;

            task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());

            helper.assertTrue(!item.isRemoved(), "reel item disappeared at the old 1.5-block delivery cutoff");
            helper.assertTrue(item.needsSync, "reel homing velocity was not marked for client synchronization");
            Vec3 motion = item.getDeltaMovement();
            helper.assertTrue(
                    Math.abs(motion.x + 0.0765625) < 1.0E-7 && Math.abs(motion.y) < 1.0E-7 && Math.abs(motion.z) < 1.0E-7,
                    "reel item did not receive vanilla XP-style homing acceleration; motion=" + motion
            );
            checkedHoming.set(true);
        });

        helper.succeedWhen(() -> helper.assertTrue(checkedHoming.get(), "no reel item reached the homing assertion"));
    }

    @GameTest(batch = "mca_fishing_reel_hopper_protection", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void protectedReelItemCannotBeCollectedByHoppers(GameTestHelper helper) {
        BlockPos hopperPos = new BlockPos(3, 2, 3);
        helper.setBlock(hopperPos, Blocks.HOPPER);
        BlockPos absoluteHopperPos = helper.absolutePos(hopperPos);
        HopperBlockEntity hopper = (HopperBlockEntity) helper.getLevel().getBlockEntity(absoluteHopperPos);
        helper.assertTrue(hopper != null, "test hopper block entity was not created");

        VillagerEntityMCA villager = spawnFisher(helper, absoluteHopperPos.north(2));
        ItemEntity protectedReel = new ItemEntity(
                helper.getLevel(),
                absoluteHopperPos.getX() + 0.5,
                absoluteHopperPos.getY() + 1.0,
                absoluteHopperPos.getZ() + 0.5,
                new ItemStack(Items.COD)
        );
        protectedReel.setThrower(villager);
        protectedReel.setTarget(villager.getUUID());
        protectedReel.setNeverPickUp();
        helper.getLevel().addFreshEntity(protectedReel);

        boolean collected = HopperBlockEntity.suckInItems(helper.getLevel(), hopper);

        helper.assertTrue(!collected, "hopper accepted a protected fishing reel item");
        helper.assertTrue(!protectedReel.isRemoved(), "hopper removed a protected fishing reel item");
        helper.assertTrue(hopper.isEmpty(), "hopper inventory received a protected fishing reel item");

        protectedReel.setTarget(null);
        protectedReel.setNoPickUpDelay();
        helper.assertTrue(
                HopperBlockEntity.suckInItems(helper.getLevel(), hopper),
                "hopper could not collect the reel item after MCA protection was released"
        );
        helper.assertTrue(protectedReel.isRemoved(), "released reel item was not consumed by the hopper");
        helper.succeed();
    }

    @GameTest(
            batch = "mca_fishing_lure",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
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
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 800
    )
    public static void rainAdvancesFishingCountdownLikeVanilla(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        BlockPos water = villagerPos.east(2);
        prepareBiteWater(helper, water);
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        MCAFishingBobberEntity bobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);
        tickUntilBobbing(helper, bobber);
        long rainSeed = findSeedForRainRoll(bobber);

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
                "rain timing fixture was not actually raining at the bobber"
        );
        setFishingCountdown(bobber, "timeUntilLured", 2);
        bobber.getRandom().setSeed(rainSeed);
        invokeCatchingFish(bobber);
        helper.assertTrue(
                getFishingCountdown(bobber, "timeUntilLured") <= 0,
                "rain did not apply vanilla's extra fishing-countdown tick"
        );
        helper.succeed();
    }

    @GameTest(
            batch = "mca_fishing_luck",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air"
    )
    public static void luckOfTheSeaContributesToFishingLootLuck(GameTestHelper helper) {
        BlockPos villagerPos = biteCycleVillagerPos(helper);
        prepareBiteWater(helper, villagerPos.east(2));
        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL));
        long gameTime = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), villager, gameTime);
        task.tick(helper.getLevel(), villager, gameTime);

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

    @GameTest(
            batch = "mca_fishing_reaction",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
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
            MCAFishingBobberEntity bobberBeforeTick = activeBobber(helper, villager);
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

    @GameTest(batch = "mca_fishing_recovery", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void stoppingFishingDuringRecoveryReturnsFoodAndClearsRod(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
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

        int startingInventoryRods = villager.getInventory().countItem(Items.FISHING_ROD);
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
                villager.getInventory().countItem(Items.FISHING_ROD) == startingInventoryRods,
                "fishing hand takeover duplicated the inventory fishing rod during recovery"
        );
        helper.assertTrue(
                villager.getItemInHand(villager.getDominantHand()).isEmpty(),
                "stopping fishing restored a rod after the chore had ended"
        );
        helper.assertTrue(
                inventoryRod.getDamageValue() == 34,
                "stopping fishing changed the inventory fishing rod during recovery cleanup"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_fishing_head_tracking", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fishingKeepsLookingAtWaterAcrossLookSinkLifecycle(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
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

    @GameTest(batch = "mca_chore_look_lifecycle", templateNamespace = "minecraft", template = "bastion/blocks/air")
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
                .filter(entity -> !entity.isRemoved() && entity.getVillagerOwner() == villager)
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
            if (isFishingJunk(invokeFishingLoot(task, helper, villager))) {
                junk++;
            }
        }
        return junk;
    }

    private static ItemStack invokeFishingLoot(
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
            return (ItemStack) method.invoke(task, helper.getLevel(), villager);
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
