package net.conczin.mca.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/** Immutable, validated dialogue effects committed by the server at conversation completion. */
public sealed interface DialogueAction permits DialogueAction.Hearts, DialogueAction.Mood,
        DialogueAction.Remember, DialogueAction.Command, DialogueAction.Registered {
    ResourceLocation HEARTS = MCA.locate("hearts");
    ResourceLocation MOOD = MCA.locate("mood");
    ResourceLocation REMEMBER = MCA.locate("remember");
    ResourceLocation COMMAND = MCA.locate("command");

    Set<ResourceLocation> BUILTIN_TYPES = Set.of(HEARTS, MOOD, REMEMBER, COMMAND);

    Codec<DialogueAction> CODEC = Codec.PASSTHROUGH.comapFlatMap(
            DialogueAction::decodeDynamic,
            DialogueAction::encodeDynamic
    );

    ResourceLocation type();

    /** Register an addon action type before dialogue datapacks are loaded. Effects run at completion. */
    static <T> void register(ResourceLocation id, Codec<T> codec, BiConsumer<T, DialogueContext> executor) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(executor, "executor");
        if (BUILTIN_TYPES.contains(id) || Extensions.TYPES.putIfAbsent(id, new ExtensionType<>(id, codec, executor)) != null) {
            throw new IllegalArgumentException("Dialogue action type is already registered: " + id);
        }
    }

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
        if (type == null || (!BUILTIN_TYPES.contains(type) && !Extensions.TYPES.containsKey(type))) {
            return DataResult.error(() -> "Unknown dialogue action type: " + object.get("type"));
        }

        try {
            if (!BUILTIN_TYPES.contains(type)) {
                return DataResult.success(decodeRegistered(Extensions.TYPES.get(type), object));
            }
            if (HEARTS.equals(type)) {
                requireOnly(object, "type", "amount");
                return DataResult.success(new Hearts(requiredInt(object, "amount")));
            }
            if (MOOD.equals(type)) {
                requireOnly(object, "type", "amount");
                return DataResult.success(new Mood(requiredInt(object, "amount")));
            }
            if (REMEMBER.equals(type)) {
                requireOnly(object, "type", "id", "var", "time");
                String id = requiredNonblankString(object, "id");
                boolean playerScoped = false;
                if (object.has("var")) {
                    String variable = requiredNonblankString(object, "var");
                    if (!"player".equals(variable)) {
                        throw new IllegalArgumentException("mca:remember var must be 'player'");
                    }
                    playerScoped = true;
                }
                OptionalLong time = OptionalLong.empty();
                if (object.has("time")) {
                    long ticks = requiredLong(object, "time");
                    if (ticks <= 0L || ticks > Integer.MAX_VALUE) {
                        throw new IllegalArgumentException(
                                "mca:remember time must be between 1 and " + Integer.MAX_VALUE + " ticks");
                    }
                    time = OptionalLong.of(ticks);
                }
                return DataResult.success(new Remember(id, playerScoped, time));
            }

            requireOnly(object, "type", "command");
            return DataResult.success(new Command(requiredNonblankString(object, "command")));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> Objects.toString(exception.getMessage(), exception.getClass().getSimpleName()));
        }
    }

    private static Dynamic<?> encodeDynamic(DialogueAction action) {
        if (action instanceof Registered<?> registered) {
            return new Dynamic<>(JsonOps.INSTANCE, encodeRegistered(registered));
        }
        JsonObject object = new JsonObject();
        object.addProperty("type", action.type().toString());
        if (action instanceof Hearts hearts) {
            object.addProperty("amount", hearts.amount());
        } else if (action instanceof Mood mood) {
            object.addProperty("amount", mood.amount());
        } else if (action instanceof Remember remember) {
            object.addProperty("id", remember.id());
            if (remember.playerScoped()) {
                object.addProperty("var", "player");
            }
            remember.time().ifPresent(value -> object.addProperty("time", value));
        } else if (action instanceof Command command) {
            object.addProperty("command", command.command());
        }
        return new Dynamic<>(JsonOps.INSTANCE, object);
    }

    private static <T> Registered<T> decodeRegistered(ExtensionType<T> extension, JsonObject object) {
        JsonObject values = object.deepCopy();
        values.remove("type");
        T value = extension.codec().parse(JsonOps.INSTANCE, values).getOrThrow();
        return new Registered<>(extension, value);
    }

    private static <T> JsonObject encodeRegistered(Registered<T> registered) {
        JsonElement values = registered.extension().codec().encodeStart(JsonOps.INSTANCE, registered.value()).getOrThrow();
        if (!values.isJsonObject() || values.getAsJsonObject().has("type")) {
            throw new IllegalArgumentException("Registered dialogue action codec must encode an object without 'type'");
        }
        JsonObject object = values.getAsJsonObject();
        object.addProperty("type", registered.type().toString());
        return object;
    }

    record ExtensionType<T>(ResourceLocation id, Codec<T> codec, BiConsumer<T, DialogueContext> executor) {}

    record Registered<T>(ExtensionType<T> extension, T value) implements DialogueAction {
        @Override
        public ResourceLocation type() {
            return extension.id();
        }

        public void execute(DialogueContext context) {
            extension.executor().accept(value, context);
        }
    }

    final class Extensions {
        private static final Map<ResourceLocation, ExtensionType<?>> TYPES = new ConcurrentHashMap<>();

        private Extensions() {}
    }

    private static String requiredNonblankString(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Missing string field '" + field + "'");
        }
        String value = object.get(field).getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Blank string field '" + field + "'");
        }
        return value;
    }

    private static int requiredInt(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing integer field '" + field + "'");
        }
        try {
            return object.getAsJsonPrimitive(field).getAsBigDecimal().intValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Field '" + field + "' must be an exact integer", exception);
        }
    }

    private static long requiredLong(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing long field '" + field + "'");
        }
        try {
            return object.getAsJsonPrimitive(field).getAsBigDecimal().longValueExact();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Field '" + field + "' must be an exact integer", exception);
        }
    }

    private static void requireOnly(JsonObject object, String... allowedFields) {
        Set<String> allowed = Set.of(allowedFields);
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("Unknown dialogue action field '" + field + "'");
            }
        }
    }

    record Hearts(int amount) implements DialogueAction {
        @Override
        public ResourceLocation type() {
            return HEARTS;
        }
    }

    // Mood actions are delta-only until authored content demonstrates a need for absolute assignment.
    record Mood(int amount) implements DialogueAction {
        @Override
        public ResourceLocation type() {
            return MOOD;
        }
    }

    record Remember(String id, boolean playerScoped, OptionalLong time) implements DialogueAction {
        public Remember {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(time, "time");
            if (id.isBlank()) {
                throw new IllegalArgumentException("Dialogue memory id must not be blank");
            }
        }

        @Override
        public ResourceLocation type() {
            return REMEMBER;
        }
    }

    record Command(String command) implements DialogueAction {
        public Command {
            Objects.requireNonNull(command, "command");
            if (command.isBlank()) {
                throw new IllegalArgumentException("Dialogue command must not be blank");
            }
        }

        @Override
        public ResourceLocation type() {
            return COMMAND;
        }
    }
}
