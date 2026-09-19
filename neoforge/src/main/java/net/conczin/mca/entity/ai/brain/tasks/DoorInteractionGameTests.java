package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class DoorInteractionGameTests {
    private DoorInteractionGameTests() {
    }

    @GameTest(batch = "mca_door_closing", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void villagerClosesAlreadyOpenDoorAfterPassingThrough(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(2, 2, 4));
        BlockPos beforeDoor = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos door = helper.absolutePos(new BlockPos(5, 2, 4));
        BlockPos afterDoor = helper.absolutePos(new BlockPos(6, 2, 4));
        BlockPos finalNode = helper.absolutePos(new BlockPos(7, 2, 4));

        BlockState lowerDoor = Blocks.OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.OPEN, true)
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        helper.getLevel().setBlock(door, lowerDoor, 3);
        helper.getLevel().setBlock(door.above(), lowerDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(villagerPos))
                .withName("Open Door Closing Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        Path path = new Path(List.of(
                node(beforeDoor),
                node(door),
                node(afterDoor),
                node(finalNode)
        ), finalNode, true);
        path.setNextNodeIndex(2);
        villager.getBrain().setMemory(MemoryModuleType.PATH, path);

        SmarterOpenDoorsTask task = new SmarterOpenDoorsTask();
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, gameTime),
                "door task did not run after the villager passed through the door");

        GlobalPos rememberedDoor = GlobalPos.of(helper.getLevel().dimension(), door);
        Set<GlobalPos> doorsToClose = villager.getBrain()
                .getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                .orElse(Set.of());
        helper.assertTrue(doorsToClose.contains(rememberedDoor),
                "already-open passed door was not remembered for closing");
        helper.assertTrue(helper.getLevel().getBlockState(door).getValue(DoorBlock.OPEN),
                "door closed before the active path cleared it");

        task.doStop(helper.getLevel(), villager, gameTime);
        path.setNextNodeIndex(3);
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, gameTime + 1),
                "door task did not rerun after advancing beyond the door");
        helper.assertTrue(!helper.getLevel().getBlockState(door).getValue(DoorBlock.OPEN),
                "already-open passed door was not closed after the path cleared it");

        villager.discard();
        helper.succeed();
    }

    private static Node node(BlockPos pos) {
        return new Node(pos.getX(), pos.getY(), pos.getZ());
    }
}
