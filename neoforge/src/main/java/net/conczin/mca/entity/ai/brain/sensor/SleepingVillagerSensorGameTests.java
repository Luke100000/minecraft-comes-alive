package net.conczin.mca.entity.ai.brain.sensor;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.gametest.SensorTestFixtures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.ai.sensing.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.ArrayList;

@PrefixGameTestTemplate(false)
public final class SleepingVillagerSensorGameTests {
    private static final List<Entity> SLEEP_ACTORS = new ArrayList<>();

    @AfterBatch(batch = "mca_sleeping_sensor_lifecycle")
    public static void cleanupLifecycle(net.minecraft.server.level.ServerLevel level) {
        SLEEP_ACTORS.forEach(Entity::discard);
        SLEEP_ACTORS.clear();
        SensorTestFixtures.restoreAll();
    }

    @GameTest(batch = "mca_sleeping_sensor_lifecycle", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void sleepingBrainKeepsExpiryAndDoesNotStartCreeperEscape(GameTestHelper helper) {
        var villager = villager(helper);
        villager.setAge(AgeState.BABY.toAge());
        villager.refreshBrain(helper.getLevel());
        sleep(helper, villager);
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(60, 2, 56));
        var nbt = new CompoundTag();
        creeper.addAdditionalSaveData(nbt);
        nbt.putShort("Fuse", (short) 30000);
        creeper.readAdditionalSaveData(nbt);
        creeper.ignite();
        creeper.setNoAi(true);
        SLEEP_ACTORS.addAll(List.of(villager, creeper));
        villager.getBrain().setMemoryWithExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY, true, 3);
        villager.setNoAi(false);
        // Allow both independent 20-tick sensors to observe a populated nearby cache.
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(villager.isSleeping(), "new creeper escape must not wake an actual sleeper");
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY),
                    "Brain expiry must continue while sleeping");
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "sleeping villager must not receive escape movement");
            helper.assertTrue(villager.getBrain().hasMemoryValue(net.conczin.mca.entity.ai.MemoryModuleTypeMCA.NEAREST_IGNITED_CREEPER),
                    "real Brain sensors must retain igniting-creeper warning while sleeping");
            helper.succeed();
        });
    }
    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void sleepingWantedItemsPauseAndResume(GameTestHelper helper) {
        var villager = villager(helper);
        var item = new ItemEntity(helper.getLevel(), villager.getX() + 3, villager.getY(), villager.getZ(), new ItemStack(Items.BREAD));
        helper.getLevel().addFreshEntity(item);
        try {
            villager.getBrain().setActiveActivityIfPossible(net.minecraft.world.entity.schedule.Activity.REST);
            helper.assertTrue(villager.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.REST)
                    && !villager.isSleeping(), "awake REST control must not count as sleeping");
            helper.assertTrue(villager.wantsToPickUp(item.getItem()), "awake fixture must want bread");
            var sensor = sensor(NearestItemSensor.class);
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).orElse(null) == item,
                    "awake positive control must see wanted item");
            villager.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM);
            sleep(helper, villager);
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).isEmpty(),
                    "sleeping item sensor must not rebuild item memory");
            villager.stopSleeping();
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_VISIBLE_WANTED_ITEM).orElse(null) == item,
                    "wanted item sensing must resume after wake");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper); villager.discard(); item.discard(); }
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void sleepingSecondaryPoisRetainOldMemoryUntilWake(GameTestHelper helper) {
        var villager = villager(helper);
        try {
            villager.setProfession(VillagerProfession.FARMER);
            var farmland = villager.blockPosition().east(3);
            helper.getLevel().setBlock(farmland, Blocks.FARMLAND.defaultBlockState(), 3);
            var sensor = sensor(SecondaryPoiSensor.class);
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.SECONDARY_JOB_SITE),
                    "awake farmer control must find farmland");
            var old = villager.getBrain().getMemory(MemoryModuleType.SECONDARY_JOB_SITE).orElseThrow();
            helper.getLevel().setBlock(farmland, Blocks.AIR.defaultBlockState(), 3);
            sleep(helper, villager);
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.SECONDARY_JOB_SITE).orElse(List.of()).equals(old),
                    "sleep must retain existing secondary POIs without scanning");
            villager.stopSleeping();
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.SECONDARY_JOB_SITE).isEmpty(),
                    "awake POI sensing must clear removed farmland");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper); villager.discard(); }
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void sleepingGolemSensorDoesNotRenewExpiry(GameTestHelper helper) {
        var villager = villager(helper);
        var golem = helper.spawn(EntityType.IRON_GOLEM, new BlockPos(59, 2, 56));
        try {
            villager.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(golem));
            var sensor = sensor(GolemSensor.class);
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().getTimeUntilExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY) == 599,
                    "awake golem control must set vanilla TTL");
            sleep(helper, villager);
            villager.getBrain().setMemoryWithExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY, true, 100);
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().getTimeUntilExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY) == 100,
                    "sleeping golem scan must not renew TTL");
            villager.stopSleeping();
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().getTimeUntilExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY) == 599,
                    "awake golem TTL refresh must resume");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper); villager.discard(); golem.discard(); }
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void sleepingBabySensorRetainsMemoryUntilWake(GameTestHelper helper) {
        var villager = villager(helper);
        var baby = VillagerFactory.newVillager(helper.getLevel()).withAge(AgeState.BABY.toAge())
                .withPosition(villager.position().add(3, 0, 0)).spawn(MobSpawnType.STRUCTURE);
        try {
            villager.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                    new NearestVisibleLivingEntities(villager, List.of(baby)));
            var sensor = sensor(VillagerMCABabiesSensor.class);
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES).orElse(List.of()).contains(baby),
                    "awake baby control must see baby");
            villager.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
            sleep(helper, villager);
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES).orElse(List.of()).contains(baby),
                    "sleeping baby scan must retain existing memory");
            villager.stopSleeping();
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.VISIBLE_VILLAGER_BABIES).orElse(List.of()).isEmpty(),
                    "awake baby scan must clear absent candidates");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper); villager.discard(); baby.discard(); }
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void sleepingBabyBedSensorPausesAndResumes(GameTestHelper helper) {
        var villager = villager(helper);
        try {
            villager.setAge(AgeState.BABY.toAge());
            sleep(helper, villager);
            var sensor = sensor(NearestBedSensor.class);
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_BED).isEmpty(),
                    "sleeping baby must not run bed search");
            villager.stopSleeping();
            scans(sensor, helper, villager);
            helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.NEAREST_BED),
                    "awake baby positive control must find real bed");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper); villager.discard(); }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void scans(Sensor sensor, GameTestHelper helper, VillagerEntityMCA villager) {
        // Drive each inherited countdown through at least one full scan interval.
        // The level does not advance: these tests isolate scan bodies from behavior ticks.
        for (int i = 0; i < 201; i++) sensor.tick(helper.getLevel(), villager);
    }

    private static Sensor<?> sensor(Class<?> parent) {
        return VillagerTasksMCA.SENSOR_TYPES.stream().map(SensorType::create)
                .filter(parent::isInstance).findFirst().orElseThrow();
    }

    private static VillagerEntityMCA villager(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(56, 2, 56));
        SensorTestFixtures.prepare(helper, pos, 7);
        var villager = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos)).spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);
        return villager;
    }

    private static void sleep(GameTestHelper helper, VillagerEntityMCA villager) {
        BlockPos foot = villager.blockPosition();
        var bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST)
                .setValue(BedBlock.PART, BedPart.FOOT);
        helper.getLevel().setBlock(foot, bed, 3);
        helper.getLevel().setBlock(foot.east(), bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        villager.startSleeping(foot.east());
        helper.assertTrue(villager.isSleeping(), "fixture must actually be sleeping");
    }
}
