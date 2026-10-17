package net.conczin.mca.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** A loader-neutral setting handle. Native config storage is bound by the active loader. */
public final class ConfigValue<T> {
    public interface Access<T> {
        T get();
        void set(T value);
    }

    private final ConfigSchema schema;
    private final List<String> path;
    private final T defaultValue;
    private final Predicate<Object> validator;
    private volatile T local;
    private volatile Access<T> access;

    ConfigValue(ConfigSchema schema, List<String> path, T defaultValue, Predicate<Object> validator) {
        this.schema = schema;
        this.path = List.copyOf(path);
        this.defaultValue = defensive(defaultValue);
        this.validator = validator;
        local = this.defaultValue;
    }

    public T get() {
        Object remote = schema.remoteValue(this);
        if (remote != ConfigSchema.NO_REMOTE_VALUE) {
            @SuppressWarnings("unchecked") T value = (T) remote;
            return defensive(value);
        }
        Access<T> bound = access;
        return defensive(bound == null ? local : bound.get());
    }

    public void set(T value) {
        if (!validator.test(value)) throw new IllegalArgumentException("Invalid MCA setting: " + String.join(".", path));
        T copy = defensive(value);
        Access<T> bound = access;
        if (bound != null) bound.set(copy);
        else local = copy;
    }

    public T getDefault() { return defensive(defaultValue); }
    public List<String> getPath() { return path; }
    public ValueSpec getSpec() { return new ValueSpec(validator); }
    public boolean accepts(Object candidate) { return validator.test(candidate); }

    public void bind(Access<T> backend) {
        this.access = Objects.requireNonNull(backend);
    }

    public void unbind() { access = null; }
    public void resetLocal() { local = defaultValue; }

    @SuppressWarnings("unchecked")
    private static <T> T defensive(T value) {
        return value instanceof List<?> list ? (T) List.copyOf(new ArrayList<>(list)) : value;
    }

    public record ValueSpec(Predicate<Object> validator) {
        public boolean test(Object value) { return validator.test(value); }
    }
}
