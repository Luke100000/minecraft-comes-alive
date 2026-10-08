package net.conczin.mca.server.world.data;

import net.conczin.mca.MCA;
import net.conczin.mca.dialogue.DialogueEvent;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pair-scoped durable dialogue history. Story records are retained until an explicit future
 * migration/purge; only expired scheduling-only records are eligible for pruning.
 */
public final class DialogueEventHistory extends SavedData {
    private static final String DATA_ID = "mca_dialogue_event_history";
    private static final int SCHEMA_VERSION = 1;

    private static final String SCHEMA_VERSION_KEY = "schema_version";
    private static final String RECORDS_KEY = "records";
    private static final String PLAYER_KEY = "player";
    private static final String VILLAGER_KEY = "villager";
    private static final String EVENT_KEY = "event";
    private static final String COMPLETION_COUNT_KEY = "completion_count";
    private static final String LAST_COMPLETED_AT_KEY = "last_completed_at";
    private static final String NEXT_ELIGIBLE_AT_KEY = "next_eligible_at";
    private static final String CHOICES_KEY = "choices";

    private static final Comparator<Map.Entry<Key, EventRecord>> SAVE_ORDER = Comparator
            .comparing((Map.Entry<Key, EventRecord> entry) -> entry.getKey().player().toString())
            .thenComparing(entry -> entry.getKey().villager().toString())
            .thenComparing(entry -> entry.getKey().event().toString());

    private final Map<Key, EventRecord> records = new LinkedHashMap<>();
    private final CompoundTag preservedFutureData;

    DialogueEventHistory() {
        preservedFutureData = null;
    }

    DialogueEventHistory(CompoundTag nbt, HolderLookup.Provider provider) {
        Objects.requireNonNull(nbt, "nbt");

        if (!nbt.contains(SCHEMA_VERSION_KEY, Tag.TAG_INT)) {
            MCA.LOGGER.warn("Dialogue event history has no valid schema_version; loading empty schema {} data", SCHEMA_VERSION);
            preservedFutureData = null;
            return;
        }

        int schemaVersion = nbt.getInt(SCHEMA_VERSION_KEY);
        if (schemaVersion > SCHEMA_VERSION) {
            preservedFutureData = nbt.copy();
            MCA.LOGGER.warn(
                    "Dialogue event history schema {} is newer than supported schema {}; preserving it read-only",
                    schemaVersion,
                    SCHEMA_VERSION);
            return;
        }

        preservedFutureData = null;
        if (schemaVersion != SCHEMA_VERSION) {
            MCA.LOGGER.warn(
                    "Unsupported dialogue event history schema {}; loading empty schema {} data",
                    schemaVersion,
                    SCHEMA_VERSION);
            return;
        }

        loadRecords(nbt);
    }

    public static DialogueEventHistory get(ServerLevel level) {
        ServerLevel overworld = level.getServer().overworld();
        // 1.21.1 DimensionDataStorage dereferences Factory.type() while reading an existing file.
        // Command storage is the vanilla saved-data type for arbitrary persisted NBT payloads.
        return WorldUtils.loadData(
                overworld,
                DialogueEventHistory::new,
                ignored -> new DialogueEventHistory(),
                DATA_ID,
                DataFixTypes.SAVED_DATA_COMMAND_STORAGE);
    }

    public boolean writable() {
        return preservedFutureData == null;
    }

    public boolean completed(UUID player, UUID villager, ResourceLocation event) {
        EventRecord record = records.get(key(player, villager, event));
        return record != null && record.story() && record.completionCount() > 0L;
    }

    public boolean chose(UUID player, UUID villager, ResourceLocation event, String choiceId) {
        Objects.requireNonNull(choiceId, "choiceId");
        EventRecord record = records.get(key(player, villager, event));
        return record != null && record.story() && record.choices().contains(choiceId);
    }

    public long nextEligibleAt(UUID player, UUID villager, ResourceLocation event) {
        EventRecord record = records.get(key(player, villager, event));
        return record == null ? 0L : record.nextEligibleAt();
    }

    public long nextEligibleAt(UUID player, UUID villager, ResourceLocation event, long gameTime) {
        Key key = key(player, villager, event);
        EventRecord record = records.get(key);
        if (writable() && record != null && !record.story() && record.nextEligibleAt() <= gameTime) {
            records.remove(key);
            setDirty();
            return 0L;
        }
        return record == null ? 0L : record.nextEligibleAt();
    }

    public void complete(
            UUID player,
            UUID villager,
            DialogueEvent event,
            Set<String> choices,
            long gameTime,
            RandomSource random
    ) {
        if (!writable()) {
            throw new IllegalStateException("Cannot complete dialogue using unsupported future history schema");
        }
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(choices, "choices");
        Objects.requireNonNull(random, "random");
        if (event.id() == null) {
            throw new IllegalArgumentException("Dialogue event history requires a namespaced event ID");
        }
        if (gameTime < 0L) {
            throw new IllegalArgumentException("Dialogue event completion game time must be nonnegative");
        }

        Key key = key(player, villager, event.id());
        long nextEligibleAt = nextEligibleAt(event.repeat(), gameTime, random);
        EventRecord previous = records.get(key);

        if (event.history() == DialogueEvent.HistoryPolicy.SCHEDULING) {
            if (event.repeat().type() != DialogueEvent.RepeatType.COOLDOWN) {
                throw new IllegalArgumentException("Scheduling-only dialogue history requires a cooldown repeat policy");
            }
            if (previous != null && previous.story()) {
                records.put(key, previous.withNextEligibleAt(nextEligibleAt));
            } else {
                records.put(key, EventRecord.scheduling(nextEligibleAt));
            }
            setDirty();
            return;
        }

        Set<String> stableChoices = validatedChoices(choices);
        long completionCount = previous != null && previous.story()
                ? saturatingIncrement(previous.completionCount())
                : 1L;
        records.put(key, EventRecord.story(completionCount, gameTime, nextEligibleAt, stableChoices));
        setDirty();
    }

    public void pruneExpiredScheduling(long gameTime) {
        if (preservedFutureData != null) {
            return;
        }
        if (gameTime < 0L) {
            throw new IllegalArgumentException("Dialogue event pruning game time must be nonnegative");
        }

        boolean removed = records.entrySet().removeIf(entry -> {
            EventRecord record = entry.getValue();
            return !record.story() && record.nextEligibleAt() <= gameTime;
        });
        if (removed) {
            setDirty();
        }
    }

    /** Scans a bounded rotating slice so old player/villager pairs are eventually reclaimed. */
    public void pruneExpiredScheduling(long gameTime, int maxEntries) {
        if (!writable()) {
            return;
        }
        if (gameTime < 0L || maxEntries <= 0) {
            throw new IllegalArgumentException("Dialogue maintenance requires nonnegative time and a positive scan budget");
        }
        Map<Key, EventRecord> retained = new LinkedHashMap<>();
        Iterator<Map.Entry<Key, EventRecord>> iterator = records.entrySet().iterator();
        boolean removed = false;
        for (int scanned = 0; scanned < maxEntries && iterator.hasNext(); scanned++) {
            Map.Entry<Key, EventRecord> entry = iterator.next();
            if (entry.getValue().story() || entry.getValue().nextEligibleAt() > gameTime) {
                retained.put(entry.getKey(), entry.getValue());
            } else {
                removed = true;
            }
            iterator.remove();
        }
        records.putAll(retained);
        if (removed) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider provider) {
        if (preservedFutureData != null) {
            return preservedFutureData.copy();
        }

        nbt.putInt(SCHEMA_VERSION_KEY, SCHEMA_VERSION);
        ListTag savedRecords = new ListTag();
        records.entrySet().stream()
                .sorted(SAVE_ORDER)
                .map(DialogueEventHistory::saveRecord)
                .forEach(savedRecords::add);
        nbt.put(RECORDS_KEY, savedRecords);
        return nbt;
    }

    private void loadRecords(CompoundTag nbt) {
        if (!nbt.contains(RECORDS_KEY, Tag.TAG_LIST)) {
            if (nbt.contains(RECORDS_KEY)) {
                MCA.LOGGER.warn("Dialogue event history records field is not a list; ignoring it");
            }
            return;
        }

        ListTag savedRecords = (ListTag) nbt.get(RECORDS_KEY);
        if (savedRecords.getElementType() != Tag.TAG_END && savedRecords.getElementType() != Tag.TAG_COMPOUND) {
            MCA.LOGGER.warn("Dialogue event history records list does not contain compounds; ignoring it");
            return;
        }
        for (int index = 0; index < savedRecords.size(); index++) {
            Tag tag = savedRecords.get(index);
            if (!(tag instanceof CompoundTag savedRecord)) {
                MCA.LOGGER.warn("Ignoring malformed dialogue event history record {}: expected compound", index);
                continue;
            }

            LoadedRecord loaded = loadRecord(savedRecord, index);
            if (loaded == null) {
                continue;
            }
            if (records.putIfAbsent(loaded.key(), loaded.record()) != null) {
                MCA.LOGGER.warn("Ignoring duplicate dialogue event history tuple at record {} for {}", index, loaded.key());
            }
        }
    }

    private static LoadedRecord loadRecord(CompoundTag nbt, int index) {
        if (!nbt.hasUUID(PLAYER_KEY)
                || !nbt.hasUUID(VILLAGER_KEY)
                || !nbt.contains(EVENT_KEY, Tag.TAG_STRING)
                || !nbt.contains(NEXT_ELIGIBLE_AT_KEY, Tag.TAG_LONG)) {
            malformed(index, "missing player, villager, event, or next_eligible_at");
            return null;
        }

        String eventText = nbt.getString(EVENT_KEY);
        ResourceLocation event = ResourceLocation.tryParse(eventText);
        if (event == null || !event.toString().equals(eventText)) {
            malformed(index, "event is not a full valid resource location: " + eventText);
            return null;
        }

        long nextEligibleAt = nbt.getLong(NEXT_ELIGIBLE_AT_KEY);
        if (nextEligibleAt < 0L) {
            malformed(index, "next_eligible_at is negative");
            return null;
        }

        Key key = new Key(nbt.getUUID(PLAYER_KEY), nbt.getUUID(VILLAGER_KEY), event);
        boolean hasCount = nbt.contains(COMPLETION_COUNT_KEY, Tag.TAG_LONG);
        boolean hasLastCompletion = nbt.contains(LAST_COMPLETED_AT_KEY, Tag.TAG_LONG);
        boolean hasChoices = nbt.contains(CHOICES_KEY, Tag.TAG_LIST);

        if (!hasCount) {
            if (hasLastCompletion || hasChoices) {
                malformed(index, "scheduling-only record contains durable story fields");
                return null;
            }
            return new LoadedRecord(key, EventRecord.scheduling(nextEligibleAt));
        }

        if (!hasLastCompletion || !hasChoices) {
            malformed(index, "story record is missing last_completed_at or choices");
            return null;
        }

        long completionCount = nbt.getLong(COMPLETION_COUNT_KEY);
        long lastCompletedAt = nbt.getLong(LAST_COMPLETED_AT_KEY);
        if (completionCount <= 0L || lastCompletedAt < 0L || nextEligibleAt < lastCompletedAt) {
            malformed(index, "story count/timestamps are invalid");
            return null;
        }

        ListTag savedChoices = (ListTag) nbt.get(CHOICES_KEY);
        if (savedChoices.getElementType() != Tag.TAG_END && savedChoices.getElementType() != Tag.TAG_STRING) {
            malformed(index, "choices is not a string list");
            return null;
        }
        Set<String> choices = new HashSet<>();
        for (int choiceIndex = 0; choiceIndex < savedChoices.size(); choiceIndex++) {
            String choice = savedChoices.getString(choiceIndex);
            if (choice.isBlank() || !choices.add(choice)) {
                malformed(index, "choices contains a blank or duplicate ID");
                return null;
            }
        }

        return new LoadedRecord(key, EventRecord.story(completionCount, lastCompletedAt, nextEligibleAt, choices));
    }

    private static CompoundTag saveRecord(Map.Entry<Key, EventRecord> entry) {
        Key key = entry.getKey();
        EventRecord record = entry.getValue();
        CompoundTag nbt = new CompoundTag();
        nbt.putUUID(PLAYER_KEY, key.player());
        nbt.putUUID(VILLAGER_KEY, key.villager());
        nbt.putString(EVENT_KEY, key.event().toString());
        nbt.putLong(NEXT_ELIGIBLE_AT_KEY, record.nextEligibleAt());

        if (record.story()) {
            nbt.putLong(COMPLETION_COUNT_KEY, record.completionCount());
            nbt.putLong(LAST_COMPLETED_AT_KEY, record.lastCompletedAt());
            ListTag choices = new ListTag();
            record.choices().stream().sorted().map(StringTag::valueOf).forEach(choices::add);
            nbt.put(CHOICES_KEY, choices);
        }
        return nbt;
    }

    private static long nextEligibleAt(DialogueEvent.Repeat repeat, long gameTime, RandomSource random) {
        return switch (repeat.type()) {
            case ALWAYS -> gameTime;
            case ONCE -> Long.MAX_VALUE;
            case COOLDOWN -> saturatingAdd(gameTime, rollCooldown(repeat, random));
        };
    }

    private static long rollCooldown(DialogueEvent.Repeat repeat, RandomSource random) {
        long min = repeat.minTicks();
        long max = repeat.maxTicks();
        if (min < 0L || max < min) {
            throw new IllegalArgumentException("Dialogue event cooldown tick range is invalid");
        }
        if (min == max) {
            return min;
        }

        long size = max - min + 1L;
        if (size > 0L) {
            return min + nextLongBounded(random, size);
        }

        long candidate;
        do {
            candidate = random.nextLong();
        } while (candidate < min || candidate > max);
        return candidate;
    }

    private static long nextLongBounded(RandomSource random, long bound) {
        long mask = bound - 1L;
        long value = random.nextLong();
        if ((bound & mask) == 0L) {
            return value & mask;
        }

        long unsigned = value >>> 1;
        long result;
        while (unsigned + mask - (result = unsigned % bound) < 0L) {
            unsigned = random.nextLong() >>> 1;
        }
        return result;
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment < 0L) {
            throw new IllegalArgumentException("Dialogue event cooldown must be nonnegative");
        }
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private static long saturatingIncrement(long value) {
        return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
    }

    private static Set<String> validatedChoices(Set<String> choices) {
        Set<String> copy = new HashSet<>();
        for (String choice : choices) {
            if (choice == null || choice.isBlank()) {
                throw new IllegalArgumentException("Dialogue event choice history contains a blank ID");
            }
            copy.add(choice);
        }
        return Set.copyOf(copy);
    }

    private static Key key(UUID player, UUID villager, ResourceLocation event) {
        return new Key(
                Objects.requireNonNull(player, "player"),
                Objects.requireNonNull(villager, "villager"),
                Objects.requireNonNull(event, "event"));
    }

    private static void malformed(int index, String reason) {
        MCA.LOGGER.warn("Ignoring malformed dialogue event history record {}: {}", index, reason);
    }

    private record Key(UUID player, UUID villager, ResourceLocation event) {
    }

    private record EventRecord(
            boolean story,
            long completionCount,
            long lastCompletedAt,
            long nextEligibleAt,
            Set<String> choices
    ) {
        private EventRecord {
            choices = Set.copyOf(choices);
        }

        private static EventRecord story(
                long completionCount,
                long lastCompletedAt,
                long nextEligibleAt,
                Set<String> choices
        ) {
            return new EventRecord(true, completionCount, lastCompletedAt, nextEligibleAt, choices);
        }

        private static EventRecord scheduling(long nextEligibleAt) {
            return new EventRecord(false, 0L, 0L, nextEligibleAt, Set.of());
        }

        private EventRecord withNextEligibleAt(long nextEligibleAt) {
            return new EventRecord(story, completionCount, lastCompletedAt, nextEligibleAt, choices);
        }
    }

    private record LoadedRecord(Key key, EventRecord record) {
    }
}
