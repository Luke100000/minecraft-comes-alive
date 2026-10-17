package net.conczin.mca.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** One ordered source of definitions shared by Fabric and NeoForge. */
public final class ConfigSchema {
    static final Object NO_REMOTE_VALUE = new Object();
    public enum Restart { NONE, WORLD, GAME }

    public record Entry<T>(ConfigValue<T> value, List<String> path, String comment, String translation,
                           Restart restart, T defaultValue, Predicate<Object> validator, boolean list,
                           Number minimum, Number maximum, Supplier<?> newListElement, Predicate<Object> listValidator) {
        public String name() { return String.join(".", path); }
    }

    public record Section(List<String> path, String comment, String translation) {}

    private final ConfigScope scope;
    private final LinkedHashMap<String, Entry<?>> definitions = new LinkedHashMap<>();
    private final LinkedHashMap<List<String>, Section> sections = new LinkedHashMap<>();
    private volatile Map<ConfigValue<?>, Object> remote;
    private volatile boolean awaitingRemote;
    private volatile boolean loaded;

    private ConfigSchema(ConfigScope scope) { this.scope = scope; }

    public static Builder builder(ConfigScope scope) { return new Builder(new ConfigSchema(scope)); }
    public ConfigScope scope() { return scope; }
    public List<Entry<?>> entries() { return List.copyOf(definitions.values()); }
    public Set<String> paths() { return Collections.unmodifiableSet(definitions.keySet()); }
    public Entry<?> entry(String key) { return definitions.get(key); }
    public Map<String, Entry<?>> definitions() { return Collections.unmodifiableMap(definitions); }
    public Section section(List<String> path) { return sections.get(path); }
    public List<Section> sections() { return List.copyOf(sections.values()); }

    // A small compatibility view for layout tests and callers that list native spec paths.
    public ValuePaths getValues() { return new ValuePaths(entries()); }
    public record ValuePaths(List<Entry<?>> entries) {
        public boolean contains(List<String> path) {
            return entries.stream().anyMatch(entry -> entry.path().equals(path));
        }
    }

    Object remoteValue(ConfigValue<?> value) {
        if (awaitingRemote)
            throw new IllegalStateException("MCA SERVER configuration not yet synchronized");
        Map<ConfigValue<?>, Object> snapshot = remote;
        return snapshot == null ? NO_REMOTE_VALUE : snapshot.getOrDefault(value, NO_REMOTE_VALUE);
    }

    public synchronized void beginRemoteSync() {
        if (scope != ConfigScope.SERVER) throw new IllegalStateException("Only SERVER settings can be remote");
        remote = null;
        awaitingRemote = true;
    }

    public void completeNativeRemoteSync() {
        if (scope != ConfigScope.SERVER) throw new IllegalStateException("Only SERVER settings can be remote");
        awaitingRemote = false;
    }

    public boolean isAwaitingRemote() { return awaitingRemote; }

    public synchronized void applyRemote(Map<String, Object> validatedValues) {
        if (scope != ConfigScope.SERVER) throw new IllegalStateException("Only SERVER settings can be remote");
        if (!validatedValues.keySet().equals(definitions.keySet())) throw new IllegalArgumentException("Incomplete SERVER snapshot");
        Map<ConfigValue<?>, Object> candidate = new LinkedHashMap<>();
        for (Entry<?> entry : definitions.values()) {
            Object value = validatedValues.get(entry.name());
            if (!entry.validator().test(value)) throw new IllegalArgumentException("Invalid SERVER value: " + entry.name());
            candidate.put(entry.value(), value instanceof List<?> list ? List.copyOf(list) : value);
        }
        remote = Map.copyOf(candidate);
        awaitingRemote = false;
    }

    public void clearRemote() { remote = null; awaitingRemote = false; }
    public boolean isRemote() { return remote != null; }
    public boolean isLoaded() { return loaded; }
    public void markLoaded() { loaded = true; }

    public static final class Builder {
        private final ConfigSchema schema;
        private final Deque<String> sections = new ArrayDeque<>();
        private String comment;
        private String translation;
        private Restart restart = Restart.NONE;
        private Number minimum;
        private Number maximum;
        private Supplier<?> newListElement;
        private Predicate<Object> listValidator;

        private Builder(ConfigSchema schema) { this.schema = schema; }
        public ConfigSchema build() { return schema; }
        public Builder push(String section) {
            sections.addLast(section);
            List<String> path = List.copyOf(sections);
            schema.sections.put(path, new Section(path, comment, translation));
            comment = null;
            translation = null;
            return this;
        }
        public Builder pop() { sections.removeLast(); return this; }
        public Builder comment(String text) { comment = text; return this; }
        public Builder translation(String key) { translation = key; return this; }
        public Builder gameRestart() { restart = Restart.GAME; return this; }
        public Builder worldRestart() { restart = Restart.WORLD; return this; }

        public <T> ConfigValue<T> define(String key, T defaultValue) {
            return defineValidated(key, defaultValue, value ->
                    value != null && defaultValue.getClass().isInstance(value), false);
        }

        public <T extends Number & Comparable<T>> ConfigValue<T> defineInRange(String key, T defaultValue,
                                                                                 T minimum, T maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
            return defineValidated(key, defaultValue,
                    value -> value != null && defaultValue.getClass().isInstance(value)
                            && minimum.compareTo((T) value) <= 0
                            && maximum.compareTo((T) value) >= 0
                            && (!(value instanceof Double number) || Double.isFinite(number)), false);
        }

        public ConfigValue<Integer> defineInRange(String key, int defaultValue, int minimum, int maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
            return defineValidated(key, defaultValue,
                    value -> value instanceof Integer number && number >= minimum && number <= maximum, false);
        }

        public ConfigValue<Double> defineInRange(String key, double defaultValue, double minimum, double maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
            return defineValidated(key, defaultValue,
                    value -> value instanceof Double number && Double.isFinite(number)
                            && number >= minimum && number <= maximum, false);
        }

        public <T> ConfigValue<List<? extends T>> defineListAllowEmpty(
                String key, List<? extends T> defaults, Supplier<T> newElement, Predicate<Object> elementValidator) {
            List<? extends T> copied = List.copyOf(defaults);
            this.newListElement = newElement;
            this.listValidator = elementValidator;
            return defineValidated(key, copied, candidate -> {
                if (!(candidate instanceof List<?> list)) return false;
                return list.stream().allMatch(elementValidator);
            }, true);
        }

        private <T> ConfigValue<T> defineValidated(String key, T defaults, Predicate<Object> validator, boolean list) {
            List<String> path = new ArrayList<>(sections);
            path.add(key);
            String joined = String.join(".", path);
            if (schema.definitions.containsKey(joined)) throw new IllegalArgumentException("Duplicate setting: " + joined);
            ConfigValue<T> value = new ConfigValue<>(schema, path, defaults, validator);
            schema.definitions.put(joined,
                    new Entry<>(value, List.copyOf(path), comment, translation, restart, value.getDefault(), validator,
                            list, minimum, maximum, newListElement, listValidator));
            comment = null; translation = null; restart = Restart.NONE;
            minimum = null; maximum = null; newListElement = null; listValidator = null;
            return value;
        }
    }
}
