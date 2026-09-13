package net.conczin.mca.entity;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;

import java.util.function.Consumer;

public class UpdatableInventory extends SimpleContainer {
    private Consumer<Container> listener;

    public UpdatableInventory(int size) {
        super(size);
    }

    public void addListener(Consumer<Container> listener) {
        this.listener = listener;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        if (listener != null) listener.accept(this);
    }

    public void update(Entity entity) {
        for (int slot = 0; slot < getContainerSize(); slot++) {
            if (!getItem(slot).isEmpty()) {
                getItem(slot).inventoryTick(entity.level(), entity, null);
            }
        }
    }
}
