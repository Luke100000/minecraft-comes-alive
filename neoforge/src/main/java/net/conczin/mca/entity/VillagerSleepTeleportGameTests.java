package net.conczin.mca.entity;

import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Objects;
import java.util.Set;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class VillagerSleepTeleportGameTests {
    private VillagerSleepTeleportGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void runningSleepBehaviorDetachesOnNextEntityTickAfterCommandTeleport(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setPos(head.getX() + 0.5D, head.getY() + 0.6875D, head.getZ() + 0.5D);
        helper.getLevel().addFreshEntity(villager);
        helper.getLevel().tickNonPassenger(villager);

        villager.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), head));
        villager.getBrain().setActiveActivityIfPossible(Activity.REST);
        villager.getBrain().tick(helper.getLevel(), villager);
        helper.assertTrue(villager.isSleeping(), "MCA brain did not start SleepInBed at HOME");

        BlockPos destination = helper.absolutePos(new BlockPos(6, 1, 6));
        int tickCountBeforeTeleport = villager.tickCount;
        helper.assertTrue(villager.teleportTo(
                        helper.getLevel(),
                        destination.getX() + 0.5D,
                        destination.getY(),
                        destination.getZ() + 0.5D,
                        Set.<RelativeMovement>of(),
                        villager.getYRot(),
                        villager.getXRot()),
                "command-style teleport failed");
        helper.assertTrue(villager.isSleeping(), "command teleport detached sleep state before normal AI reconciliation");
        helper.assertTrue(helper.getLevel().getBlockState(head).getValue(BedBlock.OCCUPIED),
                "command teleport cleared the old bed before normal AI reconciliation");
        helper.assertTrue(destination.closerToCenterThan(villager.position(), 1.0D),
                "command teleport did not leave the villager at its destination");

        helper.getLevel().tickNonPassenger(villager);
        helper.assertTrue(villager.tickCount == tickCountBeforeTeleport + 1,
                "fixture did not execute exactly one entity tick after teleport");
        helper.assertTrue(!villager.isSleeping(),
                "first villager tick did not detach stale sleep state");
        helper.assertTrue(!helper.getLevel().getBlockState(head).getValue(BedBlock.OCCUPIED),
                "first villager tick left the old bed occupied");
        helper.assertTrue(destination.closerToCenterThan(villager.position(), 1.0D),
                "sleep reconciliation moved the villager back to the old bed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void orphanedSleepingStateDetachesOnNextEntityTickAfterCommandTeleport(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setPos(head.getX() + 0.5D, head.getY() + 0.6875D, head.getZ() + 0.5D);
        helper.getLevel().addFreshEntity(villager);
        helper.getLevel().tickNonPassenger(villager);

        villager.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), head));
        villager.getBrain().setActiveActivityIfPossible(Activity.REST);

        // Simulate a restored sleeping entity whose freshly rebuilt brain does not have
        // the SleepInBed behavior running yet.
        villager.startSleeping(head);
        helper.assertTrue(villager.isSleeping(), "fixture villager did not start sleeping");

        BlockPos destination = helper.absolutePos(new BlockPos(6, 1, 6));
        int tickCountBeforeTeleport = villager.tickCount;
        helper.assertTrue(villager.teleportTo(
                        helper.getLevel(),
                        destination.getX() + 0.5D,
                        destination.getY(),
                        destination.getZ() + 0.5D,
                        Set.<RelativeMovement>of(),
                        villager.getYRot(),
                        villager.getXRot()),
                "command-style teleport failed");
        helper.assertTrue(villager.isSleeping(), "command teleport detached orphaned sleep state before normal AI reconciliation");
        helper.assertTrue(helper.getLevel().getBlockState(head).getValue(BedBlock.OCCUPIED),
                "command teleport cleared the orphaned sleeper's old bed before normal AI reconciliation");
        helper.assertTrue(destination.closerToCenterThan(villager.position(), 1.0D),
                "command teleport did not leave the orphaned sleeper at its destination");

        helper.getLevel().tickNonPassenger(villager);
        helper.assertTrue(villager.tickCount == tickCountBeforeTeleport + 1,
                "fixture did not execute exactly one entity tick after teleport");
        helper.assertTrue(!villager.isSleeping(),
                "first villager tick did not detach orphaned sleep state");
        helper.assertTrue(!helper.getLevel().getBlockState(head).getValue(BedBlock.OCCUPIED),
                "first villager tick left the orphaned sleeper's old bed occupied");
        helper.assertTrue(destination.closerToCenterThan(villager.position(), 1.0D),
                "orphaned sleep reconciliation moved the villager back to the old bed");
        helper.succeed();
    }

    private static BlockPos placeBed(GameTestHelper helper, BlockPos foot, Direction facing) {
        BlockPos head = foot.relative(facing);
        helper.getLevel().setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(head.below(), Blocks.STONE.defaultBlockState(), 3);
        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        helper.getLevel().setBlock(foot, footState, Block.UPDATE_CLIENTS);
        helper.getLevel().setBlock(head, footState.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_CLIENTS);
        return head;
    }
}
