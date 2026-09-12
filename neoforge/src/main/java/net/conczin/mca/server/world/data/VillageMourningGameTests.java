package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.Mourning;
import net.conczin.mca.entity.ai.relationship.RelationshipType;
import net.conczin.mca.registry.BlocksMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

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
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(8, 1, 1), "Resurrection Memory Probe");
        long now = helper.getLevel().getGameTime();
        deceased.getBrain().setMemory(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING, now);
        deceased.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT, now + 1_000L);
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data data = TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow();
        data.setEntity(deceased);
        deceased.discard();
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
