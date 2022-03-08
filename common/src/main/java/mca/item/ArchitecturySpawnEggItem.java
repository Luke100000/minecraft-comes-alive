package mca.item;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.nbt.NbtCompound;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Supplier;

// TODO: Remove when shedaniel adds this into the API
public class ArchitecturySpawnEggItem extends SpawnEggItem {
    private final Supplier<? extends EntityType<? extends MobEntity>> entityType;

    public ArchitecturySpawnEggItem(Supplier<? extends EntityType<? extends MobEntity>> entityType, int backgroundColor, int highlightColor, Settings properties) {
        super(null, backgroundColor, highlightColor, properties);
        this.entityType = Objects.requireNonNull(entityType, "entityType");
    }

    @Override
    public EntityType<?> getEntityType(@Nullable NbtCompound compoundTag) {
        EntityType<?> type = super.getEntityType(compoundTag);
        return type == null ? entityType.get() : type;
    }
}
