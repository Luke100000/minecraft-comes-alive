package net.mca.entity;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;

public class UpdatableInventory extends SimpleContainer {
    public UpdatableInventory(int size) {
        super(size);
    }

    public void update(Entity entity) {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (!getItem(slot).isEmpty()) {
                getItem(slot).inventoryTick(entity.level(), entity, slot, false);
            }
        }
    }
}
