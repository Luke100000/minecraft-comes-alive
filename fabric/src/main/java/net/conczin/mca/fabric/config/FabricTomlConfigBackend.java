package net.conczin.mca.fabric.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.config.ConfigBackend;
import net.conczin.mca.config.ConfigSchema;
import net.conczin.mca.config.ConfigScope;
import net.conczin.mca.config.ConfigValue;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/** File-backed Fabric config, with no dependency on FCP or native config classes. */
public final class FabricTomlConfigBackend implements ConfigBackend {
    private final Path directory;
    private final Map<ConfigScope, AtomicReference<Map<String, Object>>> values = new EnumMap<>(ConfigScope.class);
    private final Map<ConfigScope, Path> paths = new EnumMap<>(ConfigScope.class);
    private Map<String, Object> globalServer;

    public FabricTomlConfigBackend(Path directory) {
        this.directory = directory;
        for (ConfigScope scope : ConfigScope.values()) {
            var state = new AtomicReference<Map<String, Object>>(defaults(Config.schema(scope)));
            values.put(scope, state);
            paths.put(scope, directory.resolve(fileName(scope)));
            for (ConfigSchema.Entry<?> entry : Config.schema(scope).entries()) bind(entry, state);
        }
        globalServer = defaults(Config.SERVER_SPEC);
    }

    private static String fileName(ConfigScope scope) {
        return switch (scope) {
            case COMMON -> Config.COMMON_FILE_NAME;
            case SERVER -> Config.SERVER_FILE_NAME;
            case CLIENT -> Config.CLIENT_FILE_NAME;
        };
    }

    private static Map<String, Object> defaults(ConfigSchema schema) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (ConfigSchema.Entry<?> entry : schema.entries()) result.put(entry.name(), entry.value().getDefault());
        return Map.copyOf(result);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void bind(ConfigSchema.Entry<?> entry, AtomicReference<Map<String, Object>> state) {
        ConfigValue handle = entry.value();
        String key = entry.name();
        handle.bind(new ConfigValue.Access() {
            @Override public Object get() { return state.get().get(key); }
            @Override public void set(Object value) {
                synchronized (state) {
                    Map<String, Object> candidate = new LinkedHashMap<>(state.get());
                    candidate.put(key, value);
                    state.set(Map.copyOf(candidate));
                }
            }
        });
    }

    public void loadGlobal(boolean physicalClient) {
        load(ConfigScope.COMMON, directory.resolve(Config.COMMON_FILE_NAME), true);
        load(ConfigScope.SERVER, directory.resolve(Config.SERVER_FILE_NAME), true);
        if (physicalClient) load(ConfigScope.CLIENT, directory.resolve(Config.CLIENT_FILE_NAME), true);
    }

    /** A world override is used only when present; absent overrides never get created. */
    public void startWorld(Path worldConfigDirectory) {
        Path worldFile = worldConfigDirectory.resolve(Config.SERVER_FILE_NAME);
        if (Files.isRegularFile(worldFile)) load(ConfigScope.SERVER, worldFile, false);
        else load(ConfigScope.SERVER, directory.resolve(Config.SERVER_FILE_NAME), false);
    }

    public void stopWorld() {
        load(ConfigScope.SERVER, directory.resolve(Config.SERVER_FILE_NAME), false);
    }

    private void load(ConfigScope scope, Path file, boolean createIfMissing) {
        ConfigSchema schema = Config.schema(scope);
        paths.put(scope, file);
        Map<String, Object> next = defaults(schema);
        boolean valid = true;
        boolean allEntriesValid = true;
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                var parsed = new TomlParser().parse(reader);
                Map<String, Object> candidate = new LinkedHashMap<>(next);
                for (ConfigSchema.Entry<?> entry : schema.entries()) {
                    Object raw = parsed.getRaw(entry.name());
                    if (raw == null) continue;
                    Object normalized = normalize(entry, raw);
                    if (entry.value().accepts(normalized)) candidate.put(entry.name(), normalized);
                    else {
                        allEntriesValid = false;
                        MCA.LOGGER.warn("Ignoring invalid config entry '{}' in {}", entry.name(), file);
                    }
                }
                next = Map.copyOf(candidate);
            } catch (Exception ex) {
                valid = false;
                MCA.LOGGER.error("Invalid MCA TOML file {} (preserving original; error type {})",
                        file, ex.getClass().getSimpleName());
                // World overrides may not inherit the preceding world's SERVER settings.
                next = scope == ConfigScope.SERVER && !file.equals(directory.resolve(Config.SERVER_FILE_NAME))
                        ? globalServer : defaults(schema);
            }
        }
        values.get(scope).set(next);
        if (scope == ConfigScope.SERVER && file.equals(directory.resolve(Config.SERVER_FILE_NAME)))
            globalServer = next;
        schema.markLoaded();

        boolean create = !Files.exists(file) && createIfMissing;
        if (valid && allEntriesValid && (create || (createIfMissing && Files.isRegularFile(file)))) {
            boolean migrated = create && Config.migrateLegacy(schema, file);
            // Default-map versions apply to existing, valid TOML too.
            boolean upgraded = Config.upgradeDefaultMaps(schema);
            try {
                if (create || upgraded) write(scope, file);
                if (scope == ConfigScope.SERVER && file.equals(directory.resolve(Config.SERVER_FILE_NAME)))
                    globalServer = values.get(scope).get();
                if (migrated) Config.markLegacyImported(schema);
            } catch (IOException ex) {
                values.get(scope).set(next);
                MCA.LOGGER.error("Cannot create MCA config file {} (error type {})",
                        file, ex.getClass().getSimpleName());
            }
        }
    }

    static Object normalize(ConfigSchema.Entry<?> entry, Object value) {
        Object original = entry.defaultValue();
        if (original instanceof Integer && value instanceof Number number) {
            long candidate = number.longValue();
            if (candidate != number.doubleValue() || candidate < Integer.MIN_VALUE || candidate > Integer.MAX_VALUE)
                return value;
            return (int) candidate;
        }
        if (original instanceof Double && value instanceof Number number) return number.doubleValue();
        if (original instanceof List<?> && value instanceof List<?> list) return List.copyOf(list);
        return value;
    }

    @Override public void save(ConfigScope scope) {
        try { write(scope, paths.get(scope)); }
        catch (IOException ex) { MCA.LOGGER.error("Cannot save MCA config {} (error type {})",
                paths.get(scope), ex.getClass().getSimpleName()); }
    }

    private void write(ConfigScope scope, Path destination) throws IOException {
        Files.createDirectories(destination.getParent());
        CommentedConfig document;
        if (Files.isRegularFile(destination)) {
            try (Reader reader = Files.newBufferedReader(destination)) {
                document = new TomlParser().parse(reader);
            } catch (Exception ex) {
                throw new IOException("Refusing to replace malformed or unreadable MCA TOML at " + destination, ex);
            }
        } else document = CommentedConfig.inMemory();
        for (ConfigSchema.Section section : Config.schema(scope).sections()) {
            if (section.comment() != null && document.getComment(section.path()) == null)
                document.setComment(section.path(), section.comment());
        }
        for (ConfigSchema.Entry<?> entry : Config.schema(scope).entries()) {
            document.set(entry.name(), values.get(scope).get().get(entry.name()));
            String expectedComment = nativeComment(entry);
            if (expectedComment != null && document.getComment(entry.name()) == null)
                document.setComment(entry.name(), expectedComment);
        }
        Path temp = Files.createTempFile(destination.getParent(), ".mca-config-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp)) { new TomlWriter().write(document, writer); }
            try { Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) {
                throw new IOException("Atomic TOML replacement unavailable for " + destination, ex);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    /** Match ModConfigSpec.Builder#defineInRange comment lines without requiring FCP at runtime. */
    private static String nativeComment(ConfigSchema.Entry<?> entry) {
        if (entry.minimum() == null) return entry.comment();
        Object min = entry.minimum();
        Object max = entry.maximum();
        String range;
        if (min instanceof Integer && max instanceof Integer && max.equals(Integer.MAX_VALUE))
            range = "> " + min;
        else if (min instanceof Integer && max instanceof Integer && min.equals(Integer.MIN_VALUE))
            range = "< " + max;
        else range = min + " ~ " + max;
        String suffix = " Default: " + entry.defaultValue() + "\n Range: " + range;
        return entry.comment() == null ? suffix : entry.comment() + "\n" + suffix;
    }
}
