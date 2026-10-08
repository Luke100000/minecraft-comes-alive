package net.conczin.mca.dialogue;

import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ZombieVillagerEntityMCA;
import net.conczin.mca.entity.ai.RecentVillagerEvents;
import net.conczin.mca.entity.ai.brain.tasks.RecordRaidSurvivalTask;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.registry.BlocksMCA;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.raid.Raid;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class DialogueLifeEventGameTests {
    private DialogueLifeEventGameTests() {
    }

    @GameTest(batch = "mca_dialogue_life_events", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void acceptedDamageRecordsAttackButRejectedDamageDoesNot(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper, new BlockPos(1, 1, 1));
        long now = RecentVillagerEvents.gameTime(helper.getLevel());

        villager.setInvulnerable(true);
        helper.assertTrue(!villager.hurt(helper.getLevel().damageSources().generic(), 1.0F),
                "invulnerable villager must reject the fixture hit");
        helper.assertTrue(!villager.getRecentVillagerEvents().occurredWithin(RecentVillagerEvents.ATTACKED, now, 0L),
                "rejected damage must not produce mca:attacked");

        villager.setInvulnerable(false);
        helper.assertTrue(villager.hurt(helper.getLevel().damageSources().generic(), 1.0F),
                "ordinary damage must be accepted");
        helper.assertTrue(villager.getRecentVillagerEvents().occurredWithin(
                        RecentVillagerEvents.ATTACKED, RecentVillagerEvents.gameTime(helper.getLevel()), 0L),
                "accepted damage must produce mca:attacked");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_life_events", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void spouseDeathRecordsRelativeDeathOnSurvivor(GameTestHelper helper) {
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(1, 1, 1));
        VillagerEntityMCA survivor = spawnVillager(helper, new BlockPos(3, 1, 1));
        deceased.getRelationships().marry(survivor);
        survivor.getRelationships().marry(deceased);

        deceased.getRelationships().onDeath(helper.getLevel().damageSources().generic());

        helper.assertTrue(survivor.getRecentVillagerEvents().occurredWithin(
                        RecentVillagerEvents.RELATIVE_DEATH,
                        RecentVillagerEvents.gameTime(helper.getLevel()),
                        0L),
                "spouse death propagation must record a recent relative-death fact on the survivor");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_life_events", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void villagerZombieRoundTripPreservesFactsAndLongTermMemory(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper, new BlockPos(1, 1, 1));
        long now = RecentVillagerEvents.gameTime(helper.getLevel());
        villager.getRecentVillagerEvents().record(RecentVillagerEvents.ATTACKED, now);
        villager.getLongTermMemory().remember("life_event_conversion");
        UUID id = villager.getUUID();

        ZombieVillagerEntityMCA zombie = (ZombieVillagerEntityMCA) villager.convertTo(EntityType.ZOMBIE_VILLAGER, false);
        helper.assertTrue(zombie != null && zombie.getUUID().equals(id),
                "zombification must preserve MCA villager identity");
        helper.assertTrue(zombie.getRecentVillagerEvents().occurredWithin(RecentVillagerEvents.ATTACKED, now, 0L),
                "existing recent facts must survive villager-to-zombie conversion");
        helper.assertTrue(zombie.getRecentVillagerEvents().occurredWithin(RecentVillagerEvents.ZOMBIFIED, now, 0L),
                "zombification must record mca:zombified before conversion data is copied");

        VillagerEntityMCA cured = (VillagerEntityMCA) zombie.convertTo(EntityType.VILLAGER, false);
        helper.assertTrue(cured != null && cured.getUUID().equals(id), "cure must preserve MCA villager identity");
        helper.assertTrue(cured.getRecentVillagerEvents().occurredWithin(RecentVillagerEvents.ATTACKED, now, 0L),
                "existing recent facts must survive the full conversion round trip");
        helper.assertTrue(cured.getRecentVillagerEvents().occurredWithin(RecentVillagerEvents.ZOMBIFIED, now, 0L),
                "zombification fact must survive cure");
        helper.assertTrue(cured.getRecentVillagerEvents().occurredWithin(RecentVillagerEvents.CURED, now, 0L),
                "successful cure must record mca:cured");
        helper.assertTrue(cured.getLongTermMemory().hasMemory("life_event_conversion"),
                "existing LongTermMemory must remain preserved by the same conversion pipeline");
        helper.succeed();
    }

    @GameTest(batch = "mca_dialogue_life_events", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 650)
    public static void successfulTombstoneResurrectionRecordsRevival(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(5, 1, 1));
        UUID id = deceased.getUUID();
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data data = TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow();
        data.setEntity(deceased);
        // The tombstone stores the entity's NBT, not its live world instance.
        // Remove the fixture actor so the revived entity can reclaim that UUID.
        deceased.discard();
        helper.assertTrue(helper.getLevel().getEntity(id) == null,
                "the original villager must be absent before resurrection");
        data.startResurrecting(false);

        helper.runAfterDelay(520, () -> {
            VillagerEntityMCA revived = (VillagerEntityMCA) helper.getLevel().getEntity(id);
            helper.assertTrue(revived != null, "successful resurrection must restore the stored villager");
            helper.assertTrue(revived.getRecentVillagerEvents().occurredWithin(
                            RecentVillagerEvents.REVIVED,
                            RecentVillagerEvents.gameTime(helper.getLevel()),
                            30L),
                    "successful resurrection must record mca:revived after restoration");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_dialogue_life_events", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void raidVictoryRecordsOnceForTheSameRaid(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnVillager(helper, new BlockPos(1, 1, 1));
        Raid raid = victoryRaid(helper, villager.blockPosition(), 9_001);
        registerRaid(helper, raid);
        RecordRaidSurvivalTask task = new RecordRaidSurvivalTask();
        long now = RecentVillagerEvents.gameTime(helper.getLevel());

        helper.assertTrue(task.tryStart(helper.getLevel(), villager, now),
                "raid-survival task must start for a real victory raid at the villager");
        helper.assertTrue(villager.getRecentVillagerEvents().occurredWithin(
                        RecentVillagerEvents.RAID_SURVIVED, now, 0L),
                "raid victory must record mca:raid_survived");

        task.doStop(helper.getLevel(), villager, now);
        helper.assertTrue(!task.tryStart(helper.getLevel(), villager, now + 1L),
                "the same Raid instance must not refresh the recent-event timestamp");
        helper.succeed();
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos relativePos) {
        VillagerEntityMCA villager = Objects.requireNonNull(EntitiesMCA.MALE_VILLAGER.create(helper.getLevel()));
        villager.setAgeState(AgeState.ADULT);
        villager.setPos(helper.absolutePos(relativePos).getCenter());
        helper.getLevel().addFreshEntity(villager);
        return villager;
    }

    private static Raid victoryRaid(GameTestHelper helper, BlockPos center, int id) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Id", id);
        tag.putBoolean("Started", true);
        tag.putBoolean("Active", true);
        tag.putLong("TicksActive", 100L);
        tag.putInt("BadOmenLevel", 1);
        tag.putInt("GroupsSpawned", 1);
        tag.putInt("PreRaidTicks", 0);
        tag.putInt("PostRaidTicks", 40);
        tag.putFloat("TotalHealth", 0.0F);
        tag.putInt("CX", center.getX());
        tag.putInt("CY", center.getY());
        tag.putInt("CZ", center.getZ());
        tag.putInt("NumGroups", 1);
        tag.putString("Status", "victory");
        return new Raid(helper.getLevel(), tag);
    }

    @SuppressWarnings("unchecked")
    private static void registerRaid(GameTestHelper helper, Raid raid) {
        try {
            Field field = helper.getLevel().getRaids().getClass().getDeclaredField("raidMap");
            field.setAccessible(true);
            ((Map<Integer, Raid>) field.get(helper.getLevel().getRaids())).put(raid.getId(), raid);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Could not register victory raid for GameTest", exception);
        }
    }
}
