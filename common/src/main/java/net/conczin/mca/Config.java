package net.conczin.mca;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import net.conczin.mca.entity.EquipmentSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class Config {
    public static final String COMMON_FILE_NAME = "mca-common.toml";
    public static final String SERVER_FILE_NAME = "mca-server.toml";
    public static final String CLIENT_FILE_NAME = "mca-client.toml";
    public static final String LEGACY_FILE_NAME = "mca.json";
    private static final int DEV_1_21_1_LEGACY_VERSION = 2;

    private static final Gson GSON = new Gson();
    private static final Path CONFIG_DIRECTORY = MCA.platformHelper.getConfigDirectory().toAbsolutePath().normalize();
    private static final LegacyJson LEGACY = LegacyJson.load(CONFIG_DIRECTORY.resolve(LEGACY_FILE_NAME));
    private static final boolean LEGACY_COMMON_IMPORT_ELIGIBLE = isLegacyImportEligible(COMMON_FILE_NAME);
    private static final boolean LEGACY_SERVER_IMPORT_ELIGIBLE = isLegacyImportEligible(SERVER_FILE_NAME);
    private static final boolean LEGACY_CLIENT_IMPORT_ELIGIBLE = isLegacyImportEligible(CLIENT_FILE_NAME);
    private static boolean legacyCommonImported;
    private static boolean legacyServerImported;
    private static boolean legacyClientImported;

    public static final CommonConfig COMMON;
    public static final ModConfigSpec COMMON_SPEC;
    public static final ServerConfig SERVER;
    public static final ModConfigSpec SERVER_SPEC;
    public static final ClientConfig CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        Pair<CommonConfig, ModConfigSpec> common = new ModConfigSpec.Builder().configure(CommonConfig::new);
        COMMON = common.getLeft();
        COMMON_SPEC = common.getRight();

        Pair<ServerConfig, ModConfigSpec> server = new ModConfigSpec.Builder().configure(ServerConfig::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();

        Pair<ClientConfig, ModConfigSpec> client = new ModConfigSpec.Builder().configure(ClientConfig::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();
    }

    private Config() {
    }

    public static void saveCommon() {
        COMMON_SPEC.save();
    }

    public static void saveServer() {
        SERVER_SPEC.save();
    }

    public static void saveClient() {
        CLIENT_SPEC.save();
    }

    public static Map<UUID, String> legacyInworldAIResourceNames() {
        if (!LEGACY.has("inworldAIResourceNames")) {
            return Map.of();
        }
        return decodeMap(
                LEGACY.mapValue("inworldAIResourceNames", List.of()),
                UUID.class,
                String.class,
                "inworldAIResourceNames");
    }

    private static boolean applyLegacyValues(ModConfigSpec spec) {
        if (!LEGACY.present()) {
            return false;
        }

        return applyLegacyValues(spec.getValues());
    }

    private static boolean applyLegacyValues(UnmodifiableConfig config) {
        boolean changed = false;
        for (UnmodifiableConfig.Entry entry : config.entrySet()) {
            Object value = entry.getRawValue();
            if (value instanceof UnmodifiableConfig nestedConfig) {
                changed |= applyLegacyValues(nestedConfig);
                continue;
            }
            if (!(value instanceof ModConfigSpec.ConfigValue<?> configValue)) {
                continue;
            }

            List<String> path = configValue.getPath();
            String name = path.get(path.size() - 1);
            if (!LEGACY.has(name)) {
                continue;
            }

            Object migratedValue = LEGACY.value(name, configValue.getDefault());
            if (!configValue.getSpec().test(migratedValue)) {
                MCA.LOGGER.warn("Ignoring invalid legacy MCA config value for '{}': {}", name, migratedValue);
                continue;
            }

            setConfigValue(configValue, migratedValue);
            changed = true;
        }
        return changed;
    }

    public static synchronized boolean migrateLegacy(ModConfigSpec spec, Path loadedConfigPath) {
        if (!shouldImportLegacy(spec, loadedConfigPath)) {
            return false;
        }

        boolean changed = applyLegacyValues(spec);
        if (spec == COMMON_SPEC) {
            legacyCommonImported = true;
        } else if (spec == SERVER_SPEC) {
            legacyServerImported = true;
        } else if (spec == CLIENT_SPEC) {
            legacyClientImported = true;
        }
        return changed;
    }

    private static boolean shouldImportLegacy(ModConfigSpec spec, Path loadedConfigPath) {
        if (!LEGACY.present()) {
            return false;
        }
        if (spec == COMMON_SPEC) {
            return LEGACY_COMMON_IMPORT_ELIGIBLE && !legacyCommonImported;
        }
        if (spec == CLIENT_SPEC) {
            return LEGACY_CLIENT_IMPORT_ELIGIBLE && !legacyClientImported;
        }
        if (spec == SERVER_SPEC) {
            if (!LEGACY_SERVER_IMPORT_ELIGIBLE || legacyServerImported) {
                return false;
            }
            // NeoForge and Forge Config API Port use the global config as the SERVER default and
            // select an existing world/serverconfig file only as an override. Migrate only that
            // global default; an explicit world override must remain authoritative.
            Path globalServerConfig = CONFIG_DIRECTORY.resolve(SERVER_FILE_NAME).toAbsolutePath().normalize();
            return loadedConfigPath.toAbsolutePath().normalize().equals(globalServerConfig);
        }
        return false;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setConfigValue(ModConfigSpec.ConfigValue configValue, Object value) {
        configValue.set(value);
    }

    static boolean isMapEntry(Object value, Class<?> keyType, Class<?> valueType) {
        EncodedMapEntry entry = parseMapEntry(value);
        if (entry == null) {
            return false;
        }
        return canDecodeScalar(entry.key(), keyType) && canDecodeScalar(entry.value(), valueType);
    }

    static boolean isResourceLocation(Object value) {
        return value instanceof String text && !text.isBlank() && ResourceLocation.tryParse(text) != null;
    }

    static boolean isRegistrySelector(Object value) {
        if (!(value instanceof String text) || text.isBlank()) {
            return false;
        }
        String resourceLocation = text.charAt(0) == '#' ? text.substring(1) : text;
        return !resourceLocation.isBlank() && ResourceLocation.tryParse(resourceLocation) != null;
    }

    static <T> boolean isLoadSafeRegistryId(Object value, ResourceKey<? extends Registry<T>> registryKey) {
        if (!(value instanceof String text) || text.isBlank()) {
            return false;
        }
        ResourceLocation id = ResourceLocation.tryParse(text);
        if (id == null) {
            return false;
        }
        if (!"minecraft".equals(id.getNamespace()) || !isBootstrapReady()) {
            return true;
        }
        Registry<T> registry = builtInRegistry(registryKey);
        return registry != null && registry.containsKey(id);
    }

    static <T> boolean isLoadSafeRegistrySelector(Object value, ResourceKey<? extends Registry<T>> registryKey) {
        if (!(value instanceof String text) || text.isBlank()) {
            return false;
        }
        if (text.charAt(0) == '#') {
            return isRegistrySelector(text);
        }
        return isLoadSafeRegistryId(text, registryKey);
    }

    static boolean isDestinySelector(Object value) {
        return "somewhere".equals(value) || isRegistrySelector(value);
    }

    static boolean isSpawnReason(Object value) {
        if (!(value instanceof String reason) || reason.isBlank()) {
            return false;
        }
        try {
            MobSpawnType.valueOf(reason.toUpperCase(Locale.ROOT));
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    static boolean isMapEntryWithResourceLocationKey(Object value, boolean allowTag, Class<?> valueType) {
        EncodedMapEntry entry = parseMapEntry(value);
        if (entry == null) {
            return false;
        }
        boolean validKey = allowTag ? isRegistrySelector(entry.key()) : isResourceLocation(entry.key());
        return validKey && canDecodeScalar(entry.value(), valueType);
    }

    static <T> boolean isMapEntryWithLoadSafeRegistryKey(
            Object value,
            ResourceKey<? extends Registry<T>> registryKey,
            boolean allowTag,
            Class<?> valueType
    ) {
        EncodedMapEntry entry = parseMapEntry(value);
        if (entry == null) {
            return false;
        }
        boolean validKey = allowTag
                ? isLoadSafeRegistrySelector(entry.key(), registryKey)
                : isLoadSafeRegistryId(entry.key(), registryKey);
        return validKey && canDecodeScalar(entry.value(), valueType);
    }

    static boolean isProfessionConversionEntry(Object value) {
        EncodedMapEntry entry = parseMapEntry(value);
        if (entry == null) {
            return false;
        }
        boolean validSource = "default".equals(entry.key())
                || isLoadSafeRegistryId(entry.key(), Registries.VILLAGER_PROFESSION);
        return validSource && isLoadSafeRegistryId(entry.value(), Registries.VILLAGER_PROFESSION);
    }

    private static boolean isBootstrapReady() {
        try {
            Bootstrap.checkBootstrapCalled(() -> "MCA config registry validation");
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Registry<T> builtInRegistry(ResourceKey<? extends Registry<T>> registryKey) {
        return (Registry<T>) BuiltInRegistries.REGISTRY.get(registryKey.location());
    }

    private static EncodedMapEntry parseMapEntry(Object value) {
        if (!(value instanceof String entry)) {
            return null;
        }
        int separator = entry.indexOf('=');
        if (separator <= 0) {
            return null;
        }
        return new EncodedMapEntry(
                entry.substring(0, separator).trim(),
                entry.substring(separator + 1).trim()
        );
    }

    private static boolean canDecodeScalar(String value, Class<?> type) {
        try {
            decodeScalar(value, type);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static List<String> encodeMap(Map<?, ?> values) {
        List<Map.Entry<?, ?>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Comparator.comparing(entry -> Objects.toString(entry.getKey())));

        List<String> encoded = new ArrayList<>(entries.size());
        for (Map.Entry<?, ?> entry : entries) {
            encoded.add(entry.getKey() + "=" + encodeScalar(entry.getValue()));
        }
        return encoded;
    }

    static <K, V> Map<K, V> decodeMap(List<? extends String> values, Class<K> keyType, Class<V> valueType, String fieldName) {
        Map<K, V> decoded = new LinkedHashMap<>();
        for (String entry : values) {
            int separator = entry.indexOf('=');
            if (separator <= 0) {
                MCA.LOGGER.warn("Skipping malformed map entry '{}' in MCA config value {}", entry, fieldName);
                continue;
            }
            try {
                K key = keyType.cast(decodeScalar(entry.substring(0, separator).trim(), keyType));
                V value = valueType.cast(decodeScalar(entry.substring(separator + 1).trim(), valueType));
                decoded.put(key, value);
            } catch (RuntimeException exception) {
                MCA.LOGGER.warn("Skipping invalid map entry '{}' in MCA config value {}", entry, fieldName, exception);
            }
        }
        return decoded;
    }

    static final class DecodedMapCache<K, V> {
        private final Class<K> keyType;
        private final Class<V> valueType;
        private final String fieldName;
        // Snapshot the contents: native config reloads usually replace the list, but other
        // callers may modify the existing list in place without changing its identity.
        private List<String> source;
        private Map<K, V> decoded = Map.of();

        DecodedMapCache(Class<K> keyType, Class<V> valueType, String fieldName) {
            this.keyType = keyType;
            this.valueType = valueType;
            this.fieldName = fieldName;
        }

        synchronized Map<K, V> get(List<? extends String> values) {
            if (source == null || !values.equals(source)) {
                decoded = Collections.unmodifiableMap(decodeMap(values, keyType, valueType, fieldName));
                source = List.copyOf(values);
            }
            return decoded;
        }
    }

    private static String encodeScalar(Object value) {
        if (value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof UUID) {
            return value.toString();
        }
        if (value instanceof EquipmentSet) {
            return GSON.toJson(value);
        }
        throw new IllegalArgumentException("Unsupported MCA config map value type: " + value.getClass().getName());
    }

    private static Object decodeScalar(String value, Class<?> type) {
        if (type == String.class) {
            return value;
        }
        if (type == UUID.class) {
            return UUID.fromString(value);
        }
        if (type == Integer.class) {
            return Integer.valueOf(value);
        }
        if (type == Float.class) {
            return Float.valueOf(value);
        }
        if (type == Boolean.class) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException("Not a boolean: " + value);
            }
            return Boolean.valueOf(value);
        }
        if (type == EquipmentSet.class) {
            EquipmentSet equipment = GSON.fromJson(value, EquipmentSet.class);
            if (equipment == null) {
                throw new IllegalArgumentException("Not an equipment set: " + value);
            }
            return equipment;
        }
        throw new IllegalArgumentException("Unsupported MCA config map type: " + type.getName());
    }

    private static boolean isLegacyImportEligible(String destinationFileName) {
        return LEGACY.present() && Files.notExists(CONFIG_DIRECTORY.resolve(destinationFileName));
    }

    private record EncodedMapEntry(String key, String value) {
    }

    private record LegacyJson(JsonObject root, boolean present) {
        static LegacyJson load(Path path) {
            if (Files.notExists(path)) {
                return new LegacyJson(new JsonObject(), false);
            }
            try (Reader reader = Files.newBufferedReader(path)) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Legacy config root is not a JSON object");
                }
                JsonObject root = element.getAsJsonObject();
                JsonElement version = root.get("version");
                if (version == null || version.isJsonNull() || version.getAsInt() != DEV_1_21_1_LEGACY_VERSION) {
                    MCA.LOGGER.info("Ignoring MCA config {} because it is not a dev/1.21.1 schema version {} file.", path, DEV_1_21_1_LEGACY_VERSION);
                    return new LegacyJson(new JsonObject(), false);
                }
                MCA.LOGGER.info("Found legacy MCA config {}. Missing TOML configs will import its values.", path);
                return new LegacyJson(root, true);
            } catch (IOException | RuntimeException exception) {
                MCA.LOGGER.error("Unable to import legacy MCA config {}. The file is preserved and defaults will be used.", path, exception);
                return new LegacyJson(new JsonObject(), false);
            }
        }

        boolean booleanValue(String name, boolean fallback) {
            return read(name, fallback, JsonElement::getAsBoolean);
        }

        boolean has(String name) {
            JsonElement element = root.get(name);
            return element != null && !element.isJsonNull();
        }

        Object value(String name, Object fallback) {
            if (fallback instanceof Boolean value) {
                return booleanValue(name, value);
            }
            if (fallback instanceof Integer value) {
                return intValue(name, value);
            }
            if (fallback instanceof Double value) {
                return doubleValue(name, value);
            }
            if (fallback instanceof String value) {
                return stringValue(name, value);
            }
            if (fallback instanceof List<?> values) {
                List<String> strings = values.stream().map(Objects::toString).toList();
                JsonElement element = root.get(name);
                return element != null && element.isJsonObject()
                        ? mapValue(name, strings)
                        : listValue(name, strings);
            }
            throw new IllegalArgumentException("Unsupported MCA legacy config type for '" + name + "': " + fallback.getClass().getName());
        }

        int intValue(String name, int fallback) {
            return read(name, fallback, JsonElement::getAsInt);
        }

        double doubleValue(String name, double fallback) {
            return read(name, fallback, JsonElement::getAsDouble);
        }

        String stringValue(String name, String fallback) {
            return read(name, fallback, JsonElement::getAsString);
        }

        List<String> listValue(String name, List<String> fallback) {
            JsonElement element = root.get(name);
            if (element == null || element.isJsonNull()) {
                return fallback;
            }
            try {
                if (!element.isJsonArray()) {
                    throw new IllegalArgumentException("Expected a JSON array");
                }
                List<String> values = new ArrayList<>();
                for (JsonElement item : element.getAsJsonArray()) {
                    values.add(item.getAsString());
                }
                return values;
            } catch (RuntimeException exception) {
                warnField(name, exception);
                return fallback;
            }
        }

        List<String> mapValue(String name, List<String> fallback) {
            JsonElement element = root.get(name);
            if (element == null || element.isJsonNull()) {
                return fallback;
            }
            try {
                if (!element.isJsonObject()) {
                    throw new IllegalArgumentException("Expected a JSON object");
                }
                return encodeJsonMap(element.getAsJsonObject());
            } catch (RuntimeException exception) {
                warnField(name, exception);
                return fallback;
            }
        }

        private <T> T read(String name, T fallback, java.util.function.Function<JsonElement, T> converter) {
            JsonElement element = root.get(name);
            if (element == null || element.isJsonNull()) {
                return fallback;
            }
            try {
                return converter.apply(element);
            } catch (RuntimeException exception) {
                warnField(name, exception);
                return fallback;
            }
        }

        private void warnField(String name, RuntimeException exception) {
            MCA.LOGGER.warn("Unable to import legacy MCA config field '{}'; using the native config default.", name, exception);
        }

        private static List<String> encodeJsonMap(JsonObject object) {
            return object.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + encodeJsonScalar(entry.getValue()))
                    .toList();
        }

        private static String encodeJsonScalar(JsonElement value) {
            if (value.isJsonPrimitive()) {
                if (value.getAsJsonPrimitive().isString()) {
                    return value.getAsString();
                }
                return value.toString();
            }
            return GSON.toJson(value);
        }

    }
}
