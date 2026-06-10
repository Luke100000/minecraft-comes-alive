package net.conczin.mca.util.network.datasync;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import org.jetbrains.annotations.Nullable;

public class CEnumParameter<T extends Enum<T>> implements CParameter<T, Integer> {
    private final String id;

    @Nullable
    private final T defaultValue;

    private final T[] values;

    public CEnumParameter(String id, Class<T> type, @Nullable T dv) {
        this.id = id;
        this.defaultValue = dv;
        values = type.getEnumConstants();
    }

    @Override
    public Integer getDefault() {
        return defaultValue == null ? -1 : defaultValue.ordinal();
    }

    @Override
    public T get(EntityDataAccessor<Integer> param, SynchedEntityData tracker) {
        return fromIndex(tracker.get(param));
    }

    @Override
    public void set(EntityDataAccessor<Integer> param, SynchedEntityData tracker, @Nullable T v) {
        tracker.set(param, v == null ? -1 : v.ordinal());
    }

    @Override
    public T load(CompoundTag nbt, RegistryAccess registryAccess) {
        return nbt.getInt(id).map(this::fromIndex).orElse(defaultValue);
    }

    @Override
    public void save(CompoundTag nbt, T value, RegistryAccess registryAccess) {
        if (value != null) {
            nbt.putInt(id, value.ordinal());
        }
    }

    private T fromIndex(int index) {
        return index < 0 || index >= values.length ? defaultValue : values[index];
    }

    @Override
    @SuppressWarnings("unchecked")
    public EntityDataAccessor<Integer> createParam(CDataManager.AccessorFactory<?> accessorFactory) {
        return (EntityDataAccessor<Integer>) accessorFactory.create(EntityDataSerializers.INT);
    }
}
