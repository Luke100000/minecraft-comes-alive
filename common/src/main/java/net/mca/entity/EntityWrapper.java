package net.mca.entity;

import net.minecraft.world.entity.Mob;

public interface EntityWrapper {
    default Mob asEntity() {
        return (Mob) this;
    }
}
