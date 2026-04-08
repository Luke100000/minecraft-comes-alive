package net.conczin.mca.util.network.datasync;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

public interface CParameter<T, TrackedType> {
    static CDataParameter<Integer> create(String id, int def) {
        return new CDataParameter<>(id, EntityDataSerializers.INT, def,
                (nbt, key, provider) -> NbtCompoundDefaultGetters.getInt(nbt, key, def),
                (nbt, key, value, provider) -> nbt.putInt(key, value)
        );
    }

    static CDataParameter<Float> create(String id, float def) {
        return new CDataParameter<>(id, EntityDataSerializers.FLOAT, def,
                (nbt, key, provider) -> NbtCompoundDefaultGetters.getFloat(nbt, key, def),
                (nbt, key, value, provider) -> nbt.putFloat(key, value)
        );
    }

    static CDataParameter<Boolean> create(String id, boolean def) {
        return new CDataParameter<>(id, EntityDataSerializers.BOOLEAN, def,
                (nbt, key, provider) -> {
                    if (nbt.contains(key)) {
                        return nbt.getInt(key).orElse(0) != 0;
                    } else {
                        return def;
                    }
                },
                (nbt, key, value, provider) -> nbt.putInt(key, value ? 1 : 0)
        );
    }

    static CDataParameter<String> create(String id, String def) {
        return new CDataParameter<>(id, EntityDataSerializers.STRING, def,
                (nbt, key, provider) -> NbtCompoundDefaultGetters.getString(nbt, key, def),
                (nbt, key, value, provider) -> nbt.putString(key, value)
        );
    }

    static CDataParameter<CompoundTag> create(String id, CompoundTag def) {
        return new CDataParameter<>(id, MCAEntityDataSerializers.COMPOUND_TAG, def,
                (nbt, key, provider) -> NbtCompoundDefaultGetters.getCompound(nbt, key, def),
                (nbt, key, value, provider) -> nbt.put(key, value)
        );
    }

    static CDataParameter<ItemStack> create(String id, ItemStack def) {
        return new CDataParameter<>(id, EntityDataSerializers.ITEM_STACK, def,
                (nbt, key, provider) -> NbtCompoundDefaultGetters.getItemStack(nbt, key, ItemStack.EMPTY, provider),
                (nbt, key, stack, provider) -> nbt.storeNullable(
                        key,
                        ItemStack.OPTIONAL_CODEC,
                        provider.createSerializationContext(NbtOps.INSTANCE),
                        stack.isEmpty() ? null : stack
                ));
    }

    static CDataParameter<BlockPos> create(String id, BlockPos def) {
        return new CDataParameter<>(id, EntityDataSerializers.BLOCK_POS, def,
                (tag, key, provider) -> new BlockPos(
                        tag.getInt(key + "X").orElse(def.getX()),
                        tag.getInt(key + "Y").orElse(def.getY()),
                        tag.getInt(key + "Z").orElse(def.getZ())
                ),
                (tag, key, pos, provider) -> {
                    tag.putInt(key + "X", pos.getX());
                    tag.putInt(key + "Y", pos.getY());
                    tag.putInt(key + "Z", pos.getZ());
                });
    }

    static CDataParameter<Optional<UUID>> create(String id, Optional<UUID> def) {
        return new CDataParameter<>(id, MCAEntityDataSerializers.OPTIONAL_UUID, def,
                (tag, key, provider) -> tag.read(key, UUIDUtil.CODEC, provider.createSerializationContext(NbtOps.INSTANCE)).map(Optional::of).orElse(def),
                (tag, key, v, provider) -> tag.storeNullable(
                        key,
                        UUIDUtil.CODEC,
                        provider.createSerializationContext(NbtOps.INSTANCE),
                        v.orElse(null)
                )
        );
    }

    @SuppressWarnings("unchecked")
    static <T extends Enum<T>> CEnumParameter<T> create(String id, T def) {
        return new CEnumParameter<>(id, (Class<T>) def.getClass(), def);
    }

    static <T extends Enum<T>> CEnumParameter<T> create(String id, Class<T> type) {
        return new CEnumParameter<>(id, type, null);
    }

    TrackedType getDefault();

    T get(EntityDataAccessor<TrackedType> param, SynchedEntityData tracker);

    void set(EntityDataAccessor<TrackedType> param, SynchedEntityData tracker, T v);

    T load(CompoundTag nbt, RegistryAccess registries);

    void save(CompoundTag nbt, T value, RegistryAccess registries);

    EntityDataAccessor<TrackedType> createParam(CDataManager.AccessorFactory<?> accessorFactory);
}
