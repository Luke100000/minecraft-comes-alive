package net.conczin.mca.server.world.data;

import net.conczin.mca.dialogue.DialogueEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.core.RegistryAccess;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueEventHistoryTest {
    @TempDir
    Path tempDir;

    private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID VILLAGER = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final ResourceLocation STORY_ID = id("story/example");
    private static final ResourceLocation OTHER_ID = id("story/other");

    @Test
    void storyCompletionChoicesAndCooldownRoundTrip() {
        DialogueEventHistory history = new DialogueEventHistory();
        DialogueEvent event = story(STORY_ID, DialogueEvent.RepeatType.COOLDOWN, 100L, 100L);

        history.complete(PLAYER_A, VILLAGER, event, Set.of("ask_why", "comfort"), 1_000L, RandomSource.create(1L));

        CompoundTag saved = history.save(new CompoundTag(), null);
        DialogueEventHistory loaded = new DialogueEventHistory(saved, null);

        assertEquals(1, saved.getInt("schema_version"));
        assertTrue(loaded.completed(PLAYER_A, VILLAGER, STORY_ID));
        assertTrue(loaded.chose(PLAYER_A, VILLAGER, STORY_ID, "ask_why"));
        assertTrue(loaded.chose(PLAYER_A, VILLAGER, STORY_ID, "comfort"));
        assertEquals(1_100L, loaded.nextEligibleAt(PLAYER_A, VILLAGER, STORY_ID));
        assertEquals(1L, savedRecord(saved, PLAYER_A, VILLAGER, STORY_ID).getLong("completion_count"));
        assertEquals(1_000L, savedRecord(saved, PLAYER_A, VILLAGER, STORY_ID).getLong("last_completed_at"));
        assertEquals("mca:story/example", savedRecord(saved, PLAYER_A, VILLAGER, STORY_ID).getString("event"));
    }

    @Test
    void typedSavedDataFactoryReloadsHistoryFromDisk() {
        SharedConstants.tryDetectVersion();
        SavedData.Factory<DialogueEventHistory> factory = new SavedData.Factory<>(
                DialogueEventHistory::new,
                DialogueEventHistory::new,
                DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
        String dataId = "dialogue_event_history_test";

        DimensionDataStorage firstStorage = new DimensionDataStorage(
                tempDir.toFile(), DataFixers.getDataFixer(), RegistryAccess.EMPTY);
        DialogueEventHistory first = firstStorage.computeIfAbsent(factory, dataId);
        first.complete(
                PLAYER_A,
                VILLAGER,
                story(STORY_ID, DialogueEvent.RepeatType.COOLDOWN, 100L, 100L),
                Set.of("remembered"),
                1_000L,
                RandomSource.create(42L));
        firstStorage.save();

        DimensionDataStorage secondStorage = new DimensionDataStorage(
                tempDir.toFile(), DataFixers.getDataFixer(), RegistryAccess.EMPTY);
        DialogueEventHistory loaded = secondStorage.get(factory, dataId);

        assertTrue(loaded.completed(PLAYER_A, VILLAGER, STORY_ID));
        assertTrue(loaded.chose(PLAYER_A, VILLAGER, STORY_ID, "remembered"));
        assertEquals(1_100L, loaded.nextEligibleAt(PLAYER_A, VILLAGER, STORY_ID));
    }

    @Test
    void sameVillagerKeepsPlayersIndependent() {
        DialogueEventHistory history = new DialogueEventHistory();
        DialogueEvent event = story(STORY_ID, DialogueEvent.RepeatType.COOLDOWN, 100L, 100L);

        history.complete(PLAYER_A, VILLAGER, event, Set.of("positive"), 100L, RandomSource.create(1L));
        history.complete(PLAYER_B, VILLAGER, event, Set.of("negative"), 500L, RandomSource.create(2L));

        assertTrue(history.chose(PLAYER_A, VILLAGER, STORY_ID, "positive"));
        assertFalse(history.chose(PLAYER_A, VILLAGER, STORY_ID, "negative"));
        assertTrue(history.chose(PLAYER_B, VILLAGER, STORY_ID, "negative"));
        assertFalse(history.chose(PLAYER_B, VILLAGER, STORY_ID, "positive"));
        assertEquals(200L, history.nextEligibleAt(PLAYER_A, VILLAGER, STORY_ID));
        assertEquals(600L, history.nextEligibleAt(PLAYER_B, VILLAGER, STORY_ID));
    }

    @Test
    void fixedAndRandomCooldownsAreRolledAtCompletionAndThenStayStable() {
        DialogueEventHistory history = new DialogueEventHistory();
        DialogueEvent fixed = story(STORY_ID, DialogueEvent.RepeatType.COOLDOWN, 100L, 100L);
        history.complete(PLAYER_A, VILLAGER, fixed, Set.of(), 1_000L, RandomSource.create(3L));
        assertEquals(1_100L, history.nextEligibleAt(PLAYER_A, VILLAGER, STORY_ID));

        DialogueEvent ranged = story(OTHER_ID, DialogueEvent.RepeatType.COOLDOWN, 40L, 60L);
        long firstRoll = -1L;
        boolean sawDifferentRoll = false;
        for (long seed = 4L; seed < 24L; seed++) {
            history.complete(PLAYER_A, VILLAGER, ranged, Set.of(), 2_000L, RandomSource.create(seed));
            long rolled = history.nextEligibleAt(PLAYER_A, VILLAGER, OTHER_ID);
            assertTrue(rolled >= 2_040L && rolled <= 2_060L);
            assertEquals(rolled, history.nextEligibleAt(PLAYER_A, VILLAGER, OTHER_ID));
            if (firstRoll < 0L) {
                firstRoll = rolled;
            } else if (rolled != firstRoll) {
                sawDifferentRoll = true;
            }
        }
        assertTrue(sawDifferentRoll);
    }

    @Test
    void fiveSecondCooldownUsesGameTicksAndOnlyBlocksItsOwnPairAndEvent() {
        DialogueEventHistory history = new DialogueEventHistory();
        DialogueEvent event = story(STORY_ID, DialogueEvent.RepeatType.COOLDOWN, 100L, 100L);
        history.complete(PLAYER_A, VILLAGER, event, Set.of(), 500L, RandomSource.create(5L));

        long next = history.nextEligibleAt(PLAYER_A, VILLAGER, STORY_ID);
        assertEquals(600L, next);
        assertFalse(599L >= next);
        assertTrue(600L >= next);
        assertEquals(0L, history.nextEligibleAt(PLAYER_B, VILLAGER, STORY_ID));
        assertEquals(0L, history.nextEligibleAt(PLAYER_A, VILLAGER, OTHER_ID));
    }

    @Test
    void expiredSchedulingRecordsPruneButDurableStoryHistoryRemains() {
        DialogueEventHistory history = new DialogueEventHistory();
        DialogueEvent scheduling = scheduling(STORY_ID, 5L, 5L);
        DialogueEvent story = story(OTHER_ID, DialogueEvent.RepeatType.COOLDOWN, 5L, 5L);

        history.complete(PLAYER_A, VILLAGER, scheduling, Set.of("ignored"), 100L, RandomSource.create(6L));
        history.complete(PLAYER_A, VILLAGER, story, Set.of("remembered"), 100L, RandomSource.create(7L));

        history.pruneExpiredScheduling(104L);
        assertEquals(105L, history.nextEligibleAt(PLAYER_A, VILLAGER, STORY_ID));

        history.pruneExpiredScheduling(105L);
        assertEquals(0L, history.nextEligibleAt(PLAYER_A, VILLAGER, STORY_ID));
        assertTrue(history.completed(PLAYER_A, VILLAGER, OTHER_ID));
        assertTrue(history.chose(PLAYER_A, VILLAGER, OTHER_ID, "remembered"));
        assertEquals(105L, history.nextEligibleAt(PLAYER_A, VILLAGER, OTHER_ID));
    }

    @Test
    void schedulingHistoryStoresTimingOnly() {
        DialogueEventHistory history = new DialogueEventHistory();
        DialogueEvent event = scheduling(STORY_ID, 100L, 100L);

        history.complete(PLAYER_A, VILLAGER, event, Set.of("must_not_persist"), 250L, RandomSource.create(8L));

        assertFalse(history.completed(PLAYER_A, VILLAGER, STORY_ID));
        assertFalse(history.chose(PLAYER_A, VILLAGER, STORY_ID, "must_not_persist"));
        assertEquals(350L, history.nextEligibleAt(PLAYER_A, VILLAGER, STORY_ID));

        CompoundTag record = savedRecord(history.save(new CompoundTag(), null), PLAYER_A, VILLAGER, STORY_ID);
        assertFalse(record.contains("completion_count"));
        assertFalse(record.contains("last_completed_at"));
        assertFalse(record.contains("choices"));
    }

    @Test
    void latestCompletedRunReplacesChoicesAndAbandonedAttemptsDoNothing() {
        DialogueEventHistory history = new DialogueEventHistory();
        DialogueEvent event = story(STORY_ID, DialogueEvent.RepeatType.COOLDOWN, 0L, 0L);

        history.complete(PLAYER_A, VILLAGER, event, Set.of("first", "kept_only_first_time"), 10L, RandomSource.create(9L));
        history.complete(PLAYER_A, VILLAGER, event, Set.of("second"), 20L, RandomSource.create(10L));

        assertFalse(history.chose(PLAYER_A, VILLAGER, STORY_ID, "first"));
        assertFalse(history.chose(PLAYER_A, VILLAGER, STORY_ID, "kept_only_first_time"));
        assertTrue(history.chose(PLAYER_A, VILLAGER, STORY_ID, "second"));
        assertTrue(history.completed(PLAYER_A, VILLAGER, STORY_ID));

        CompoundTag beforeAbandon = history.save(new CompoundTag(), null);
        // An abandoned run never calls complete(...), so it must not alter durable history.
        CompoundTag afterAbandon = history.save(new CompoundTag(), null);
        assertEquals(beforeAbandon, afterAbandon);
        assertEquals(2L, savedRecord(afterAbandon, PLAYER_A, VILLAGER, STORY_ID).getLong("completion_count"));
    }

    @Test
    void repeatableStoryHistorySurvivesCooldownExpiryAndOnceRemainsCompleted() {
        DialogueEventHistory history = new DialogueEventHistory();
        DialogueEvent repeatable = story(STORY_ID, DialogueEvent.RepeatType.COOLDOWN, 100L, 100L);
        history.complete(PLAYER_A, VILLAGER, repeatable, Set.of("ask_why"), 1_000L, RandomSource.create(11L));

        DialogueEventHistory loaded = new DialogueEventHistory(history.save(new CompoundTag(), null), null);
        assertTrue(loaded.completed(PLAYER_A, VILLAGER, STORY_ID));
        assertTrue(loaded.chose(PLAYER_A, VILLAGER, STORY_ID, "ask_why"));
        assertFalse(1_099L >= loaded.nextEligibleAt(PLAYER_A, VILLAGER, STORY_ID));
        assertTrue(1_100L >= loaded.nextEligibleAt(PLAYER_A, VILLAGER, STORY_ID));
        assertTrue(loaded.completed(PLAYER_A, VILLAGER, STORY_ID));

        DialogueEvent once = story(OTHER_ID, DialogueEvent.RepeatType.ONCE, 0L, 0L);
        loaded.complete(PLAYER_A, VILLAGER, once, Set.of(), 2_000L, RandomSource.create(12L));
        assertTrue(loaded.completed(PLAYER_A, VILLAGER, OTHER_ID));
        assertEquals(Long.MAX_VALUE, loaded.nextEligibleAt(PLAYER_A, VILLAGER, OTHER_ID));
    }

    @Test
    void malformedRecordsAreIgnoredWithoutDiscardingValidRecords() {
        DialogueEventHistory history = new DialogueEventHistory();
        history.complete(
                PLAYER_A,
                VILLAGER,
                story(STORY_ID, DialogueEvent.RepeatType.COOLDOWN, 100L, 100L),
                Set.of("valid"),
                1_000L,
                RandomSource.create(13L));
        CompoundTag saved = history.save(new CompoundTag(), null);
        ListTag records = saved.getList("records", Tag.TAG_COMPOUND);

        CompoundTag malformed = new CompoundTag();
        malformed.putUUID("player", PLAYER_B);
        malformed.putUUID("villager", VILLAGER);
        malformed.putString("event", "not a valid resource id!");
        malformed.putLong("next_eligible_at", 2_000L);
        records.add(malformed);

        DialogueEventHistory loaded = new DialogueEventHistory(saved, null);

        assertTrue(loaded.completed(PLAYER_A, VILLAGER, STORY_ID));
        assertFalse(loaded.completed(PLAYER_B, VILLAGER, STORY_ID));
    }

    @Test
    void malformedSchemaVersionLoadsEmptyCurrentState() {
        CompoundTag saved = new CompoundTag();
        saved.putString("schema_version", "one");
        saved.put("records", new ListTag());

        DialogueEventHistory loaded = new DialogueEventHistory(saved, null);

        assertFalse(loaded.completed(PLAYER_A, VILLAGER, STORY_ID));
        assertEquals(1, loaded.save(new CompoundTag(), null).getInt("schema_version"));
    }

    @Test
    void unsupportedFutureVersionIsPreservedAndReadOnly() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 2);
        future.putString("future_field", "preserve me");
        ListTag futureRecords = new ListTag();
        futureRecords.add(StringTag.valueOf("unknown future shape"));
        future.put("records", futureRecords);

        DialogueEventHistory loaded = new DialogueEventHistory(future, null);
        loaded.complete(
                PLAYER_A,
                VILLAGER,
                story(STORY_ID, DialogueEvent.RepeatType.COOLDOWN, 100L, 100L),
                Set.of("ignored"),
                100L,
                RandomSource.create(14L));
        loaded.pruneExpiredScheduling(Long.MAX_VALUE);

        assertFalse(loaded.completed(PLAYER_A, VILLAGER, STORY_ID));
        assertFalse(loaded.isDirty());
        assertEquals(future, loaded.save(new CompoundTag(), null));
    }

    @Test
    void completionCountAndCooldownTimestampSaturateInsteadOfOverflowing() {
        DialogueEventHistory history = new DialogueEventHistory();
        DialogueEvent event = story(STORY_ID, DialogueEvent.RepeatType.COOLDOWN, 100L, 100L);
        history.complete(PLAYER_A, VILLAGER, event, Set.of("first"), 10L, RandomSource.create(15L));
        CompoundTag saved = history.save(new CompoundTag(), null);
        CompoundTag record = savedRecord(saved, PLAYER_A, VILLAGER, STORY_ID);
        record.putLong("completion_count", Long.MAX_VALUE);

        DialogueEventHistory loaded = new DialogueEventHistory(saved, null);
        loaded.complete(
                PLAYER_A,
                VILLAGER,
                event,
                Set.of("latest"),
                Long.MAX_VALUE - 5L,
                RandomSource.create(16L));

        CompoundTag saturated = savedRecord(loaded.save(new CompoundTag(), null), PLAYER_A, VILLAGER, STORY_ID);
        assertEquals(Long.MAX_VALUE, saturated.getLong("completion_count"));
        assertEquals(Long.MAX_VALUE - 5L, saturated.getLong("last_completed_at"));
        assertEquals(Long.MAX_VALUE, saturated.getLong("next_eligible_at"));
        assertTrue(loaded.chose(PLAYER_A, VILLAGER, STORY_ID, "latest"));
    }

    @Test
    void mutationsDirtyCurrentDataAndNoopPruningDoesNot() {
        DialogueEventHistory history = new DialogueEventHistory();
        assertFalse(history.isDirty());

        history.pruneExpiredScheduling(100L);
        assertFalse(history.isDirty());

        history.complete(
                PLAYER_A,
                VILLAGER,
                story(STORY_ID, DialogueEvent.RepeatType.COOLDOWN, 100L, 100L),
                Set.of(),
                100L,
                RandomSource.create(17L));
        assertTrue(history.isDirty());
    }

    private static DialogueEvent story(
            ResourceLocation id,
            DialogueEvent.RepeatType repeatType,
            long minTicks,
            long maxTicks
    ) {
        return event(id, new DialogueEvent.Repeat(repeatType, minTicks, maxTicks), DialogueEvent.HistoryPolicy.STORY);
    }

    private static DialogueEvent scheduling(ResourceLocation id, long minTicks, long maxTicks) {
        return event(
                id,
                new DialogueEvent.Repeat(DialogueEvent.RepeatType.COOLDOWN, minTicks, maxTicks),
                DialogueEvent.HistoryPolicy.SCHEDULING);
    }

    private static DialogueEvent event(
            ResourceLocation id,
            DialogueEvent.Repeat repeat,
            DialogueEvent.HistoryPolicy history
    ) {
        DialogueEvent.Node terminal = new DialogueEvent.Node(
                List.of("dialogue.test.line"),
                Optional.empty(),
                Optional.empty(),
                true,
                false,
                false,
                false);
        return new DialogueEvent(
                id,
                DialogueEvent.Trigger.TALK,
                new DialogueEvent.Presentation(
                        DialogueEvent.PresentationMode.ASK,
                        Optional.of("dialogue.test.prompt"),
                        "dialogue.test.resume",
                        Optional.empty()),
                0,
                1.0,
                List.of(),
                repeat,
                history,
                "terminal",
                Map.of("terminal", terminal));
    }

    private static CompoundTag savedRecord(
            CompoundTag saved,
            UUID player,
            UUID villager,
            ResourceLocation event
    ) {
        for (Tag tag : saved.getList("records", Tag.TAG_COMPOUND)) {
            CompoundTag record = (CompoundTag) tag;
            if (record.hasUUID("player")
                    && record.hasUUID("villager")
                    && record.getUUID("player").equals(player)
                    && record.getUUID("villager").equals(villager)
                    && record.getString("event").equals(event.toString())) {
                return record;
            }
        }
        throw new AssertionError("Missing saved record for " + player + "/" + villager + "/" + event);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mca", path);
    }
}
