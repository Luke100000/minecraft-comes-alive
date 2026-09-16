package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Chore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;

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
