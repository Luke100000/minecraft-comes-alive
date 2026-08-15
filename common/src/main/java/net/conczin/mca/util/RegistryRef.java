package net.conczin.mca.util;

import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.function.Supplier;

public final class RegistryRef<T> implements Supplier<T> {
    private final ResourceLocation id;
    private final Supplier<? extends T> factory;
    private T value;

    private RegistryRef(ResourceLocation id, Supplier<? extends T> factory) {
        this.id = Objects.requireNonNull(id);
        this.factory = Objects.requireNonNull(factory);
    }

    public static <T> RegistryRef<T> of(ResourceLocation id, Supplier<? extends T> factory) {
        return new RegistryRef<>(id, factory);
    }

    public ResourceLocation id() {
        return id;
    }

    @Override
    public T get() {
        if (value == null) {
            value = Objects.requireNonNull(factory.get(), "Registry factory returned null for " + id);
        }
        return value;
    }
}
