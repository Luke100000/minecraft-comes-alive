package net.conczin.mca.util.network.datasync;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Synchronizes a {@link ResourceLocation} through vanilla's string entity-data
 * serializer while exposing a strongly typed value to MCA code.
 *
 * <p>{@link ResourceLocation#STREAM_CODEC} uses the same UTF-8 string wire
 * representation. Reusing {@link EntityDataSerializers#STRING} avoids adding
 * loader-specific custom serializer registration.</p>
 */
public final class CResourceLocationParameter implements CParameter<ResourceLocation, String> {
    private final String id;
    private final ResourceLocation defaultValue;

    CResourceLocationParameter(@NotNull String id, @NotNull ResourceLocation defaultValue) {
        this.id = Objects.requireNonNull(id, "id");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
    }

    @Override
    public @NotNull String getDefault() {
        return defaultValue.toString();
    }

    @Override
    public @NotNull ResourceLocation get(
            @NotNull EntityDataAccessor<String> param,
            @NotNull SynchedEntityData tracker
    ) {
        return parseOrDefault(tracker.get(param));
    }

    @Override
    public void set(
            @NotNull EntityDataAccessor<String> param,
            @NotNull SynchedEntityData tracker,
            @NotNull ResourceLocation value
    ) {
        tracker.set(param, Objects.requireNonNull(value, "value").toString());
    }

    @Override
    public @NotNull ResourceLocation load(@NotNull CompoundTag nbt, @NotNull RegistryAccess registryAccess) {
        return nbt.contains(id, Tag.TAG_STRING)
                ? parseOrDefault(nbt.getString(id))
                : defaultValue;
    }

    @Override
    public void save(
            @NotNull CompoundTag nbt,
            @NotNull ResourceLocation value,
            @NotNull RegistryAccess registryAccess
    ) {
        nbt.putString(id, Objects.requireNonNull(value, "value").toString());
    }

    @Override
    public @NotNull EntityDataAccessor<String> createParam(@NotNull Class<? extends Entity> type) {
        return SynchedEntityData.defineId(type, EntityDataSerializers.STRING);
    }

    private @NotNull ResourceLocation parseOrDefault(@Nullable String value) {
        if (value == null) {
            return defaultValue;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null ? defaultValue : parsed;
    }
}
