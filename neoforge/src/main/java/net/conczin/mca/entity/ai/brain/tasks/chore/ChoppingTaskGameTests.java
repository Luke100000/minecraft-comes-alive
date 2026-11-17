package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Chore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
@PrefixGameTestTemplate(false)
public final class ChoppingTaskGameTests {
    private ChoppingTaskGameTests() {
    }

    @GameTest(batch = "mca_chopping_reach", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void distantTreeDoesNotAccumulateChoppingProgress(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos tree = start.east(7);
        helper.getLevel().setBlock(tree, Blocks.OAK_LOG.defaultBlockState(), 3);

        VillagerEntityMCA villager = spawnChopper(helper, start);
        ChoppingTask task = new ChoppingTask();
        long time = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), villager, time);
        setField(task, "targetTree", tree);
        setIntField(task, "targetTreeTicks", 1);

        task.tick(helper.getLevel(), villager, time);

        helper.assertTrue(
                helper.getLevel().getBlockState(tree).is(Blocks.OAK_LOG),
                "chopping destroyed a tree before the villager reached working distance"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_chopping_overflow_full", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fullInventoryDropsChoppedLogAtItsSource(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos log = start.east();
        helper.getLevel().setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 3);
        VillagerEntityMCA villager = spawnChopper(helper, start);
        fillInventory(villager, -1);

        try {
            destroyPreparedLogs(helper, villager, log);

            helper.assertTrue(helper.getLevel().getBlockState(log).isAir(), "log was not chopped");
            helper.assertTrue(inventoryLogCount(villager) == 0, "full inventory unexpectedly accepted a log");
            helper.assertTrue(droppedLogCount(helper, log) == 1,
                    "full inventory did not drop its generated log at the destroyed block");
        } finally {
            discardLogDrops(helper, log);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chopping_overflow_partial", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void nearFullInventoryPreservesSecondLogAtSecondSource(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos lowerLog = start.east();
        BlockPos upperLog = lowerLog.above();
        helper.getLevel().setBlock(lowerLog, Blocks.OAK_LOG.defaultBlockState(), 3);
        helper.getLevel().setBlock(upperLog, Blocks.OAK_LOG.defaultBlockState(), 3);
        VillagerEntityMCA villager = spawnChopper(helper, start);
        fillInventory(villager, 63);

        try {
            destroyPreparedLogs(helper, villager, lowerLog);

            helper.assertTrue(helper.getLevel().getBlockState(lowerLog).isAir()
                            && helper.getLevel().getBlockState(upperLog).isAir(),
                    "prepared two-log tree was not chopped completely");
            helper.assertTrue(inventoryLogCount(villager) == 64,
                    "near-full inventory did not accept exactly one log");
            helper.assertTrue(droppedLogCount(helper, lowerLog) == 0
                            && droppedLogCount(helper, upperLog) == 1,
                    "overflow log was lost or dropped at the next/previous log position");
        } finally {
            discardLogDrops(helper, lowerLog);
            discardLogDrops(helper, upperLog);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chopping_overflow_rule", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void doBlockDropsFalseSuppressesChoppingOverflow(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos log = start.east();
        helper.getLevel().setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 3);
        VillagerEntityMCA villager = spawnChopper(helper, start);
        fillInventory(villager, -1);
        var dropsRule = helper.getLevel().getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS);
        boolean previous = dropsRule.get();

        try {
            dropsRule.set(false, helper.getLevel().getServer());
            destroyPreparedLogs(helper, villager, log);
            helper.assertTrue(droppedLogCount(helper, log) == 0,
                    "chopping emitted overflow with doBlockDrops=false");
        } finally {
            dropsRule.set(previous, helper.getLevel().getServer());
            discardLogDrops(helper, log);
            villager.discard();
        }
        helper.succeed();
    }

    private static void destroyPreparedLogs(GameTestHelper helper, VillagerEntityMCA villager, BlockPos log) {
        ChoppingTask task = new ChoppingTask();
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
        try {
            var destroyTree = ChoppingTask.class.getDeclaredMethod("destroyTree", net.minecraft.server.level.ServerLevel.class,
                    BlockPos.class);
            destroyTree.setAccessible(true);
            destroyTree.invoke(task, helper.getLevel(), log);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not execute chopping on prepared tree", exception);
        }
    }

    private static void fillInventory(VillagerEntityMCA villager, int logCount) {
        var inventory = villager.getInventory();
        for (int slot = 1; slot < inventory.getContainerSize(); slot++) {
            inventory.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        if (logCount > 0) {
            inventory.setItem(inventory.getContainerSize() - 1, new ItemStack(Items.OAK_LOG, logCount));
        }
    }

    private static int inventoryLogCount(VillagerEntityMCA villager) {
        var inventory = villager.getInventory();
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            if (inventory.getItem(slot).is(Items.OAK_LOG)) {
                count += inventory.getItem(slot).getCount();
            }
        }
        return count;
    }

    private static int droppedLogCount(GameTestHelper helper, BlockPos pos) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(0.2),
                        entity -> entity.getItem().is(Items.OAK_LOG))
                .stream().mapToInt(item -> item.getItem().getCount()).sum();
    }

    private static void discardLogDrops(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(0.2),
                        entity -> entity.getItem().is(Items.OAK_LOG))
                .forEach(ItemEntity::discard);
    }

    private static void setField(ChoppingTask task, String name, Object value) {
        try {
            Field field = ChoppingTask.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(task, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not prepare chopping field " + name, exception);
        }
    }

    private static void setIntField(ChoppingTask task, String name, int value) {
        try {
            Field field = ChoppingTask.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setInt(task, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not prepare chopping field " + name, exception);
        }
    }

    private static VillagerEntityMCA spawnChopper(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().getChunk(pos);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Chopping Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getInventory().addItem(new ItemStack(Items.IRON_AXE));
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getVillagerBrain().assignJob(Chore.CHOP, helper.makeMockPlayer(GameType.SURVIVAL));
        return villager;
    }
}
