package net.conczin.mca.registry;

import com.mojang.serialization.Codec;
import net.conczin.mca.MCA;
import net.conczin.mca.item.components.BabyParentsComponent;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.component.CustomData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

public interface DataComponentsMCA {
    Map<ResourceLocation, DataComponentType<?>> COMPONENTS = new HashMap<>();

    DataComponentType<GlobalPos> TRACKER_POS = register("tracker_pos", (b) -> b.persistent(GlobalPos.CODEC).networkSynchronized(GlobalPos.STREAM_CODEC));
    DataComponentType<String> TRACKER_NAME = register("tracker_name", (b) -> b.persistent(Codec.STRING).networkSynchronized(ByteBufCodecs.STRING_UTF8));
    DataComponentType<UUID> TRACKER_UUID = register("tracker_uuid", (b) -> b.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));

    DataComponentType<BabyParentsComponent> BABY_PARENTS = register("baby_parents", (b) -> b.persistent(BabyParentsComponent.CODEC).networkSynchronized(BabyParentsComponent.STREAM_CODEC));
    DataComponentType<Integer> BABY_AGE = register("baby_age", (b) -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT));
    DataComponentType<Integer> BABY_DROP_ATTEMPTS = register("baby_drop_attempts", (b) -> b.persistent(Codec.INT).networkSynchronized(ByteBufCodecs.INT));
    DataComponentType<Boolean> BABY_INVALIDATED = register("baby_invalidated", (b) -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    @SuppressWarnings("deprecation")
    DataComponentType<CustomData> BABY_NBT = register("baby_nbt", (b) -> b.persistent(CustomData.CODEC_WITH_ID).networkSynchronized(CustomData.STREAM_CODEC));

    DataComponentType<Boolean> SCYTHE_ACTIVE = register("scythe_active", (b) -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));
    DataComponentType<Boolean> SCYTHE_HAS_SOUL = register("scythe_has_soul", (b) -> b.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    DataComponentType<List<Component>> BOOK_PAGES = register("book_pages", (b) -> b.persistent(ComponentSerialization.FLAT_CODEC.listOf()).networkSynchronized(ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list())));

    private static <T> DataComponentType<T> register(String name, UnaryOperator<DataComponentType.Builder<T>> builder) {
        DataComponentType<T> component = builder.apply(DataComponentType.builder()).build();
        COMPONENTS.put(MCA.locate(name), component);
        return component;
    }

    static void registerProfessions(MCA.RegisterHelper<DataComponentType<?>> helper) {
        COMPONENTS.forEach(helper::register);
    }
}
