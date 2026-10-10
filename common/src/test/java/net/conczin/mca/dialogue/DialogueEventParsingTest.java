package net.conczin.mca.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueEventParsingTest {
    private static final ResourceLocation EVENT_ID = ResourceLocation.fromNamespaceAndPath("mca", "personal/test");

    @Test
    void nestedEnumsAndRepeatCanInitializeBeforeOuterEventCodec() {
        DialogueEvent.Repeat repeat = new DialogueEvent.Repeat(DialogueEvent.RepeatType.COOLDOWN, 100L, 100L);

        assertEquals(DialogueEvent.RepeatType.COOLDOWN, repeat.type());
        assertEquals(100L, repeat.minTicks());
        assertEquals(DialogueEvent.Trigger.TALK, DialogueEvent.Trigger.valueOf("TALK"));
    }

    @Test
    void decodesNamespacedEventAndNormalizesOrderedLinesAndCooldownSeconds() {
        DialogueEvent event = decode("""
                {
                  "trigger": "talk",
                  "presentation": {
                    "mode": "ask",
                    "prompt": "dialogue_event.test.prompt",
                    "resume_prompt": "dialogue_event.test.resume",
                    "topic": "personal"
                  },
                  "priority": 7,
                  "weight": 2.5,
                  "requirements": [],
                  "repeat": { "type": "cooldown", "seconds": 5 },
                  "start": "intro",
                  "nodes": {
                    "intro": {
                      "lines": ["dialogue_event.test.first", "dialogue_event.test.second"],
                      "choices": [
                        { "id": "continue_topic", "text": "dialogue_event.test.choice", "next": "done" }
                      ]
                    },
                    "done": { "line": "dialogue_event.test.done", "complete": true }
                  }
                }
                """);

        assertEquals(EVENT_ID, event.id());
        assertEquals(DialogueEvent.Trigger.TALK, event.trigger());
        assertEquals(DialogueEvent.PresentationMode.ASK, event.presentation().mode());
        assertEquals("dialogue_event.test.prompt", event.presentation().prompt().orElseThrow());
        assertEquals("dialogue_event.test.resume", event.presentation().resumePrompt());
        assertEquals("personal", event.presentation().topic().orElseThrow());
        assertEquals(7, event.priority());
        assertEquals(2.5, event.weight());
        assertEquals(DialogueEvent.HistoryPolicy.STORY, event.history());
        assertEquals(DialogueEvent.RepeatType.COOLDOWN, event.repeat().type());
        assertEquals(100L, event.repeat().minTicks());
        assertEquals(100L, event.repeat().maxTicks());
        assertEquals(List.of("dialogue_event.test.first", "dialogue_event.test.second"), event.nodes().get("intro").lines());
        assertEquals(List.of("dialogue_event.test.done"), event.nodes().get("done").lines());
    }

    @Test
    void decodesCrossEventRequirementsAndExplicitNegation() {
        DialogueEvent event = decode(baseEvent("""
                [
                  { "type": "mca:event_completed", "event": "mca:story/first" },
                  { "type": "mca:event_choice", "event": "mca:story/first", "choice": "ask_why" },
                  { "type": "mca:not", "condition": { "type": "mca:event_completed", "event": "addon:optional" } }
                ]
                """, "{ \"type\": \"always\" }"));

        assertInstanceOf(DialogueCondition.EventCompleted.class, event.requirements().get(0));
        assertInstanceOf(DialogueCondition.EventChoice.class, event.requirements().get(1));
        DialogueCondition.Not not = assertInstanceOf(DialogueCondition.Not.class, event.requirements().get(2));
        assertInstanceOf(DialogueCondition.EventCompleted.class, not.condition());
    }

    @Test
    void decodesCurrentInfectionRequirementAndNegation() {
        DialogueEvent event = decode(baseEvent("""
                [
                  { "type": "mca:infected" },
                  { "type": "mca:not", "condition": { "type": "mca:infected" } }
                ]
                """, "{ \"type\": \"always\" }"));

        DialogueCondition.Defined infected = assertInstanceOf(DialogueCondition.Defined.class, event.requirements().getFirst());
        assertEquals(ResourceLocation.fromNamespaceAndPath("mca", "infected"), infected.type());
        DialogueCondition.Not not = assertInstanceOf(DialogueCondition.Not.class, event.requirements().get(1));
        assertEquals(ResourceLocation.fromNamespaceAndPath("mca", "infected"), not.condition().type());
    }

    @Test
    void acceptsOptionalInfectionProgressBoundsAndPreservesThemOnRoundTrip() {
        for (String requirement : List.of(
                "{ \"type\": \"mca:infected\" }",
                "{ \"type\": \"mca:infected\", \"min\": 0.2 }",
                "{ \"type\": \"mca:infected\", \"max\": 0.8 }",
                "{ \"type\": \"mca:infected\", \"min\": 0.2, \"max\": 0.8 }"
        )) {
            DialogueCondition condition = decode(baseEvent("[" + requirement + "]", "{ \"type\": \"always\" }"))
                    .requirements().getFirst();
            assertEquals(JsonParser.parseString(requirement),
                    DialogueCondition.CODEC.encodeStart(JsonOps.INSTANCE, condition).getOrThrow());
        }
    }

    @Test
    void rejectsInvalidInfectionProgressBounds() {
        for (String requirement : List.of(
                "{ \"type\": \"mca:infected\", \"min\": -0.01 }",
                "{ \"type\": \"mca:infected\", \"max\": 1.01 }",
                "{ \"type\": \"mca:infected\", \"min\": 0.8, \"max\": 0.2 }",
                "{ \"type\": \"mca:infected\", \"min\": \"0.2\" }",
                "{ \"type\": \"mca:infected\", \"max\": null }"
        )) {
            assertDecodeFails(baseEvent("[" + requirement + "]", "{ \"type\": \"always\" }"));
        }
    }

    @Test
    void rejectsDuplicateChoiceIdsAcrossNodes() {
        assertDecodeFails("""
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": { "type": "always" },
                  "start": "a",
                  "nodes": {
                    "a": { "line": "a", "choices": [{ "id": "same", "text": "x", "next": "b" }] },
                    "b": { "line": "b", "choices": [{ "id": "same", "text": "y", "next": "done" }] },
                    "done": { "line": "done", "complete": true }
                  }
                }
                """);
    }

    @Test
    void rejectsMissingStartNodeAndMissingChoiceNext() {
        assertDecodeFails(simpleEvent("missing", "{ \"line\": \"done\", \"complete\": true }"));
        assertDecodeFails(simpleEvent("intro", "{ \"line\": \"intro\", \"choices\": [{ \"id\": \"x\", \"text\": \"x\" }] }"));
    }

    @Test
    void rejectsInvalidEventAndOutcomeWeights() {
        for (double weight : List.of(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            JsonObject json = parse(baseEvent("[]", "{ \"type\": \"always\" }"));
            json.add("weight", new JsonPrimitive(weight));
            assertDecodeFails(json);

            JsonObject outcome = parse("""
                    {
                      "trigger": "talk",
                      "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                      "repeat": { "type": "always" },
                      "start": "intro",
                      "nodes": {
                        "intro": {
                          "line": "intro",
                          "choices": [{
                            "id": "x",
                            "text": "x",
                            "outcomes": [{ "weight": 1, "next": "done" }]
                          }]
                        },
                        "done": { "line": "done", "complete": true }
                      }
                    }
                    """);
            outcome.getAsJsonObject("nodes")
                    .getAsJsonObject("intro")
                    .getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonArray("outcomes")
                    .get(0).getAsJsonObject()
                    .add("weight", new JsonPrimitive(weight));
            assertDecodeFails(outcome);
        }
    }

    @Test
    void rejectsChoiceThatMixesDirectPathAndOutcomes() {
        assertDecodeFails("""
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": { "type": "always" },
                  "start": "intro",
                  "nodes": {
                    "intro": {
                      "line": "intro",
                      "choices": [{
                        "id": "x",
                        "text": "x",
                        "next": "done",
                        "outcomes": [{ "next": "done" }]
                      }]
                    },
                    "done": { "line": "done", "complete": true }
                  }
                }
                """);
    }

    @Test
    void requiresExplicitRetryableForNonCompletingOnceOrCooldownTerminals() {
        assertDecodeFails(simpleEvent("intro", "{ \"line\": \"bye\", \"end\": true }", "{ \"type\": \"once\" }"));
        assertDecodeFails(simpleEvent("intro", "{ \"line\": \"bye\", \"end\": true }", "{ \"type\": \"cooldown\", \"seconds\": 5 }"));

        DialogueEvent retryable = decode(simpleEvent(
                "intro",
                "{ \"line\": \"bye\", \"end\": true, \"retryable\": true }",
                "{ \"type\": \"cooldown\", \"seconds\": 5 }"
        ));
        assertTrue(retryable.nodes().get("intro").retryable());
    }

    @Test
    void validatesNodeLineAndContinuationShapesAndCycles() {
        assertDecodeFails(simpleEvent("intro", "{ \"line\": \"a\", \"lines\": [\"b\"], \"complete\": true }"));
        assertDecodeFails(simpleEvent("intro", "{ \"lines\": [], \"complete\": true }"));
        assertDecodeFails(simpleEvent("intro", "{ \"line\": \"   \", \"complete\": true }"));
        assertDecodeFails(simpleEvent("intro", "{ \"line\": \"a\", \"next\": \"done\", \"complete\": true }"));

        DialogueEvent nextOnly = decode("""
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": { "type": "always" },
                  "start": "intro",
                  "nodes": {
                    "intro": { "line": "intro", "next": "done" },
                    "done": { "line": "done", "complete": true }
                  }
                }
                """);
        assertEquals("done", nextOnly.nodes().get("intro").next().orElseThrow());

        assertDecodeFails("""
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": { "type": "always" },
                  "start": "a",
                  "nodes": {
                    "a": { "line": "a", "next": "b" },
                    "b": { "line": "b", "next": "a" }
                  }
                }
                """);
    }

    @Test
    void defaultsHistoryToStoryAndRestrictsSchedulingHistoryToCooldown() {
        DialogueEvent event = decode(baseEvent("[]", "{ \"type\": \"always\" }"));
        assertEquals(DialogueEvent.HistoryPolicy.STORY, event.history());

        assertDecodeFails(baseEventWithHistory("[]", "{ \"type\": \"always\" }", "scheduling"));
        DialogueEvent scheduling = decode(baseEventWithHistory("[]", "{ \"type\": \"cooldown\", \"seconds\": 5 }", "scheduling"));
        assertEquals(DialogueEvent.HistoryPolicy.SCHEDULING, scheduling.history());
    }

    @Test
    void requiresRepeatAndValidSecondsAuthoring() {
        assertDecodeFails(baseEventWithoutRepeat());

        assertEquals(DialogueEvent.RepeatType.ONCE, decode(baseEvent("[]", "{ \"type\": \"once\" }")).repeat().type());
        assertEquals(DialogueEvent.RepeatType.ALWAYS, decode(baseEvent("[]", "{ \"type\": \"always\" }")).repeat().type());

        DialogueEvent fractional = decode(baseEvent("[]", "{ \"type\": \"cooldown\", \"seconds\": 0.01 }"));
        assertEquals(1L, fractional.repeat().minTicks());
        assertEquals(1L, fractional.repeat().maxTicks());

        DialogueEvent random = decode(baseEvent("[]", "{ \"type\": \"cooldown\", \"min_seconds\": 1.25, \"max_seconds\": 2.5 }"));
        assertEquals(25L, random.repeat().minTicks());
        assertEquals(50L, random.repeat().maxTicks());

        for (String repeat : List.of(
                "{ \"type\": \"cooldown\" }",
                "{ \"type\": \"cooldown\", \"seconds\": 5, \"min_seconds\": 1, \"max_seconds\": 2 }",
                "{ \"type\": \"cooldown\", \"min_seconds\": 2, \"max_seconds\": 1 }",
                "{ \"type\": \"cooldown\", \"min_seconds\": 1 }",
                "{ \"type\": \"cooldown\", \"seconds\": -1 }",
                "{ \"type\": \"cooldown\", \"min_ticks\": 100, \"max_ticks\": 100 }",
                "{ \"type\": \"once\", \"seconds\": 5 }"
        )) {
            assertDecodeFails(baseEvent("[]", repeat));
        }

        JsonObject nonFinite = parse(baseEvent("[]", "{ \"type\": \"cooldown\", \"seconds\": 5 }"));
        nonFinite.getAsJsonObject("repeat").add("seconds", new JsonPrimitive(Double.NaN));
        assertDecodeFails(nonFinite);

        JsonObject overflow = parse(baseEvent("[]", "{ \"type\": \"cooldown\", \"seconds\": 5 }"));
        overflow.getAsJsonObject("repeat").add("seconds", new JsonPrimitive(Double.MAX_VALUE));
        assertDecodeFails(overflow);
    }

    @Test
    void requiresNonblankResumePromptAndPromptForSelectableModes() {
        assertDecodeFails(baseEventWithPresentation("{ \"mode\": \"ask\", \"prompt\": \"p\", \"resume_prompt\": \"   \" }"));
        assertDecodeFails(baseEventWithPresentation("{ \"mode\": \"highlighted\", \"resume_prompt\": \"r\" }"));

        DialogueEvent ambient = decode(baseEventWithPresentation("{ \"mode\": \"ambient\", \"resume_prompt\": \"r\" }"));
        assertTrue(ambient.presentation().prompt().isEmpty());
    }

    @Test
    void rejectsUnknownConditionAndActionTypes() {
        assertDecodeFails(baseEvent("[{ \"type\": \"addon:unknown_condition\" }]", "{ \"type\": \"always\" }"));
        assertDecodeFails("""
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": { "type": "always" },
                  "start": "intro",
                  "nodes": {
                    "intro": {
                      "line": "intro",
                      "choices": [{
                        "id": "x",
                        "text": "x",
                        "actions": [{ "type": "addon:unknown_action" }],
                        "next": "done"
                      }]
                    },
                    "done": { "line": "done", "complete": true }
                  }
                }
                """);
    }

    @Test
    void parsesRecentEventConditionWithOptionalWindow() {
        DialogueEvent event = decode(baseEvent(
                "[{ \"type\": \"mca:recent_event\", \"event\": \"mca:attacked\", \"within_ticks\": 200 }]",
                "{ \"type\": \"always\" }"
        ));
        DialogueCondition.Defined condition = assertInstanceOf(DialogueCondition.Defined.class, event.requirements().get(0));
        assertEquals(DialogueCondition.RECENT_EVENT, condition.type());

        DialogueCondition ever = decode(baseEvent(
                "[{ \"type\": \"mca:recent_event\", \"event\": \"mca:cured\" }]",
                "{ \"type\": \"always\" }"
        )).requirements().getFirst();
        assertEquals(JsonParser.parseString("{ \"type\": \"mca:recent_event\", \"event\": \"mca:cured\" }"),
                DialogueCondition.CODEC.encodeStart(JsonOps.INSTANCE, ever).getOrThrow());
        assertDecodeFails(baseEvent(
                "[{ \"type\": \"mca:recent_event\", \"event\": \"mca:attacked\", \"within_ticks\": -1 }]",
                "{ \"type\": \"always\" }"
        ));
    }

    @SuppressWarnings("unchecked")
    @Test
    void cooldownRepeatCodecRoundTripsFixedAndRangedDurations() throws ReflectiveOperationException {
        Field field = DialogueEvent.Repeat.class.getDeclaredField("CODEC");
        field.setAccessible(true);
        Codec<DialogueEvent.Repeat> codec = (Codec<DialogueEvent.Repeat>) field.get(null);

        for (String repeat : List.of(
                "{\"type\":\"cooldown\",\"seconds\":5}",
                "{\"type\":\"cooldown\",\"min_seconds\":2,\"max_seconds\":8}")) {
            DialogueEvent.Repeat decoded = decode(baseEvent("[]", repeat)).repeat();
            var encoded = codec.encodeStart(JsonOps.INSTANCE, decoded).getOrThrow();
            assertEquals(decoded, codec.parse(JsonOps.INSTANCE, encoded).getOrThrow(),
                    "encoding a cooldown must preserve its normalized duration");
        }
    }

    @Test
    void decodesPlayerHitAndLastSeenVillageSpaceConditionsWithoutParameters() {
        DialogueEvent event = decode(baseEvent(
                """
                [
                  { "type": "mca:hit_by" },
                  { "type": "mca:village_has_space" }
                ]
                """,
                "{ \"type\": \"always\" }"
        ));

        DialogueCondition.Defined hitBy = assertInstanceOf(
                DialogueCondition.Defined.class,
                event.requirements().get(0)
        );
        DialogueCondition.Defined villageHasSpace = assertInstanceOf(
                DialogueCondition.Defined.class,
                event.requirements().get(1)
        );
        assertEquals(ResourceLocation.fromNamespaceAndPath("mca", "hit_by"), hitBy.type());
        assertEquals(ResourceLocation.fromNamespaceAndPath("mca", "village_has_space"), villageHasSpace.type());
    }

    @Test
    void decodesLineLessAutomaticRoutingNodesWithWeightedOutcomes() {
        DialogueEvent event = decode("""
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": { "type": "always" },
                  "start": "route",
                  "nodes": {
                    "route": {
                      "outcomes": [
                        {
                          "weight": 4,
                          "actions": [{ "type": "mca:hearts", "amount": 2 }],
                          "next": "done"
                        },
                        {
                          "requirements": [{ "type": "mca:personality", "value": "gloomy" }],
                          "weight": 1,
                          "next": "done"
                        }
                      ]
                    },
                    "done": { "line": "done", "complete": true }
                  }
                }
                """);

        DialogueEvent.Node route = event.nodes().get("route");
        assertTrue(route.lines().isEmpty());
        assertEquals(2, route.outcomes().orElseThrow().size());
        assertInstanceOf(DialogueAction.Hearts.class,
                route.outcomes().orElseThrow().getFirst().actions().getFirst());
    }

    @Test
    void automaticRoutingNodesRequireAnUnconditionalFallbackAndNoVisibleLine() {
        assertDecodeFails("""
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": { "type": "always" },
                  "start": "route",
                  "nodes": {
                    "route": {
                      "line": "must.not.render",
                      "outcomes": [{ "weight": 1, "next": "done" }]
                    },
                    "done": { "line": "done", "complete": true }
                  }
                }
                """);
        assertDecodeFails("""
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": { "type": "always" },
                  "start": "route",
                  "nodes": {
                    "route": {
                      "outcomes": [{
                        "requirements": [{ "type": "mca:personality", "value": "gloomy" }],
                        "weight": 1,
                        "next": "done"
                      }]
                    },
                    "done": { "line": "done", "complete": true }
                  }
                }
                """);
    }

    @Test
    void rejectsInvalidBuiltInConditionShapes() {
        for (String requirement : List.of(
                "{ \"type\": \"mca:time\", \"value\": \"night\", \"min\": 13000 }",
                "{ \"type\": \"mca:time\" }",
                "{ \"type\": \"mca:pregnancy\", \"value\": false, \"min_progress\": 1 }",
                "{ \"type\": \"mca:inventory\", \"item\": \"minecraft:oak_log\", \"tag\": \"minecraft:logs\" }",
                "{ \"type\": \"mca:inventory\" }",
                "{ \"type\": \"mca:item\", \"value\": \"minecraft:oak_log\", \"min\": 3, \"max\": 2 }",
                "{ \"type\": \"mca:health\", \"min\": 1, \"typo\": true }",
                "{ \"type\": \"mca:infected\", \"progress\": 0.2 }",
                "{ \"type\": \"mca:hit_by\", \"within_ticks\": 20 }",
                "{ \"type\": \"mca:village_has_space\", \"value\": true }"
        )) {
            assertDecodeFails(baseEvent("[" + requirement + "]", "{ \"type\": \"always\" }"));
        }
    }

    @Test
    void decodesKnownConditionAndTypedActionParameters() {
        DialogueEvent event = decode("""
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "requirements": [{ "type": "mca:personality", "value": "gloomy" }],
                  "repeat": { "type": "always" },
                  "start": "intro",
                  "nodes": {
                    "intro": {
                      "line": "intro",
                      "choices": [{
                        "id": "x",
                        "text": "x",
                        "actions": [{ "type": "mca:hearts", "amount": 5 }],
                        "next": "done"
                      }]
                    },
                    "done": { "line": "done", "complete": true }
                  }
                }
                """);

        DialogueCondition.Defined condition = assertInstanceOf(DialogueCondition.Defined.class, event.requirements().get(0));
        assertEquals("gloomy", condition.definition().get("value").getAsString());

        DialogueAction.Hearts action = assertInstanceOf(
                DialogueAction.Hearts.class,
                event.nodes().get("intro").choices().orElseThrow().get(0).actions().get(0)
        );
        assertEquals(5, action.amount());
    }

    @Test
    void slapActionAcceptsPositiveDamageAndRoundTrips() {
        for (String definition : List.of(
                "{ \"type\": \"mca:slap\", \"amount\": 1 }",
                "{ \"type\": \"mca:slap\", \"amount\": 2.5 }"
        )) {
            DialogueAction action = DialogueAction.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(definition)).getOrThrow();
            DialogueAction.Slap slap = assertInstanceOf(DialogueAction.Slap.class, action);
            assertTrue(slap.amount() > 0);
            assertEquals(JsonParser.parseString(definition),
                    DialogueAction.CODEC.encodeStart(JsonOps.INSTANCE, action).getOrThrow());
        }
    }

    @Test
    void slapActionRejectsInvalidDamage() {
        for (String definition : List.of(
                "{ \"type\": \"mca:slap\" }",
                "{ \"type\": \"mca:slap\", \"amount\": 0 }",
                "{ \"type\": \"mca:slap\", \"amount\": -1 }",
                "{ \"type\": \"mca:slap\", \"amount\": 1e400 }",
                "{ \"type\": \"mca:slap\", \"amount\": \"2\" }",
                "{ \"type\": \"mca:slap\", \"amount\": 1, \"typo\": true }"
        )) {
            assertTrue(DialogueAction.CODEC.parse(JsonOps.INSTANCE,
                    JsonParser.parseString(definition)).error().isPresent());
        }
    }

    @Test
    void rejectsChoiceIdsAndChoiceListsThatExceedWireBounds() {
        assertDecodeFails("""
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": { "type": "always" },
                  "start": "intro",
                  "nodes": {
                    "intro": {
                      "line": "intro",
                      "choices": [{ "id": "%s", "text": "x", "next": "done" }]
                    },
                    "done": { "line": "done", "complete": true }
                  }
                }
                """.formatted("x".repeat(DialogueEvent.MAX_CHOICE_ID_LENGTH + 1)));

        String choices = java.util.stream.IntStream.rangeClosed(0, DialogueEvent.MAX_CHOICES)
                .mapToObj(i -> "{ \"id\": \"c" + i + "\", \"text\": \"x\", \"next\": \"done\" }")
                .collect(java.util.stream.Collectors.joining(","));
        assertDecodeFails("""
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": { "type": "always" },
                  "start": "intro",
                  "nodes": {
                    "intro": { "line": "intro", "choices": [%s] },
                    "done": { "line": "done", "complete": true }
                  }
                }
                """.formatted(choices));
    }

    @Test
    void rejectsInvalidBuiltInActionShapes() {
        for (String action : List.of(
                "{ \"type\": \"mca:hearts\" }",
                "{ \"type\": \"mca:hearts\", \"amount\": 1, \"typo\": true }",
                "{ \"type\": \"mca:hearts\", \"amount\": 1.5 }",
                "{ \"type\": \"mca:mood\", \"amount\": \"sad\" }",
                "{ \"type\": \"mca:remember\", \"id\": \"x\", \"var\": \"villager\" }",
                "{ \"type\": \"mca:remember\", \"id\": \"x\", \"time\": 0 }",
                "{ \"type\": \"mca:remember\", \"id\": \"x\", \"time\": 1.25 }",
                "{ \"type\": \"mca:command\", \"command\": \" \" }"
        )) {
            assertDecodeFails(eventWithAction(action));
        }
    }

    @Test
    void rejectsUnknownStructuralFieldsInsteadOfSilentlyIgnoringThem() {
        assertDecodeFails(baseEvent("[]", "{ \"type\": \"always\", \"typo\": 1 }"));
        assertDecodeFails("""
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": { "type": "always" },
                  "start": "intro",
                  "nodes": {
                    "intro": { "line": "intro", "complete": true, "typo": true }
                  }
                }
                """);
    }

    private static DialogueEvent decode(String json) {
        return DialogueEvent.decode(EVENT_ID, parse(json));
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static void assertDecodeFails(String json) {
        assertDecodeFails(parse(json));
    }

    private static void assertDecodeFails(JsonObject json) {
        assertThrows(IllegalArgumentException.class, () -> DialogueEvent.decode(EVENT_ID, json));
    }

    private static String baseEvent(String requirements, String repeat) {
        return """
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "requirements": %s,
                  "repeat": %s,
                  "start": "intro",
                  "nodes": { "intro": { "line": "intro", "complete": true } }
                }
                """.formatted(requirements, repeat);
    }

    private static String baseEventWithHistory(String requirements, String repeat, String history) {
        return baseEvent(requirements, repeat).replace("\"start\":", "\"history\": \"" + history + "\",\n  \"start\":");
    }

    private static String baseEventWithoutRepeat() {
        return """
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "start": "intro",
                  "nodes": { "intro": { "line": "intro", "complete": true } }
                }
                """;
    }

    private static String baseEventWithPresentation(String presentation) {
        return """
                {
                  "trigger": "talk",
                  "presentation": %s,
                  "repeat": { "type": "always" },
                  "start": "intro",
                  "nodes": { "intro": { "line": "intro", "complete": true } }
                }
                """.formatted(presentation);
    }

    private static String eventWithAction(String action) {
        return """
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": { "type": "always" },
                  "start": "intro",
                  "nodes": {
                    "intro": {
                      "line": "intro",
                      "choices": [{
                        "id": "x",
                        "text": "x",
                        "actions": [%s],
                        "next": "done"
                      }]
                    },
                    "done": { "line": "done", "complete": true }
                  }
                }
                """.formatted(action);
    }

    private static String simpleEvent(String start, String node) {
        return simpleEvent(start, node, "{ \"type\": \"always\" }");
    }

    private static String simpleEvent(String start, String node, String repeat) {
        return """
                {
                  "trigger": "talk",
                  "presentation": { "mode": "ask", "prompt": "p", "resume_prompt": "r" },
                  "repeat": %s,
                  "start": "%s",
                  "nodes": { "intro": %s }
                }
                """.formatted(repeat, start, node);
    }
}
