package net.conczin.mca.item;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;

public class TexturedSpawnEggItem extends SpawnEggItem {
    public TexturedSpawnEggItem(EntityType<? extends Mob> entityType, Item.Properties properties) {
        super(entityType, 0xFFFFFF, 0xFFFFFF, properties);
    }

    @Override
    public int getColor(int tintIndex) {
        return -1;
    }
}
