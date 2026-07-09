package net.conczin.mca.util;

import com.mojang.datafixers.util.Pair;
import net.conczin.mca.MCA;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface NbtHelper {

    @SuppressWarnings("unchecked")
    static <T extends Tag> T computeIfAbsent(CompoundTag nbt, String key, int type, Supplier<T> factory) {
        if (!nbt.contains(key, type)) {
            nbt.put(key, factory.get());
        }
        return (T) nbt.get(key);
    }

    static CompoundTag copyTo(CompoundTag from, CompoundTag to) {
        from.getAllKeys().forEach(key -> to.put(key, from.get(key)));
        return to;
    }

    static CompoundTag getCompoundOrSelf(CompoundTag nbt, String key) {
        return nbt.contains(key, 10) ? nbt.getCompound(key) : nbt;
    }

    static CompoundTag getOrCreateCompound(CompoundTag nbt, String key) {
        if (!nbt.contains(key, 10)) {
            nbt.put(key, new CompoundTag());
        }
        return nbt.getCompound(key);
    }

    static <V> List<V> toList(Tag nbt, Function<Tag, V> valueMapper) {
        return toStream(nbt, valueMapper).collect(Collectors.toList());
    }

    static <V> Stream<V> toStream(Tag nbt, Function<Tag, V> valueMapper) {
        return ((ListTag) nbt).stream().map(valueMapper);
    }

    static <K, V> Map<K, V> toMap(CompoundTag nbt, Function<String, K> keyMapper, Function<Tag, V> valueMapper) {
        return toMap(nbt, keyMapper, (k, e) -> valueMapper.apply(e));
    }

    static <K, V> Map<K, V> toMap(CompoundTag nbt, Function<String, K> keyMapper, BiFunction<K, Tag, V> valueMapper) {
        return nbt.getAllKeys().stream()
                .map(e -> {
                    K k = keyMapper.apply(e);
                    if (k == null) return null;
                    V v = valueMapper.apply(k, nbt.get(e));
                    if (v == null) return null;
                    return new Pair<>(k, v);
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Pair::getFirst, Pair::getSecond)
                );
    }

    static <V> ListTag fromList(Iterable<V> list, Function<V, Tag> valueMapper) {
        ListTag output = new ListTag();
        list.forEach(item -> {
            output.add(valueMapper.apply(item));
        });
        return output;
    }

    static <K, V> CompoundTag fromMap(CompoundTag output, Map<K, V> map, Function<K, String> keyMapper, Function<V, Tag> valueMapper) {
        map.forEach((key, value) -> output.put(keyMapper.apply(key), valueMapper.apply(value)));
        return output;
    }

    static Tag encodeGlobalPosition(GlobalPos v) {
        return GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, v).resultOrPartial(MCA.LOGGER::error).orElseThrow();
    }

    static GlobalPos decodeGlobalPos(Tag element) {
        return GlobalPos.CODEC.parse(NbtOps.INSTANCE, element).resultOrPartial(MCA.LOGGER::error).orElse(null);
    }
}
