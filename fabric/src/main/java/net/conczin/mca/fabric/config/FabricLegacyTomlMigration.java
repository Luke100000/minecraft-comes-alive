package net.conczin.mca.fabric.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.electronwill.nightconfig.toml.TomlWriter;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.config.ConfigSchema;
import net.conczin.mca.config.ConfigScope;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

/** Offline, idempotent path migration before FCP's native tracker reads TOML files. */
public final class FabricLegacyTomlMigration {
    private FabricLegacyTomlMigration() {}

    private record LocatedValue(ConfigScope source, String path, String leaf, Object value, String comment) {}
    private record Destination(ConfigScope scope, ConfigSchema.Entry<?> entry) {}

    public static void migrate(Path configDirectory) {
        migrate(configDirectory, true);
    }

    /** Dedicated servers must not read, parse, or rewrite physical-client configuration. */
    public static void migrate(Path configDirectory, boolean physicalClient) {
        if (!physicalClient) {
            migrateFiles(Map.of(
                    ConfigScope.COMMON, configDirectory.resolve(Config.COMMON_FILE_NAME),
                    ConfigScope.SERVER, configDirectory.resolve(Config.SERVER_FILE_NAME)));
        } else {
            migrateFiles(Map.of(
                    ConfigScope.COMMON, configDirectory.resolve(Config.COMMON_FILE_NAME),
                    ConfigScope.SERVER, configDirectory.resolve(Config.SERVER_FILE_NAME),
                    ConfigScope.CLIENT, configDirectory.resolve(Config.CLIENT_FILE_NAME)));
        }
    }

    /** FCP reads world overrides on SERVER_STARTING; relocate their old keys before that phase. */
    public static void migrateWorld(Path serverConfigDirectory) {
        migrateFiles(Map.of(ConfigScope.SERVER, serverConfigDirectory.resolve(Config.SERVER_FILE_NAME)));
    }

    private static void migrateFiles(Map<ConfigScope, Path> files) {
        Map<ConfigScope, CommentedConfig> documents = new EnumMap<>(ConfigScope.class);
        List<LocatedValue> legacyValues = new ArrayList<>();
        for (var file : files.entrySet()) {
            if (!Files.isRegularFile(file.getValue())) continue;
            try (Reader reader = Files.newBufferedReader(file.getValue())) {
                CommentedConfig parsed = new TomlParser().parse(reader);
                documents.put(file.getKey(), parsed);
                collectUnknown(file.getKey(), parsed, List.of(), legacyValues);
            } catch (Exception ex) {
                MCA.LOGGER.warn("Cannot migrate malformed MCA TOML {} (left unchanged; {})",
                        file.getValue(), ex.getClass().getSimpleName());
                // Never modify any sibling file based on incomplete input.
                return;
            }
        }

        Map<String, List<Destination>> byLeaf = new java.util.HashMap<>();
        for (ConfigScope scope : ConfigScope.values()) {
            for (ConfigSchema.Entry<?> entry : Config.schema(scope).entries()) {
                String leaf = entry.path().getLast();
                byLeaf.computeIfAbsent(leaf, unused -> new ArrayList<>()).add(new Destination(scope, entry));
            }
        }

        EnumSet<ConfigScope> changed = EnumSet.noneOf(ConfigScope.class);
        for (LocatedValue old : legacyValues) {
            List<Destination> candidates = byLeaf.getOrDefault(old.leaf(), List.of());
            if (candidates.size() != 1) continue; // Ambiguous old leaf: preserve for manual review.
            Destination target = candidates.getFirst();
            if (!files.containsKey(target.scope())) continue;
            if (!isRecognizedLegacyPath(old, target)) continue;
            Object normalized = FabricTomlConfigBackend.normalize(target.entry(), old.value());
            if (!target.entry().value().accepts(normalized)) continue;

            CommentedConfig destination = documents.computeIfAbsent(target.scope(), key -> CommentedConfig.inMemory());
            if (destination.getRaw(target.entry().name()) == null) {
                destination.set(target.entry().name(), normalized);
                if (old.comment() != null) destination.setComment(target.entry().name(), old.comment());
                changed.add(target.scope());
            }
            // Canonical values win conflicts; remove the obsolete alias either way.
            documents.get(old.source()).remove(old.path());
            changed.add(old.source());
        }
        if (changed.isEmpty()) return;

        // Keep original bytes for every existing file that may be rewritten.
        try {
            for (ConfigScope scope : changed) {
                Path path = files.get(scope);
                if (!Files.isRegularFile(path)) continue;
                Path backup = path.resolveSibling(path.getFileName() + ".pre-mca-migration.bak");
                if (Files.exists(backup)) {
                    for (int i = 1; ; i++) {
                        backup = path.resolveSibling(path.getFileName() + ".pre-mca-migration-" + i + ".bak");
                        if (!Files.exists(backup)) break;
                    }
                }
                Files.copy(path, backup);
            }
            for (ConfigScope scope : changed) write(files.get(scope), documents.get(scope));
        } catch (IOException ex) {
            MCA.LOGGER.error("Cannot migrate MCA TOML (existing backups retained; error type {})",
                    ex.getClass().getSimpleName());
        }
    }

    /** Recognize only historical MCA layouts; never reinterpret arbitrary user-defined tables. */
    private static boolean isRecognizedLegacyPath(LocatedValue old, Destination target) {
        if (old.source() == ConfigScope.SERVER && old.path().equals(old.leaf()))
            return target.scope() == ConfigScope.SERVER; // Earlier SERVER specs used flat root keys.
        if (old.source() == ConfigScope.SERVER && old.path().equals("player_customization." + old.leaf()))
            return target.scope() == ConfigScope.SERVER; // Former SERVER categories before the split.
        if (old.source() == ConfigScope.COMMON && old.path().equals("ai." + old.leaf()))
            return target.scope() == ConfigScope.SERVER || target.scope() == ConfigScope.CLIENT;
        return old.source() == ConfigScope.COMMON && old.path().equals("tts." + old.leaf())
                && target.scope() == ConfigScope.CLIENT;
    }

    private static void collectUnknown(ConfigScope scope, CommentedConfig document,
                                       List<String> parents, List<LocatedValue> result) {
        for (var entry : document.valueMap().entrySet()) {
            List<String> path = new ArrayList<>(parents);
            path.add(entry.getKey());
            if (entry.getValue() instanceof CommentedConfig nested) {
                collectUnknown(scope, nested, path, result);
            } else if (!Config.schema(scope).paths().contains(String.join(".", path))) {
                result.add(new LocatedValue(scope, String.join(".", path), entry.getKey(),
                        entry.getValue(), document.getComment(entry.getKey())));
            }
        }
    }

    private static void write(Path path, CommentedConfig config) throws IOException {
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".mca-migrate-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary)) { new TomlWriter().write(config, writer); }
            try { Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { throw new IOException("Atomic migration unavailable: " + path, ex); }
        } finally { Files.deleteIfExists(temporary); }
    }
}
