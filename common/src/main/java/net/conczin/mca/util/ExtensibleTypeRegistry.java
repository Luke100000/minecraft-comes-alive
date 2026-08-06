package net.conczin.mca.util;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private final Map<ResourceLocation, T> entries = new LinkedHashMap<>();

    public ExtensibleTypeRegistry(@NotNull String defaultNamespace, @NotNull String typeName) {
        this.defaultNamespace = defaultNamespace;
        this.typeName = typeName;
    }

    public synchronized <V extends T> @NotNull V register(
            @NotNull ResourceLocation id,
            @NotNull Function<@NotNull ResourceLocation, @NotNull V> factory
    ) {
        if (entries.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate " + typeName + " id '" + id + "'");
        }

        V value = factory.apply(id);
        entries.put(id, value);
        return value;
    }

    public synchronized @NotNull Optional<T> get(@NotNull ResourceLocation id) {
        return Optional.ofNullable(entries.get(id));
    }

    public @NotNull Optional<T> get(@Nullable String id) {
        return parse(id).flatMap(this::get);
    }

    public synchronized @NotNull List<T> all() {
        return List.copyOf(entries.values());
    }

    public synchronized int size() {
        return entries.size();
    }

    public @NotNull Optional<ResourceLocation> parse(@Nullable String value) {
        if (value == null) {
            return Optional.empty();
        }

        String normalized = value.toLowerCase(Locale.ROOT);
        ResourceLocation id = normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(defaultNamespace, normalized);
        return Optional.ofNullable(id);
    }

    public @NotNull String translationSuffix(@NotNull ResourceLocation id) {
        String path = id.getPath().replace('/', '.');
        return id.getNamespace().equals(defaultNamespace) ? path : id.getNamespace() + "." + path;
    }

    public @NotNull String legacyId(@NotNull ResourceLocation id) {
        return id.getNamespace().equals(defaultNamespace) ? id.getPath() : id.toString();
    }
}
