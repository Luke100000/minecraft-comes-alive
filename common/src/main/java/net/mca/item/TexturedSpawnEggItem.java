package net.mca.item;

import dev.architectury.core.item.ArchitecturySpawnEggItem;
import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.Item;

public class TexturedSpawnEggItem extends ArchitecturySpawnEggItem {
    public TexturedSpawnEggItem(RegistrySupplier<? extends EntityType<? extends MobEntity>> entityType, Item.Settings settings) {
        super(entityType, 0xFFFFFF, 0xFFFFFF, settings);
    }

    @Override
    public int getColor(int tintIndex) {
        return -1;
    }
}
