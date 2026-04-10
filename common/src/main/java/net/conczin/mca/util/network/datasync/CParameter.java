package net.conczin.mca.util.network.datasync;

import net.conczin.mca.MCA;
import net.conczin.mca.util.NbtHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

public interface CParameter<T, TrackedType> {
    EntityDataSerializer<CompoundTag> COMPOUND_TAG_SERIALIZER = registerSerializer("compound_tag", new EntityDataSerializer<>() {
        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, CompoundTag> codec() {
            return ByteBufCodecs.COMPOUND_TAG;
        }

        @Override
        public CompoundTag copy(CompoundTag value) {
            return value.copy();
        }
    });

    EntityDataSerializer<Optional<UUID>> OPTIONAL_UUID_SERIALIZER = registerSerializer("optional_uuid", new EntityDataSerializer<>() {
        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, Optional<UUID>> codec() {
            return ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC);
        }

        @Override
        public Optional<UUID> copy(Optional<UUID> value) {
            return value;
        }
    });

    static CDataParameter<Integer> create(String id, int def) {
        return new CDataParameter<>(id, EntityDataSerializers.INT, def,
                (nbt, key, provider) -> NbtCompoundDefaultGetters.getInt(nbt, key, def),
                (nbt, key, value, provider) -> nbt.putInt(key, value));
    }

    static CDataParameter<Float> create(String id, float def) {
        return new CDataParameter<>(id, EntityDataSerializers.FLOAT, def,
                (nbt, key, provider) -> NbtCompoundDefaultGetters.getFloat(nbt, key, def),
                (nbt, key, value, provider) -> nbt.putFloat(key, value));
    }

    static CDataParameter<Boolean> create(String id, boolean def) {
        return new CDataParameter<>(id, EntityDataSerializers.BOOLEAN, def,
                (nbt, key, provider) -> {
                    return nbt.getInt(key).map(value -> value != 0).orElse(def);
                },
                (nbt, key, value, provider) -> nbt.putInt(key, value ? 1 : 0));
    }

    static CDataParameter<String> create(String id, String def) {
        return new CDataParameter<>(id, EntityDataSerializers.STRING, def,
                (nbt, key, provider) -> NbtCompoundDefaultGetters.getString(nbt, key, def),
                (nbt, key, value, provider) -> nbt.putString(key, value));
    }

    static CDataParameter<CompoundTag> create(String id, CompoundTag def) {
        return new CDataParameter<>(id, COMPOUND_TAG_SERIALIZER, def,
                (nbt, key, provider) -> NbtCompoundDefaultGetters.getCompound(nbt, key, def),
                (nbt, key, value, provider) -> nbt.put(key, value));
    }

    static CDataParameter<ItemStack> create(String id, ItemStack def) {
        return new CDataParameter<>(id, EntityDataSerializers.ITEM_STACK, def,
                (nbt, key, provider) -> NbtCompoundDefaultGetters.getItemStack(nbt, key, ItemStack.EMPTY, provider),
                (nbt, key, stack, provider) -> ItemStack.CODEC
                        .encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), stack)
                        .result()
                        .ifPresent(tag -> nbt.put(key, tag)));
    }

    static CDataParameter<BlockPos> create(String id, BlockPos def) {
        return new CDataParameter<>(id, EntityDataSerializers.BLOCK_POS, def,
                (tag, key, provider) -> new BlockPos(
                        tag.getInt(key + "X").orElse(def.getX()),
                        tag.getInt(key + "Y").orElse(def.getY()),
                        tag.getInt(key + "Z").orElse(def.getZ())),
                (tag, key, pos, provider) -> {
                    tag.putInt(key + "X", pos.getX());
                    tag.putInt(key + "Y", pos.getY());
                    tag.putInt(key + "Z", pos.getZ());
                });
    }

    static CDataParameter<Optional<UUID>> create(String id, Optional<UUID> def) {
        return new CDataParameter<>(id, OPTIONAL_UUID_SERIALIZER, def,
                (tag, key, provider) -> NbtHelper.hasUUID(tag, key) ? Optional.of(NbtHelper.getUUID(tag, key))
                        : Optional.empty(),
                (tag, key, v, provider) -> v.ifPresent(uuid -> NbtHelper.putUUID(tag, key, uuid)));
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

    EntityDataAccessor<TrackedType> createParam(Class<? extends Entity> type);

    static <T> EntityDataSerializer<T> registerSerializer(String id, EntityDataSerializer<T> serializer) {
        if (isNeoForgeEnvironment()) {
            return serializer;
        }

        if (isFabricEnvironment()) {
            registerFabricSerializer(id, serializer);
            return serializer;
        }

        try {
            EntityDataSerializers.registerSerializer(serializer);
        } catch (UnsupportedOperationException ignored) {
        }
        return serializer;
    }

    private static <T> void registerFabricSerializer(String id, EntityDataSerializer<T> serializer) {
        if (tryInvokeFabricRegistry(
                "net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityDataRegistry",
                id,
                serializer
        )) {
            return;
        }

        if (tryInvokeFabricRegistry(
                "net.fabricmc.fabric.api.object.builder.v1.entity.FabricTrackedDataRegistry",
                id,
                serializer
        )) {
            return;
        }

        try {
            EntityDataSerializers.registerSerializer(serializer);
        } catch (UnsupportedOperationException e) {
            throw new IllegalStateException("Fabric tracked data serializer registration is unsupported for " + id, e);
        }
    }

    private static <T> boolean tryInvokeFabricRegistry(String className, String id, EntityDataSerializer<T> serializer) {
        try {
            Class<?> registryClass = Class.forName(className);
            Method registerMethod = registryClass.getMethod("register", net.minecraft.resources.Identifier.class, EntityDataSerializer.class);
            registerMethod.invoke(null, MCA.locate("tracked_data/" + id), serializer);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Unable to register Fabric tracked data serializer " + id + " via " + className, e);
        }
    }

    private static boolean isNeoForgeEnvironment() {
        try {
            Class.forName("net.neoforged.fml.ModList");
            return true;
        } catch (ClassNotFoundException ignored) {
        }

        try {
            Class.forName("net.neoforged.neoforge.registries.NeoForgeRegistries");
            return true;
        } catch (ClassNotFoundException ignored) {
        }

        return false;
    }

    private static boolean isFabricEnvironment() {
        try {
            Class.forName("net.fabricmc.loader.api.FabricLoader");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
    }
}
