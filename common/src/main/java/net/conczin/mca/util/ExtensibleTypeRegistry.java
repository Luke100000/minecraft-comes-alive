package net.conczin.mca.util;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Small insertion-ordered registry for MCA types exposed to addon mods.
 *
 * <p>The registry owns stable namespaced identifiers and accepts legacy bare
 * identifiers by resolving them against its configured default namespace.</p>
 */
public final class ExtensibleTypeRegistry<T> {
    private final String defaultNamespace;
    private final String typeName;
    private final Map<Identifier, T> entries = new LinkedHashMap<>();
    private final Map<String, T> entriesByName = new HashMap<>();

    public ExtensibleTypeRegistry(String defaultNamespace, String typeName) {
        this.defaultNamespace = Objects.requireNonNull(defaultNamespace, "defaultNamespace");
        this.typeName = Objects.requireNonNull(typeName, "typeName");
    }

    public synchronized <V extends T> V register(
            Identifier id,
            Function<Identifier, V> factory
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(factory, "factory");
        if (entries.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate " + typeName + " id '" + id + "'");
        }

        V value = Objects.requireNonNull(factory.apply(id), typeName + " factory result");
        entries.put(id, value);
        if (!id.getNamespace().isEmpty()) {
            entriesByName.put(id.toString(), value);
        }
        if (id.getNamespace().equals(defaultNamespace)) {
            entriesByName.put(id.getPath(), value);
        }
        // Identifier interprets an empty explicit namespace as minecraft, not the registry default.
        if (id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE)) {
            entriesByName.put(":" + id.getPath(), value);
        }
        return value;
    }

    public synchronized Optional<T> get(Identifier id) {
        return Optional.ofNullable(entries.get(id));
    }

    public synchronized Optional<T> get(@Nullable String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(entriesByName.get(id.toLowerCase(Locale.ROOT)));
    }

    public synchronized List<T> all() {
        return List.copyOf(entries.values());
    }

    public synchronized int size() {
        return entries.size();
    }

    public Optional<Identifier> parse(@Nullable String value) {
        if (value == null) {
            return Optional.empty();
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        Identifier id = normalized.indexOf(':') >= 0
                ? Identifier.tryParse(normalized)
                : Identifier.tryBuild(defaultNamespace, normalized);
        return Optional.ofNullable(id);
    }

    public String translationSuffix(Identifier id) {
        Objects.requireNonNull(id, "id");
        String path = id.getPath().replace('/', '.');
        return id.getNamespace().equals(defaultNamespace) ? path : id.getNamespace() + "." + path;
    }

    public String legacyId(Identifier id) {
        Objects.requireNonNull(id, "id");
        return id.getNamespace().equals(defaultNamespace) ? id.getPath() : id.toString();
    }
}
