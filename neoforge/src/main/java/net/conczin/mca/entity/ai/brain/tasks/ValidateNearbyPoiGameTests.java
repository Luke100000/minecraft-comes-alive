package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.BedPoiCompatibilityGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.SleepInBed;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ValidateNearbyPoiGameTests {
    private ValidateNearbyPoiGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void untaggedBedBlockAllowsMcaVillagerToSleep(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos head = BedPoiCompatibilityGameTests.placeUntaggedBedPoi(helper, foot, Direction.EAST);
        helper.assertTrue(!helper.getLevel().getBlockState(head).is(BlockTags.BEDS),
                "fixture bed unexpectedly belongs to #minecraft:beds");

        VillagerEntityMCA villager = spawnVillager(helper, head.relative(Direction.SOUTH), "Untagged Bed Sleeper");
        villager.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), head));

        SleepInBed sleepInBed = new SleepInBed();
        helper.assertTrue(sleepInBed.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                "SleepInBed rejected an MCA-compatible BedBlock outside #minecraft:beds");
        helper.assertTrue(villager.isSleeping(),
                "villager did not sleep in an MCA-compatible BedBlock outside #minecraft:beds");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void occupiedUntaggedBedIsValidatedAsHome(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos head = BedPoiCompatibilityGameTests.placeUntaggedBedPoi(helper, foot, Direction.EAST);
        helper.assertTrue(!helper.getLevel().getBlockState(head).is(BlockTags.BEDS),
                "fixture bed unexpectedly belongs to #minecraft:beds");

        var poiManager = helper.getLevel().getPoiManager();
        helper.assertTrue(poiManager.take(
                        poi -> poi.is(PoiTypes.HOME),
                        (poi, pos) -> pos.equals(head),
                        head,
                        1
                ).filter(head::equals).isPresent(),
                "fixture could not claim the untagged bed HOME ticket");

        VillagerEntityMCA sleeper = spawnVillager(helper, head.relative(Direction.SOUTH), "Untagged Bed Owner");
        sleeper.startSleeping(head);
        helper.assertTrue(sleeper.isSleeping(), "fixture owner did not start sleeping");

        VillagerEntityMCA observer = spawnVillager(helper, head.relative(Direction.NORTH), "Untagged Bed Observer");
        observer.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), head));

        var validator = ValidateNearbyPoi.create(poi -> poi.is(PoiTypes.HOME), MemoryModuleType.HOME);
        helper.assertTrue(validator.tryStart(helper.getLevel(), observer, helper.getLevel().getGameTime()),
                "HOME validator did not run");
        helper.assertTrue(observer.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                "validator ignored an occupied MCA-compatible BedBlock outside #minecraft:beds");
        helper.assertTrue(poiManager.getFreeTickets(head) == 0,
                "validating the occupied untagged bed released its sleeping owner's HOME ticket");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void occupiedVillagerBedKeepsPoiTicket(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(3, 1, 3));
        Direction facing = Direction.EAST;
        BlockPos head = foot.relative(facing);
        placeBed(helper, foot, facing);

        var poiManager = helper.getLevel().getPoiManager();
        helper.assertTrue(poiManager.existsAtPosition(PoiTypes.HOME, head),
                "fixture bed head was not registered as a HOME POI");
        helper.assertTrue(poiManager.take(
                        poi -> poi.is(PoiTypes.HOME),
                        (poi, pos) -> pos.equals(head),
                        head,
                        1
                ).filter(head::equals).isPresent(),
                "fixture could not claim the bed HOME ticket");
        helper.assertTrue(poiManager.getFreeTickets(head) == 0,
                "fixture HOME ticket was not claimed");

        VillagerEntityMCA sleeper = spawnVillager(helper, head.relative(Direction.SOUTH), "Sleeping Owner");
        sleeper.startSleeping(head);
        helper.assertTrue(sleeper.isSleeping(), "fixture owner did not start sleeping");

        VillagerEntityMCA observer = spawnVillager(helper, head.relative(Direction.NORTH), "Duplicate Observer");
        observer.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), head));

        var validator = ValidateNearbyPoi.create(poi -> poi.is(PoiTypes.HOME), MemoryModuleType.HOME);
        helper.assertTrue(validator.tryStart(helper.getLevel(), observer, helper.getLevel().getGameTime()),
                "HOME validator did not run");

        helper.assertTrue(observer.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                "observer retained HOME already occupied by another villager");
        helper.assertTrue(poiManager.getFreeTickets(head) == 0,
                "validating a duplicate HOME released the sleeping villager's POI ticket");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void vanillaVillagerValidationKeepsSleepingHomePoiTicket(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(3, 1, 3));
        Direction facing = Direction.EAST;
        BlockPos head = foot.relative(facing);
        placeBed(helper, foot, facing);

        var poiManager = helper.getLevel().getPoiManager();
        helper.assertTrue(poiManager.take(
                        poi -> poi.is(PoiTypes.HOME),
                        (poi, pos) -> pos.equals(head),
                        head,
                        1
                ).filter(head::equals).isPresent(),
                "fixture could not claim the vanilla villager HOME ticket");

        VillagerEntityMCA sleeper = spawnVillager(helper, head.relative(Direction.SOUTH), "MCA Sleeping Owner");
        sleeper.startSleeping(head);
        helper.assertTrue(sleeper.isSleeping(), "fixture MCA villager did not start sleeping");

        Villager observer = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(4, 1, 2));
        observer.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), head));

        var validator = ValidateNearbyPoi.create(poi -> poi.is(PoiTypes.HOME), MemoryModuleType.HOME);
        helper.assertTrue(validator.tryStart(helper.getLevel(), observer, helper.getLevel().getGameTime()),
                "vanilla HOME validator did not run");
        helper.assertTrue(observer.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                "vanilla observer retained an occupied HOME");
        helper.assertTrue(poiManager.getFreeTickets(head) == 0,
                "vanilla ValidateNearbyPoi released the sleeping villager's HOME ticket");
        helper.succeed();
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos feet, String name) {
        helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .withName(name)
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        return villager;
    }

    private static void placeBed(GameTestHelper helper, BlockPos foot, Direction facing) {
        helper.getLevel().setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(foot.relative(facing).below(), Blocks.STONE.defaultBlockState(), 3);
        BlockState footState = Blocks.RED_BED.defaultBlockState()
                .setValue(BedBlock.FACING, facing)
                .setValue(BedBlock.PART, BedPart.FOOT);
        helper.getLevel().setBlock(foot, footState, 3);
        helper.getLevel().setBlock(foot.relative(facing), footState.setValue(BedBlock.PART, BedPart.HEAD), 3);
    }

}
