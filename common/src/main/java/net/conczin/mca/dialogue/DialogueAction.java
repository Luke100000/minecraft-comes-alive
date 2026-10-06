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
 * Immutable decoded action definitions. Server-side execution is added only by
 * the action/progression task; unknown action kinds fail closed here.
 */
public sealed interface DialogueAction permits DialogueAction.Defined {
    Set<ResourceLocation> BUILTIN_TYPES = Set.of(
            MCA.locate("hearts"), MCA.locate("mood"), MCA.locate("remember"), MCA.locate("command")
    );

    Codec<DialogueAction> CODEC = Codec.PASSTHROUGH.comapFlatMap(
            DialogueAction::decodeDynamic,
            DialogueAction::encodeDynamic
    );

    ResourceLocation type();

    private static DataResult<DialogueAction> decodeDynamic(Dynamic<?> dynamic) {
        JsonElement element = dynamic.convert(JsonOps.INSTANCE).getValue();
        if (!element.isJsonObject()) {
            return DataResult.error(() -> "Dialogue action must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("type") || !object.get("type").isJsonPrimitive()) {
            return DataResult.error(() -> "Dialogue action requires string field 'type'");
        }
        ResourceLocation type = ResourceLocation.tryParse(object.get("type").getAsString());
        if (type == null || !BUILTIN_TYPES.contains(type)) {
            return DataResult.error(() -> "Unknown dialogue action type: " + object.get("type"));
        }
        return DataResult.success(new Defined(type, object.deepCopy()));
    }

    private static Dynamic<?> encodeDynamic(DialogueAction action) {
        JsonObject object = action instanceof Defined defined
                ? defined.definition().deepCopy()
                : new JsonObject();
        object.addProperty("type", action.type().toString());
        return new Dynamic<>(JsonOps.INSTANCE, object);
    }

    record Defined(ResourceLocation type, JsonObject definition) implements DialogueAction {
        public Defined {
            definition = definition.deepCopy();
        }

        @Override
        public JsonObject definition() {
            return definition.deepCopy();
        }
    }
}
