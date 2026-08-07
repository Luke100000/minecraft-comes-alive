package net.conczin.mca.util.network.datasync;

import net.conczin.mca.datafix.McaDataFixers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public class CDataManager<E extends Entity> {
    @FunctionalInterface
    public interface AccessorFactory<E extends Entity> {
        EntityDataAccessor<?> create(net.minecraft.network.syncher.EntityDataSerializer<?> serializer);
    }

    private final List<Entry<E, ?, ?>> params;

    private final Map<CParameter<?, ?>, Entry<E, ?, ?>> forwardLookup = new HashMap<>();
    private final Map<EntityDataAccessor<?>, Entry<E, ?, ?>> backwardLookup = new HashMap<>();

    private CDataManager(List<Entry<E, ?, ?>> params) {
        this.params = params;
        params.forEach(param -> {
            forwardLookup.put(param.parameter, param);
            backwardLookup.put(param.data, param);
        });
    }

    public boolean isParam(CParameter<?, ?> parameter, EntityDataAccessor<?> data) {
        Entry<E, ?, ?> entry = backwardLookup.get(data);
        return entry != null && entry.parameter == parameter;
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    public <T, TrackedType> T get(E entity, CParameter<T, TrackedType> parameter) {
        //noinspection RedundantCast
        return parameter.get(((Entry<E, T, TrackedType>) forwardLookup.get(parameter)).data, entity.getEntityData());
    }

    @SuppressWarnings({"unchecked", "RedundantSuppression"})
    public <T, TrackedType> void set(E entity, CParameter<T, TrackedType> parameter, T value) {
        //noinspection RedundantCast
        parameter.set(((Entry<E, T, TrackedType>) forwardLookup.get(parameter)).data, entity.getEntityData(), value);
    }

    //register all entries
    public void register(SynchedEntityData.Builder builder) {
        params.forEach(p -> p.register(builder));
    }

    public void load(E entity, CompoundTag nbt) {
        CompoundTag migrated = McaDataFixers.update(nbt);
        params.forEach(p -> p.load(entity, migrated));
    }

    public void save(E entity, CompoundTag nbt) {
        params.forEach(p -> p.save(entity, nbt));
        McaDataFixers.stampCurrentVersion(nbt);
    }

    public static class Builder<E extends Entity> {
        private final AccessorFactory<E> accessorFactory;
        private final List<Entry<E, ?, ?>> params = new ArrayList<>();

        public Builder(Class<E> type, AccessorFactory<E> accessorFactory) {
            this.accessorFactory = accessorFactory;
        }

        public Builder<E> addAll(CParameter<?, ?>... params) {
            Stream.of(params).map(p -> new Entry<>(accessorFactory, p)).forEach(this.params::add);
            return this;
        }

        public Builder<E> add(Function<Builder<E>, Builder<E>> subType) {
            return subType.apply(this);
        }

        public CDataManager<E> build() {
            return new CDataManager<>(params);
        }
    }

    private static class Entry<E extends Entity, T, TrackedType> {
        CParameter<T, TrackedType> parameter;
        EntityDataAccessor<TrackedType> data;

        public Entry(AccessorFactory<E> accessorFactory, CParameter<T, TrackedType> parameter) {
            this.parameter = parameter;
            this.data = parameter.createParam(accessorFactory);
        }

        public void save(E entity, CompoundTag nbt) {
            parameter.save(nbt, parameter.get(data, entity.getEntityData()), entity.registryAccess());
        }

        //load entity from nbt
        public void load(E entity, CompoundTag nbt) {
            parameter.set(data, entity.getEntityData(), parameter.load(nbt, entity.registryAccess()));
        }

        public void register(SynchedEntityData.Builder builder) {
            builder.define(data, parameter.getDefault());
        }
    }
}
