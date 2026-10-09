package net.conczin.mca.dialogue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueExtensionRegistrationTest {
    private static final ResourceLocation CONDITION = ResourceLocation.parse("dialogue_test:confidence");
    private static final ResourceLocation ACTION = ResourceLocation.parse("dialogue_test:record_reply");

    @Test
    void customConditionDecodesEvaluatesAndRoundTripsIncludingNestedNot() {
        DialogueCondition.register(CONDITION, Codec.INT.fieldOf("min").codec(),
                (min, context) -> min >= 10
                        ? DialogueCondition.Evaluation.MATCH : DialogueCondition.Evaluation.UNAVAILABLE);
        String json = """
                {"type":"mca:not","condition":{"type":"dialogue_test:confidence","min":5}}
                """;
        DialogueCondition condition = DialogueCondition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
        assertEquals(DialogueCondition.Evaluation.UNAVAILABLE, condition.evaluate(null));
        var encoded = DialogueCondition.CODEC.encodeStart(JsonOps.INSTANCE, condition).getOrThrow();
        assertEquals(condition.evaluate(null),
                DialogueCondition.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().evaluate(null));
    }

    @Test
    void customActionDecodesAndRoundTripsWithTypedCodec() {
        DialogueAction.register(ACTION, Codec.INT.fieldOf("count").codec(), (count, context) -> {});
        var action = DialogueAction.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"dialogue_test:record_reply\",\"count\":3}")).getOrThrow();
        assertEquals(ACTION, action.type());
        var encoded = DialogueAction.CODEC.encodeStart(JsonOps.INSTANCE, action).getOrThrow();
        assertEquals(3, encoded.getAsJsonObject().get("count").getAsInt());
        assertEquals(ACTION, DialogueAction.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow().type());
    }

    @Test
    void customConditionAndActionAppearThroughoutEventStructure() {
        ResourceLocation conditionId = ResourceLocation.parse("dialogue_test:node_gate");
        ResourceLocation actionId = ResourceLocation.parse("dialogue_test:node_effect");
        DialogueCondition.register(conditionId, Codec.BOOL.fieldOf("enabled").codec(),
                (value, context) -> value ? DialogueCondition.Evaluation.MATCH : DialogueCondition.Evaluation.NO_MATCH);
        DialogueAction.register(actionId, Codec.INT.fieldOf("points").codec(), (value, context) -> {});
        var event = DialogueEvent.decode(ResourceLocation.parse("dialogue_test:extension_event"),
                JsonParser.parseString("""
                {
                  "trigger":"talk", "presentation":{"mode":"ask","prompt":"p","resume_prompt":"r"},
                  "repeat":{"type":"always"},
                  "requirements":[{"type":"dialogue_test:node_gate","enabled":true}],
                  "start":"intro", "nodes":{
                    "intro":{"line":"a", "choices":[{"id":"reply","text":"b",
                      "requirements":[{"type":"dialogue_test:node_gate","enabled":true}],
                      "actions":[{"type":"dialogue_test:node_effect","points":3}], "next":"done"},
                      {"id":"maybe","text":"c","outcomes":[{"weight":1,
                        "requirements":[{"type":"dialogue_test:node_gate","enabled":true}],
                        "actions":[{"type":"dialogue_test:node_effect","points":5}],"next":"done"}]}]},
                    "done":{"line":"done","complete":true}
                  }
                }
                """).getAsJsonObject());
        assertTrue(event.requirements().getFirst().matches(null));
        assertTrue(event.nodes().get("intro").choices().orElseThrow().getFirst().requirements().getFirst().matches(null));
        assertEquals(actionId, event.nodes().get("intro").choices().orElseThrow().getFirst().actions().getFirst().type());
        var outcome = event.nodes().get("intro").choices().orElseThrow().get(1).outcomes().orElseThrow().getFirst();
        assertTrue(outcome.requirements().getFirst().matches(null));
        assertEquals(actionId, outcome.actions().getFirst().type());
    }

    @Test
    void duplicatesBuiltinsAndMalformedCustomDefinitionsAreRejected() {
        ResourceLocation id = ResourceLocation.parse("dialogue_test:duplicate_gate");
        DialogueCondition.register(id, Codec.INT.fieldOf("min").codec(),
                (minimum, context) -> DialogueCondition.Evaluation.MATCH);
        assertThrows(IllegalArgumentException.class, () -> DialogueCondition.register(id, Codec.INT,
                (minimum, context) -> DialogueCondition.Evaluation.MATCH));
        assertThrows(IllegalArgumentException.class, () -> DialogueAction.register(DialogueAction.HEARTS,
                Codec.INT, (minimum, context) -> {}));
        assertTrue(DialogueCondition.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"dialogue_test:duplicate_gate\",\"min\":\"bad\"}"))
                .error().isPresent());
        assertTrue(DialogueAction.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"dialogue_test:unknown\"}"))
                .error().isPresent());
    }
}
