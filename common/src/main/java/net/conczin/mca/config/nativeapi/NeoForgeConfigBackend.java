package net.conczin.mca.config.nativeapi;

import net.conczin.mca.Config;
import net.conczin.mca.config.ConfigBackend;
import net.conczin.mca.config.ConfigSchema;
import net.conczin.mca.config.ConfigScope;
import net.conczin.mca.config.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Loader-owned native config materialization: shared definitions remain loader-independent. */
public final class NeoForgeConfigBackend implements ConfigBackend {
    private final Map<ConfigScope, ModConfigSpec> specs = new EnumMap<>(ConfigScope.class);

    public NeoForgeConfigBackend() {
        for (ConfigScope scope : ConfigScope.values()) {
            specs.put(scope, build(Config.schema(scope)));
        }
    }

    public ModConfigSpec spec(ConfigScope scope) {
        return specs.get(scope);
    }

    @Override
    public void save(ConfigScope scope) {
        spec(scope).save();
    }

    private ModConfigSpec build(ConfigSchema schema) {
        ModConfigSpec.Builder nativeBuilder = new ModConfigSpec.Builder();
        List<String> previousParents = List.of();
        for (ConfigSchema.Entry<?> entry : schema.entries()) {
            List<String> parents = entry.path().subList(0, entry.path().size() - 1);
            int shared = 0;
            while (shared < previousParents.size() && shared < parents.size()
                    && previousParents.get(shared).equals(parents.get(shared))) shared++;
            for (int index = previousParents.size(); index > shared; index--) nativeBuilder.pop();
            for (int index = shared; index < parents.size(); index++) nativeBuilder.push(parents.get(index));
            previousParents = parents;
            if (entry.comment() != null) nativeBuilder.comment(entry.comment());
            if (entry.translation() != null) nativeBuilder.translation(entry.translation());
            if (entry.restart() == ConfigSchema.Restart.GAME) nativeBuilder.gameRestart();
            else if (entry.restart() == ConfigSchema.Restart.WORLD) nativeBuilder.worldRestart();
            bind(nativeBuilder, entry);
        }
        for (int index = previousParents.size(); index > 0; index--) nativeBuilder.pop();
        return nativeBuilder.build();
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void bind(ModConfigSpec.Builder builder, ConfigSchema.Entry<?> entry) {
        String name = entry.path().getLast();
        Object value = entry.defaultValue();
        ModConfigSpec.ConfigValue nativeValue;
        if (entry.list()) {
            nativeValue = builder.defineListAllowEmpty(name, (List) value,
                    (Supplier) entry.newListElement(), entry.listValidator());
        } else if (entry.minimum() != null) {
            if (value instanceof Integer v) {
                nativeValue = builder.defineInRange(name, v, entry.minimum().intValue(), entry.maximum().intValue());
            } else if (value instanceof Double v) {
                nativeValue = builder.defineInRange(name, v, entry.minimum().doubleValue(), entry.maximum().doubleValue());
            } else {
                throw new IllegalStateException("Unsupported MCA config range type: " + value.getClass());
            }
        } else {
            nativeValue = builder.define(name, () -> value, (Predicate<Object>) entry.validator());
        }
        ConfigValue handle = entry.value();
        handle.bind(new ConfigValue.Access() {
            @Override public Object get() { return nativeValue.get(); }
            @Override public void set(Object newValue) { nativeValue.set(newValue); }
        });
    }
}
