package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.PathingBlockInteraction;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
@PrefixGameTestTemplate(false)
public final class DoorInteractionGameTests {
    private DoorInteractionGameTests() {
    }

    @GameTest(batch = "mca_any_door_config", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void anyDoorConfigAllowsIronDoorInteraction(GameTestHelper helper) {
        BlockPos doorPos = helper.absolutePos(new BlockPos(5, 2, 4));
        helper.getLevel().setBlock(doorPos, Blocks.IRON_DOOR.defaultBlockState(), 3);

        boolean oldAnyDoorConfig = Config.getInstance().villagersInteractWithAnyDoor;
        try {
            Config.getInstance().villagersInteractWithAnyDoor = false;
            helper.assertTrue(!PathingBlockInteraction.isOpenable(helper.getLevel().getBlockState(doorPos)),
                    "iron door should not be MCA-operable under the vanilla tag policy");

            Config.getInstance().villagersInteractWithAnyDoor = true;
            BlockState closed = helper.getLevel().getBlockState(doorPos);
            helper.assertTrue(PathingBlockInteraction.isOpenable(closed),
                    "any-door config should allow an arbitrary DoorBlock");
            helper.assertTrue(PathingBlockInteraction.setOpen(null, helper.getLevel(), closed, doorPos, true),
                    "any-door config should let MCA open the arbitrary DoorBlock");
            helper.assertTrue(helper.getLevel().getBlockState(doorPos).getValue(DoorBlock.OPEN),
                    "iron door stayed closed after MCA opened it");
            helper.succeed();
        } finally {
            Config.getInstance().villagersInteractWithAnyDoor = oldAnyDoorConfig;
        }
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

    @GameTest(batch = "mca_stacked_fence_gate_opening", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void villagerOpensStackedFenceGatesAlongPath(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(2, 2, 4));
        BlockPos beforeGate = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos lowerGate = helper.absolutePos(new BlockPos(5, 2, 4));
        BlockPos upperGate = lowerGate.above();
        BlockPos afterGate = helper.absolutePos(new BlockPos(6, 2, 4));
        BlockPos finalNode = helper.absolutePos(new BlockPos(7, 2, 4));

        helper.getLevel().setBlock(lowerGate, Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);
        helper.getLevel().setBlock(upperGate, Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(villagerPos))
                .withName("Stacked Fence Gate Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        Path path = new Path(List.of(
                node(beforeGate),
                node(lowerGate),
                node(afterGate),
                node(finalNode)
        ), finalNode, true);
        path.setNextNodeIndex(1);
        villager.getBrain().setMemory(MemoryModuleType.PATH, path);

        SmarterOpenDoorsTask task = new SmarterOpenDoorsTask();
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, gameTime),
                "door task did not run while approaching stacked fence gates");

        helper.assertTrue(helper.getLevel().getBlockState(lowerGate).getValue(BlockStateProperties.OPEN),
                "lower fence gate did not open");
        helper.assertTrue(helper.getLevel().getBlockState(upperGate).getValue(BlockStateProperties.OPEN),
                "upper fence gate did not open with the lower gate");

        Set<GlobalPos> gatesToClose = villager.getBrain()
                .getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                .orElse(Set.of());
        helper.assertTrue(gatesToClose.contains(GlobalPos.of(helper.getLevel().dimension(), lowerGate)),
                "lower fence gate was not remembered for closing");
        helper.assertTrue(gatesToClose.contains(GlobalPos.of(helper.getLevel().dimension(), upperGate)),
                "upper fence gate was not remembered for closing");

        task.doStop(helper.getLevel(), villager, gameTime);
        path.setNextNodeIndex(3);
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, gameTime + 1),
                "door task did not rerun after clearing stacked fence gates");
        helper.assertTrue(!helper.getLevel().getBlockState(lowerGate).getValue(BlockStateProperties.OPEN),
                "lower fence gate did not close after the path cleared it");
        helper.assertTrue(!helper.getLevel().getBlockState(upperGate).getValue(BlockStateProperties.OPEN),
                "upper fence gate did not close after the path cleared it");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_head_height_fence_gate_opening", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void villagerOpensHeadHeightFenceGateAlongPath(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(2, 2, 4));
        BlockPos beforeGate = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos gateColumn = helper.absolutePos(new BlockPos(5, 2, 4));
        BlockPos headHeightGate = gateColumn.above();
        BlockPos afterGate = helper.absolutePos(new BlockPos(6, 2, 4));
        BlockPos finalNode = helper.absolutePos(new BlockPos(7, 2, 4));

        helper.getLevel().setBlock(headHeightGate, Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(villagerPos))
                .withName("Head Height Fence Gate Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        Path path = new Path(List.of(
                node(beforeGate),
                node(gateColumn),
                node(afterGate),
                node(finalNode)
        ), finalNode, true);
        path.setNextNodeIndex(1);
        villager.getBrain().setMemory(MemoryModuleType.PATH, path);

        SmarterOpenDoorsTask task = new SmarterOpenDoorsTask();
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, gameTime),
                "door task did not run while approaching a head-height fence gate");
        helper.assertTrue(helper.getLevel().getBlockState(headHeightGate).getValue(BlockStateProperties.OPEN),
                "head-height fence gate did not open");

        Set<GlobalPos> gatesToClose = villager.getBrain()
                .getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                .orElse(Set.of());
        helper.assertTrue(gatesToClose.contains(GlobalPos.of(helper.getLevel().dimension(), headHeightGate)),
                "head-height fence gate was not remembered for closing");

        task.doStop(helper.getLevel(), villager, gameTime);
        path.setNextNodeIndex(3);
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, gameTime + 1),
                "door task did not rerun after clearing the head-height fence gate");
        helper.assertTrue(!helper.getLevel().getBlockState(headHeightGate).getValue(BlockStateProperties.OPEN),
                "head-height fence gate did not close after the path cleared it");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_stacked_fence_gate_closing", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void villagerClosesAlreadyOpenStackedFenceGatesAfterPassingThrough(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(2, 2, 4));
        BlockPos beforeGate = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos lowerGate = helper.absolutePos(new BlockPos(5, 2, 4));
        BlockPos upperGate = lowerGate.above();
        BlockPos afterGate = helper.absolutePos(new BlockPos(6, 2, 4));
        BlockPos finalNode = helper.absolutePos(new BlockPos(7, 2, 4));

        BlockState openGate = Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true);
        helper.getLevel().setBlock(lowerGate, openGate, 3);
        helper.getLevel().setBlock(upperGate, openGate, 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(villagerPos))
                .withName("Open Stacked Fence Gate Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        Path path = new Path(List.of(
                node(beforeGate),
                node(lowerGate),
                node(afterGate),
                node(finalNode)
        ), finalNode, true);
        path.setNextNodeIndex(2);
        villager.getBrain().setMemory(MemoryModuleType.PATH, path);

        SmarterOpenDoorsTask task = new SmarterOpenDoorsTask();
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, gameTime),
                "door task did not run after the villager passed through stacked fence gates");

        Set<GlobalPos> gatesToClose = villager.getBrain()
                .getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                .orElse(Set.of());
        helper.assertTrue(gatesToClose.contains(GlobalPos.of(helper.getLevel().dimension(), lowerGate)),
                "already-open lower fence gate was not remembered for closing");
        helper.assertTrue(gatesToClose.contains(GlobalPos.of(helper.getLevel().dimension(), upperGate)),
                "already-open upper fence gate was not remembered for closing");

        task.doStop(helper.getLevel(), villager, gameTime);
        path.setNextNodeIndex(3);
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, gameTime + 1),
                "door task did not rerun after clearing already-open stacked fence gates");
        helper.assertTrue(!helper.getLevel().getBlockState(lowerGate).getValue(BlockStateProperties.OPEN),
                "already-open lower fence gate did not close after the path cleared it");
        helper.assertTrue(!helper.getLevel().getBlockState(upperGate).getValue(BlockStateProperties.OPEN),
                "already-open upper fence gate did not close after the path cleared it");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_trapdoor_interaction", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void villagerLeavesDecorativeTrapdoorClosedBesideRaisedPath(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(2, 2, 4));
        BlockPos trapdoor = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos raisedNode = helper.absolutePos(new BlockPos(5, 3, 4));
        BlockPos finalNode = helper.absolutePos(new BlockPos(6, 3, 4));

        helper.getLevel().setBlock(trapdoor.relative(Direction.SOUTH), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        helper.getLevel().setBlock(trapdoor, Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.NORTH), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(villagerPos))
                .withName("Decorative Trapdoor Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        Path path = new Path(List.of(
                node(trapdoor),
                node(raisedNode),
                node(finalNode)
        ), finalNode, true);
        path.setNextNodeIndex(1);
        villager.getBrain().setMemory(MemoryModuleType.PATH, path);

        SmarterOpenDoorsTask task = new SmarterOpenDoorsTask();
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, gameTime),
                "door task did not run on the raised path transition");

        helper.assertTrue(!helper.getLevel().getBlockState(trapdoor).getValue(TrapDoorBlock.OPEN),
                "decorative trapdoor was opened just because the path rose beside it");
        helper.assertTrue(!villager.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                        .orElse(Set.of())
                        .contains(GlobalPos.of(helper.getLevel().dimension(), trapdoor)),
                "decorative trapdoor was incorrectly claimed by the path");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_trapdoor_interaction", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void villagerDoesNotClaimAlreadyOpenVerticalHatch(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(2, 2, 4));
        BlockPos belowHatch = helper.absolutePos(new BlockPos(5, 2, 4));
        BlockPos hatch = belowHatch.above();
        BlockPos aboveHatch = hatch.above();
        BlockPos finalNode = aboveHatch.above();

        helper.getLevel().setBlock(hatch, Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(villagerPos))
                .withName("Open Hatch Ownership Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        Path path = new Path(List.of(
                node(belowHatch),
                node(hatch),
                node(aboveHatch),
                node(finalNode)
        ), finalNode, true);
        path.setNextNodeIndex(2);
        villager.getBrain().setMemory(MemoryModuleType.PATH, path);

        SmarterOpenDoorsTask task = new SmarterOpenDoorsTask();
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, gameTime),
                "door task did not run after passing an already-open hatch");

        helper.assertTrue(!villager.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                        .orElse(Set.of())
                        .contains(GlobalPos.of(helper.getLevel().dimension(), hatch)),
                "already-open hatch was claimed even though the villager did not open it");
        helper.assertTrue(helper.getLevel().getBlockState(hatch).getValue(TrapDoorBlock.OPEN),
                "already-open hatch was unexpectedly closed");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_trapdoor_interaction", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void villagerOpensClosedTrapdoorForVerticalHatch(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(2, 2, 4));
        BlockPos belowHatch = helper.absolutePos(new BlockPos(5, 2, 4));
        BlockPos hatch = belowHatch.above();
        BlockPos aboveHatch = hatch.above();

        helper.getLevel().setBlock(hatch, Blocks.OAK_TRAPDOOR.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(villagerPos))
                .withName("Vertical Hatch Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        Path path = new Path(List.of(
                node(belowHatch),
                node(hatch),
                node(aboveHatch)
        ), aboveHatch, true);
        path.setNextNodeIndex(1);
        villager.getBrain().setMemory(MemoryModuleType.PATH, path);

        SmarterOpenDoorsTask task = new SmarterOpenDoorsTask();
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, gameTime),
                "door task did not run while approaching the vertical hatch");

        helper.assertTrue(helper.getLevel().getBlockState(hatch).getValue(TrapDoorBlock.OPEN),
                "closed hatch was not opened for a same-column vertical transition");
        helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                        .orElse(Set.of())
                        .contains(GlobalPos.of(helper.getLevel().dimension(), hatch)),
                "hatch opened by the villager was not remembered for closing");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_stale_toggleable_memory", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void distantRememberedToggleableDoesNotLoadChunk(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(2, 2, 4));
        BlockPos distant = villagerPos.offset(1024, 0, 1024);
        ChunkPos distantChunk = new ChunkPos(distant);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(villagerPos))
                .withName("Stale Toggleable Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        GlobalPos remembered = GlobalPos.of(helper.getLevel().dimension(), distant);
        villager.getBrain().setMemory(MemoryModuleType.DOORS_TO_CLOSE, new HashSet<>(Set.of(remembered)));
        helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(distantChunk.x, distantChunk.z) == null,
                "fixture distant chunk was already loaded");

        SmarterOpenDoorsTask.closeDoors(helper.getLevel(), villager, null, null);

        helper.assertTrue(!villager.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                        .orElse(Set.of()).contains(remembered),
                "stale remembered toggleable was not discarded");
        helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(distantChunk.x, distantChunk.z) == null,
                "stale remembered toggleable forced a distant chunk load");
        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_shared_gate_closing", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void gateRemainsOpenWhileAnotherVillagerUsesItsPath(GameTestHelper helper) {
        BlockPos gate = helper.absolutePos(new BlockPos(5, 2, 4));
        BlockPos ownerPos = helper.absolutePos(new BlockPos(2, 2, 4));
        BlockPos otherPos = gate.west();
        helper.getLevel().setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true), 3);

        VillagerEntityMCA owner = VillagerFactory.newVillager(helper.getLevel())
                .withGender(Gender.MALE).withAge(0).withPosition(Vec3.atBottomCenterOf(ownerPos))
                .spawn(MobSpawnType.STRUCTURE);
        VillagerEntityMCA other = VillagerFactory.newVillager(helper.getLevel())
                .withGender(Gender.MALE).withAge(0).withPosition(Vec3.atBottomCenterOf(otherPos))
                .spawn(MobSpawnType.STRUCTURE);
        owner.setNoAi(true);
        other.setNoAi(true);
        owner.refreshBrain(helper.getLevel());
        other.refreshBrain(helper.getLevel());
        try {
            GlobalPos rememberedGate = GlobalPos.of(helper.getLevel().dimension(), gate);
            owner.getBrain().setMemory(MemoryModuleType.DOORS_TO_CLOSE,
                    new HashSet<>(Set.of(rememberedGate)));
            owner.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(other));
            Path otherPath = new Path(List.of(node(otherPos), node(gate), node(gate.east())), gate.east(), true);
            otherPath.setNextNodeIndex(1);
            other.getBrain().setMemory(MemoryModuleType.PATH, otherPath);

            SmarterOpenDoorsTask.closeDoors(helper.getLevel(), owner, null, null);
            helper.assertTrue(helper.getLevel().getBlockState(gate).getValue(BlockStateProperties.OPEN),
                    "the gate closed while another villager was following its path");
            helper.assertTrue(owner.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                            .orElse(Set.of()).contains(rememberedGate),
                    "gate ownership was dropped while another villager still needed it");

            other.getBrain().eraseMemory(MemoryModuleType.PATH);
            SmarterOpenDoorsTask.closeDoors(helper.getLevel(), owner, null, null);
            helper.assertTrue(!helper.getLevel().getBlockState(gate).getValue(BlockStateProperties.OPEN),
                    "gate was not closed after the other villager abandoned its path");
            helper.assertTrue(!owner.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                            .orElse(Set.of()).contains(rememberedGate),
                    "gate ownership was not released after closing");
            helper.succeed();
        } finally {
            owner.discard();
            other.discard();
        }
    }

    @GameTest(batch = "mca_stationary_door_peer", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 40)
    public static void doorRemainsOpenUntilStationaryPeerClearsItsBody(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos door = helper.absolutePos(new BlockPos(5, 2, 4));
        BlockState originalSupport = level.getBlockState(door.below());
        BlockState originalLower = level.getBlockState(door);
        BlockState originalUpper = level.getBlockState(door.above());
        level.setBlock(door.below(), Blocks.STONE.defaultBlockState(), 3);
        BlockState openDoor = Blocks.SPRUCE_DOOR.defaultBlockState()
                .setValue(DoorBlock.FACING, Direction.SOUTH)
                .setValue(DoorBlock.OPEN, true);
        level.setBlock(door, openDoor, 3);
        level.setBlock(door.above(), openDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        VillagerEntityMCA owner = VillagerFactory.newVillager(level)
                .withGender(Gender.MALE).withAge(0).withPosition(Vec3.atBottomCenterOf(door.west(3)))
                .spawn(MobSpawnType.STRUCTURE);
        VillagerEntityMCA peer = VillagerFactory.newVillager(level)
                .withGender(Gender.MALE).withAge(0)
                .withPosition(new Vec3(door.getX() + 0.5D, door.getY(), door.getZ() - 0.1D))
                .spawn(MobSpawnType.STRUCTURE);
        owner.setNoAi(true);
        peer.setNoAi(true);
        try {
            peer.getBrain().eraseMemory(MemoryModuleType.PATH);
            helper.assertTrue(level.noCollision(peer, peer.getBoundingBox())
                            && openDoor.setValue(DoorBlock.OPEN, false).getCollisionShape(level, door).toAabbs()
                            .stream().anyMatch(box -> box.move(door).intersects(peer.getBoundingBox())),
                    "The stationary peer must fit the open door but intersect its closed panel");
            GlobalPos remembered = GlobalPos.of(level.dimension(), door);
            owner.getBrain().setMemory(MemoryModuleType.DOORS_TO_CLOSE, new HashSet<>(Set.of(remembered)));
            owner.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(peer));

            SmarterOpenDoorsTask.closeDoors(level, owner, null, null);
            helper.assertTrue(level.getBlockState(door).getValue(DoorBlock.OPEN)
                            && level.noCollision(peer, peer.getBoundingBox()),
                    "A remembered door must not close across a stationary peer without a PATH");
            helper.assertTrue(owner.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                            .orElse(Set.of()).contains(remembered),
                    "Door cleanup must remain owned until the stationary peer clears it");

            peer.setPos(door.getX() + 0.5D, door.getY(), door.getZ() - 3.0D);
            SmarterOpenDoorsTask.closeDoors(level, owner, null, null);
            helper.assertTrue(!level.getBlockState(door).getValue(DoorBlock.OPEN)
                            && !owner.getBrain().getMemory(MemoryModuleType.DOORS_TO_CLOSE)
                            .orElse(Set.of()).contains(remembered),
                    "The remembered door must close and release ownership after the peer clears it");
            helper.succeed();
        } finally {
            owner.discard();
            peer.discard();
            level.setBlock(door.below(), originalSupport, 3);
            level.setBlock(door, originalLower, 3);
            level.setBlock(door.above(), originalUpper, 3);
        }
    }

    @GameTest(batch = "mca_trapdoor_interaction", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void villagerOpensTrapdoorOneBlockAboveLadderFromEitherDirection(GameTestHelper helper) {
        BlockPos ladder = helper.absolutePos(new BlockPos(5, 2, 4));
        BlockPos hatch = ladder.above();
        BlockPos upperExit = hatch.east();
        BlockPos upperFinal = upperExit.east();

        helper.getLevel().setBlock(ladder.west(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(ladder, Blocks.LADDER.defaultBlockState()
                .setValue(LadderBlock.FACING, Direction.EAST), 3);
        helper.getLevel().setBlock(hatch, Blocks.OAK_TRAPDOOR.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(ladder))
                .withName("Ladder Hatch Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        Path ascending = new Path(List.of(
                node(ladder),
                node(upperExit),
                node(upperFinal)
        ), upperFinal, true);
        ascending.setNextNodeIndex(1);
        villager.getBrain().setMemory(MemoryModuleType.PATH, ascending);

        long gameTime = helper.getLevel().getGameTime();
        SmarterOpenDoorsTask ascendingTask = new SmarterOpenDoorsTask();
        helper.assertTrue(ascendingTask.tryStart(helper.getLevel(), villager, gameTime),
                "door task did not run while leaving the ladder through the hatch");
        helper.assertTrue(helper.getLevel().getBlockState(hatch).getValue(TrapDoorBlock.OPEN),
                "closed hatch one block above the ladder was not opened while climbing up");

        helper.getLevel().setBlock(hatch, helper.getLevel().getBlockState(hatch)
                .setValue(TrapDoorBlock.OPEN, false), 3);
        villager.getBrain().eraseMemory(MemoryModuleType.DOORS_TO_CLOSE);
        villager.setPos(Vec3.atBottomCenterOf(upperExit));

        Path descending = new Path(List.of(
                node(upperExit),
                node(ladder),
                node(ladder.below())
        ), ladder.below(), true);
        descending.setNextNodeIndex(1);
        villager.getBrain().setMemory(MemoryModuleType.PATH, descending);

        SmarterOpenDoorsTask descendingTask = new SmarterOpenDoorsTask();
        helper.assertTrue(descendingTask.tryStart(helper.getLevel(), villager, gameTime + 1),
                "door task did not run while approaching the ladder hatch from above");
        helper.assertTrue(helper.getLevel().getBlockState(hatch).getValue(TrapDoorBlock.OPEN),
                "closed hatch one block above the ladder was not opened from the top side");

        villager.discard();
        helper.succeed();
    }

    private static Node node(BlockPos pos) {
        return new Node(pos.getX(), pos.getY(), pos.getZ());
    }
}
