package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.gametest.GameTestBrain;
import com.mojang.authlib.GameProfile;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

import java.util.UUID;

@GameTestHolder("minecraft")
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
        var dropsRule = helper.getLevel().getGameRules();
        boolean previous = dropsRule.get(GameRules.BLOCK_DROPS);

        try {
            dropsRule.set(GameRules.BLOCK_DROPS, false, helper.getLevel().getServer());
            destroyPreparedLogs(helper, villager, log);
            helper.assertTrue(droppedLogCount(helper, log) == 0,
                    "chopping emitted overflow with doBlockDrops=false");
        } finally {
            dropsRule.set(GameRules.BLOCK_DROPS, previous, helper.getLevel().getServer());
            discardLogDrops(helper, log);
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chopping_immediate_walk", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void discoveredTreeStartsWalkingImmediatelyWithoutFailureCooldown(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos tree = start.east(5);
        placeValidOakTree(helper, tree);

        ConnectedChopper fixture = spawnConnectedChopper(helper, start);
        VillagerEntityMCA villager = fixture.villager();
        try {
            ChoppingTask task = new ChoppingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);
            task.tick(helper.getLevel(), villager, time);

            WalkTarget walkTarget = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            helper.assertTrue(task.failedTicks == 0,
                    "discovering a valid tree set a false failure cooldown: failedTicks=" + task.failedTicks);
            helper.assertTrue(tree.equals(getField(task, "targetTree")),
                    "chopping did not select the nearby tree");
            helper.assertTrue(walkTarget != null && walkTarget.getTarget().currentBlockPosition().equals(tree),
                    "chopping did not publish movement to the discovered tree on the same tick");
        } finally {
            clearOakTree(helper, tree);
            fixture.close(helper);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chopping_reset_unreachable", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void stopAndWalkFailureClearTargetTreeAndSwitchOrBackOff(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(6, 2, 6));
        BlockPos blockedTree = start.east(4);
        BlockPos alternateTree = start.west(6);
        placeValidOakTree(helper, blockedTree);
        placeValidOakTree(helper, alternateTree);

        ConnectedChopper fixture = spawnConnectedChopper(helper, start);
        VillagerEntityMCA villager = fixture.villager();
        try {
            ChoppingTask task = new ChoppingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);
            task.tick(helper.getLevel(), villager, time);
            helper.assertTrue(blockedTree.equals(getField(task, "targetTree")),
                    "fixture did not initially pick the nearest tree");
            setIntField(task, "chopTicks", 12);

            task.stop(helper.getLevel(), villager, time + 1);
            helper.assertTrue(getField(task, "targetTree") == null
                            && getIntField(task, "chopTicks") == 0
                            && getIntField(task, "targetTreeTicks") == 0,
                    "stopping chopping left stale tree progress or target state");

            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            task.start(helper.getLevel(), villager, time + 2);
            task.tick(helper.getLevel(), villager, time + 2);
            helper.assertTrue(blockedTree.equals(getField(task, "targetTree")),
                    "restarted chopping did not reacquire the nearest tree");

            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            WalkTargetFailureMemory.record(villager, blockedTree, time + 3);
            task.tick(helper.getLevel(), villager, time + 3);
            helper.assertTrue(alternateTree.equals(getField(task, "targetTree")) && task.failedTicks == 0,
                    "walk failure on the first tree did not immediately switch to the reachable alternate tree");

            clearOakTree(helper, blockedTree);
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            WalkTargetFailureMemory.record(villager, alternateTree, time + 4);
            task.tick(helper.getLevel(), villager, time + 4);
            helper.assertTrue(getField(task, "targetTree") == null
                            && task.failedTicks == AbstractChoreTask.FAILED_COOLDOWN,
                    "walk failure on the last remaining tree did not clear targetTree and enter cooldown");
        } finally {
            clearOakTree(helper, blockedTree);
            clearOakTree(helper, alternateTree);
            fixture.close(helper);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chopping_two_unreachable_backoff", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void twoUnreachableTreesBackOffIntoCooldownInsteadOfPingPonging(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(6, 2, 6));
        BlockPos blockedTree1 = start.east(4);
        BlockPos blockedTree2 = start.west(6);
        BlockPos diagonalInRangeTree = start.offset(1, 0, 1);
        placeValidOakTree(helper, blockedTree1);
        placeValidOakTree(helper, blockedTree2);

        ConnectedChopper fixture = spawnConnectedChopper(helper, start);
        VillagerEntityMCA villager = fixture.villager();
        try {
            ChoppingTask task = new ChoppingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);
            task.tick(helper.getLevel(), villager, time);
            helper.assertTrue(blockedTree1.equals(getField(task, "targetTree")) && task.failedTicks == 0,
                    "fixture did not initially select the first distant tree");

            // First tree fails navigation -> task switches to second tree; switching walk target overwrites CANT_REACH_WALK_TARGET.
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            WalkTargetFailureMemory.record(villager, blockedTree1, time + 1);
            task.tick(helper.getLevel(), villager, time + 1);
            helper.assertTrue(blockedTree2.equals(getField(task, "targetTree")) && task.failedTicks == 0,
                    "first tree failure did not switch to the second candidate tree");
            WalkTargetFailureMemory.clearIfTargetChanged(villager, blockedTree2);

            // Second tree also fails navigation -> must NOT re-select blockedTree1; must enter FAILED_COOLDOWN.
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            WalkTargetFailureMemory.record(villager, blockedTree2, time + 2);
            task.tick(helper.getLevel(), villager, time + 2);
            helper.assertTrue(getField(task, "targetTree") == null
                            && task.failedTicks == AbstractChoreTask.FAILED_COOLDOWN,
                    "two unreachable trees ping-ponged instead of backing off into FAILED_COOLDOWN; target="
                            + getField(task, "targetTree") + ", failedTicks=" + task.failedTicks);

            // An already-in-range diagonal tree (distSqr = 2.0 <= 6.0, distManhattan = 2 > 1) must still be chopped.
            clearOakTree(helper, blockedTree1);
            clearOakTree(helper, blockedTree2);
            placeValidOakTree(helper, diagonalInRangeTree);
            task.stop(helper.getLevel(), villager, time + 3);
            task.failedTicks = 0;
            WalkTargetFailureMemory.clear(villager);
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
            task.start(helper.getLevel(), villager, time + 4);
            task.tick(helper.getLevel(), villager, time + 4);
            helper.assertTrue(diagonalInRangeTree.equals(getField(task, "targetTree")),
                    "did not select in-range diagonal tree");
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            WalkTargetFailureMemory.record(villager, diagonalInRangeTree, time + 5);
            task.tick(helper.getLevel(), villager, time + 5);
            helper.assertTrue(diagonalInRangeTree.equals(getField(task, "targetTree"))
                            && task.failedTicks == 0
                            && getIntField(task, "chopTicks") >= 2,
                    "in-range diagonal tree was prematurely aborted on Manhattan walk-target failure");
        } finally {
            clearOakTree(helper, blockedTree1);
            clearOakTree(helper, blockedTree2);
            clearOakTree(helper, diagonalInRangeTree);
            fixture.close(helper);
        }
        helper.succeed();
    }

    private static void placeValidOakTree(GameTestHelper helper, BlockPos baseLog) {
        helper.getLevel().setBlock(baseLog.below(), Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(baseLog, Blocks.OAK_LOG.defaultBlockState(), 3);
        helper.getLevel().setBlock(baseLog.above(), Blocks.OAK_LEAVES.defaultBlockState(), 3);
    }

    private static void clearOakTree(GameTestHelper helper, BlockPos baseLog) {
        helper.getLevel().setBlock(baseLog.above(), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(baseLog, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(baseLog.below(), Blocks.AIR.defaultBlockState(), 3);
    }

    private static Object getField(ChoppingTask task, String name) {
        try {
            Field field = ChoppingTask.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(task);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not inspect chopping field " + name, exception);
        }
    }

    private static int getIntField(ChoppingTask task, String name) {
        try {
            Field field = ChoppingTask.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getInt(task);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not inspect chopping field " + name, exception);
        }
    }

    private static ConnectedChopper spawnConnectedChopper(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().getChunk(pos);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Connected Chopping Probe")
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.getInventory().addItem(new ItemStack(Items.IRON_AXE));
        ServerPlayer assigner = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "chop-assigner"), ClientInformation.createDefault());
        helper.getLevel().players().add(assigner);
        villager.getVillagerBrain().assignJob(Chore.CHOP, assigner);
        villager.getBrain().setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
        villager.getVillagerBrain().think();
        helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.CHORE),
                "connected chopping fixture did not activate CHORE");
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        return new ConnectedChopper(villager, assigner);
    }

    private record ConnectedChopper(VillagerEntityMCA villager, ServerPlayer assigner) {
        private void close(GameTestHelper helper) {
            helper.getLevel().players().remove(assigner);
            villager.discard();
        }
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
                        entity -> entity.getItem().is(Items.OAK_LOG) && entity.blockPosition().equals(pos))
                .stream().mapToInt(item -> item.getItem().getCount()).sum();
    }

    private static void discardLogDrops(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                        new net.minecraft.world.phys.AABB(pos).inflate(0.2),
                        entity -> entity.getItem().is(Items.OAK_LOG) && entity.blockPosition().equals(pos))
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
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getInventory().addItem(new ItemStack(Items.IRON_AXE));
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getVillagerBrain().assignJob(Chore.CHOP, helper.makeMockPlayer(GameType.SURVIVAL));
        return villager;
    }
}
