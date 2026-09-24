package net.conczin.mca.entity.ai.brain.tasks;

import com.mojang.datafixers.util.Pair;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.BedPoiCompatibilityGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;
import java.util.Set;

import static net.minecraft.world.entity.ai.behavior.AcquirePoi.findPathToPois;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ExtendedFindPointOfInterestTaskGameTests {
    private ExtendedFindPointOfInterestTaskGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void occupiedHomePoiIsNotAcquired(GameTestHelper helper) {
        BlockPos bedFoot = helper.absolutePos(new BlockPos(3, 1, 3));
        Direction facing = Direction.EAST;
        BlockPos bedHead = bedFoot.relative(facing);
        BlockPos villagerFeet = bedHead.relative(Direction.SOUTH);
        prepareFloor(helper, villagerFeet, bedHead);
        placeOccupiedBed(helper, bedFoot, facing);

        var poiManager = helper.getLevel().getPoiManager();
        BlockState bedHeadState = helper.getLevel().getBlockState(bedHead);
        helper.assertTrue(bedHeadState.is(BlockTags.BEDS),
                "fixture bed head is not in the vanilla beds tag");
        helper.assertTrue(bedHeadState.getValue(BedBlock.OCCUPIED),
                "fixture bed head did not remain physically occupied");
        helper.assertTrue(poiManager.existsAtPosition(PoiTypes.HOME, bedHead),
                "fixture occupied bed head was not registered as a HOME POI");
        helper.assertTrue(poiManager.getCountInRange(
                        poi -> poi.is(PoiTypes.HOME), bedHead, 0, PoiManager.Occupancy.HAS_SPACE) == 1,
                "fixture occupied bed HOME POI must remain unclaimed");

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(villagerFeet))
                .withName("Occupied Home Acquisition Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setOnGround(true);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        var homePoiType = poiManager.getType(bedHead)
                .orElseThrow(() -> new AssertionError("fixture HOME POI type disappeared"));
        Path fixturePath = findPathToPois(villager, Set.of(Pair.of(homePoiType, bedHead)));
        helper.assertTrue(fixturePath != null && fixturePath.canReach(),
                "fixture villager could not path to the occupied bed HOME POI");

        ExtendedFindPointOfInterestTask task = new ExtendedFindPointOfInterestTask(
                poi -> poi.is(PoiTypes.HOME),
                MemoryModuleType.HOME,
                false,
                Optional.empty(),
                ignored -> { },
                (ignored, pos) -> pos.equals(bedHead)
        );
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());

        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                "automatic HOME acquisition selected a physically occupied bed");
        helper.assertTrue(poiManager.getCountInRange(
                        poi -> poi.is(PoiTypes.HOME), bedHead, 0, PoiManager.Occupancy.HAS_SPACE) == 1,
                "automatic HOME acquisition claimed a physically occupied bed POI ticket");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void untaggedBedBlockHomePoiIsAcquired(GameTestHelper helper) {
        BlockPos bedFoot = helper.absolutePos(new BlockPos(3, 1, 3));
        Direction facing = Direction.EAST;
        BlockPos bedHead = bedFoot.relative(facing);
        BlockPos villagerFeet = bedHead.relative(Direction.SOUTH);
        prepareFloor(helper, villagerFeet, bedHead);
        BedPoiCompatibilityGameTests.placeUntaggedBedPoi(helper, bedFoot, facing);

        BlockState bedHeadState = helper.getLevel().getBlockState(bedHead);
        helper.assertTrue(!bedHeadState.is(BlockTags.BEDS),
                "fixture bed unexpectedly belongs to #minecraft:beds");

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(villagerFeet))
                .withName("Untagged Home Acquisition Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setOnGround(true);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());

        ExtendedFindPointOfInterestTask task = new ExtendedFindPointOfInterestTask(
                poi -> poi.is(PoiTypes.HOME),
                MemoryModuleType.HOME,
                false,
                Optional.empty(),
                ignored -> { },
                (ignored, pos) -> pos.equals(bedHead)
        );
        task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());

        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.HOME)
                        .map(home -> home.pos().equals(bedHead))
                        .orElse(false),
                "automatic HOME acquisition rejected an untagged BedBlock HOME POI");
        helper.succeed();
    }

    private static void prepareFloor(GameTestHelper helper, BlockPos from, BlockPos to) {
        int minX = Math.min(from.getX(), to.getX()) - 1;
        int maxX = Math.max(from.getX(), to.getX()) + 1;
        int minZ = Math.min(from.getZ(), to.getZ()) - 1;
        int maxZ = Math.max(from.getZ(), to.getZ()) + 1;
        int feetY = from.getY();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos feet = new BlockPos(x, feetY, z);
                helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void placeOccupiedBed(GameTestHelper helper, BlockPos foot, Direction facing) {
        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT)
                .setValue(BedBlock.OCCUPIED, true);
        helper.getLevel().setBlock(foot, footState, 3);
        helper.getLevel().setBlock(foot.relative(facing), footState.setValue(BedBlock.PART, BedPart.HEAD), 3);
    }

}
