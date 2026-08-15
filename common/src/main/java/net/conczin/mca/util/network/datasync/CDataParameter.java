package net.conczin.mca.util.network.datasync;

import java.util.function.BiFunction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;

public class CDataParameter<T> implements CParameter<T, T> {
    private final String id;

    private final T defaultValue;

    private final EntityDataSerializer<T> valueType;

    private final BiFunction<CompoundTag, String, T> load;
    private final TriConsumer<CompoundTag, String, ? super T> save;

    protected CDataParameter(String id, EntityDataSerializer<T> valueType, T defaultValue,
            BiFunction<CompoundTag, String, T> load,
            TriConsumer<CompoundTag, String, ? super T> save) {
        this.id = id;
        this.defaultValue = defaultValue;
        this.valueType = valueType;
        this.load = load;
        this.save = save;
    }

    @Override
    public T getDefault() {
        return defaultValue;
    }

    @Override
    public T get(EntityDataAccessor<T> param, SynchedEntityData tracker) {
        return tracker.get(param);
    }

    @Override
    public void set(EntityDataAccessor<T> param, SynchedEntityData tracker, T v) {
        tracker.set(param, v);
    }

    @Override
    public T load(CompoundTag nbt) {
        return load.apply(nbt, id);
    }

    @Override
    public void save(CompoundTag nbt, T value) {
        save.accept(nbt, id, value);
    }

    @Override
    public EntityDataAccessor<T> createParam(Class<? extends Entity> type) {
        return SynchedEntityData.defineId(type, valueType);
    }

    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
}
