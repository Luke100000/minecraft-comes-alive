package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.MoodGroup;
import net.conczin.mca.entity.ai.Mourning;
import net.conczin.mca.entity.ai.relationship.RelationshipType;
import net.conczin.mca.registry.BlocksMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class VillageMourningGameTests {
    private VillageMourningGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void emptyTombstoneIsNotMournable(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);

        helper.assertTrue(!Mourning.isMournableTombstone(helper.getLevel(), grave),
                "empty tombstone must not be mournable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void occupiedTombstoneIsMournable(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(3, 1, 1), "Mourning Probe");
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);

        helper.assertTrue(Mourning.isMournableTombstone(helper.getLevel(), grave),
                "occupied tombstone must be mournable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void resurrectingTombstoneIsNotMournable(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(3, 1, 1), "Resurrection Probe");
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data data = TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow();
        data.setEntity(deceased);
        data.startResurrecting(false);

        helper.assertTrue(!Mourning.isMournableTombstone(helper.getLevel(), grave),
                "resurrecting tombstone must not be mournable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void graveDiscoveryDoesNotLoadUnloadedChunks(GameTestHelper helper) {
        BlockPos farPosition = helper.absolutePos(new BlockPos(4_096, 1, 4_096));
        helper.assertTrue(!helper.getLevel().isLoaded(farPosition),
                "fixture requires the distant chunk to start unloaded");
        Village village = new Village(1, helper.getLevel());
        registerGraveyard(village, farPosition);

        helper.assertTrue(Mourning.getMournableGraves(village, helper.getLevel()).isEmpty(),
                "an unloaded graveyard must not produce ambient mourning graves");
        helper.assertTrue(!helper.getLevel().isLoaded(farPosition),
                "ambient grave discovery must not force-load graveyard chunks");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void mourningStartClearsCompetingInteractionState(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA mourner = spawnVillager(helper, new BlockPos(3, 1, 1), "Mourning State Probe");
        VillagerEntityMCA target = spawnVillager(helper, new BlockPos(4, 1, 1), "Mourning State Target");
        mourner.getBrain().setMemory(MemoryModuleType.BREED_TARGET, target);
        mourner.getBrain().setMemory(MemoryModuleType.INTERACTION_TARGET, target);

        Mourning.start(mourner, grave);

        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleType.BREED_TARGET).isEmpty(),
                "starting mourning must stop an existing breeding interaction");
        helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleType.INTERACTION_TARGET).isEmpty(),
                "starting mourning must stop an existing social interaction");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void ambientMourningOnlyUsesIdleOrMeet(GameTestHelper helper) {
        VillagerEntityMCA idle = spawnVillager(helper, new BlockPos(1, 1, 1), "Idle Mourning Probe");
        VillagerEntityMCA meet = spawnVillager(helper, new BlockPos(2, 1, 1), "Meet Mourning Probe");
        VillagerEntityMCA work = spawnVillager(helper, new BlockPos(3, 1, 1), "Work Mourning Probe");
        VillagerEntityMCA rest = spawnVillager(helper, new BlockPos(4, 1, 1), "Rest Mourning Probe");
        VillagerEntityMCA chore = spawnVillager(helper, new BlockPos(5, 1, 1), "Chore Mourning Probe");
        GlobalPos poi = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(2, 1, 2)));

        meet.getBrain().setMemory(MemoryModuleType.MEETING_POINT, poi);
        meet.getBrain().setActiveActivityIfPossible(Activity.MEET);
        work.getBrain().setMemory(MemoryModuleType.JOB_SITE, poi);
        work.getBrain().setActiveActivityIfPossible(Activity.WORK);
        rest.getBrain().setActiveActivityIfPossible(Activity.REST);
        chore.getBrain().setActiveActivityIfPossible(ActivitiesMCA.CHORE);

        helper.assertTrue(Mourning.canMournAmbiently(idle), "IDLE resident should be eligible for ambient mourning");
        helper.assertTrue(Mourning.canMournAmbiently(meet), "MEET resident should be eligible for ambient mourning");
        helper.assertTrue(!Mourning.canMournAmbiently(work), "WORK resident must not be interrupted by ambient mourning");
        helper.assertTrue(!Mourning.canMournAmbiently(rest), "REST resident must not be interrupted by ambient mourning");
        helper.assertTrue(!Mourning.canMournAmbiently(chore), "CHORE resident must not be interrupted by ambient mourning");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void assignedMourningRetryWaitsForRetryTimestamp(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Retry Deceased Probe");
        VillagerEntityMCA mourner = spawnVillager(helper, new BlockPos(3, 1, 1), "Retry Mourner Probe");
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);

        long now = helper.getLevel().getGameTime();
        long retryAt = now + 20L;
        mourner.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_SITE, grave);
        mourner.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT, retryAt);
        mourner.getBrain().setMemory(MemoryModuleTypeMCA.LAST_GRIEVE, now);
        mourner.getBrain().setActiveActivityIfPossible(Activity.IDLE);

        helper.onEachTick(() -> {
            long tick = helper.getLevel().getGameTime();
            if (tick < retryAt) {
                helper.assertTrue(!mourner.getBrain().isActive(ActivitiesMCA.GRIEVE),
                        "retry must not start before MOURNING_RETRY_AT");
                helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                                .filter(grave::equals).isPresent(),
                        "waiting retry must preserve the assigned grave");
                return;
            }

            if (mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT).isEmpty()) {
                helper.assertTrue(mourner.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                                .filter(grave::equals).isPresent(),
                        "retry must restart the same assigned grave");
                helper.succeed();
            } else if (tick > retryAt + 40L) {
                helper.fail("mourning retry did not restart after its deadline");
            }
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void spouseTragedyStartsExactMourningWhenEnabled(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Spouse Deceased Probe");
        VillagerEntityMCA spouse = spawnVillager(helper, new BlockPos(3, 1, 1), "Spouse Mourner Probe");
        boolean previous = Config.getInstance().enableMourning;
        try {
            Config.getInstance().enableMourning = true;
            spouse.getRelationships().onTragedy(
                    helper.getLevel().damageSources().generic(), grave, RelationshipType.SPOUSE, deceased);

            helper.assertTrue(spouse.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                            .filter(grave::equals).isPresent(),
                    "spouse tragedy should target the exact burial site");
        } finally {
            Config.getInstance().enableMourning = previous;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void deceasedDoesNotMournOwnGrave(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(3, 1, 1), "Self Mourning Probe");
        boolean previous = Config.getInstance().enableMourning;
        try {
            Config.getInstance().enableMourning = true;
            deceased.getRelationships().onTragedy(
                    helper.getLevel().damageSources().generic(), grave, RelationshipType.SELF, deceased);

            helper.assertTrue(deceased.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty(),
                    "the deceased villager must not be assigned its own grave for mourning");
        } finally {
            Config.getInstance().enableMourning = previous;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void strangerTragedyDoesNotStartMourning(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Stranger Deceased Probe");
        VillagerEntityMCA stranger = spawnVillager(helper, new BlockPos(3, 1, 1), "Stranger Mourner Probe");

        stranger.getRelationships().onTragedy(
                helper.getLevel().damageSources().generic(), grave, RelationshipType.STRANGER, deceased);

        helper.assertTrue(stranger.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty(),
                "stranger tragedy must not start grave mourning");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void disabledMourningKeepsTragedyMoodWithoutGraveAssignment(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(6, 1, 1), "Disabled Deceased Probe");
        VillagerEntityMCA spouse = spawnVillager(helper, new BlockPos(3, 1, 1), "Disabled Mourner Probe");
        spouse.getVillagerBrain().modifyMoodValue(
                MoodGroup.MAX_LEVEL - spouse.getVillagerBrain().getMoodValue());
        int moodBefore = spouse.getVillagerBrain().getMoodValue();
        boolean previous = Config.getInstance().enableMourning;
        try {
            Config.getInstance().enableMourning = false;
            spouse.getRelationships().onTragedy(
                    helper.getLevel().damageSources().generic(), grave, RelationshipType.SPOUSE, deceased);

            helper.assertTrue(spouse.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty(),
                    "disabled mourning must not assign a grave");
            helper.assertTrue(spouse.getVillagerBrain().getMoodValue() < moodBefore,
                    "tragedy mood penalty must remain when mourning is disabled");
        } finally {
            Config.getInstance().enableMourning = previous;
        }
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 650)
    public static void resurrectionClearsAmbientRecencyAndRetryState(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = VillagerFactory.newVillager(helper.getLevel())
                .withPosition(Vec3.atCenterOf(helper.absolutePos(new BlockPos(8, 1, 1))))
                .withName("Resurrection Memory Probe")
                .build();
        long now = helper.getLevel().getGameTime();
        deceased.getBrain().setMemory(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING, now);
        deceased.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT, now + 1_000L);
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data data = TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow();
        data.setEntity(deceased);
        data.startResurrecting(false);

        helper.onEachTick(() -> {
            long tick = helper.getLevel().getGameTime();
            if (tick < now + 500L) {
                return;
            }

            VillagerEntityMCA resurrected = helper.getLevel()
                    .getEntitiesOfClass(VillagerEntityMCA.class, new AABB(grave).inflate(3.0D))
                    .stream()
                    .findFirst()
                    .orElse(null);
            if (resurrected != null) {
                helper.assertTrue(resurrected.getBrain()
                                .getMemoryInternal(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING).isEmpty(),
                        "resurrected villager must not inherit ambient mourning recency");
                helper.assertTrue(resurrected.getBrain()
                                .getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT).isEmpty(),
                        "resurrected villager must not inherit a mourning retry deadline");
                helper.succeed();
            } else if (tick > now + 580L) {
                helper.fail("resurrected MCA villager was not recreated near its grave");
            }
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void firstVillageTickOnlySchedulesMourning(GameTestHelper helper) {
        Village village = new Village(1, helper.getLevel());
        long now = helper.getLevel().getGameTime();

        village.tick(helper.getLevel(), now);

        helper.assertTrue(village.getNextMourningTime() >= now + 4_000L,
                "first tick should schedule mourning at least four thousand ticks away");
        helper.assertTrue(village.getNextMourningTime() <= now + 10_000L,
                "first tick should schedule mourning no more than ten thousand ticks away");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void dueVillageWithoutMournableGraveReschedules(GameTestHelper helper) {
        long now = helper.getLevel().getGameTime();
        helper.getLevel().setDayTime(6_000L);
        Village due = withNextMourningTime(new Village(1, helper.getLevel()), now, helper.getLevel());

        due.tick(helper.getLevel(), now);

        helper.assertTrue(due.getNextMourningTime() > now,
                "empty due burst must still schedule the following burst");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void dueVillageReleasesOneSmallBurstAndSchedulesAnother(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        occupyGrave(helper, grave);
        List<VillagerEntityMCA> residents = spawnResidents(helper, 10, "Burst Probe");
        long now = helper.getLevel().getGameTime();
        helper.getLevel().setDayTime(6_000L);
        Village due = withNextMourningTime(villageWithGraveyard(helper, grave), now, helper.getLevel());
        residents.forEach(due::updateResident);

        due.tick(helper.getLevel(), now);
        int firstCount = mourningSites(residents).size();
        long nextBurst = due.getNextMourningTime();

        helper.assertTrue(firstCount >= 2 && firstCount <= 4,
                "one due burst must select only two to four residents");
        helper.assertTrue(nextBurst >= now + 4_000L && nextBurst <= now + 10_000L,
                "one due burst must schedule one later random burst");

        due.tick(helper.getLevel(), nextBurst - 1L);
        helper.assertTrue(mourningSites(residents).size() == firstCount,
                "village must not release another burst before its timestamp");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void dueAmbientBurstWaitsUntilDaytime(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        occupyGrave(helper, grave);
        List<VillagerEntityMCA> residents = spawnResidents(helper, 4, "Night Burst Probe");
        long now = helper.getLevel().getGameTime();
        Village due = withNextMourningTime(villageWithGraveyard(helper, grave), now, helper.getLevel());
        residents.forEach(due::updateResident);

        helper.getLevel().setDayTime(18_000L);
        due.tick(helper.getLevel(), now);
        helper.assertTrue(mourningSites(residents).isEmpty(),
                "ambient mourning must not start during the night");
        helper.assertTrue(due.getNextMourningTime() == now,
                "a due nighttime burst must remain due instead of being consumed");

        helper.getLevel().setDayTime(6_000L);
        due.tick(helper.getLevel(), now + 1L);
        helper.assertTrue(!mourningSites(residents).isEmpty(),
                "the deferred ambient burst should run once daytime returns");
        helper.assertTrue(due.getNextMourningTime() > now + 1L,
                "the daytime burst must schedule the next random occurrence");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void ambientBurstPreservesWorkAndRestActivities(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        occupyGrave(helper, grave);
        VillagerEntityMCA idle = spawnVillager(helper, new BlockPos(2, 1, 1), "Burst Idle Probe");
        VillagerEntityMCA meet = spawnVillager(helper, new BlockPos(3, 1, 1), "Burst Meet Probe");
        VillagerEntityMCA work = spawnVillager(helper, new BlockPos(4, 1, 1), "Burst Work Probe");
        VillagerEntityMCA rest = spawnVillager(helper, new BlockPos(5, 1, 1), "Burst Rest Probe");
        GlobalPos poi = GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(2, 1, 2)));
        meet.getBrain().setMemory(MemoryModuleType.MEETING_POINT, poi);
        meet.getBrain().setActiveActivityIfPossible(Activity.MEET);
        work.getBrain().setMemory(MemoryModuleType.JOB_SITE, poi);
        work.getBrain().setActiveActivityIfPossible(Activity.WORK);
        rest.getBrain().setActiveActivityIfPossible(Activity.REST);
        List<VillagerEntityMCA> residents = List.of(idle, meet, work, rest);
        long now = helper.getLevel().getGameTime();
        helper.getLevel().setDayTime(6_000L);
        Village due = withNextMourningTime(villageWithGraveyard(helper, grave), now, helper.getLevel());
        residents.forEach(due::updateResident);

        due.tick(helper.getLevel(), now);

        helper.assertTrue(idle.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isPresent(),
                "IDLE resident should be available to an ambient burst");
        helper.assertTrue(meet.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isPresent(),
                "MEET resident should be available to an ambient burst");
        helper.assertTrue(work.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty(),
                "WORK resident must not be pulled into ambient mourning");
        helper.assertTrue(rest.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty(),
                "REST resident must not be pulled into ambient mourning");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void ambientFairnessPrefersNeverSelectedResidents(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        occupyGrave(helper, grave);
        List<VillagerEntityMCA> residents = spawnResidents(helper, 8, "Fairness Probe");
        long now = helper.getLevel().getGameTime();
        for (VillagerEntityMCA recent : residents.subList(0, 4)) {
            recent.getBrain().setMemory(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING, now);
        }
        helper.getLevel().setDayTime(6_000L);
        Village due = withNextMourningTime(villageWithGraveyard(helper, grave), now, helper.getLevel());
        residents.forEach(due::updateResident);

        due.tick(helper.getLevel(), now);

        List<VillagerEntityMCA> selected = residents.stream()
                .filter(villager -> villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isPresent())
                .toList();
        helper.assertTrue(selected.size() >= 2 && selected.size() <= 4,
                "an ambient burst must select two to four eligible residents");
        helper.assertTrue(selected.stream().noneMatch(residents.subList(0, 4)::contains),
                "ambient fairness must prefer never/older-selected residents when enough are available");
        helper.assertTrue(selected.stream().allMatch(villager -> villager.getBrain()
                        .getMemoryInternal(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING)
                        .filter(last -> last == now).isPresent()),
                "selected ambient mourners must record the current game time");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void disabledMourningFreezesNextAmbientBurst(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        occupyGrave(helper, grave);
        List<VillagerEntityMCA> residents = spawnResidents(helper, 4, "Disabled Ambient Probe");
        long now = helper.getLevel().getGameTime();
        helper.getLevel().setDayTime(6_000L);
        Village due = withNextMourningTime(villageWithGraveyard(helper, grave), now, helper.getLevel());
        residents.forEach(due::updateResident);
        long beforeBurst = due.getNextMourningTime();
        boolean previous = Config.getInstance().enableMourning;
        try {
            Config.getInstance().enableMourning = false;
            due.tick(helper.getLevel(), now);

            helper.assertTrue(mourningSites(residents).isEmpty(),
                    "disabled mourning must not assign ambient mourners");
            helper.assertTrue(due.getNextMourningTime() == beforeBurst,
                    "disabled mourning must freeze the next ambient burst timestamp");
        } finally {
            Config.getInstance().enableMourning = previous;
        }
        helper.succeed();
    }

    private static Village villageWithGraveyard(GameTestHelper helper, BlockPos grave) {
        Village village = new Village(1, helper.getLevel());
        registerGraveyard(village, grave);
        return village;
    }

    private static void registerGraveyard(Village village, BlockPos grave) {
        ExternalBuilding graveyard = new ExternalBuilding(grave);
        graveyard.setId(100);
        graveyard.setType("graveyard");
        graveyard.setTypeForced(true);
        graveyard.addBlock(BlocksMCA.CROSS_HEADSTONE, grave);
        graveyard.addBlock(BlocksMCA.CROSS_HEADSTONE, grave.east());
        graveyard.addBlock(BlocksMCA.CROSS_HEADSTONE, grave.west());
        village.registerExternalBuilding(graveyard);
    }

    private static Village withNextMourningTime(Village village, long nextMourningTime, ServerLevel level) {
        CompoundTag tag = village.save();
        tag.putLong("nextMourningTime", nextMourningTime);
        return new Village(tag, level);
    }

    private static void occupyGrave(GameTestHelper helper, BlockPos grave) {
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(7, 1, 1), "Ambient Grave Probe");
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);
    }

    private static List<VillagerEntityMCA> spawnResidents(GameTestHelper helper, int count, String prefix) {
        List<VillagerEntityMCA> residents = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            residents.add(spawnVillager(helper, new BlockPos(2 + index, 1, 3), prefix + " " + index));
        }
        return residents;
    }

    private static List<BlockPos> mourningSites(List<VillagerEntityMCA> residents) {
        return residents.stream()
                .flatMap(villager -> villager.getBrain()
                        .getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).stream())
                .toList();
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos relativePos, String name) {
        BlockPos pos = helper.absolutePos(relativePos);
        helper.getLevel().getChunk(pos);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName(name)
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        return villager;
    }
}
