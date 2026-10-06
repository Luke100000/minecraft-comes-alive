package net.conczin.mca.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Immutable decoded condition definitions. Gameplay evaluation is attached in
 * the condition task; this type deliberately owns strict type decoding first.
 */
public sealed interface DialogueCondition permits DialogueCondition.Defined, DialogueCondition.EventCompleted,
        DialogueCondition.EventChoice, DialogueCondition.Not {
    ResourceLocation NOT = MCA.locate("not");
    ResourceLocation EVENT_COMPLETED = MCA.locate("event_completed");
    ResourceLocation EVENT_CHOICE = MCA.locate("event_choice");

    Set<ResourceLocation> BUILTIN_TYPES = Set.of(
            MCA.locate("personality"), MCA.locate("mood"), MCA.locate("hearts"),
            MCA.locate("relationship"), MCA.locate("family"), MCA.locate("age_group"),
            MCA.locate("profession"), MCA.locate("rank"), MCA.locate("trait"),
            MCA.locate("health"), MCA.locate("time"), MCA.locate("weather"),
            MCA.locate("biome"), MCA.locate("advancement"), MCA.locate("village_has_building"),
            MCA.locate("in_building"), MCA.locate("recent_event"), MCA.locate("gender"),
            MCA.locate("pregnancy"), MCA.locate("inventory"), MCA.locate("item"),
            MCA.locate("tag"), MCA.locate("memory"), MCA.locate("building_assignment"),
            EVENT_COMPLETED, EVENT_CHOICE, NOT
    );

    Codec<DialogueCondition> CODEC = Codec.PASSTHROUGH.comapFlatMap(
            DialogueCondition::decodeDynamic,
            DialogueCondition::encodeDynamic
    );

    ResourceLocation type();

    private static DataResult<DialogueCondition> decodeDynamic(Dynamic<?> dynamic) {
        JsonElement element = dynamic.convert(JsonOps.INSTANCE).getValue();
        if (!element.isJsonObject()) {
            return DataResult.error(() -> "Dialogue condition must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("type") || !object.get("type").isJsonPrimitive()) {
            return DataResult.error(() -> "Dialogue condition requires string field 'type'");
        }
        ResourceLocation type = ResourceLocation.tryParse(object.get("type").getAsString());
        if (type == null || !BUILTIN_TYPES.contains(type)) {
            return DataResult.error(() -> "Unknown dialogue condition type: " + object.get("type"));
        }

        try {
            if (EVENT_COMPLETED.equals(type)) {
                return DataResult.success(new EventCompleted(requiredLocation(object, "event")));
            }
            if (EVENT_CHOICE.equals(type)) {
                return DataResult.success(new EventChoice(
                        requiredLocation(object, "event"),
                        requiredNonblankString(object, "choice")
                ));
            }
            if (NOT.equals(type)) {
                if (!object.has("condition")) {
                    throw new IllegalArgumentException("mca:not requires condition");
                }
                DialogueCondition child = CODEC.parse(JsonOps.INSTANCE, object.get("condition")).getOrThrow();
                return DataResult.success(new Not(child));
            }
            return DataResult.success(new Defined(type, object.deepCopy()));
        } catch (RuntimeException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static Dynamic<?> encodeDynamic(DialogueCondition condition) {
        JsonObject object;
        if (condition instanceof Defined defined) {
            object = defined.definition().deepCopy();
        } else {
            object = new JsonObject();
            object.addProperty("type", condition.type().toString());
        }
        if (condition instanceof EventCompleted completed) {
            object.addProperty("event", completed.event().toString());
        } else if (condition instanceof EventChoice choice) {
            object.addProperty("event", choice.event().toString());
            object.addProperty("choice", choice.choiceId());
        } else if (condition instanceof Not not) {
            JsonElement child = CODEC.encodeStart(JsonOps.INSTANCE, not.condition()).getOrThrow();
            object.add("condition", child);
        }
        return new Dynamic<>(JsonOps.INSTANCE, object);
    }

    private static ResourceLocation requiredLocation(JsonObject object, String field) {
        String value = requiredNonblankString(object, field);
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid ResourceLocation in '" + field + "': " + value);
        }
        return id;
    }

    private static String requiredNonblankString(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing string field '" + field + "'");
        }
        String value = object.get(field).getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Blank string field '" + field + "'");
        }
        return value;
    }

    record Defined(ResourceLocation type, JsonObject definition) implements DialogueCondition {
        public Defined {
            definition = definition.deepCopy();
        }
    }

    record EventCompleted(ResourceLocation event) implements DialogueCondition {
        @Override
        public ResourceLocation type() {
            return EVENT_COMPLETED;
        }
    }

    record EventChoice(ResourceLocation event, String choiceId) implements DialogueCondition {
        @Override
        public ResourceLocation type() {
            return EVENT_CHOICE;
        }
    }

    record Not(DialogueCondition condition) implements DialogueCondition {
        @Override
        public ResourceLocation type() {
            return NOT;
        }
    }
}
