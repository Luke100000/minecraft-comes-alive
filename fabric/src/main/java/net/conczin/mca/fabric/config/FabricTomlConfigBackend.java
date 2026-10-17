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

    public FabricTomlConfigBackend(Path directory) {
        this.directory = directory;
        for (ConfigScope scope : ConfigScope.values()) {
            var state = new AtomicReference<Map<String, Object>>(defaults(Config.schema(scope)));
            values.put(scope, state);
            paths.put(scope, directory.resolve(fileName(scope)));
            for (ConfigSchema.Entry<?> entry : Config.schema(scope).entries()) bind(entry, state);
        }
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
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                var parsed = new TomlParser().parse(reader);
                Map<String, Object> candidate = new LinkedHashMap<>(next);
                for (ConfigSchema.Entry<?> entry : schema.entries()) {
                    Object raw = parsed.getRaw(entry.name());
                    if (raw == null) continue;
                    Object normalized = normalize(entry, raw);
                    if (entry.value().accepts(normalized)) candidate.put(entry.name(), normalized);
                    else MCA.LOGGER.warn("Ignoring invalid config entry '{}' in {}", entry.name(), file);
                }
                next = Map.copyOf(candidate);
            } catch (Exception ex) {
                MCA.LOGGER.error("Invalid MCA TOML file {} (preserving original; error type {})",
                        file, ex.getClass().getSimpleName());
                // Keep the last fully validated state if a reload fails.
                next = schema.isLoaded() ? values.get(scope).get() : defaults(schema);
            }
        }
        values.get(scope).set(next);
        schema.markLoaded();

        if (!Files.exists(file) && createIfMissing) {
            boolean migrated = Config.migrateLegacy(schema, file);
            boolean upgraded = Config.upgradeDefaultMaps(schema);
            try {
                write(scope, file);
                if (migrated) Config.markLegacyImported(schema);
            } catch (IOException ex) {
                MCA.LOGGER.error("Cannot create MCA config file {} (error type {})",
                        file, ex.getClass().getSimpleName());
            }
        }
    }

    private static Object normalize(ConfigSchema.Entry<?> entry, Object value) {
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
        for (ConfigSchema.Entry<?> entry : Config.schema(scope).entries()) {
            document.set(entry.name(), values.get(scope).get().get(entry.name()));
            if (entry.comment() != null && document.getComment(entry.name()) == null)
                document.setComment(entry.name(), entry.comment());
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
}
