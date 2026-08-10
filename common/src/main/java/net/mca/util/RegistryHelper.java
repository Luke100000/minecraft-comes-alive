package net.mca.util;

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

public class RegistryHelper {

    public static <T> Optional<TagKey<T>> tryGetTagKey(Registry<T> registry, ResourceLocation id) {
        return registry.getTagNames().filter(tagKey -> tagKey.location().equals(id)).findFirst();
    }

    public static <T> Optional<? extends HolderSet<T>> getEntries(TagKey<T> tagKey) {
        return getRegistryOf(tagKey).getTag(tagKey);
    }

    public static <T> Optional<Holder<T>> tryGetEntry(Registry<T> registry, T object) {
        return registry.getResourceKey(object).map(registry::getHolderOrThrow);
    }

    public static <T> boolean isObjectInTag(Registry<T> registry, ResourceLocation tagId, T object) {
        return tryGetTagKey(registry, tagId).map(tagKey -> isObjectInTag(registry, tagKey, object)).orElse(false);
    }

    public static <T> boolean isObjectInTag(Registry<T> registry, TagKey<T> tag, T object) {
        var entry = tryGetEntry(registry, object);
        return entry.map(tRegistryEntry -> tRegistryEntry.is(tag)).orElse(false);
    }

    public static <T> boolean isTagEmpty(TagKey<T> tag) {
        return getEntries(tag).map(HolderSet::size).orElse(0) == 0;
    }

    @SuppressWarnings("unchecked")
    public static <T> Registry<T> getRegistryOf(@NotNull TagKey<T> key) {
        return (Registry<T>) BuiltInRegistries.REGISTRY.get(key.registry().location());
    }
}
