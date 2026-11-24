package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.Chore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;
@PrefixGameTestTemplate(false)
public final class HarvestingTaskGameTests {
    private HarvestingTaskGameTests() {
    }

    @GameTest(batch = "mca_harvesting_priority", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void matureCropIsTargetedBeforeEmptyFarmland(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos crop = start.east(3);
        BlockPos emptyFarmland = start.west(2).below();
        prepareFarmland(helper, crop.below());
        prepareFarmland(helper, emptyFarmland);
        helper.getLevel().setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE), 3);

        var cropState = helper.getLevel().getBlockState(crop);
        helper.assertTrue(cropState.getBlock() instanceof CropBlock,
                "prepared crop is not a CropBlock: " + cropState);
        CropBlock cropBlock = (CropBlock) cropState.getBlock();
        helper.assertTrue(cropBlock.isMaxAge(cropState),
                "prepared crop is not mature: " + cropState);
        helper.assertTrue(cropState.canSurvive(helper.getLevel(), crop),
                "prepared mature crop cannot survive at " + crop + ": " + cropState);

        VillagerEntityMCA villager = spawnHarvester(helper, start);
        ServerPlayer assigner = connectedAssigner(villager);
        villager.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS));
        try {
            HarvestingTask task = new HarvestingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);
            task.tick(helper.getLevel(), villager, time);

            WalkTarget walkTarget = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            helper.assertTrue(walkTarget != null, "harvesting did not publish a walk target");
            BlockPos actualTarget = walkTarget.getTarget().currentBlockPosition();
            helper.assertTrue(
                    actualTarget.equals(crop),
                    "harvesting target was " + actualTarget + " instead of mature crop " + crop
                            + "; empty farmland was " + emptyFarmland
            );
        } finally {
            removeConnectedPlayerIfPresent(helper, assigner);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_harvesting_stems", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void harvestingLeavesGrowingStems(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos immaturePumpkinStem = start.north(2);
        BlockPos matureMelonStem = start.east();
        BlockPos attachedStem = start.south(3);
        BlockPos melon = attachedStem.south();
        BlockPos matureWheat = start.west(2);

        prepareFarmland(helper, immaturePumpkinStem.below());
        prepareFarmland(helper, matureMelonStem.below());
        prepareFarmland(helper, attachedStem.below());
        prepareMatureWheat(helper, matureWheat);
        helper.getLevel().setBlock(immaturePumpkinStem,
                Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE, 0), 3);
        helper.getLevel().setBlock(matureMelonStem,
                Blocks.MELON_STEM.defaultBlockState().setValue(StemBlock.AGE, 7), 3);
        helper.getLevel().setBlock(melon, Blocks.MELON.defaultBlockState(), 3);
        helper.getLevel().setBlock(attachedStem,
                Blocks.ATTACHED_MELON_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.SOUTH), 3);

        VillagerEntityMCA villager = spawnHarvester(helper, start);
        ServerPlayer assigner = connectedAssigner(villager);
        villager.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
        HarvestingTask task = new HarvestingTask();
        long time = helper.getLevel().getGameTime();
        try {
            task.start(helper.getLevel(), villager, time);
            helper.assertTrue(task.canStillUse(helper.getLevel(), villager, time),
                    "fixture did not find a harvestable crop");
            setIntField(task, "workingTick", 40);
            task.tick(helper.getLevel(), villager, time);

            helper.assertTrue(helper.getLevel().getBlockState(immaturePumpkinStem).is(Blocks.PUMPKIN_STEM)
                            && helper.getLevel().getBlockState(immaturePumpkinStem).getValue(StemBlock.AGE) == 0,
                    "immature pumpkin stem was destroyed or altered");
            helper.assertTrue(helper.getLevel().getBlockState(matureMelonStem).is(Blocks.MELON_STEM)
                            && helper.getLevel().getBlockState(matureMelonStem).getValue(StemBlock.AGE) == 7,
                    "mature melon stem was destroyed or altered");
            helper.assertTrue(helper.getLevel().getBlockState(attachedStem).is(Blocks.ATTACHED_MELON_STEM)
                            && helper.getLevel().getBlockState(melon).is(Blocks.MELON),
                    "attached stem or its melon fruit was damaged");
            helper.assertTrue(helper.getLevel().getBlockState(matureWheat).is(Blocks.WHEAT)
                            && helper.getLevel().getBlockState(matureWheat).getValue(CropBlock.AGE) == 0,
                    "mature wheat was not harvested and replanted");
        } finally {
            removeConnectedPlayerIfPresent(helper, assigner);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_harvesting_overflow_full", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fullInventoryDropsHarvestedWheatAtCrop(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos crop = start.east();
        prepareMatureWheat(helper, crop);
        VillagerEntityMCA villager = spawnHarvester(helper, start);
        ServerPlayer assigner = connectedAssigner(villager);
        fillCropInventory(villager, -1);

        try {
            harvestPreparedCrop(helper, villager, crop);
            helper.assertTrue(helper.getLevel().getBlockState(crop).is(Blocks.WHEAT)
                            && helper.getLevel().getBlockState(crop).getValue(CropBlock.AGE) == 0,
                    "harvested wheat was not replanted with a seed");
            helper.assertTrue(inventoryWheatCount(villager) == 0,
                    "full inventory unexpectedly accepted harvested wheat");
            helper.assertTrue(droppedWheatCount(helper, crop) == 1,
                    "full inventory lost its guaranteed wheat instead of dropping at the crop");
        } finally {
            discardWheatDrops(helper, crop);
            removeConnectedPlayerIfPresent(helper, assigner);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_harvesting_overflow_partial", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void nearFullInventoryPreservesSecondWheatAtSecondCrop(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos firstCrop = start.east();
        BlockPos secondCrop = start.south();
        prepareMatureWheat(helper, firstCrop);
        prepareMatureWheat(helper, secondCrop);
        VillagerEntityMCA villager = spawnHarvester(helper, start);
        ServerPlayer assigner = connectedAssigner(villager);
        fillCropInventory(villager, 63);

        try {
            HarvestingTask task = new HarvestingTask();
            task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
            harvestPreparedCrop(helper, villager, task, firstCrop);
            harvestPreparedCrop(helper, villager, task, secondCrop);

            helper.assertTrue(helper.getLevel().getBlockState(firstCrop).is(Blocks.WHEAT)
                            && helper.getLevel().getBlockState(firstCrop).getValue(CropBlock.AGE) == 0
                            && helper.getLevel().getBlockState(secondCrop).is(Blocks.WHEAT)
                            && helper.getLevel().getBlockState(secondCrop).getValue(CropBlock.AGE) == 0,
                    "harvest overflow control failed to replant both crops");
            helper.assertTrue(inventoryWheatCount(villager) == 64,
                    "near-full inventory did not accept exactly one wheat");
            helper.assertTrue(droppedWheatCount(helper, firstCrop) == 0
                            && droppedWheatCount(helper, secondCrop) == 1,
                    "overflow wheat was lost or spawned away from its source crop");
        } finally {
            discardWheatDrops(helper, firstCrop);
            discardWheatDrops(helper, secondCrop);
            removeConnectedPlayerIfPresent(helper, assigner);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_harvesting_overflow_rule", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void doBlockDropsFalseSuppressesHarvestOverflow(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos crop = start.east();
        prepareMatureWheat(helper, crop);
        VillagerEntityMCA villager = spawnHarvester(helper, start);
        ServerPlayer assigner = connectedAssigner(villager);
        fillCropInventory(villager, -1);
        var rule = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS);
        boolean previous = rule.get();

        try {
            rule.set(false, helper.getLevel().getServer());
            harvestPreparedCrop(helper, villager, crop);
            helper.assertTrue(droppedWheatCount(helper, crop) == 0,
                    "harvesting emitted overflow with doBlockDrops=false");
        } finally {
            rule.set(previous, helper.getLevel().getServer());
            discardWheatDrops(helper, crop);
            removeConnectedPlayerIfPresent(helper, assigner);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_harvesting_cancel_approach", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void disconnectedAssignerStopsApproachBeforeNewMovement(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos distantCrop = start.east(7);
        prepareMatureWheat(helper, distantCrop);
        VillagerEntityMCA villager = spawnHarvester(helper, start);
        villager.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
        ServerPlayer assigner = connectedAssigner(villager);

        try {
            HarvestingTask task = new HarvestingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);
            task.tick(helper.getLevel(), villager, time);
            helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "fixture villager never began approaching the crop");
            helper.assertTrue(helper.getLevel().getBlockState(distantCrop).getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    "fixture harvested its distant crop before disconnect");

            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            disconnectAssigner(helper, villager, assigner);
            task.tick(helper.getLevel(), villager, time + 1);

            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "absent assigner caused a new harvesting movement target");
            helper.assertTrue(helper.getLevel().getBlockState(distantCrop).is(Blocks.WHEAT)
                            && helper.getLevel().getBlockState(distantCrop).getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    "harvest mutated a crop during missing-assigner cancellation");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.NONE,
                    "harvesting kept the assignment after its player disappeared");
        } finally {
            removeConnectedPlayerIfPresent(helper, assigner);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_harvesting_cancel_work", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void disconnectedAssignerStopsCropActionAndConsumption(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos crop = start.east();
        prepareMatureWheat(helper, crop);
        VillagerEntityMCA villager = spawnHarvester(helper, start);
        villager.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS, 8));
        ServerPlayer assigner = connectedAssigner(villager);

        try {
            HarvestingTask task = new HarvestingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);
            helper.assertTrue(task.canStillUse(helper.getLevel(), villager, time),
                    "fixture did not prepare harvestable wheat");
            setIntField(task, "workingTick", 40);
            int seedsBefore = inventorySeedCount(villager);
            disconnectAssigner(helper, villager, assigner);

            task.tick(helper.getLevel(), villager, time + 1);

            helper.assertTrue(helper.getLevel().getBlockState(crop).is(Blocks.WHEAT)
                            && helper.getLevel().getBlockState(crop).getValue(CropBlock.AGE) == CropBlock.MAX_AGE,
                    "absent assigner harvested or replanted wheat on its work tick");
            helper.assertTrue(inventorySeedCount(villager) == seedsBefore && inventoryWheatCount(villager) == 0,
                    "absent assigner consumed seeds or generated wheat in inventory");
            helper.assertTrue(droppedWheatCount(helper, crop) == 0,
                    "absent assigner generated harvested items in the world");
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "absent assigner issued a new work movement target");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.NONE,
                    "harvesting assignment was not abandoned after disconnect");
        } finally {
            removeConnectedPlayerIfPresent(helper, assigner);
            discardWheatDrops(helper, crop);
            villager.discard();
        }
        helper.succeed();
    }

    private static ServerPlayer assignConnectedHarvester(GameTestHelper helper, VillagerEntityMCA villager) {
        ServerPlayer assigner = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "harvest-assigner"), ClientInformation.createDefault());
        assigner.setPos(villager.getX(), villager.getY(), villager.getZ());
        // getJobAssigner resolves the level's player list. Avoid registering a connectionless
        // fixture with ChunkMap, which would try to send tracking packets on a later tick.
        helper.getLevel().players().add(assigner);
        villager.getVillagerBrain().assignJob(Chore.HARVEST, assigner);
        helper.assertTrue(villager.getVillagerBrain().getJobAssigner().orElse(null) == assigner,
                "fixture assigning player is not tracked in the villager's level");
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        return assigner;
    }

    private static ServerPlayer connectedAssigner(VillagerEntityMCA villager) {
        return (ServerPlayer) villager.getVillagerBrain().getJobAssigner().orElseThrow();
    }

    private static void disconnectAssigner(GameTestHelper helper, VillagerEntityMCA villager, ServerPlayer assigner) {
        helper.getLevel().players().remove(assigner);
        helper.assertTrue(villager.getVillagerBrain().getJobAssigner().isEmpty(),
                "fixture assigning player remained visible after disconnect");
    }

    private static void removeConnectedPlayerIfPresent(GameTestHelper helper, ServerPlayer assigner) {
        if (helper.getLevel().getPlayerByUUID(assigner.getUUID()) == assigner) {
            helper.getLevel().players().remove(assigner);
        }
    }

    private static int inventorySeedCount(VillagerEntityMCA villager) {
        var inventory = villager.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.WHEAT_SEEDS)) {
                count += inventory.getItem(slot).getCount();
            }
        }
        return count;
    }

    private static void harvestPreparedCrop(GameTestHelper helper, VillagerEntityMCA villager, BlockPos crop) {
        HarvestingTask task = new HarvestingTask();
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        harvestPreparedCrop(helper, villager, task, crop);
    }

    private static void harvestPreparedCrop(GameTestHelper helper, VillagerEntityMCA villager,
                                            HarvestingTask task, BlockPos crop) {
        setObjectField(task, "currentPos", crop);
        setIntField(task, "workingTick", 40);
        task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());
    }

    private static void fillCropInventory(VillagerEntityMCA villager, int wheatCount) {
        var inventory = villager.getInventory();
        for (int slot = 1; slot < inventory.getContainerSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        inventory.setItem(1, new ItemStack(Items.WHEAT_SEEDS, 8));
        if (wheatCount > 0) {
            inventory.setItem(inventory.getContainerSize() - 1, new ItemStack(Items.WHEAT, wheatCount));
        }
    }

    private static int inventoryWheatCount(VillagerEntityMCA villager) {
        var inventory = villager.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.WHEAT)) {
                count += inventory.getItem(slot).getCount();
            }
        }
        return count;
    }

    private static int droppedWheatCount(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(0.2),
                        drop -> drop.getItem().is(Items.WHEAT) && drop.blockPosition().equals(pos))
                .stream().mapToInt(drop -> drop.getItem().getCount()).sum();
    }

    private static void discardWheatDrops(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(0.2),
                        drop -> drop.getItem().is(Items.WHEAT) && drop.blockPosition().equals(pos))
                .forEach(ItemEntity::discard);
    }

    private static void setObjectField(HarvestingTask task, String name, Object value) {
        try {
            Field field = HarvestingTask.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(task, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not prepare harvesting field " + name, exception);
        }
    }

    @GameTest(batch = "mca_harvesting_seedless", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void emptyFarmlandIsIgnoredWithoutSeeds(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareFarmland(helper, start.east(2).below());

        VillagerEntityMCA villager = spawnFarmlandOnlyHarvester(helper, start);
        ServerPlayer assigner = connectedAssigner(villager);
        try {
            HarvestingTask task = new HarvestingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);

            helper.assertTrue(
                    !task.canStillUse(helper.getLevel(), villager, time),
                    "harvesting kept an empty-farmland target even though the villager had no seeds"
            );
        } finally {
            removeConnectedPlayerIfPresent(helper, assigner);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_harvesting_planting", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void emptyFarmlandIsTargetedWithSeeds(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos emptyFarmland = start.east(2).below();
        prepareFarmland(helper, emptyFarmland);

        VillagerEntityMCA villager = spawnFarmlandOnlyHarvester(helper, start);
        ServerPlayer assigner = connectedAssigner(villager);
        villager.getInventory().addItem(new ItemStack(Items.WHEAT_SEEDS));
        try {
            HarvestingTask task = new HarvestingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);
            task.tick(helper.getLevel(), villager, time);

            WalkTarget walkTarget = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            helper.assertTrue(walkTarget != null, "harvesting did not publish a walk target for plantable farmland");
            helper.assertTrue(
                    walkTarget.getTarget().currentBlockPosition().equals(emptyFarmland),
                    "harvesting did not target empty farmland when seeds were available"
            );
        } finally {
            removeConnectedPlayerIfPresent(helper, assigner);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_harvesting_interruption", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void interruptedCropProgressDoesNotCarryToNextTarget(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareMatureWheat(helper, start.east());

        VillagerEntityMCA villager = spawnHarvester(helper, start);
        ServerPlayer assigner = connectedAssigner(villager);
        try {
            HarvestingTask task = new HarvestingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);
            setIntField(task, "workingTick", 35);
            task.stop(helper.getLevel(), villager, time + 1);
            helper.assertTrue(
                    getIntField(task, "workingTick") == 0,
                    "stopping harvesting did not reset interrupted work progress"
            );
        } finally {
            removeConnectedPlayerIfPresent(helper, assigner);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_harvesting_bonemeal_swap", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void bonemealFromInventoryGrowsImmatureCropWhileHoeWasHeld(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos crop = start.east();
        prepareFarmland(helper, crop.below());
        helper.getLevel().setBlock(crop, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0), 3);

        VillagerEntityMCA villager = spawnHarvester(helper, start);
        ServerPlayer assigner = connectedAssigner(villager);
        villager.getInventory().addItem(new ItemStack(Items.BONE_MEAL, 4));
        try {
            HarvestingTask task = new HarvestingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);
            // Simulate arriving at an immature crop while the main hand still holds the hoe.
            ItemStack hoe = villager.getInventory().getItem(0);
            villager.setItemInHand(villager.getDominantHand(), hoe);
            setObjectField(task, "currentPos", crop);
            setIntField(task, "workingTick", 40);

            task.tick(helper.getLevel(), villager, time + 1);

            int ageAfter = helper.getLevel().getBlockState(crop).getValue(CropBlock.AGE);
            int remainingBoneMeal = inventoryItemCount(villager, Items.BONE_MEAL);
            helper.assertTrue(ageAfter > 0,
                    "bonemeal swapped from inventory did not grow the immature wheat; age=" + ageAfter);
            helper.assertTrue(remainingBoneMeal == 3,
                    "bonemeal growth did not consume exactly one bonemeal from inventory; remaining=" + remainingBoneMeal);
        } finally {
            removeConnectedPlayerIfPresent(helper, assigner);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_harvesting_fortune_hoe", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fortuneHoeAppliesToolLootContextToMatureCropHarvest(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos crop = start.east();
        prepareFarmland(helper, crop.below());

        VillagerEntityMCA villager = spawnHarvester(helper, start);
        ServerPlayer assigner = connectedAssigner(villager);
        ItemStack fortuneHoe = new ItemStack(Items.IRON_HOE);
        var enchantments = helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        fortuneHoe.enchant(enchantments.getOrThrow(Enchantments.FORTUNE), 3);
        villager.getInventory().setItem(0, fortuneHoe);

        try {
            HarvestingTask task = new HarvestingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == fortuneHoe,
                    "harvesting did not equip the Fortune III hoe");

            // Without LootContextParams.TOOL, mature carrots yield 1 + Binomial(3, 4/7) <= 4 net carrots after replant.
            // With Fortune III, 1 + Binomial(6, 4/7) yields >= 5 on ~48.5% of harvests.
            int maxSingleHarvestNetGain = 0;
            for (int i = 0; i < 32; i++) {
                helper.getLevel().setBlock(crop,
                        Blocks.CARROTS.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE), 3);
                int before = inventoryItemCount(villager, Items.CARROT);
                harvestPreparedCrop(helper, villager, task, crop);
                int gain = inventoryItemCount(villager, Items.CARROT) - before;
                maxSingleHarvestNetGain = Math.max(maxSingleHarvestNetGain, gain);
            }
            helper.assertTrue(maxSingleHarvestNetGain >= 5,
                    "Fortune III hoe never exceeded unenchanted max net carrot yield (4); maxGain="
                            + maxSingleHarvestNetGain);
        } finally {
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(crop).inflate(1.0))
                    .forEach(ItemEntity::discard);
            removeConnectedPlayerIfPresent(helper, assigner);
            villager.discard();
        }
        helper.succeed();
    }

    private static int inventoryItemCount(VillagerEntityMCA villager, Item item) {
        var inventory = villager.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void setIntField(HarvestingTask task, String name, int value) {
        try {
            Field field = HarvestingTask.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(task, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not prepare harvesting field " + name, exception);
        }
    }

    private static int getIntField(HarvestingTask task, String name) {
        try {
            Field field = HarvestingTask.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(task);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not inspect harvesting field " + name, exception);
        }
    }

    private static VillagerEntityMCA spawnHarvester(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().getChunk(pos);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Harvesting Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.tickCount = 20;
        villager.getInventory().addItem(new ItemStack(Items.IRON_HOE));
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        assignConnectedHarvester(helper, villager);
        // Cropping tests should not depend on the GameTest world's current
        // day time; REST activity deliberately suppresses all chore tasks.
        villager.getBrain().setSchedule(new ScheduleBuilder(new Schedule())
                .changeActivityAt(0, Activity.IDLE).build());
        villager.getVillagerBrain().think();
        helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.CHORE),
                "harvesting fixture failed to activate its assigned chore");
        return villager;
    }

    private static VillagerEntityMCA spawnFarmlandOnlyHarvester(GameTestHelper helper, BlockPos pos) {
        VillagerEntityMCA villager = spawnHarvester(helper, pos);
        // HarvestingTask makes the farmland scan eligible before its crop scan.
        // Use that window so these tests isolate seed gating from crops in neighboring GameTest structures.
        villager.tickCount = 1;
        return villager;
    }

    private static void prepareFarmland(GameTestHelper helper, BlockPos farmland) {
        helper.getLevel().setBlock(farmland.below(), Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(farmland, Blocks.FARMLAND.defaultBlockState(), 3);
        helper.getLevel().setBlock(farmland.above(), Blocks.AIR.defaultBlockState(), 3);
    }

    private static void prepareMatureWheat(GameTestHelper helper, BlockPos crop) {
        prepareFarmland(helper, crop.below());
        helper.getLevel().setBlock(
                crop,
                Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, CropBlock.MAX_AGE),
                3
        );
    }
}
