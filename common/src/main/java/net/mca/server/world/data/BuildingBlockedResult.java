package net.mca.server.world.data;

import net.minecraft.util.math.BlockPos;

import java.util.Set;

public record BuildingBlockedResult(
        Set<BlockPos> blocked,
        Building existingBuilding,
        Village village
) {
}
