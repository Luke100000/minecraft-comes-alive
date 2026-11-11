package net.conczin.mca.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.util.RandomPos;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;

/** A server-side, read-only node lookup that produces a localized dialogue line. */
public sealed interface DialogueAsyncResolver permits DialogueAsyncResolver.StructureLocation, DialogueAsyncResolver.Registered {
    ResourceLocation STRUCTURE_LOCATION = MCA.locate("structure_location");
    Codec<DialogueAsyncResolver> CODEC = Codec.PASSTHROUGH.comapFlatMap(
            DialogueAsyncResolver::decode, DialogueAsyncResolver::encode);

    ResourceLocation type();
    String pendingLine();
    String failureLine();
    CompletionStage<Result> resolve(DialogueContext context, UUID operationId);

    /** Called after invalidating the engine's ticket, including on timeout and session close. */
    void cancel(UUID operationId);

    static <T> void register(ResourceLocation id, Codec<T> codec, Provider<T> provider) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(provider, "provider");
        if (STRUCTURE_LOCATION.equals(id)
                || Extensions.TYPES.putIfAbsent(id, new ExtensionType<>(id, codec, provider)) != null) {
            throw new IllegalArgumentException("Dialogue resolver type is already registered: " + id);
        }
    }

    private static DataResult<DialogueAsyncResolver> decode(Dynamic<?> dynamic) {
        JsonElement element = dynamic.convert(JsonOps.INSTANCE).getValue();
        if (!element.isJsonObject()) {
            return DataResult.error(() -> "Dialogue node resolver must be an object");
        }
        JsonObject json = element.getAsJsonObject();
        try {
            String rawType = requiredString(json, "type");
            ResourceLocation id = ResourceLocation.tryParse(rawType);
            if (id == null) {
                throw new IllegalArgumentException("Invalid dialogue resolver type: " + rawType);
            }
            String pendingLine = optionalString(json, "pending_line", "gui.dialogue.searching");
            String failureLine = requiredString(json, "failure_line");
            if (STRUCTURE_LOCATION.equals(id)) {
                requireOnly(json, Set.of("type", "pool", "structure", "pending_line", "failure_line"));
                boolean hasPool = json.has("pool");
                boolean hasStructure = json.has("structure");
                if (hasPool == hasStructure) {
                    throw new IllegalArgumentException("mca:structure_location requires exactly one of pool or structure");
                }
                Optional<String> pool = Optional.empty();
                Optional<ResourceLocation> structure = Optional.empty();
                if (hasPool) {
                    String value = requiredString(json, "pool");
                    if (!"configured_rumors".equals(value)) {
                        throw new IllegalArgumentException("Unknown structure location pool: " + value);
                    }
                    pool = Optional.of(value);
                } else {
                    String value = requiredString(json, "structure");
                    ResourceLocation parsed = ResourceLocation.tryParse(value);
                    if (parsed == null) {
                        throw new IllegalArgumentException("Invalid structure ID: " + value);
                    }
                    structure = Optional.of(parsed);
                }
                return DataResult.success(new StructureLocation(pool, structure, pendingLine, failureLine));
            }
            ExtensionType<?> extension = Extensions.TYPES.get(id);
            if (extension == null) {
                throw new IllegalArgumentException("Unknown dialogue resolver type: " + id);
            }
            return DataResult.success(decodeRegistered(extension, json, pendingLine, failureLine));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> Objects.toString(exception.getMessage(), exception.getClass().getSimpleName()));
        }
    }

    private static Dynamic<?> encode(DialogueAsyncResolver resolver) {
        JsonObject json;
        if (resolver instanceof Registered<?> registered) {
            json = encodeRegistered(registered);
        } else {
            StructureLocation location = (StructureLocation) resolver;
            json = new JsonObject();
            location.pool().ifPresent(value -> json.addProperty("pool", value));
            location.structure().ifPresent(value -> json.addProperty("structure", value.toString()));
        }
        json.addProperty("type", resolver.type().toString());
        json.addProperty("pending_line", resolver.pendingLine());
        json.addProperty("failure_line", resolver.failureLine());
        return new Dynamic<>(JsonOps.INSTANCE, json);
    }

    private static <T> Registered<T> decodeRegistered(ExtensionType<T> extension, JsonObject json,
                                                       String pendingLine, String failureLine) {
        JsonObject payload = json.deepCopy();
        payload.remove("type");
        payload.remove("pending_line");
        payload.remove("failure_line");
        T value = extension.codec().parse(JsonOps.INSTANCE, payload).getOrThrow();
        JsonElement normalized = extension.codec().encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        // Encoding can omit explicitly supplied defaults; map codecs expose all declared keys.
        Set<String> knownKeys = extension.codec() instanceof MapCodec.MapCodecCodec<?> mapCodec
                ? Set.copyOf(mapCodec.codec().keys(JsonOps.INSTANCE).map(JsonElement::getAsString).toList())
                : normalized.isJsonObject() ? normalized.getAsJsonObject().keySet() : Set.of();
        if (!normalized.isJsonObject() || !knownKeys.containsAll(payload.keySet())) {
            throw new IllegalArgumentException("Unknown dialogue resolver parameters: " + extension.id());
        }
        return new Registered<>(extension, value, pendingLine, failureLine);
    }

    private static <T> JsonObject encodeRegistered(Registered<T> registered) {
        JsonElement value = registered.extension().codec().encodeStart(JsonOps.INSTANCE, registered.value()).getOrThrow();
        if (!value.isJsonObject() || value.getAsJsonObject().has("type")
                || value.getAsJsonObject().has("pending_line") || value.getAsJsonObject().has("failure_line")) {
            throw new IllegalArgumentException("Registered resolver parameters must exclude reserved fields");
        }
        return value.getAsJsonObject();
    }

    private static void requireOnly(JsonObject json, Set<String> allowed) {
        for (String key : json.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException("Unknown resolver field: " + key);
            }
        }
    }

    private static String requiredString(JsonObject json, String field) {
        if (!json.has(field) || !json.get(field).isJsonPrimitive()
                || !json.getAsJsonPrimitive(field).isString()) {
            throw new IllegalArgumentException("Missing string field '" + field + "'");
        }
        String value = json.get(field).getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Blank string field '" + field + "'");
        }
        return value;
    }

    private static String optionalString(JsonObject json, String field, String fallback) {
        return json.has(field) ? requiredString(json, field) : fallback;
    }

    @FunctionalInterface
    interface Provider<T> {
        CompletionStage<Result> resolve(T parameters, DialogueContext context, UUID operationId);

        default void cancel(T parameters, UUID operationId) {
        }
    }

    record Result(boolean found, Optional<String> lineOverride, List<String> arguments) {
        public Result {
            lineOverride = Objects.requireNonNull(lineOverride);
            arguments = List.copyOf(arguments);
            if (lineOverride.isPresent() && lineOverride.orElseThrow().isBlank()) {
                throw new IllegalArgumentException("Async dialogue result key must not be blank");
            }
            if (arguments.size() > 16 || arguments.stream().anyMatch(argument -> argument.length() > 1024)) {
                throw new IllegalArgumentException("Async dialogue result arguments exceed safe bounds");
            }
        }

        public static Result withKey(String key, String... arguments) {
            return new Result(true, Optional.of(key), List.of(arguments));
        }

        public static Result found(String... arguments) {
            return new Result(true, Optional.empty(), List.of(arguments));
        }

        public static Result missing() {
            return new Result(false, Optional.empty(), List.of());
        }
    }

    record StructureLocation(Optional<String> pool, Optional<ResourceLocation> structure,
                             String pendingLine, String failureLine) implements DialogueAsyncResolver {
        public StructureLocation {
            if (pool.isPresent() == structure.isPresent()) {
                throw new IllegalArgumentException("structure location requires a pool or a structure");
            }
        }

        @Override
        public ResourceLocation type() {
            return STRUCTURE_LOCATION;
        }

        @Override
        public CompletionStage<Result> resolve(DialogueContext context, UUID operationId) {
            ResourceLocation identifier;
            if (structure.isPresent()) {
                identifier = structure.orElseThrow();
            } else {
                List<String> configured = Config.getInstance().structuresInRumors;
                if (configured.isEmpty()) {
                    return CompletableFuture.completedFuture(Result.missing());
                }
                identifier = ResourceLocation.tryParse(configured.get(context.villager().getRandom().nextInt(configured.size())));
                if (identifier == null) {
                    return CompletableFuture.completedFuture(Result.missing());
                }
            }
            var holder = context.level().registryAccess().registryOrThrow(Registries.STRUCTURE).getHolder(identifier);
            if (holder.isEmpty()) {
                return CompletableFuture.completedFuture(Result.missing());
            }
            BlockPos origin = RandomPos.generateRandomDirection(context.villager().getRandom(), 1024, 0)
                    .offset(context.villager().blockPosition());
            return MCA.getStructureLocator().locate(operationId, context.level(), origin,
                            HolderSet.direct(holder.orElseThrow()), 64)
                    .thenApply(found -> found.map(position -> Result.withKey(
                            "dialogue.location." + identifier.getPath(),
                            position.getX() + "," + position.getY() + "," + position.getZ()))
                            .orElseGet(Result::missing));
        }

        @Override
        public void cancel(UUID operationId) {
            MCA.getStructureLocator().cancel(operationId);
        }
    }

    record ExtensionType<T>(ResourceLocation id, Codec<T> codec, Provider<T> provider) {
    }

    record Registered<T>(ExtensionType<T> extension, T value, String pendingLine, String failureLine)
            implements DialogueAsyncResolver {
        @Override
        public ResourceLocation type() {
            return extension.id();
        }

        @Override
        public CompletionStage<Result> resolve(DialogueContext context, UUID operationId) {
            return extension.provider().resolve(value, context, operationId);
        }

        @Override
        public void cancel(UUID operationId) {
            extension.provider().cancel(value, operationId);
        }
    }

    final class Extensions {
        private static final Map<ResourceLocation, ExtensionType<?>> TYPES = new ConcurrentHashMap<>();

        private Extensions() {
        }
    }
}
