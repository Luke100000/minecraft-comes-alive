package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;

/** Lightweight common spatial contract for physical Structures and External Buildings. */
public interface VillageBuilding {
    int getId();

    BlockPos getPos0();

    BlockPos getPos1();

    BlockPos getCenter();

    boolean containsPos(Vec3i pos);
}
