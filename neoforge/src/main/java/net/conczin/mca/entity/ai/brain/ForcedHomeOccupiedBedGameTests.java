package net.conczin.mca.entity.ai.brain;

import com.mojang.datafixers.util.Pair;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
@PrefixGameTestTemplate(false)
public final class ForcedHomeOccupiedBedGameTests {
    private ForcedHomeOccupiedBedGameTests() {
    }

    @GameTest(batch = "mca_forced_home_occupied_bed", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void occupiedForcedHomePausesBedMovementWithoutForgettingAssignment(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, head.relative(Direction.NORTH, 6));
        GlobalPos home = GlobalPos.of(helper.getLevel().dimension(), head);
        villager.getBrain().setMemory(MemoryModuleType.HOME, home);
        villager.getBrain().setMemory(MemoryModuleTypeMCA.FORCED_HOME, true);

        runRestMovementAndSleepTasks(helper, villager);
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                "free forced HOME did not publish producer-owned bed movement");

        VillagerEntityMCA sleeper = spawnVillager(helper, head.relative(Direction.SOUTH));
        sleeper.startSleeping(head);
        helper.assertTrue(sleeper.isSleeping(), "fixture villager did not occupy the forced HOME bed");
        helper.assertTrue(helper.getLevel().getBlockState(head).getValue(BedBlock.OCCUPIED),
                "fixture bed was not marked occupied");
        helper.assertTrue(home.dimension() == helper.getLevel().dimension(),
                "fixture HOME dimension was equal but not identical to the level key");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME)
                        .filter(Boolean::booleanValue).isPresent(),
                "fixture lost the FORCED_HOME marker before REST tasks ran");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.npc.villager.Villager.class,
                        new AABB(head), candidate -> candidate != villager && candidate.isSleeping()).isEmpty(),
                "fixture sleeper was not discoverable in the bed-head AABB");

        runRestMovementAndSleepTasks(helper, villager);

        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.HOME)
                        .filter(home::equals).isPresent(),
                "occupied forced HOME was forgotten");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME).isPresent(),
                "occupied forced HOME lost its FORCED_HOME marker");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                "occupied forced HOME retained or republished bed movement: "
                        + villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                        .map(target -> target.getClass().getSimpleName() + "/" + target.getTarget().getClass().getSimpleName())
                        .orElse("empty"));
        helper.assertTrue(!villager.isSleeping(),
                "villager slept in a forced HOME already occupied by another sleeper");

        sleeper.stopSleeping();
        helper.assertTrue(!helper.getLevel().getBlockState(head).getValue(BedBlock.OCCUPIED),
                "fixture bed stayed occupied after the sleeper left");
        villager.snapTo(foot.getX() - 5.5D, foot.getY(), foot.getZ() + 0.5D);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        runRestMovementAndSleepTasks(helper, villager);

        WalkTarget resumed = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElseThrow(() -> new AssertionError("free forced HOME did not resume bed movement"));
        helper.assertTrue(resumed.getTarget().currentBlockPosition().equals(head),
                "resumed forced HOME movement no longer targets the assigned bed");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.HOME)
                        .filter(home::equals).isPresent(),
                "resuming forced HOME movement changed the assignment");
        helper.succeed();
    }

    @GameTest(batch = "mca_forced_home_occupied_bed", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void forcedHomePausesForAnyRealSleepingOccupant(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, head.relative(Direction.NORTH, 6));
        GlobalPos home = GlobalPos.of(helper.getLevel().dimension(), head);
        villager.getBrain().setMemory(MemoryModuleType.HOME, home);
        villager.getBrain().setMemory(MemoryModuleTypeMCA.FORCED_HOME, true);

        runRestMovementAndSleepTasks(helper, villager);
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent(),
                "free forced HOME did not publish producer-owned bed movement");

        var sleeper = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, head.relative(Direction.SOUTH));
        sleeper.startSleeping(head);
        helper.assertTrue(sleeper.isSleeping(), "non-villager fixture did not occupy the forced HOME bed");

        runRestMovementAndSleepTasks(helper, villager);

        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.HOME)
                        .filter(home::equals).isPresent(),
                "forced HOME was forgotten while a real non-villager sleeper occupied it");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isEmpty(),
                "forced HOME retained movement while a real non-villager sleeper occupied it");
        helper.assertTrue(!villager.isSleeping(),
                "villager slept in a forced HOME occupied by a non-villager sleeper");

        sleeper.stopSleeping();
        helper.succeed();
    }

    private static void runRestMovementAndSleepTasks(GameTestHelper helper, VillagerEntityMCA villager) {
        long gameTime = helper.getLevel().getGameTime();
        for (Pair<Integer, ? extends BehaviorControl<? super VillagerEntityMCA>> entry
                : VillagerTasksMCA.getRestPackage(0.5F)) {
            if (entry.getFirst() <= 3) {
                entry.getSecond().tryStart(helper.getLevel(), villager, gameTime);
            }
        }
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos feet) {
        helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.setNoAi(true);
        return villager;
    }

    private static BlockPos placeBed(GameTestHelper helper, BlockPos foot, Direction facing) {
        BlockPos head = foot.relative(facing);
        helper.getLevel().setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(head.below(), Blocks.STONE.defaultBlockState(), 3);
        BlockState footState = Blocks.BED.pick(net.minecraft.world.item.DyeColor.RED).defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        helper.getLevel().setBlock(foot, footState, 3);
        helper.getLevel().setBlock(head, footState.setValue(BedBlock.PART, BedPart.HEAD), 3);
        return head;
    }
}
