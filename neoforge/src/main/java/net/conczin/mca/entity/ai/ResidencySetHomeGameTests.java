package net.conczin.mca.entity.ai;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.navigation.BedApproachTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ResidencySetHomeGameTests {
    private ResidencySetHomeGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void setHomeClaimsReachableBed(GameTestHelper helper) {
        placeFloor(helper, 1, 8, 3, 7);
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        assertBedReachable(helper, villager, head);

        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), foot),
                "Set Home rejected a reachable bed");
        assertHome(helper, villager, head);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "reachable HOME POI was not claimed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void setHomeClaimsUntaggedBedBlockHomePoi(GameTestHelper helper) {
        placeFloor(helper, 1, 8, 3, 7);
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos head = BedPoiCompatibilityGameTests.placeUntaggedBedPoi(helper, foot, Direction.EAST);
        BlockState headState = helper.getLevel().getBlockState(head);
        helper.assertTrue(!headState.is(BlockTags.BEDS),
                "fixture bed unexpectedly belongs to #minecraft:beds");

        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        assertBedReachable(helper, villager, head);

        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), foot),
                "Set Home rejected an untagged BedBlock HOME POI");
        assertHome(helper, villager, head);
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void setHomeRejectsEnclosedBed(GameTestHelper helper) {
        placeFloor(helper, 1, 8, 3, 7);
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        blockBedApproaches(helper, head, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));

        helper.assertTrue(!villager.getResidency().trySetHome(helper.getLevel(), foot),
                "Set Home accepted a bed with no reachable approach");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                "failed Set Home created a HOME memory");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME).isEmpty(),
                "failed Set Home created a FORCED_HOME memory");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 1,
                "failed Set Home consumed the enclosed bed POI ticket");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void setHomeChoosesReachableBedOverCloserBlockedBed(GameTestHelper helper) {
        placeFloor(helper, 1, 11, 3, 9);
        BlockPos blockedFoot = helper.absolutePos(new BlockPos(4, 1, 5));
        BlockPos blockedHead = placeBed(helper, blockedFoot, Direction.EAST);
        blockBedApproaches(helper, blockedHead, Direction.EAST);
        BlockPos reachableFoot = helper.absolutePos(new BlockPos(8, 1, 5));
        BlockPos reachableHead = placeBed(helper, reachableFoot, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 8)));
        assertBedReachable(helper, villager, reachableHead);

        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), blockedFoot),
                "Set Home failed when a farther reachable bed was available");
        assertHome(helper, villager, reachableHead);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(blockedHead) == 1,
                "Set Home claimed the blocked bed");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(reachableHead) == 0,
                "Set Home did not claim the reachable bed");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void failedReassignmentPreservesExistingForcedHome(GameTestHelper helper) {
        placeFloor(helper, 1, 16, 1, 5);
        BlockPos oldFoot = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos oldHead = placeBed(helper, oldFoot, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 3)));
        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), oldFoot),
                "fixture could not assign initial HOME");

        BlockPos blockedFoot = helper.absolutePos(new BlockPos(13, 1, 3));
        BlockPos blockedHead = placeBed(helper, blockedFoot, Direction.EAST);
        blockBedApproaches(helper, blockedHead, Direction.EAST);

        helper.assertTrue(!villager.getResidency().trySetHome(helper.getLevel(), blockedFoot),
                "unreachable reassignment unexpectedly succeeded");
        assertHome(helper, villager, oldHead);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(oldHead) == 0,
                "failed reassignment released the existing HOME ticket");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(blockedHead) == 1,
                "failed reassignment claimed the blocked HOME ticket");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void reselectingCurrentHomeKeepsItsPoiTicket(GameTestHelper helper) {
        placeFloor(helper, 1, 8, 3, 7);
        BlockPos foot = helper.absolutePos(new BlockPos(5, 1, 5));
        BlockPos head = placeBed(helper, foot, Direction.EAST);
        VillagerEntityMCA villager = spawnVillager(helper, helper.absolutePos(new BlockPos(2, 1, 5)));
        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), foot),
                "fixture could not assign initial HOME");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "fixture HOME ticket was not claimed");

        helper.assertTrue(villager.getResidency().trySetHome(helper.getLevel(), foot),
                "Set Home rejected the villager's already-owned bed");
        assertHome(helper, villager, head);
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "reselecting the current HOME released its POI ticket");
        helper.succeed();
    }

    private static void assertHome(GameTestHelper helper, VillagerEntityMCA villager, BlockPos expectedHome) {
        GlobalPos home = villager.getBrain().getMemoryInternal(MemoryModuleType.HOME)
                .orElseThrow(() -> new AssertionError("villager has no HOME"));
        helper.assertTrue(home.pos().equals(expectedHome), "villager selected the wrong HOME");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.FORCED_HOME).isPresent(),
                "Set Home did not mark the HOME as forced");
    }

    private static void assertBedReachable(GameTestHelper helper, VillagerEntityMCA villager, BlockPos head) {
        BedApproachTarget target = BedApproachTarget.create(helper.getLevel(), head)
                .orElseThrow(() -> new AssertionError("fixture did not create a BedApproachTarget"));
        var pathTargets = target.getPathTargets(villager);
        helper.assertTrue(!pathTargets.isEmpty(), "fixture bed had no valid approach targets");
        var path = villager.getNavigation().createPath(pathTargets, 0);
        helper.assertTrue(path != null, "fixture navigation returned no path to the bed approaches");
        helper.assertTrue(path.canReach(), "fixture navigation could not reach the bed approaches");
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos feet) {
        helper.getLevel().getChunk(feet);
        helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setOnGround(true);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());
        return villager;
    }

    private static void placeFloor(GameTestHelper helper, int minX, int maxX, int minZ, int maxZ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                BlockPos feet = helper.absolutePos(new BlockPos(x, 1, z));
                helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.above(2), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static BlockPos placeBed(GameTestHelper helper, BlockPos foot, Direction facing) {
        BlockPos head = foot.relative(facing);
        helper.getLevel().setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(head.below(), Blocks.STONE.defaultBlockState(), 3);
        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        helper.getLevel().setBlock(foot, footState, 3);
        helper.getLevel().setBlock(head, footState.setValue(BedBlock.PART, BedPart.HEAD), 3);
        return head;
    }

    private static void blockBedApproaches(GameTestHelper helper, BlockPos head, Direction facing) {
        BlockPos foot = head.relative(facing.getOpposite());
        helper.getLevel().setBlock(head.relative(facing), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(head.relative(facing.getClockWise()), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(head.relative(facing.getCounterClockWise()), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(foot.relative(facing.getClockWise()), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(foot.relative(facing.getCounterClockWise()), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(foot.relative(facing.getOpposite()), Blocks.STONE.defaultBlockState(), 3);
    }
}
