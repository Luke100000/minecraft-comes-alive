package net.conczin.mca.server.world.data;

import java.util.Set;
import net.minecraft.core.BlockPos;

public record BuildingBlockedResult(
        Set<BlockPos> blocked,
        Building existingBuilding,
        Village village
) {
}
