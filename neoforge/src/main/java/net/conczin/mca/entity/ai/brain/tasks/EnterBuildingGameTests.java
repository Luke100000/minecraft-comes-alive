package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class EnterBuildingGameTests {
    private EnterBuildingGameTests() {
    }

    @GameTest(batch = "mca_enter_building_follow_range", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void buildingBeyondFollowRangeKeepsRealDestination(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(3, 1, 1));

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Follow Range Building Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        int pathfindingDistance = Config.getInstance().getVillagerPathfindingDistance();
        double testFollowRange = Math.max(4.0D, pathfindingDistance / 4.0D);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(testFollowRange);
        double followRange = villager.getAttributeValue(Attributes.FOLLOW_RANGE);
        int targetDistance = (int)Math.ceil(followRange) + 4;
        helper.assertTrue(targetDistance > followRange,
                "fixture target must be beyond the villager's single-path range");
        helper.assertTrue(targetDistance < pathfindingDistance,
                "fixture target must be inside the configured pathfinding distance");
        BlockPos buildingTarget = start.east(targetDistance);
        prepareFlatArea(helper, start, targetDistance + 4, 3);

        EnterBuildingTask task = new FixedTargetEnterBuildingTask(0.5F, buildingTarget);
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                "enter-building task did not start");

        var walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(walkTarget != null,
                "building beyond FOLLOW_RANGE did not publish a walk target");
        helper.assertTrue(walkTarget.getTarget().currentBlockPosition().equals(buildingTarget),
                "building travel replaced the real destination with an intermediate point");
        helper.assertTrue(walkTarget.getTarget().getClass() == BlockPosTracker.class,
                "enter-building caller still opted into a special long-distance target");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_enter_building_long_distance", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void distantBuildingKeepsRealDestination(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(3, 1, 1));
        int pathfindingDistance = Config.getInstance().getVillagerPathfindingDistance();
        BlockPos buildingTarget = start.east(pathfindingDistance + 32);
        prepareFlatArea(helper, start, 16, 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Distant Building Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        EnterBuildingTask task = new FixedTargetEnterBuildingTask(0.5F, buildingTarget);
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(task.tryStart(helper.getLevel(), villager, gameTime),
                "enter-building task did not start");

        var walkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(walkTarget != null,
                "distant building did not publish a walk target");
        helper.assertTrue(walkTarget.getTarget().currentBlockPosition().equals(buildingTarget),
                "distant building replaced the real destination with an intermediate point");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_enter_building_floor_cells", templateNamespace = "minecraft",
            template = "bastion/blocks/air")
    public static void enterBuildingTargetsRegisteredFloorCells(GameTestHelper helper) {
        BlockPos min = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos max = min.offset(4, 4, 4);
        BlockPos elevatedShelfTarget = min.offset(2, 2, 2);
        Set<BlockPos> floorCells = Set.of(
                min,
                min.offset(4, 0, 0),
                min.offset(0, 0, 4),
                min.offset(4, 0, 4)
        );

        for (BlockPos floorCell : floorCells) {
            helper.getLevel().setBlock(floorCell.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(floorCell.above(2), Blocks.STONE.defaultBlockState(), 3);
        }
        helper.getLevel().setBlock(elevatedShelfTarget.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(elevatedShelfTarget.above(2), Blocks.STONE.defaultBlockState(), 3);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(min.west(2)))
                .withName("Building Floor Target Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);

        Building building = buildingWithFloorCells(min, max, floorCells);
        Optional<BlockPos> selected = new TargetSelectionProbe().select(building, helper.getLevel(), villager);

        helper.assertTrue(selected.isPresent(), "enter-building target selection returned no usable position");
        helper.assertTrue(floorCells.contains(selected.orElseThrow()),
                "enter-building selected an elevated/non-floor position instead of registered room floor geometry");

        villager.discard();
        helper.succeed();
    }

    private static Building buildingWithFloorCells(BlockPos min, BlockPos max, Set<BlockPos> floorCells) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("id", 1);
        tag.putInt("pos0X", min.getX());
        tag.putInt("pos0Y", min.getY());
        tag.putInt("pos0Z", min.getZ());
        tag.putInt("pos1X", max.getX());
        tag.putInt("pos1Y", max.getY());
        tag.putInt("pos1Z", max.getZ());
        tag.putInt("posX", min.getX());
        tag.putInt("posY", min.getY());
        tag.putInt("posZ", min.getZ());
        tag.putInt("structureId", 1);
        tag.putInt("floorId", 1);
        tag.putString("type", "house");
        tag.putBoolean("contributesToMain", true);
        tag.put("blocks2", new CompoundTag());

        ListTag floorCellTags = new ListTag();
        for (BlockPos floorCell : floorCells) {
            floorCellTags.add(BlockPos.CODEC.encodeStart(NbtOps.INSTANCE, floorCell).getOrThrow());
        }
        tag.put("floorCells", floorCellTags);
        return new Building(tag);
    }

    private static final class FixedTargetEnterBuildingTask extends EnterBuildingTask {
        private final BlockPos target;

        private FixedTargetEnterBuildingTask(float speed, BlockPos target) {
            super("test", speed);
            this.target = target;
        }

        @Override
        protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
            return Optional.of(target);
        }
    }

    private static final class TargetSelectionProbe extends EnterBuildingTask {
        private TargetSelectionProbe() {
            super("house", 0.5F);
        }

        private Optional<BlockPos> select(Building building, Level level, VillagerEntityMCA villager) {
            return getRandomPositionIn(building, level, villager);
        }
    }
}
