package net.conczin.mca.dialogue;

import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueSelectionTest {
    @Test
    void continuationPromptUsesAuthoredKeyAndVillagerNameArgument() {
        DialogueEvent event = event("test:resume", "ask", 1, 1.0, "always");
        Component villagerName = Component.literal("Mara");

        Component prompt = DialogueEngine.continuationPrompt(event, villagerName);

        TranslatableContents contents = (TranslatableContents) prompt.getContents();
        assertEquals("dialogue.resume", contents.getKey());
        assertEquals(1, contents.getArgs().length);
        assertEquals(villagerName, contents.getArgs()[0]);
    }

    @Test
    void menuOfferTokenIsSingleUseAndGenerationBound() {
        DialogueEvent offered = event("test:offered", "ask", 1, 1.0, "always");
        DialogueEngine.DialogueOptions options = new DialogueEngine.DialogueOptions(
                UUID.fromString("00000000-0000-0000-0000-000000000010"),
                7L,
                Optional.empty(),
                List.of(new DialogueEngine.EventOption(offered.id(), net.minecraft.network.chat.Component.literal("offered"))),
                false,
                Optional.empty(),
                101L,
                List.of(),
                Optional.empty()
        );

        assertTrue(DialogueEngine.acceptsMenuOffer(options, 101L, 7L));
        assertFalse(DialogueEngine.acceptsMenuOffer(options, 102L, 7L));
        assertFalse(DialogueEngine.acceptsMenuOffer(options, 101L, 8L));
        assertTrue(options.containsEvent(offered.id()));
        assertFalse(options.containsEvent(ResourceLocation.parse("test:forged")));

        options.consume();
        assertFalse(DialogueEngine.acceptsMenuOffer(options, 101L, 7L));
    }

    @Test
    void highlightedOverflowAskAndAmbientUseCanonicalPriorityRules() {
        DialogueEvent highlightedB = event("test:highlighted_b", "highlighted", 9, 1.0, "always");
        DialogueEvent highlightedA = event("test:highlighted_a", "highlighted", 9, 1.0, "always");
        DialogueEvent lowerHighlighted = event("test:highlighted_low", "highlighted", 2, 1.0, "always");
        DialogueEvent ask = event("test:ask", "ask", 7, 1.0, "always");
        DialogueEvent ambientLow = event("test:ambient_low", "ambient", 3, 100.0, "always");
        DialogueEvent ambientHighB = event("test:ambient_high_b", "ambient", 8, 3.0, "always");
        DialogueEvent ambientHighA = event("test:ambient_high_a", "ambient", 8, 1.0, "always");

        DialogueEngine.SelectionPlan plan = DialogueEngine.planSelection(
                List.of(ambientLow, highlightedB, ask, ambientHighB, lowerHighlighted, highlightedA, ambientHighA),
                event -> true,
                null
        );

        assertEquals(highlightedA.id(), plan.highlighted().orElseThrow().id());
        assertEquals(
                List.of(highlightedB.id(), ask.id(), lowerHighlighted.id()),
                plan.ask().stream().map(DialogueEvent::id).toList()
        );
        assertEquals(
                List.of(ambientHighA.id(), ambientHighB.id()),
                plan.ambient().stream().map(DialogueEvent::id).toList()
        );
    }

    @Test
    void hardEligibilityAndPausedEventAreExcludedBeforeClassification() {
        DialogueEvent eligible = event("test:eligible", "ask", 1, 1.0, "always");
        DialogueEvent ineligible = event("test:ineligible", "highlighted", 99, 1.0, "always");
        DialogueEvent paused = event("test:paused", "ambient", 99, 1.0, "always");

        DialogueEngine.SelectionPlan plan = DialogueEngine.planSelection(
                List.of(ineligible, paused, eligible),
                event -> !event.id().equals(ineligible.id()),
                paused.id()
        );

        assertTrue(plan.highlighted().isEmpty());
        assertEquals(List.of(eligible.id()), plan.ask().stream().map(DialogueEvent::id).toList());
        assertTrue(plan.ambient().isEmpty());
    }

    @Test
    void menuSelectionPlanNeverExceedsWireOptionBudget() {
        List<DialogueEvent> events = new ArrayList<>();
        events.add(event("test:highlighted", "highlighted", 100, 1.0, "always"));
        for (int i = 0; i < DialogueEngine.MAX_EVENT_OPTIONS + 10; i++) {
            events.add(event("test:ask_" + i, "ask", i, 1.0, "always"));
        }

        DialogueEngine.SelectionPlan plan = DialogueEngine.planSelection(events, event -> true, null);

        assertTrue(plan.highlighted().isPresent());
        assertEquals(
                DialogueEngine.MAX_EVENT_OPTIONS,
                1 + plan.ask().size(),
                "highlighted plus Ask entries must fit the bounded options packet"
        );
    }

    @Test
    void repeatAvailabilityDistinguishesAlwaysOnceAndCooldown() {
        DialogueEvent always = event("test:always", "ask", 0, 1.0, "always");
        DialogueEvent once = event("test:once", "ask", 0, 1.0, "once");
        DialogueEvent cooldown = event("test:cooldown", "ask", 0, 1.0, "cooldown");

        assertTrue(DialogueEngine.repeatAvailable(always, true, Long.MAX_VALUE, 100L));
        assertTrue(DialogueEngine.repeatAvailable(once, false, 0L, 100L));
        assertFalse(DialogueEngine.repeatAvailable(once, true, Long.MAX_VALUE, 100L));
        assertFalse(DialogueEngine.repeatAvailable(cooldown, true, 101L, 100L));
        assertTrue(DialogueEngine.repeatAvailable(cooldown, true, 100L, 100L));
    }

    @Test
    void weightedPickUsesInjectedRandomAndOnlyHighestAmbientTier() {
        DialogueEvent a = event("test:a", "ambient", 5, 1.0, "always");
        DialogueEvent b = event("test:b", "ambient", 5, 3.0, "always");
        DialogueEvent lower = event("test:lower", "ambient", 4, 1000.0, "always");
        DialogueEngine.SelectionPlan plan = DialogueEngine.planSelection(List.of(lower, b, a), event -> true, null);

        long seed = 44L;
        double roll = RandomSource.create(seed).nextDouble() * 4.0;
        ResourceLocation expected = roll < 1.0 ? a.id() : b.id();
        DialogueEvent picked = DialogueEngine.weightedPick(
                plan.ambient(),
                DialogueEvent::weight,
                RandomSource.create(seed)
        ).orElseThrow();

        assertEquals(expected, picked.id());
        assertFalse(picked.id().equals(lower.id()));
    }

    @Test
    void choicesWithNoEligibleOutcomeAreNotOfferedAndOutcomeDrawIsWeighted() {
        DialogueEvent event = outcomeEvent();
        DialogueEvent.Node node = event.nodes().get(event.start());
        DialogueEvent.Choice unavailable = node.choices().orElseThrow().get(0);
        DialogueEvent.Choice weighted = node.choices().orElseThrow().get(1);
        Predicate<DialogueCondition> onlyAdult = condition ->
                condition instanceof DialogueCondition.Defined defined
                        && defined.definition().has("value")
                        && "adult".equals(defined.definition().get("value").getAsString());

        assertFalse(DialogueEngine.choiceEligible(unavailable, onlyAdult));
        assertTrue(DialogueEngine.choiceEligible(weighted, onlyAdult));

        List<DialogueEvent.Outcome> outcomes = DialogueEngine.eligibleOutcomes(weighted, onlyAdult);
        assertEquals(2, outcomes.size());
        long seed = 17L;
        double roll = RandomSource.create(seed).nextDouble() * 5.0;
        double expectedWeight = roll < 1.0 ? 1.0 : 4.0;
        DialogueEvent.Outcome picked = DialogueEngine.weightedPick(
                outcomes,
                DialogueEvent.Outcome::weight,
                RandomSource.create(seed)
        ).orElseThrow();
        assertEquals(expectedWeight, picked.weight());
    }

    private static DialogueEvent event(
            String id,
            String mode,
            int priority,
            double weight,
            String repeat
    ) {
        String prompt = "ambient".equals(mode) ? "" : "\"prompt\":\"dialogue." + id.replace(':', '.') + ".prompt\",";
        String repeatJson = "cooldown".equals(repeat)
                ? "{\"type\":\"cooldown\",\"seconds\":5}"
                : "{\"type\":\"" + repeat + "\"}";
        return DialogueEvent.decode(ResourceLocation.parse(id), JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{
                    "mode":"%s",
                    %s
                    "resume_prompt":"dialogue.resume"
                  },
                  "priority":%d,
                  "weight":%s,
                  "repeat":%s,
                  "start":"start",
                  "nodes":{"start":{"line":"dialogue.line","complete":true}}
                }
                """.formatted(mode, prompt, priority, weight, repeatJson)).getAsJsonObject());
    }

    private static DialogueEvent outcomeEvent() {
        return DialogueEvent.decode(ResourceLocation.parse("test:outcomes"), JsonParser.parseString("""
                {
                  "trigger":"talk",
                  "presentation":{
                    "mode":"ask",
                    "prompt":"dialogue.outcomes.prompt",
                    "resume_prompt":"dialogue.outcomes.resume"
                  },
                  "repeat":{"type":"always"},
                  "start":"start",
                  "nodes":{
                    "start":{
                      "line":"dialogue.outcomes.line",
                      "choices":[
                        {
                          "id":"none",
                          "text":"dialogue.none",
                          "outcomes":[
                            {
                              "requirements":[{"type":"mca:age_group","value":"child"}],
                              "weight":1,
                              "next":"done"
                            }
                          ]
                        },
                        {
                          "id":"weighted",
                          "text":"dialogue.weighted",
                          "outcomes":[
                            {
                              "requirements":[{"type":"mca:age_group","value":"adult"}],
                              "weight":1,
                              "next":"done"
                            },
                            {
                              "requirements":[{"type":"mca:age_group","value":"adult"}],
                              "weight":4,
                              "next":"done"
                            },
                            {
                              "requirements":[{"type":"mca:age_group","value":"child"}],
                              "weight":100,
                              "next":"done"
                            }
                          ]
                        }
                      ]
                    },
                    "done":{"line":"dialogue.done","complete":true}
                  }
                }
                """).getAsJsonObject());
    }
}
