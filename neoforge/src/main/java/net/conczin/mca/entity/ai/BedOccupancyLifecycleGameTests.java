package net.conczin.mca.entity.ai;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.SpawnQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.WakeUp;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class BedOccupancyLifecycleGameTests {
    private static final String BATCH = "mca_bed_occupancy_lifecycle";
    private static final List<Mob> ACTORS = new ArrayList<>();

    private BedOccupancyLifecycleGameTests() {
    }

    @AfterBatch(batch = BATCH)
    public static void cleanActors(ServerLevel level) {
        for (Mob actor : ACTORS) {
            if (actor.isSleeping()) actor.stopSleeping();
            if (actor instanceof Villager villager) villager.releasePoi(MemoryModuleType.HOME);
            actor.discard();
        }
        ACTORS.clear();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void normalWakeClearsOccupancyAndKeepsHome(GameTestHelper helper) {
        checkWake(helper, VillagerProfession.NONE);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void archerWakeClearsOccupancyAndKeepsHome(GameTestHelper helper) {
        checkWake(helper, ProfessionsMCA.ARCHER);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void sleepingResidentSaveLoadKeepsBedAndSleepingPosition(GameTestHelper helper) {
        BlockPos head = placeBed(helper);
        VillagerEntityMCA source = spawnResident(helper, head, VillagerProfession.NONE);
        source.startSleeping(head);
        CompoundTag saved = source.saveWithoutId(new CompoundTag());
        VillagerEntityMCA loaded = (VillagerEntityMCA) source.getType().create(helper.getLevel());
        helper.assertTrue(loaded != null, "could not create the reloaded resident");
        ACTORS.add(loaded);
        loaded.load(saved);
        helper.assertTrue(loaded.getSleepingPos().filter(head::equals).isPresent(),
                "save/load dropped the resident's sleeping position");
        helper.assertTrue(loaded.getBrain().getMemory(MemoryModuleType.HOME)
                        .filter(home -> home.pos().equals(head)).isPresent(),
                "save/load dropped the sleeping resident's HOME");
        assertOccupied(helper, head, true, "loading a valid sleeper cleared its occupied bed");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void replacingSleepingVanillaVillagerDoesNotLeaveOccupiedBed(GameTestHelper helper) {
        BlockPos head = placeBed(helper);
        Villager source = EntityType.VILLAGER.create(helper.getLevel());
        helper.assertTrue(source != null, "could not create vanilla source villager");
        ACTORS.add(source);
        source.setNoAi(true);
        source.setPos(Vec3.atBottomCenterOf(head));
        helper.assertTrue(helper.getLevel().getPoiManager().take(poi -> poi.is(PoiTypes.HOME),
                        (poi, pos) -> pos.equals(head), head, 1).isPresent(),
                "vanilla sleeper could not claim the fixture HOME");
        source.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), head));
        source.startSleeping(head);
        assertOccupied(helper, head, true, "source did not occupy the fixture bed");

        // Drive the actual queued conversion used by MCA, without ticking unrelated AI.
        SpawnQueue.getInstance().convert(source);
        SpawnQueue.getInstance().tick();

        helper.assertTrue(source.isRemoved(), "queued conversion never removed its source");
        helper.getLevel().getEntitiesOfClass(VillagerEntityMCA.class,
                source.getBoundingBox().inflate(4)).forEach(ACTORS::add);
        assertOccupied(helper, head, false,
                "queued vanilla replacement discarded the sleeper but left occupied=true");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 1,
                "queued replacement left its discarded source's HOME ticket claimed");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void convertingSleepingResidentDoesNotLeaveOccupiedBed(GameTestHelper helper) {
        BlockPos head = placeBed(helper);
        VillagerEntityMCA source = spawnResident(helper, head, VillagerProfession.NONE);
        source.startSleeping(head);
        UUID identity = source.getUUID();

        Mob converted = source.convertTo(EntityType.ZOMBIE_VILLAGER, false);
        helper.assertTrue(converted != null, "resident conversion failed");
        ACTORS.add(converted);
        helper.assertTrue(source.isRemoved() && identity.equals(converted.getUUID()),
                "conversion did not replace the source while preserving its identity");
        helper.assertTrue(!converted.isSleeping(), "converted zombie unexpectedly retained sleep");
        assertOccupied(helper, head, false,
                "MCA resident conversion discarded the sleeper but left occupied=true");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 1,
                "zombification left the former resident's HOME ticket claimed");
        helper.succeed();
    }

    private static void checkWake(GameTestHelper helper, VillagerProfession profession) {
        BlockPos head = placeBed(helper);
        VillagerEntityMCA resident = spawnResident(helper, head, profession);
        resident.startSleeping(head);
        resident.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        helper.assertTrue(!resident.getBrain().isActive(Activity.REST), "wake fixture remained in REST");
        WakeUp.create().tryStart(helper.getLevel(), resident, helper.getLevel().getGameTime());
        helper.assertTrue(!resident.isSleeping(), "vanilla WakeUp did not wake the resident");
        assertOccupied(helper, head, false, "ordinary waking left occupied=true");
        helper.assertTrue(resident.getBrain().getMemory(MemoryModuleType.HOME)
                        .filter(home -> home.pos().equals(head)).isPresent(),
                "waking forgot the resident's HOME");
        helper.assertTrue(helper.getLevel().getPoiManager().getFreeTickets(head) == 0,
                "waking released the resident's HOME ticket");
        helper.succeed();
    }

    private static VillagerEntityMCA spawnResident(GameTestHelper helper, BlockPos head,
                                                  VillagerProfession profession) {
        VillagerEntityMCA resident = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withProfession(profession).withPosition(Vec3.atBottomCenterOf(head.west(3)))
                .spawn(MobSpawnType.STRUCTURE);
        ACTORS.add(resident);
        resident.setNoAi(true);
        helper.assertTrue(helper.getLevel().getPoiManager().take(poi -> poi.is(PoiTypes.HOME),
                        (poi, pos) -> pos.equals(head), head, 1).isPresent(),
                "fixture resident could not claim HOME");
        resident.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), head));
        return resident;
    }

    private static BlockPos placeBed(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(6, 2, 6));
        BlockPos head = foot.east();
        for (BlockPos pos : BlockPos.betweenClosed(foot.offset(-4, -1, -3), foot.offset(4, 2, 3))) {
            helper.getLevel().setBlock(pos, pos.getY() == foot.getY() - 1
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
        }
        var state = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        helper.getLevel().setBlock(foot, state.setValue(BedBlock.PART, BedPart.FOOT), 3);
        helper.getLevel().setBlock(head, state.setValue(BedBlock.PART, BedPart.HEAD), 3);
        return head;
    }

    private static void assertOccupied(GameTestHelper helper, BlockPos head, boolean expected, String message) {
        helper.assertTrue(helper.getLevel().getBlockState(head).getValue(BedBlock.OCCUPIED) == expected, message);
        BlockPos foot = head.west();
        helper.assertTrue(helper.getLevel().getBlockState(foot).getValue(BedBlock.OCCUPIED) == expected,
                message + " (bed foot)");
    }
}
