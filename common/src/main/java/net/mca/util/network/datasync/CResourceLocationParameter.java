package net.mca.util.network.datasync;

import net.minecraft.entity.Entity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Synchronizes a namespaced identifier through vanilla's string tracked-data serializer.
 */
public final class CResourceLocationParameter implements CParameter<Identifier, String> {
    private final String id;
    private final Identifier defaultValue;

    CResourceLocationParameter(String id, Identifier defaultValue) {
        this.id = Objects.requireNonNull(id, "id");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
    }

    @Override
    public String getDefault() {
        return defaultValue.toString();
    }

    @Override
    public Identifier get(TrackedData<String> param, DataTracker tracker) {
        return parseOrDefault(tracker.get(param));
    }

    @Override
    public void set(TrackedData<String> param, DataTracker tracker, Identifier value) {
        tracker.set(param, Objects.requireNonNull(value, "value").toString());
    }

    @Override
    public Identifier load(NbtCompound nbt) {
        return nbt.contains(id, NbtElement.STRING_TYPE)
                ? parseOrDefault(nbt.getString(id))
                : defaultValue;
    }

    @Override
    public void save(NbtCompound nbt, Identifier value) {
        nbt.putString(id, Objects.requireNonNull(value, "value").toString());
    }

    @Override
    public TrackedData<String> createParam(Class<? extends Entity> type) {
        return DataTracker.registerData(type, TrackedDataHandlerRegistry.STRING);
    }

    private Identifier parseOrDefault(@Nullable String value) {
        if (value == null) {
            return defaultValue;
        }
        Identifier parsed = Identifier.tryParse(value);
        return parsed == null ? defaultValue : parsed;
    }
}
