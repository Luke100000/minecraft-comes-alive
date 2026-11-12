package net.conczin.mca.dialogue;

import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalDialogueContentTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void firstEncounterOffersCoherentFollowups() {
        DialogueEvent first = loadSocial("first");
        for (String reply : List.of("exploring_reply", "settling_reply",
                "spent_night_success", "spent_night_failed")) {
            DialogueEvent.Node initial = first.nodes().get(reply);
            assertNotNull(initial, "Missing introduction reply " + reply);
            assertTrue(initial.next().isPresent() || initial.choices().isPresent(),
                    () -> "Introduction ends immediately after " + reply);
            if (initial.next().isPresent()) {
                DialogueEvent.Node followup = first.nodes().get(initial.next().orElseThrow());
                assertNotNull(followup, "Missing follow-up for " + reply);
                assertTrue(followup.choices().isPresent(),
                        () -> "Introduction needs a player response after " + reply);
            }
        }
        DialogueEvent.Node noInn = first.nodes().get("spent_night_failed");
        assertTrue(noInn.next().isEmpty(), "no inn must offer its replies without an extra villager question");
        assertEquals(List.of("no_inn_travel", "no_inn_stay"), noInn.choices().orElseThrow().stream()
                .map(DialogueEvent.Choice::id).toList());
        for (DialogueEvent.Choice choice : noInn.choices().orElseThrow()) {
            DialogueEvent.Node ending = first.nodes().get(choice.next().orElseThrow());
            assertNotNull(ending);
            assertTrue(ending.complete());
        }
    }

    @Test
    void neutralSocialQuestionsNeverReachPunitiveReaction() {
        DialogueEvent chat = loadSocial("chat");
        DialogueEvent.Node opening = chat.nodes().get("prompt");
        assertNotNull(opening);
        for (DialogueEvent.Choice choice : opening.choices().orElseThrow()) {
            assertTrue(choice.next().isPresent(), "Ordinary Chat topic should route to a response");
            DialogueEvent.Node reaction = chat.nodes().get(choice.next().orElseThrow());
            assertNotNull(reaction, "Missing reaction for " + choice.id());
            for (DialogueEvent.Outcome outcome : reaction.outcomes().orElseThrow()) {
                assertTrue(outcome.actions().stream().noneMatch(action ->
                                action instanceof DialogueAction.Hearts hearts && hearts.amount() < 0),
                        () -> "Asking about " + choice.id() + " must not reduce friendship at random");
            }
        }
    }

    @Test
    void jokeReactionsAllowAnActualReply() {
        DialogueEvent joke = loadSocial("joke");
        for (String nodeId : List.of("success", "animal_success", "monster_success", "animal_fail")) {
            DialogueEvent.Node reaction = joke.nodes().get(nodeId);
            assertNotNull(reaction);
            assertTrue(reaction.next().isPresent() || reaction.choices().isPresent(),
                    () -> "Joke reply " + nodeId + " cuts the player off without a response");
        }
    }

    @Test
    void storyReactionsWithQuestionsCanContinue() {
        DialogueEvent story = loadSocial("story");
        for (String nodeId : List.of("generic_success", "exploring_success",
                "nether_success", "enderdragon_success", "wither_success",
                "exploring_fail")) {
            DialogueEvent.Node reaction = story.nodes().get(nodeId);
            assertNotNull(reaction);
            assertTrue(reaction.next().isPresent() || reaction.choices().isPresent(),
                    () -> "Story reply " + nodeId + " ends before the player can react");
        }
    }

    @Test
    void adventureStoriesCanBeToldWithoutProofButAchievementsBoostCredibility() {
        DialogueEvent story = loadSocial("story");
        Map<String, String> evidence = Map.of(
                "nether", "minecraft:nether/root",
                "enderdragon", "minecraft:end/kill_dragon",
                "wither", "minecraft:nether/summon_wither"
        );
        for (var entry : evidence.entrySet()) {
            String topic = entry.getKey();
            DialogueEvent.Choice choice = story.nodes().get("prompt").choices().orElseThrow().stream()
                    .filter(candidate -> candidate.id().equals(topic))
                    .findFirst().orElseThrow();

            assertTrue(DialogueEngine.choiceEligible(choice, condition -> false),
                    () -> "Players without achievements should be allowed to tell a " + topic + " story");

            var unverified = DialogueEngine.eligibleOutcomes(choice, condition -> false);
            assertTrue(unverified.stream().anyMatch(outcome -> outcome.next().equals(topic + "_fail")),
                    () -> "Unverified " + topic + " stories need a skeptical reaction");

            var verified = DialogueEngine.eligibleOutcomes(choice, condition ->
                    condition instanceof DialogueCondition.Defined defined
                            && defined.type().equals(DialogueCondition.ADVANCEMENT)
                            && defined.definition().get("value").getAsString().equals(entry.getValue()));
            double unverifiedSuccess = unverified.stream()
                    .filter(outcome -> outcome.next().equals(topic + "_success"))
                    .mapToDouble(DialogueEvent.Outcome::weight).sum();
            double verifiedSuccess = verified.stream()
                    .filter(outcome -> outcome.next().equals(topic + "_success"))
                    .mapToDouble(DialogueEvent.Outcome::weight).sum();
            assertTrue(verifiedSuccess > unverifiedSuccess,
                    () -> "Recorded achievement should make the " + topic + " story more credible");
        }
    }


    @Test
    void storyMemoriesAreScopedToEachTopic() {
        DialogueEvent story = loadSocial("story");
        Map<String, Long> durations = Map.of("exploring", 24000L, "nether", 2400000L,
                "enderdragon", 2400000L, "wither", 2400000L);
        Map<String, Integer> skepticism = Map.of("exploring", 3, "nether", 10,
                "enderdragon", 10, "wither", 10);
        Map<String, Integer> hearts = Map.of("exploring", -4, "nether", -4,
                "enderdragon", -5, "wither", -6);

        var choices = story.nodes().get("prompt").choices().orElseThrow();
        assertTrue(choices.stream().allMatch(choice ->
                        DialogueEngine.choiceEligible(choice, condition -> false)),
                "Stories should never require achievements to appear");
        for (var entry : durations.entrySet()) {
            String topic = entry.getKey();
            DialogueEvent.Choice choice = choices.stream()
                    .filter(candidate -> candidate.id().equals(topic)).findFirst().orElseThrow();
            var outcomes = choice.outcomes().orElseThrow();
            var expectedMemory = new DialogueAction.Remember("story." + topic, true,
                    java.util.OptionalLong.of(entry.getValue()));
            for (var outcome : outcomes) {
                assertEquals(List.of(expectedMemory), outcome.actions().stream()
                        .filter(DialogueAction.Remember.class::isInstance).toList(),
                        "Every " + topic + " outcome must remember this player's telling");
            }
            var remembered = outcomes.stream().filter(outcome ->
                    outcome.requirements().stream().anyMatch(requirement ->
                            requirement instanceof DialogueCondition.Defined condition
                                    && condition.type().equals(DialogueCondition.MEMORY))).toList();
            assertEquals(1, remembered.size(), "Exactly one remembered-story skeptical outcome for " + topic);
            var skeptical = remembered.getFirst();
            assertEquals(skepticism.get(topic).doubleValue(), skeptical.weight());
            assertEquals(topic + "_fail", skeptical.next());
            assertTrue(skeptical.actions().contains(new DialogueAction.Hearts(hearts.get(topic))));
            assertEquals(0.0, skeptical.fatigue(), "Story memory must not double-count fatigue");
            var condition = (DialogueCondition.Defined) skeptical.requirements().getFirst();
            assertEquals("story." + topic, condition.definition().get("id").getAsString());
            assertEquals("player", condition.definition().get("var").getAsString());
            assertTrue(condition.definition().get("present").getAsBoolean());
            assertEquals(outcomes.size() - 1, DialogueEngine.eligibleOutcomes(choice, requirement -> !(requirement instanceof DialogueCondition.Defined definition
                    && definition.type().equals(DialogueCondition.MEMORY))).size(),
                    "Without memory, only the new skeptical outcome should be removed for " + topic);
        }
        DialogueEvent.Choice generic = choices.stream()
                .filter(choice -> choice.id().equals("generic")).findFirst().orElseThrow();
        assertTrue(generic.outcomes().orElseThrow().stream().noneMatch(outcome ->
                outcome.actions().stream().anyMatch(DialogueAction.Remember.class::isInstance)));
    }



    @Test
    void storyFatigueMarksOnlyOriginalSkepticalOutcomes() {
        DialogueEvent story = loadSocial("story");
        for (DialogueEvent.Choice choice : story.nodes().get("prompt").choices().orElseThrow()) {
            var marked = choice.outcomes().orElseThrow().stream()
                    .filter(outcome -> outcome.fatigue() > 0.0).toList();
            assertEquals(1, marked.size(), "Exactly one original skeptical outcome should carry fatigue for " + choice.id());
            var skeptical = marked.getFirst();
            assertEquals(1.0, skeptical.fatigue());
            assertEquals(choice.id() + "_fail", skeptical.next());
            assertTrue(skeptical.requirements().isEmpty(),
                    "Remembered-story skepticism should remain an independent, unmarked outcome");
        }
    }


    private static DialogueEvent loadSocial(String name) {
        String resource = "data/mca/dialogue_events/social/" + name + ".json";
        var stream = Objects.requireNonNull(
                NaturalDialogueContentTest.class.getClassLoader().getResourceAsStream(resource),
                "Missing event resource " + resource);
        try (stream; var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return DialogueEvent.decode(ResourceLocation.fromNamespaceAndPath("mca", "social/" + name),
                    JsonParser.parseReader(reader).getAsJsonObject());
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Cannot read " + resource, exception);
        }
    }
}
