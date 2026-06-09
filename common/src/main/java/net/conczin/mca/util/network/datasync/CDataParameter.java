package net.conczin.mca.util.network.datasync;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;

public class CDataParameter<T> implements CParameter<T, T> {
    private final String id;

    private final T defaultValue;

    private final EntityDataSerializer<T> valueType;

    private final Decoder<T> load;
    private final Encoder<? super T> save;

    protected CDataParameter(
            String id,
            EntityDataSerializer<T> valueType,
            T defaultValue,
            Decoder<T> load,
            Encoder<? super T> save
    ) {
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
    public T load(CompoundTag nbt, RegistryAccess registryAccess) {
        return load.apply(nbt, id, registryAccess);
    }

    @Override
    public void save(CompoundTag nbt, T value, RegistryAccess registryAccess) {
        save.accept(nbt, id, value, registryAccess);
    }

    @Override
    public EntityDataSerializer<T> serializer() {
        return valueType;
    }

    public interface Decoder<T> {
        T apply(CompoundTag n, String k, RegistryAccess r);
    }

    public interface Encoder<T> {
        void accept(CompoundTag n, String k, T v, RegistryAccess r);
    }

    public interface TriConsumer<A, B, C> {
        void accept(A a, B b, C c);
    }
}
