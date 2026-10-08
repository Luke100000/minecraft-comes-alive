package net.conczin.mca.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McaDialogueEventResourcesTest {
    private static final String RESOURCE_ROOT = "data/mca/dialogue_events";
    private static final ResourceLocation BASELINE_ID = id("ambient/baseline");
    private static final Set<ResourceLocation> TASK_10_EVENTS = Set.of(
            id("ambient/root/generic"),
            id("ambient/root/morning_early"),
            id("ambient/root/morning_late"),
            id("ambient/root/evening"),
            id("ambient/root/night"),
            id("ambient/root/mayor"),
            id("ambient/root/monarch"),
            id("ambient/root/negative"),
            id("social/first"),
            id("ambient/greet"),
            id("social/chat"),
            id("social/joke"),
            id("social/story"),
            id("social/rumors"),
            id("social/rock_paper_scissor")
    );
    private static final Set<ResourceLocation> TASK_11_EVENTS = Set.of(
            id("relationship/apologize"),
            id("relationship/flirt"),
            id("relationship/hug"),
            id("relationship/kiss"),
            id("gameplay/hire"),
            id("relationship/procreate"),
            id("relationship/procreate_engaged"),
            id("relationship/divorce"),
            id("gameplay/divorce_papers"),
            id("relationship/adopt"),
            id("gameplay/stay"),
            id("personal/gloomy_reflection"),
            id("personal/gloomy_reflection_followup"),
            id("ambient/crabby_night"),
            id("ambient/rain_relaxed"),
            id("personal/lactose_intolerance"),
            id("personal/mourning"),
            id("personal/cured_identity"),
            id("personal/cured_zombies"),
            id("personal/cured_zombies_followup"),
            id("personal/revived"),
            id("location/infirmary"),
            id("location/prison")
    );

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyShippedDialogueEventDecodesAndBaselineAlwaysProvidesAmbientTalk() throws Exception {
        List<DialogueEvent> events = loadShippedEvents();
        JsonObject translations = englishTranslations();

        assertFalse(events.isEmpty(), "MCA must ship at least one dialogue event");
        assertEquals(events.size(), events.stream().map(DialogueEvent::id).distinct().count(),
                "shipped event IDs must be unique");
        assertTrue(events.stream().allMatch(event -> "mca".equals(event.id().getNamespace())),
                "MCA-shipped dialogue resources must retain the mca namespace");
        assertTrue(events.stream().map(DialogueEvent::id).collect(java.util.stream.Collectors.toSet())
                        .containsAll(TASK_10_EVENTS),
                "Task 10 ordinary/social migrations must all be present");
        assertTrue(events.stream().map(DialogueEvent::id).collect(java.util.stream.Collectors.toSet())
                        .containsAll(TASK_11_EVENTS),
                "Task 11 command-backed migrations and reference stories must all be present");
        long task11ResumePromptCount = events.stream()
                .filter(event -> TASK_11_EVENTS.contains(event.id()))
                .map(event -> event.presentation().resumePrompt())
                .distinct()
                .count();
        assertEquals(TASK_11_EVENTS.size(), task11ResumePromptCount,
                "every Task 11 event must author its own event-specific resume prompt");

        Map<ResourceLocation, DialogueEvent> byId = new HashMap<>();
        events.forEach(event -> byId.put(event.id(), event));
        for (DialogueEvent event : events) {
            assertTrue(hasExactTranslation(translations, event.presentation().resumePrompt()),
                    () -> "missing exact resume translation for " + event.id() + ": "
                            + event.presentation().resumePrompt());
            event.presentation().prompt().ifPresent(prompt ->
                    assertTrue(hasExactTranslation(translations, prompt),
                            () -> "missing exact selectable prompt translation for " + event.id() + ": " + prompt));
            for (DialogueEvent.Node node : event.nodes().values()) {
                for (String line : node.lines()) {
                    assertTrue(hasLineTranslation(translations, line),
                            () -> "missing line translation for " + event.id() + ": " + line);
                }
                node.outcomes().orElse(List.of()).forEach(outcome ->
                        outcome.requirements().forEach(condition -> assertEventReference(condition, byId)));
                for (DialogueEvent.Choice choice : node.choices().orElse(List.of())) {
                    assertTrue(hasExactTranslation(translations, choice.text()),
                            () -> "missing exact choice translation for " + event.id() + ": " + choice.text());
                    choice.requirements().forEach(condition -> assertEventReference(condition, byId));
                    choice.outcomes().orElse(List.of()).forEach(outcome ->
                            outcome.requirements().forEach(condition -> assertEventReference(condition, byId)));
                }
            }
            event.requirements().forEach(condition -> assertEventReference(condition, byId));

            if (TASK_11_EVENTS.contains(event.id())) {
                assertTask11PresentationAndGraph(event, translations);
            }

            if (TASK_10_EVENTS.contains(event.id())) {
                for (DialogueEvent.Node node : event.nodes().values()) {
                    node.outcomes().orElse(List.of()).forEach(outcome ->
                            assertNoDuplicatedHeartMoodEffect(event, outcome.actions()));
                    for (DialogueEvent.Choice choice : node.choices().orElse(List.of())) {
                        assertNoDuplicatedHeartMoodEffect(event, choice.actions());
                        choice.outcomes().orElse(List.of()).forEach(outcome ->
                                assertNoDuplicatedHeartMoodEffect(event, outcome.actions()));
                    }
                }
            }
        }

        DialogueEvent baseline = events.stream()
                .filter(event -> event.id().equals(BASELINE_ID))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing shipped baseline event " + BASELINE_ID));

        assertEquals(DialogueEvent.Trigger.TALK, baseline.trigger());
        assertEquals(DialogueEvent.PresentationMode.AMBIENT, baseline.presentation().mode());
        assertTrue(baseline.requirements().isEmpty(), "baseline must not depend on contextual state");
        assertEquals(DialogueEvent.RepeatType.COOLDOWN, baseline.repeat().type());
        assertEquals(0L, baseline.repeat().minTicks());
        assertEquals(0L, baseline.repeat().maxTicks());
        assertEquals(DialogueEvent.HistoryPolicy.SCHEDULING, baseline.history());
        assertTrue(DialogueEngine.repeatAvailable(baseline, false, 0L, 0L));

        int lowestPriority = events.stream().map(DialogueEvent::priority).min(Comparator.naturalOrder()).orElseThrow();
        assertEquals(lowestPriority, baseline.priority(), "baseline must remain the last-resort ambient tier");

        DialogueEngine.SelectionPlan fallbackPlan = DialogueEngine.planSelection(
                List.of(baseline),
                event -> DialogueEngine.repeatAvailable(event, false, 0L, 0L),
                null
        );
        assertEquals(List.of(BASELINE_ID), fallbackPlan.ambient().stream().map(DialogueEvent::id).toList());

        DialogueEvent.Node start = baseline.nodes().get(baseline.start());
        assertEquals(List.of("dialogue.main"), start.lines());
        assertTrue(start.complete());
        assertTrue(hasLineTranslation(translations, "dialogue.main"),
                "baseline line must resolve through MCA's pooled dialogue translations");

        DialogueEvent chat = byId.get(id("social/chat"));
        DialogueEvent.Node chatStart = chat.nodes().get(chat.start());
        assertTrue(chatStart.lines().isEmpty(), "Chat selection must route internally before rendering a villager line");
        assertTrue(chatStart.choices().isEmpty(), "Chat selection must not require a throwaway player confirmation");
        assertTrue(chatStart.outcomes().isPresent(), "Chat must preserve the legacy automatic weighted reaction");

        DialogueEvent rumors = byId.get(id("social/rumors"));
        DialogueEvent.Node rumorsStart = rumors.nodes().get(rumors.start());
        assertTrue(rumorsStart.lines().isEmpty(), "Rumors selection must preserve the legacy automatic weighted draw");
        assertTrue(rumorsStart.choices().isEmpty(), "Rumors selection must not require a throwaway ask confirmation");
        assertTrue(rumorsStart.outcomes().isPresent(), "Rumors must preserve the legacy automatic weighted outcomes");

        DialogueEvent first = byId.get(id("social/first"));
        DialogueEvent.Node firstStart = first.nodes().get(first.start());
        assertTrue(firstStart.lines().isEmpty(), "First contact must preserve the legacy automatic opening draw");
        assertTrue(firstStart.choices().isEmpty(), "First contact must not force a reply before its opening draw");
        assertTrue(firstStart.outcomes().orElseThrow().stream()
                        .allMatch(outcome -> hasPlayerSeenRemember(outcome.actions())),
                "all first-contact opening outcomes must preserve legacy player-scoped seen memory");

    }

    @Test
    void duplicateLocalizationSourceKeysAreRejectedBeforeGsonCollapse() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                assertNoDuplicateTranslationKeys(
                        "duplicate-fixture.json",
                        new StringReader("{\"dialogue.same\":\"first\",\"dialogue.same\":\"second\"}")
                ));

        assertTrue(exception.getMessage().contains("dialogue.same"));
        assertTrue(exception.getMessage().contains("duplicate-fixture.json"));
    }

    private static void assertTask11PresentationAndGraph(DialogueEvent event, JsonObject translations) {
        assertTrue(event.nodes().containsKey(event.start()),
                () -> "Task 11 event start node must decode for " + event.id());
        assertTrue(hasExactTranslation(translations, event.presentation().resumePrompt()),
                () -> "Task 11 event resume prompt must resolve for " + event.id());
        if (event.presentation().mode() != DialogueEvent.PresentationMode.AMBIENT) {
            String prompt = event.presentation().prompt()
                    .orElseThrow(() -> new AssertionError("Task 11 selectable event missing prompt " + event.id()));
            assertTrue(hasExactTranslation(translations, prompt),
                    () -> "Task 11 event prompt must resolve for " + event.id());
        }
    }

    private static void assertNoDuplicateTranslationKeys(String source, Reader sourceReader) throws IOException {
        Set<String> keys = new HashSet<>();
        try (JsonReader reader = new JsonReader(sourceReader)) {
            reader.beginObject();
            while (reader.hasNext()) {
                String key = reader.nextName();
                if (!keys.add(key)) {
                    throw new IllegalArgumentException("Duplicate localization key '" + key + "' in " + source);
                }
                reader.skipValue();
            }
            reader.endObject();
        }
    }

    private static boolean hasPlayerSeenRemember(List<DialogueAction> actions) {
        return actions.stream().anyMatch(action ->
                action instanceof DialogueAction.Remember remember
                        && "seen".equals(remember.id())
                        && remember.playerScoped());
    }

    private static void assertNoDuplicatedHeartMoodEffect(DialogueEvent event, List<DialogueAction> actions) {
        Set<Integer> heartAmounts = actions.stream()
                .filter(DialogueAction.Hearts.class::isInstance)
                .map(DialogueAction.Hearts.class::cast)
                .map(DialogueAction.Hearts::amount)
                .collect(java.util.stream.Collectors.toSet());
        for (DialogueAction action : actions) {
            if (action instanceof DialogueAction.Mood mood) {
                assertFalse(heartAmounts.contains(mood.amount()),
                        () -> event.id() + " duplicates rewardHearts' mood side effect with mca:mood "
                                + mood.amount());
            }
        }
    }

    private static List<DialogueEvent> loadShippedEvents() throws IOException, URISyntaxException {
        var root = Objects.requireNonNull(
                McaDialogueEventResourcesTest.class.getClassLoader().getResource(RESOURCE_ROOT),
                "Missing shipped dialogue event resource directory " + RESOURCE_ROOT
        );
        Path rootPath = Path.of(root.toURI());
        try (var files = Files.walk(rootPath)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .map(path -> decode(rootPath, path))
                    .toList();
        }
    }

    private static DialogueEvent decode(Path root, Path path) {
        String eventPath = root.relativize(path).toString().replace('\\', '/');
        eventPath = eventPath.substring(0, eventPath.length() - ".json".length());
        try {
            JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
            return DialogueEvent.decode(id(eventPath), json);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read shipped dialogue event " + path, exception);
        }
    }

    private static JsonObject englishTranslations() throws IOException {
        JsonObject translations = new JsonObject();
        for (String path : List.of("assets/mca/lang/en_us.json", "assets/mca_dialogue/lang/en_us.json")) {
            var stream = Objects.requireNonNull(
                    McaDialogueEventResourcesTest.class.getClassLoader().getResourceAsStream(path),
                    "Missing translations " + path
            );
            try (stream) {
                String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                assertNoDuplicateTranslationKeys(path, new StringReader(source));
                JsonParser.parseString(source).getAsJsonObject().entrySet()
                        .forEach(entry -> translations.add(entry.getKey(), entry.getValue()));
            }
        }
        return translations;
    }

    private static boolean hasExactTranslation(JsonObject translations, String key) {
        return translations.has(key);
    }

    private static boolean hasLineTranslation(JsonObject translations, String key) {
        if (hasExactTranslation(translations, key)) {
            return true;
        }
        String prefix = key + "/";
        return translations.keySet().stream().anyMatch(candidate -> candidate.startsWith(prefix));
    }

    private static void assertEventReference(
            DialogueCondition condition,
            Map<ResourceLocation, DialogueEvent> events
    ) {
        if (condition instanceof DialogueCondition.EventCompleted completed) {
            assertTrue(events.containsKey(completed.event()),
                    () -> "event_completed references missing event " + completed.event());
            return;
        }
        if (condition instanceof DialogueCondition.EventChoice choice) {
            DialogueEvent referenced = events.get(choice.event());
            assertTrue(referenced != null, () -> "event_choice references missing event " + choice.event());
            Set<String> choiceIds = new HashSet<>();
            referenced.nodes().values().forEach(node ->
                    node.choices().orElse(List.of()).forEach(candidate -> choiceIds.add(candidate.id())));
            assertTrue(choiceIds.contains(choice.choiceId()),
                    () -> "event_choice references missing choice " + choice.event() + "#" + choice.choiceId());
            return;
        }
        if (condition instanceof DialogueCondition.Not not) {
            assertEventReference(not.condition(), events);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("mca", path);
    }
}
