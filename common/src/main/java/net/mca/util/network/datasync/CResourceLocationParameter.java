package net.mca.util.network.datasync;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Synchronizes a namespaced identifier through vanilla's string tracked-data serializer.
 */
public final class CResourceLocationParameter implements CParameter<ResourceLocation, String> {
    private final String id;
    private final ResourceLocation defaultValue;

    CResourceLocationParameter(String id, ResourceLocation defaultValue) {
        this.id = Objects.requireNonNull(id, "id");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
    }

    @Override
    public String getDefault() {
        return defaultValue.toString();
    }

    @Override
    public ResourceLocation get(EntityDataAccessor<String> param, SynchedEntityData tracker) {
        return parseOrDefault(tracker.get(param));
    }

    @Override
    public void set(EntityDataAccessor<String> param, SynchedEntityData tracker, ResourceLocation value) {
        tracker.set(param, Objects.requireNonNull(value, "value").toString());
    }

    @Override
    public ResourceLocation load(CompoundTag nbt) {
        return nbt.contains(id, Tag.TAG_STRING)
                ? parseOrDefault(nbt.getString(id))
                : defaultValue;
    }

    @Override
    public void save(CompoundTag nbt, ResourceLocation value) {
        nbt.putString(id, Objects.requireNonNull(value, "value").toString());
    }

    @Override
    public EntityDataAccessor<String> createParam(Class<? extends Entity> type) {
        return SynchedEntityData.defineId(type, EntityDataSerializers.STRING);
    }

    private ResourceLocation parseOrDefault(@Nullable String value) {
        if (value == null) {
            return defaultValue;
        }
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        return parsed == null ? defaultValue : parsed;
    }
}
