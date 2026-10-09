package net.conczin.mca.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class PersonalityDialogueLibraryTest {
    private record Entry(String name, String story, String first, String second, String contextType) { }

    private static final List<Entry> COVERAGE = List.of(
            new Entry("gloomy", "personal/gloomy_reflection", "ambient/personality_gloomy_tomorrow", "ambient/personality_gloomy_bright_spot", "mca:profession"),
            new Entry("friendly", "personal/personality_friendly", "ambient/personality_friendly_newcomer", "ambient/personality_friendly_chores", "mca:profession"),
            new Entry("playful", "personal/personality_playful", "ambient/personality_playful_contest", "ambient/personality_playful_rain_game", "mca:weather"),
            new Entry("sensitive", "personal/personality_sensitive", "ambient/personality_sensitive_kindness", "ambient/personality_sensitive_tone", "mca:time"),
            new Entry("flirty", "personal/personality_flirty", "ambient/personality_flirty_compliments", "ambient/personality_flirty_gesture", "mca:age_group"),
            new Entry("odd", "personal/personality_odd", "ambient/personality_odd_observations", "ambient/personality_odd_objects", "mca:weather"),
            new Entry("introverted", "personal/personality_introverted", "ambient/personality_introverted_hobbies", "ambient/personality_introverted_quiet_spot", "mca:time"),
            new Entry("greedy", "personal/personality_greedy", "ambient/personality_greedy_bargain", "ambient/personality_greedy_fairness", "mca:profession"),
            new Entry("crabby", "personal/personality_crabby", "ambient/crabby_night", "ambient/personality_crabby_good_work", "mca:time"),
            new Entry("relaxed", "personal/personality_relaxed", "ambient/rain_relaxed", "ambient/personality_relaxed_afternoon", "mca:weather"),
            new Entry("anxious", "personal/personality_anxious", "ambient/personality_anxious_supplies", "ambient/personality_anxious_sound", "mca:recent_event"),
            new Entry("peaceful", "personal/personality_peaceful", "ambient/personality_peaceful_compromise", "ambient/personality_peaceful_morning", "mca:profession"),
            new Entry("upbeat", "personal/personality_upbeat", "ambient/personality_upbeat_small_win", "ambient/personality_upbeat_prediction", "mca:weather"),
            new Entry("extroverted", "personal/personality_extroverted", "ambient/personality_extroverted_square", "ambient/personality_extroverted_neighbors", "mca:time")
    );

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void completeFourteenPersonalityMatrix() throws Exception {
        assertEquals(14, COVERAGE.size());
        assertEquals(14, COVERAGE.stream().map(Entry::name).distinct().count());
        for (Entry entry : COVERAGE) {
            assertPersonalityCoverage(entry);
        }
    }

    @Test
    void defaultStoryEligibilityIsNotRarelyGated() throws Exception {
        for (Entry entry : COVERAGE) {
            JsonObject story = json(entry.story());
            JsonArray requirements = story.getAsJsonArray("requirements");
            assertEquals(2, requirements.size(), entry.story() + " may only require personality and 20 hearts");
            assertTrue(containsCondition(requirements, "mca:personality", "value", entry.name()), entry.story());
            assertTrue(containsCondition(requirements, "mca:hearts", "min", "20"), entry.story());
            JsonObject start = story.getAsJsonObject("nodes").getAsJsonObject(story.get("start").getAsString());
            assertFalse(start.has("line") || start.has("lines"), entry.story() + " must route before text");
            JsonArray outcomes = start.getAsJsonArray("outcomes");
            assertNotNull(outcomes, entry.story());
            assertTrue(outcomes.size() >= 2, entry.story());
            assertTrue(containsConditionalRoute(outcomes, entry.contextType()), entry.story());
            assertTrue(containsUnconditionalRoute(outcomes), entry.story() + " must have a fallback");
        }
    }

    @Test
    void flirtyRomanceIsOnlyAnOptionalAdultRoute() throws Exception {
        JsonObject story = json("personal/personality_flirty");
        JsonArray outcomes = story.getAsJsonObject("nodes").getAsJsonObject("start").getAsJsonArray("outcomes");
        assertTrue(outcomes.toString().contains("mca:age_group"));
        assertTrue(outcomes.toString().contains("adult"));
        assertTrue(containsUnconditionalRoute(outcomes));
        assertFalse(story.getAsJsonArray("requirements").toString().contains("age_group"));
    }

    @Test
    void fourChoiceDependentFollowupsHaveValidReferences() throws Exception {
        String[][] pairs = {
                {"personal/gloomy_reflection_followup", "personal/gloomy_reflection", "comfort"},
                {"personal/personality_playful_followup", "personal/personality_playful", "apologize"},
                {"personal/personality_greedy_followup", "personal/personality_greedy", "share"},
                {"personal/personality_anxious_followup", "personal/personality_anxious", "make_plan"}
        };
        for (String[] pair : pairs) {
            JsonObject followup = json(pair[0]);
            JsonArray requirements = followup.getAsJsonArray("requirements");
            assertTrue(containsCondition(requirements, "mca:event_completed", "event", "mca:" + pair[1]), pair[0]);
            assertTrue(containsCondition(requirements, "mca:event_choice", "choice", pair[2]), pair[0]);
            assertEquals("story", json(pair[1]).get("history").getAsString());
            assertEquals("ask", followup.getAsJsonObject("presentation").get("mode").getAsString());
            assertEquals("scheduling", followup.get("history").getAsString());
        }
    }

    @Test
    void ambientPersonalitiesShareTheOrdinarySelectionTier() throws Exception {
        List<DialogueEvent> candidates = List.of(
                decoded("ambient/personality_friendly_newcomer"),
                decoded("ambient/personality_friendly_chores"),
                decoded("ambient/greet"),
                decoded("ambient/root/generic"),
                decoded("ambient/baseline")
        );
        DialogueEngine.SelectionPlan plan = DialogueEngine.planSelection(candidates, event -> true, null);
        assertEquals(Set.of(
                ResourceLocation.fromNamespaceAndPath("mca", "ambient/personality_friendly_newcomer"),
                ResourceLocation.fromNamespaceAndPath("mca", "ambient/personality_friendly_chores"),
                ResourceLocation.fromNamespaceAndPath("mca", "ambient/greet"),
                ResourceLocation.fromNamespaceAndPath("mca", "ambient/root/generic")
        ), plan.ambient().stream().map(DialogueEvent::id).collect(Collectors.toSet()));
    }

    @Test
    void gloomyChoicesStayStableForRecordedFollowups() throws Exception {
        JsonObject original = json("personal/gloomy_reflection");
        JsonArray choices = original.getAsJsonObject("nodes").getAsJsonObject("intro").getAsJsonArray("choices");
        Set<String> ids = new HashSet<>();
        for (var choice : choices) ids.add(choice.getAsJsonObject().get("id").getAsString());
        assertEquals(Set.of("comfort", "give_space"), ids);
    }

    @Test
    void followupDoesNotInventNightOrRepairs() throws Exception {
        JsonObject locale = translations();
        for (String suffix : List.of("prompt", "resume", "remember", "small_step", "progress", "morning")) {
            String line = locale.get("dialogue_event.mca.personal.gloomy_reflection_followup." + suffix).getAsString();
            assertFalse(line.toLowerCase(java.util.Locale.ROOT).matches(
                    ".*(night|sunrise|morning|latch|repair|fixed|door).*"), suffix + ": " + line);
        }
        String repair = locale.get("dialogue_event.mca.ambient.personality_crabby_good_work.line.0").getAsString();
        assertFalse(repair.contains("has been") || repair.contains("properly fixed"), repair);
    }

    @Test
    void hourIndependentAmbientTopicsCannotAssumeAfternoonOrSunset() throws Exception {
        JsonObject locale = translations();
        for (String subject : List.of("relaxed_afternoon", "upbeat_prediction")) {
            JsonObject ambient = json("ambient/personality_" + subject);
            assertEquals(1, ambient.getAsJsonArray("requirements").size());
            JsonArray passages = ambient.getAsJsonObject("nodes").getAsJsonObject("main").getAsJsonArray("lines");
            for (var key : passages) {
                String line = locale.get(key.getAsString()).getAsString();
                assertFalse(line.toLowerCase(java.util.Locale.ROOT).matches(
                        ".*(afternoon|sunset|before sunrise|before dusk).*"), line);
            }
        }
    }

    @Test
    void personalityAmbientResumeLabelsAreSpecificAndNatural() throws Exception {
        JsonObject locale = translations();
        for (Entry entry : COVERAGE) {
            for (String path : List.of(entry.first(), entry.second())) {
                if (path.equals("ambient/crabby_night") || path.equals("ambient/rain_relaxed")) continue;
                String key = json(path).getAsJsonObject("presentation").get("resume_prompt").getAsString();
                String label = locale.get(key).getAsString();
                assertFalse(label.startsWith("We were talking about "), key + ": " + label);
                assertTrue(label.length() >= 22, key + " should describe the conversation");
            }
        }
    }

    private static JsonObject translations() throws IOException {
        try (InputStream input = PersonalityDialogueLibraryTest.class.getClassLoader()
                .getResourceAsStream("assets/mca_dialogue/lang/en_us.json")) {
            assertNotNull(input);
            return JsonParser.parseString(new String(input.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void assertPersonalityCoverage(Entry entry) throws Exception {
        JsonObject story = json(entry.story());
        assertEquals("highlighted", story.getAsJsonObject("presentation").get("mode").getAsString());
        assertEquals("story", story.get("history").getAsString());
        assertEquals(40, story.get("priority").getAsInt());
        assertCooldown(story, 300, 900);
        JsonObject nodes = story.getAsJsonObject("nodes");
        Set<String> choices = new HashSet<>();
        int endings = 0;
        for (var nodeEntry : nodes.entrySet()) {
            JsonObject node = nodeEntry.getValue().getAsJsonObject();
            if (node.has("choices")) {
                for (var choice : node.getAsJsonArray("choices")) {
                    assertTrue(choices.add(choice.getAsJsonObject().get("id").getAsString()), entry.story());
                }
            }
            if (node.has("complete") && node.get("complete").getAsBoolean()) {
                endings++;
                assertTrue(node.has("lines") && node.getAsJsonArray("lines").size() >= 2, entry.story());
            }
        }
        assertTrue(choices.size() >= 2, entry.story() + " needs choices");
        assertTrue(endings >= 2, entry.story() + " needs distinct endings");
        assertTrue(nodes.has("intro") && nodes.getAsJsonObject("intro").has("lines")
                && nodes.getAsJsonObject("intro").getAsJsonArray("lines").size() >= 4,
                entry.story() + " needs multiple short passages before choosing");
        for (String path : List.of(entry.first(), entry.second())) {
            JsonObject ambient = json(path);
            assertEquals("ambient", ambient.getAsJsonObject("presentation").get("mode").getAsString(), path);
            assertEquals("scheduling", ambient.get("history").getAsString(), path);
            assertEquals(0, ambient.get("priority").getAsInt(), path);
            assertEquals(10, ambient.get("weight").getAsInt(), path);
            assertTrue(containsCondition(ambient.getAsJsonArray("requirements"), "mca:personality", "value", entry.name()), path);
            assertCooldown(ambient, 60, 180);
        }
    }

    private static void assertCooldown(JsonObject json, int min, int max) {
        JsonObject repeat = json.getAsJsonObject("repeat");
        assertEquals("cooldown", repeat.get("type").getAsString());
        assertEquals(min, repeat.get("min_seconds").getAsInt());
        assertEquals(max, repeat.get("max_seconds").getAsInt());
    }

    private static boolean containsCondition(JsonArray requirements, String type, String field, String expected) {
        if (requirements == null) return false;
        for (var requirement : requirements) {
            JsonObject condition = requirement.getAsJsonObject();
            if (type.equals(condition.get("type").getAsString()) && condition.has(field)
                    && expected.equals(condition.get(field).getAsString())) return true;
        }
        return false;
    }

    private static boolean containsConditionalRoute(JsonArray outcomes, String type) {
        for (var outcome : outcomes) {
            JsonObject route = outcome.getAsJsonObject();
            if (route.has("requirements")) {
                for (var condition : route.getAsJsonArray("requirements")) {
                    if (type.equals(condition.getAsJsonObject().get("type").getAsString())) return true;
                }
            }
        }
        return false;
    }

    private static boolean containsUnconditionalRoute(JsonArray outcomes) {
        for (var outcome : outcomes) {
            JsonObject route = outcome.getAsJsonObject();
            if (!route.has("requirements") || route.getAsJsonArray("requirements").isEmpty()) return true;
        }
        return false;
    }

    private static JsonObject json(String relativeId) throws IOException {
        String path = "data/mca/dialogue_events/" + relativeId + ".json";
        try (InputStream input = PersonalityDialogueLibraryTest.class.getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, "Missing personality dialogue event " + relativeId);
            String content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            JsonObject json = JsonParser.parseString(content).getAsJsonObject();
            DialogueEvent.decode(ResourceLocation.fromNamespaceAndPath("mca", relativeId), json);
            return json;
        }
    }

    private static DialogueEvent decoded(String relativeId) throws IOException {
        return DialogueEvent.decode(ResourceLocation.fromNamespaceAndPath("mca", relativeId), json(relativeId));
    }
}
