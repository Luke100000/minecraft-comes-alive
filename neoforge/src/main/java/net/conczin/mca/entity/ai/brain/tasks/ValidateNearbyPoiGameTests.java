package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.BedPoiCompatibilityGameTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
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
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
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

        VillagerEntityMCA observer = spawnVillager(helper, head.relative(Direction.NORTH), "Untagged Bed Observer");
        var poiManager = helper.getLevel().getPoiManager();
        helper.assertTrue(poiManager.take(
                        poi -> poi.is(PoiTypes.HOME),
                        (poi, pos) -> pos.equals(head),
                        head,
                        1
                ).filter(head::equals).isPresent(),
                "validating villager could not claim the untagged bed HOME ticket");
        observer.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), head));

        VillagerEntityMCA sleeper = spawnVillager(helper, head.relative(Direction.SOUTH), "Untagged Bed Owner");
        sleeper.startSleeping(head);
        helper.assertTrue(sleeper.isSleeping(), "fixture owner did not start sleeping");

        var validator = ValidateNearbyPoi.create(poi -> poi.is(PoiTypes.HOME), MemoryModuleType.HOME);
        helper.assertTrue(validator.tryStart(helper.getLevel(), observer, helper.getLevel().getGameTime()),
                "HOME validator did not run");
        helper.assertTrue(observer.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                "validator ignored an occupied MCA-compatible BedBlock outside #minecraft:beds");
        helper.assertTrue(poiManager.getFreeTickets(head) == 0,
                "validating the occupied untagged bed released the sleeping villager's HOME ticket");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void occupiedVillagerBedKeepsPoiTicket(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(3, 1, 3));
        Direction facing = Direction.EAST;
        BlockPos head = foot.relative(facing);
        placeBed(helper, foot, facing);

        VillagerEntityMCA observer = spawnVillager(helper, head.relative(Direction.NORTH), "Ticket Owner");
        var poiManager = helper.getLevel().getPoiManager();
        helper.assertTrue(poiManager.existsAtPosition(PoiTypes.HOME, head),
                "fixture bed head was not registered as a HOME POI");
        helper.assertTrue(poiManager.take(
                        poi -> poi.is(PoiTypes.HOME),
                        (poi, pos) -> pos.equals(head),
                        head,
                        1
                ).filter(head::equals).isPresent(),
                "validating villager could not claim the bed HOME ticket");
        helper.assertTrue(poiManager.getFreeTickets(head) == 0,
                "fixture HOME ticket was not claimed");
        observer.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), head));

        VillagerEntityMCA sleeper = spawnVillager(helper, head.relative(Direction.SOUTH), "Physical Sleeper");
        sleeper.startSleeping(head);
        helper.assertTrue(sleeper.isSleeping(), "fixture sleeper did not start sleeping");

        var validator = ValidateNearbyPoi.create(poi -> poi.is(PoiTypes.HOME), MemoryModuleType.HOME);
        helper.assertTrue(validator.tryStart(helper.getLevel(), observer, helper.getLevel().getGameTime()),
                "HOME validator did not run");

        helper.assertTrue(observer.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                "validating villager retained HOME already occupied by another sleeper");
        helper.assertTrue(poiManager.getFreeTickets(head) == 0,
                "validating a duplicate HOME released the sleeping villager's POI ticket");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void sleepingVillagerWithoutHomeWakesOnLoad(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(3, 1, 3));
        Direction facing = Direction.EAST;
        BlockPos head = foot.relative(facing);
        placeBed(helper, foot, facing);

        VillagerEntityMCA villager = spawnVillager(helper, head.relative(Direction.SOUTH), "Stale Sleeper");
        villager.startSleeping(head);
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);

        helper.assertTrue(villager.isSleeping(), "fixture villager did not start in the stale sleeping state");
        CompoundTag saved = villager.saveWithoutId(new CompoundTag());
        helper.assertTrue(saved.contains("SleepingX"), "fixture did not persist SleepingPos");

        villager.stopSleeping();
        villager.discard();

        VillagerEntityMCA reloaded = VillagerFactory.newVillager(helper.getLevel()).build();
        reloaded.load(saved);

        helper.assertTrue(!reloaded.isSleeping(), "villager restored stale SleepingPos without HOME");
        helper.assertTrue(!helper.getLevel().getBlockState(head).getValue(BedBlock.OCCUPIED),
                "stale loaded sleeper left the bed marked occupied");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void vanillaVillagerValidationKeepsSleepingHomePoiTicket(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(3, 1, 3));
        Direction facing = Direction.EAST;
        BlockPos head = foot.relative(facing);
        placeBed(helper, foot, facing);

        Villager observer = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new BlockPos(4, 1, 2));
        var poiManager = helper.getLevel().getPoiManager();
        helper.assertTrue(poiManager.take(
                        poi -> poi.is(PoiTypes.HOME),
                        (poi, pos) -> pos.equals(head),
                        head,
                        1
                ).filter(head::equals).isPresent(),
                "validating vanilla villager could not claim the HOME ticket");
        observer.getBrain().setMemory(MemoryModuleType.HOME,
                GlobalPos.of(helper.getLevel().dimension(), head));

        VillagerEntityMCA sleeper = spawnVillager(helper, head.relative(Direction.SOUTH), "Physical Sleeper");
        sleeper.startSleeping(head);
        helper.assertTrue(sleeper.isSleeping(), "fixture MCA villager did not start sleeping");

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
