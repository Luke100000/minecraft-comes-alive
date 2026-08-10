package net.mca.util.network.datasync;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public interface CParameter<T, TrackedType> {
    static CDataParameter<Integer> create(String id, int def) {
        return new CDataParameter<>(id, EntityDataSerializers.INT, def, (nbt, key) -> NbtCompoundDefaultGetters.getInt(nbt, key, def), CompoundTag::putInt);
    }

    static CDataParameter<Float> create(String id, float def) {
        return new CDataParameter<>(id, EntityDataSerializers.FLOAT, def, (nbt, key) -> NbtCompoundDefaultGetters.getFloat(nbt, key, def), CompoundTag::putFloat);
    }

    static CDataParameter<Boolean> create(String id, boolean def) {
        return new CDataParameter<>(id, EntityDataSerializers.BOOLEAN, def, CompoundTag::getBoolean, CompoundTag::putBoolean);
    }

    static CDataParameter<String> create(String id, String def) {
        return new CDataParameter<>(id, EntityDataSerializers.STRING, def, (nbt, key) -> NbtCompoundDefaultGetters.getString(nbt, key, def), CompoundTag::putString);
    }

    static CDataParameter<CompoundTag> create(String id, CompoundTag def) {
        return new CDataParameter<>(id, EntityDataSerializers.COMPOUND_TAG, def, (nbt, key) -> NbtCompoundDefaultGetters.getCompound(nbt, key, def), CompoundTag::put);
    }

    static CDataParameter<ItemStack> create(String id, ItemStack def) {
		return new CDataParameter<>(id, EntityDataSerializers.ITEM_STACK, def,
			(nbt, key) -> NbtCompoundDefaultGetters.getItemStack(nbt, key, def), (nbt, key, stack) ->
			{
				CompoundTag item = new CompoundTag();
				stack.save(item);
				nbt.put(key, item);
			});
    }

    static CDataParameter<BlockPos> create(String id, BlockPos def) {
        return new CDataParameter<>(id, EntityDataSerializers.BLOCK_POS, def,
                (tag, key) -> new BlockPos(
                    tag.getInt(key + "X"),
                    tag.getInt(key + "Y"),
                    tag.getInt(key + "Z")
                ),
                (tag, key, pos) -> {
                    tag.putInt(key + "X", pos.getX());
                    tag.putInt(key + "Y", pos.getY());
                    tag.putInt(key + "Z", pos.getZ());
                });
    }

    static CDataParameter<Optional<UUID>> create(String id, Optional<UUID> def) {
        return new CDataParameter<>(id, EntityDataSerializers.OPTIONAL_UUID, def,
                (tag, key) -> tag.hasUUID(key) ? Optional.of(tag.getUUID(key)) : Optional.empty(),
                (tag, key, v) -> v.ifPresent(uuid -> tag.putUUID(key, uuid)));
    }

    @SuppressWarnings("unchecked")
    static <T extends Enum<T>> CEnumParameter<T> create(String id, T def) {
        return new CEnumParameter<>(id, (Class<T>)def.getClass(), def);
    }

    static <T extends Enum<T>> CEnumParameter<T> create(String id, Class<T> type) {
        return new CEnumParameter<>(id, type, null);
    }

    static CResourceLocationParameter create(String id, ResourceLocation def) {
        return new CResourceLocationParameter(id, def);
    }

    TrackedType getDefault();

    T get(EntityDataAccessor<TrackedType> param, SynchedEntityData tracker);

    void set(EntityDataAccessor<TrackedType> param, SynchedEntityData tracker, T v);

    T load(CompoundTag nbt);

    void save(CompoundTag nbt, T value);

    EntityDataAccessor<TrackedType> createParam(Class<? extends Entity> type);
}
