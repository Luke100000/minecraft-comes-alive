package net.conczin.mca.util.network.datasync;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Synchronizes an {@link Identifier} through vanilla's string entity-data
 * serializer while exposing a strongly typed value to MCA code.
 *
 * <p>{@link Identifier#STREAM_CODEC} uses the same UTF-8 string wire
 * representation. Reusing {@link EntityDataSerializers#STRING} avoids adding
 * loader-specific custom serializer registration.</p>
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
    public Identifier get(
            EntityDataAccessor<String> param,
            SynchedEntityData tracker
    ) {
        return parseOrDefault(tracker.get(param));
    }

    @Override
    public void set(
            EntityDataAccessor<String> param,
            SynchedEntityData tracker,
            Identifier value
    ) {
        tracker.set(param, Objects.requireNonNull(value, "value").toString());
    }

    @Override
    public Identifier load(CompoundTag nbt, RegistryAccess registryAccess) {
        return nbt.getString(id)
                .map(this::parseOrDefault)
                .orElse(defaultValue);
    }

    @Override
    public void save(
            CompoundTag nbt,
            Identifier value,
            RegistryAccess registryAccess
    ) {
        nbt.putString(id, Objects.requireNonNull(value, "value").toString());
    }

    @Override
    @SuppressWarnings("unchecked")
    public EntityDataAccessor<String> createParam(CDataManager.AccessorFactory<?> accessorFactory) {
        return (EntityDataAccessor<String>) accessorFactory.create(EntityDataSerializers.STRING);
    }

    private Identifier parseOrDefault(@Nullable String value) {
        if (value == null) {
            return defaultValue;
        }
        Identifier parsed = Identifier.tryParse(value);
        return parsed == null ? defaultValue : parsed;
    }
}
