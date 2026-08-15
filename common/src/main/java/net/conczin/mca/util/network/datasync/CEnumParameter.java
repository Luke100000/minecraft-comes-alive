package net.conczin.mca.util.network.datasync;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
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
    public T load(CompoundTag nbt) {
        return nbt.contains(id, Tag.TAG_ANY_NUMERIC) ? fromIndex(nbt.getInt(id)) : defaultValue;
    }

    @Override
    public void save(CompoundTag nbt, T value) {
        if (value != null) {
            nbt.putInt(id, value.ordinal());
        }
    }

    private T fromIndex(int index) {
        return index < 0 || index >= values.length ? defaultValue : values[index];
    }

    @Override
    public EntityDataAccessor<Integer> createParam(Class<? extends Entity> type) {
        return SynchedEntityData.defineId(type, EntityDataSerializers.INT);
    }
}
